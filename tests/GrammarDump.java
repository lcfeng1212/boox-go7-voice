import tw.boox.voicepage.CommandEngine;
/** Prints Vosk grammar phrases, one per line, for scripts/check_grammar_vocab.py. */
public final class GrammarDump {
    public static void main(String[] args){
        for(String phrase:CommandEngine.GRAMMAR_PHRASES)System.out.println(phrase);
    }
}
