package tw.boox.voicepage;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
/** High-contrast, lightweight UI for e-ink devices. */
public final class MainActivity extends Activity {
    private static final int MIC_REQUEST=101;
    private TextView status;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresher=new Runnable(){
        @Override public void run(){refresh();handler.postDelayed(this,1500);}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroller=new ScrollView(this);scroller.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(Color.WHITE);
        scroller.addView(root);
        label(root,"BOOX 中文語音翻頁 V"+versionName(),24);
        label(root,"離線中文語音：說「下一頁」、「上一頁」翻頁，說「回到首頁」或「關閉」離開並停止聆聽。",18);
        status=label(root,"狀態載入中",16);
        CheckBox grammar=new CheckBox(this);
        grammar.setText("限定指令模式（建議：較準、較省電、反應較快）");
        grammar.setTextSize(16);grammar.setTextColor(Color.BLACK);
        grammar.setChecked(prefs().getBoolean(OfflineListeningService.PREF_GRAMMAR,true));
        grammar.setOnCheckedChangeListener((v,checked)->{
            prefs().edit().putBoolean(OfflineListeningService.PREF_GRAMMAR,checked).apply();
            if(OfflineListeningService.running)toast("已儲存，請按「停止聆聽」再重新開始才會生效");
        });
        root.addView(grammar);
        CheckBox complete=new CheckBox(this);
        complete.setText("完整口令模式（說完整句、句中沒有其他話才執行；較不易誤翻，反應稍慢）");
        complete.setTextSize(16);complete.setTextColor(Color.BLACK);
        complete.setChecked(prefs().getBoolean(OfflineListeningService.PREF_COMPLETE,false));
        complete.setOnCheckedChangeListener((v,checked)->{
            prefs().edit().putBoolean(OfflineListeningService.PREF_COMPLETE,checked).apply();
            OfflineListeningService.completeMode=checked;   // applies immediately
        });
        root.addView(complete);
        CheckBox ball=new CheckBox(this);
        ball.setText("顯示浮球（點一下開／關聆聽；拖到左右邊緣收起；長按 5 秒開啟本 App）");
        ball.setTextSize(16);ball.setTextColor(Color.BLACK);
        ball.setChecked(prefs().getBoolean(FloatingBall.PREF_SHOW,true));
        ball.setOnCheckedChangeListener((v,checked)->{
            prefs().edit().putBoolean(FloatingBall.PREF_SHOW,checked).apply();
            SwipeAccessibilityService.ballSettingChanged();
            if(checked&&!SwipeAccessibilityService.isConnected())toast("浮球需要先啟用無障礙手勢服務");
        });
        root.addView(ball);
        label(root,"翻頁方式（所有閱讀 App 共用）",16);
        RadioGroup modes=new RadioGroup(this);
        String[] names={"左右滑動（下一頁＝往左滑）","點擊左右兩側（下一頁＝點右側）","上下滑動（下一頁＝往上滑）"};
        int[] values={GestureGeometry.SWIPE_HORIZONTAL,GestureGeometry.TAP_SIDES,GestureGeometry.SWIPE_VERTICAL};
        int current=prefs().getInt(SwipeAccessibilityService.PREF_TURN_MODE,GestureGeometry.SWIPE_HORIZONTAL);
        for(int i=0;i<names.length;i++){
            RadioButton option=new RadioButton(this);
            option.setId(1000+values[i]);
            option.setText(names[i]);option.setTextSize(16);option.setTextColor(Color.BLACK);
            modes.addView(option);
            if(values[i]==current)option.setChecked(true);
        }
        modes.setOnCheckedChangeListener((group,id)->
            prefs().edit().putInt(SwipeAccessibilityService.PREF_TURN_MODE,id-1000).apply());
        root.addView(modes);
        button(root,"① 開啟無障礙設定",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(root,"② 開始離線聆聽",this::begin);
        button(root,"停止聆聽",()->{
            Intent stop=new Intent(this,OfflineListeningService.class).setAction(OfflineListeningService.ACTION_STOP);
            startService(stop);refresh();
        });
        button(root,"測試下一頁",()->testSwipe(true));
        button(root,"測試上一頁",()->testSwipe(false));
        label(root,"可用指令：下一頁、下、左滑（下一頁）；上一頁、上、右滑（上一頁）；回到首頁、關閉（回到桌面並停止聆聽）。「下」「上」「回到首頁」「關閉」要單獨說，說完才會執行。",15);
        label(root,"先啟用 BOOX 語音翻頁手勢無障礙服務，再開始聆聽並允許麥克風權限。返回閱讀 App 後說下一頁或上一頁。通知列可停止。螢幕關閉時自動暫停收音，解鎖後自動恢復。若限定指令模式誤判或聽不到，可取消勾選改用一般模式比較。",15);
        setContentView(scroller);
    }
    private TextView label(LinearLayout root,String text,int size){
        TextView view=new TextView(this);view.setText(text);view.setTextSize(size);
        view.setTextColor(Color.BLACK);view.setPadding(0,8,0,16);root.addView(view);
        return view;
    }
    private void button(LinearLayout root,String text,Runnable action){
        Button view=new Button(this);view.setText(text);view.setAllCaps(false);view.setTextSize(18);
        view.setOnClickListener(v->action.run());
        root.addView(view,new LinearLayout.LayoutParams(-1,80));
    }
    private void begin(){
        if(!SwipeAccessibilityService.isConnected()){toast("請先啟用無障礙手勢");return;}
        // Android 13 needs the notification permission for the "listening / stop" notification.
        // Asked once together with the microphone; listening still works if it is refused.
        java.util.List<String> ask=new java.util.ArrayList<>();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.RECORD_AUDIO);
        if(Build.VERSION.SDK_INT>=33&&!prefs().getBoolean("asked_notifications",false)
                &&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            ask.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!ask.isEmpty()){
            if(ask.contains(Manifest.permission.POST_NOTIFICATIONS))
                prefs().edit().putBoolean("asked_notifications",true).apply();
            requestPermissions(ask.toArray(new String[0]),MIC_REQUEST);return;
        }
        Intent start=new Intent(this,OfflineListeningService.class).setAction(OfflineListeningService.ACTION_START);
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(start);else startService(start);}
        catch(RuntimeException e){toast("啟動失敗："+e.getClass().getSimpleName());}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==MIC_REQUEST){
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)begin();
            else toast("尚未授予麥克風權限");
        }
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresher);handler.post(refresher);}
    @Override protected void onPause(){handler.removeCallbacks(refresher);super.onPause();}
    private void refresh(){
        if(status==null)return;
        String detail=prefs().getString("status","尚未開始");
        String text="無障礙："+(SwipeAccessibilityService.isConnected()?"已啟用":"未啟用")+
           "\n麥克風："+micState()+"\n狀態："+detail;
        // Only redraw when the text changes: every redraw costs an e-ink refresh.
        if(!text.contentEquals(status.getText()))status.setText(text);
    }
    private static String micState(){
        if(OfflineListeningService.running){
            if(OfflineListeningService.paused)return "已暫停（螢幕關閉）";
            return OfflineListeningService.ready?"聆聽中":"載入中";
        }
        return OfflineListeningService.gaveUp?"已停止（連續失敗，請重試）":"未執行";
    }
    private SharedPreferences prefs(){return getSharedPreferences("state",MODE_PRIVATE);}
    private String versionName(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(PackageManager.NameNotFoundException e){return "?";}
    }
    private void testSwipe(boolean next){toast(SwipeAccessibilityService.swipe(next)?"已送出手勢":"手勢未送出，請確認設定");}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
