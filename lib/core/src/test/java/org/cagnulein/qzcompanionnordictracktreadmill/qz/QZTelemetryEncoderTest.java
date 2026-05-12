package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.CadenceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.GearTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.HeartRateTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.InclineTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.ResistanceTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.SpeedTelemetry;
import org.cagnulein.qzcompanionnordictracktreadmill.telemetry.WattsTelemetry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QZTelemetryEncoderTest {

    @Test
    public void encode_speed_emitsChangedKph() {
        assertEquals("Changed KPH 12.5",
                QZTelemetryEncoder.encode(new SpeedTelemetry(12.5f)).serialize());
    }

    @Test
    public void encode_incline_emitsChangedGrade() {
        assertEquals("Changed Grade 3.0",
                QZTelemetryEncoder.encode(new InclineTelemetry(3.0f)).serialize());
    }

    @Test
    public void encode_resistance_emitsChangedResistance() {
        assertEquals("Changed Resistance 15.0",
                QZTelemetryEncoder.encode(new ResistanceTelemetry(15.0f)).serialize());
    }

    @Test
    public void encode_gear_emitsChangedCurrentGear() {
        assertEquals("Changed CurrentGear 4.0",
                QZTelemetryEncoder.encode(new GearTelemetry(4.0f)).serialize());
    }

    @Test
    public void encode_cadence_emitsChangedRpm() {
        assertEquals("Changed RPM 80.0",
                QZTelemetryEncoder.encode(new CadenceTelemetry(80.0f)).serialize());
    }

    @Test
    public void encode_watts_truncatesToInt() {
        assertEquals("Changed Watts 180",
                QZTelemetryEncoder.encode(new WattsTelemetry(180.7f)).serialize());
    }

    @Test
    public void encode_heartRate_emitsHeartRateDataUpdate() {
        assertEquals("HeartRateDataUpdate 72",
                QZTelemetryEncoder.encode(new HeartRateTelemetry(72.0f)).serialize());
    }
}
