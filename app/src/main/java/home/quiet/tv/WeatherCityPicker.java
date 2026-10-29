package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One TV-owned draft shared by cascading menus, fuzzy search and phone input. */
final class WeatherCityPicker {
    private interface Selection { void accept(WeatherRegions.Region region); }
    private final AppManagementActivity ui;
    private final WeatherStore store;
    private final Runnable saved;
    private final WeatherRegions regions;
    private final WeatherClient searchClient;
    private int searchGeneration;
    private Future<?> searchRequest;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet editor,phone;
    private PopupWindow dropdown;
    private Button provinceButton,cityButton,districtButton,save,scan,reconnect;
    private EditText query;
    private LinearLayout results;
    private TextView selection,address,code,status;
    private ImageView qr;
    private Bitmap qrBitmap;
    private WeatherRegions.Region province,city,draft;
    private WeatherInputServer server;
    private volatile int generation;
    private String host;
    private boolean received;
    private final Runnable search=()->showResults(false);
    private final Runnable check=new Runnable(){@Override public void run(){
        if(phone==null||server==null)return;
        if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(ui))){stopServer(false);clearQr();code.setText("");status.setText(tr("会话已结束，请重新连接","Session ended. Reconnect to continue."));reconnect.setVisibility(View.VISIBLE);}
        else main.postDelayed(this,2000);
    }};
    WeatherCityPicker(AppManagementActivity ui,WeatherStore store,Runnable saved){this.ui=ui;this.store=store;this.saved=saved;regions=WeatherRegions.load(ui);searchClient=new WeatherClient(ui);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private void identify(View view,String tag){view.setId(androidx.core.view.ViewCompat.generateViewId());view.setTag(tag);}
    void show(){
        if(editor!=null)return;View origin=ui.getCurrentFocus();province=null;city=null;draft=null;
        editor=ui.new Sheet(tr("选择天气地区","Choose weather location"));
        LinearLayout row=ui.row();
        provinceButton=selector(row,tr("省份","Province"),"weather-province",()->menu(provinceButton,regions.children(""),region->{province=region;city=null;draft=null;update();cityButton.requestFocus();}));
        cityButton=selector(row,tr("城市","City"),"weather-city",()->menu(cityButton,regions.children(province.code),region->{city=region;draft=region;update();districtButton.requestFocus();}));
        districtButton=selector(row,tr("区县","District"),"weather-district",()->menu(districtButton,regions.children(city.code),this::select));
        editor.body.addView(row,new LinearLayout.LayoutParams(-1,ui.px(42)));ui.gap(editor.body,12);
        LinearLayout searchRow=ui.row();query=ui.input("",tr("搜索城市或区县，如武汉、武昌","Search city or district"));identify(query,"weather-query");query.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI);searchRow.addView(query,new LinearLayout.LayoutParams(0,ui.px(44),1));
        Button submit=ui.button(tr("搜索","Search"),()->showResults(true));identify(submit,"weather-search");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ui.px(72),ui.px(44));sp.leftMargin=ui.px(8);searchRow.addView(submit,sp);editor.body.addView(searchRow);
        ui.gap(editor.body,10);selection=ui.muted("",12);identify(selection,"weather-selection");editor.body.addView(selection);results=ui.column();editor.body.addView(results);
        scan=editor.action(tr("手机扫码输入","Phone input"),this::showPhone);identify(scan,"weather-phone");editor.actions.addView(new View(ui),new LinearLayout.LayoutParams(0,1,1));
        Button cancel=editor.action(tr("取消","Cancel"),()->editor.dialog.dismiss());identify(cancel,"weather-cancel");
        save=editor.action(tr("保存","Save"),()->{if(draft==null)return;store.select(draft);saved.run();editor.dialog.dismiss();});identify(save,"weather-save");
        for(Button button:new Button[]{scan,cancel,save})button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){++searchGeneration;main.removeCallbacks(search);main.postDelayed(search,180);}public void afterTextChanged(Editable value){}});
        query.setOnEditorActionListener((v,id,event)->{if(id!=EditorInfo.IME_ACTION_SEARCH)return false;hideKeyboard();showResults(true);return true;});
        query.setOnKeyListener((v,key,event)->{
            if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&event.getAction()==KeyEvent.ACTION_DOWN){((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(query,InputMethodManager.SHOW_IMPLICIT);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN&&event.getAction()==KeyEvent.ACTION_DOWN){if(results.getChildCount()>0&&results.getChildAt(0).isFocusable())results.getChildAt(0).requestFocus();else scan.requestFocus();return true;}return false;
        });
        editor.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        editor.dialog.setOnDismissListener(d->{++searchGeneration;if(searchRequest!=null)searchRequest.cancel(true);searchClient.cancel();main.removeCallbacks(search);hideKeyboard();if(dropdown!=null)dropdown.dismiss();if(phone!=null)phone.dialog.dismiss();stopServer(false);editor=null;query=null;if(origin!=null&&origin.isShown())origin.requestFocus();});
        WeatherRegions.Region selected=store.selected();if(selected!=null)select(selected);else update();
        editor.show(provinceButton);editor.dialog.getWindow().setLayout(ui.px(650),-2);
    }
    private Button selector(LinearLayout row,String title,String tag,Runnable action){
        Button button=ui.button(title,action);identify(button,tag);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(13));button.setBackgroundDrawable(ui.focusSurface(10));button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setSingleLine(true);button.setEllipsize(TextUtils.TruncateAt.END);
        DesktopSettingsRow.Glyph arrow=new DesktopSettingsRow.Glyph("down",ManagementUi.MUTED);arrow.setBounds(0,0,ui.px(14),ui.px(14));button.setCompoundDrawables(null,null,arrow,null);button.setCompoundDrawablePadding(ui.px(6));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(row.getChildCount()>0)p.leftMargin=ui.px(8);row.addView(button,p);return button;
    }
    private void update(){
        provinceButton.setText(province==null?tr("选择省份","Choose province"):province.name);
        cityButton.setText(city==null?tr("选择城市","Choose city"):city.name);
        districtButton.setText(draft!=null&&draft.level==2?draft.name:tr("选择区县","Choose district"));
        cityButton.setEnabled(province!=null);cityButton.setAlpha(province==null?.4f:1);
        boolean hasDistricts=city!=null&&!regions.children(city.code).isEmpty();districtButton.setEnabled(hasDistricts);districtButton.setAlpha(hasDistricts?1:.4f);
        save.setEnabled(draft!=null);save.setAlpha(draft==null?.4f:1);selection.setText(draft==null?"":tr("已选  ","Selected  ")+draft.label());
    }
    private void select(WeatherRegions.Region region){
        draft=region;city=region.level==2?regions.find(region.parent):region;province=city==null?null:regions.find(city.parent);update();
    }
    private void menu(Button anchor,List<WeatherRegions.Region> choices,Selection selected){
        if(dropdown!=null)dropdown.dismiss();hideKeyboard();LinearLayout list=ui.column();list.setPadding(ui.px(6),ui.px(6),ui.px(6),ui.px(6));list.setBackgroundDrawable(ui.surface(0xfa25262e,0xfa25262e,0,14));
        ScrollView scroll=new ScrollView(ui);scroll.setFillViewport(false);scroll.addView(list);PopupWindow popup=new PopupWindow(scroll,Math.max(anchor.getWidth(),ui.px(180)),ui.px(Math.min(212,choices.size()*40+12)),true);dropdown=popup;
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);if(Build.VERSION.SDK_INT>=21)popup.setElevation(ui.px(14));
        for(WeatherRegions.Region region:choices){
            Button option=ui.button(region.name,()->{popup.dismiss();selected.accept(region);});option.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(13));option.setSingleLine(true);option.setEllipsize(TextUtils.TruncateAt.END);option.setBackgroundDrawable(ui.listSurface());option.setSelected((draft!=null&&draft.code.equals(region.code))||(city!=null&&city.code.equals(region.code))||(province!=null&&province.code.equals(region.code)));list.addView(option,new LinearLayout.LayoutParams(-1,ui.px(40)));
        }
        popup.setOnDismissListener(()->{if(dropdown==popup)dropdown=null;anchor.requestFocus();});popup.showAsDropDown(anchor,0,ui.px(6));if(list.getChildCount()>0)list.getChildAt(0).requestFocus();
    }
    private void showResults(boolean online){
        if(editor==null)return;main.removeCallbacks(search);int version=++searchGeneration;if(searchRequest!=null)searchRequest.cancel(true);searchClient.cancel();
        results.removeAllViews();String text=query.getText().toString().trim();if(text.isEmpty())return;
        List<WeatherRegions.Region> matches=regions.search(text,20);
        if(!online){displayResults(matches,false);return;}
        hideKeyboard();ManagementUi.Sheet sheet=editor;results.addView(ui.muted(tr("正在联网搜索…","Searching online…"),12));
        searchRequest=worker.submit(()->{
            try{
                List<WeatherRegions.Region> remote=searchClient.cities(text);java.util.LinkedHashMap<String,WeatherRegions.Region> merged=new java.util.LinkedHashMap<>();
                for(WeatherRegions.Region place:matches)merged.put(place.code,place);for(WeatherRegions.Region place:remote)merged.put(place.code,place);
                main.post(()->{if(editor==sheet&&version==searchGeneration)displayResults(new ArrayList<>(merged.values()),true);});
            }catch(IOException|org.json.JSONException e){main.post(()->{
                if(editor!=sheet||version!=searchGeneration)return;displayResults(matches,false);
                results.addView(ui.muted(tr("联网搜索暂不可用，可使用本地结果","Online search unavailable. Local results remain available."),12));
            });}
        });
    }
    private void displayResults(List<WeatherRegions.Region> matches,boolean focus){
        results.removeAllViews();
        if(matches.isEmpty())results.addView(ui.muted(tr("未找到本地地点，点击搜索联网查找","No local places. Select Search to look online."),12));
        for(WeatherRegions.Region region:matches){
            Button option=ui.button(region.label(),()->{select(region);hideKeyboard();save.requestFocus();});option.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);option.setSingleLine(true);option.setEllipsize(TextUtils.TruncateAt.END);option.setBackgroundDrawable(ui.listSurface());option.setSelected(draft!=null&&draft.code.equals(region.code));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ui.px(42));p.topMargin=ui.px(5);results.addView(option,p);
        }
        if(focus&&!matches.isEmpty())results.getChildAt(0).requestFocus();
    }
    private void showPhone(){
        if(phone!=null)return;hideKeyboard();received=false;phone=ui.new Sheet(tr("手机扫码输入","Phone input"));LinearLayout row=ui.row(),details=ui.column();
        TextView instructions=ui.label(tr("手机与电视连接同一局域网\n扫码搜索地区，发送后在电视保存","Connect your phone to the TV’s local network.\nSearch a location, then save it on TV."),13);instructions.setLineSpacing(ui.px(6),1);details.addView(instructions);ui.gap(details,14);address=ui.label("",14);details.addView(address);ui.gap(details,6);code=ui.label("",13);details.addView(code);ui.gap(details,12);status=ui.muted(tr("也可打开地址，输入电视上的连接码","Or open the address and enter the TV code"),11);details.addView(status);
        row.addView(details,new LinearLayout.LayoutParams(0,-2,1));qr=new ImageView(ui);qr.setContentDescription(tr("天气地区输入二维码","Weather location input QR code"));qr.setBackgroundColor(Color.WHITE);qr.setPadding(ui.px(8),ui.px(8),ui.px(8),ui.px(8));LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(ui.px(150),ui.px(150));qp.leftMargin=ui.px(22);row.addView(qr,qp);phone.body.addView(row);
        reconnect=phone.action(tr("重新连接","Reconnect"),this::startServer);Button back=phone.action(tr("返回选择","Back to selection"),()->phone.dialog.dismiss());
        phone.dialog.setOnDismissListener(d->{phone=null;stopServer(received);clearQr();if(editor!=null){if(received)save.requestFocus();else scan.requestFocus();}});phone.show(back);phone.dialog.getWindow().setLayout(ui.px(610),-2);startServer();
    }
    private void startServer(){
        stopServer(false);int attempt=generation;received=false;reconnect.setVisibility(View.GONE);clearQr();code.setText("");host=WallpaperUploadPanel.lanAddress(ui);
        if(host==null){address.setText(tr("请先连接 Wi-Fi 或网线","Connect Wi-Fi or Ethernet first"));reconnect.setVisibility(View.VISIBLE);return;}
        String selectedHost=host;address.setText(tr("正在连接…","Connecting…"));
        worker.execute(()->{
            WeatherInputServer candidate=new WeatherInputServer(selectedHost,name->ui.getAssets().open(name),regions,query->searchClient.cities(query),region->receive(attempt,region));
            try{
                candidate.listen();BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,1));int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;Bitmap bitmap=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);
                main.post(()->{if(phone==null||attempt!=generation){candidate.stop();bitmap.recycle();return;}server=candidate;qrBitmap=bitmap;qr.setImageBitmap(bitmap);address.setText(candidate.shortAddress());code.setText(String.format(tr(ui.getString(R.string.greeting_connection_code_zh),ui.getString(R.string.greeting_connection_code_en)),candidate.connectionCode()));main.postDelayed(check,2000);});
            }catch(IOException|WriterException e){candidate.stop();main.post(()->{if(phone!=null&&attempt==generation){address.setText(tr("无法连接，请检查网络后重试","Unable to connect. Check the network and retry."));reconnect.setVisibility(View.VISIBLE);}});}
        });
    }
    private boolean receive(int attempt,WeatherRegions.Region region)throws IOException{
        FutureTask<Boolean> accepted=new FutureTask<>(()->{if(attempt!=generation||phone==null||editor==null||ui.isFinishing())return false;select(region);query.setText("");received=true;phone.dialog.dismiss();ui.feedback(tr("地区已接收，请保存","Location received. Select Save to apply."));return true;});main.post(accepted);
        try{return accepted.get(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();accepted.cancel(false);throw new IOException("Selection interrupted");}catch(ExecutionException|TimeoutException e){accepted.cancel(false);throw new IOException("Selection unavailable");}
    }
    private void clearQr(){if(qr!=null)qr.setImageDrawable(null);if(qrBitmap!=null){qrBitmap.recycle();qrBitmap=null;}}
    private void stopServer(boolean graceful){++generation;main.removeCallbacks(check);WeatherInputServer old=server;server=null;if(old!=null){searchClient.cancel();old.invalidate();worker.execute(()->{if(graceful)old.finishAfterResponse();else old.stop();});}}
    private void hideKeyboard(){if(query!=null)((InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);}
    void pause(){if(editor!=null)editor.dialog.dismiss();}
    void destroy(){pause();stopServer(false);worker.shutdown();main.removeCallbacksAndMessages(null);}
}
