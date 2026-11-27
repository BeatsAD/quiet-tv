package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Stores the active movie-wall source and a bounded offline archive for each source. */
final class FeiniuMovieWall {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_POSTERS = 160, MAX_ITEMS = 2000;
    private static final int MAX_CACHE_BYTES = 24 * 1024 * 1024;
    private static final String INDEX = "index.json";
    static final String MEDIA = "feiniu", PLEX = "plex", SMB = "smb";
    private static FeiniuMovieWall instance;

    static synchronized FeiniuMovieWall get(Context context) {
        if (instance == null) instance = new FeiniuMovieWall(context.getApplicationContext());
        return instance;
    }

    interface Result {
        void done(String status, int cachedCount);
    }

    static final class SyncProgress {
        final String library;
        final int processed, total;

        SyncProgress(String library, int processed, int total) {
            this.library = library;
            this.processed = processed;
            this.total = total;
        }

        JSONObject state() throws JSONException {
            return new JSONObject().put("library", library).put("processed", processed).put("total", total);
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final AtomicFile archive;
    private final AtomicFile smbArchive;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Runnable> statusListeners = new ArrayList<>();
    private volatile List<FeiniuMediaClient.Item> libraries = Collections.emptyList();
    private volatile List<String> ids = Collections.emptyList();
    private volatile String library = "", source = "", revision = "", status = "";
    private volatile boolean busy;
    private volatile long operation;
    private volatile String operationProvider = "";
    private volatile SyncProgress syncProgress;
    private volatile boolean smbActive;
    private volatile SmbCache smb;
    private volatile String smbPassword;

    private FeiniuMovieWall(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("feiniu-movie-wall", Context.MODE_PRIVATE);
        archive = new AtomicFile(new File(context.getFilesDir(), "feiniu-movie-posters.zip"));
        smbArchive = new AtomicFile(new File(context.getFilesDir(), "smb-movie-posters.zip"));
        source = prefs.getString("source", "");
        libraries = readLibraries();
        restoreIndex();
        smb = restoreSmb();
        smbActive = smb != null && prefs.getBoolean("smb-active", false);
    }

    boolean connected() { return smbActive || feiniuConnected(); }
    boolean feiniuConnected() { return !source.isEmpty(); }
    String mediaProvider() { return PLEX.equals(prefs.getString("media-provider", MEDIA)) ? PLEX : MEDIA; }
    boolean smbActive() { return smbActive; }
    String smbAddress() { SmbCache cache = smb; return cache == null ? "" : cache.address; }
    String smbName() { SmbCache cache = smb; return cache == null ? "" : cache.username; }
    boolean smbCanSync() { SmbCache cache = smb; return cache != null && (cache.username.isEmpty() || smbPassword != null); }
    String address() { return prefs.getString("address", ""); }
    String name() { return prefs.getString("name", ""); }
    boolean busy() { return busy; }
    long operation() { return operation; }
    String status() { return status; }
    SyncProgress syncProgress() { return syncProgress; }
    String feiniuLibrary() { return library; }
    List<FeiniuMediaClient.Item> libraries() { return libraries; }
    List<String> ids() { return smbActive ? smb.posters.ids : ids; }
    String revision() { return smbActive ? smb.posters.revision : revision; }
    void addListener(Runnable listener) { listeners.add(listener); }
    void removeListener(Runnable listener) { listeners.remove(listener); }
    void addStatusListener(Runnable listener) { statusListeners.add(listener); }
    void removeStatusListener(Runnable listener) { statusListeners.remove(listener); }

    JSONObject state() throws JSONException {
        JSONArray mediaLibraries = new JSONArray();
        for (FeiniuMediaClient.Item item : libraries) {
            mediaLibraries.put(new JSONObject().put("id", item.id).put("name", item.name));
        }
        boolean sharedFolder = smbActive;
        SmbCache cache = smb;
        String mediaProvider = mediaProvider();
        JSONArray choices = sharedFolder ? new JSONArray().put(new JSONObject().put("id", SMB).put("name", "SMB")) : mediaLibraries;
        JSONObject sources = new JSONObject()
                .put(MEDIA, mediaSource(MEDIA.equals(mediaProvider), mediaLibraries))
                .put(PLEX, mediaSource(PLEX.equals(mediaProvider), mediaLibraries))
                .put(SMB, new JSONObject().put("address", smbAddress()).put("username", smbName()).put("canSync", smbCanSync()));
        SyncProgress progress = syncProgress;
        return new JSONObject().put("connected", sharedFolder || feiniuConnected()).put("busy", busy).put("status", status)
                .put("provider", sharedFolder ? SMB : mediaProvider).put("canSync", !sharedFolder || smbCanSync())
                .put("library", sharedFolder ? SMB : library).put("libraries", choices)
                .put("ids", new JSONArray(sharedFolder ? cache.posters.ids : ids))
                .put("revision", sharedFolder ? cache.posters.revision : revision)
                .put("sources", sources).put("operation", operation).put("operationProvider", operationProvider)
                .put("syncProgress", progress == null ? JSONObject.NULL : progress.state());
    }

    private JSONObject mediaSource(boolean selected, JSONArray mediaLibraries) throws JSONException {
        return new JSONObject().put("address", selected ? address() : "").put("username", selected ? name() : "")
                .put("library", selected ? library : "").put("libraries", selected ? mediaLibraries : new JSONArray());
    }

    synchronized boolean connect(String provider, String address, String name, String secret, Result result) {
        boolean plex = PLEX.equals(provider);
        if (busy || (plex ? secret.trim().isEmpty() : name.trim().isEmpty())) return false;
        start(() -> {
            String endpoint = plex ? FeiniuMediaClient.address(address, PlexMovieClient.DEFAULT_PORT)
                    : FeiniuMediaClient.address(address);
            String device = prefs.getString("device", UUID.randomUUID().toString());
            MediaPosterClient client;
            FeiniuMediaClient.Session session;
            boolean jellyfin = false, embyRoot = false;
            if (plex) {
                PlexMovieClient server = new PlexMovieClient(endpoint, device);
                client = server;
                session = server.authorize(secret.trim());
            } else {
                FeiniuMediaClient server = FeiniuMediaClient.discover(endpoint, device);
                client = server;
                session = server.login(name.trim(), secret);
                jellyfin = server.jellyfin;
                embyRoot = server.embyRoot;
            }
            List<FeiniuMediaClient.Item> available = client.libraries(session);
            boolean sameAccount = plex ? session.token.equals(prefs.getString("token", ""))
                    : session.user.equals(prefs.getString("user", ""));
            String key = endpoint.equals(address()) && provider.equals(mediaProvider()) && sameAccount
                    ? source : UUID.randomUUID().toString();
            if (!prefs.edit().putString("source", key).putString("address", endpoint).putString("name", plex ? "" : name.trim())
                    .putString("token", session.token).putString("user", session.user).putString("device", device)
                    .putString("media-provider", provider).putBoolean("jellyfin", jellyfin).putBoolean("emby-root", embyRoot)
                    .putString("libraries", serialize(available)).commit()) throw new IOException("Unable to save source");
            source = key;
            libraries = Collections.unmodifiableList(available);
            restoreIndex();
            status = available.isEmpty() ? "empty-libraries" : "connected";
        }, result, provider, null);
        return true;
    }

    synchronized boolean sync(String id, Result result) {
        if (!smbActive) return syncFeiniu(id, result);
        return SMB.equals(id) && syncSavedSmb(result);
    }

    synchronized boolean syncSavedSmb(Result result) {
        if (!smbCanSync()) return false;
        SmbCache cache = smb;
        return syncSmb(cache.address, cache.username, "", result);
    }

    synchronized boolean syncFeiniu(String id, Result result) {
        if (busy || !feiniuConnected() || !containsLibrary(id)) return false;
        String provider = mediaProvider();
        start(() -> {
            String device = prefs.getString("device", "");
            MediaPosterClient client = PLEX.equals(provider) ? new PlexMovieClient(address(), device)
                    : new FeiniuMediaClient(address(), device, prefs.getBoolean("jellyfin", false),
                            prefs.getBoolean("emby-root", false));
            FeiniuMediaClient.Session session = new FeiniuMediaClient.Session(
                    prefs.getString("token", ""), prefs.getString("user", ""));
            sync(client, session, id);
        }, result, provider, id);
        return true;
    }

    synchronized boolean syncSmb(String address, String username, String password, Result result) {
        if (busy) return false;
        start(() -> {
            SmbMoviePosters.Folder folder = SmbMoviePosters.folder(address);
            String name = username.trim();
            SmbCache previous = smb;
            String savedPassword = smbPassword;
            String secret = password.isEmpty() && savedPassword != null && previous != null
                    && folder.address.equals(previous.address) && name.equals(previous.username) ? savedPassword : password;
            JSONObject index = new JSONObject().put("address", folder.address).put("username", name);
            CachedPosters posters = SmbMoviePosters.read(folder, name, secret, share -> {
                Map<String, String> candidates = new LinkedHashMap<>();
                for (String path : SmbMoviePosters.posters(share, folder.path, MAX_POSTERS))
                    candidates.put(UUID.randomUUID().toString(), path);
                return writeCache(smbArchive, index, candidates.keySet(), id -> SmbMoviePosters.poster(share, candidates.get(id)));
            });
            if (posters == null) {
                status = "smb-empty";
                return;
            }
            if (!smbActive && !prefs.edit().putBoolean("smb-active", true).commit())
                throw new IOException("Unable to save active source");
            smb = new SmbCache(folder.address, name, posters);
            smbPassword = secret;
            smbActive = true;
            status = "synced";
        }, result, SMB, SMB);
        return true;
    }

    private void sync(MediaPosterClient client, FeiniuMediaClient.Session session, String id) throws IOException, JSONException {
        Map<String, FeiniuMediaClient.Item> movies = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int offset = 0; offset < MAX_ITEMS && movies.size() < MAX_POSTERS; offset += FeiniuMediaClient.PAGE_SIZE) {
            List<FeiniuMediaClient.Item> page = client.movies(session, id, offset);
            int previous = seen.size();
            for (FeiniuMediaClient.Item item : page) {
                if (seen.add(item.id) && "Movie".equalsIgnoreCase(item.type)) movies.put(item.id, item);
            }
            if (page.size() < FeiniuMediaClient.PAGE_SIZE || seen.size() == previous) break;
        }
        if (movies.isEmpty()) {
            status = "empty-movies";
            return;
        }
        JSONObject index = new JSONObject().put("source", source).put("library", id);
        CachedPosters posters = writeCache(archive, index, movies.keySet(), movie -> client.poster(session, movies.get(movie)));
        if (posters == null) {
            status = "empty-posters";
            return;
        }
        if (smbActive && !prefs.edit().putBoolean("smb-active", false).commit())
            throw new IOException("Unable to save active source");
        library = id;
        revision = posters.revision;
        ids = posters.ids;
        smbActive = false;
        status = "synced";
    }

    private interface ReadImage { byte[] read(String id) throws IOException; }

    private CachedPosters writeCache(AtomicFile destination, JSONObject index, Set<String> candidates,
                                     ReadImage image) throws IOException {
        if (candidates.isEmpty()) return null;
        String syncingLibrary = syncProgress.library;
        int total = candidates.size();
        int processed = 0;
        syncProgress = new SyncProgress(syncingLibrary, processed, total);
        main.post(this::notifyStatus);
        long lastNotice = SystemClock.elapsedRealtime();
        FileOutputStream output = destination.startWrite();
        List<String> downloaded = new ArrayList<>();
        String nextRevision = UUID.randomUUID().toString();
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(output) {
                @Override public void close() throws IOException { flush(); }
            })) {
                int totalBytes = 0;
                for (String id : candidates) {
                    if (downloaded.size() >= MAX_POSTERS) break;
                    byte[] poster = jpeg(image.read(id));
                    if (poster != null) {
                        if (totalBytes + poster.length > MAX_CACHE_BYTES) break;
                        put(zip, id + ".jpg", poster);
                        downloaded.add(id);
                        totalBytes += poster.length;
                    }
                    syncProgress = new SyncProgress(syncingLibrary, ++processed, total);
                    // The phone polls the latest count. Limit updates to native settings views.
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastNotice >= 250) {
                        lastNotice = now;
                        main.post(this::notifyStatus);
                    }
                }
                put(zip, INDEX, index.put("revision", nextRevision).put("ids", new JSONArray(downloaded)).toString().getBytes(UTF8));
            }
            if (downloaded.isEmpty()) {
                destination.failWrite(output);
                return null;
            }
            destination.finishWrite(output);
        } catch (JSONException error) {
            destination.failWrite(output);
            throw new IOException("Unable to write the poster index", error);
        } catch (IOException | RuntimeException error) {
            destination.failWrite(output);
            throw error;
        }
        return new CachedPosters(nextRevision, downloaded);
    }

    private boolean containsLibrary(String id) {
        for (FeiniuMediaClient.Item item : libraries) if (item.id.equals(id)) return true;
        return false;
    }

    private interface Task { void run() throws IOException, JSONException; }

    private void start(Task task, Result result, String provider, String syncingLibrary) {
        boolean sharedFolder = SMB.equals(provider);
        status = "working";
        operation++;
        operationProvider = provider;
        syncProgress = syncingLibrary == null ? null : new SyncProgress(syncingLibrary, 0, 0);
        busy = true;
        publish();
        worker.execute(() -> {
            try {
                task.run();
            } catch (FeiniuMediaClient.Unauthorized error) {
                status = sharedFolder ? "smb-auth" : PLEX.equals(provider) ? "plex-auth" : "unauthorized";
            } catch (SmbMoviePosters.InvalidFolder error) {
                status = "smb-folder";
            } catch (IOException | JSONException | RuntimeException error) {
                status = sharedFolder ? "smb-failed" : "failed";
            } finally {
                synchronized (this) {
                    busy = false;
                    publish();
                    if (result != null) {
                        String completedStatus = status;
                        int cachedCount = ids().size();
                        main.post(() -> result.done(completedStatus, cachedCount));
                    }
                }
            }
        });
    }

    private void publish() {
        main.post(() -> {
            notifyStatus();
            for (Runnable listener : new ArrayList<>(listeners)) listener.run();
            PhoneSettingsSession.get(context).configurationChanged();
        });
    }

    private void notifyStatus() {
        for (Runnable listener : new ArrayList<>(statusListeners)) listener.run();
    }

    private List<FeiniuMediaClient.Item> readLibraries() {
        try {
            JSONArray data = new JSONArray(prefs.getString("libraries", "[]"));
            List<FeiniuMediaClient.Item> items = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                items.add(new FeiniuMediaClient.Item(item.getString("id"), item.getString("name")));
            }
            return Collections.unmodifiableList(items);
        } catch (JSONException error) {
            return Collections.emptyList();
        }
    }

    private static String serialize(List<FeiniuMediaClient.Item> items) throws JSONException {
        JSONArray data = new JSONArray();
        for (FeiniuMediaClient.Item item : items) data.put(new JSONObject().put("id", item.id).put("name", item.name));
        return data.toString();
    }

    private void restoreIndex() {
        library = "";
        revision = "";
        ids = Collections.emptyList();
        if (!feiniuConnected()) return;
        try {
            JSONObject index = readIndex(archive);
            if (!source.equals(index.getString("source"))) return;
            String selected = index.getString("library");
            if (!containsLibrary(selected)) return;
            CachedPosters restored = CachedPosters.from(index);
            library = selected;
            revision = restored.revision;
            ids = restored.ids;
        } catch (IOException | JSONException ignored) {
        }
    }

    private SmbCache restoreSmb() {
        try {
            JSONObject index = readIndex(smbArchive);
            String address = SmbMoviePosters.folder(index.getString("address")).address;
            CachedPosters posters = CachedPosters.from(index);
            return posters.ids.isEmpty() ? null : new SmbCache(address, index.getString("username"), posters);
        } catch (IOException | JSONException ignored) {
            return null;
        }
    }

    private static JSONObject readIndex(AtomicFile archive) throws IOException, JSONException {
        return new JSONObject(new String(readZip(archive, INDEX, 65536), UTF8));
    }

    private static final class CachedPosters {
        final String revision;
        final List<String> ids;

        CachedPosters(String revision, List<String> ids) {
            this.revision = revision;
            this.ids = Collections.unmodifiableList(ids);
        }

        static CachedPosters from(JSONObject index) throws JSONException {
            JSONArray entries = index.getJSONArray("ids");
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                String id = entries.getString(i);
                if (FeiniuMediaClient.validId(id)) ids.add(id);
            }
            return new CachedPosters(index.getString("revision"), ids);
        }
    }

    private static final class SmbCache {
        final String address, username;
        final CachedPosters posters;

        SmbCache(String address, String username, CachedPosters posters) {
            this.address = address;
            this.username = username;
            this.posters = posters;
        }
    }

    byte[] posterBytes(String id) throws IOException {
        boolean sharedFolder = smbActive;
        List<String> available = sharedFolder ? smb.posters.ids : ids;
        if (!FeiniuMediaClient.validId(id) || !available.contains(id)) throw new IOException("Poster unavailable");
        return readZip(sharedFolder ? smbArchive : archive, id + ".jpg", FeiniuMediaClient.IMAGE_LIMIT);
    }

    Bitmap bitmap(String id) throws IOException {
        byte[] data = posterBytes(id);
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    private static byte[] readZip(AtomicFile archive, String name, int limit) throws IOException {
        try (ZipFile zip = new ZipFile(archive.getBaseFile())) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) throw new IOException("Poster unavailable");
            try (InputStream input = zip.getInputStream(entry)) {
                return FeiniuMediaClient.bounded(input, limit);
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] jpeg(byte[] data) {
        if (data == null || data.length == 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 768) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) return null;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            return bitmap.compress(Bitmap.CompressFormat.JPEG, 83, output) ? output.toByteArray() : null;
        } finally {
            bitmap.recycle();
        }
    }
}
