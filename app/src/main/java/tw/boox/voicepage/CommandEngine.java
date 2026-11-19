package tw.boox.voicepage;
import java.util.Locale;
/**
 * Voice commands with a minimum interval between accepted actions.
 * <ul>
 * <li>Fast commands (下一頁, 上一頁, 左滑, 右滑) may fire on a partial result, before the pause
 *     at the end of speech.</li>
 * <li>Strict commands (下, 上, 回到首頁, 關閉) fire only on a final result that contains nothing
 *     but the command, because single characters and leave-the-app commands are easy to hear
 *     inside ordinary speech.</li>
 * </ul>
 * In complete-command mode (optional) every command waits for the final result and is rejected
 * when the utterance also contains unknown speech, trading a little speed for fewer false turns.
 */
public final class CommandEngine {
    public static final int NONE=0, NEXT=1, PREVIOUS=-1, HOME=2;
    /**
     * Vosk grammar phrases for vosk-model-small-cn-0.22. The model outputs Simplified Chinese
     * words separated by spaces; every token must exist in the model vocabulary (checked in CI
     * by scripts/check_grammar_vocab.py). "[unk]" absorbs any other speech.
     */
    public static final String[] GRAMMAR_PHRASES={
        "下一页","上 一页","下","上","左 滑","右 滑","回到 首页","关闭",
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
    /** Removes [unk], spaces and punctuation and folds Traditional characters to Simplified. */
    public static String normalize(String input) {
        if(input==null) return "";
        return input.replace("[unk]","")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{Punct}，。！？、：；『』「」（）]+","")
            .replace('頁','页').replace('關','关').replace('閉','闭');
    }
    /**
     * Normalized text between leading and trailing unknown speech, or null when unknown speech
     * sits inside the phrase ("左 [unk] 滑" is not 左滑).
     */
    static String core(String input){
        if(input==null)return "";
        String[] t=input.trim().split("\\s+");
        int a=0,b=t.length;
        while(a<b&&t[a].equals("[unk]"))a++;
        while(b>a&&t[b-1].equals("[unk]"))b--;
        StringBuilder kept=new StringBuilder();
        for(int i=a;i<b;i++){
            if(t[i].equals("[unk]"))return null;
            kept.append(t[i]).append(' ');
        }
        return normalize(kept.toString());
    }
    private static int lookup(String normalized){
        switch(normalized){
            case "下一页": case "左滑": case "下": return NEXT;
            case "上一页": case "右滑": case "上": return PREVIOUS;
            case "回到首页": case "关闭": return HOME;
            default: return NONE;
        }
    }
    /** True for commands that must be the whole utterance and wait for the final result. */
    public static boolean isStrict(String normalized){
        return normalized.length()==1||lookup(normalized)==HOME;
    }
    /** Command in a final (end-of-speech) result. */
    public static int parseFinal(String input){return parseFinal(input,false);}
    /** @param complete complete-command mode: reject any utterance that also has unknown speech. */
    public static int parseFinal(String input,boolean complete){
        String s=core(input);
        if(s==null)return NONE;
        int cmd=lookup(s);
        if(cmd!=NONE&&(complete||isStrict(s))&&input.contains("[unk]"))return NONE;
        return cmd;
    }
    /** Command in a partial (still speaking) result; strict commands never fire here. */
    public static int parsePartial(String input){
        String s=core(input);
        if(s==null)return NONE;
        return isStrict(s)?NONE:lookup(s);
    }
    public int accept(String input,long uptime,boolean partial) {return accept(input,uptime,partial,false);}
    public int accept(String input,long uptime,boolean partial,boolean complete) {
        int cmd=partial?(complete?NONE:parsePartial(input)):parseFinal(input,complete);
        if(cmd==NONE) return NONE;
        if(lastAt!=Long.MIN_VALUE && uptime>=lastAt && uptime-lastAt<intervalMs) return NONE;
        lastAt=uptime;
        return cmd;
    }
}
