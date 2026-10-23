package home.quiet.tv;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Weather controls live under Desktop Settings, independent of OEM settings or ADB. */
public final class WeatherSettingsActivity extends ManagementUi {
    private boolean chinese;
    private WeatherStore store;
    private WeatherController controller;
    private TextView status,location;
    private Button automatic;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService searcher=Executors.newSingleThreadExecutor();
    private final WeatherClient searchClient=new WeatherClient();
    private Future<?> search;
    private Sheet picker;
    private int searchVersion;
    private String tr(String zh,String en){return chinese?zh:en;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try(java.io.InputStream input=getAssets().open("launcher.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
            chinese=!"en".equals(new JSONObject(out.toString("UTF-8")).optString("language","zh"));
        }catch(java.io.IOException|org.json.JSONException e){throw new IllegalStateException("Invalid launcher configuration",e);}
        ((TextView)header.getChildAt(1)).setText(tr("天气设置","Weather Settings"));header.getChildAt(0).setContentDescription(tr("返回","Back"));
        store=new WeatherStore(this);
        ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(0,px(18),0,px(12));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Switch enabled=new Switch(this);enabled.setText(tr("显示天气","Show weather"));enabled.setTextColor(TEXT);enabled.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(18));enabled.setChecked(store.enabled());enabled.setPadding(px(18),0,px(18),0);focusable(enabled,12);body.addView(enabled,new LinearLayout.LayoutParams(-1,px(54)));
        gap(body,12);location=label("",16);body.addView(location,new LinearLayout.LayoutParams(-1,px(28)));
        LinearLayout actions=row();gap(body,10);body.addView(actions);
        automatic=button(tr("自动定位","Automatic location"),this::automaticLocation);actions.addView(automatic,new LinearLayout.LayoutParams(-2,px(42)));
        Button choose=button(tr("选择城市","Choose city"),this::chooseCity);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,px(42));cp.leftMargin=px(12);actions.addView(choose,cp);
        Button refresh=button(tr("立即刷新","Refresh"),()->controller.refresh(true));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,px(42));rp.leftMargin=px(12);actions.addView(refresh,rp);
        gap(body,16);status=label("",14);status.setLineSpacing(px(5),1);body.addView(status,new LinearLayout.LayoutParams(-1,-2));
        gap(body,14);TextView note=muted(tr("自动定位不可用时按网络 IP 推测城市，可手动纠正。启用后通过 HTTPS 向 UAPI 查询天气和位置。","When system location is unavailable, the network IP estimates the city. You can choose a city instead. Enabling weather sends weather and location queries to UAPI over HTTPS."),12);body.addView(note);
        gap(body,14);LinearLayout sources=row();body.addView(sources);
        sources.addView(button(tr("数据来源  UAPI","Weather source  UAPI"),()->open(WeatherClient.SOURCE_URL)),new LinearLayout.LayoutParams(-2,px(38)));
        Button privacy=button(tr("服务隐私与条款","Service privacy and terms"),()->open("https://uapis.cn/docs/getting-started/privacy-policy"));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,px(38));pp.leftMargin=px(12);sources.addView(privacy,pp);
        controller=new WeatherController(this,chinese,this::render);
        enabled.setOnCheckedChangeListener((view,checked)->{store.enabled(checked);controller.settingsChanged();});enabled.requestFocus();
    }
    private void automaticLocation(){
        store.automaticMode();
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},201);
        controller.settingsChanged();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==201)controller.settingsChanged();}
    private void render(WeatherData data,String message,boolean loading){
        location.setText(store.automatic()?tr("位置  自动","Location  Automatic"):tr("城市  ","City  ")+store.city());
        String value="";
        if(!store.enabled())value=tr("天气已关闭","Weather is off");
        else if(data!=null){
            String source="system".equals(data.locationSource)?tr("系统定位","System location"):"ip".equals(data.locationSource)?tr("IP 推测位置","IP estimated location"):tr("手动选择","Manually selected");
            SimpleDateFormat time=new SimpleDateFormat("MM-dd HH:mm",Locale.US);
            value=data.city+"  "+data.temperatureText()+"  "+data.description+"\n"+source+"\n"+tr("获取时间  ","Fetched  ")+time.format(new Date(data.fetchedAt));
            value+="\n"+(data.observedAt>0?tr("数据时间  ","Observed  ")+time.format(new Date(data.observedAt)):tr("数据时间  接口未提供可识别时间","Observation time unavailable"));
        }
        if(loading)value+=(value.isEmpty()?"":"\n")+tr("正在更新天气…","Updating weather…");
        if(!message.isEmpty())value+=(value.isEmpty()?"":"\n")+message;
        status.setText(value);automatic.setSelected(store.automatic());
    }
    private void chooseCity(){
        if(picker!=null)return;
        Sheet sheet=new Sheet(tr("选择城市","Choose city"));picker=sheet;
        EditText query=new EditText(this);query.setSingleLine(true);query.setTextColor(TEXT);query.setHintTextColor(MUTED);query.setHint(tr("输入城市或区县名称","Enter city or district"));query.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(16));query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);sheet.body.addView(query,new LinearLayout.LayoutParams(-1,px(46)));
        LinearLayout results=column();sheet.body.addView(results,new LinearLayout.LayoutParams(-1,-2));
        Runnable run=()->{
            String keywords=query.getText().toString().trim();if(keywords.isEmpty()||keywords.length()>60){feedback(tr("请输入城市名称","Enter a city name"));return;}
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(query.getWindowToken(),0);
            int version=++searchVersion;if(search!=null)search.cancel(true);searchClient.cancel();
            results.removeAllViews();results.addView(muted(tr("正在搜索…","Searching…"),13));
            search=searcher.submit(()->{
                try{
                    List<WeatherClient.City> cities=searchClient.cities(keywords,chinese);
                    main.post(()->{
                        if(picker!=sheet||version!=searchVersion)return;results.removeAllViews();
                        if(cities.isEmpty())results.addView(muted(tr("未找到城市，请输入完整名称","No results. Try the full city name."),13));
                        for(WeatherClient.City city:cities){
                            Button option=button(city.name+(city.area.isEmpty()?"":"  "+city.area),()->{store.select(city.adcode,city.name);sheet.dialog.dismiss();controller.settingsChanged();});option.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(42));p.topMargin=px(6);results.addView(option,p);
                        }
                        if(results.getChildCount()>0)results.getChildAt(0).requestFocus();
                    });
                }catch(java.io.IOException|org.json.JSONException e){main.post(()->{if(picker!=sheet||version!=searchVersion)return;results.removeAllViews();results.addView(muted(e instanceof WeatherClient.ApiException&&((WeatherClient.ApiException)e).status==429?tr("搜索受限，请稍后再试","Search limited. Try later."):tr("搜索失败，请检查网络后重试","Search failed. Check the network."),13));});}
            });
        };
        Button submit=sheet.action(tr("搜索","Search"),run);submit.setId(View.generateViewId());sheet.action(tr("取消","Cancel"),sheet.dialog::dismiss);
        query.setOnEditorActionListener((view,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){run.run();return true;}return false;});
        sheet.dialog.setOnDismissListener(d->{++searchVersion;if(search!=null)search.cancel(true);searchClient.cancel();picker=null;});sheet.show(query);
        query.setNextFocusDownId(submit.getId());
    }
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){feedback(tr("当前电视没有可用的浏览器","No browser is available on this TV"));}}
    @Override protected void onResume(){super.onResume();controller.resume();}
    @Override protected void onPause(){controller.pause();if(picker!=null)picker.dialog.dismiss();super.onPause();}
    @Override protected void onDestroy(){controller.destroy();++searchVersion;searchClient.cancel();searcher.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
