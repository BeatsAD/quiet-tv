package home.quiet.tv;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** A confirmation owns the exact artwork and cover version that the user reviews. */
final class IconLibraryReplaceSheet {
    private final AppManagementActivity activity;
    private final Context artwork;
    private final AppCoverStore covers;
    private final String pkg,label,shape,form,expected;
    private final ResolveInfo target;
    private final IconLibraryCatalog.Item item;
    private final int revision;
    private final Runnable changed,closedCallback;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final IconLibraryTransfer.Request request=new IconLibraryTransfer.Request();
    private ManagementUi.Sheet sheet;
    private Button cancel,confirm,close;
    private TextView status;
    private ImageView before,after;
    private Bitmap current,selected;
    private File candidate;
    private JSONObject metadata;
    private volatile boolean closed,committing;
    IconLibraryReplaceSheet(AppManagementActivity activity,Context artwork,String pkg,String label,ResolveInfo target,IconLibraryCatalog.Item item,String shape,String form,int revision,Runnable changed,Runnable closedCallback){
        this.activity=activity;this.artwork=artwork;this.pkg=pkg;this.label=label;this.target=target;this.item=item;this.shape=shape;this.form=form;this.revision=revision;this.changed=changed;this.closedCallback=closedCallback;covers=AppCoverStore.get(activity);expected=covers.key(pkg);
    }
    private String tr(String zh,String en){return activity.tr(zh,en);}
    void show(){
        sheet=activity.new Sheet(tr("替换图标","Replace icon"));close=sheet.settingsLayout(tr("关闭","Close"),this::close);sheet.box.setTag("icon-replace-sheet");
        sheet.dialog.setOnDismissListener(d->release());sheet.text(label);
        LinearLayout comparison=activity.row();before=picture(comparison,tr("当前图标","Current icon"),"icon-replace-current");after=picture(comparison,tr("所选图标","Selected icon"),"icon-replace-selected");sheet.body.addView(comparison,new LinearLayout.LayoutParams(-1,-2));
        status=activity.muted(tr("正在读取图标…","Loading icons…"),12);status.setTag("icon-replace-status");status.setPadding(0,activity.px(12),0,activity.px(6));sheet.body.addView(status);
        cancel=sheet.action(tr("取消","Cancel"),this::close);identify(cancel,"icon-replace-cancel");confirm=sheet.action(tr("确认替换","Confirm replacement"),this::commit);identify(confirm,"icon-replace-confirm");confirm.setEnabled(false);
        cancel.setNextFocusRightId(confirm.getId());confirm.setNextFocusLeftId(cancel.getId());cancel.setNextFocusLeftId(cancel.getId());confirm.setNextFocusRightId(confirm.getId());
        close.setNextFocusDownId(cancel.getId());cancel.setNextFocusUpId(close.getId());confirm.setNextFocusUpId(close.getId());cancel.setNextFocusDownId(cancel.getId());confirm.setNextFocusDownId(confirm.getId());
        sheet.show(cancel);worker.execute(this::load);
    }
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());}
    private ImageView picture(LinearLayout row,String title,String tag){
        LinearLayout column=activity.column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(row.getChildCount()>0)cp.leftMargin=activity.px(24);row.addView(column,cp);column.addView(activity.muted(title,12));activity.gap(column,8);
        ImageView image=new ImageView(activity);image.setTag(tag);image.setContentDescription(title);image.setScaleType(ImageView.ScaleType.FIT_CENTER);column.addView(image,new LinearLayout.LayoutParams(-1,activity.px(126)));return image;
    }
    private void load(){
        Bitmap original=null,next=null;
        try{
            request.check();AppCoverPolicy.require(activity,pkg);metadata=LibraryCover.metadata(item,shape,form,revision);original=AppArtwork.load(activity,target);candidate=covers.prepared();IconLibraryArtwork.prepare(artwork,item,shape,form,candidate,activity.chinese(),request);next=AppCoverImages.load(candidate,360);if(next==null)throw new IOException(tr("图标读取失败","Unable to read icon"));request.check();
            if(!expected.equals(covers.key(pkg)))throw new IOException(tr("当前图标已变化，请重新打开确认","Icon changed; reopen confirmation"));
            Bitmap old=original,chosen=next;original=null;next=null;main.post(()->{if(closed){old.recycle();chosen.recycle();return;}current=old;selected=chosen;before.setImageBitmap(current);after.setImageBitmap(selected);status.setVisibility(View.GONE);confirm.setEnabled(true);});
        }catch(IOException|JSONException|RuntimeException failure){String message=failure.getMessage();main.post(()->{if(!closed){status.setText(message==null?tr("图标读取失败","Unable to read icon"):message);OperationFeedback.status(status,OperationFeedback.Kind.ERROR);confirm.setEnabled(false);}});}
        finally{if(original!=null)original.recycle();if(next!=null)next.recycle();}
    }
    private void commit(){
        if(closed||committing||selected==null||candidate==null)return;committing=true;sheet.dialog.setCancelable(false);sheet.box.setEnabled(false);close.setEnabled(false);cancel.setEnabled(false);confirm.setEnabled(false);status.setText(tr("正在替换图标…","Replacing icon…"));OperationFeedback.status(status,OperationFeedback.Kind.PROGRESS);status.setVisibility(View.VISIBLE);
        worker.execute(()->{String error=null;boolean applied=false;try{AppCoverPolicy.require(activity,pkg);applied=covers.applyLibrary(pkg,candidate,metadata,expected);}catch(IOException|RuntimeException failure){error=failure.getMessage();if(error==null)error=tr("图标替换失败","Unable to replace icon");}String failure=error;boolean saved=applied;main.post(()->{
            committing=false;if(saved)PhoneSettingsSession.get(activity).configurationChanged();if(closed)return;
            if(failure==null){close();changed.run();if(saved)activity.success(tr("已替换「"+label+"」图标","Icon replaced for "+label));else activity.feedback(tr("已使用此图标","This icon is already applied"));}
            else{sheet.dialog.setCancelable(true);sheet.box.setEnabled(true);close.setEnabled(true);cancel.setEnabled(true);status.setText(failure);OperationFeedback.status(status,OperationFeedback.Kind.ERROR);confirm.setEnabled(true);cancel.requestFocus();}
        });});
    }
    void close(){if(committing||closed)return;ManagementUi.Sheet own=sheet;release();own.dialog.setOnDismissListener(null);own.dialog.dismiss();}
    private void release(){
        if(closed)return;closed=true;request.cancel();if(before!=null)before.setImageDrawable(null);if(after!=null)after.setImageDrawable(null);if(current!=null)current.recycle();if(selected!=null)selected.recycle();worker.execute(()->covers.discardCandidate(candidate));worker.shutdown();closedCallback.run();
    }
    void destroy(){if(closed)return;ManagementUi.Sheet own=sheet;release();if(own!=null){own.dialog.setOnDismissListener(null);own.dialog.dismiss();}}
}
