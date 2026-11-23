package home.quiet.tv;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;

/** Optional app-window render timings, not display presentation or system-wide frame data. */
final class DiagnosticFrameWindow {
    private static final long WINDOW_MS=1200;

    static Runnable watch(Window window,AutoDiagnosticOperation operation){
        if(Build.VERSION.SDK_INT<24||operation==null||!DiagnosticProbe.active()
                ||!androidx.core.view.ViewCompat.isAttachedToWindow(window.getDecorView())
                ||!window.getDecorView().isHardwareAccelerated())return null;
        return Api24.watch(window,operation);
    }

    @androidx.annotation.RequiresApi(24)
    private static final class Api24 implements Window.OnFrameMetricsAvailableListener,Runnable {
        private final Window window;
        private final AutoDiagnosticOperation operation;
        private final Handler main=new Handler(Looper.getMainLooper());
        private final long slowNs;
        private int frames,slow,dropped;
        private long maxNs;
        private boolean closed;

        private Api24(Window window,AutoDiagnosticOperation operation){
            this.window=window;this.operation=operation;
            android.view.Display display=window.getDecorView().getDisplay();
            float rate=display==null?60f:display.getRefreshRate();
            slowNs=(long)(1_000_000_000d/(rate>0f?rate:60f));
        }
        static Runnable watch(Window window,AutoDiagnosticOperation operation){
            Api24 collector=new Api24(window,operation);
            try{window.addOnFrameMetricsAvailableListener(collector,collector.main);}
            catch(RuntimeException unavailable){return null;}
            collector.main.postDelayed(collector,WINDOW_MS);
            return collector;
        }
        @Override public void onFrameMetricsAvailable(Window window,android.view.FrameMetrics metrics,int droppedSinceLast){
            if(closed||frames>=120)return;
            if(!DiagnosticProbe.active()){run();return;}
            long total=metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION);
            if(total<0)return;
            long deadline=Build.VERSION.SDK_INT>=31?metrics.getMetric(android.view.FrameMetrics.DEADLINE):-1;
            frames++;if(total>(deadline>0?deadline:slowNs))slow++;
            maxNs=Math.max(maxNs,total);dropped+=Math.max(0,droppedSinceLast);
        }
        @Override public void run(){
            if(closed)return;
            closed=true;main.removeCallbacks(this);
            try{window.removeOnFrameMetricsAvailableListener(this);}
            catch(RuntimeException unavailable){/* Not all vendor windows retain listeners after detach. */}
            if(frames>0)operation.frameSummary(frames,slow,maxNs/1_000_000,dropped);
        }
    }
}
