package dev.astriavr.player;

/** D-pad key/HAT deduplication, with repeat for left/right only. Retain held bits on menu close. */
public final class PlaylistNavigation {
    public static final int LEFT = 1, RIGHT = 2, CONFIRM = 4;
    private int keys, hat, repeatDirection;
    private long repeatAt;
    public boolean isNeutral() { return (keys | hat) == 0; }
    public int key(int action, boolean down, boolean repeated, long now) {
        int before = keys | hat;
        if (down) keys |= action; else keys &= ~action;
        updateRepeat(now);
        return down && !repeated ? fresh((keys | hat) & ~before) : 0;
    }
    public int hat(float x, float y, long now) {
        int before = keys | hat;
        hat = (x < -.5f ? LEFT : x > .5f ? RIGHT : 0) | (y < -.5f ? CONFIRM : 0);
        updateRepeat(now);
        return fresh((keys | hat) & ~before);
    }
    private int fresh(int mask) {
        if ((mask & CONFIRM) != 0) return CONFIRM;
        return ((keys | hat) & (LEFT | RIGHT)) == (LEFT | RIGHT) ? 0 : mask;
    }
    private void updateRepeat(long now) {
        int direction = (keys | hat) & (LEFT | RIGHT);
        if (direction == (LEFT | RIGHT)) direction = 0;
        if (repeatDirection != direction) { repeatDirection = direction; repeatAt = now + 350; }
    }
    public int repeat(long now) {
        if (repeatDirection == 0 || now < repeatAt) return 0;
        repeatAt = now + 180;
        return repeatDirection;
    }
    public boolean hasRepeat() { return repeatDirection != 0; }
}
