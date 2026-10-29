package home.quiet.tv;

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
    private LinearLayout connectionCard;
    private TextView count;
    private Button upButton,accessButton,cancelTransfer;
    private android.app.Dialog fileDialog;
    private final android.util.LruCache<String,Bitmap> thumbnails=new android.util.LruCache<String,Bitmap>(2048){@Override protected int sizeOf(String key,Bitmap image){return image.getByteCount()/1024;}};
    private ImageView qr;
    private Bitmap qrImage;
    private Button start,received,local,removable;
    private android.app.Dialog codeDialog;
    private FileTransferStore.Entry selected;
    private FileTransferSource.Item selectedSource;
    private final Runnable check=new Runnable(){public void run(){FileTransferServer active=server;if(active==null)return;if(active.expired()||!active.shortAddress().startsWith("http://"+WallpaperUploadPanel.lanAddress(activity)+":")){stopSession();status.setText("传输已结束，请重新开启");}else main.postDelayed(this,2000);}};
    FileTransferPanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        LinearLayout body=managementContent();space=muted("",11);space.setSingleLine(true);space.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        LinearLayout columns=row();columns.setGravity(Gravity.TOP);body.addView(columns,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout left=column();columns.addView(left,new LinearLayout.LayoutParams(px(244),-1));
        TextView caption=muted("手机或电脑",14);managementInfo.addView(caption,new LinearLayout.LayoutParams(px(244),-1));
        connectionCard=column();connectionCard.setGravity(Gravity.CENTER);connectionCard.setPadding(px(20),px(16),px(20),px(12));connectionCard.setBackgroundDrawable(surface(0x0cffffff,0x06ffffff,0x18ffffff,18));left.addView(connectionCard,new LinearLayout.LayoutParams(-1,0,1));
        connection=center("把文件传到电视",17);connectionCard.addView(connection,new LinearLayout.LayoutParams(-1,-2));
        qr=new ImageView(activity);qr.setContentDescription("扫码打开文件传输页");qr.setVisibility(View.GONE);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(px(126),px(126));qp.gravity=Gravity.CENTER_HORIZONTAL;qp.topMargin=px(10);qp.bottomMargin=px(8);connectionCard.addView(qr,qp);
        status=muted("手机和电脑连接同一局域网",11);status.setGravity(Gravity.CENTER);status.setMinHeight(px(32));connectionCard.addView(status,new LinearLayout.LayoutParams(-1,-2));
        gap(connectionCard,10);start=button("开启传输",()->{if(server!=null)stopSession();else startSession(null);});start.setId(androidx.core.view.ViewCompat.generateViewId());connectionCard.addView(start,new LinearLayout.LayoutParams(-1,px(36)));
        cancelTransfer=button("取消当前文件",()->{if(networkReceiving&&server!=null){server.cancelCurrent();status.setText("正在取消接收…");}else{jobGeneration++;busy=false;status.setText("已取消");renderActions();}});cancelTransfer.setVisibility(View.GONE);connectionCard.addView(cancelTransfer,new LinearLayout.LayoutParams(-1,px(32)));
        LinearLayout right=column();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=px(30);columns.addView(right,rp);
        LinearLayout sources=row();LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,1);sp.leftMargin=px(30);managementInfo.addView(sources,sp);
        received=sourceButton(sources,"已接收",()->choose(0));local=sourceButton(sources,"本地存储",()->choose(1));removable=sourceButton(sources,"U 盘",()->choose(2));sources.addView(space,new LinearLayout.LayoutParams(0,-1,1));count=muted("",11);count.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-1);cp.leftMargin=px(12);sources.addView(count,cp);
        LinearLayout pathRow=row();right.addView(pathRow,new LinearLayout.LayoutParams(-1,px(32)));upButton=button("返回上级",this::up);pathRow.addView(upButton,new LinearLayout.LayoutParams(-2,px(28)));upButton.setVisibility(View.GONE);
        location=muted("最近接收",11);location.setSingleLine(true);location.setEllipsize(android.text.TextUtils.TruncateAt.END);pathRow.addView(location,new LinearLayout.LayoutParams(0,-1,1));
        accessButton=button("选择文件",this::accessOptions);accessButton.setVisibility(View.GONE);pathRow.addView(accessButton,new LinearLayout.LayoutParams(-2,px(28)));
        list=new KeyFeedback.List(activity);list.setId(androidx.core.view.ViewCompat.generateViewId());list.setDivider(null);list.setDividerHeight(px(4));list.setSelector(listSurface());right.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_LEFT){start.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP&&list.getSelectedItemPosition()<=0){sourceTab().requestFocus();return true;}return false;});
        start.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)focusFiles();return true;}return false;});
        for(Button tab:new Button[]{received,local,removable}){tab.setNextFocusUpId(managementTab().getId());tab.setNextFocusDownId(list.getId());}
        androidx.core.content.ContextCompat.registerReceiver(activity,screen,new IntentFilter(Intent.ACTION_SCREEN_OFF),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        if(state!=null){source=state.getInt("source",0);usb=source==2;listPosition=state.getInt("position",0);String path=state.getString("folder"),edge=state.getString("boundary");if(path!=null&&edge!=null){folder=new File(path);boundary=new File(edge);}if(Build.VERSION.SDK_INT>=21&&state.getString("tree")!=null){tree=Uri.parse(state.getString("tree"));current=Uri.parse(state.getString("current"));ArrayList<String> saved=state.getStringArrayList("parents");if(saved!=null)for(String uri:saved)parents.addLast(Uri.parse(uri));}}
        try{store=new FileTransferStore(new File(getFilesDir(),"received-files"));refresh(false);}catch(IOException error){location.setText(error.getMessage());start.setEnabled(false);}
    }
    private Button sourceTab(){return source==0?received:source==1?local:removable;}
    private void focusFiles(){if(list.getCount()>0)list.requestFocus();else if(accessButton.isShown())accessButton.requestFocus();else sourceTab().requestFocus();}
    private Button sourceButton(LinearLayout parent,String text,Runnable action){
        Button choice=button(text,action);choice.setId(androidx.core.view.ViewCompat.generateViewId());activity.styleTab(choice,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.rightMargin=px(6);parent.addView(choice,p);
        choice.setOnKeyListener((v,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN)focusFiles();
            else if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocus();
            else {Button[] tabs={received,local,removable};int index=0;for(int i=0;i<tabs.length;i++)if(tabs[i]==v)index=i;int next=Math.max(0,Math.min(tabs.length-1,index+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));if(next!=index){tabs[next].requestFocus();tabs[next].performClick();}}
            return true;
        });return choice;
    }
    private void accessOptions(){Sheet sheet=new Sheet("读取本地文件");sheet.text("可选择单个文件，或授权一个目录供本页浏览。");Button first=sheet.option("系统选择文件",()->{sheet.dialog.dismiss();pick();});if(Build.VERSION.SDK_INT>=21)sheet.option("授权目录",()->{sheet.dialog.dismiss();pickTree();});sheet.option("重新读取存储",()->{sheet.dialog.dismiss();if(!FileTransferSource.requestPermission(activity,PERMISSION))refresh(true);});sheet.action("返回",sheet.dialog::dismiss);sheet.show(first);}
    private void choose(int source){
        if(busy||networkReceiving){message("请先取消或等待当前文件传输完成");return;}
        this.source=source;usb=source==2;folder=boundary=null;tree=current=null;parents.clear();selected=null;selectedSource=null;renderActions();
        if(source>0&&FileTransferSource.requestPermission(activity,PERMISSION)){refresh(false);return;}
        refresh(false);
    }
    private void refresh(boolean focus){
        if(store==null)return;int request=++listing;final int desired=focus?0:list.hasFocus()?Math.max(0,list.getSelectedItemPosition()):listPosition;final String keep=focus?null:selected!=null?selected.id:selectedSource!=null?selectedSource.uri.toString():null;File path=folder;Uri root=tree,at=current;int mode=source;
        worker.execute(()->{
            List<?> entries;String problem=null;long used=store.used();
            try{entries=mode==0?store.list():root!=null?FileTransferSource.Documents.children(activity,root,at):path!=null?FileTransferSource.children(path):FileTransferSource.volumes(activity,mode==2);}
            catch(IOException|SecurityException error){entries=Collections.emptyList();problem="目录无法读取，请检查授权或 U 盘连接";}
            final List<?> values=entries;final String failure=problem;
            main.post(()->{if(destroyed||!showing||request!=listing)return;space.setText("已接收  "+FileTransferSource.size(used)+" / 500 MB");location.setText(failure!=null?failure:mode==0?"已接收文件":root!=null?"已授权目录":path!=null?path.getName():mode==2?"选择 U 盘":"选择本地存储");
                received.setSelected(mode==0);local.setSelected(mode==1);removable.setSelected(mode==2);count.setText(values.size()+" 项");upButton.setVisibility(path!=null||root!=null?View.VISIBLE:View.GONE);accessButton.setVisibility(mode>0?View.VISIBLE:View.GONE);
                if(values.isEmpty()&&failure==null)location.setText(mode==0?"还没有接收文件":mode==2?"未发现可读取的 U 盘，请检查连接或授权目录":"没有可读取的文件，请授权读取或选择文件");
                list.setAdapter(new BaseAdapter(){public int getCount(){return values.size();}public Object getItem(int position){return values.get(position);}public long getItemId(int position){return position;}public View getView(int position,View convert,ViewGroup parent){Object item=values.get(position);LinearLayout row=FileTransferPanel.this.row();row.setPadding(px(10),px(7),px(10),px(7));row.setMinimumHeight(px(60));ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(image,new LinearLayout.LayoutParams(px(62),px(38)));boolean receipt=item instanceof FileTransferStore.Entry;String name=receipt?((FileTransferStore.Entry)item).name:((FileTransferSource.Item)item).name;boolean directory=!receipt&&((FileTransferSource.Item)item).directory;long bytes=receipt?((FileTransferStore.Entry)item).bytes:((FileTransferSource.Item)item).bytes;
                    image.setImageDrawable(UiCompat.drawable(activity,directory?R.drawable.ic_transfer_folder:FileTransferPolicy.video(name)?R.drawable.ic_transfer_video:FileTransferPolicy.image(name)?R.drawable.ic_home_wallpaper:R.drawable.ic_transfer_file));image.setPadding(px(16),px(4),px(16),px(4));
                    LinearLayout names=column();LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=px(16);row.addView(names,np);TextView title=label(name,14);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(title);TextView meta=muted(directory?"文件夹":(FileTransferPolicy.video(name)?"视频":FileTransferPolicy.image(name)?"图片":"文件")+"  "+FileTransferSource.size(bytes),10);names.addView(meta);row.setContentDescription(name);loadThumbnail(item,image);return row;}});
                list.setOnItemClickListener((parent,view,index,id)->select(values.get(index)));list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int index,long id){if(index>=0&&index<values.size())highlight(values.get(index));}});
                int position=desired;if(keep!=null)for(int i=0;i<values.size();i++){Object item=values.get(i);String key=item instanceof FileTransferStore.Entry?((FileTransferStore.Entry)item).id:((FileTransferSource.Item)item).uri.toString();if(keep.equals(key)){position=i;break;}}list.setSelection(Math.min(Math.max(0,position),Math.max(0,values.size()-1)));listPosition=0;if(values.isEmpty()){selected=null;selectedSource=null;renderActions();}if(focus)focusFiles();
            });
        });
    }
    private void highlight(Object value){selected=value instanceof FileTransferStore.Entry?(FileTransferStore.Entry)value:null;selectedSource=value instanceof FileTransferSource.Item?(FileTransferSource.Item)value:null;renderActions();}
    private void select(Object value){
        if(busy)return;highlight(value);
        if(selectedSource!=null&&selectedSource.directory){if(tree!=null){parents.push(current);current=selectedSource.uri;}else{if(folder==null)boundary=selectedSource.file;folder=selectedSource.file;}refresh(true);}else showFileActions();
    }
    private void renderActions(){if(qr==null)return;qr.setVisibility(server!=null?View.VISIBLE:View.GONE);cancelTransfer.setVisibility(busy||networkReceiving?View.VISIBLE:View.GONE);}
    private void loadThumbnail(Object item,ImageView image){
        String name=item instanceof FileTransferStore.Entry?((FileTransferStore.Entry)item).name:((FileTransferSource.Item)item).name;if(!FileTransferPolicy.image(name))return;
        String key=item instanceof FileTransferStore.Entry?((FileTransferStore.Entry)item).id:((FileTransferSource.Item)item).uri.toString();Bitmap cached=thumbnails.get(key);if(cached!=null){image.setPadding(0,0,0,0);image.setImageBitmap(cached);return;}
        worker.execute(()->{try{File file=item instanceof FileTransferStore.Entry?store.file(((FileTransferStore.Entry)item).id):((FileTransferSource.Item)item).file;Uri uri=item instanceof FileTransferSource.Item?((FileTransferSource.Item)item).uri:null;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=file!=null?new FileInputStream(file):activity.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>px(248)||bounds.outHeight/options.inSampleSize>px(152))options.inSampleSize*=2;Bitmap bitmap;try(InputStream in=file!=null?new FileInputStream(file):activity.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap!=null){thumbnails.put(key,bitmap);main.post(()->{if(!destroyed&&image.getWindowToken()!=null){image.setPadding(0,0,0,0);image.setImageBitmap(bitmap);}});}}catch(IOException|RuntimeException|OutOfMemoryError ignored){}});
    }
    private void showFileActions(){
        if(busy||networkReceiving)return;if(selected==null&&(selectedSource==null||selectedSource.directory))return;
        dismissFileDialog();Sheet sheet=new Sheet("文件操作");sheet.text((selected!=null?selected.name:selectedSource.name)+"  "+FileTransferSource.size(selected!=null?selected.bytes:selectedSource.bytes));Button first=null;
        if(selected!=null){final FileTransferStore.Entry entry=selected;if(FileTransferPolicy.image(entry.name)||FileTransferPolicy.video(entry.name))first=sheet.option("预览并设为壁纸",()->{sheet.dialog.dismiss();preview(entry.id);});Button send=sheet.option("传到手机或电脑",()->{sheet.dialog.dismiss();offer(entry.id);});if(first==null)first=send;sheet.option("删除文件",()->{sheet.dialog.dismiss();confirmDelete(entry);});}
        else {final FileTransferSource.Item item=selectedSource;if(FileTransferPolicy.image(item.name)||FileTransferPolicy.video(item.name))first=sheet.option("预览并设为壁纸",()->{sheet.dialog.dismiss();receive(item,false,true);});Button save=sheet.option("保存到已接收",()->{sheet.dialog.dismiss();receive(item,false,false);});if(first==null)first=save;sheet.option("传到手机或电脑",()->{sheet.dialog.dismiss();receive(item,true,false);});}
        for(int i=0;i<sheet.body.getChildCount();i++)if(sheet.body.getChildAt(i) instanceof Button)sheet.body.getChildAt(i).setBackgroundDrawable(ControlStyle.action(unit,11,0x0cffffff,0x22ffffff));
        if(first!=null){ControlStyle.choice(first,unit,11,0x0cffffff,0x22ffffff);first.setSelected(true);}
        sheet.action("关闭",sheet.dialog::dismiss);fileDialog=sheet.dialog;sheet.dialog.setOnDismissListener(d->{if(fileDialog==sheet.dialog)fileDialog=null;if(showing)list.post(this::focusFiles);});sheet.show(first);
    }
    private void dismissFileDialog(){if(fileDialog!=null){fileDialog.dismiss();fileDialog=null;}}
    private void confirmDelete(FileTransferStore.Entry entry){Sheet sheet=new Sheet("删除接收文件？");sheet.text(entry.name+"\n已导入的壁纸不受影响。");Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("删除",()->{sheet.dialog.dismiss();worker.execute(()->{try{store.remove(entry.id);activity.getSharedPreferences("file-transfer-wallpapers",0).edit().remove(entry.id).apply();main.post(()->{selected=null;renderActions();refresh(false);});}catch(IOException error){main.post(()->message("文件删除未完成"));}});});sheet.show(cancel);}
    private void pick(){Intent intent=new Intent(Build.VERSION.SDK_INT>=19?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);try{activity.startActivityForResult(intent,PICK);}catch(ActivityNotFoundException|SecurityException error){if(Build.VERSION.SDK_INT>=19)try{activity.startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),PICK);return;}catch(ActivityNotFoundException|SecurityException unavailable){}message("系统文件选择器不可用，请选择可读取的本地存储或使用局域网传输");}}
    private void pickTree(){if(Build.VERSION.SDK_INT<21){pick();return;}try{activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),TREE);}catch(ActivityNotFoundException|SecurityException error){message("目录授权入口不可用，请尝试选择文件或局域网传输");}}
    @Override void activityResult(int request,int result,Intent data){
        if(result!=android.app.Activity.RESULT_OK||data==null||data.getData()==null)return;
        if(request==TREE&&Build.VERSION.SDK_INT>=21){tree=data.getData();current=FileTransferSource.Documents.root(tree);parents.clear();source=usb?2:1;refresh(false);}
        else if(request==PICK){pending=data.getData();if(showing)receivePending();}
    }
    @Override void permissionResult(int request,int[] results){if(request==PERMISSION){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)refresh(false);else {refresh(false);feedback("未获得完整读取授权，可选择文件或授权目录");}}}
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
    private void offer(String id){if(server==null){startSession(()->offer(id));return;}try{server.offer(id);status.setText("文件已准备好，扫码下载");showCode();}catch(IOException error){message("文件已移除，请重新选择");}}
    private void startSession(Runnable ready){
        if(store==null||!showing)return;String host=WallpaperUploadPanel.lanAddress(activity);if(host==null){status.setText("未连接局域网，请连接 Wi-Fi 或网线");return;}
        stopSession();int attempt=++sessionGeneration;start.setEnabled(false);status.setText("正在开启传输…");
        worker.execute(()->{
            FileTransferServer candidate=new FileTransferServer(host,store,name->activity.getAssets().open("file-transfer/"+name),new FileTransferServer.Listener(){public void started(){main.post(()->{if(attempt==sessionGeneration){networkReceiving=true;renderActions();}});}public void finished(boolean success){main.post(()->{if(attempt==sessionGeneration){networkReceiving=false;if(!success)status.setText("接收未完成，文件未保存");renderActions();}});}public void progress(long bytes,long total){if(attempt==sessionGeneration)FileTransferPanel.this.progress(bytes,total,-attempt);}public void received(FileTransferStore.Entry entry){DiagnosticLog.info("file_transfer.received","bytes="+entry.bytes);main.post(()->{if(attempt==sessionGeneration&&showing){status.setText("已接收文件");refresh(false);}});}});
            try{candidate.listen();BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,320,320,Collections.singletonMap(EncodeHintType.MARGIN,2));int[] pixels=new int[320*320];for(int y=0;y<320;y++)for(int x=0;x<320;x++)pixels[y*320+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;Bitmap bitmap=Bitmap.createBitmap(pixels,320,320,Bitmap.Config.ARGB_8888);
                main.post(()->{if(destroyed||!showing||attempt!=sessionGeneration){bitmap.recycle();candidate.stop();return;}server=candidate;qrImage=bitmap;qr.setImageBitmap(bitmap);qr.setVisibility(View.VISIBLE);start.setEnabled(true);start.setText("结束传输");renderActions();connection.setText("等待接收文件");status.setText(candidate.shortAddress()+"\n连接码  "+candidate.code());main.postDelayed(check,2000);if(ready!=null)ready.run();});
            }catch(IOException|WriterException error){candidate.stop();main.post(()->{if(attempt==sessionGeneration){start.setEnabled(true);status.setText("无法开启传输，请检查网络后重试");}});}
        });
    }
    private void showCode(){FileTransferServer active=server;if(active==null||qrImage==null)return;Sheet sheet=new Sheet("扫码连接文件传输");sheet.text(active.shortAddress()+"\n连接码  "+active.code());ImageView image=new ImageView(activity);image.setImageBitmap(qrImage.copy(Bitmap.Config.ARGB_8888,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(158),px(158));p.gravity=Gravity.CENTER_HORIZONTAL;sheet.body.addView(image,p);Button back=sheet.action("返回",sheet.dialog::dismiss);codeDialog=sheet.dialog;sheet.dialog.setOnDismissListener(d->{image.setImageDrawable(null);if(codeDialog==sheet.dialog)codeDialog=null;});sheet.show(back);}
    private void stopSession(){++sessionGeneration;if(codeDialog!=null){codeDialog.dismiss();codeDialog=null;}main.removeCallbacks(check);FileTransferServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}if(qr!=null){qr.setImageDrawable(null);qr.setVisibility(View.GONE);}if(qrImage!=null){qrImage.recycle();qrImage=null;}if(start!=null){start.setEnabled(store!=null);start.setText("开启传输");}networkReceiving=false;if(connection!=null)connection.setText("把文件传到电视");if(status!=null)status.setText("传输已关闭");if(cancelTransfer!=null)renderActions();}
    private void up(){if(busy)return;if(tree!=null){if(parents.isEmpty()){tree=current=null;source=0;selectedSource=null;renderActions();refresh(true);}else{current=parents.pop();refresh(true);}}else if(folder!=null){folder=folder.equals(boundary)?null:folder.getParentFile();refresh(true);}else managementTab().requestFocus();}
    @Override void focusManagementContent(){sourceTab().requestFocus();}
    @Override public void handleBack(){if(busy){jobGeneration++;busy=false;renderActions();status.setText("已取消");}else if(!managementTabFocused())up();else super.handleBack();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("source",source);state.putInt("position",Math.max(0,list.getSelectedItemPosition()));if(folder!=null){state.putString("folder",folder.getPath());state.putString("boundary",boundary.getPath());}if(tree!=null){state.putString("tree",tree.toString());state.putString("current",current.toString());ArrayList<String> saved=new ArrayList<>();for(Uri uri:parents)saved.add(uri.toString());state.putStringArrayList("parents",saved);}super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();refresh(false);if(server==null)startSession(null);if(pending!=null)receivePending();}
    @Override protected void onPause(){super.onPause();listPosition=Math.max(0,list.getSelectedItemPosition());listing++;jobGeneration++;busy=false;dismissFileDialog();stopSession();}
    @Override protected void onDestroy(){destroyed=true;jobGeneration++;stopSession();activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);thumbnails.evictAll();worker.shutdown();super.onDestroy();}
}
