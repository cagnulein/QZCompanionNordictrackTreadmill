package org.cagnulein.qzcompanionnordictracktreadmill.command;

public final class ResistanceCommand extends Command {
    public ResistanceCommand(float resistanceLvl) { super(resistanceLvl); }

    @Override public String toString() { return "resistance=" + value; }
}
