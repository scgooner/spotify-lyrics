package dev.spotifylyrics;

/** Thread-safe "what's playing right now" state with smooth local position extrapolation. */
public final class Now {
    public static volatile String title = "";
    public static volatile String artist = "";
    public static volatile boolean playing = false;
    public static volatile double duration = 0;
    private static volatile double basePos = 0;
    private static volatile long baseNanos = System.nanoTime();

    private Now() {}

    public static String key() {
        return title.isEmpty() ? "" : artist + "|" + title;
    }

    public static double position() {
        return playing ? basePos + (System.nanoTime() - baseNanos) / 1e9 : basePos;
    }

    public static synchronized void update(boolean found, String t, String a, double pos, double dur, boolean pl) {
        if (!found || t == null || t.isEmpty()) {
            title = "";
            artist = "";
            playing = false;
            return;
        }
        boolean changed = !t.equals(title) || !a.equals(artist);
        double cur = position();
        if (changed || !playing || !pl || Math.abs(cur - pos) > 0.6) {
            basePos = pos;
            baseNanos = System.nanoTime();
        }
        title = t;
        artist = a;
        duration = dur;
        playing = pl;
        if (changed) LyricsManager.request(a, t, dur);
    }
}
