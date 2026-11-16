package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** An incomplete restore never exposes a partly applied desktop. */
public final class DesktopRecoveryActivity extends ManagementUi {
    private boolean busy;
    private DesktopBackups backups;
    private final android.os.Handler progress=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable checkProgress=()->{if(isFinishing()||UiCompat.destroyed(this))return;if(backups.phoneRestoring())render();else if(backups.recoveryError().isEmpty()){startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}else render();};
    @Override boolean loadWallpaper(){return false;}
    @Override public void onCreate(Bundle state){super.onCreate(state);backups=DesktopBackups.get(this);render();}
    private void render(){
        progress.removeCallbacks(checkProgress);
        if(backups.phoneRestoring()){root.removeAllViews();root.setGravity(Gravity.CENTER);root.addView(center("正在恢复桌面",26));gap(root,20);root.addView(center("恢复完成后自动返回，请稍候",14));progress.postDelayed(checkProgress,500);return;}
        root.removeAllViews();root.setGravity(Gravity.CENTER);TextView title=center("桌面恢复尚未完成",26);root.addView(title);gap(root,20);root.addView(center(backups.recoveryError(),14));gap(root,24);
        Button retry=button(busy?"正在恢复…":"重试恢复",()->{if(busy)return;busy=true;render();backups.retryRecovery(()->{if(isFinishing()||UiCompat.destroyed(this))return;busy=false;if(backups.recoveryError().isEmpty()){startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}else render();});});retry.setEnabled(!busy);root.addView(retry,new LinearLayout.LayoutParams(px(200),px(44)));gap(root,12);Button help=button("电脑协助",()->startActivity(new Intent(this,MaintenanceHelpActivity.class)));root.addView(help,new LinearLayout.LayoutParams(px(200),px(44)));if(!busy)retry.requestFocus();
    }
    @Override public void handleBack(){if(!busy&&!backups.phoneRestoring())startActivity(new Intent(this,MaintenanceHelpActivity.class));}
    @Override protected void onDestroy(){progress.removeCallbacks(checkProgress);super.onDestroy();}
}
