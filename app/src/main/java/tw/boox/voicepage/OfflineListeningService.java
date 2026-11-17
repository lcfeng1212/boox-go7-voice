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
/** Foreground offline microphone recognizer; no INTERNET permission. */
public final class OfflineListeningService extends Service {
    public static final String ACTION_START="tw.boox.voicepage.START_OFFLINE";
    public static final String ACTION_STOP="tw.boox.voicepage.STOP_OFFLINE";
    public static volatile boolean running=false;
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
    private int session=0;
    private boolean hadCallback;
    @Override public void onCreate(){super.onCreate();notificationChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){stopNow();return START_NOT_STICKY;}
        if(!ACTION_START.equals(action)||running)return START_NOT_STICKY;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            update("請先授權麥克風");stopSelf();return START_NOT_STICKY;
        }
        try{startForeground(NOTICE,notification());}
        catch(RuntimeException ex){
            update("前景服務無法啟動："+ex.getClass().getSimpleName());
            stopSelf();return START_NOT_STICKY;
        }
        running=true;
        final int ticket=++session;
        update("正在載入離線中文模型");
        new Thread(()->{
            Model loaded=null;
            try {
                File folder=ModelAssets.prepare(getApplicationContext());
                loaded=new Model(folder.getAbsolutePath());
                Model completed=loaded;
                main.post(()->{
                    if(!running||ticket!=session){completed.close();return;}
                    model=completed;begin(ticket);
                });
            }catch(Exception ex){
                if(loaded!=null)loaded.close();
                String reason=ex.getClass().getSimpleName()+": "+ex.getMessage();
                main.post(()->{
                    if(running&&ticket==session){stopNow();update("載入模型失敗："+reason);}
                });
            }
        },"load-model").start();
        return START_NOT_STICKY;
    }
    private void begin(int ticket){
        if(!running||ticket!=session||model==null)return;
        try {
            recognizer=new Recognizer(model,RATE);
            speech=new SpeechService(recognizer,RATE);
            if(!speech.startListening(new Receiver(ticket)))throw new IllegalStateException("startListening false");
            hadCallback=false;
            health.started(SystemClock.elapsedRealtime());
            scheduleHealth(ticket);
            update("離線聆聽中：下一頁／上一頁");
        }catch(Exception ex){update("收音啟動失敗："+ex.getClass().getSimpleName());retry(ticket);}
    }
    private final class Receiver implements RecognitionListener {
        private final int ticket;
        Receiver(int ticket){this.ticket=ticket;}
        private boolean current(){return running&&ticket==session;}
        @Override public void onResult(String result){
            if(!current())return;
            heartbeat();
            String heard="";
            try{heard=new JSONObject(result).optString("text","");}
            catch(JSONException ignored){}
            final int command=commands.accept(heard,SystemClock.elapsedRealtime());
            if(command!=CommandEngine.NONE)main.post(()->{
                if(!current())return;
                boolean next=command==CommandEngine.NEXT;
                boolean ok=SwipeAccessibilityService.swipe(next);
                update((next?"下一頁":"上一頁")+(ok?"：已送出手勢":"：無障礙手勢未成功"));
            });
        }
        @Override public void onPartialResult(String result){if(current())heartbeat();}
        @Override public void onFinalResult(String result){}
        @Override public void onTimeout(){if(current())main.post(()->retry(ticket));}
        @Override public void onError(Exception ex){
            Log.w("BOOXVoice","Recognition error",ex);
            if(current())main.post(()->retry(ticket));
        }
    }
    private void heartbeat(){
        health.heartbeat(SystemClock.elapsedRealtime());
        if(!hadCallback){hadCallback=true;retryBackoff.succeeded();}
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
        final int next=session;
        long wait=retryBackoff.failed();
        update("重新啟動語音辨識中");
        pendingRetry=()->{pendingRetry=null;begin(next);};
        main.postDelayed(pendingRetry,wait);
    }
    private void clearHealth(){
        if(healthCheck!=null)main.removeCallbacks(healthCheck);
        healthCheck=null;
    }
    private void closeSpeech(){
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
    private void stopNow(){
        running=false;++session;
        if(pendingRetry!=null)main.removeCallbacks(pendingRetry);
        pendingRetry=null;
        clearHealth();closeSpeech();
        if(model!=null){try{model.close();}catch(Exception ignored){}model=null;}
        update("已停止聆聽");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private void update(String s){
        getSharedPreferences("state",MODE_PRIVATE).edit().putString("status",s).apply();
    }
    private void notificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(manager!=null)manager.createNotificationChannel(
                new NotificationChannel(CHANNEL,"BOOX 離線語音翻頁",NotificationManager.IMPORTANCE_LOW));
        }
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,
            new Intent(this,OfflineListeningService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BOOX 離線中文語音翻頁")
            .setContentText("正在聆聽；按停止可結束")
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"停止",stop).build();
    }
    @Override public void onDestroy(){
        running=false;++session;
        if(pendingRetry!=null)main.removeCallbacks(pendingRetry);
        clearHealth();closeSpeech();
        if(model!=null){try{model.close();}catch(Exception ignored){}model=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
