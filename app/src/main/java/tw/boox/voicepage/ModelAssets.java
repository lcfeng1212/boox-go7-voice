package tw.boox.voicepage;
import android.content.Context;
import android.content.res.AssetManager;
import java.io.*;
/** Safely copies packaged model assets to a private filesystem location for Vosk. */
public final class ModelAssets {
    private ModelAssets(){}
    public static File prepare(Context context) throws IOException {
        File folder=new File(context.getFilesDir(),"vosk-cn-v3");
        File marker=new File(folder,".complete");
        File model=new File(folder,"am/final.mdl");
        if(marker.isFile() && model.isFile())return folder;
        delete(folder);
        if(!folder.mkdirs()) throw new IOException("cannot create model folder");
        try {
            copy(context.getAssets(),"model-cn",folder);
            if(!model.isFile()||!new File(folder,"conf/model.conf").isFile())
                throw new IOException("model files missing");
            if(!marker.createNewFile())throw new IOException("cannot create marker");
            return folder;
        }catch(IOException ex){delete(folder);throw ex;}
    }
    private static void copy(AssetManager assets,String path,File destination) throws IOException {
        String[] children=assets.list(path);
        if(children!=null && children.length>0){
            if(!destination.exists()&&!destination.mkdirs())throw new IOException("mkdir failed");
            for(String child:children)copy(assets,path+"/"+child,new File(destination,child));
        }else{
            File parent=destination.getParentFile();
            if(parent==null||(!parent.exists()&&!parent.mkdirs()))throw new IOException("mkdir failed");
            try(InputStream in=assets.open(path);OutputStream out=new FileOutputStream(destination)){
                byte[] bytes=new byte[16384];int n;
                while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            }
        }
    }
    private static void delete(File path) throws IOException {
        if(!path.exists())return;
        if(path.isDirectory()){
            File[] children=path.listFiles();
            if(children!=null)for(File c:children)delete(c);
        }
        if(!path.delete())throw new IOException("cannot delete "+path.getName());
    }
}
