package home.quiet.tv;

import java.io.*;
import java.security.*;
import java.util.*;

/** Disposable downloaded artwork. Applied covers live in AppCoverStore, outside this budget. */
final class IconLibraryCache {
    static final long MAX_BYTES=32L*1024*1024;
    private final File root;private final long budget;
    IconLibraryCache(File root){this(root,MAX_BYTES);}
    IconLibraryCache(File root,long budget){this.root=root;this.budget=budget;}
    static String hash(byte[] bytes){try{return UpdateTransfer.hex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    static String hash(File file)throws IOException{
        try(InputStream input=new FileInputStream(file)){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);return UpdateTransfer.hex(digest.digest());
        }catch(GeneralSecurityException e){throw new IOException(e);}
    }
    synchronized File find(IconLibraryCatalog.Asset asset)throws IOException{
        File file=new File(root,asset.sha256+".png");
        if(!file.isFile()||file.length()!=asset.bytes||!hash(file).equals(asset.sha256))return null;
        file.setLastModified(System.currentTimeMillis());return file;
    }
    synchronized File put(IconLibraryCatalog.Asset asset,byte[] bytes)throws IOException{
        asset.verify(bytes);File found=find(asset);if(found!=null)return found;
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法保存图标缓存");
        File file=new File(root,asset.sha256+".png"),pending=File.createTempFile("incoming-",".tmp",root);
        try{
            try(FileOutputStream stream=new FileOutputStream(pending)){stream.write(bytes);stream.getFD().sync();}
            if(!pending.renameTo(file))throw new IOException("无法保存已验证图标");
        }finally{if(pending.exists())pending.delete();}
        trim(file);return file;
    }
    private void trim(File keep){
        File[] files=root.listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.png"));if(files==null)return;
        long total=0;for(File file:files)total+=file.length();
        Arrays.sort(files,(a,b)->a.lastModified()<b.lastModified()?-1:a.lastModified()==b.lastModified()?0:1);
        for(File file:files){if(total<=budget)break;if(file.equals(keep))continue;long bytes=file.length();if(file.delete())total-=bytes;}
    }
}
