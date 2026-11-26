package home.quiet.tv;

import android.os.Handler;
import android.os.SystemClock;
import android.util.LruCache;
import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class TopShelfController {
    interface Display {
        void show(TopShelfContent.Card card);
    }

    private static final long CACHE_MS = 15 * 60 * 1000L;
    private static final class Snapshot {
        final TopShelfContent.Card card;
        final long savedAt = SystemClock.elapsedRealtime();

        Snapshot(TopShelfContent.Card card) {
            this.card = card;
        }
    }

    private final Handler main;
    private final TopShelfContent content;
    private final Display display;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LruCache<TopShelfContent.Source, Snapshot> cache = new LruCache<>(2);
    private TopShelfContent.Source selected;
    private boolean held;
    private int generation;
    private Future<?> work;
    private final Runnable load = this::load;

    TopShelfController(Handler main, TopShelfContent content, Display display) {
        this.main = main;
        this.content = content;
        this.display = display;
    }

    void show(TopShelfContent.Source source) {
        if (held || selected == source) return;
        selected = source;
        cancelLoad();
        Snapshot current = source == null ? null : cache.get(source);
        display.show(current == null ? null : current.card);
        if (source != null && (current == null || SystemClock.elapsedRealtime() - current.savedAt >= CACHE_MS))
            main.postDelayed(load, 300);
    }

    boolean showing(TopShelfContent.Source source) {
        return source != null && selected == source && cache.get(source) != null;
    }

    void hold() {
        held = true;
        cancelLoad();
    }

    void release() {
        held = false;
    }

    private void cancelLoad() {
        ++generation;
        main.removeCallbacks(load);
        if (work != null) work.cancel(true);
        work = null;
        content.cancel();
    }

    private void load() {
        TopShelfContent.Source source = selected;
        int attempt = generation;
        work = worker.submit(() -> {
            try {
                TopShelfContent.Card card = content.load(source);
                if (card == null) return;
                main.post(() -> {
                    if (generation != attempt) return;
                    cache.put(source, new Snapshot(card));
                    display.show(card);
                });
            } catch (IOException | JSONException | RuntimeException | OutOfMemoryError error) {
                if (!Thread.currentThread().isInterrupted())
                    DiagnosticLog.info("home.top_shelf.unavailable", "source=" + source.name() + " reason=" + error.getClass().getSimpleName());
            }
        });
    }

    void destroy() {
        release();
        show(null);
        worker.shutdownNow();
        cache.evictAll();
    }
}
