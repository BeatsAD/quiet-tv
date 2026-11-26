package home.quiet.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

final class TopShelfContent {
    private static final int MAX_FEED_BYTES = 512 * 1024;
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;
    private static final String BILIBILI_URL = "https://api.bilibili.com/x/web-interface/popular?ps=6&pn=1";
    private static final String DOUYU_URL = "https://www.douyu.com/japi/weblist/apinc/allpage/6/1";

    enum Source {
        BILIBILI("哔哩哔哩热门", "Bilibili popular", "B 站（云视听小电视）", "Bilibili (TV)"),
        DOUYU("斗鱼直播", "Douyu live", "斗鱼", "Douyu"),
        TENCENT("云视听极光精选", "Tencent Video picks", "云视听极光", "Tencent Video"),
        IQIYI("银河奇异果精选", "iQIYI picks", "银河奇异果", "iQIYI"),
        YOUKU("酷喵精选", "Youku picks", "酷喵", "Youku"),
        MANGO("芒果 TV 精选", "Mango TV picks", "芒果 TV", "Mango TV"),
        NETFLIX("Netflix 精选", "Netflix picks", "Netflix", "Netflix");

        final String chinese;
        final String english;
        final String appName;
        final String englishAppName;

        Source(String chinese, String english, String appName, String englishAppName) {
            this.chinese = chinese;
            this.english = english;
            this.appName = appName;
            this.englishAppName = englishAppName;
        }
    }

    static List<String> supportedApps(boolean chinese) {
        List<String> apps = new ArrayList<>();
        for (Source source : Source.values()) apps.add(chinese ? source.appName : source.englishAppName);
        return apps;
    }

    static Source source(String pkg) {
        switch (pkg) {
            case "com.xiaodianshi.tv.yst":
            case "com.tutu.myblbl": return Source.BILIBILI;
            case "com.douyu.xl.douyutv": return Source.DOUYU;
            case "com.ktcp.video": return Source.TENCENT;
            case "com.gitvdemo.video": return Source.IQIYI;
            case "com.cibn.tv": return Source.YOUKU;
            case "com.starcor.mango": return Source.MANGO;
            case "com.netflix.ninja": return Source.NETFLIX;
            default: return null;
        }
    }

    static final class Card {
        final String title;
        final String source;
        final Bitmap image;

        Card(String title, String source, Bitmap image) {
            this.title = title;
            this.source = source;
            this.image = image;
        }
    }

    private final Context context;
    private final boolean chinese;
    private final NetworkRequest requests = new NetworkRequest();

    TopShelfContent(Context context, boolean chinese) {
        this.context = context.getApplicationContext();
        this.chinese = chinese;
    }

    void cancel() {
        requests.cancel();
    }

    Card load(Source source) throws IOException, JSONException {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        List<Entry> entries = source == Source.BILIBILI || source == Source.DOUYU ?
                live(source, deadline) : curated(source);
        IOException last = null;
        for (Entry entry : entries) {
            checkActive(deadline);
            try {
                Bitmap image = decode(fetch(entry.image, null, MAX_IMAGE_BYTES, deadline));
                return new Card(entry.title, chinese ? source.chinese : source.english, image);
            } catch (IOException error) {
                last = error;
            }
        }
        if (last != null) throw last;
        return null;
    }

    private List<Entry> live(Source source, long deadline) throws IOException, JSONException {
        boolean bilibili = source == Source.BILIBILI;
        String address = bilibili ? BILIBILI_URL : DOUYU_URL;
        String referer = bilibili ? null : "https://www.douyu.com/";
        JSONObject root = new JSONObject(new String(fetch(address, referer, MAX_FEED_BYTES, deadline), Charsets.UTF_8));
        if (root.optInt(bilibili ? "code" : "error", -1) != 0) throw new IOException("Preview feed unavailable");
        JSONArray rows = root.getJSONObject("data").getJSONArray(bilibili ? "list" : "rl");
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < rows.length() && entries.size() < 3; index++) {
            JSONObject row = rows.optJSONObject(index);
            if (row == null) continue;
            String title = row.optString(bilibili ? "title" : "rn").trim();
            String image = row.optString(bilibili ? "pic" : "rs16");
            if (image.startsWith("http://")) image = "https://" + image.substring(7);
            if (title.isEmpty() || image.isEmpty()) continue;
            URL url;
            try {
                url = https(image);
            } catch (IOException invalid) {
                continue;
            }
            String host = url.getHost();
            if (bilibili && !host.endsWith(".hdslb.com")) continue;
            if (!bilibili && !host.endsWith(".douyucdn.cn") && !host.endsWith(".douyucdn2.cn")) continue;
            if (bilibili) image += "@1280w_720h_1c.jpg";
            else if (image.endsWith("/dy1") || image.endsWith("/dy2") || image.endsWith("/dy3"))
                image = image.substring(0, image.length() - 1) + "4";
            entries.add(new Entry(title, image));
        }
        return entries;
    }

    private List<Entry> curated(Source source) throws IOException, JSONException {
        try (InputStream file = context.getAssets().open("top-shelf.json")) {
            JSONObject catalog = new JSONObject(new String(read(file, MAX_FEED_BYTES, Long.MAX_VALUE), Charsets.UTF_8));
            JSONArray rows = catalog.getJSONArray(source.name());
            List<Entry> entries = new ArrayList<>();
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                entries.add(new Entry(row.getString("title"), row.getString("image")));
            }
            return entries;
        }
    }

    private static final class Entry {
        final String title;
        final String image;

        Entry(String title, String image) {
            this.title = title;
            this.image = image;
        }
    }

    private byte[] fetch(String address, String referer, int limit, long deadline) throws IOException {
        checkActive(deadline);
        HttpsURLConnection connection = (HttpsURLConnection) https(address).openConnection();
        NetworkRequest.Handle handle = requests.begin(connection);
        try {
            checkActive(deadline);
            if (Build.VERSION.SDK_INT < 21) connection.setSSLSocketFactory(new Tls12SocketFactory());
            int timeout = (int) Math.min(3500, Math.max(1, deadline - SystemClock.elapsedRealtime()));
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) QuietTV/" + BuildConfig.VERSION_NAME);
            if (referer != null) connection.setRequestProperty("Referer", referer);
            if (connection.getResponseCode() != 200 || connection.getContentLength() > limit)
                throw new IOException("Preview response unavailable");
            try (InputStream input = connection.getInputStream()) {
                return read(input, limit, deadline);
            }
        } finally {
            requests.finish(handle);
        }
    }

    private static URL https(String address) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || url.getHost().isEmpty() || url.getUserInfo() != null)
            throw new IOException("Invalid preview URL");
        return url;
    }

    private static byte[] read(InputStream input, int limit, long deadline) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkActive(deadline);
            if (count > limit - output.size()) throw new IOException("Preview response too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void checkActive(long deadline) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() >= deadline)
            throw new InterruptedIOException("Preview request stopped");
    }

    static Bitmap decode(byte[] data) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth < 320 || options.outHeight < 180 || options.outWidth <= options.outHeight)
            throw new IOException("Preview requires a landscape image");
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > MAX_WIDTH || options.outHeight / options.inSampleSize > MAX_HEIGHT)
            options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (image == null) throw new IOException("Preview image could not be decoded");
        return image;
    }

    static Card demo(Context context, boolean chinese) throws IOException {
        try (InputStream file = context.getAssets().open("yosemite-valley.jpg")) {
            Bitmap image = decode(read(file, MAX_IMAGE_BYTES, Long.MAX_VALUE));
            return new Card(chinese ? "优胜美地山谷" : "Yosemite Valley",
                    chinese ? "预览示例" : "Sample preview", image);
        }
    }
}
