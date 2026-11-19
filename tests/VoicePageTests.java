import tw.boox.voicepage.*;
public final class VoicePageTests {
    private static int n;
    static void check(boolean value){n++;if(!value)throw new AssertionError("test "+n);}
    public static void main(String[] args){
        // Fast commands: fire on partial and final results.
        check(CommandEngine.parsePartial("下一页")==1&&CommandEngine.parseFinal("下一頁")==1);
        check(CommandEngine.parsePartial("上 一页")==-1&&CommandEngine.parseFinal("上一頁")==-1);
        check(CommandEngine.parsePartial("左 滑")==1&&CommandEngine.parseFinal("左滑")==1);
        check(CommandEngine.parsePartial("右 滑")==-1&&CommandEngine.parseFinal("右滑")==-1);
        check(CommandEngine.parseFinal("[unk] 下一页")==1);
        // Strict commands: final result only, and nothing else in the utterance.
        check(CommandEngine.parsePartial("下")==0&&CommandEngine.parseFinal("下")==1);
        check(CommandEngine.parsePartial("上")==0&&CommandEngine.parseFinal("上")==-1);
        check(CommandEngine.parseFinal("[unk] 下")==0&&CommandEngine.parseFinal("上 [unk]")==0);
        check(CommandEngine.parsePartial("回到 首页")==0&&CommandEngine.parseFinal("回到 首页")==2);
        check(CommandEngine.parseFinal("回到首頁")==2&&CommandEngine.parseFinal("關閉")==2&&CommandEngine.parseFinal("关闭")==2);
        check(CommandEngine.parsePartial("关闭")==0&&CommandEngine.parseFinal("[unk] 关闭")==0);
        // Unknown speech may surround a command but not sit inside it.
        check(CommandEngine.parseFinal("左 [unk] 滑")==0&&CommandEngine.parsePartial("左 [unk] 滑")==0);
        check(CommandEngine.parseFinal("上 [unk] 一页")==0&&CommandEngine.parseFinal("下一 [unk] 页")==0);
        check(CommandEngine.parseFinal("[unk] 上 一页 [unk]")==-1&&CommandEngine.parsePartial("[unk] [unk] 右 滑")==-1);
        check(CommandEngine.parseFinal("回到 [unk] 首页")==0);
        // Complete-command mode: every command waits for the final result and rejects unknown speech.
        check(CommandEngine.parseFinal("[unk] 下一页",true)==0&&CommandEngine.parseFinal("下一页 [unk]",true)==0);
        check(CommandEngine.parseFinal("下一页",true)==1&&CommandEngine.parseFinal("上",true)==-1&&CommandEngine.parseFinal("关闭",true)==2);
        CommandEngine strict=new CommandEngine(900);
        check(strict.accept("下一页",1000,true,true)==0&&strict.accept("下一页",1001,false,true)==1);
        // Removed or never-commands.
        check(CommandEngine.parseFinal("翻页")==0&&CommandEngine.parseFinal("往下翻")==0&&CommandEngine.parseFinal("下页")==0);
        check(CommandEngine.parseFinal("他說下一頁")==0&&CommandEngine.parseFinal("下一頁下一頁")==0);
        check(CommandEngine.parseFinal("")==0&&CommandEngine.parseFinal("[unk]")==0&&CommandEngine.parsePartial(null)==0);
        for(String phrase:CommandEngine.GRAMMAR_PHRASES)
            check(phrase.equals("[unk]")||CommandEngine.parseFinal(phrase)!=0);
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
        check(c.accept("下一頁",1000,true)==1);
        check(c.accept("下一頁",1001,false)==0);
        check(c.accept("上一頁",1899,true)==0);
        check(c.accept("上一頁",1900,true)==-1);
        check(c.accept("下",5000,true)==0&&c.accept("下",5001,false)==1);
        // Every page-turn mode moves away from a ball sitting on its default path.
        float[] tap=GestureGeometry.plan(1264,1680,GestureGeometry.TAP_SIDES,true,null);
        check(tap[0]==tap[2]&&tap[1]==tap[3]&&tap[0]>632&&tap[4]<100);
        float[] tapPrev=GestureGeometry.plan(1264,1680,GestureGeometry.TAP_SIDES,false,null);
        check(tapPrev[0]<632);
        float[] tapAvoid=GestureGeometry.plan(1264,1680,GestureGeometry.TAP_SIDES,true,
            new int[]{(int)tap[0]-50,(int)tap[1]-50,(int)tap[0]+50,(int)tap[1]+50});
        check(tapAvoid[1]!=tap[1]);
        float[] up=GestureGeometry.plan(1264,1680,GestureGeometry.SWIPE_VERTICAL,true,null);
        check(up[0]==up[2]&&up[1]>up[3]);
        float[] down=GestureGeometry.plan(1264,1680,GestureGeometry.SWIPE_VERTICAL,false,null);
        check(down[1]<down[3]);
        float[] upAvoid=GestureGeometry.plan(1264,1680,GestureGeometry.SWIPE_VERTICAL,true,new int[]{580,700,685,805});
        check(upAvoid[0]!=up[0]);
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
        for(int i=1;i<RestartPolicy.MAX_FAILURES;i++)policy.failed();
        check(policy.exhausted());
        policy.succeeded();check(!policy.exhausted());
        System.out.println("PASS "+n+" Java checks");
    }
}
