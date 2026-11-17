package home.quiet.tv;

import android.graphics.*;
import android.view.*;
import androidx.core.graphics.PathParser;

/** One TV-level frosted label, outside the scrolling list and focused control. */
final class QuickActionTooltip extends View implements GlassStyle.Listener {
    private final GlassStyle glassStyle;private final ManagementUi ui;private final Paint paint=new Paint(3),text=new Paint(3);@android.annotation.SuppressLint("RestrictedApi") private final Path outline=PathParser.createPathFromPathData("M16 1H84Q99 1 99 16V36Q99 51 84 51H61Q58 51 55 54L50 59L45 54Q42 51 39 51H16Q1 51 1 36V16Q1 1 16 1Z");
    private Bitmap backdrop;private View target;private String caption="";
    QuickActionTooltip(ManagementUi ui){super(ui);this.ui=ui;glassStyle=GlassStyle.get(ui);glassStyle.observe(this);setVisibility(GONE);setFocusable(false);text.setColor(Color.WHITE);text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));text.setTextSize(11.04f*ui.unit);setLayerType(glassStyle.rendering()?View.LAYER_TYPE_SOFTWARE:View.LAYER_TYPE_NONE,null);ui.stage.addView(this,new android.widget.FrameLayout.LayoutParams(1,1));}
    void show(View anchor,String label){target=anchor;caption=label;setContentDescription(label);if(anchor==null||!anchor.isShown()||!anchor.isEnabled()||!anchor.hasWindowFocus()){hide();return;}Rect visible=new Rect();if(!anchor.getGlobalVisibleRect(visible)||visible.height()<anchor.getHeight()-2){hide();return;}
        int width=Math.round(Math.max(42.24f*ui.unit,text.measureText(label)+17.28f*ui.unit)),height=ui.px(30.888f);int[] place=new int[2],stage=new int[2];anchor.getLocationOnScreen(place);ui.stage.getLocationOnScreen(stage);android.widget.FrameLayout.LayoutParams p=(android.widget.FrameLayout.LayoutParams)getLayoutParams();p.width=width;p.height=height;p.leftMargin=Math.max(0,Math.min(ui.stage.getWidth()-width,place[0]-stage[0]+anchor.getWidth()/2-width/2));p.topMargin=Math.max(0,place[1]-stage[1]-ui.px(6)-height);setLayoutParams(p);
        if(backdrop==null&&glassStyle.rendering()){setVisibility(INVISIBLE);int w=320,h=Math.max(1,Math.round(w*ui.stage.getHeight()/(float)ui.stage.getWidth()));backdrop=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas snapshot=new Canvas(backdrop);snapshot.scale(w/(float)ui.stage.getWidth(),h/(float)ui.stage.getHeight());ui.stage.draw(snapshot);SettingsSheetBackdrop.gaussian(backdrop,4);}
        setVisibility(VISIBLE);bringToFront();invalidate();
    }
    @Override public void glassChanged(){if(backdrop!=null){backdrop.recycle();backdrop=null;}setLayerType(glassStyle.rendering()?View.LAYER_TYPE_SOFTWARE:View.LAYER_TYPE_NONE,null);if(target!=null)show(target,caption);invalidate();}
    void reposition(){if(target!=null)show(target,caption);}
    void hide(){target=null;setVisibility(GONE);}
    void reset(){hide();if(backdrop!=null){backdrop.recycle();backdrop=null;}}
    void destroy(){glassStyle.forget(this);reset();ui.stage.removeView(this);}
    @Override protected void onDraw(Canvas c){Path path=new Path(outline);Matrix scale=new Matrix();scale.setScale(getWidth()/100f,getHeight()/60f);path.transform(scale);paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0x66000000);if(glassStyle.rendering())paint.setShadowLayer(ui.px(6.72f),0,ui.px(2.88f),0x66000000);c.drawPath(path,paint);paint.clearShadowLayer();
        if(backdrop!=null){BitmapShader shader=new BitmapShader(backdrop,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);Matrix mapping=new Matrix();mapping.setScale(ui.stage.getWidth()/(float)backdrop.getWidth(),ui.stage.getHeight()/(float)backdrop.getHeight());mapping.postTranslate(-getLeft(),-getTop());shader.setLocalMatrix(mapping);paint.setShader(shader);c.drawPath(path,paint);}
        paint.setShader(!glassStyle.rendering()?null:new LinearGradient(0,0,0,getHeight(),0x8c000000,0xad000000,Shader.TileMode.CLAMP));paint.setColor(UiPalette.INK_SECONDARY);if(glassStyle.rendering())paint.setAlpha(255);c.drawPath(path,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.9f*ui.unit);paint.setColor(0x52ffffff);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);text.setShadowLayer(ui.px(1.5f),0,ui.px(.75f),0x33000000);c.drawText(caption,(getWidth()-text.measureText(caption))/2,ui.px(4.8f)-text.ascent(),text);
    }
}
