package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;

import java.util.function.Consumer;

public final class DirectConnectCommandBridge {
    private static volatile Consumer<Command> sink = null;

    private DirectConnectCommandBridge() {}

    public static void setSink(Consumer<Command> nextSink) {
        sink = nextSink;
    }

    public static boolean submit(Command command) {
        Consumer<Command> current = sink;
        if (current == null) return false;
        current.accept(command);
        return true;
    }
}
