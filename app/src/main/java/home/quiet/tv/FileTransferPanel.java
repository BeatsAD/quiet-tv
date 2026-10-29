package home.quiet.tv;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** A management tab with independent, explicitly opened LAN sessions and read-only sources. */
final class FileTransferPanel extends ManagementPanel {
    static final int PAGE=6,PICK=7401,TREE=7402,PERMISSION=7403;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private FileTransferStore store;
    private volatile FileTransferServer server;
    private volatile int sessionGeneration,jobGeneration;
    private boolean destroyed,busy,usb,networkReceiving;
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){stopSession();}};
    private int source,listing,listPosition;
    private File folder,boundary;
    private Uri tree,current,pending;
    private final ArrayDeque<Uri> parents=new ArrayDeque<>();
    private ListView list;
    private TextView location,space,status,connection;
    private LinearLayout actions;
    private ImageView qr;
    private Bitmap qrImage;
    private Button start,codeButton,received,local,removable;
    private android.app.Dialog codeDialog;
    private FileTransferStore.Entry selected;
    private FileTransferSource.Item selectedSource;
    private final Runnable check=new Runnable(){public void run(){FileTransferServer active=server;if(active==null)return;if(active.expired()||!active.shortAddress().startsWith("http://"+WallpaperUploadPanel.lanAddress(activity)+":")){stopSession();status.setText("传输已结束，请重新开启");}else main.postDelayed(this,2000);}};
    FileTransferPanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        LinearLayout body=managementContent();managementInfo.addView(label("文件传输助手",16));space=muted("",11);managementInfo.addView(space,new LinearLayout.LayoutParams(0,-1,1));space.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        LinearLayout columns=row();columns.setGravity(Gravity.TOP);body.addView(columns,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout sources=column();sources.setPadding(0,px(8),px(14),0);columns.addView(sources,new LinearLayout.LayoutParams(px(132),-1));
        received=sourceButton(sources,"已接收",()->choose(0));local=sourceButton(sources,"本地存储",()->choose(1));removable=sourceButton(sources,"U 盘",()->choose(2));
        sourceButton(sources,"选择文件",this::pick);if(Build.VERSION.SDK_INT>=21)sourceButton(sources,"授权目录",this::pickTree);
        LinearLayout middle=column();middle.setPadding(0,px(8),px(16),0);columns.addView(middle,new LinearLayout.LayoutParams(0,-1,1));
        Button up=button("返回上级",this::up);middle.addView(up,new LinearLayout.LayoutParams(-1,px(34)));location=muted("",12);location.setPadding(0,px(10),0,px(10));location.setMaxLines(2);middle.addView(location);
        list=new ListView(activity);list.setId(androidx.core.view.ViewCompat.generateViewId());list.setDividerHeight(px(5));list.setSelector(focusSurface(8));middle.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_LEFT){(source==0?received:source==1?local:removable).requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT){start.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP&&list.getSelectedItemPosition()<=0){up.requestFocus();return true;}return false;});
        LinearLayout right=column();right.setPadding(px(8),px(8),0,0);columns.addView(right,new LinearLayout.LayoutParams(px(235),-1));
        LinearLayout transferActions=row();right.addView(transferActions,new LinearLayout.LayoutParams(-1,px(36)));
        start=button("开启传输",()->{if(server!=null)stopSession();else startSession(null);});start.setId(androidx.core.view.ViewCompat.generateViewId());transferActions.addView(start,new LinearLayout.LayoutParams(0,-1,1));
        codeButton=button("扫码连接",this::showCode);codeButton.setEnabled(false);transferActions.addView(codeButton,new LinearLayout.LayoutParams(0,-1,1));
        connection=muted("手机或电脑与电视连接同一局域网",11);connection.setPadding(0,px(8),0,0);right.addView(connection);
        status=muted("",11);status.setPadding(0,px(10),0,px(10));right.addView(status);
        qr=new ImageView(activity);qr.setContentDescription("扫码打开文件传输页");qr.setVisibility(View.GONE);right.addView(qr,new LinearLayout.LayoutParams(px(150),px(150)));
        ScrollView operationScroll=new ScrollView(activity);operationScroll.setFillViewport(false);right.addView(operationScroll,new LinearLayout.LayoutParams(-1,0,1));actions=column();operationScroll.addView(actions);
        androidx.core.content.ContextCompat.registerReceiver(activity,screen,new IntentFilter(Intent.ACTION_SCREEN_OFF),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        if(state!=null){source=state.getInt("source",0);usb=source==2;listPosition=state.getInt("position",0);String path=state.getString("folder"),edge=state.getString("boundary");if(path!=null&&edge!=null){folder=new File(path);boundary=new File(edge);}if(Build.VERSION.SDK_INT>=21&&state.getString("tree")!=null){tree=Uri.parse(state.getString("tree"));current=Uri.parse(state.getString("current"));ArrayList<String> saved=state.getStringArrayList("parents");if(saved!=null)for(String uri:saved)parents.addLast(Uri.parse(uri));}}
        try{store=new FileTransferStore(new File(getFilesDir(),"received-files"));refresh(false);}catch(IOException error){location.setText(error.getMessage());start.setEnabled(false);}
    }
    private Button sourceButton(LinearLayout parent,String text,Runnable action){Button button=button(text,action);button.setId(androidx.core.view.ViewCompat.generateViewId());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(36));p.bottomMargin=px(10);if(parent.getChildCount()>0){View previous=parent.getChildAt(parent.getChildCount()-1);previous.setNextFocusDownId(button.getId());button.setNextFocusUpId(previous.getId());}else button.setNextFocusUpId(managementTab().getId());parent.addView(button,p);button.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)list.requestFocus();return true;}return false;});return button;}
    private void choose(int source){
        if(busy||networkReceiving){message("请先取消或等待当前文件传输完成");return;}
        this.source=source;usb=source==2;folder=boundary=null;tree=current=null;parents.clear();selected=null;selectedSource=null;renderActions();
        if(source>0&&Build.VERSION.SDK_INT>=29){pickTree();return;}
        if(source>0&&Build.VERSION.SDK_INT>=23&&activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION);return;}
        refresh(true);
    }
    private void refresh(boolean focus){
        if(store==null)return;int request=++listing;final int desired=focus?0:list.hasFocus()?Math.max(0,list.getSelectedItemPosition()):listPosition;final String keep=focus?null:selected!=null?selected.id:selectedSource!=null?selectedSource.uri.toString():null;File path=folder;Uri root=tree,at=current;int mode=source;
        worker.execute(()->{
            List<?> entries;String problem=null;long used=store.used();
            try{entries=mode==0?store.list():root!=null?FileTransferSource.Documents.children(activity,root,at):path!=null?FileTransferSource.children(path):FileTransferSource.volumes(mode==2);}
            catch(IOException|SecurityException error){entries=Collections.emptyList();problem="目录无法读取，请检查授权或 U 盘连接";}
            final List<?> values=entries;final String failure=problem;
            main.post(()->{if(destroyed||!showing||request!=listing)return;space.setText(FileTransferSource.size(used)+" / 500 MB");location.setText(failure!=null?failure:mode==0?"已接收文件":root!=null?"已授权目录":path!=null?path.getName():mode==2?"选择 U 盘":"选择本地存储");
                ArrayList<String> labels=new ArrayList<>();for(Object entry:values)labels.add(entry instanceof FileTransferStore.Entry?((FileTransferStore.Entry)entry).name+"  "+FileTransferSource.size(((FileTransferStore.Entry)entry).bytes):entry.toString());
                if(labels.isEmpty()&&failure==null)location.setText(mode==0?"尚未接收文件":mode==2?"未发现可读取的 U 盘，可使用授权目录":"没有可读取的文件，可使用选择文件");
                list.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_activated_1,labels){@Override public View getView(int position,View convert,android.view.ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextColor(TEXT);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));view.setMinHeight(px(46));view.setMaxLines(2);return view;}});
                list.setOnItemClickListener((parent,view,index,id)->select(values.get(index)));list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int index,long id){if(index>=0&&index<values.size())highlight(values.get(index));}});
                int position=desired;if(keep!=null)for(int i=0;i<values.size();i++){Object item=values.get(i);String key=item instanceof FileTransferStore.Entry?((FileTransferStore.Entry)item).id:((FileTransferSource.Item)item).uri.toString();if(keep.equals(key)){position=i;break;}}list.setSelection(Math.min(Math.max(0,position),Math.max(0,values.size()-1)));listPosition=0;if(values.isEmpty()){selected=null;selectedSource=null;renderActions();}if(focus)list.requestFocus();
            });
        });
    }
    private void highlight(Object value){selected=value instanceof FileTransferStore.Entry?(FileTransferStore.Entry)value:null;selectedSource=value instanceof FileTransferSource.Item?(FileTransferSource.Item)value:null;renderActions();}
    private void select(Object value){
        if(busy)return;highlight(value);
        if(selectedSource!=null&&selectedSource.directory){if(tree!=null){parents.push(current);current=selectedSource.uri;}else{if(folder==null)boundary=selectedSource.file;folder=selectedSource.file;}refresh(true);}else if(actions.getChildCount()>1)actions.getChildAt(1).requestFocus();
    }
    private void renderActions(){
        if(actions==null)return;qr.setVisibility(server!=null&&selected==null&&(selectedSource==null||selectedSource.directory)&&!busy&&!networkReceiving?View.VISIBLE:View.GONE);actions.removeAllViews();String name=selected!=null?selected.name:selectedSource!=null?selectedSource.name:"选择文件后可传输或设为壁纸";TextView title=label(name,14);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);actions.addView(title);
        if(busy||networkReceiving){action("取消当前文件",()->{if(networkReceiving&&server!=null){server.cancelCurrent();status.setText("正在取消接收…");}else{jobGeneration++;busy=false;status.setText("已取消");renderActions();}});return;}
        if(selected!=null){final FileTransferStore.Entry entry=selected;
            if(FileTransferPolicy.image(entry.name)||FileTransferPolicy.video(entry.name))action("预览并设为壁纸",()->preview(entry.id));
            action("传到手机或电脑",()->offer(entry.id));action("删除文件",()->confirmDelete(entry));
        }else if(selectedSource!=null&&!selectedSource.directory){final FileTransferSource.Item item=selectedSource;action("保存到已接收",()->receive(item,false,false));action("传到手机或电脑",()->receive(item,true,false));if(FileTransferPolicy.image(item.name)||FileTransferPolicy.video(item.name))action("预览并设为壁纸",()->receive(item,false,true));}
    }
    private void action(String text,Runnable action){Button button=button(text,action);button.setId(androidx.core.view.ViewCompat.generateViewId());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(36));p.topMargin=px(10);actions.addView(button,p);button.setNextFocusLeftId(list.getId());}
    private void confirmDelete(FileTransferStore.Entry entry){Sheet sheet=new Sheet("删除接收文件？");sheet.text(entry.name+"\n已导入的壁纸不受影响。");Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("删除",()->{sheet.dialog.dismiss();worker.execute(()->{try{store.remove(entry.id);activity.getSharedPreferences("file-transfer-wallpapers",0).edit().remove(entry.id).apply();main.post(()->{selected=null;renderActions();refresh(false);});}catch(IOException error){main.post(()->message("文件删除未完成"));}});});sheet.show(cancel);}
    private void pick(){Intent intent=new Intent(Build.VERSION.SDK_INT>=19?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);try{activity.startActivityForResult(intent,PICK);}catch(ActivityNotFoundException|SecurityException error){if(Build.VERSION.SDK_INT>=19)try{activity.startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),PICK);return;}catch(ActivityNotFoundException|SecurityException unavailable){}message("系统文件选择器不可用，请选择可读取的本地存储或使用局域网传输");}}
    private void pickTree(){if(Build.VERSION.SDK_INT<21){pick();return;}try{activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),TREE);}catch(ActivityNotFoundException|SecurityException error){message("目录授权入口不可用，请尝试选择文件或局域网传输");}}
    @Override void activityResult(int request,int result,Intent data){
        if(result!=android.app.Activity.RESULT_OK||data==null||data.getData()==null)return;
        if(request==TREE&&Build.VERSION.SDK_INT>=21){tree=data.getData();current=FileTransferSource.Documents.root(tree);parents.clear();source=usb?2:1;refresh(false);}
        else if(request==PICK){pending=data.getData();if(showing)receivePending();}
    }
    @Override void permissionResult(int request,int[] results){if(request==PERMISSION){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)refresh(false);else message("未获得读取授权，可使用选择文件或局域网传输");}}
    private void receivePending(){Uri uri=pending;pending=null;if(uri==null)return;worker.execute(()->{try{FileTransferSource.Item item=FileTransferSource.selected(activity,uri);main.post(()->{if(showing)receive(item,false,false);});}catch(IOException|SecurityException error){main.post(()->message("选中文件无法读取"));}});}
    private void receive(FileTransferSource.Item item,boolean offer,boolean preview){
        if(busy||networkReceiving||store==null)return;
        if(offer&&server==null){startSession(()->receive(item,true,preview));return;}
        busy=true;final int job=++jobGeneration;renderActions();status.setText("正在读取文件…");
        worker.execute(()->{
            try(InputStream input=item.file!=null?new FileInputStream(item.file):activity.getContentResolver().openInputStream(item.uri)){
                if(input==null)throw new IOException("文件不可读取");
                FileTransferStore.Entry entry=store.receive(input,item.bytes,item.name,()->!destroyed&&job==jobGeneration,(bytes,total)->progress(bytes,total,job));
                main.post(()->{if(destroyed||job!=jobGeneration)return;busy=false;selected=entry;selectedSource=null;source=0;renderActions();refresh(false);status.setText("文件已保存，可拔出 U 盘");if(offer)offer(entry.id);else if(preview)preview(entry.id);});
            }catch(IOException|RuntimeException error){main.post(()->{if(destroyed||job!=jobGeneration)return;busy=false;renderActions();status.setText("传输未完成，请检查文件、容量或 U 盘连接");});}
        });
    }
    private volatile long lastProgress;
    private void progress(long bytes,long total,int job){long now=SystemClock.elapsedRealtime();if(now-lastProgress<400&&bytes!=total)return;lastProgress=now;main.post(()->{if(!destroyed&&(job<0?-job==sessionGeneration:job==jobGeneration))status.setText("正在接收  "+FileTransferSource.size(bytes)+(total>0?" / "+FileTransferSource.size(total):""));});}
    private void preview(String id){activity.startActivity(new Intent(activity,FileTransferPreviewActivity.class).putExtra("file",id));}
    private void offer(String id){if(server==null){startSession(()->offer(id));return;}try{server.offer(id);status.setText("已加入下载列表，请在手机或电脑页面刷新");}catch(IOException error){message("文件已移除，请重新选择");}}
    private void startSession(Runnable ready){
        if(store==null||!showing)return;String host=WallpaperUploadPanel.lanAddress(activity);if(host==null){status.setText("未连接局域网，请连接 Wi-Fi 或网线");return;}
        stopSession();int attempt=++sessionGeneration;start.setEnabled(false);status.setText("正在开启传输…");
        worker.execute(()->{
            FileTransferServer candidate=new FileTransferServer(host,store,name->activity.getAssets().open("file-transfer/"+name),new FileTransferServer.Listener(){public void started(){main.post(()->{if(attempt==sessionGeneration){networkReceiving=true;renderActions();}});}public void finished(boolean success){main.post(()->{if(attempt==sessionGeneration){networkReceiving=false;if(!success)status.setText("接收未完成，文件未保存");renderActions();}});}public void progress(long bytes,long total){if(attempt==sessionGeneration)FileTransferPanel.this.progress(bytes,total,-attempt);}public void received(FileTransferStore.Entry entry){DiagnosticLog.info("file_transfer.received","bytes="+entry.bytes);main.post(()->{if(attempt==sessionGeneration&&showing){status.setText("已接收文件");refresh(false);}});}});
            try{candidate.listen();BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,320,320,Collections.singletonMap(EncodeHintType.MARGIN,2));int[] pixels=new int[320*320];for(int y=0;y<320;y++)for(int x=0;x<320;x++)pixels[y*320+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;Bitmap bitmap=Bitmap.createBitmap(pixels,320,320,Bitmap.Config.ARGB_8888);
                main.post(()->{if(destroyed||!showing||attempt!=sessionGeneration){bitmap.recycle();candidate.stop();return;}server=candidate;qrImage=bitmap;qr.setImageBitmap(bitmap);qr.setVisibility(View.VISIBLE);start.setEnabled(true);start.setText("结束传输");codeButton.setEnabled(true);renderActions();connection.setText(candidate.shortAddress()+"\n连接码  "+candidate.code());status.setText("等待传输文件");main.postDelayed(check,2000);if(ready!=null)ready.run();});
            }catch(IOException|WriterException error){candidate.stop();main.post(()->{if(attempt==sessionGeneration){start.setEnabled(true);status.setText("无法开启传输，请检查网络后重试");}});}
        });
    }
    private void showCode(){FileTransferServer active=server;if(active==null||qrImage==null)return;Sheet sheet=new Sheet("扫码连接文件传输");sheet.text(active.shortAddress()+"\n连接码  "+active.code());ImageView image=new ImageView(activity);image.setImageBitmap(qrImage.copy(Bitmap.Config.ARGB_8888,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(158),px(158));p.gravity=Gravity.CENTER_HORIZONTAL;sheet.body.addView(image,p);Button back=sheet.action("返回",sheet.dialog::dismiss);codeDialog=sheet.dialog;sheet.dialog.setOnDismissListener(d->{image.setImageDrawable(null);if(codeDialog==sheet.dialog)codeDialog=null;});sheet.show(back);}
    private void stopSession(){++sessionGeneration;if(codeDialog!=null){codeDialog.dismiss();codeDialog=null;}if(codeButton!=null)codeButton.setEnabled(false);main.removeCallbacks(check);FileTransferServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}if(qr!=null){qr.setImageDrawable(null);qr.setVisibility(View.GONE);}if(qrImage!=null){qrImage.recycle();qrImage=null;}if(start!=null){start.setEnabled(store!=null);start.setText("开启传输");}networkReceiving=false;if(connection!=null)connection.setText("手机或电脑与电视连接同一局域网");if(status!=null)status.setText("传输已关闭");if(actions!=null)renderActions();}
    private void up(){if(busy)return;if(tree!=null){if(parents.isEmpty()){tree=current=null;source=0;selectedSource=null;renderActions();refresh(true);}else{current=parents.pop();refresh(true);}}else if(folder!=null){folder=folder.equals(boundary)?null:folder.getParentFile();refresh(true);}else managementTab().requestFocus();}
    @Override void focusManagementContent(){received.requestFocus();}
    @Override public void handleBack(){if(busy){jobGeneration++;busy=false;renderActions();status.setText("已取消");}else if(!managementTabFocused())up();else super.handleBack();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("source",source);state.putInt("position",Math.max(0,list.getSelectedItemPosition()));if(folder!=null){state.putString("folder",folder.getPath());state.putString("boundary",boundary.getPath());}if(tree!=null){state.putString("tree",tree.toString());state.putString("current",current.toString());ArrayList<String> saved=new ArrayList<>();for(Uri uri:parents)saved.add(uri.toString());state.putStringArrayList("parents",saved);}super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();refresh(false);if(pending!=null)receivePending();}
    @Override protected void onPause(){super.onPause();listPosition=Math.max(0,list.getSelectedItemPosition());listing++;jobGeneration++;busy=false;stopSession();}
    @Override protected void onDestroy(){destroyed=true;jobGeneration++;stopSession();activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
