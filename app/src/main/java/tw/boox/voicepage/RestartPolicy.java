package tw.boox.voicepage;
/** Bounded exponential backoff for microphone errors. */
public final class RestartPolicy {
    private int failures;
    public void succeeded(){failures=0;}
    public int failures(){return failures;}
    public long failed(){
        failures=Math.min(12,failures+1);
        return Math.min(30000L,1200L*(1L<<Math.min(4,failures-1)));
    }
}
