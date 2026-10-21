package home.quiet.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bundled, offline wallpapers. The picker previews without committing preferences. */
final class WallpaperController {
    private static final String[] FILES = {"big-sur-coastline", "big-sur-mountains", "big-sur-road"};
    private static final String[] ZH = {"大苏尔海岸", "大苏尔群山", "大苏尔山路"};
    private static final String[] EN = {"Big Sur Coastline", "Big Sur Mountains", "Big Sur Road"};
    private static final long ROTATION_MS = 5 * 60 * 1000L;
    private final Activity activity;
    private final FrameLayout root;
    private final SharedPreferences preferences;
    private final float unit;
    private final boolean chinese;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final ImageView current, incoming;
    private final View shade;
    private final Ambient ambient;
    private final Bitmap[] thumbnails = new Bitmap[FILES.length];
    private Bitmap entry;
    private volatile int request;
    private int index, preview, shown = -1;
    private boolean rotating, active, useDefault;
    private int themeColor = 0xff557eac;
    private Dialog picker;
    private ValueAnimator transition;
    private final Runnable rotate = () -> {
        index = (index + 1) % FILES.length;
        save();
        display(index, true);
        schedule();
    };

    WallpaperController(Activity activity, FrameLayout root, float unit, boolean chinese) {
        this.activity = activity; this.root = root; this.unit = unit; this.chinese = chinese;
        preferences = activity.getSharedPreferences("wallpapers", 0);
        index = preferences.getInt("index", 0);
        rotating = preferences.getBoolean("rotating", true);
        useDefault = preferences.getBoolean("default", !preferences.contains("index"));
        if (useDefault) rotating = false;
        FrameLayout background = new FrameLayout(activity);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        current = image(); incoming = image(); incoming.setAlpha(0);
        background.addView(current, new FrameLayout.LayoutParams(-1, -1));
        background.addView(incoming, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x70000000, 0x30000000, 0x99000000}));
        background.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        ambient = new Ambient(); background.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        ambient.setVisibility(View.INVISIBLE);
        display(useDefault ? -1 : index, false);
    }

    private int px(float value) { return Math.round(value * unit); }
    private String tr(String zh, String en) { return chinese ? zh : en; }
    private String name(int n) { return n < 0 ? tr("默认壁纸", "Default wallpaper") : chinese ? ZH[n] : EN[n]; }
    void setThemeColor(int color) {
        themeColor = color;
        if (ambient.getVisibility() == View.VISIBLE) ambient.setColor(color);
    }
    private ImageView image() {
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    private Bitmap decode(int n, int sample) {
        try (InputStream input = activity.getAssets().open("wallpapers/" + FILES[n] + ".jpg")) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) { throw new IllegalStateException("Missing bundled wallpaper", e); }
    }

    private synchronized Bitmap thumbnail(int n) {
        if (thumbnails[n] == null) thumbnails[n] = decode(n, 4);
        return thumbnails[n];
    }

    private Bitmap fullImage(int n) {
        android.util.DisplayMetrics screen = activity.getResources().getDisplayMetrics();
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream input = activity.getAssets().open("wallpapers/" + FILES[n] + ".jpg")) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException e) { throw new IllegalStateException("Missing bundled wallpaper", e); }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= screen.widthPixels
                && bounds.outHeight / (sample * 2) >= screen.heightPixels) sample *= 2;
        return decode(n, sample);
    }

    private void display(int n, boolean animate) {
        final int version = ++request;
        if (transition != null) { transition.end(); transition = null; }
        if (n < 0) {
            current.setImageDrawable(null); incoming.setImageDrawable(null);
            shade.setVisibility(View.INVISIBLE); ambient.setVisibility(View.VISIBLE);
            ambient.setColor(themeColor); shown = -1;
            Log.i("QuietTV", "Wallpaper display=default");
            return;
        }
        if (shown == n) return;
        decoder.execute(() -> {
            if (version != request) return;
            Bitmap bitmap = fullImage(n);
            main.post(() -> {
                if (version != request || activity.isDestroyed()) return;
                ambient.setVisibility(View.INVISIBLE); shade.setVisibility(View.VISIBLE);
                if (!animate || shown < 0) {
                    current.setImageBitmap(bitmap); shown = n;
                } else {
                    incoming.setImageBitmap(bitmap); incoming.setAlpha(0);
                    transition = ValueAnimator.ofFloat(0, 1); transition.setDuration(900);
                    transition.addUpdateListener(a -> {
                        float fraction = (float) a.getAnimatedValue(); incoming.setAlpha(fraction);
                        if (fraction == 1f) {
                            current.setImageBitmap(bitmap); incoming.setImageDrawable(null);
                            incoming.setAlpha(0); shown = n;
                        }
                    });
                    transition.start();
                }
                Log.i("QuietTV", "Wallpaper display=" + FILES[n] + " rotating=" + rotating);
            });
        });
    }

    private void save() { preferences.edit().putInt("index", index).putBoolean("rotating", rotating).putBoolean("default", useDefault).apply(); }
    private void schedule() {
        main.removeCallbacks(rotate);
        if (active && rotating && !useDefault && picker == null) main.postDelayed(rotate, ROTATION_MS);
    }
    void resume() { active = true; schedule(); }
    void pause() {
        active = false; main.removeCallbacks(rotate);
        if (picker != null) picker.dismiss();
        if (transition != null) transition.end();
    }
    void destroy() {
        ++request; decoder.shutdownNow(); main.removeCallbacksAndMessages(null);
        if (transition != null) { transition.cancel(); transition.removeAllUpdateListeners(); transition = null; }
        current.setImageDrawable(null); incoming.setImageDrawable(null);
    }

    synchronized Bitmap entryBanner() {
        if (entry != null) return entry;
        Bitmap bitmap = Bitmap.createBitmap(360, 203, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(3);
        Bitmap thumbnail = thumbnail(0);
        canvas.drawBitmap(thumbnail, null, new Rect(0, 0, 360, 203), paint);
        paint.setShader(new LinearGradient(0, 0, 0, 203, 0x18000000, 0xc0000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 360, 203, paint); paint.setShader(null);
        paint.setColor(Color.WHITE); paint.setTextSize(chinese ? 34 : 27); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        canvas.drawText(tr("壁纸设置", "Wallpaper Settings"), 180, 120, paint);
        entry = bitmap;
        return entry;
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(activity); text.setText(value); text.setTextColor(Color.WHITE);
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size)); text.setIncludeFontPadding(false);
        return text;
    }
    private GradientDrawable border(boolean focus) {
        GradientDrawable background = new GradientDrawable(); background.setColor(0xb5202228);
        background.setCornerRadius(px(12)); background.setStroke(px(focus ? 3 : 1), focus ? Color.WHITE : 0x60ffffff);
        return background;
    }
    private void place(FrameLayout parent, View view, int width, int height, int left, int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(width), px(height));
        params.leftMargin = px(left); params.topMargin = px(top); parent.addView(view, params);
    }

    void showPicker() {
        if (picker != null) return;
        main.removeCallbacks(rotate); preview = useDefault ? -1 : index;
        for (int n = 1; n < root.getChildCount(); n++) root.getChildAt(n).setVisibility(View.INVISIBLE);
        picker = new Dialog(activity);
        FrameLayout content = new FrameLayout(activity);
        TextView title = label(tr("壁纸设置", "Wallpaper Settings"), 26);
        place(content, title, 300, 38, 48, 28);
        TextView mode = label(useDefault ? tr("当前：默认壁纸 · 纯黑背景＋应用主题色光晕", "Current: default wallpaper · App color glow") : rotating ? tr("当前：自动轮播【每 5 分钟】", "Current: slideshow · Every 5 minutes") : tr("当前：固定壁纸", "Current: fixed wallpaper"), 13);
        place(content, mode, 600, 25, 48, 73);
        TextView auto = label(rotating ? tr("壁纸轮播：开", "Slideshow: On") : tr("壁纸轮播：关", "Slideshow: Off"), 14);
        auto.setGravity(Gravity.CENTER); auto.setFocusable(true); auto.setBackground(border(false));
        place(content, auto, 144, 40, 768, 42);
        TextView status = label("", 14);
        status.setGravity(Gravity.CENTER); place(content, status, 864, 28, 48, 496);
        FrameLayout[] cards = new FrameLayout[FILES.length + 1];
        for (int n = 0; n < cards.length; n++) {
            final int slot = n, choice = n - 1;
            FrameLayout card = new FrameLayout(activity); cards[n] = card;
            card.setFocusable(true); card.setPadding(px(5), px(5), px(5), px(5));
            card.setBackground(border(false)); card.setContentDescription(name(choice));
            place(content, card, 204, 162, 48 + n * 220, 318);
            ImageView thumb = image();
            card.addView(thumb, new FrameLayout.LayoutParams(-1, px(112)));
            if (choice < 0) thumb.setImageBitmap(defaultThumbnail());
            else decoder.execute(() -> {
                Bitmap bitmap = thumbnail(choice);
                main.post(() -> { if (picker != null && picker.isShowing()) thumb.setImageBitmap(bitmap); });
            });
            boolean checked = choice < 0 ? useDefault : !useDefault && !rotating && index == choice;
            TextView caption = label(name(choice) + (checked ? "  ✓" : ""), 15);
            caption.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(-1, px(32), Gravity.BOTTOM);
            card.addView(caption, captionParams);
            card.setOnFocusChangeListener((v, focus) -> {
                card.setBackground(border(focus));
                if (focus) { preview = choice; display(choice, true); status.setText(name(choice)); }
            });
            card.setOnClickListener(v -> commit(choice < 0 ? index : choice, false, choice < 0));
            card.setOnKeyListener((v, key, event) -> {
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) cards[Math.max(0, Math.min(cards.length - 1, slot + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)))].requestFocus();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (key == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) auto.requestFocus();
                    return true;
                }
                return false;
            });
        }
        auto.setOnFocusChangeListener((v, focus) -> {
            auto.setBackground(border(focus));
            if (focus) status.setText(rotating ? tr("按确定关闭轮播，固定当前风景壁纸", "Press OK to turn off slideshow and keep this wallpaper") : tr("按确定开启三张风景壁纸轮播", "Press OK to rotate all three landscape wallpapers"));
        });
        auto.setOnClickListener(v -> commit(preview < 0 ? index : preview, !rotating, false));
        auto.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) cards[preview + 1].requestFocus();
                return true;
            }
            return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        picker.setContentView(content);
        picker.setOnDismissListener(dialog -> {
            picker = null;
            for (int n = 1; n < root.getChildCount(); n++) root.getChildAt(n).setVisibility(View.VISIBLE);
            display(useDefault ? -1 : index, true); schedule();
        });
        Window window = picker.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(.12f);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        picker.show(); window.setLayout(-1, -1); cards[useDefault ? 0 : index + 1].requestFocus();
    }

    private void commit(int choice, boolean slideshow, boolean defaultWallpaper) {
        index = choice; rotating = slideshow; useDefault = defaultWallpaper; save(); picker.dismiss();
        Toast.makeText(activity, defaultWallpaper ? tr("已恢复默认壁纸", "Default wallpaper restored") : slideshow ? tr("已开启壁纸轮播", "Wallpaper slideshow enabled") : tr("已关闭轮播，使用固定壁纸", "Slideshow off · Fixed wallpaper applied"), Toast.LENGTH_SHORT).show();
    }
    private Bitmap defaultThumbnail() {
        Bitmap bitmap = Bitmap.createBitmap(360, 203, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.BLACK);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setShader(new RadialGradient(116, 59, 232,
                new int[]{0xac557eac, 0x40557eac, Color.TRANSPARENT},
                new float[]{0, .5f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 360, 203, paint);
        return bitmap;
    }

    private final class Ambient extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
        Shader bloom,spill;int color=Color.TRANSPARENT,targetColor=Color.TRANSPARENT;ValueAnimator animation;
        final float[] stops={0,.12f,.26f,.42f,.58f,.74f,.88f,1};
        final float[] falloff={1,.96f,.80f,.54f,.28f,.10f,.015f,0};
        Ambient(){super(activity);}
        void setColor(int value){
            float[] hsv=new float[3];Color.colorToHSV(value,hsv);
            hsv[1]=Math.min(.88f,hsv[1]);hsv[2]=.95f;value=Color.HSVToColor(hsv);
            if(value==targetColor)return;targetColor=value;
            if(animation!=null)animation.cancel();
            if(color==Color.TRANSPARENT){color=value;updateGlow();return;}
            animation=ValueAnimator.ofObject(new android.animation.ArgbEvaluator(),color,value);animation.setDuration(650);
            animation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animation.addUpdateListener(a->{color=(int)a.getAnimatedValue();updateGlow();});animation.start();
        }
        private Shader glow(float x,float y,float radius,float flatten,int alpha){
            int[] colors=new int[stops.length];
            for(int n=0;n<colors.length;n++)colors[n]=(color&0xffffff)|(Math.round(alpha*falloff[n])<<24);
            RadialGradient gradient=new RadialGradient(px(x),px(y),px(radius),colors,stops,Shader.TileMode.CLAMP);
            Matrix ellipse=new Matrix();ellipse.setScale(1,flatten,px(x),px(y));gradient.setLocalMatrix(ellipse);
            return gradient;
        }
        private void updateGlow(){
            bloom=glow(310,158,620,.68f,172);
            spill=glow(100,310,480,.72f,48);
            invalidate();
        }
        @Override protected void onDraw(Canvas c){
            if(bloom==null)return;
            paint.setShader(bloom);c.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(spill);c.drawRect(0,0,getWidth(),getHeight(),paint);
        }
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    }

}
