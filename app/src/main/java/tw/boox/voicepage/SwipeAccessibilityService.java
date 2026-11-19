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
    public static final String PREF_TURN_MODE="turn_mode";
    private static volatile SwipeAccessibilityService connected;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private long lastGestureAt=-10000L;
    private FloatingBall ball;
    public static boolean isConnected(){return connected!=null;}
    /** Turns the page with the gesture chosen in settings (swipe left/right, tap sides, swipe up/down). */
    public static boolean swipe(boolean next) {
        SwipeAccessibilityService s=connected;
        return s!=null && s.performSwipe(next);
    }
    /** Leaves the current app for the home screen. */
    public static boolean goHome(){
        SwipeAccessibilityService s=connected;
        return s!=null && s.performGlobalAction(GLOBAL_ACTION_HOME);
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
        // Without this service no command can act, so do not keep the microphone on.
        if(OfflineListeningService.running)OfflineListeningService.stopFromUi("無障礙服務已關閉，已停止聆聽");
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
        int mode=getSharedPreferences("state",MODE_PRIVATE).getInt(PREF_TURN_MODE,GestureGeometry.SWIPE_HORIZONTAL);
        float[] g=GestureGeometry.plan(screen.x,screen.y,mode,next,ball==null?null:ball.bounds());
        Path path=new Path();
        path.moveTo(g[0],g[1]);
        if(g[0]!=g[2]||g[1]!=g[3])path.lineTo(g[2],g[3]);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,(long)g[4])).build();
        try {
            boolean accepted=dispatchGesture(gesture,null,new Handler(Looper.getMainLooper()));
            if(accepted)lastGestureAt=now;
            return accepted;
        }catch(RuntimeException e){return false;}
    }
}
