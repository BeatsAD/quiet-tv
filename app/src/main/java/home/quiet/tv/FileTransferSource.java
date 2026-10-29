package home.quiet.tv;

import android.annotation.TargetApi;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.storage.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.*;
import java.io.*;
import java.util.*;

/** Read-only media access and explicitly granted document trees; never requests all-files access. */
final class FileTransferSource {
    static final class Item {
        final String name;final long bytes;final boolean directory;final Uri uri;final File file;
        Item(String name,long bytes,boolean directory,Uri uri,File file){this.name=name==null||name.trim().isEmpty()?"未命名文件":name;this.bytes=bytes;this.directory=directory;this.uri=uri;this.file=file;}
        @Override public String toString(){return (directory?"[文件夹] ":"")+name+(directory?"":"  "+size(bytes));}
    }
    static String size(long bytes){return bytes<0?"大小未知":bytes>=1024*1024?String.format(Locale.ROOT,"%.1f MB",bytes/(1024f*1024)):bytes>=1024?String.format(Locale.ROOT,"%.1f KB",bytes/1024f):bytes+" B";}
    static Item file(File file){return new Item(file.getName(),file.isDirectory()?-1:file.length(),file.isDirectory(),Uri.fromFile(file),file);}
    static String[] missingPermissions(Context context){
        if(Build.VERSION.SDK_INT<23)return new String[0];
        String[] needed=Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO}:new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        ArrayList<String> missing=new ArrayList<>();for(String permission:needed)if(context.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)missing.add(permission);
        return missing.toArray(new String[0]);
    }
    static boolean requestPermission(Activity activity,int request){if(Build.VERSION.SDK_INT<23)return false;String[] missing=missingPermissions(activity);if(missing.length==0)return false;activity.requestPermissions(missing,request);return true;}
    static List<Item> volumes(Context context,boolean usb){
        List<Item> result=volumes(usb);
        if(Build.VERSION.SDK_INT>=30){
            StorageManager manager=(StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
            if(manager!=null)for(StorageVolume volume:manager.getStorageVolumes())if(volume.isPrimary()!=usb){File directory=volume.getDirectory();if(directory!=null)add(result,directory);}
        }
        return result;
    }
    static List<Item> volumes(boolean usb){
        ArrayList<Item> result=new ArrayList<>();if(!usb)add(result,Environment.getExternalStorageDirectory());
        else{File[] files=new File("/storage").listFiles();if(files!=null)for(File file:files)if(!file.getName().equals("emulated")&&!file.getName().equals("self"))add(result,file);add(result,new File("/mnt/usb_storage"));}
        return result;
    }
    private static void add(List<Item> items,File file){
        if(!file.isDirectory()||!file.canRead())return;
        try{for(Item item:items)if(item.file.getCanonicalFile().equals(file.getCanonicalFile()))return;items.add(file(file));}catch(IOException ignored){}
    }
    static List<Item> children(File parent)throws IOException{
        File[] files=parent.listFiles();if(files==null)throw new IOException("目录无法读取，请检查权限或 U 盘是否仍已连接");
        ArrayList<Item> result=new ArrayList<>();String boundary=parent.getCanonicalPath()+File.separator;
        for(File file:files)if(!file.isHidden()&&file.canRead()&&file.getCanonicalPath().startsWith(boundary))result.add(file(file));
        sort(result);return result;
    }
    static Item selected(Context context,Uri uri)throws IOException{
        if("file".equals(uri.getScheme())){
            File selected=new File(uri.getPath());String path=selected.getCanonicalPath();List<Item> roots=new ArrayList<>(volumes(context,false));roots.addAll(volumes(context,true));
            for(Item root:roots)if(path.startsWith(root.file.getCanonicalPath()+File.separator)&&selected.isFile()&&selected.canRead())return file(selected);
        }
        if(!"content".equals(uri.getScheme()))throw new IOException("文件选择器未提供可读取的文件授权");
        String name=null;long size=-1;
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){name=cursor.getString(0);if(!cursor.isNull(1))size=cursor.getLong(1);}
        }catch(RuntimeException e){throw new IOException("文件信息无法读取",e);}
        return new Item(FileTransferPolicy.name(name),size,false,uri,null);
    }
    private static void sort(List<Item> items){Collections.sort(items,(a,b)->a.directory!=b.directory?(a.directory?-1:1):a.name.compareToIgnoreCase(b.name));}
    @TargetApi(21) static final class Documents {
    static List<Item> children(Context context,Uri tree,Uri current)throws IOException{
        ArrayList<Item> items=new ArrayList<>();String id=DocumentsContract.getDocumentId(current);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE};
        try(Cursor cursor=context.getContentResolver().query(children,columns,null,null,null)){
            if(cursor==null)throw new IOException("目录无法读取");
            while(cursor.moveToNext()){Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));items.add(new Item(cursor.getString(1),cursor.isNull(3)?-1:cursor.getLong(3),DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)),uri,null));}
        }catch(RuntimeException e){throw new IOException("目录授权已失效或 U 盘已拔出",e);}
        sort(items);return items;
    }
    static Uri root(Uri tree){return DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}
    }
    private FileTransferSource(){}
}
