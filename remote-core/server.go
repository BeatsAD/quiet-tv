package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math/big"
	"net"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"

	"tailscale.com/ipn"
)

type tvBinding struct {
	ID         string `json:"id"`
	Credential []byte `json:"credential"`
	Client     string `json:"client"`
}
type tvSecrets struct {
	ID       string               `json:"id"`
	Key      []byte               `json:"key"`
	Bindings map[string]tvBinding `json:"bindings"`
}
type tvPair struct {
	Ticket, Session, Request, Client, Name string
	Approved, Denied                       bool
	Binding                                tvBinding
}
type tvServer struct {
	mu              sync.Mutex
	node            *node
	peer            map[string]any
	store           ipn.StateStore
	secrets         tvSecrets
	cert            tls.Certificate
	lan             net.Listener
	tail            net.Listener
	http            *http.Server
	session, secret string
	expires         time.Time
	pending         map[string]*tvPair
	bridges         map[string]map[net.Conn]bool
	port, adbPort   int
	ip              string
}

var television *tvServer
var tvPeer map[string]any

func opaque() string {
	b := make([]byte, 24)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}
func sameSecret(a, b string) bool {
	return len(a) == len(b) && subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}
func (s *tvServer) save() error {
	b, e := json.Marshal(s.secrets)
	if e != nil {
		return e
	}
	return s.store.WriteState(ipn.StateKey("quiettv-remote-service-v1"), b)
}
func (s *tvServer) identity(request string) map[string]any {
	r := map[string]any{"protocol": 1, "requestId": request, "tvId": s.secrets.ID}
	for k, v := range s.peer {
		r[k] = v
	}
	return r
}
func (s *tvServer) status() (string, string, error) {
	lc, e := s.node.s.LocalClient()
	if e != nil {
		return "", "", e
	}
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	v, e := lc.StatusWithoutPeers(ctx)
	if e != nil || v.Self == nil || v.BackendState != "Running" {
		return "", "", errors.New("node not running")
	}
	for _, ip := range v.TailscaleIPs {
		if ip.Is4() {
			return string(v.Self.ID), ip.String(), nil
		}
	}
	return "", "", errors.New("node address unavailable")
}
func (s *tvServer) contextMatches(raw string) bool {
	b, e := base64.StdEncoding.DecodeString(raw)
	if e != nil || len(b) > 4096 {
		return false
	}
	var p map[string]any
	if json.Unmarshal(b, &p) != nil {
		return false
	}
	a, _ := json.Marshal(p)
	expected, _ := json.Marshal(s.peer)
	return string(a) == string(expected)
}
func (s *tvServer) reply(w http.ResponseWriter, r *http.Request, code int, fields map[string]any) {
	out := s.identity(r.Header.Get("X-QuietTV-Request"))
	for k, v := range fields {
		out[k] = v
	}
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(out)
}
func (s *tvServer) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if len(r.Header.Get("X-QuietTV-Request")) != 36 || !s.contextMatches(r.Header.Get("X-QuietTV-Context")) {
		s.reply(w, r, 412, map[string]any{"result": "REJECTED"})
		return
	}
	if r.URL.RawQuery != "" || r.URL.Path != r.URL.EscapedPath() {
		s.reply(w, r, 400, map[string]any{"result": "REJECTED"})
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if r.Method == "POST" && (r.URL.Path == "/quiettv-mobile/v1/bindings" || r.URL.Path == "/quiettv-mobile/v1/bindings/status") {
		s.pair(w, r)
		return
	}
	token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
	var binding tvBinding
	for _, b := range s.secrets.Bindings {
		if sameSecret(token, base64.StdEncoding.EncodeToString(b.Credential)) {
			binding = b
			break
		}
	}
	if binding.ID == "" {
		s.reply(w, r, 403, map[string]any{"result": "REJECTED"})
		return
	}
	switch {
	case r.Method == "GET" && r.URL.Path == "/quiettv-mobile/v1/remote/status":
		nodeID, ip, e := s.status()
		if e != nil {
			s.reply(w, r, 503, map[string]any{"enabled": false})
			return
		}
		s.reply(w, r, 200, map[string]any{"bindingId": binding.ID, "enabled": true, "nodeId": nodeID, "tailscaleIp": ip, "servicePort": 42391})
	case r.Method == "DELETE" && r.URL.Path == "/quiettv-mobile/v1/bindings/"+binding.ID:
		delete(s.secrets.Bindings, binding.ID)
		if e := s.save(); e != nil {
			s.secrets.Bindings[binding.ID] = binding
			s.reply(w, r, 500, map[string]any{"revoked": false})
			return
		}
		for c := range s.bridges[binding.ID] {
			c.Close()
		}
		delete(s.bridges, binding.ID)
		s.reply(w, r, 200, map[string]any{"bindingId": binding.ID, "revoked": true})
	case r.Method == "CONNECT" && r.URL.Path == "/quiettv-mobile/v1/remote/adb":
		s.bridge(w, r, binding)
	default:
		s.reply(w, r, 404, map[string]any{"result": "UNSUPPORTED"})
	}
}
func (s *tvServer) pair(w http.ResponseWriter, r *http.Request) {
	if time.Now().After(s.expires) || !sameSecret(r.Header.Get("Authorization"), "Pairing "+s.secret) {
		s.reply(w, r, 403, map[string]any{"result": "REJECTED"})
		return
	}
	var body struct {
		Session  string   `json:"sessionId"`
		Ticket   string   `json:"ticket"`
		Public   string   `json:"clientPublicKey"`
		Proof    string   `json:"clientProof"`
		Name     string   `json:"clientName"`
		Audience string   `json:"audience"`
		Scopes   []string `json:"requestedScopes"`
	}
	if json.NewDecoder(http.MaxBytesReader(w, r.Body, 16384)).Decode(&body) != nil || body.Session != s.session {
		s.reply(w, r, 400, map[string]any{"result": "REJECTED"})
		return
	}
	var p *tvPair
	if r.URL.Path == "/quiettv-mobile/v1/bindings/status" {
		p = s.pending[body.Ticket]
	} else {
		public, e := base64.StdEncoding.DecodeString(body.Public)
		proof, pe := base64.StdEncoding.DecodeString(body.Proof)
		if e != nil || pe != nil || len(public) > 1024 || len(proof) > 256 || body.Audience != "remote" || len(body.Scopes) != 2 {
			s.reply(w, r, 400, map[string]any{"result": "REJECTED"})
			return
		}
		sort.Strings(body.Scopes)
		if strings.Join(body.Scopes, ",") != "remote.bridge,remote.read" {
			s.reply(w, r, 403, map[string]any{"result": "REJECTED"})
			return
		}
		key, e := x509.ParsePKIXPublicKey(public)
		ec, ok := key.(*ecdsa.PublicKey)
		if e != nil || !ok || ec.Curve != elliptic.P256() {
			s.reply(w, r, 403, map[string]any{"result": "REJECTED"})
			return
		}
		fingerprint := sha256.Sum256(public)
		client := hex.EncodeToString(fingerprint[:])
		challenge := fmt.Sprintf("quiettv-mobile-binding-v1\nremote\n%s\n%s\n%.0f\n%s\nremote.bridge,remote.read\n%s", s.session, s.secrets.ID, s.peer["uid"], client, r.Header.Get("X-QuietTV-Request"))
		digest := sha256.Sum256([]byte(challenge))
		if !ecdsa.VerifyASN1(ec, digest[:], proof) {
			s.reply(w, r, 403, map[string]any{"result": "REJECTED"})
			return
		}
		for _, candidate := range s.pending {
			if candidate.Request == r.Header.Get("X-QuietTV-Request") {
				p = candidate
				break
			}
		}
		if p == nil {
			if len(s.pending) >= 4 || len(s.secrets.Bindings) >= 8 {
				s.reply(w, r, 429, map[string]any{"result": "REJECTED"})
				return
			}
			name := strings.Map(func(r rune) rune {
				if r < 32 {
					return -1
				}
				return r
			}, body.Name)
			if len(name) > 120 {
				name = "QuietTV 手机助手"
			}
			p = &tvPair{Ticket: opaque(), Session: s.session, Request: r.Header.Get("X-QuietTV-Request"), Client: client, Name: name}
			s.pending[p.Ticket] = p
		}
	}
	if p == nil || p.Session != s.session || p.Denied {
		s.reply(w, r, 403, map[string]any{"result": "REJECTED"})
		return
	}
	if !p.Approved {
		s.reply(w, r, 202, map[string]any{"result": "PENDING", "ticket": p.Ticket})
		return
	}
	s.reply(w, r, 201, map[string]any{"result": "BOUND", "bindingId": p.Binding.ID, "credential": base64.StdEncoding.EncodeToString(p.Binding.Credential), "audience": "remote", "grantedScopes": []string{"remote.bridge", "remote.read"}})
}
func verifyAdbd(port int) error {
	if port < 1 || port > 65535 {
		return errors.New("invalid adbd port")
	}
	c, e := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 2*time.Second)
	if e != nil {
		return e
	}
	defer c.Close()
	c.SetDeadline(time.Now().Add(3 * time.Second))
	payload := []byte("host::quiettv-probe\x00")
	packet := make([]byte, 24+len(payload))
	binary.LittleEndian.PutUint32(packet, 0x4e584e43)
	binary.LittleEndian.PutUint32(packet[4:], 0x01000000)
	binary.LittleEndian.PutUint32(packet[8:], 4096)
	binary.LittleEndian.PutUint32(packet[12:], uint32(len(payload)))
	var sum uint32
	for _, b := range payload {
		sum += uint32(b)
	}
	binary.LittleEndian.PutUint32(packet[16:], sum)
	binary.LittleEndian.PutUint32(packet[20:], 0x4e584e43^0xffffffff)
	copy(packet[24:], payload)
	if _, e = c.Write(packet); e != nil {
		return e
	}
	header := make([]byte, 24)
	if _, e = io.ReadFull(c, header); e != nil {
		return e
	}
	command := binary.LittleEndian.Uint32(header)
	size := binary.LittleEndian.Uint32(header[12:])
	if command^binary.LittleEndian.Uint32(header[20:]) != 0xffffffff || size > 4096 {
		return errors.New("not adbd")
	}
	body := make([]byte, size)
	if _, e = io.ReadFull(c, body); e != nil {
		return e
	}
	if command == 0x534c5453 && size == 0 {
		return nil
	}
	if command == 0x48545541 && size == 20 && binary.LittleEndian.Uint32(header[4:]) == 1 {
		return nil
	}
	if command == 0x4e584e43 && strings.HasPrefix(string(body), "device:") {
		return nil
	}
	return errors.New("not an authenticated adbd endpoint")
}
func (s *tvServer) bridge(w http.ResponseWriter, r *http.Request, b tvBinding) {
	if r.Header.Get("Upgrade") != "quiettv-adb-v1" || len(s.bridges[b.ID]) >= 4 || verifyAdbd(s.adbPort) != nil {
		s.reply(w, r, 503, map[string]any{"result": "ADB_UNAVAILABLE"})
		return
	}
	adb, e := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", s.adbPort), 3*time.Second)
	if e != nil {
		s.reply(w, r, 503, map[string]any{"result": "ADB_UNAVAILABLE"})
		return
	}
	h, ok := w.(http.Hijacker)
	if !ok {
		adb.Close()
		return
	}
	client, rw, e := h.Hijack()
	if e != nil {
		adb.Close()
		return
	}
	client.SetDeadline(time.Time{})
	if s.bridges[b.ID] == nil {
		s.bridges[b.ID] = map[net.Conn]bool{}
	}
	s.bridges[b.ID][client] = true
	s.bridges[b.ID][adb] = true
	fmt.Fprintf(rw, "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: quiettv-adb-v1\r\nX-QuietTV-Request: %s\r\nX-QuietTV-Binding: %s\r\nX-QuietTV-Tv: %s\r\nX-QuietTV-Context: %s\r\n\r\n", r.Header.Get("X-QuietTV-Request"), b.ID, s.secrets.ID, r.Header.Get("X-QuietTV-Context"))
	if rw.Flush() != nil {
		client.Close()
		adb.Close()
		delete(s.bridges[b.ID], client)
		delete(s.bridges[b.ID], adb)
		return
	}
	go func() {
		defer func() {
			client.Close()
			adb.Close()
			s.mu.Lock()
			delete(s.bridges[b.ID], client)
			delete(s.bridges[b.ID], adb)
			s.mu.Unlock()
		}()
		done := make(chan struct{})
		go func() { io.Copy(client, adb); client.Close(); close(done) }()
		io.Copy(adb, rw)
		adb.Close()
		<-done
	}()
}
func (s *tvServer) close() {
	s.http.Close()
	s.lan.Close()
	s.tail.Close()
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, conns := range s.bridges {
		for c := range conns {
			c.Close()
		}
	}
}
func newTVServer(n *node, peer map[string]any, port int) (*tvServer, error) {
	if verifyAdbd(port) != nil {
		return nil, errors.New("local adbd unavailable")
	}
	s := &tvServer{node: n, peer: peer, store: n.s.Store, adbPort: port, pending: map[string]*tvPair{}, bridges: map[string]map[net.Conn]bool{}}
	_, ip, e := s.status()
	if e != nil {
		return nil, e
	}
	s.ip = ip
	saved, e := s.store.ReadState(ipn.StateKey("quiettv-remote-service-v1"))
	if e == nil {
		if json.Unmarshal(saved, &s.secrets) != nil {
			return nil, errors.New("invalid remote state")
		}
	} else if errors.Is(e, ipn.ErrStateNotExist) {
		key, e := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		if e != nil {
			return nil, e
		}
		raw, e := x509.MarshalPKCS8PrivateKey(key)
		if e != nil {
			return nil, e
		}
		s.secrets = tvSecrets{ID: opaque(), Key: raw, Bindings: map[string]tvBinding{}}
		if e = s.save(); e != nil {
			return nil, e
		}
	} else {
		return nil, e
	}
	key, e := x509.ParsePKCS8PrivateKey(s.secrets.Key)
	if e != nil {
		return nil, e
	}
	ec, ok := key.(*ecdsa.PrivateKey)
	if !ok {
		return nil, errors.New("invalid certificate key")
	}
	serial, e := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if e != nil {
		return nil, e
	}
	certificate := x509.Certificate{SerialNumber: serial, Subject: pkix.Name{CommonName: "QuietTV remote"}, NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(365 * 24 * time.Hour), KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}, IPAddresses: []net.IP{net.ParseIP(ip)}}
	interfaces, _ := net.InterfaceAddrs()
	for _, a := range interfaces {
		if p, _, e := net.ParseCIDR(a.String()); e == nil && !p.IsLoopback() && p.To4() != nil {
			certificate.IPAddresses = append(certificate.IPAddresses, p)
		}
	}
	der, e := x509.CreateCertificate(rand.Reader, &certificate, &certificate, &ec.PublicKey, ec)
	if e != nil {
		return nil, e
	}
	s.cert = tls.Certificate{Certificate: [][]byte{der}, PrivateKey: ec}
	s.lan, e = net.Listen("tcp4", "0.0.0.0:0")
	if e != nil {
		return nil, e
	}
	s.port = s.lan.Addr().(*net.TCPAddr).Port
	s.tail, e = n.s.Listen("tcp", ":42391")
	if e != nil {
		s.lan.Close()
		return nil, e
	}
	s.http = &http.Server{Handler: s, ReadHeaderTimeout: 8 * time.Second, ReadTimeout: 15 * time.Second, WriteTimeout: 15 * time.Second, IdleTimeout: 15 * time.Second, MaxHeaderBytes: 16384, ErrorLog: log.New(io.Discard, "", 0)}
	config := &tls.Config{MinVersion: tls.VersionTLS12, Certificates: []tls.Certificate{s.cert}}
	go s.http.Serve(tls.NewListener(s.lan, config))
	go s.http.Serve(tls.NewListener(s.tail, config))
	return s, nil
}
func tvOperation(n *node, q request) (any, error) {
	if q.Op == "serve" || q.Op == "offer" {
		if television == nil {
			var e error
			television, e = newTVServer(n, tvPeer, q.Port)
			if e != nil {
				return nil, e
			}
		}
		if q.Op == "serve" {
			return map[string]any{"ready": true}, nil
		}
		s := television
		s.mu.Lock()
		defer s.mu.Unlock()
		ip := net.ParseIP(q.IP)
		found := false
		addresses, _ := net.InterfaceAddrs()
		for _, a := range addresses {
			p, _, _ := net.ParseCIDR(a.String())
			if p.Equal(ip) && !p.IsLoopback() {
				found = true
			}
		}
		if !found {
			return nil, errors.New("not a local interface")
		}
		s.session = opaque()
		secret := make([]byte, 32)
		if _, e := rand.Read(secret); e != nil {
			return nil, e
		}
		s.secret = base64.StdEncoding.EncodeToString(secret)
		s.expires = time.Now().Add(3 * time.Minute)
		s.pending = map[string]*tvPair{}
		parsed, _ := x509.ParseCertificate(s.cert.Certificate[0])
		pin := sha256.Sum256(parsed.RawSubjectPublicKeyInfo)
		result := s.identity("")
		delete(result, "requestId")
		result["kind"] = "quiettv-service-binding"
		result["audience"] = "remote"
		result["origin"] = fmt.Sprintf("https://%s:%d", q.IP, s.port)
		result["certificateSpkiSha256"] = hex.EncodeToString(pin[:])
		result["sessionId"] = s.session
		result["oneTimeSecret"] = s.secret
		result["expiresAt"] = s.expires.UnixMilli()
		return result, nil
	}
	if television == nil {
		return map[string]any{"requests": []any{}}, nil
	}
	s := television
	s.mu.Lock()
	defer s.mu.Unlock()
	if q.Op == "pending" {
		requests := []any{}
		if time.Now().Before(s.expires) {
			for _, p := range s.pending {
				if !p.Approved && !p.Denied {
					requests = append(requests, map[string]any{"ticket": p.Ticket, "name": p.Name, "fingerprint": p.Client})
				}
			}
		}
		return map[string]any{"requests": requests}, nil
	}
	p := s.pending[q.Node]
	if p == nil || p.Denied || time.Now().After(s.expires) {
		return nil, errors.New("expired request")
	}
	if p.Approved {
		return map[string]any{"approved": true}, nil
	}
	if q.Op == "deny" {
		p.Denied = true
		return map[string]any{"denied": true}, nil
	}
	credential := make([]byte, 32)
	if _, e := rand.Read(credential); e != nil {
		return nil, e
	}
	p.Binding = tvBinding{ID: opaque(), Credential: credential, Client: p.Client}
	s.secrets.Bindings[p.Binding.ID] = p.Binding
	if e := s.save(); e != nil {
		delete(s.secrets.Bindings, p.Binding.ID)
		return nil, e
	}
	p.Approved = true
	return map[string]any{"approved": true}, nil
}
