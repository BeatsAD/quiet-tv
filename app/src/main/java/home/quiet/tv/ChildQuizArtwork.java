package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.PathParser;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;

/** Bundled path-only SVGs, drawn as vectors on API14+ without a bitmap conversion. */
final class ChildQuizArtwork extends Drawable {
    private static final String[] FILES={"zh","math","en","science","culture","logic","custom"};
    private static final Map<String,List<Part>> CACHE=new HashMap<>();
    private static final class Part {final Path path;final int fill,stroke;final float width;Part(Path path,int fill,int stroke,float width){this.path=path;this.fill=fill;this.stroke=stroke;this.width=width;}}
    private final List<Part> parts;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha=255;
    static String file(String subject){int index=Arrays.asList(ChildQuizBank.SUBJECTS).indexOf(subject);return FILES[index<0?6:index];}
    static boolean validFile(String name){return Arrays.asList(FILES).contains(name);}
    ChildQuizArtwork(Context context,String subject)throws IOException{
        String name=file(subject);synchronized(CACHE){List<Part> cached=CACHE.get(name);if(cached==null){cached=read(context,name);CACHE.put(name,cached);}parts=cached;}
    }
    @android.annotation.SuppressLint("RestrictedApi") // Pinned AndroidX parser supports the fixed bundled SVG paths on API14+.
    private static List<Part> read(Context context,String name)throws IOException{
        List<Part> result=new ArrayList<>();ArrayDeque<Matrix> matrices=new ArrayDeque<>();matrices.push(new Matrix());
        try(InputStream stream=context.getAssets().open("child-quiz/artwork/"+name+".svg")){
            XmlPullParser parser=android.util.Xml.newPullParser();parser.setInput(stream,"UTF-8");int event;
            while((event=parser.next())!=XmlPullParser.END_DOCUMENT){
                if(event==XmlPullParser.END_TAG&&"g".equals(parser.getName())){matrices.pop();continue;}
                if(event!=XmlPullParser.START_TAG)continue;String tag=parser.getName();
                if("g".equals(tag)){Matrix next=new Matrix(matrices.peek());String transform=parser.getAttributeValue(null,"transform");if(transform!=null){String[] values=transform.substring(7,transform.length()-1).split(" ");next.preRotate(Float.parseFloat(values[0]),Float.parseFloat(values[1]),Float.parseFloat(values[2]));}matrices.push(next);}
                else if("path".equals(tag)){Path path=PathParser.createPathFromPathData(parser.getAttributeValue(null,"d"));if(path==null)throw new IOException("插画路径无效");path.transform(matrices.peek());String width=parser.getAttributeValue(null,"stroke-width");result.add(new Part(path,color(parser.getAttributeValue(null,"fill")),color(parser.getAttributeValue(null,"stroke")),width==null?0:Float.parseFloat(width)));}
            }
            return Collections.unmodifiableList(result);
        }catch(org.xmlpull.v1.XmlPullParserException|RuntimeException e){throw new IOException("矢量插画无法读取",e);}
    }
    private static int color(String value){return value==null||"none".equals(value)?Color.TRANSPARENT:Color.parseColor(value);}
    @Override public void draw(Canvas canvas){Rect bounds=getBounds();float scale=Math.min(bounds.width()/440f,bounds.height()/340f);canvas.save();canvas.translate(bounds.left+(bounds.width()-440*scale)/2,bounds.top+(bounds.height()-340*scale)/2);canvas.scale(scale,scale);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);for(Part part:parts){if(Color.alpha(part.fill)>0){paint.setStyle(Paint.Style.FILL);paint.setColor(part.fill);paint.setAlpha(Color.alpha(part.fill)*alpha/255);canvas.drawPath(part.path,paint);}if(part.width>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(part.width);paint.setColor(part.stroke);paint.setAlpha(Color.alpha(part.stroke)*alpha/255);canvas.drawPath(part.path,paint);}}canvas.restore();}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return 440;}
    @Override public int getIntrinsicHeight(){return 340;}
}
