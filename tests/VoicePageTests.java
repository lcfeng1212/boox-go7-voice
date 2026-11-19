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
        check(CommandEngine.parse("小书 上 一页")==-1);
        check(CommandEngine.parse("小书 下一页")==1);
        check(CommandEngine.parse("翻页")==1);
        check(CommandEngine.parse("[unk] 下一页")==1);
        check(CommandEngine.parse("[unk]")==0);
        for(String phrase:CommandEngine.GRAMMAR_PHRASES)
            check(phrase.equals("[unk]")||CommandEngine.parse(phrase)!=0);
        check(CommandEngine.grammarJson().startsWith("[\"下一页\",")&&CommandEngine.grammarJson().endsWith("\"[unk]\"]"));
        // Floating ball edge tucking (ball 105 px on a 1264 px wide screen).
        check(BallLayout.edgeFor(10,105,1264)==BallLayout.LEFT);
        check(BallLayout.edgeFor(1260,105,1264)==BallLayout.RIGHT);
        check(BallLayout.edgeFor(600,105,1264)==BallLayout.NONE);
        check(BallLayout.tuckedX(BallLayout.LEFT,105,1264)==0);
        check(BallLayout.tuckedX(BallLayout.RIGHT,105,1264)==1264-BallLayout.tuckedWidth(105));
        check(BallLayout.expandedX(BallLayout.RIGHT,105,1264,11)==1264-105-11);
        check(BallLayout.clamp(-5,0,100)==0&&BallLayout.clamp(500,0,100)==100&&BallLayout.clamp(50,0,100)==50);
        check(BallLayout.nearestSide(100,105,1264)==BallLayout.LEFT&&BallLayout.nearestSide(1000,105,1264)==BallLayout.RIGHT);
        // Swipe row moves away from a ball parked in the middle of the default row.
        float[] free=GestureGeometry.coordinates(1264,1680,true,null);
        check(free[1]==1680*.52f);
        float[] moved=GestureGeometry.coordinates(1264,1680,true,new int[]{580,820,685,925});
        check(moved[1]!=free[1]&&moved[1]==moved[3]);
        check(Math.abs(moved[1]-872)>105);
        float[] edgeBall=GestureGeometry.coordinates(1264,1680,true,new int[]{1148,820,1253,925});
        check(edgeBall[1]==free[1]);
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
