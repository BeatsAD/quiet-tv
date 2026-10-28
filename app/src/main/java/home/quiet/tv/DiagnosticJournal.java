package home.quiet.tv;

import java.io.*;
import home.quiet.tv.Charsets;
import java.util.List;
import java.util.regex.Pattern;

/** Two fixed files, at most 2 MiB total; rotation never enumerates or deletes files. */
final class DiagnosticJournal {
    static final int SEGMENT_BYTES=1024*1024,MAX_ENTRY_BYTES=4096;
    private static final String PREFIX="# QuietTV segment=";
    private static final Pattern CONTROL=Pattern.compile("[\\p{Cntrl}]"),URL=Pattern.compile("(?i)(?:https?://|www\\.)\\S+"),
        IP=Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"),EMAIL=Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"),
        SECRET=Pattern.compile("(?i)((?:password|passwd|token|secret|pairing[_-]?code|连接码|配对码)\\s*[=:]\\s*)\\S+");
    private final File[] files;
    private int active;
    private long sequence;
    DiagnosticJournal(File directory) throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Diagnostic storage unavailable");
        files=new File[]{new File(directory,"events-0.log"),new File(directory,"events-1.log")};
        long first=sequence(files[0]),second=sequence(files[1]);
        active=second>first?1:0;sequence=Math.max(0,Math.max(first,second));
        if(Math.max(first,second)<0)rotate(active);
    }
    private long sequence(File file) throws IOException {
        if(!file.exists())return -1;
        if(file.length()>SEGMENT_BYTES)throw new IOException("Diagnostic segment exceeds limit");
        try(InputStream input=new FileInputStream(file)){
            ByteArrayOutputStream header=new ByteArrayOutputStream();int value;
            while(header.size()<64&&(value=input.read())>=0&&value!='\n')header.write(value);
            String text=header.toString("UTF-8");
            if(text.startsWith(PREFIX))try{return Long.parseLong(text.substring(PREFIX.length()));}catch(NumberFormatException ignored){}
            return -1;
        }
    }
    private void rotate(int slot) throws IOException {
        try(OutputStream output=new FileOutputStream(files[slot])){output.write((PREFIX+(++sequence)+"\n").getBytes(Charsets.UTF_8));}
        active=slot;
    }
    void append(byte[] entry) throws IOException {
        if(entry.length==0||entry.length>MAX_ENTRY_BYTES)throw new IOException("Diagnostic entry exceeds limit");
        if(files[active].length()+entry.length>SEGMENT_BYTES)rotate(1-active);
        try(OutputStream output=new FileOutputStream(files[active],true)){output.write(entry);}
    }
    /** One open/write/close per segment for the records already waiting in the bounded queue. */
    void appendBatch(List<byte[]> entries) throws IOException {
        for(byte[] entry:entries)if(entry.length==0 || entry.length>MAX_ENTRY_BYTES)throw new IOException("Diagnostic entry exceeds limit");
        OutputStream output=null;long length=files[active].length();
        try{
            for(byte[] entry:entries){
                if(length+entry.length>SEGMENT_BYTES){if(output!=null){output.close();output=null;}rotate(1-active);length=files[active].length();}
                if(output==null)output=new BufferedOutputStream(new FileOutputStream(files[active],true));
                output.write(entry);length+=entry.length;
            }
        }finally{if(output!=null)output.close();}
    }
    void copyTo(OutputStream output) throws IOException {copy(files[1-active],output);copy(files[active],output);}
    private void copy(File file,OutputStream output) throws IOException {
        if(!file.exists())return;
        try(InputStream input=new FileInputStream(file)){
            byte[] buffer=new byte[8192];int count,total=0;
            while((count=input.read(buffer))!=-1){total+=count;if(total>SEGMENT_BYTES)throw new IOException("Diagnostic segment exceeds limit");output.write(buffer,0,count);}
        }
        output.write('\n');
    }
    static String clean(String text){
        if(text==null)return "";
        String value=CONTROL.matcher(text.substring(0,Math.min(text.length(),1000))).replaceAll(" ");
        value=URL.matcher(value).replaceAll("[url]");value=IP.matcher(value).replaceAll("[ip]");
        value=EMAIL.matcher(value).replaceAll("[email]");return SECRET.matcher(value).replaceAll("$1[redacted]");
    }
    static byte[] entry(String text){return (clean(text)+"\n").getBytes(Charsets.UTF_8);}
}
