package tw.boox.voicepage;
/** Pure position rules for the floating ball, kept free of Android types so they are unit-tested. */
public final class BallLayout {
    /** Part of the ball width still visible when it is tucked against a screen edge. */
    public static final float TUCKED_FRACTION=.45f;
    public static final int LEFT=-1, NONE=0, RIGHT=1;
    private BallLayout(){}
    /** Edge the ball should tuck into when the finger is released at x, or NONE to stay put. */
    public static int edgeFor(float fingerX,int ballSize,int screenWidth){
        float zone=ballSize*.8f;
        if(fingerX<=zone)return LEFT;
        if(fingerX>=screenWidth-zone)return RIGHT;
        return NONE;
    }
    public static int tuckedWidth(int ballSize){return Math.max(1,Math.round(ballSize*TUCKED_FRACTION));}
    public static int clamp(int value,int min,int max){return max<min?min:Math.max(min,Math.min(max,value));}
    /** Window x for a ball tucked into the given edge. */
    public static int tuckedX(int side,int ballSize,int screenWidth){
        return side==LEFT?0:screenWidth-tuckedWidth(ballSize);
    }
    /** Window x for a fully shown ball next to the given edge. */
    public static int expandedX(int side,int ballSize,int screenWidth,int margin){
        return side==LEFT?margin:screenWidth-ballSize-margin;
    }
    /** Side a free-floating ball is closest to. */
    public static int nearestSide(int x,int ballSize,int screenWidth){
        return x+ballSize/2f<screenWidth/2f?LEFT:RIGHT;
    }
}
