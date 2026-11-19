package tw.boox.voicepage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
/**
 * Extracts the packaged Vosk model to private storage. Free of Android types so it is unit-tested.
 * <ul>
 * <li>Only one extraction runs at a time (a quick stop/start cannot delete another job's files).</li>
 * <li>Files are written to a temporary folder and moved into place only after verification.</li>
 * <li>A manifest of every file and its size is checked on each start, so a missing or truncated
 *     file triggers a fresh extraction instead of a broken model.</li>
 * </ul>
 */
public final class ModelStore {
    /** Read access to the packaged model (AssetManager on Android, a fake in tests). */
    public interface Source {
        /** Children of a folder, or an empty array for a file. */
        String[] list(String path) throws IOException;
        InputStream open(String path) throws IOException;
    }
    static final String DIR="vosk-cn-v4", ROOT="model-cn", MANIFEST=".manifest";
    private static final String[] LEGACY_DIRS={"vosk-cn-v3"};
    private static final String[] REQUIRED={"am/final.mdl","conf/model.conf","graph/Gr.fst","graph/HCLr.fst"};
    private static final Object LOCK=new Object();
    private ModelStore(){}

    /**
     * @param rebuild extract again even if the current copy verifies (used after a load failure).
     * @return folder to pass to org.vosk.Model
     */
    public static File prepare(Source source,File base,boolean rebuild) throws IOException {
        synchronized(LOCK){
            File folder=new File(base,DIR);
            if(!rebuild&&verify(folder))return folder;
            File tmp=new File(base,DIR+".tmp");
            delete(tmp);
            try{
                List<String> files=new ArrayList<>();
                copy(source,ROOT,tmp,"",files);
                writeManifest(tmp,files);
                if(!verify(tmp))throw new IOException("model verification failed");
                delete(folder);
                if(!tmp.renameTo(folder))throw new IOException("cannot move model into place");
            }catch(IOException ex){
                try{delete(tmp);}catch(IOException ignored){}
                throw ex;
            }
            for(String old:LEGACY_DIRS){
                try{delete(new File(base,old));}catch(IOException ignored){}
            }
            return folder;
        }
    }

    /** True when the manifest exists, every listed file has its recorded size and required files exist. */
    static boolean verify(File folder){
        File manifest=new File(folder,MANIFEST);
        if(!manifest.isFile())return false;
        int count=0;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(manifest),"UTF-8"))){
            String line;
            while((line=in.readLine())!=null){
                if(line.isEmpty())continue;
                int tab=line.lastIndexOf('\t');
                if(tab<=0)return false;
                File f=new File(folder,line.substring(0,tab));
                if(!f.isFile()||f.length()!=Long.parseLong(line.substring(tab+1)))return false;
                count++;
            }
        }catch(IOException|NumberFormatException ex){return false;}
        if(count==0)return false;
        for(String required:REQUIRED)if(!new File(folder,required).isFile())return false;
        return true;
    }

    private static void copy(Source source,String path,File destination,String relative,List<String> files)
            throws IOException {
        String[] children=source.list(path);
        if(children!=null&&children.length>0){
            if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("mkdir failed");
            for(String child:children)
                copy(source,path+"/"+child,new File(destination,child),
                    relative.isEmpty()?child:relative+"/"+child,files);
        }else{
            File parent=destination.getParentFile();
            if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("mkdir failed");
            try(InputStream in=source.open(path);OutputStream out=new FileOutputStream(destination)){
                byte[] bytes=new byte[65536];int n;
                while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            }
            files.add(relative);
        }
    }
    private static void writeManifest(File folder,List<String> files) throws IOException {
        try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(folder,MANIFEST)),"UTF-8")){
            for(String f:files)out.write(f+"\t"+new File(folder,f).length()+"\n");
        }
    }
    public static void delete(File path) throws IOException {
        if(!path.exists())return;
        if(path.isDirectory()){
            File[] children=path.listFiles();
            if(children!=null)for(File c:children)delete(c);
        }
        if(!path.delete())throw new IOException("cannot delete "+path.getName());
    }
}
