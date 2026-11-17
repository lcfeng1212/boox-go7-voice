package tw.boox.voicepage;
import java.util.Locale;
/** Exact-match voice commands with a minimum interval between accepted page turns. */
public final class CommandEngine {
    public static final int NONE=0, NEXT=1, PREVIOUS=-1;
    private final long intervalMs;
    private long lastAt=Long.MIN_VALUE;
    public CommandEngine(long intervalMs) {
        if(intervalMs<0) throw new IllegalArgumentException("intervalMs");
        this.intervalMs=intervalMs;
    }
    public static String normalize(String input) {
        if(input==null) return "";
        return input.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{Punct}，。！？、：；『』「」（）]+","")
            .replace('页','頁');
    }
    public static int parse(String input) {
        String s=normalize(input);
        if(s.equals("下一頁")||s.equals("下頁")||s.equals("翻下一頁")||
           s.equals("小書下一頁")||s.equals("往下翻")) return NEXT;
        if(s.equals("上一頁")||s.equals("上頁")||s.equals("翻上一頁")||
           s.equals("小書上一頁")||s.equals("往上翻")) return PREVIOUS;
        return NONE;
    }
    public int accept(String input,long uptime) {
        int cmd=parse(input);
        if(cmd==NONE) return NONE;
        if(lastAt!=Long.MIN_VALUE && uptime>=lastAt && uptime-lastAt<intervalMs) return NONE;
        lastAt=uptime;
        return cmd;
    }
}
