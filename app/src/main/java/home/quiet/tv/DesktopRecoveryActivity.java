package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** An incomplete restore never exposes a partly applied desktop. */
public final class DesktopRecoveryActivity extends ManagementUi {
    private DesktopBackups backups;
    private final android.os.Handler progress=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable checkProgress=()->{if(!isFinishing()&&!UiCompat.destroyed(this))render();};
    @Override boolean loadWallpaper(){return false;}
    @Override public void onCreate(Bundle state){super.onCreate(state);backups=DesktopBackups.get(this);render();}
    private void render(){
        progress.removeCallbacks(checkProgress);
        DesktopRestoreState state=backups.restoreState();
        if(!state.running()&&!state.needsReview()){
            startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();return;
        }
        root.removeAllViews();root.setGravity(Gravity.CENTER);
        if(state.running()){
            root.addView(center(state.message,26));gap(root,20);
            root.addView(center("处理完成后自动返回，请稍候",14));progress.postDelayed(checkProgress,500);return;
        }
        root.addView(center("桌面恢复尚未完成",26));gap(root,20);root.addView(center(state.message,14));gap(root,24);
        Button retry=button("重试恢复",()->{
            if(!backups.restoreState().needsReview())return;
            backups.retryRecovery(checkProgress);render();
        });
        root.addView(retry,new LinearLayout.LayoutParams(px(200),px(44)));gap(root,12);
        Button help=button("电脑协助",()->startActivity(new Intent(this,MaintenanceHelpActivity.class)));
        root.addView(help,new LinearLayout.LayoutParams(px(200),px(44)));retry.requestFocus();
    }
    @Override public void handleBack(){if(backups.restoreState().needsReview())startActivity(new Intent(this,MaintenanceHelpActivity.class));}
    @Override protected void onDestroy(){progress.removeCallbacks(checkProgress);super.onDestroy();}
}
