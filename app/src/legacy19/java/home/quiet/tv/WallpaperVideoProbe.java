package home.quiet.tv;

import android.os.Build;
import java.io.*;

/** Old Android retains its platform parser without a Media3 dependency. */
final class WallpaperVideoProbe {
    static WallpaperVideo.Info inspect(File file,String context)throws IOException{
        return Build.VERSION.SDK_INT>=16?WallpaperVideo.Api16.inspect(file,context):LegacyVideoProbe.inspect(file,context);
    }
    private WallpaperVideoProbe(){}
}
