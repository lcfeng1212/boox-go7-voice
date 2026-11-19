package tw.boox.voicepage;
/** Computes positions using current screen width and height, including landscape. */
public final class GestureGeometry {
    /** Heights (fraction of screen) tried in order; the first one clear of the floating ball wins. */
    private static final float[] ROWS={.52f,.70f,.35f,.82f,.22f};
    private GestureGeometry() {}
    public static float[] coordinates(int width,int height,boolean next) {
        return coordinates(width,height,next,null);
    }
    /**
     * @param avoid floating ball bounds {left, top, right, bottom} in screen pixels, or null.
     *              A swipe that starts on the ball would be delivered to the ball, not the reader.
     */
    public static float[] coordinates(int width,int height,boolean next,int[] avoid) {
        if(width<=0||height<=0) throw new IllegalArgumentException("screen size");
        float a=width*(next ? .78f : .22f);
        float b=width*(next ? .22f : .78f);
        float y=height*ROWS[0];
        if(avoid!=null){
            float pad=Math.max(24f,height*.03f);
            for(float row:ROWS){
                float candidate=height*row;
                boolean hits=avoid[0]<width*.78f+pad&&avoid[2]>width*.22f-pad
                    &&avoid[1]<candidate+pad&&avoid[3]>candidate-pad;
                if(!hits){y=candidate;break;}
            }
        }
        return new float[]{a,y,b,y};
    }
}
