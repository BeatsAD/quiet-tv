package home.quiet.tv;

import android.os.Bundle;
import java.util.*;
import android.view.*;
import android.widget.*;

/** Lifecycle adapter for the existing weather editor and its permission callbacks. */
final class WeatherSettingsPanel extends ManagementPanel {
    private DesktopWeatherPanel weather;
    private DesktopSettingsRow hideHomeApps, bootAds;
    private android.app.Dialog originalSheet;
    private android.app.Dialog systemSettingsSheet;
    private boolean systemSettingsKeyHeld;
    private final SystemSettingsTrial systemSettingsTrial;
    private int scrollY;
    private String focusTag;
    private final boolean general;
    private final Runnable backupChanged=this::refreshBackup;
    private final ManagementAdb.Listener adbChanged=()->{refreshAdb();refreshBootAds();};
    WeatherSettingsPanel(AppManagementActivity activity,boolean general){super(activity);this.general=general;systemSettingsTrial=new SystemSettingsTrial(activity);}
    @Override public void onCreate(Bundle state){
        pageRoot=column();weather=new DesktopWeatherPanel(activity);activity.addSettingsScroll(pageRoot,weather.view);activity.attachPanel(pageRoot);
        ArrayList<DesktopSettingsRow> rows=new ArrayList<>(Arrays.asList(weather.rows()));
        if(general){
            gap(weather.body,12);weather.body.addView(activity.section(activity.tr("桌面与启动","Desktop and startup")));
            LinearLayout desktop=activity.group();weather.body.addView(desktop);
            hideHomeApps=activity.setting(activity.tr("第二屏隐藏首屏应用","Hide Home apps on second screen"),"desktop","second-screen-hide-home");hideHomeApps.checked(new DesktopSettingsStore(this).hideHomeAppsOnSecondScreen());hideHomeApps.setOnClickListener(v->{DesktopSettingsStore settings=new DesktopSettingsStore(this);try{settings.savePhoneValue("hide-home-apps-on-second-screen",!settings.hideHomeAppsOnSecondScreen());PhoneSettingsSession.get(activity).configurationChanged();}catch(java.io.IOException error){message(error.getMessage());}hideHomeApps.checked(settings.hideHomeAppsOnSecondScreen());});activity.addSetting(desktop,hideHomeApps);
            DesktopSettingsRow guard=activity.setting(activity.tr("桌面管理","Desktop management"),"shield","desktop-guard");guard.value(DesktopSwitch.get(activity).state().optString("summary","状态待核对"));guard.tail("arrow");guard.setOnClickListener(v->activity.guardTool());activity.addSetting(desktop,guard);
            DesktopSettingsRow backup=activity.setting(activity.tr("桌面备份与恢复","Desktop backup and restore"),"","desktop-backups");backup.tail("arrow");backup.setOnClickListener(v->activity.backupTool());activity.addSetting(desktop,backup);refreshBackup();
            DesktopSettingsRow startup=activity.setting(activity.tr("开机直达","Startup destination"),"desktop","startup-destination");BootAppStore boot=new BootAppStore(this);startup.value(boot.enabled()?boot.label():activity.tr("Quiet TV 首页","Quiet TV Home"));startup.tail("arrow");startup.setOnClickListener(v->activity.startupTool());activity.addSetting(desktop,startup);
            bootAds=activity.setting(activity.tr("开机广告","Startup ads"),"","boot-ads");
            bootAds.tail("arrow");bootAds.setOnClickListener(v->activity.bootAdTool());
            activity.addSetting(desktop,bootAds);refreshBootAds();
            gap(weather.body,12);weather.body.addView(activity.section(activity.tr("连接与信号源","Connections and inputs")));LinearLayout inputs=activity.group();weather.body.addView(inputs);
            DesktopSettingsRow adb=activity.setting("ADB 管理","","adb-management");adb.tail("arrow");adb.setOnClickListener(v->activity.adbTool());activity.addSetting(inputs,adb);refreshAdb();
            DesktopSettingsRow source=activity.setting(activity.tr("信号源配置","Input configuration"),"tools","input-configuration");source.tail("arrow");source.setOnClickListener(v->activity.sourceTool());activity.addSetting(inputs,source);refreshSource();
            DesktopSettingsRow system=activity.setting(activity.tr("原设置","TV system settings"),"tools","general-system-settings");system.tail("arrow");system.value(activity.tr("打不开时长按","Long-press if it does not open"));system.setOnClickListener(v->openSystemSettings(false));
            system.setOnKeyListener((view,key,event)->{
                if(KeyFeedback.confirmKey(key)){
                    if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)systemSettingsKeyHeld=!event.isCanceled();
                    else if(event.getAction()==KeyEvent.ACTION_UP)systemSettingsKeyHeld=false;
                }
                return false;
            });
            system.setOnFocusChangeListener((view,focused)->{if(!focused)systemSettingsKeyHeld=false;});
            system.setOnLongClickListener(view->{
                boolean confirm=systemSettingsKeyHeld&&systemSettingsSheet==null;
                systemSettingsKeyHeld=false;
                systemSettingsOptions();
                if(confirm)KeySounds.play(activity,KeySoundPolicy.CONFIRM);
                return true;
            });
            activity.addSetting(inputs,system);
            Collections.addAll(rows,hideHomeApps,guard,backup,startup,bootAds,adb,source,system);
        }
        activity.wireRows(rows.toArray(new DesktopSettingsRow[0]),managementTab());
        if(state!=null){scrollY=state.getInt("scroll");focusTag=state.getString("focus");weather.restore(state);}
    }
    private void refreshBootAds(){if(bootAds!=null)bootAds.value(BootAdSheet.label(activity,BootAdSettings.get(activity).state().status));}
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scrollY=weather.view.getScrollY();}
    private final Runnable phoneChanged=this::phoneChanged;
    private String phoneWeatherKey="",phoneGeneralKey="";
    private void phoneChanged(){if(!showing)return;refreshBootAds();refreshDesktopSwitch();WeatherStore store=new WeatherStore(this);WeatherData data=store.cached(activity.chinese());String weatherKey=store.key(activity.chinese())+store.enabled()+":"+(data==null?0:data.fetchedAt);if(!weatherKey.equals(phoneWeatherKey)){phoneWeatherKey=weatherKey;weather.settingsChanged();}String generalKey=new BootAppStore(this).target()+new BootAppStore(this).hdmiBoot()+getSharedPreferences("boot-app",0).getAll().toString()+getSharedPreferences("device-input-profile",0).getString("profile","")+new HomeGuardState(this,false).preferences.getAll().toString();if(general&&!generalKey.equals(phoneGeneralKey)){phoneGeneralKey=generalKey;refreshSource();((DesktopSettingsRow)pageRoot.findViewWithTag("desktop-guard")).value(DesktopSwitch.get(activity).state().optString("summary","状态待核对"));BootAppStore boot=new BootAppStore(this);((DesktopSettingsRow)pageRoot.findViewWithTag("startup-destination")).value(boot.enabled()?boot.label():activity.tr("Quiet TV 首页","Quiet TV Home"));}}
    @Override protected void onResume(){super.onResume();PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);weather.resume();refreshDesktopSwitch();if(general){ManagementAdb.get(activity).addListener(adbChanged);refreshAdb();refreshBootAds();refreshSource();DesktopBackups.get(activity).listen(backupChanged);refreshBackup();((DesktopSettingsRow)pageRoot.findViewWithTag("desktop-guard")).value(DesktopSwitch.get(activity).state().optString("summary","状态待核对"));BootAppStore boot=new BootAppStore(this);((DesktopSettingsRow)pageRoot.findViewWithTag("startup-destination")).value(boot.enabled()?boot.label():activity.tr("Quiet TV 首页","Quiet TV Home"));}weather.view.post(()->{if(showing)weather.view.scrollTo(0,scrollY);});}
    @Override protected void onPause(){PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);if(general){DesktopBackups.get(activity).unlisten(backupChanged);ManagementAdb.get(activity).removeListener(adbChanged);}remember();weather.pause();super.onPause();}
    @Override protected void onStop(){if(originalSheet!=null)originalSheet.dismiss();if(systemSettingsSheet!=null)systemSettingsSheet.dismiss();super.onStop();}
    @Override protected void onDestroy(){if(general){DesktopBackups.get(activity).unlisten(backupChanged);ManagementAdb.get(activity).removeListener(adbChanged);}weather.destroy();super.onDestroy();}
    @Override void permissionResult(int request,int[] results){weather.permissionResult(request);}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isShown()||!target.isEnabled())target=weather.firstRow();target.requestFocus();}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putString("focus",focusTag);state.putInt("scroll",scrollY);weather.save(state);}
    private void original(){if(originalSheet!=null)return;originalSheet=DesktopSwitchUi.show(activity,()->{originalSheet=null;focusTag="general-tool-original";if(showing)pageRoot.findViewWithTag(focusTag).requestFocusFromTouch();refreshDesktopSwitch();});}
    private void openSystemSettings(boolean next){
        if(!systemSettingsTrial.open(next)){
            boolean tried=systemSettingsTrial.hasTried();
            message(activity.tr(tried?"没有其他可用入口，可长按后重新检测":"当前电视没有可用的系统设置入口",tried?"No further entry is available; long-press to detect again":"No system settings entry is available on this TV"));
        }
    }
    private void systemSettingsOptions(){
        if(systemSettingsSheet!=null)return;
        ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
        labels.add(activity.tr("尝试其他打开方式","Try another way"));actions.add(()->openSystemSettings(true));
        String confirmation=systemSettingsTrial.confirmation();
        if(!confirmation.isEmpty()){
            labels.add(activity.tr("已正常打开，记住此方式","It worked; remember this way"));actions.add(()->{
                boolean saved=systemSettingsTrial.remember(confirmation);
                if(saved)success(activity.tr("已记住当前电视的打开方式","Opening method saved for this TV"));else error(activity.tr("入口已变化或测试已过期，请重新打开设置","The entry changed or the test expired; open settings again"));
            });
        }
        if(systemSettingsTrial.hasTried()){labels.add(activity.tr("重新检测入口","Detect entries again"));actions.add(()->openSystemSettings(false));}
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(activity)
            .setItems(labels.toArray(new String[0]),(d,index)->actions.get(index).run()).create();
        ControlStyle.dialogNavigation(dialog,activity.tr("原设置","TV system settings"),activity.tr("关闭","Close"),unit);
        systemSettingsSheet=dialog;dialog.setOnDismissListener(d->{systemSettingsSheet=null;focusTag="general-system-settings";if(showing&&!isFinishing())pageRoot.findViewWithTag(focusTag).requestFocusFromTouch();});
        dialog.show();ControlStyle.dialog(dialog,unit,(canvas,paint)->activity.drawSheetBackdrop(canvas),activity::filterSheetBackdrop);
    }
    private void refreshDesktopSwitch(){if(general&&pageRoot!=null){hideHomeApps.checked(new DesktopSettingsStore(this).hideHomeAppsOnSecondScreen());DesktopSettingsRow row=(DesktopSettingsRow)pageRoot.findViewWithTag("general-tool-original");if(row!=null)row.value(DesktopSwitch.get(activity).state().optString("mode","待核对"));}}
    void settingsChanged(){weather.settingsChanged();}

    void startupToolReturned(){if(general){BootAppStore boot=new BootAppStore(this);DesktopSettingsRow row=(DesktopSettingsRow)pageRoot.findViewWithTag("startup-destination");row.value(boot.enabled()?boot.label():activity.tr("Quiet TV 首页","Quiet TV Home"));row.requestFocusFromTouch();}}
    private void refreshBackup(){DesktopBackups.get(activity).read(store->store.entries(),(entries,failure)->{if(!general||pageRoot==null)return;DesktopSettingsRow row=(DesktopSettingsRow)pageRoot.findViewWithTag("desktop-backups");row.value(!failure.isEmpty()?"暂不可用":entries.isEmpty()?"尚未备份":new java.text.SimpleDateFormat("MM/dd HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date(entries.get(0).record.created)));});}
    void backupToolReturned(){if(general){refreshBackup();((DesktopSettingsRow)pageRoot.findViewWithTag("desktop-backups")).requestFocusFromTouch();}}
    void guardToolReturned(){if(general){DesktopSettingsRow row=(DesktopSettingsRow)pageRoot.findViewWithTag("desktop-guard");row.value(DesktopSwitch.get(activity).state().optString("summary","状态待核对"));row.requestFocusFromTouch();}}
    private void refreshSource(){if(general){DesktopSettingsRow row=(DesktopSettingsRow)pageRoot.findViewWithTag("input-configuration");BootAppStore boot=new BootAppStore(this);row.value(boot.watchTarget().isEmpty()?"搜索  配对  端口校正":boot.watchLabel());}}
    private void refreshAdb(){
        if(!general||pageRoot==null)return;
        DesktopSettingsRow row=(DesktopSettingsRow)pageRoot.findViewWithTag("adb-management");
        if(row==null)return;
        ManagementAdb connection=ManagementAdb.get(activity);String label=connection.label();
        switch(connection.tone()){
            case "success":row.statusValue(label,"circle-check",UiPalette.STATUS_SUCCESS,0xff146c2e);break;
            case "progress":row.statusValue(label,"loader-circle",UiPalette.STATUS_PROGRESS,0xff005bb8);break;
            case "warning":row.statusValue(label,"circle-exclamation-point",UiPalette.STATUS_WARNING,0xff7a4b00);break;
            case "error":row.statusValue(label,"circle-exclamation-point",UiPalette.STATUS_ERROR,0xffb3261e);break;
            default:row.statusValue(label,"unplug",UiPalette.SECONDARY,0xff525b66);
        }
    }
    void adbToolReturned(){if(general){refreshAdb();pageRoot.findViewWithTag("adb-management").requestFocusFromTouch();}}
    void sourceToolReturned(){if(general){refreshSource();pageRoot.findViewWithTag("input-configuration").requestFocusFromTouch();}}
}
