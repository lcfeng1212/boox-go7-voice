package tw.boox.voicepage;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
/**
 * Invisible trampoline used by the floating ball. Android only lets a microphone foreground service
 * start while the app is visible, so this activity starts it from onResume and closes at once.
 */
public final class StartListeningActivity extends Activity {
    @Override protected void onResume(){
        super.onResume();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED
                ||!SwipeAccessibilityService.isConnected()){
            Toast.makeText(this,"請先在 App 內完成設定並開始聆聽一次",Toast.LENGTH_LONG).show();
            startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }else if(!OfflineListeningService.running){
            Intent start=new Intent(this,OfflineListeningService.class).setAction(OfflineListeningService.ACTION_START);
            try{
                if(Build.VERSION.SDK_INT>=26)startForegroundService(start);else startService(start);
                Log.i("BOOXVoiceBall","listening service started from ball");
            }catch(RuntimeException e){
                Log.w("BOOXVoiceBall","start service",e);
                Toast.makeText(this,"啟動失敗："+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
            }
        }
        finish();
        overridePendingTransition(0,0);
    }
}
