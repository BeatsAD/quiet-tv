package home.quiet.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only library access through the Jellyfin and Emby interfaces. */
final class FeiniuMediaClient implements MediaPosterClient {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String SERVER_INFO = "/System/Info/Public";
    static final int PAGE_SIZE = 50;
    static final int IMAGE_LIMIT = 4 * 1024 * 1024;

    static final class Session {
        final String token, user;
        Session(String token, String user) throws IOException {
            if (!validToken(token) || !validId(user)) {
                throw new IOException("Invalid session");
            }
            this.token = token;
            this.user = user;
        }
    }

    static final class Item {
        final String id, name, type, artwork;
        Item(String id, String name) {
            this(id, name, "");
        }
        Item(String id, String name, String type) {
            this(id, name, type, "");
        }
        Item(String id, String name, String type, String artwork) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.artwork = artwork;
        }
    }

    private final String base, device;
    final boolean jellyfin, embyRoot;

    FeiniuMediaClient(String base, String device, boolean jellyfin) {
        this(base, device, jellyfin, false);
    }

    FeiniuMediaClient(String base, String device, boolean jellyfin, boolean embyRoot) {
        this.base = base;
        this.device = device;
        this.jellyfin = jellyfin;
        this.embyRoot = embyRoot;
    }

    static FeiniuMediaClient discover(String base, String device) throws IOException {
        FeiniuMediaClient compatible = new FeiniuMediaClient(base, device, true);
        JSONObject info = compatible.serverInfo();
        if ("Jellyfin Server".equalsIgnoreCase(info.optString("ProductName"))) return compatible;
        FeiniuMediaClient emby = new FeiniuMediaClient(base, device, false);
        if (isEmby(info) && !isEmby(emby.serverInfo())) {
            return new FeiniuMediaClient(base, device, false, true);
        }
        return emby;
    }

    private static boolean isEmby(JSONObject info) {
        return "Emby Server".equalsIgnoreCase(info.optString("ProductName"))
                || (info.has("Id") && info.has("ServerName") && info.has("Version"));
    }

    private JSONObject serverInfo() throws IOException {
        byte[] data = request(SERVER_INFO, null, null, 32 * 1024);
        if (data == null) return new JSONObject();
        try {
            return new JSONObject(new String(data, UTF8));
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    static String address(String input) throws IOException {
        return address(input, 8005);
    }

    static String address(String input, int defaultPort) throws IOException {
        String value = input.trim();
        if (!value.contains("://")) value = "http://" + value;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme(), path = uri.getPath();
            String host = uri.getHost();
            boolean tls = "https".equalsIgnoreCase(scheme);
            if (host == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getQuery() != null || (!tls && !"http".equalsIgnoreCase(scheme))
                    || !path.matches("(?:/[A-Za-z0-9_-]+)*/?")
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw new IOException("Invalid server address");
            if (!tls && !host.matches("(?:10\\.\\d+|192\\.168|172\\.(?:1[6-9]|2[0-9]|3[01]))\\.\\d+\\.\\d+")
                    && !"127.0.0.1".equals(host)) throw new IOException("Use HTTPS for a non-LAN server");
            int port = uri.getPort() == -1 && !tls ? defaultPort : uri.getPort();
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if ("/v".equals(path)) path = "";
            return new URI(tls ? "https" : "http", null, host, port, path, null, null).toASCIIString();
        } catch (URISyntaxException error) {
            throw new IOException("Invalid server address");
        }
    }

    Session login(String name, String password) throws IOException {
        try {
            JSONObject body = new JSONObject().put("Username", name).put("Pw", password);
            JSONObject result = json(request("/Users/AuthenticateByName", null, body, 1024 * 1024));
            return new Session(result.getString("AccessToken"), result.getJSONObject("User").getString("Id"));
        } catch (JSONException error) {
            throw new IOException("Invalid authentication response");
        }
    }

    public List<Item> libraries(Session session) throws IOException {
        String path = jellyfin ? "/UserViews?userId=" + session.user : "/Users/" + session.user + "/Views";
        return items(json(request(path, session, null, 1024 * 1024)));
    }

    public List<Item> movies(Session session, String library, int offset) throws IOException {
        if (!validId(library)) throw new IOException("Invalid library");
        String query = jellyfin
                ? "/Items?userId=" + session.user + "&parentId=" + library
                    + "&recursive=true&includeItemTypes=Movie&sortBy=SortName&startIndex=" + offset + "&limit=" + PAGE_SIZE
                : "/Users/" + session.user + "/Items?ParentId=" + library
                    + "&Recursive=true&IncludeItemTypes=Movie&SortBy=SortName&StartIndex=" + offset + "&Limit=" + PAGE_SIZE;
        return items(json(request(query, session, null, 2 * 1024 * 1024)));
    }

    @Override public byte[] poster(Session session, Item movie) throws IOException {
        if (!validId(movie.id)) throw new IOException("Invalid movie");
        String path = "/Items/" + movie.id + "/Images/Primary?maxWidth=420&maxHeight=630";
        if (!movie.artwork.isEmpty()) path += "&tag=" + URLEncoder.encode(movie.artwork, UTF8.name());
        return request(path, session, null, IMAGE_LIMIT);
    }

    private static List<Item> items(JSONObject result) throws IOException {
        List<Item> items = new ArrayList<>();
        JSONArray rows = result.optJSONArray("Items");
        if (rows == null) throw new IOException("Invalid catalog");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("Id"), name = row.optString("Name").trim();
            if (validId(id) && !name.isEmpty()) {
                JSONObject tags = row.optJSONObject("ImageTags");
                Object primary = tags == null ? null : tags.opt("Primary");
                items.add(new Item(id, name, row.optString("Type"), primary instanceof String ? (String) primary : ""));
            }
        }
        return items;
    }

    private static JSONObject json(byte[] data) throws IOException {
        try {
            return new JSONObject(new String(data, UTF8));
        } catch (JSONException error) {
            throw new IOException("Invalid media server response");
        }
    }

    static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,100}");
    }

    static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9._~+/=-]{1,2048}");
    }

    private byte[] request(String path, Session session, JSONObject body, int max) throws IOException {
        String prefix = jellyfin || embyRoot || base.endsWith("/emby") ? "" : "/emby";
        HttpURLConnection connection = (HttpURLConnection) new URL(base + prefix + path).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        String client = "Client=\"Quiet TV\", Device=\"Android TV\", DeviceId=\"" + device + "\", Version=\"1.0\"";
        if (session != null) {
            connection.setRequestProperty("X-Emby-Token", session.token);
        }
        // Feiniu requires the legacy headers even on its Jellyfin-compatible routes.
        connection.setRequestProperty("X-Emby-Authorization", "Emby " + client);
        if (jellyfin) {
            String auth = "MediaBrowser " + client;
            if (session != null) auth += ", Token=\"" + session.token + "\"";
            connection.setRequestProperty("Authorization", auth);
        }
        connection.setRequestProperty("Accept", path.contains("/Images/") ? "image/*" : "application/json");
        try {
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] data = body.toString().getBytes(UTF8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }
            }
            int status = connection.getResponseCode();
            if (SERVER_INFO.equals(path)) {
                String type = connection.getContentType();
                if (status != 200 || type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) return null;
            }
            if (status == 404 && path.contains("/Images/")) return null;
            if (status == 401 || status == 403) throw new Unauthorized();
            if (status != 200) throw new IOException("Media server HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                return bounded(input, max);
            }
        } finally {
            connection.disconnect();
        }
    }

    static byte[] bounded(InputStream input, int max) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) != -1) {
            if (result.size() + length > max) throw new IOException("Response too large");
            result.write(buffer, 0, length);
        }
        return result.toByteArray();
    }

    static final class Unauthorized extends IOException {}
}
