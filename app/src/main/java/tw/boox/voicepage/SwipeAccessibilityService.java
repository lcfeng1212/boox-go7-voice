package tw.boox.voicepage;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
/** Gesture-only accessibility service. Never extracts text from other apps. Hosts the floating ball. */
public final class SwipeAccessibilityService extends AccessibilityService {
    private static volatile SwipeAccessibilityService connected;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private long lastGestureAt=-10000L;
    private FloatingBall ball;
    public static boolean isConnected(){return connected!=null;}
    public static boolean swipe(boolean next) {
        SwipeAccessibilityService s=connected;
        return s!=null && s.performSwipe(next);
    }
    /** Called when listening starts, stops or finishes loading, so the ball shows the right state. */
    public static void listeningStateChanged(){
        MAIN.post(()->{SwipeAccessibilityService s=connected;if(s!=null&&s.ball!=null)s.ball.refresh();});
    }
    /** Shows or hides the ball according to the saved setting. */
    public static void ballSettingChanged(){
        MAIN.post(()->{SwipeAccessibilityService s=connected;if(s!=null)s.syncBall();});
    }
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        connected=this;
        syncBall();
    }
    private void syncBall(){
        boolean show=getSharedPreferences("state",MODE_PRIVATE).getBoolean(FloatingBall.PREF_SHOW,true);
        if(show){
            if(ball==null)ball=new FloatingBall(this);
            ball.show();
        }else if(ball!=null){
            ball.hide();
        }
    }
    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        if(ball!=null)ball.onScreenChanged();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(android.content.Intent intent){
        if(ball!=null)ball.hide();
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        if(ball!=null)ball.hide();
        if(connected==this)connected=null;
        super.onDestroy();
    }
    private boolean performSwipe(boolean next){
        long now=SystemClock.elapsedRealtime();
        if(now-lastGestureAt<500)return false;
        WindowManager manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(manager==null)return false;
        Point screen=new Point();
        manager.getDefaultDisplay().getRealSize(screen);
        float[] xy=GestureGeometry.coordinates(screen.x,screen.y,next,ball==null?null:ball.bounds());
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
