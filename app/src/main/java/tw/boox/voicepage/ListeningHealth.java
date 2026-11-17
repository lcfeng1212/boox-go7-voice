package tw.boox.voicepage;
/** Detects recognition callback stalls using monotonic time. */
public final class ListeningHealth {
    private long lastCallback;
    public void started(long ms){lastCallback=ms;}
    public void heartbeat(long ms){lastCallback=ms;}
    public boolean stale(long now,long timeout){return now>=lastCallback && now-lastCallback>timeout;}
}
