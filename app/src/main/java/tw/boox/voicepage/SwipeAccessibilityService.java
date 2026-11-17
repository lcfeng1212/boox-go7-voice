package tw.boox.voicepage;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
/** Gesture-only accessibility service. Never extracts text from other apps. */
public final class SwipeAccessibilityService extends AccessibilityService {
    private static volatile SwipeAccessibilityService connected;
    private long lastGestureAt=-10000L;
    public static boolean isConnected(){return connected!=null;}
    public static boolean swipe(boolean next) {
        SwipeAccessibilityService s=connected;
        return s!=null && s.performSwipe(next);
    }
    @Override protected void onServiceConnected(){super.onServiceConnected();connected=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(connected==this)connected=null;super.onDestroy();}
    private boolean performSwipe(boolean next){
        long now=SystemClock.elapsedRealtime();
        if(now-lastGestureAt<500)return false;
        WindowManager manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(manager==null)return false;
        Point screen=new Point();
        manager.getDefaultDisplay().getRealSize(screen);
        float[] xy=GestureGeometry.coordinates(screen.x,screen.y,next);
        Path path=new Path();
        path.moveTo(xy[0],xy[1]);path.lineTo(xy[2],xy[3]);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,260)).build();
        try {
            boolean accepted=dispatchGesture(gesture,null,new Handler(Looper.getMainLooper()));
            if(accepted)lastGestureAt=now;
            return accepted;
        }catch(RuntimeException e){return false;}
    }
}
