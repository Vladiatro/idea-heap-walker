package net.falsetrue.heapwalker.util;

public class TimeManager {
    public static final int BLACK_AGE = 90000;

    private long from;
    private long pauseTime;
    private boolean paused = true;

    public void start() {
        from = System.currentTimeMillis();
        paused = false;
    }

    public long getTime() {
        if (paused) {
            return pauseTime - from;
        }
        return System.currentTimeMillis() - from;
    }

    public void pause() {
        pauseTime = System.currentTimeMillis();
        paused = true;
    }

    public void resume() {
        from += System.currentTimeMillis() - pauseTime;
        paused = false;
    }

    public boolean isPaused() {
        return paused;
    }
}
