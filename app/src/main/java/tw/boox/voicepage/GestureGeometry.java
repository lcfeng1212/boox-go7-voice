package tw.boox.voicepage;
/** Page-turn gesture positions from the current screen size, for every page-turn mode. */
public final class GestureGeometry {
    /** Page-turn modes, applied to all apps. */
    public static final int SWIPE_HORIZONTAL=0, TAP_SIDES=1, SWIPE_VERTICAL=2;
    /** Alternative rows/columns (fraction of screen) tried in order; the first clear of the ball wins. */
    private static final float[] LANES={.52f,.70f,.35f,.82f,.22f};
    private static final float[] COLUMNS={.50f,.65f,.35f,.80f,.20f};
    private GestureGeometry() {}
    public static float[] coordinates(int width,int height,boolean next) {
        return coordinates(width,height,next,null);
    }
    public static float[] coordinates(int width,int height,boolean next,int[] avoid) {
        float[] p=plan(width,height,SWIPE_HORIZONTAL,next,avoid);
        return new float[]{p[0],p[1],p[2],p[3]};
    }
    /**
     * @param avoid floating ball bounds {left, top, right, bottom} in screen pixels, or null.
     *              A gesture that touches the ball would be delivered to the ball, not the reader.
     * @return {startX, startY, endX, endY, durationMs}
     */
    public static float[] plan(int width,int height,int mode,boolean next,int[] avoid) {
        if(width<=0||height<=0) throw new IllegalArgumentException("screen size");
        float pad=Math.max(24f,Math.min(width,height)*.03f);
        float[] first=null;
        if(mode==SWIPE_VERTICAL){
            // Next page: swipe up. Previous page: swipe down.
            float a=height*(next?.75f:.25f), b=height*(next?.25f:.75f);
            for(float col:COLUMNS){
                float x=width*col;
                float[] p={x,a,x,b,260};
                if(first==null)first=p;
                if(!hits(avoid,x,Math.min(a,b),x,Math.max(a,b),pad))return p;
            }
        }else if(mode==TAP_SIDES){
            // Next page: tap the right side. Previous page: tap the left side.
            float x=width*(next?.85f:.15f);
            for(float lane:LANES){
                float y=height*lane;
                float[] p={x,y,x,y,60};
                if(first==null)first=p;
                if(!hits(avoid,x,y,x,y,pad))return p;
            }
        }else{
            // Next page: swipe left. Previous page: swipe right.
            float a=width*(next?.78f:.22f), b=width*(next?.22f:.78f);
            for(float lane:LANES){
                float y=height*lane;
                float[] p={a,y,b,y,260};
                if(first==null)first=p;
                if(!hits(avoid,Math.min(a,b),y,Math.max(a,b),y,pad))return p;
            }
        }
        return first;
    }
    private static boolean hits(int[] ball,float left,float top,float right,float bottom,float pad){
        return ball!=null&&ball[0]<right+pad&&ball[2]>left-pad&&ball[1]<bottom+pad&&ball[3]>top-pad;
    }
}
