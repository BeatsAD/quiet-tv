package home.quiet.tv;

import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;

/** Shared presentation for application visibility, cover and package confirmations. */
final class AppActionConfirmationUi {
    static final String HIDE="隐藏此应用在 Quiet TV 桌面上的入口。\n应用和数据保留，可在“管理应用 → 隐藏应用”中恢复显示。";
    static final String RESTORE_COVER="恢复应用自带封面，移除当前自定义封面。\n应用数据保留。";

    static void content(ManagementUi ui,ManagementUi.Sheet sheet,String pkg,String name,String description,boolean destructive){
        sheet.contentWidth(480,480);
        LinearLayout identity=ui.row();identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new SurfaceCorners.Image(ui);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try{icon.setImageDrawable(ui.getPackageManager().getApplicationIcon(pkg));}catch(PackageManager.NameNotFoundException unavailable){icon.setVisibility(View.INVISIBLE);}
        identity.addView(icon,new LinearLayout.LayoutParams(ui.px(40),ui.px(40)));
        LinearLayout labels=ui.column();TextView title=ui.label(name,16);title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16.8f*ui.unit);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));labels.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView packageName=ui.muted(pkg,10);packageName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,10.08f*ui.unit);LinearLayout.LayoutParams packageParams=new LinearLayout.LayoutParams(-1,-2);packageParams.topMargin=ui.px(2);labels.addView(packageName,packageParams);
        LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-2,1);labelParams.leftMargin=ui.px(12);identity.addView(labels,labelParams);
        LinearLayout.LayoutParams identityParams=new LinearLayout.LayoutParams(-1,-2);identityParams.topMargin=ui.px(12);identityParams.bottomMargin=ui.px(5);sheet.body.addView(identity,identityParams);
        sheet.text(description);TextView copy=(TextView)sheet.body.getChildAt(sheet.body.getChildCount()-1);copy.setTextColor(UiPalette.TEXT);
        int warning=description.indexOf(AppOperations.DATA_LOSS);if(destructive&&warning>=0){SpannableString styled=new SpannableString(description);styled.setSpan(new ForegroundColorSpan(UiPalette.STATUS_ERROR),warning,warning+AppOperations.DATA_LOSS.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);copy.setText(styled);}
        ViewGroup overlay=(ViewGroup)sheet.box.getParent();overlay.getChildAt(0).setFocusable(false);overlay.getChildAt(0).setFocusableInTouchMode(false);
    }

    static void actions(ManagementUi ui,ManagementUi.Sheet sheet,Button cancel,Button confirm,boolean destructive){
        cancel.setTag("app-confirm-cancel");confirm.setTag("app-confirm-submit");
        confirm.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,UiPalette.CONTROL,false,destructive?UiPalette.STATUS_ERROR:UiPalette.EDGE_STRONG));confirm.setTextColor(SettingsFocusDrawable.textColors(destructive?UiPalette.STATUS_ERROR:UiPalette.TEXT));
        cancel.setId(androidx.core.view.ViewCompat.generateViewId());confirm.setId(androidx.core.view.ViewCompat.generateViewId());View close=sheet.box.findViewWithTag("sheet-close");
        cancel.setNextFocusLeftId(cancel.getId());cancel.setNextFocusRightId(confirm.getId());confirm.setNextFocusLeftId(cancel.getId());confirm.setNextFocusRightId(confirm.getId());cancel.setNextFocusDownId(cancel.getId());confirm.setNextFocusDownId(confirm.getId());
        if(close!=null){cancel.setNextFocusUpId(close.getId());confirm.setNextFocusUpId(close.getId());close.setNextFocusDownId(cancel.getId());}
    }

    private AppActionConfirmationUi(){}
}
