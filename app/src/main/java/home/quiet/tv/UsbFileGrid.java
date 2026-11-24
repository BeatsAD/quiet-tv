package home.quiet.tv;

import android.annotation.SuppressLint;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** The TV's folder view. Preview decoding only follows attached, recycled tiles. */
@SuppressLint("ViewConstructor") // Constructed with live directory entries and the dialog callback.
final class UsbFileGrid extends RecyclerView {
    static final int COLUMNS=6;
    private static final int THUMB_WIDTH=320;
    interface Choice {
        void open(FileTransferSource.Item item);
        default void focused(FileTransferSource.Item item){}
    }
    private final ManagementUi ui;
    private final ExecutorService previews;
    private final List<FileTransferSource.Item> items;
    private final Choice choice;
    private final int headerFocusId;
    private final LruCache<String,Bitmap> thumbs=new LruCache<>(4*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getRowBytes()*value.getHeight();}
    };
    private String selected;
    private String focusOnLayout;
    private boolean busy;

    UsbFileGrid(ManagementUi ui,ExecutorService previews,List<FileTransferSource.Item> items,int headerFocusId,Choice choice){
        super(ui);
        this.ui=ui;
        this.previews=previews;
        this.items=items;
        this.choice=choice;
        this.headerFocusId=headerFocusId;
        setId(androidx.core.view.ViewCompat.generateViewId());
        setLayoutManager(new GridLayoutManager(ui,COLUMNS));
        setItemAnimator(null);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFocusable(false);
        setAdapter(new Tiles());
    }

    static String tag(FileTransferSource.Item item){return "usb-item-"+item.uri;}
    void select(String key,boolean busy){
        this.selected=key;
        this.busy=busy;
        for(int i=0;i<getChildCount();i++)((Tile)getChildAt(i)).appearance();
    }

    void focus(String key){
        if(items.isEmpty())return;
        int position=0;
        for(int i=0;i<items.size();i++)if(tag(items.get(i)).equals(key)){position=i;break;}
        View tile=getLayoutManager().findViewByPosition(position);
        if(tile!=null){tile.requestFocusFromTouch();return;}
        focusOnLayout=tag(items.get(position));
        scrollToPosition(position);
    }

    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        if(focusOnLayout!=null){
            View tile=findViewWithTag(focusOnLayout);
            focusOnLayout=null;
            if(tile!=null)tile.requestFocusFromTouch();
        }
    }

    private final class Tiles extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount(){return items.size();}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){
            return new RecyclerView.ViewHolder(new Tile()){};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            Tile tile=(Tile)holder.itemView;
            tile.setNextFocusUpId(position<COLUMNS?headerFocusId:View.NO_ID);
            tile.bind(items.get(position));
        }
        @Override public void onViewAttachedToWindow(RecyclerView.ViewHolder holder){((Tile)holder.itemView).load();}
        @Override public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder){((Tile)holder.itemView).cancel();}
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder){((Tile)holder.itemView).cancel();}
    }

    private final class Tile extends SurfaceCorners.Frame {
        final TextView name;
        final ImageView picture,icon,check;
        final FrameLayout play;
        FileTransferSource.Item item;
        Bitmap thumbnail;
        Future<?> request;

        Tile(){
            super(ui);
            RecyclerView.LayoutParams size=new RecyclerView.LayoutParams(-1,ui.px(80));
            size.setMargins(ui.px(3.5f),0,ui.px(3.5f),ui.px(7));
            setLayoutParams(size);
            setFocusable(true);
            setSoundEffectsEnabled(false);
            setOnClickListener(v->{if(!busy)KeyFeedback.clicked(v,()->choice.open(item));});
            setOnFocusChangeListener((v,on)->{
                appearance();
                if(on&&item!=null)choice.focused(item);
            });

            picture=new ImageView(ui);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            FrameLayout.LayoutParams photo=new FrameLayout.LayoutParams(-1,-1);
            photo.bottomMargin=ui.px(21);
            addView(picture,photo);

            icon=new ImageView(ui);
            FrameLayout.LayoutParams symbol=new FrameLayout.LayoutParams(ui.px(29),ui.px(29),Gravity.TOP|Gravity.START);
            symbol.leftMargin=ui.px(14);
            symbol.topMargin=ui.px(13);
            addView(icon,symbol);

            ImageView playIcon=new ImageView(ui);
            playIcon.setImageDrawable(UiCompat.drawable(ui,R.drawable.ic_transfer_video));
            playIcon.setColorFilter(UiPalette.TEXT);
            playIcon.setBackgroundDrawable(ui.surface(0x99000000,0x99000000,0,999));
            play=new FrameLayout(ui);
            play.addView(playIcon,new FrameLayout.LayoutParams(ui.px(24),ui.px(24),Gravity.CENTER));
            FrameLayout.LayoutParams playSize=new FrameLayout.LayoutParams(-1,-1);
            playSize.bottomMargin=ui.px(21);
            addView(play,playSize);

            name=ui.label("",11);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX,ui.px(11));
            name.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            addView(name,new FrameLayout.LayoutParams(-1,ui.px(21),Gravity.BOTTOM));

            check=new ImageView(ui);
            check.setImageDrawable(new QuickControlIcon("check"));
            check.setBackgroundDrawable(ui.surface(0xcc000000,0xcc000000,0,999));
            check.setPadding(ui.px(3),ui.px(3),ui.px(3),ui.px(3));
            FrameLayout.LayoutParams tick=new FrameLayout.LayoutParams(ui.px(17),ui.px(17),Gravity.TOP|Gravity.END);
            tick.setMargins(ui.px(5),ui.px(5),ui.px(5),0);
            addView(check,tick);
            if(Build.VERSION.SDK_INT>=16)name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void bind(FileTransferSource.Item value){
            cancel();
            item=value;
            setTag(tag(item));
            name.setText(item.name);
            int resource=item.directory?R.drawable.ic_transfer_folder:FileTransferPolicy.apk(item.name)
                    ?R.drawable.ic_transfer_apk:WallpaperImport.video(item.name,null)
                    ?R.drawable.ic_transfer_video:FileTransferPolicy.image(item.name)
                    ?R.drawable.ic_transfer_image:R.drawable.ic_transfer_file;
            icon.setImageDrawable(UiCompat.drawable(ui,resource));
            thumbnail=thumbs.get(tag(item));
            picture.setImageBitmap(thumbnail);
            appearance();
        }

        void appearance(){
            if(item==null)return;
            boolean focused=isFocused()&&hasWindowFocus();
            boolean chosen=tag(item).equals(selected);
            boolean media=thumbnail!=null;
            int color=focused?UiPalette.INK:UiPalette.TEXT;
            int fill=focused?UiPalette.TEXT:0x1fffffff;
            setBackgroundDrawable(ui.surface(fill,fill,focused?0:0x38ffffff,8));
            // Keep a picture visible while its filename band switches to white on focus.
            name.setBackgroundColor(media&&!focused?0xd900080b:Color.TRANSPARENT);
            name.setTextColor(color);
            name.setPadding(ui.px(8),0,ui.px(8),0);
            icon.setColorFilter(color);
            icon.setVisibility(media?GONE:VISIBLE);
            play.setVisibility(media&&WallpaperImport.video(item.name,null)?VISIBLE:GONE);
            check.setVisibility(chosen?VISIBLE:GONE);
            setSelected(chosen);
            setContentDescription(item.name+(chosen?ui.tr("，已选择",", selected"):""));
        }

        @Override public void onWindowFocusChanged(boolean focused){
            super.onWindowFocusChanged(focused);
            appearance();
        }

        void cancel(){
            if(request!=null){request.cancel(true);request=null;}
        }

        void load(){
            if(thumbnail!=null||request!=null||item.directory||!WallpaperImport.media(item.name))return;
            FileTransferSource.Item source=item;
            request=previews.submit(()->{
                Bitmap bitmap=preview(ui,source);
                if(bitmap==null)return;
                if(Thread.currentThread().isInterrupted()){bitmap.recycle();return;}
                ui.runOnUiThread(()->{
                    thumbs.put(tag(source),bitmap);
                    if(item!=source||getParent()!=UsbFileGrid.this)return;
                    thumbnail=bitmap;
                    picture.setImageBitmap(bitmap);
                    appearance();
                });
            });
        }
    }

    private static Bitmap preview(ManagementUi ui,FileTransferSource.Item item){
        try{
            if(WallpaperImport.video(item.name,null))return videoPreview(ui,item);
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds=true;
            try(InputStream in=ui.getContentResolver().openInputStream(item.uri)){BitmapFactory.decodeStream(in,null,options);}
            options.inSampleSize=1;
            while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>THUMB_WIDTH*2)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            try(InputStream in=ui.getContentResolver().openInputStream(item.uri)){return scaled(BitmapFactory.decodeStream(in,null,options));}
        }catch(IOException|RuntimeException|OutOfMemoryError unreadable){return null;}
    }

    private static Bitmap videoPreview(ManagementUi ui,FileTransferSource.Item item)throws IOException{
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{
            retriever.setDataSource(ui,item.uri);
            if(Build.VERSION.SDK_INT>=27)return retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,THUMB_WIDTH,THUMB_WIDTH);
            int width=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            return (long)width*height>3840L*2160?null:scaled(retriever.getFrameAtTime(0));
        }finally{retriever.release();}
    }

    private static Bitmap scaled(Bitmap bitmap){
        if(bitmap==null)return null;
        int edge=Math.max(bitmap.getWidth(),bitmap.getHeight());
        if(edge<=THUMB_WIDTH)return bitmap;
        float scale=THUMB_WIDTH/(float)edge;
        Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);
        if(small!=bitmap)bitmap.recycle();
        return small;
    }
}
