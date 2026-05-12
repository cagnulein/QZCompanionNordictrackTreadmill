package org.cagnulein.qzcompanionnordictracktreadmill.command;

public final class RawSwipeCommand extends Command {
    private final float x;
    private final float fromY;
    private final float toY;

    public RawSwipeCommand(float x, float fromY, float toY) {
        super(0);
        this.x = x;
        this.fromY = fromY;
        this.toY = toY;
    }

    public float x() { return x; }
    public float fromY() { return fromY; }
    public float toY() { return toY; }

    @Override
    public String toString() {
        return "rawSwipe(" + format(x) + "," + format(fromY) + "->" + format(toY) + ")";
    }

    private static String format(float value) {
        if (value == Math.rint(value)) return Integer.toString((int) value);
        return Float.toString(value);
    }
}
