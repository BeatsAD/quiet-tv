package home.quiet.tv;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import home.quiet.components.ComponentRepository;
import org.json.JSONObject;

/** Component control over the phone's already authenticated ADB session, never a browser endpoint. */
public final class ComponentProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        int uid=Binder.getCallingUid();if(uid!=2000&&uid!=0&&uid!=android.os.Process.myUid())throw new SecurityException("ADB authorization required");
        try{
            Context c=getContext();ComponentRepository repository=ComponentRepository.get(c);JSONObject result;
            if("state".equals(method))result=repository.snapshot();
            else if("download".equals(method)){repository.start(arg);result=repository.snapshot();}
            else if("cancel".equals(method)){if(!"weather".equals(arg)&&!"tailscale".equals(arg))throw new IllegalArgumentException();repository.cancel(arg);result=repository.snapshot();}
            else if("uninstall".equals(method)){if(!"weather".equals(arg))throw new IllegalArgumentException();WeatherComponentActions.uninstall(c);result=repository.snapshot();}
            else if("wallpaper".equals(method)){if(!"weather".equals(arg)&&!"fixed".equals(arg))throw new IllegalArgumentException();WallpaperStore store=WallpaperStore.get(c);WallpaperCatalog current=store.snapshot();store.background(arg,"fixed".equals(arg)&&WallpaperCatalog.DAILY.equals(current.current)?current.lastFixed:current.current,current.rotationMinutes,current.weatherDayNight);PhoneSettingsSession.get(c).configurationChanged();result=repository.snapshot();}
            else if(java.util.Arrays.asList("remote-state","remote-start","remote-pause","remote-login","remote-offer").contains(method)){if(Build.VERSION.SDK_INT<26)throw new java.io.IOException("组网需要安卓8及以上系统");result=TvRemoteController.command(c,method.substring(7),arg);}
            else throw new IllegalArgumentException("Unsupported component action");
            WallpaperCatalog background=WallpaperStore.get(c).snapshot();result.put("wallMode",background.weather?"weather":WallpaperCatalog.DAILY.equals(background.current)?"daily":background.rotating?"rotation":"fixed");
            result.put("protocol",1).put("packageName",c.getPackageName()).put("uid",android.os.Process.myUid()).put("versionCode",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode);
            Bundle reply=new Bundle();reply.putString("result",android.util.Base64.encodeToString(result.toString().getBytes("UTF-8"),android.util.Base64.NO_WRAP));return reply;
        }catch(Exception e){Bundle reply=new Bundle();reply.putString("error",e instanceof java.io.IOException?e.getMessage():"组件操作未完成，请核对电视状态");return reply;}
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
}
