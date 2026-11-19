package tw.boox.voicepage;
/** Bounded exponential backoff for microphone errors, giving up after MAX_FAILURES in a row. */
public final class RestartPolicy {
    public static final int MAX_FAILURES=5;
    private int failures;
    public void succeeded(){failures=0;}
    public int failures(){return failures;}
    /** True once MAX_FAILURES consecutive failures happened without a successful start in between. */
    public boolean exhausted(){return failures>=MAX_FAILURES;}
    public long failed(){
        failures=Math.min(12,failures+1);
        return Math.min(30000L,1200L*(1L<<Math.min(4,failures-1)));
    }
}
