package tw.boox.voicepage;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import org.json.*;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import java.io.File;
/**
 * Foreground offline microphone recognizer; no INTERNET permission.
 * Pauses the microphone while the screen is off and resumes after unlock (model stays loaded).
 * Gives up after RestartPolicy.MAX_FAILURES consecutive microphone failures.
 */
public final class OfflineListeningService extends Service {
    public static final String ACTION_START="tw.boox.voicepage.START_OFFLINE";
    public static final String ACTION_STOP="tw.boox.voicepage.STOP_OFFLINE";
    public static final String PREF_GRAMMAR="grammar_mode";
    public static final String PREF_COMPLETE="complete_command_mode";
    /** Complete-command mode, read on every result so the setting applies immediately. */
    public static volatile boolean completeMode=false;
    /** Consecutive-failure count resets only after this much uninterrupted listening. */
    private static final long STABLE_MS=60000L;
    public static volatile boolean running=false;
    /** True once the recognizer is actually listening (false while loading, restarting or paused). */
    public static volatile boolean ready=false;
    /** True while the microphone is paused because the screen is off. */
    public static volatile boolean paused=false;
    /** True after listening stopped because the microphone kept failing; the ball offers a retry. */
    public static volatile boolean gaveUp=false;
    private static volatile OfflineListeningService instance;
    private static final String CHANNEL="offline_chinese_voice";
    private static final int NOTICE=4072;
    private static final float RATE=16000f;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CommandEngine commands=new CommandEngine(900);
    private final RestartPolicy retryBackoff=new RestartPolicy();
    private final ListeningHealth health=new ListeningHealth();
    private Model model;
    private Recognizer recognizer;
    private SpeechService speech;
    private Runnable pendingRetry, healthCheck;
    /** Changes on every start/stop: invalidates a model load from an earlier run. */
    private volatile int runId=0;
    /** Changes on every recognizer (re)start, pause and stop: invalidates stale callbacks. */
    private int session=0;
    private boolean stableReset;
    private long listeningSince;
    private String lastNotice;
    private BroadcastReceiver screenReceiver;

    @Override public void onCreate(){
        super.onCreate();instance=this;notificationChannel();
        completeMode=getSharedPreferences("state",MODE_PRIVATE).getBoolean(PREF_COMPLETE,false);
    }
    /** Stops listening from the floating ball without going through the background-start rules. */
    static void stopFromUi(){stopFromUi("已停止聆聽");}
    static void stopFromUi(String reason){
        OfflineListeningService s=instance;
        if(s!=null)s.main.post(()->{if(running)s.stopNow(reason);});
        else{running=false;ready=false;paused=false;SwipeAccessibilityService.listeningStateChanged();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){
            // Not listening (e.g. it already gave up): just go away and keep the last status text.
            if(running)stopNow("已停止聆聽");else stopSelf();
            return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(action))return START_NOT_STICKY;
        // Every startForegroundService() must be answered with startForeground(), even when we
        // are about to stop again; otherwise Android treats it as a violation and may crash the app.
        if(!running)lastNotice="正在載入語音模型";
        try{startForeground(NOTICE,notification(lastNotice));}
        catch(RuntimeException ex){
            update("前景服務無法啟動："+ex.getClass().getSimpleName());
            stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_NOT_STICKY;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            update("請先授權麥克風");
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;
        }
        running=true;gaveUp=false;paused=false;
        retryBackoff.succeeded();
        registerScreenReceiver();
        final int run=++runId;
        ++session;
        if(model!=null){begin(session);return START_NOT_STICKY;}
        update("正在載入離線中文模型");
        new Thread(()->{
            Model loaded=null;
            try {
                File folder=ModelAssets.prepare(getApplicationContext(),false);
                if(run!=runId)return;   // stopped while extracting
                try{loaded=new Model(folder.getAbsolutePath());}
                catch(Exception first){
                    // A damaged copy can pass the size check; extract once more and retry.
                    Log.w("BOOXVoice","model load failed, re-extracting",first);
                    folder=ModelAssets.prepare(getApplicationContext(),true);
                    if(run!=runId)return;
                    loaded=new Model(folder.getAbsolutePath());
                }
                Model completed=loaded;
                main.post(()->{
                    if(!running||run!=runId){completed.close();return;}
                    model=completed;
                    if(!paused)begin(++session);
                });
            }catch(Exception ex){
                if(loaded!=null)loaded.close();
                String reason=ex.getClass().getSimpleName()+": "+ex.getMessage();
                main.post(()->{
                    if(running&&run==runId)stopNow("載入模型失敗："+reason);
                });
            }
        },"load-model").start();
        return START_NOT_STICKY;
    }
    private void begin(int ticket){
        if(!running||paused||ticket!=session||model==null)return;
        try {
            boolean grammar=getSharedPreferences("state",MODE_PRIVATE).getBoolean(PREF_GRAMMAR,true);
            if(grammar){
                // Restricting decoding to the command phrases is faster, uses less CPU and
                // avoids matching unrelated speech; fall back to the full model if it fails.
                try{recognizer=new Recognizer(model,RATE,CommandEngine.grammarJson());}
                catch(Exception ex){Log.w("BOOXVoice","grammar recognizer",ex);grammar=false;}
            }
            if(recognizer==null)recognizer=new Recognizer(model,RATE);
            speech=new SpeechService(recognizer,RATE);
            if(!speech.startListening(new Receiver(ticket,grammar)))throw new IllegalStateException("startListening false");
            stableReset=false;
            listeningSince=SystemClock.elapsedRealtime();
            ready=true;
            health.started(SystemClock.elapsedRealtime());
            scheduleHealth(ticket);
            update((grammar?"離線聆聽中（限定指令）":"離線聆聽中（一般模式）")+"：下一頁／上一頁");
        }catch(Exception ex){update("收音啟動失敗："+ex.getClass().getSimpleName());retry(ticket);}
    }
    private final class Receiver implements RecognitionListener {
        private final int ticket;
        /** In grammar mode partial hypotheses are stable enough to act on before end-of-speech. */
        private final boolean actOnPartial;
        private boolean firedThisUtterance;
        Receiver(int ticket,boolean actOnPartial){this.ticket=ticket;this.actOnPartial=actOnPartial;}
        private boolean current(){return running&&ticket==session;}
        @Override public void onResult(String result){
            if(!current())return;
            heartbeat();
            if(firedThisUtterance){firedThisUtterance=false;return;}
            handle(field(result,"text"),false);
        }
        @Override public void onPartialResult(String result){
            if(!current())return;
            heartbeat();
            if(!actOnPartial||completeMode||firedThisUtterance)return;
            if(handle(field(result,"partial"),true))firedThisUtterance=true;
        }
        private boolean handle(String heard,boolean partial){
            final int command=commands.accept(heard,SystemClock.elapsedRealtime(),partial,completeMode);
            if(command==CommandEngine.NONE)return false;
            main.post(()->{
                if(!current())return;
                if(command==CommandEngine.HOME){
                    boolean ok=SwipeAccessibilityService.goHome();
                    stopNow(ok?"已回到首頁並停止聆聽":"回到首頁失敗（無障礙未啟用），已停止聆聽");
                    return;
                }
                boolean next=command==CommandEngine.NEXT;
                boolean ok=SwipeAccessibilityService.swipe(next);
                update((next?"下一頁":"上一頁")+(ok?"：已送出手勢":"：無障礙手勢未成功"));
            });
            return true;
        }
        @Override public void onFinalResult(String result){}
        @Override public void onTimeout(){if(current())main.post(()->retry(ticket));}
        @Override public void onError(Exception ex){
            Log.w("BOOXVoice","Recognition error",ex);
            if(current())main.post(()->retry(ticket));
        }
    }
    private static String field(String json,String name){
        try{return new JSONObject(json).optString(name,"");}
        catch(JSONException ignored){return "";}
    }
    private void heartbeat(){
        long now=SystemClock.elapsedRealtime();
        health.heartbeat(now);
        // Reset the failure count only after a stretch of stable listening, so a microphone that
        // starts and then fails right away still reaches the retry limit.
        if(!stableReset&&now-listeningSince>=STABLE_MS){stableReset=true;retryBackoff.succeeded();}
    }
    private void scheduleHealth(int ticket){
        clearHealth();
        healthCheck=new Runnable(){
            @Override public void run(){
                if(!running||ticket!=session)return;
                if(health.stale(SystemClock.elapsedRealtime(),35000L)){
                    update("辨識逾時，正在重新啟動");retry(ticket);
                }else main.postDelayed(this,12000L);
            }
        };
        main.postDelayed(healthCheck,12000L);
    }
    private void retry(int ticket){
        if(!running||ticket!=session)return;
        ++session;
        clearHealth();closeSpeech();
        long wait=retryBackoff.failed();
        if(retryBackoff.exhausted()){
            gaveUp=true;
            stopNow("麥克風連續 "+RestartPolicy.MAX_FAILURES+" 次啟動失敗，已停止。點浮球重試");
            return;
        }
        final int next=session;
        update("重新啟動語音辨識中");
        pendingRetry=()->{pendingRetry=null;begin(next);};
        main.postDelayed(pendingRetry,wait);
    }

    // ---- Screen off: pause the microphone; unlock: resume ----------------------------------
    private void registerScreenReceiver(){
        if(screenReceiver!=null)return;
        screenReceiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent i){
                String a=i.getAction();
                if(Intent.ACTION_SCREEN_OFF.equals(a))pause();
                else if(Intent.ACTION_USER_PRESENT.equals(a))resume();
                else if(Intent.ACTION_SCREEN_ON.equals(a)){
                    // Without a lock screen USER_PRESENT may never come; resume once unlocked.
                    KeyguardManager k=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
                    if(k==null||!k.isKeyguardLocked())resume();
                }
            }
        };
        IntentFilter f=new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        // USER_PRESENT is sent by SystemUI, not the system uid, so the receiver must be exported.
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,f,Context.RECEIVER_EXPORTED);
        else registerReceiver(screenReceiver,f);
    }
    private void unregisterScreenReceiver(){
        if(screenReceiver==null)return;
        try{unregisterReceiver(screenReceiver);}catch(RuntimeException ignored){}
        screenReceiver=null;
    }
    private void pause(){
        if(!running||paused)return;
        paused=true;++session;
        if(pendingRetry!=null)main.removeCallbacks(pendingRetry);
        pendingRetry=null;
        clearHealth();closeSpeech();
        update("螢幕關閉，已暫停聆聽（解鎖後自動恢復）");
    }
    private void resume(){
        if(!running||!paused)return;
        paused=false;
        retryBackoff.succeeded();
        if(model!=null)begin(++session);
        else update("正在載入離線中文模型");
    }

    private void clearHealth(){
        if(healthCheck!=null)main.removeCallbacks(healthCheck);
        healthCheck=null;
    }
    private void closeSpeech(){
        ready=false;
        if(speech!=null){
            try{speech.cancel();}catch(Exception e){Log.w("BOOXVoice","cancel",e);}
            try{speech.shutdown();}catch(Exception e){Log.w("BOOXVoice","shutdown",e);}
            speech=null;
        }
        if(recognizer!=null){
            try{recognizer.close();}catch(Exception ignored){}
            recognizer=null;
        }
    }
    private void stopNow(String reason){
        running=false;paused=false;++session;++runId;
        if(pendingRetry!=null)main.removeCallbacks(pendingRetry);
        pendingRetry=null;
        clearHealth();closeSpeech();
        unregisterScreenReceiver();
        if(model!=null){try{model.close();}catch(Exception ignored){}model=null;}
        update(reason);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private void update(String s){
        getSharedPreferences("state",MODE_PRIVATE).edit().putString("status",s).apply();
        Log.i("BOOXVoice","status: "+s);
        SwipeAccessibilityService.listeningStateChanged();
        refreshNotice();
    }
    /** Keeps the ongoing notification in step with the state (only re-posted when its text changes). */
    private void refreshNotice(){
        if(!running)return;
        String text=paused?"已暫停（螢幕關閉），解鎖後自動恢復"
            :ready?"正在聆聽；按停止可結束":"正在載入或重新啟動語音辨識";
        if(text.equals(lastNotice))return;
        lastNotice=text;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager!=null){
            try{manager.notify(NOTICE,notification(text));}catch(RuntimeException ignored){}
        }
    }
    private void notificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(manager!=null)manager.createNotificationChannel(
                new NotificationChannel(CHANNEL,"BOOX 離線語音翻頁",NotificationManager.IMPORTANCE_LOW));
        }
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,
            new Intent(this,OfflineListeningService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BOOX 離線中文語音翻頁")
            .setContentText(text).setOnlyAlertOnce(true)
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"停止",stop).build();
    }
    @Override public void onDestroy(){
        running=false;ready=false;paused=false;++session;++runId;
        if(pendingRetry!=null)main.removeCallbacks(pendingRetry);
        clearHealth();closeSpeech();
        unregisterScreenReceiver();
        if(model!=null){try{model.close();}catch(Exception ignored){}model=null;}
        if(instance==this)instance=null;
        SwipeAccessibilityService.listeningStateChanged();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
