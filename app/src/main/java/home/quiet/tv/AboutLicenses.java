package home.quiet.tv;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Offline text generated from the same notices as the HTML asset. */
final class AboutLicenses {
    private AboutLicenses(){}
    static String read(Context context)throws IOException{
        try(InputStream input=context.getAssets().open("about/license.txt");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];
            for(int size;(size=input.read(buffer))!=-1;)out.write(buffer,0,size);
            return out.toString("UTF-8").trim();
        }
    }
}
