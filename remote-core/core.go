package main

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"sync"
	"time"

	"tailscale.com/envknob"
	"tailscale.com/ipn"
	"tailscale.com/tsnet"
)

// State files contain authenticated ciphertext only. A nil write is an encrypted
// tombstone, so logout does not require a recursive filesystem cleanup.
type sealedStore struct {
	mu   sync.Mutex
	dir  string
	aead cipher.AEAD
}

func newStore(dir string, key []byte) (*sealedStore, error) {
	if len(key) != 32 {
		return nil, errors.New("invalid state key")
	}
	b, e := aes.NewCipher(key)
	if e != nil {
		return nil, e
	}
	a, e := cipher.NewGCM(b)
	if e != nil {
		return nil, e
	}
	if e = os.MkdirAll(dir, 0700); e != nil {
		return nil, e
	}
	return &sealedStore{dir: dir, aead: a}, nil
}
func (s *sealedStore) path(id ipn.StateKey) string {
	h := sha256.Sum256([]byte(id))
	return filepath.Join(s.dir, hex.EncodeToString(h[:])+".sealed")
}
func (s *sealedStore) ReadState(id ipn.StateKey) ([]byte, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	b, e := os.ReadFile(s.path(id))
	if os.IsNotExist(e) {
		return nil, ipn.ErrStateNotExist
	}
	if e != nil {
		return nil, e
	}
	if len(b) < s.aead.NonceSize() {
		return nil, errors.New("invalid sealed state")
	}
	p, e := s.aead.Open(nil, b[:s.aead.NonceSize()], b[s.aead.NonceSize():], []byte(id))
	if e != nil {
		return nil, e
	}
	if len(p) < 1 {
		return nil, errors.New("invalid state")
	}
	if p[0] == 0 {
		return nil, ipn.ErrStateNotExist
	}
	return p[1:], nil
}
func (s *sealedStore) WriteState(id ipn.StateKey, p []byte) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if len(p) > 4<<20 {
		return errors.New("state limit")
	}
	n := make([]byte, s.aead.NonceSize())
	if _, e := rand.Read(n); e != nil {
		return e
	}
	flag := byte(1)
	if p == nil {
		flag = 0
	}
	b := s.aead.Seal(n, n, append([]byte{flag}, p...), []byte(id))
	path := s.path(id)
	f, e := os.OpenFile(path+".pending", os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0600)
	if e != nil {
		return e
	}
	_, e = f.Write(b)
	if e == nil {
		e = f.Sync()
	}
	ce := f.Close()
	if e != nil {
		return e
	}
	if ce != nil {
		return ce
	}
	if e = os.Rename(path+".pending", path); e != nil {
		return e
	}
	d, e := os.Open(s.dir)
	if e != nil {
		return e
	}
	defer d.Close()
	return d.Sync()
}

type request struct {
	Peer map[string]any `json:"peer"`
	Op   string         `json:"op"`
	Dir  string         `json:"dir"`
	Key  string         `json:"key"`
	Node string         `json:"node"`
	IP   string         `json:"ip"`
	Port int            `json:"port"`
}
type node struct {
	s       *tsnet.Server
	started bool
	relays  map[net.Listener]net.Conn
	mu      sync.Mutex
}

var state struct {
	sync.Mutex
	n *node
}

func main()                {}
func respond(v any) string { b, _ := json.Marshal(v); return string(b) }
func invoke(raw string) (out string) {
	defer func() {
		if recover() != nil {
			out = respond(map[string]any{"error": "remote core failure"})
		}
	}()
	var q request
	if len(raw) > 16384 || json.Unmarshal([]byte(raw), &q) != nil {
		return respond(map[string]any{"error": "invalid request"})
	}
	v, e := run(q)
	if e != nil {
		return respond(map[string]any{"error": "remote operation unavailable"})
	}
	return respond(v)
}
func run(q request) (any, error) {
	state.Lock()
	defer state.Unlock()
	if q.Op == "version" {
		return map[string]any{"hostApi": 1, "version": "1.104.4"}, nil
	}
	if q.Op == "create" {
		tvPeer = q.Peer
		if state.n != nil {
			return map[string]any{"state": "OFF"}, nil
		}
		if !filepath.IsAbs(q.Dir) {
			return nil, errors.New("state path")
		}
		key, e := base64.StdEncoding.DecodeString(q.Key)
		if e != nil {
			return nil, e
		}
		defer clear(key)
		store, e := newStore(filepath.Join(q.Dir, "sealed"), key)
		if e != nil {
			return nil, e
		}
		envknob.SetNoLogsNoSupport()
		state.n = &node{s: &tsnet.Server{Dir: q.Dir, Store: store, Hostname: "quiettv-tv", Logf: func(string, ...any) {}, UserLogf: func(string, ...any) {}}, relays: make(map[net.Listener]net.Conn)}
		return map[string]any{"state": "OFF", "version": "1.104.4"}, nil
	}
	n := state.n
	if n == nil {
		return nil, errors.New("not initialized")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second)
	defer cancel()
	switch q.Op {
	case "start":
		if !n.started {
			if e := n.s.Start(); e != nil {
				return nil, e
			}
			n.started = true
		}
		lc, e := n.s.LocalClient()
		if e != nil {
			return nil, e
		}
		_, e = lc.EditPrefs(ctx, &ipn.MaskedPrefs{WantRunningSet: true, Prefs: ipn.Prefs{WantRunning: true}})
		if e != nil {
			return nil, e
		}
		return map[string]any{"state": "STARTING"}, nil
	case "status":
		if !n.started {
			return map[string]any{"state": "OFF"}, nil
		}
		lc, e := n.s.LocalClient()
		if e != nil {
			return nil, e
		}
		s, e := lc.StatusWithoutPeers(ctx)
		if e != nil {
			return nil, e
		}
		id := ""
		if s.Self != nil {
			id = string(s.Self.ID)
		}
		return map[string]any{"state": s.BackendState, "nodeId": id, "authUrl": s.AuthURL}, nil
	case "login":
		if !n.started {
			return nil, errors.New("not started")
		}
		lc, e := n.s.LocalClient()
		if e != nil {
			return nil, e
		}
		if e = lc.StartLoginInteractive(ctx); e != nil {
			return nil, e
		}
		return map[string]any{"requested": true}, nil
	case "relay":
		if !n.started {
			return nil, errors.New("not started")
		}
		return n.relay(ctx, q)
	case "logout":
		if !n.started {
			return nil, errors.New("not started")
		}
		lc, e := n.s.LocalClient()
		if e != nil {
			return nil, e
		}
		if e = lc.Logout(ctx); e != nil {
			return nil, e
		}
		return map[string]any{"loggedOut": true}, nil
	case "serve", "offer", "pending", "approve", "deny":
		return tvOperation(n, q)
	case "close":
		if television != nil {
			television.close()
			television = nil
		}
		n.mu.Lock()
		for l, c := range n.relays {
			l.Close()
			c.Close()
		}
		n.relays = make(map[net.Listener]net.Conn)
		n.mu.Unlock()
		if n.started {
			if e := n.s.Close(); e != nil {
				return nil, e
			}
		}
		state.n = nil
		return map[string]any{"state": "OFF"}, nil
	default:
		return nil, errors.New("unsupported operation")
	}
}
func allowedIP(v string) bool {
	ip, e := netip.ParseAddr(v)
	if e != nil {
		return false
	}
	return netip.MustParsePrefix("100.64.0.0/10").Contains(ip) || netip.MustParsePrefix("fd7a:115c:a1e0::/48").Contains(ip)
}
func (n *node) relay(ctx context.Context, q request) (any, error) {
	if !allowedIP(q.IP) || q.Port < 1 || q.Port > 65535 || len(q.Node) < 8 || len(q.Node) > 100 {
		return nil, errors.New("invalid bound endpoint")
	}
	lc, e := n.s.LocalClient()
	if e != nil {
		return nil, e
	}
	who, e := lc.WhoIs(ctx, net.JoinHostPort(q.IP, "0"))
	if e != nil || who == nil || who.Node == nil || string(who.Node.StableID) != q.Node {
		return nil, errors.New("node mismatch")
	}
	remote, e := n.s.Dial(ctx, "tcp", net.JoinHostPort(q.IP, itoa(q.Port)))
	if e != nil {
		return nil, e
	}
	l, e := net.Listen("tcp4", "127.0.0.1:0")
	if e != nil {
		remote.Close()
		return nil, e
	}
	token := make([]byte, 32)
	if _, e = rand.Read(token); e != nil {
		l.Close()
		remote.Close()
		return nil, e
	}
	n.mu.Lock()
	n.relays[l] = remote
	n.mu.Unlock()
	encodedProof := base64.StdEncoding.EncodeToString(token)
	go func() {
		defer func() {
			l.Close()
			remote.Close()
			clear(token)
			n.mu.Lock()
			delete(n.relays, l)
			n.mu.Unlock()
		}()
		l.(*net.TCPListener).SetDeadline(time.Now().Add(10 * time.Second))
		for tries := 0; tries < 8; tries++ {
			local, e := l.Accept()
			if e != nil {
				return
			}
			local.SetDeadline(time.Now().Add(5 * time.Second))
			proof := make([]byte, 32)
			_, e = io.ReadFull(local, proof)
			ok := e == nil && equal(proof, token)
			clear(proof)
			if !ok {
				local.Close()
				continue
			}
			local.SetDeadline(time.Time{})
			l.Close()
			done := make(chan struct{})
			go func() { io.Copy(remote, local); remote.Close(); close(done) }()
			io.Copy(local, remote)
			local.Close()
			<-done
			return
		}
	}()
	return map[string]any{"port": l.Addr().(*net.TCPAddr).Port, "token": encodedProof}, nil
}
func equal(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	var n byte
	for i := range a {
		n |= a[i] ^ b[i]
	}
	return n == 0
}
func itoa(n int) string { b, _ := json.Marshal(n); return string(b) }
