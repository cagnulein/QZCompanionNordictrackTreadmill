package org.cagnulein.qzcompanionnordictracktreadmill.device.ifit2;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2.CommandTransport;
import org.cagnulein.qzcompanionnordictracktreadmill.qz.QZCommandPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GrpcBikeDevice extends GrpcDevice {

    public GrpcBikeDevice(CommandTransport transport) { super(transport); }

    @Override public String displayName() { return "iFit2 Bike"; }

    @Override
    public List<Command> decodeCommands(QZCommandPacket pkt) {
        if (pkt.fieldCount() == 2) {
            if (QZCommandPacket.END_OF_RIDE.equals(pkt.raw())) return Collections.emptyList();
            List<Command> cmds = new ArrayList<>();
            Float i = QZCommandPacket.parseField(pkt.rawField(0));
            Float r = QZCommandPacket.parseField(pkt.rawField(1));
            if (i != null && i != QZCommandPacket.NO_COMMAND)
                cmds.add(new InclineCommand(QZCommandPacket.roundToOneDecimal(i)));
            if (r != null && r != QZCommandPacket.NO_RESISTANCE && r != QZCommandPacket.NO_COMMAND)
                cmds.add(new ResistanceCommand(QZCommandPacket.roundToOneDecimal(r)));
            return cmds;
        }
        if (pkt.fieldCount() == 1) {
            Float v = QZCommandPacket.parseField(pkt.rawField(0));
            if (v == null || v == QZCommandPacket.NO_RESISTANCE || v == QZCommandPacket.NO_COMMAND)
                return Collections.emptyList();
            return Collections.singletonList(
                    new ResistanceCommand(QZCommandPacket.roundToOneDecimal(v)));
        }
        return Collections.emptyList();
    }
}
