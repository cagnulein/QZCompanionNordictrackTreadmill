package org.cagnulein.qzcompanionnordictracktreadmill.command;

public final class SpeedCommand extends Command {
    public SpeedCommand(float speedKmh) { super(speedKmh); }

    @Override public String toString() { return "speed=" + value; }
}
