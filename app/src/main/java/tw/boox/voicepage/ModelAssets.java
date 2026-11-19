package tw.boox.voicepage;
import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
/** Android adapter: extracts the model packaged in the APK assets via {@link ModelStore}. */
public final class ModelAssets {
    private ModelAssets(){}
    public static File prepare(Context context,boolean rebuild) throws IOException {
        final AssetManager assets=context.getAssets();
        return ModelStore.prepare(new ModelStore.Source(){
            @Override public String[] list(String path) throws IOException {return assets.list(path);}
            @Override public InputStream open(String path) throws IOException {return assets.open(path);}
        },context.getFilesDir(),rebuild);
    }
}
