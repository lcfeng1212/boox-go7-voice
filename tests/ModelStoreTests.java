import tw.boox.voicepage.ModelStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
/** Reproduces the model-extraction problems found in the v0.5.0 review and checks they are fixed. */
public final class ModelStoreTests {
    private static int n;
    static void check(boolean value,String what){n++;if(!value)throw new AssertionError("model test "+n+": "+what);}

    /** In-memory model: folder -> children, file -> bytes. Optional delay per opened file. */
    static final class FakeSource implements ModelStore.Source {
        final Map<String,byte[]> files=new TreeMap<>();
        volatile long delayMs;
        FakeSource(){
            files.put("model-cn/am/final.mdl",new byte[50000]);
            files.put("model-cn/conf/model.conf","--min-active=200\n".getBytes());
            files.put("model-cn/conf/mfcc.conf","--sample-frequency=16000\n".getBytes());
            files.put("model-cn/graph/Gr.fst",new byte[30000]);
            files.put("model-cn/graph/HCLr.fst",new byte[20000]);
            files.put("model-cn/graph/phones/word_boundary.int","1 nonword\n".getBytes());
            files.put("model-cn/ivector/final.ie",new byte[9000]);
        }
        @Override public String[] list(String path){
            if(files.containsKey(path))return new String[0];
            Set<String> children=new TreeSet<>();
            for(String f:files.keySet())if(f.startsWith(path+"/"))children.add(f.substring(path.length()+1).split("/")[0]);
            return children.toArray(new String[0]);
        }
        @Override public InputStream open(String path) throws IOException {
            if(delayMs>0)try{Thread.sleep(delayMs);}catch(InterruptedException e){throw new InterruptedIOException();}
            byte[] b=files.get(path);
            if(b==null)throw new FileNotFoundException(path);
            return new ByteArrayInputStream(b);
        }
    }

    public static void main(String[] args) throws Exception {
        File base=Files.createTempDirectory("modelstore").toFile();
        FakeSource source=new FakeSource();

        // Fresh extraction produces a verified model and removes the old 0.5.0 folder.
        File legacy=new File(base,"vosk-cn-v3/am");legacy.mkdirs();
        File folder=ModelStore.prepare(source,base,false);
        check(new File(folder,"conf/model.conf").isFile()&&new File(folder,"ivector/final.ie").length()==9000,"extracted");
        check(!new File(base,"vosk-cn-v3").exists(),"legacy folder removed");
        check(!new File(base,"vosk-cn-v4.tmp").exists(),"temp folder cleaned");

        // Missing file (report: conf/model.conf removed) is detected and restored.
        new File(folder,"conf/model.conf").delete();
        ModelStore.prepare(source,base,false);
        check(new File(folder,"conf/model.conf").isFile(),"missing file restored");

        // Truncated file is detected and restored.
        try(FileOutputStream out=new FileOutputStream(new File(folder,"graph/Gr.fst"))){out.write(1);}
        ModelStore.prepare(source,base,false);
        check(new File(folder,"graph/Gr.fst").length()==30000,"truncated file restored");

        // Report scenario: an old job and a new job overlap (quick stop then start during the first
        // extraction). Neither may delete the other's finished model.
        ModelStore.delete(folder);
        source.delayMs=40;
        // Daemon threads so a failing assertion cannot leave the test JVM hanging.
        ExecutorService pool=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
        List<Future<File>> jobs=new ArrayList<>();
        jobs.add(pool.submit(()->ModelStore.prepare(source,base,false)));
        Thread.sleep(60);
        jobs.add(pool.submit(()->ModelStore.prepare(source,base,false)));
        jobs.add(pool.submit(()->ModelStore.prepare(source,base,true)));
        for(Future<File> job:jobs)check(job.get(30,TimeUnit.SECONDS).equals(folder),"job finished");
        pool.shutdown();
        source.delayMs=0;
        check(new File(folder,"am/final.mdl").length()==50000&&new File(folder,"conf/model.conf").isFile(),
            "model intact after overlapping jobs");
        check(!new File(base,"vosk-cn-v4.tmp").exists(),"no temp folder left");

        // A failing extraction leaves no half-written model behind and keeps nothing marked complete.
        ModelStore.delete(folder);
        source.files.remove("model-cn/conf/model.conf");
        boolean failed=false;
        try{ModelStore.prepare(source,base,false);}catch(IOException expected){failed=true;}
        check(failed&&!folder.exists()&&!new File(base,"vosk-cn-v4.tmp").exists(),"failed extraction cleaned up");

        ModelStore.delete(base);
        System.out.println("PASS "+n+" model store checks");
    }
}
