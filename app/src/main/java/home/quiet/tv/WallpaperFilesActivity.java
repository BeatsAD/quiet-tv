package home.quiet.tv;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit file selection; originals are never changed and USB is not needed after import. */
public final class WallpaperFilesActivity extends Activity {
    static final int REQUEST=7301;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private boolean busy;
    private LinearLayout root;
    private TextView status;
    private ListView list;
    private File boundary;
    private int listing;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(5894);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,24,32,24);root.setBackgroundColor(0xff151a21);setContentView(root);
        Button back=new Button(this);back.setText("取消");back.setOnClickListener(v->finish());root.addView(back);
        status=new TextView(this);status.setTextColor(0xffeeeeee);status.setTextSize(20);status.setPadding(0,16,0,16);root.addView(status);
        list=new ListView(this);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        if(saved==null)systemPicker();else files();
    }
    private void systemPicker(){
        String[] actions=Build.VERSION.SDK_INT>=19?new String[]{Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_GET_CONTENT}:new String[]{Intent.ACTION_GET_CONTENT};
        for(String action:actions){
            Intent pick=new Intent(action).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            if(Build.VERSION.SDK_INT>=19)pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
            try{startActivityForResult(pick,1);status.setText("请选择本地或 U 盘中的图片、视频");return;}
            catch(ActivityNotFoundException|SecurityException unavailable){/* Try an installed content picker before browsing permitted storage. */}
        }
        files();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==1){if(result==RESULT_OK&&data!=null&&data.getData()!=null)importFile(data.getData(),null);else finish();}
    }
    private void files(){
        if(Build.VERSION.SDK_INT>=29){status.setText("系统文件选择器不可用，无法取得本地或 U 盘文件授权。可返回使用局域网上传。");return;}
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},2);return;}
        browse(null);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==2&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)browse(null);else status.setText("未获得读取授权，可返回使用局域网上传。");
    }
    private void browse(File target){
        int request=++listing;status.setText("正在读取目录…");list.setEnabled(false);
        worker.execute(()->{
            ArrayList<File> choices=new ArrayList<>();
            try{
                if(target==null){
                    addRoot(choices,Environment.getExternalStorageDirectory());
                    File[] volumes=new File("/storage").listFiles();if(volumes!=null)for(File volume:volumes)if(!volume.getName().equals("self")&&!volume.getName().equals("emulated"))addRoot(choices,volume);
                    addRoot(choices,new File("/mnt/usb_storage"));
                }else{
                    File[] entries=target.listFiles();if(entries!=null)for(File file:entries)if(!file.isHidden()&&file.canRead()&&(file.isDirectory()||WallpaperImport.media(file.getName())))choices.add(file);
                    Collections.sort(choices,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):a.getName().compareToIgnoreCase(b.getName()));
                }
            }catch(SecurityException ignored){choices.clear();}
            runOnUiThread(()->{
                if(closed||request!=listing)return;list.setEnabled(true);status.setText(choices.isEmpty()?"没有可读取的文件；请检查 U 盘是否已挂载或使用局域网上传。":target==null?"选择本地存储或 U 盘":"选择图片或视频  "+target.getName());
                ArrayList<String> labels=new ArrayList<>();if(target!=null)labels.add("返回上一级");for(File file:choices)labels.add(file.isDirectory()?"[文件夹] "+file.getName():file.getName());
                list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));
                list.setOnItemClickListener((p,v,index,id)->{
                    if(busy)return;
                    if(target!=null&&index==0){browse(target.equals(boundary)?null:target.getParentFile());return;}
                    File file=choices.get(index-(target==null?0:1));
                    if(file.isDirectory()){if(target==null)boundary=file;browse(file);}else importFile(Uri.fromFile(file),file);
                });list.requestFocus();
            });
        });
    }
    private static void addRoot(List<File> roots,File file){
        if(!file.isDirectory()||!file.canRead())return;
        try{for(File root:roots)if(root.getCanonicalFile().equals(file.getCanonicalFile()))return;roots.add(file);}catch(IOException ignored){}
    }
    private void importFile(Uri uri,File file){
        if(busy)return;busy=true;list.setEnabled(false);status.setText("正在导入，完成后可拔出 U 盘…");
        worker.execute(()->{
            try{
                String name=file==null?"wallpaper":file.getName(),mime=file==null?getContentResolver().getType(uri):null;long size=file==null?-1:file.length();
                if(file==null)try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
                    if(cursor!=null&&cursor.moveToFirst()){int n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=cursor.getColumnIndex(OpenableColumns.SIZE);if(n>=0&&!cursor.isNull(n))name=cursor.getString(n);if(s>=0&&!cursor.isNull(s))size=cursor.getLong(s);}
                }
                WallpaperCatalog.Entry entry;
                try(InputStream input=file==null?getContentResolver().openInputStream(uri):new FileInputStream(file)){
                    if(input==null)throw new IOException("文件不可读取");
                    boolean video=WallpaperImport.video(name,mime);if(size>(video?WallpaperVideo.MAX_UPLOAD:WallpaperUploadServer.MAX_UPLOAD))throw new IOException("文件超过大小限制");
                    entry=WallpaperImport.accept(this,input,-1,name,video,()->!closed,"source=selected_file");
                }
                runOnUiThread(()->{if(!closed){setResult(RESULT_OK,new Intent().putExtra("wallpaper",entry.id));finish();}});
            }catch(IOException|RuntimeException|OutOfMemoryError failure){
                DiagnosticLog.info("wallpaper.import.failed","source=selected_file type="+failure.getClass().getSimpleName());
                runOnUiThread(()->{if(!closed){busy=false;status.setText("导入失败，请检查文件格式、大小、存储空间及读取授权。");Button retry=new Button(this);retry.setText("重新选择");retry.setOnClickListener(v->systemPicker());root.addView(retry);retry.requestFocus();}});
            }
        });
    }
    @Override public void finish(){closed=true;super.finish();}
    @Override protected void onDestroy(){closed=true;++listing;worker.shutdownNow();super.onDestroy();}
}
