package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.CadenceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.HeartRateTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.WattsTelemetry;

public final class QZTelemetryEncoder {
    private QZTelemetryEncoder() {}

    public static QZMetricPacket encode(Telemetry telemetry) {
        if (telemetry instanceof SpeedTelemetry)
            return new QZMetricPacket(QZMetricPacket.Metric.KPH, telemetry.value);
        if (telemetry instanceof InclineTelemetry)
            return new QZMetricPacket(QZMetricPacket.Metric.GRADE, telemetry.value);
        if (telemetry instanceof CadenceTelemetry)
            return new QZMetricPacket(QZMetricPacket.Metric.RPM, telemetry.value);
        if (telemetry instanceof GearTelemetry)
            return new QZMetricPacket(QZMetricPacket.Metric.CURRENT_GEAR, telemetry.value);
        if (telemetry instanceof ResistanceTelemetry)
            return new QZMetricPacket(QZMetricPacket.Metric.RESISTANCE, telemetry.value);
        if (telemetry instanceof WattsTelemetry)
            return new QZMetricPacket(QZMetricPacket.Metric.WATTS, telemetry.value);
        if (telemetry instanceof HeartRateTelemetry)
            return new QZMetricPacket(QZMetricPacket.Metric.HEART_RATE, telemetry.value);
        return null;
    }
}
