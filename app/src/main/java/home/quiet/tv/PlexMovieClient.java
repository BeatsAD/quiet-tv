package home.quiet.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** Read-only movie libraries and artwork on one Plex Media Server. */
final class PlexMovieClient implements MediaPosterClient {
    static final int DEFAULT_PORT = 32400;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final String base, device;

    PlexMovieClient(String base, String device) {
        this.base = base;
        this.device = device;
    }

    FeiniuMediaClient.Session authorize(String token) throws IOException {
        if (!FeiniuMediaClient.validToken(token)) throw new FeiniuMediaClient.Unauthorized();
        String server = container(request("/identity", null, false, 16 * 1024, -1)).optString("machineIdentifier");
        return new FeiniuMediaClient.Session(token, server);
    }

    @Override public List<FeiniuMediaClient.Item> libraries(FeiniuMediaClient.Session session) throws IOException {
        JSONArray sections = entries("/library/sections", session, "Directory", -1);
        List<FeiniuMediaClient.Item> libraries = new ArrayList<>();
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null || !"movie".equals(section.optString("type"))) continue;
            String key = section.optString("key"), title = section.optString("title").trim();
            if (numericId(key) && !title.isEmpty()) libraries.add(new FeiniuMediaClient.Item(key, title));
        }
        return libraries;
    }

    @Override public List<FeiniuMediaClient.Item> movies(FeiniuMediaClient.Session session, String library, int offset)
            throws IOException {
        if (!numericId(library)) throw new IOException("Invalid Plex library");
        JSONArray entries = entries("/library/sections/" + library + "/all", session, "Metadata", offset);
        List<FeiniuMediaClient.Item> movies = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            String key = entry.optString("ratingKey"), title = entry.optString("title").trim();
            if (numericId(key) && !title.isEmpty()) {
                movies.add(new FeiniuMediaClient.Item(key, title, entry.optString("type"), entry.optString("thumb")));
            }
        }
        return movies;
    }

    @Override public byte[] poster(FeiniuMediaClient.Session session, FeiniuMediaClient.Item movie) throws IOException {
        if (!numericId(movie.id)
                || !movie.artwork.matches("/library/metadata/" + movie.id + "/(?:thumb|poster)/[0-9]{1,20}")) return null;
        return request(movie.artwork, session, true, FeiniuMediaClient.IMAGE_LIMIT, -1);
    }

    private JSONArray entries(String path, FeiniuMediaClient.Session session, String key, int offset) throws IOException {
        JSONObject result = container(request(path, session, false, 2 * 1024 * 1024, offset));
        JSONArray entries = result.optJSONArray(key);
        if (entries != null) return entries;
        if (result.optInt("size", -1) == 0) return new JSONArray();
        throw new IOException("Invalid Plex library response");
    }

    private static JSONObject container(byte[] bytes) throws IOException {
        try {
            return new JSONObject(new String(bytes, UTF8)).getJSONObject("MediaContainer");
        } catch (JSONException error) {
            throw new IOException("Invalid Plex server response");
        }
    }

    private static boolean numericId(String id) {
        return id.matches("[0-9]{1,20}");
    }

    private byte[] request(String path, FeiniuMediaClient.Session session, boolean image, int limit, int offset)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("X-Plex-Product", "Quiet TV");
        connection.setRequestProperty("X-Plex-Client-Identifier", device);
        connection.setRequestProperty("Accept", image ? "image/*" : "application/json");
        if (session != null) connection.setRequestProperty("X-Plex-Token", session.token);
        if (offset >= 0) {
            connection.setRequestProperty("X-Plex-Container-Start", Integer.toString(offset));
            connection.setRequestProperty("X-Plex-Container-Size", Integer.toString(FeiniuMediaClient.PAGE_SIZE));
        }
        try {
            int status = connection.getResponseCode();
            if (status == 404 && image) return null;
            if (status == 401 || status == 403) throw new FeiniuMediaClient.Unauthorized();
            if (status != 200) throw new IOException("Plex server HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                return FeiniuMediaClient.bounded(input, limit);
            }
        } finally {
            connection.disconnect();
        }
    }
}
