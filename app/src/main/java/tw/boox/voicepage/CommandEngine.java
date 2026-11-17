package tw.boox.voicepage;
import java.util.Locale;
/** Exact-match voice commands with a minimum interval between accepted page turns. */
public final class CommandEngine {
    public static final int NONE=0, NEXT=1, PREVIOUS=-1;
    /**
     * Vosk grammar phrases for vosk-model-small-cn-0.22. The model outputs Simplified Chinese
     * words separated by spaces; every token below exists in the model vocabulary (checked in CI
     * by scripts/check_grammar_vocab.py). "[unk]" absorbs any other speech.
     */
    public static final String[] GRAMMAR_PHRASES={
        "下一页","下 页","翻 下一页","往下 翻","小书 下一页","翻页",
        "上 一页","上页","翻 上 一页","往上 翻","小书 上 一页",
        "[unk]"
    };
    private final long intervalMs;
    private long lastAt=Long.MIN_VALUE;
    public CommandEngine(long intervalMs) {
        if(intervalMs<0) throw new IllegalArgumentException("intervalMs");
        this.intervalMs=intervalMs;
    }
    /** JSON array accepted by org.vosk.Recognizer(model, rate, grammar). */
    public static String grammarJson() {
        StringBuilder b=new StringBuilder("[");
        for(int i=0;i<GRAMMAR_PHRASES.length;i++){
            if(i>0)b.append(',');
            b.append('"').append(GRAMMAR_PHRASES[i]).append('"');
        }
        return b.append(']').toString();
    }
    public static String normalize(String input) {
        if(input==null) return "";
        return input.replace("[unk]","")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{Punct}，。！？、：；『』「」（）]+","")
            .replace('页','頁')
            .replace('书','書');
    }
    public static int parse(String input) {
        String s=normalize(input);
        switch(s){
            case "下一頁": case "下頁": case "翻下一頁": case "往下翻": case "小書下一頁": case "翻頁":
                return NEXT;
            case "上一頁": case "上頁": case "翻上一頁": case "往上翻": case "小書上一頁":
                return PREVIOUS;
            default:
                return NONE;
        }
    }
    public int accept(String input,long uptime) {
        int cmd=parse(input);
        if(cmd==NONE) return NONE;
        if(lastAt!=Long.MIN_VALUE && uptime>=lastAt && uptime-lastAt<intervalMs) return NONE;
        lastAt=uptime;
        return cmd;
    }
}
