package home.quiet.tv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;

/** Only a verified, compatible repository candidate may open this HOME sheet. */
public final class UpdateReminderActivity extends ManagementUi {
    static final String CANDIDATE="update-candidate";
    private UpdateRepository repository;
    private UpdatePanel panel;
    private Sheet reminderSheet;
    private final UpdateRepository.Listener changed=()->{if(panel!=null)panel.changed();};
    @Override boolean managementChrome(){return false;}
    @Override boolean unifiedSettings(){return true;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);stage.setBackgroundColor(Color.TRANSPARENT);overridePendingTransition(0,0);
        repository=UpdateRepository.get(this);
        String candidate=getIntent().getStringExtra(CANDIDATE);
        if(candidate==null||candidate.isEmpty()||!candidate.equals(repository.reminderCandidate())){finish();return;}
        panel=new UpdatePanel(this);
        reminderSheet=new Sheet("软件更新");
        reminderSheet.settingsLayout("关闭",panel::close);
        reminderSheet.dialog.setOnDismissListener(dialog->finish());
        panel.openReminder(reminderSheet,this::finish,()->repository.homeReminderShown(candidate));
    }
    @Override void drawSheetBackdrop(Canvas canvas){
        Bitmap wallpaper=entryBackdrop();
        if(wallpaper==null)canvas.drawColor(0xff202124);
        else canvas.drawBitmap(wallpaper,null,new Rect(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels),new Paint(Paint.FILTER_BITMAP_FLAG));
    }
    @Override protected void onResume(){super.onResume();if(panel!=null){repository.add(changed);panel.resumed();}}
    @Override protected void onPause(){if(panel!=null){repository.remove(changed);panel.pause();}super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==UpdateInstaller.REQUEST_INSTALL&&repository!=null)repository.fileInstallReturned(result,data);
    }
    @Override public void finish(){super.finish();overridePendingTransition(0,0);}
    @Override protected void onDestroy(){
        if(panel!=null)panel.detach();
        if(reminderSheet!=null){reminderSheet.dialog.setOnDismissListener(null);reminderSheet.dialog.dismiss();}
        super.onDestroy();
    }
}
