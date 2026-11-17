package home.quiet.components;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Installs only files named and hashed in a verified manifest, without replacing a live version. */
public final class ComponentFiles {
    public interface Active {void check()throws IOException;}
    public static String sha256(File file)throws IOException{try(InputStream in=new FileInputStream(file)){return hash(in,Long.MAX_VALUE,()->{});}}
    private static String hash(InputStream in,long limit,Active active)throws IOException{
        MessageDigest hash=digest();byte[] buffer=new byte[16384];long bytes=0;int count;
        while((count=in.read(buffer))!=-1){active.check();bytes+=count;if(bytes>limit)throw new IOException("组件文件超过约定大小");hash.update(buffer,0,count);}return hex(hash.digest());
    }
    public static File install(File root,File archive,ComponentManifest.Artifact artifact,Active active)throws IOException{
        if(archive.length()!=artifact.bytes||!sha256(archive).equals(artifact.sha256))throw new IOException("组件文件校验失败");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法创建组件目录");
        File pending=new File(root,"pending-"+artifact.id+"-"+UUID.randomUUID());if(!pending.mkdir())throw new IOException("无法准备组件目录");Set<String> seen=new HashSet<>();
        try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){
            active.check();ComponentManifest.Entry expected=artifact.files.get(entry.getName());
            if(entry.isDirectory()||expected==null||!seen.add(entry.getName())||entry.getSize()>expected.bytes)throw new IOException("组件包含未授权文件");
            File output=new File(pending,expected.name);MessageDigest hash=digest();long count=0;byte[] buffer=new byte[16384];int n;
            try(FileOutputStream out=new FileOutputStream(output)){while((n=zip.read(buffer))!=-1){active.check();count+=n;if(count>expected.bytes)throw new IOException("组件展开大小超限");hash.update(buffer,0,n);out.write(buffer,0,n);}out.getFD().sync();}
            if(count!=expected.bytes||!hex(hash.digest()).equals(expected.sha256))throw new IOException("组件内容校验失败");
            if(!output.setWritable(false,false))throw new IOException("无法保护组件文件");zip.closeEntry();
        }}
        if(seen.size()!=artifact.files.size())throw new IOException("组件文件不完整");
        if(artifact.id.equals("tailscale"))verifyCore(new File(pending,"libquietremote.so"));active.check();
        File ready=new File(root,artifact.id+"-"+artifact.version+"-"+UUID.randomUUID());if(!pending.renameTo(ready))throw new IOException("无法保存已验证组件");return ready;
    }
    public static void verify(File directory,ComponentManifest.Artifact artifact)throws IOException{
        for(ComponentManifest.Entry entry:artifact.files.values()){File file=new File(directory,entry.name);if(!file.isFile()||file.length()!=entry.bytes||!sha256(file).equals(entry.sha256))throw new IOException("本地组件已损坏，请重新下载");}
        if(artifact.id.equals("tailscale"))verifyCore(new File(directory,"libquietremote.so"));
    }
    private static boolean weatherFile(String name){return name.matches("weather\\.json(?:\\.bak|\\.new)?|download-weather-[a-f0-9]{64}\\.zip|(?:pending-weather-|weather-[0-9]+\\.[0-9]+\\.[0-9]+-)[a-f0-9-]{36}");}
    public static boolean hasWeather(File root){File[] files=root.listFiles();if(files!=null)for(File file:files)if(weatherFile(file.getName()))return true;return false;}
    /** Flat, component-owned files only. Never follow links or traverse another component. */
    public static void uninstallWeather(File root)throws IOException{
        if(!root.exists())return;File[] files=root.listFiles();if(files==null)throw new IOException("无法读取天气资源目录");
        // Retire every AtomicFile receipt before removing resources, including an interrupted write.
        for(String name:new String[]{"weather.json.bak","weather.json.new","weather.json"})removeWeatherFile(root,new File(root,name));
        for(File file:files){if(!weatherFile(file.getName())||file.getName().startsWith("weather.json"))continue;
            requireChild(root,file);
            if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("无法读取天气资源文件");for(File child:children){requireChild(file,child);if(child.isDirectory())throw new IOException("天气资源目录包含未知内容，卸载未完成");removeWeatherFile(file,child);}}
            removeWeatherFile(root,file);
        }
    }
    private static void requireChild(File parent,File child)throws IOException{if(!child.getCanonicalFile().equals(new File(parent.getCanonicalFile(),child.getName())))throw new IOException("天气资源路径无效，卸载未完成");}
    private static void removeWeatherFile(File parent,File file)throws IOException{requireChild(parent,file);if(file.exists()&&!file.delete())throw new IOException("部分天气资源未能移除，请重试卸载");}
    private static void verifyCore(File file)throws IOException{byte[] header=new byte[20];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(header);}if(header[0]!=127||header[1]!='E'||header[2]!='L'||header[3]!='F'||header[4]!=2||header[5]!=1||header[16]!=3||header[17]!=0||(header[18]&255)!=183||header[19]!=0)throw new IOException("组网组件不是可加载的ARM64库");}
    public static byte[] read(InputStream in,int limit)throws IOException{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){if(out.size()+n>limit)throw new IOException("响应大小超限");out.write(b,0,n);}return out.toByteArray();}}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
}
