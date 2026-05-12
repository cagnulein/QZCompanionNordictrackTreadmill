package org.cagnulein.qzcompanionnordictracktreadmill.command;

import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;

import java.util.List;

@FunctionalInterface
public interface CommandDecoder {
    List<Command> decode(QZCommandPacket packet);
}
