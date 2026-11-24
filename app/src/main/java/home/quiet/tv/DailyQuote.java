package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONException;
import org.json.JSONObject;

/** One process-wide download and one cached quote, keyed by the TV's local date. */
final class DailyQuote {
    static final String HOME = "show-home-quote";
    static final String SAVER = "show-screensaver-quote";
    private static final String PAGES = "https://beatsad.github.io/quiet-tv-quotes/daily/";
    private static final String RAW = "https://raw.githubusercontent.com/BeatsAD/quiet-tv-quotes/main/docs/daily/";
    private static final int MAX_RESPONSE = 4096;
    private static DailyQuote instance;

    static final class Entry {
        final String date, text, credit;
        Entry(String date, String text, String credit) {
            this.date = date;
            this.text = text;
            this.credit = credit;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("date", date).put("text", text).put("credit", credit);
        }
    }

    static synchronized DailyQuote get(Context context) {
        if (instance == null) instance = new DailyQuote(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final SharedPreferences cache;
    private final ConnectivityManager connectivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor worker = Executors.newSingleThreadExecutor();
    private final Set<Runnable> observers = new HashSet<>();
    private volatile Entry entry;
    private String attempted = "";
    private boolean fetching;
    private long retryAt;

    private DailyQuote(Context context) {
        this.context = context;
        cache = context.getSharedPreferences("daily-quote", Context.MODE_PRIVATE);
        connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        String json = cache.getString("current", "");
        if (!json.isEmpty()) try {
            JSONObject saved = new JSONObject(json);
            entry = parse(saved, saved.getString("date"));
        } catch (JSONException ignored) { }
    }

    static String date(long time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(time));
    }

    Entry current(String date) {
        Entry value = entry;
        return value != null && date.equals(value.date) ? value : null;
    }

    void observe(Runnable observer) { observers.add(observer); }
    void forget(Runnable observer) { observers.remove(observer); }

    // Called on the main thread from visible surfaces. Routine clock ticks do not retry a failed date.
    void request(String day, boolean retry) {
        if (current(day) != null || fetching
                || (day.equals(attempted) && (!retry || SystemClock.elapsedRealtime() < retryAt))) return;
        NetworkInfo network = connectivity == null ? null : connectivity.getActiveNetworkInfo();
        if (network == null || !network.isConnected()) return;
        attempted = day;
        fetching = true;
        worker.execute(() -> {
            Entry result = null;
            try { result = download(day); }
            catch (IOException failure) {
                DiagnosticLog.info("daily_quote.unavailable", failure.getClass().getSimpleName());
            }
            Entry response = result;
            main.post(() -> complete(day, response));
        });
    }

    private void complete(String day, Entry response) {
        fetching = false;
        retryAt = SystemClock.elapsedRealtime() + 30_000;
        if (response != null && day.equals(date(System.currentTimeMillis()))) {
            entry = response;
            try { cache.edit().putString("current", response.json().toString()).apply(); }
            catch (JSONException impossible) { throw new AssertionError(impossible); }
            PhoneSettingsSession.get(context).dailyQuoteChanged();
        }
        if (response != null || !day.equals(date(System.currentTimeMillis()))) {
            for (Runnable observer : new ArrayList<>(observers)) observer.run();
        }
    }

    private Entry download(String day) throws IOException {
        String file = day + ".json";
        ArrayList<String> addresses = new ArrayList<>();
        for (String prefix : UpdateTransfer.SOURCES) {
            if (!prefix.isEmpty()) addresses.add(prefix + RAW + file);
        }
        addresses.add(PAGES + file);
        addresses.add(RAW + file);

        SSLSocketFactory tls = Build.VERSION.SDK_INT < 26 ? UpdateTls.create(context) : null;
        Exception lastFailure = null;
        for (String address : addresses) {
            URL url = new URL(address);
            try {
                int timeout = address.startsWith(PAGES) || address.startsWith(RAW) ? 5000 : 2500;
                Entry result = download(url, day, tls, timeout);
                DiagnosticLog.info("daily_quote.source", url.getHost());
                return result;
            } catch (IOException | JSONException failure) {
                lastFailure = failure;
            }
        }
        throw new IOException("Daily quote unavailable from configured sources", lastFailure);
    }

    private static Entry download(URL url, String day, SSLSocketFactory tls, int timeout)
            throws IOException, JSONException {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try {
            if (tls != null) connection.setSSLSocketFactory(tls);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) throw new IOException("Quote unavailable");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[512];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > MAX_RESPONSE) throw new IOException("Quote response too large");
                    body.write(buffer, 0, count);
                }
                return parse(new JSONObject(body.toString("UTF-8")), day);
            }
        } finally {
            connection.disconnect();
        }
    }

    static Entry parse(JSONObject json, String expected) throws JSONException {
        if (!(json.opt("date") instanceof String) || !(json.opt("text") instanceof String)
                || !(json.opt("credit") instanceof String)) {
            throw new JSONException("Invalid daily quote fields");
        }
        String date = json.getString("date");
        String text = json.getString("text").trim();
        String credit = json.getString("credit").trim();
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || !date.equals(expected)
                || text.isEmpty() || text.length() > 46 || credit.isEmpty() || credit.length() > 36
                || text.contains("\n") || text.contains("\r") || credit.contains("\n") || credit.contains("\r")) {
            throw new JSONException("Invalid daily quote");
        }
        return new Entry(date, text, credit);
    }
}
