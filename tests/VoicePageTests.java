import tw.boox.voicepage.*;
public final class VoicePageTests {
    private static int n;
    static void check(boolean value){n++;if(!value)throw new AssertionError("test "+n);}
    public static void main(String[] args){
        check(CommandEngine.parse("下一頁")==1);
        check(CommandEngine.parse("上一頁")==-1);
        check(CommandEngine.parse("下 一 页")==1);
        check(CommandEngine.parse("上 一 页")==-1);
        check(CommandEngine.parse("翻下一頁")==1);
        check(CommandEngine.parse("往上翻")==-1);
        check(CommandEngine.parse("他說下一頁")==0);
        check(CommandEngine.parse("下一頁下一頁")==0);
        check(CommandEngine.parse("")==0);
        check(CommandEngine.parse(null)==0);
        CommandEngine c=new CommandEngine(900);
        check(c.accept("下一頁",1000)==1);
        check(c.accept("下一頁",1001)==0);
        check(c.accept("上一頁",1899)==0);
        check(c.accept("上一頁",1900)==-1);
        float[] a=GestureGeometry.coordinates(1080,1440,true);
        check(a[0]>a[2]);check(a[1]==a[3]);
        float[] b=GestureGeometry.coordinates(1080,1440,false);
        check(b[0]<b[2]);
        ListeningHealth health=new ListeningHealth();
        health.started(100);
        check(!health.stale(35100,35000));
        check(health.stale(35101,35000));
        RestartPolicy policy=new RestartPolicy();
        check(policy.failed()==1200);
        check(policy.failed()==2400);
        policy.succeeded();check(policy.failed()==1200);
        System.out.println("PASS "+n+" Java checks");
    }
}
