package tw.boox.voicepage;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
/**
 * Floating microphone ball drawn as an accessibility overlay (no "draw over other apps" permission).
 * Tap: start/stop listening. Drag to a side edge: tuck away; tap the tucked ball to show it again.
 * Hold 5 seconds: open the app. Black = listening, grey = loading, white = off.
 */
final class FloatingBall {
    static final String PREF_SHOW="show_ball";
    private static final String TAG="BOOXVoiceBall";
    private static final long LONG_PRESS_MS=5000L;
    private final Context context;
    private final WindowManager windows;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final int size, margin, slop;
    private final BallView view;
    private final WindowManager.LayoutParams params;
    private boolean attached, tucked;
    private int side=BallLayout.RIGHT;

    FloatingBall(Context context){
        this.context=context;
        windows=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        prefs=context.getSharedPreferences("state",Context.MODE_PRIVATE);
        float density=context.getResources().getDisplayMetrics().density;
        size=Math.round(56*density);
        margin=Math.round(6*density);
        slop=ViewConfiguration.get(context).getScaledTouchSlop()*2;
        view=new BallView(context);
        params=new WindowManager.LayoutParams(size,size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        view.setOnTouchListener(new Touch());
    }

    void show(){
        if(attached||windows==null)return;
        Point screen=screen();
        side=prefs.getInt("ball_side",BallLayout.RIGHT);
        tucked=prefs.getBoolean("ball_tucked",false);
        params.y=prefs.getInt("ball_y",Math.round(screen.y*.30f));
        params.x=prefs.getInt("ball_x",BallLayout.expandedX(side,size,screen.x,margin));
        if(tucked)tuck(side,false);else place(params.x,params.y,false);
        try{windows.addView(view,params);attached=true;logPosition();}
        catch(RuntimeException e){Log.w(TAG,"addView",e);}
    }
    void hide(){
        if(!attached)return;
        main.removeCallbacksAndMessages(null);
        try{windows.removeViewImmediate(view);}catch(RuntimeException e){Log.w(TAG,"removeView",e);}
        attached=false;
    }
    void refresh(){view.invalidate();}
    /** Re-fit after rotation or screen size change. */
    void onScreenChanged(){
        if(!attached)return;
        if(tucked)tuck(side,true);else place(params.x,params.y,true);
    }
    /** Ball bounds {left, top, right, bottom} on screen, or null when hidden. */
    int[] bounds(){
        if(!attached)return null;
        return new int[]{params.x,params.y,params.x+params.width,params.y+params.height};
    }

    private Point screen(){
        Point p=new Point();
        windows.getDefaultDisplay().getRealSize(p);
        return p;
    }
    private void place(int x,int y,boolean apply){
        Point screen=screen();
        tucked=false;
        params.width=size;
        params.x=BallLayout.clamp(x,0,screen.x-size);
        params.y=BallLayout.clamp(y,0,screen.y-size);
        side=BallLayout.nearestSide(params.x,size,screen.x);
        if(apply)apply();
    }
    private void tuck(int edge,boolean apply){
        Point screen=screen();
        tucked=true;side=edge;
        params.width=BallLayout.tuckedWidth(size);
        params.x=BallLayout.tuckedX(edge,size,screen.x);
        params.y=BallLayout.clamp(params.y,0,screen.y-size);
        if(apply)apply();
    }
    private void expand(){
        place(BallLayout.expandedX(side,size,screen().x,margin),params.y,true);
        save();
    }
    private void apply(){
        if(!attached)return;
        try{windows.updateViewLayout(view,params);}catch(RuntimeException e){Log.w(TAG,"update",e);}
        view.invalidate();
        logPosition();
    }
    private void save(){
        prefs.edit().putInt("ball_x",params.x).putInt("ball_y",params.y)
            .putInt("ball_side",side).putBoolean("ball_tucked",tucked).apply();
    }
    private void logPosition(){
        Log.i(TAG,"center="+(params.x+params.width/2)+","+(params.y+params.height/2)+" tucked="+tucked);
    }

    private void toggleListening(){
        if(OfflineListeningService.running){
            OfflineListeningService.stopFromUi();
        }else{
            // Microphone foreground services may only start while the app is visible, so start
            // through an invisible activity that closes itself immediately.
            try{
                context.startActivity(new Intent(context,StartListeningActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION
                        |Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
            }catch(RuntimeException e){Log.w(TAG,"start listening",e);}
        }
    }
    private void openApp(){
        try{
            context.startActivity(new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }catch(RuntimeException e){Log.w(TAG,"open app",e);}
    }

    private final class Touch implements View.OnTouchListener {
        private float downX, downY;
        private int startX, startY;
        private boolean dragging, longPressed;
        private final Runnable longPress=()->{longPressed=true;Log.i(TAG,"long press");openApp();};
        @Override public boolean onTouch(View v,MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=e.getRawX();downY=e.getRawY();
                    startX=params.x;startY=params.y;
                    dragging=false;longPressed=false;
                    main.postDelayed(longPress,LONG_PRESS_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:{
                    float dx=e.getRawX()-downX, dy=e.getRawY()-downY;
                    if(!dragging&&!longPressed&&Math.hypot(dx,dy)>slop){
                        dragging=true;main.removeCallbacks(longPress);
                        if(tucked){
                            // Pull the tucked ball out so it follows the finger.
                            startX=BallLayout.expandedX(side,size,screen().x,margin);
                        }
                    }
                    if(dragging)place(startX+Math.round(dx),startY+Math.round(dy),true);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    main.removeCallbacks(longPress);
                    if(longPressed)return true;
                    if(dragging){
                        int edge=BallLayout.edgeFor(e.getRawX(),size,screen().x);
                        if(edge!=BallLayout.NONE)tuck(edge,true);
                        save();
                    }else if(tucked){
                        expand();
                    }else{
                        toggleListening();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    main.removeCallbacks(longPress);
                    if(dragging)save();
                    return true;
                default:
                    return false;
            }
        }
    }

    /** Draws the ball; pure black, white and one grey so it stays crisp on e-ink. */
    private final class BallView extends View {
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect=new RectF();
        BallView(Context c){
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            setContentDescription("語音翻頁浮球");
        }
        @Override protected void onDraw(Canvas canvas){
            boolean on=OfflineListeningService.running, ready=OfflineListeningService.ready;
            int bg=on?(ready?Color.BLACK:Color.rgb(128,128,128)):Color.WHITE;
            int fg=on?Color.WHITE:Color.BLACK;
            float s=size, r=s/2f;
            // When tucked, the circle extends past the window edge facing the screen border.
            float cx=tucked?(side==BallLayout.LEFT?getWidth()-r:r):r, cy=getHeight()/2f;
            float border=s*.06f;
            fill.setStyle(Paint.Style.FILL);fill.setColor(bg);
            canvas.drawCircle(cx,cy,r-border/2f,fill);
            stroke.setColor(Color.BLACK);stroke.setStrokeWidth(border);
            canvas.drawCircle(cx,cy,r-border/2f,stroke);
            if(tucked)return;
            // Microphone: capsule, cradle arc, stem and base.
            fill.setColor(fg);
            rect.set(cx-s*.10f,cy-s*.25f,cx+s*.10f,cy+s*.05f);
            canvas.drawRoundRect(rect,s*.10f,s*.10f,fill);
            stroke.setColor(fg);stroke.setStrokeWidth(s*.045f);
            rect.set(cx-s*.17f,cy-s*.12f,cx+s*.17f,cy+s*.14f);
            canvas.drawArc(rect,0,180,false,stroke);
            canvas.drawLine(cx,cy+s*.14f,cx,cy+s*.23f,stroke);
            canvas.drawLine(cx-s*.09f,cy+s*.23f,cx+s*.09f,cy+s*.23f,stroke);
        }
    }
}
