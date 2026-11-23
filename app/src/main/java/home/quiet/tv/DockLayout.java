package home.quiet.tv;

/** Shared Home/preview geometry in the existing 960 by 540 coordinate system. */
final class DockLayout {
    static final int FEATURED_TITLE_HEIGHT=56;
    private static final float ART_TITLE_GAP=14, PANEL_TITLE_GAP=5;
    private static final float MAX_WIDTH=864, PADDING=16, BORDER=.75f, GAP=14, MIN_GAP=8, BOTTOM=32.4f, TOP=280;
    final float left,top,width,height,padding,gap,labelSize,labelGap,radius;
    final float[] x,y,cardWidth,cardHeight;
    private DockLayout(int count,float left,float top,float width,float height,float padding,float gap,float labelSize,float labelGap,float radius){
        this.left=left;this.top=top;this.width=width;this.height=height;this.padding=padding;this.gap=gap;this.labelSize=labelSize;this.labelGap=labelGap;this.radius=radius;x=new float[count];y=new float[count];cardWidth=new float[count];cardHeight=new float[count];
    }
    /** Anchor to the visible row's highest artwork edge, not any individual card. */
    float featuredTitleTop(boolean panel){
        float artTop=top;for(int i=0;i<y.length;i++)artTop=i==0?y[i]:Math.min(artTop,y[i]);
        float bottom=artTop-ART_TITLE_GAP;
        if(panel)bottom=Math.min(bottom,top-PANEL_TITLE_GAP);
        return bottom-FEATURED_TITLE_HEIGHT;
    }
    /** The add action already contains its label; its default image matches the 1.3.6 card. */
    static DockLayout empty(int focus,String size,int scalePercent,boolean magnify,int magnification){
        float sizeScale="small".equals(size)?5.6f/8.2f:"large".equals(size)?10.4f/8.2f:1;
        float width=151.2f*sizeScale*scalePercent/100f*(focus>=0&&magnify?magnification/100f:1);
        float height=width*203f/360f,left=(960-width)/2,top=404+151.2f*203f/360f-height;
        DockLayout layout=new DockLayout(1,left,top,width,height,0,0,0,0,height*.22f);
        layout.x[0]=left;layout.y[0]=top;layout.cardWidth[0]=width;layout.cardHeight[0]=height;return layout;
    }
    private static float capacityWidth(int count){return (MAX_WIDTH-2*(PADDING+BORDER)-MIN_GAP*Math.max(0,count-1))/Math.max(1,count);}
    private static float baseWidth(int count,String size){
        float medium=144;
        float width="small".equals(size)?112:"large".equals(size)?168:144;
        // More apps lower the common baseline once, independently of focus and size choice.
        return width*Math.min(1,capacityWidth(count)/medium);
    }
    private static float capacityHeight(String titlePosition){return 540-BOTTOM-("top-left".equals(titlePosition)?344:TOP)-2*(PADDING+BORDER)-("below".equals(titlePosition)?25.6f:0);}
    static int maximumScale(int count,String size,String shape,boolean magnify,int magnification,String titlePosition){
        if(count==0)return 130;
        float aspect="rectangle".equals(shape)?16/9f:1;
        float limit=Math.min(capacityWidth(count),capacityHeight(titlePosition)*aspect)/baseWidth(count,size)*100;
        return Math.min(130,5*(int)Math.floor((limit+.001f)/5));
    }
    static DockLayout create(int count,int focus,String size,String shape,int scalePercent,boolean magnify,int magnification,String titlePosition){
        count=Math.max(0,count);float aspect="rectangle".equals(shape)?16/9f:1;
        boolean names="below".equals(titlePosition);
        float card=baseWidth(count,size)*Math.min(scalePercent,maximumScale(count,size,shape,magnify,magnification,titlePosition))/100f;
        float[] widths=new float[count];float occupied=0;
        boolean focused=magnify&&focus>=0&&focus<count;
        // Limit focus growth to the available height without shrinking the normal icons.
        float zoom=focused?Math.min(magnification/100f,capacityHeight(titlePosition)*aspect/card):1;
        for(int i=0;i<count;i++){int distance=Math.abs(i-focus);float weight=!focused?1:1+(zoom-1)*(distance==0?1:distance==1?.35f:0);widths[i]=card*weight;occupied+=widths[i];}
        float gap=count<2?0:Math.max(MIN_GAP,Math.min(GAP,(MAX_WIDTH-2*(PADDING+BORDER)-occupied)/(count-1)));
        float available=MAX_WIDTH-2*(PADDING+BORDER)-gap*Math.max(0,count-1);
        if(focused&&occupied>available){
            // Keep the selected card's requested magnification; only other cards yield space.
            float fit=(available-widths[focus])/(occupied-widths[focus]);
            for(int i=0;i<count;i++)if(i!=focus)widths[i]*=fit;
        }
        float width=0,largest=0;for(float value:widths){width+=value;largest=Math.max(largest,value);}
        width=count==0?0:width+gap*(count-1)+2*(PADDING+BORDER);
        float labelSize=14,labelGap=names?6:0,labelHeight=names?labelSize*1.4f:0;
        float height=count==0?0:largest/aspect+labelGap+labelHeight+2*(PADDING+BORDER);
        float left=(960-width)/2,top=540-BOTTOM-height;
        DockLayout layout=new DockLayout(count,left,top,width,height,PADDING,gap,labelSize,labelGap,SurfaceCorners.PANEL);
        float x=left+PADDING+BORDER;
        for(int i=0;i<count;i++){layout.x[i]=x;layout.cardWidth[i]=widths[i];layout.cardHeight[i]=widths[i]/aspect;layout.y[i]=top+height-PADDING-BORDER-labelHeight-labelGap-layout.cardHeight[i];x+=layout.cardWidth[i]+gap;}
        return layout;
    }
}
