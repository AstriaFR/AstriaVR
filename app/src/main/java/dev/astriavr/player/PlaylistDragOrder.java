package dev.astriavr.player;

/** One moving slot over a windowed cursor; no full-playlist copy while dragging. */
public final class PlaylistDragOrder {
    private PlaylistDragOrder() {}
    public static int originalPosition(int visiblePosition, int from, int to) {
        if (from < 0 || to < 0 || from == to) return visiblePosition;
        if (visiblePosition == to) return from;
        if (from < to && visiblePosition >= from && visiblePosition < to) return visiblePosition + 1;
        if (from > to && visiblePosition > to && visiblePosition <= from) return visiblePosition - 1;
        return visiblePosition;
    }
}
