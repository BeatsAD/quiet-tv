package home.quiet.tv;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Non-interactive settings preview, in the prototype's 1280-wide coordinate system. */
final class QuickControlPreviewView extends LinearLayout {
    private final ManagementUi ui;private final ScrollView scroll;private final QuickControlCards modules;private int scrollLimit;
    QuickControlPreviewView(ManagementUi ui){super(ui);this.ui=ui;setOrientation(VERTICAL);setPadding(p(16),p(16),p(16),p(16));setBackgroundDrawable(ui.surface(0x2e000000,0x4d000000,0x4dffffff,15));setClipChildren(false);setClipToPadding(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        TextView title=text("快捷控制",22.4f,31.515f,UiPalette.TEXT);weight(title,650);title.setGravity(Gravity.TOP);title.setPadding(p(4),0,p(4),0);addView(title,new LayoutParams(-1,p(34)));
        scroll=new ScrollView(ui){@Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.min(scrollLimit,MeasureSpec.getSize(h)==0?scrollLimit:MeasureSpec.getSize(h)),MeasureSpec.AT_MOST));}@Override protected void dispatchDraw(Canvas c){int save=c.save();c.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(c);c.restoreToCount(save);}};scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(OVER_SCROLL_NEVER);scroll.setOnTouchListener((v,e)->true);scroll.setPadding(p(8),p(6),p(8),p(6));scroll.setClipChildren(false);scroll.setClipToPadding(false);modules=new QuickControlCards(ui,ui.unit);scroll.addView(modules,new ScrollView.LayoutParams(-1,-2));LayoutParams sp=new LayoutParams(-1,-2);sp.leftMargin=sp.rightMargin=-p(8);addView(scroll,sp);
        LinearLayout phone=footer("手机设置","device-mobile","caret-down",true);LayoutParams phoneSize=new LayoutParams(-1,p(48.636f));phoneSize.topMargin=p(11.52f);addView(phone,phoneSize);
    }
    @Override protected void onMeasure(int width,int height){int available=MeasureSpec.getSize(height);if(available==0)available=getResources().getDisplayMetrics().heightPixels;int next=Math.max(p(74.667f),Math.min(p(520),available-getPaddingTop()-getPaddingBottom()-p(34+48.636f+11.52f)));if(next!=scrollLimit){scrollLimit=next;scroll.forceLayout();}super.onMeasure(width,height);}
    private int p(float logical){return ui.px(logical*.75f);}
    static void type(TextView text,float size,float height,float unit,int color){text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*.75f*unit);text.setTextColor(color);text.setIncludeFontPadding(false);text.setTypeface(android.graphics.Typeface.create("sans-serif",Typeface.NORMAL));Paint.FontMetrics fm=text.getPaint().getFontMetrics();float extra=Math.max(0,height*.75f*unit-(fm.descent-fm.ascent));text.setLineSpacing(extra,1);text.setPadding(0,Math.round(extra/2),0,Math.round(extra/2));text.setMinHeight(Math.round(height*.75f*unit));}
    static void controlType(TextView text,float size,float height,float unit,int color){
        type(text,size,height,unit,color);text.setIncludeFontPadding(true);text.setLineSpacing(0,1);Paint.FontMetrics fm=text.getPaint().getFontMetrics();int actual=(int)Math.ceil(fm.bottom-fm.top);text.setMinHeight(Math.max(actual,Math.round(height*.75f*unit)));text.setPadding(0,0,0,0);
    }
    static void weight(TextView text,int weight){text.setTypeface(android.os.Build.VERSION.SDK_INT>=28?Api28.weight(weight):android.graphics.Typeface.create(weight>=600?"sans-serif":"sans-serif-medium",weight>=600?android.graphics.Typeface.BOLD:Typeface.NORMAL));}
    @android.annotation.TargetApi(28) private static final class Api28 {static android.graphics.Typeface weight(int value){return android.graphics.Typeface.create(android.graphics.Typeface.create("sans-serif",Typeface.NORMAL),value,false);}}
    private TextView text(String value,float size,float height,int color){TextView t=new TextView(ui);t.setText(value);controlType(t,size,height,ui.unit,color);return t;}
    private ImageView icon(String name,float size){ImageView v=new ImageView(ui);v.setImageDrawable(new QuickControlIcon(name));v.setLayoutParams(new LayoutParams(p(size),p(size)));return v;}
    private LinearLayout footer(String title,String glyph,String end,boolean bold){LinearLayout row=ui.row();row.setPadding(p(8.96f),0,p(8.96f),0);row.addView(icon(glyph,23.68f));TextView label=text(title,17.92f,25.984f,UiPalette.TEXT);if(bold)weight(label,600);LayoutParams lp=new LayoutParams(0,-2,1);lp.leftMargin=p(7.68f);row.addView(label,lp);row.addView(icon(end,12.8f));return row;}
    private List<String> renderedIds;
    private boolean renderedSounds,renderedWallpaperSound,renderedScreensaverSound,renderedChinese,renderedMagnify;private Map<String,Integer> renderedValues;
    void select(String selected){View target=modules.findViewWithTag("quick-preview-"+selected);modules.preview(target);if(target!=null)scroll.post(()->{if(target.getWindowToken()==null)return;Rect rect=new Rect();target.getDrawingRect(rect);modules.offsetDescendantRectToMyCoords(target,rect);int top=scroll.getScrollY()+scroll.getPaddingTop(),bottom=scroll.getScrollY()+scroll.getHeight()-scroll.getPaddingBottom();if(rect.top<top)scroll.scrollTo(0,Math.max(0,rect.top-scroll.getPaddingTop()));else if(rect.bottom>bottom)scroll.scrollTo(0,rect.bottom-scroll.getHeight()+scroll.getPaddingBottom());});}
    void render(List<String> ids,String selected,boolean sounds,boolean wallpaperSound,boolean screensaverSound,DesktopSettingsStore desktop,boolean chinese){
        android.media.AudioManager audio=(android.media.AudioManager)ui.getSystemService(android.content.Context.AUDIO_SERVICE);int volume=audio==null?0:Math.round(100f*audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)/Math.max(1,audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)));
        Map<String,Integer> values=new HashMap<>();for(String id:QuickControlStore.RANGES)values.put(id,"volume".equals(id)?volume:QuickControlStore.rangeValue(id,desktop));
        if(ids.equals(renderedIds)&&sounds==renderedSounds&&wallpaperSound==renderedWallpaperSound&&screensaverSound==renderedScreensaverSound&&values.equals(renderedValues)&&desktop.dockMagnify()==renderedMagnify&&chinese==renderedChinese){select(selected);return;}
        renderedIds=new ArrayList<>(ids);renderedSounds=sounds;renderedWallpaperSound=wallpaperSound;renderedScreensaverSound=screensaverSound;renderedValues=values;renderedMagnify=desktop.dockMagnify();renderedChinese=chinese;
        modules.clearCards();
        List<String> grid=QuickControlStore.grid(ids);
        for(int r=0;r<(grid.size()+2)/3;r++){LinearLayout row=ui.row();row.setClipChildren(false);LayoutParams rp=new LayoutParams(-1,p(64));if(r>0)rp.topMargin=p(8);modules.addView(row,rp);
            for(int col=0;col<3;col++){int index=r*3+col;String id=index<grid.size()?grid.get(index):"";LayoutParams cp=new LayoutParams(0,-1,1);if(col>0)cp.leftMargin=p(8);if(id.isEmpty()){row.addView(new View(ui),cp);continue;}boolean on="keySounds".equals(id)&&sounds||"mute".equals(id)&&wallpaperSound
                        ||QuickControlStore.SCREENSAVER_SOUND.equals(id)&&screensaverSound;
                LinearLayout card=ui.column();card.setClipChildren(false);card.setClipToPadding(false);card.setGravity(Gravity.CENTER);card.setTag("quick-preview-"+id);card.setContentDescription("预览"+QuickControlStore.title(id,chinese));
                ImageView image=icon(glyph(id),24);card.addView(image);TextView label=text(QuickControlStore.shortTitle(id,chinese),16,22,UiPalette.TEXT);label.setGravity(Gravity.CENTER);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=p(6);card.addView(label,lp);row.addView(card,cp);
                QuickControlIcon drawable = (QuickControlIcon) image.getDrawable();
                modules.register(card, card, (color, focused, active) -> {
                    drawable.color(color);
                    drawable.setFocused(focused);
                    drawable.setSoundOn(active);
                    label.setTextColor(color);
                });
                modules.setOn(card,on);
            }
        }
        for(String id:QuickControlStore.ranges(ids)){
            int value=values.get(id),minimum=QuickControlStore.rangeMinimum(id),maximum=QuickControlStore.rangeMaximum(id);boolean available=!"dockMagnification".equals(id)||desktop.dockMagnify();LinearLayout card=ui.column();card.setTag("quick-preview-"+id);card.setClipChildren(false);card.setClipToPadding(false);card.setPadding(p(12),p(7.333f),p(12),p(7.333f));LayoutParams cp=new LayoutParams(-1,p(74.667f));cp.topMargin=p(8);modules.addView(card,cp);
            LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(QuickControlStore.title(id,chinese),16,22,UiPalette.TEXT);TextView level=text(available?value+"%":chinese?"未开启聚焦放大":"Disabled",16,22,UiPalette.TEXT);line.addView(title,new LayoutParams(0,-1,1));line.addView(level);card.addView(line,new LayoutParams(-1,p(20)));
            LinearLayout controls=ui.row();controls.setGravity(Gravity.CENTER_VERTICAL);ImageView low=icon(rangeGlyph(id,false),24),high=icon(rangeGlyph(id,true),24);controls.addView(low);
            PreviewTrack track=new PreviewTrack(Math.max(0,Math.min(1,(value-minimum)/(float)(maximum-minimum))));LayoutParams tp=new LayoutParams(0,p(24),1);tp.leftMargin=tp.rightMargin=p(10);controls.addView(track,tp);controls.addView(high);LayoutParams lp=new LayoutParams(-1,p(24));lp.topMargin=p(16);card.addView(controls,lp);
            modules.register(card,card,(color,focused,active)->{title.setTextColor(color);level.setTextColor(color);((QuickControlIcon)low.getDrawable()).color(color);((QuickControlIcon)high.getDrawable()).color(color);track.ink=color;track.invalidate();});
        }
        select(selected);
    }
    private final class PreviewTrack extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float progress;
        int ink=UiPalette.TEXT;
        PreviewTrack(float progress){super(ui);this.progress=progress;paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(p(3));}
        @Override protected void onDraw(Canvas canvas){
            float y=getHeight()/2f,left=p(7),right=getWidth()-p(7),x=left+(right-left)*progress;
            paint.setColor(QuickControlCards.trackTint(ink));
            canvas.drawLine(left,y,right,y,paint);
            paint.setColor(ink);
            canvas.drawLine(left,y,x,y,paint);
            canvas.drawCircle(x,y,p(7),paint);
        }
    }
    static String rangeGlyph(String id,boolean high){return "brightness".equals(id)?high?"range-sun-high":"range-sun-low":"volume".equals(id)?high?"range-speaker-high":"range-speaker-low":glyph(id);}
    static String glyph(String id){switch(id){case "keySounds":return "key-sound";case "mute":return "wallpaper-sound";case QuickControlStore.SCREENSAVER_SOUND:return "saver-sound";case "volume":return "speaker-high";case "brightness":return "sun";case "homeBlur":return "sliders";case "dockScale":return "desktop-layout";case "dockMagnification":return "plus";case "network":return "header-wifi";case "bluetooth":return "bluetooth";case "source":return "plugs";case "boot":return "boot-direct";case "apps":return "apps";case "original":return "left-desktop";case "screensaver":return "monitor-play";case "transfer":return "folder-transfer";case "memoShortcut":return "note";case "child":return "child";case "clock":return "clock";case "weather":return "weather";case QuickControlStore.SETTINGS:return QuickControlIcon.DESKTOP_SETTINGS;case "systemSettings":return "header-settings";default:return "image";}}
}
