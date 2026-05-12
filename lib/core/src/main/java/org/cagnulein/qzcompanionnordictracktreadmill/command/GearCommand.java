package org.cagnulein.qzcompanionnordictracktreadmill.command;

public final class GearCommand extends Command {
    public GearCommand(float gear) { super(gear); }

    @Override public String toString() { return "gear=" + value; }
}
