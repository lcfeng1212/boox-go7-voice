package tw.boox.voicepage;
/** Computes positions using current screen width and height, including landscape. */
public final class GestureGeometry {
    private GestureGeometry() {}
    public static float[] coordinates(int width,int height,boolean next) {
        if(width<=0||height<=0) throw new IllegalArgumentException("screen size");
        float a=width*(next ? .78f : .22f);
        float b=width*(next ? .22f : .78f);
        return new float[]{a,height*.52f,b,height*.52f};
    }
}
