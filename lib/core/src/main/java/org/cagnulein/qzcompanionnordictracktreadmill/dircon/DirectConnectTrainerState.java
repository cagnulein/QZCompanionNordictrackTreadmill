package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.CadenceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.HeartRateTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.Telemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.WattsTelemetry;

public final class DirectConnectTrainerState {
    public Float watts;
    public Float cadenceRpm;
    public Float resistance;
    public Float gradePct;
    public Float speedKph;
    public Float heartRate;

    public void apply(Telemetry telemetry) {
        if (telemetry instanceof WattsTelemetry) watts = telemetry.value;
        else if (telemetry instanceof CadenceTelemetry) cadenceRpm = telemetry.value;
        else if (telemetry instanceof ResistanceTelemetry) resistance = telemetry.value;
        else if (telemetry instanceof InclineTelemetry) gradePct = telemetry.value;
        else if (telemetry instanceof SpeedTelemetry) speedKph = telemetry.value;
        else if (telemetry instanceof HeartRateTelemetry) heartRate = telemetry.value;
    }
}
