package net.falsetrue.heapwalker.util;

public class TimeManager {
    private long from;
    private long pauseTime = System.currentTimeMillis();
    private volatile boolean paused = true;

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
