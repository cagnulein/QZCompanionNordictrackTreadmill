package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for QZMetricPacket: serialize, parse, round-trip, and wire-format identity.
 *
 * Pure-JVM — no Android dependencies.
 */
public class QZMetricPacketTest {

    // ── Metric enum: prefix and isInteger ────────────────────────────────────

    @Test
    public void metric_kph_hasCorrectPrefix() {
        assertEquals("Changed KPH ", QZMetricPacket.Metric.KPH.prefix);
    }

    @Test
    public void metric_kph_isNotInteger() {
        assertFalse(QZMetricPacket.Metric.KPH.isInteger);
    }

    @Test
    public void metric_watts_isInteger() {
        assertTrue(QZMetricPacket.Metric.WATTS.isInteger);
    }

    @Test
    public void metric_heartRate_isInteger() {
        assertTrue(QZMetricPacket.Metric.HEART_RATE.isInteger);
    }

    @Test
    public void metric_grade_isNotInteger() {
        assertFalse(QZMetricPacket.Metric.GRADE.isInteger);
    }

    @Test
    public void metric_rpm_isNotInteger() {
        assertFalse(QZMetricPacket.Metric.RPM.isInteger);
    }

    @Test
    public void metric_currentGear_isNotInteger() {
        assertFalse(QZMetricPacket.Metric.CURRENT_GEAR.isInteger);
    }

    @Test
    public void metric_resistance_isNotInteger() {
        assertFalse(QZMetricPacket.Metric.RESISTANCE.isInteger);
    }

    // ── serialize: float metrics ──────────────────────────────────────────────

    @Test
    public void serialize_kph_producesCorrectWireFormat() {
        assertEquals("Changed KPH 12.5", new QZMetricPacket(QZMetricPacket.Metric.KPH, 12.5f).serialize());
    }

    @Test
    public void serialize_grade_producesCorrectWireFormat() {
        assertEquals("Changed Grade 3.0", new QZMetricPacket(QZMetricPacket.Metric.GRADE, 3.0f).serialize());
    }

    @Test
    public void serialize_rpm_producesCorrectWireFormat() {
        assertEquals("Changed RPM 80.0", new QZMetricPacket(QZMetricPacket.Metric.RPM, 80.0f).serialize());
    }

    @Test
    public void serialize_currentGear_producesCorrectWireFormat() {
        assertEquals("Changed CurrentGear 4.0", new QZMetricPacket(QZMetricPacket.Metric.CURRENT_GEAR, 4.0f).serialize());
    }

    @Test
    public void serialize_resistance_producesCorrectWireFormat() {
        assertEquals("Changed Resistance 15.0", new QZMetricPacket(QZMetricPacket.Metric.RESISTANCE, 15.0f).serialize());
    }

    // ── serialize: integer metrics ────────────────────────────────────────────

    @Test
    public void serialize_watts_producesIntegerWireFormat() {
        assertEquals("Changed Watts 180", new QZMetricPacket(QZMetricPacket.Metric.WATTS, 180.7f).serialize());
    }

    @Test
    public void serialize_heartRate_producesIntegerWireFormat() {
        assertEquals("HeartRateDataUpdate 72", new QZMetricPacket(QZMetricPacket.Metric.HEART_RATE, 72.0f).serialize());
    }

    @Test
    public void serialize_watts_truncatesNotRounds() {
        // (int)180.9f = 180, not 181
        assertEquals("Changed Watts 180", new QZMetricPacket(QZMetricPacket.Metric.WATTS, 180.9f).serialize());
    }

    // ── parse: happy path ─────────────────────────────────────────────────────

    @Test
    public void parse_kph_returnsCorrectMetricAndValue() {
        QZMetricPacket pkt = QZMetricPacket.parse("Changed KPH 12.5");
        assertNotNull(pkt);
        assertEquals(QZMetricPacket.Metric.KPH, pkt.metric);
        assertEquals(12.5f, pkt.value, 0.001f);
    }

    @Test
    public void parse_watts_returnsCorrectMetricAndValue() {
        QZMetricPacket pkt = QZMetricPacket.parse("Changed Watts 180");
        assertNotNull(pkt);
        assertEquals(QZMetricPacket.Metric.WATTS, pkt.metric);
        assertEquals(180.0f, pkt.value, 0.001f);
    }

    @Test
    public void parse_heartRate_returnsCorrectMetricAndValue() {
        QZMetricPacket pkt = QZMetricPacket.parse("HeartRateDataUpdate 72");
        assertNotNull(pkt);
        assertEquals(QZMetricPacket.Metric.HEART_RATE, pkt.metric);
        assertEquals(72.0f, pkt.value, 0.001f);
    }

    @Test
    public void parse_grade_returnsCorrectMetricAndValue() {
        QZMetricPacket pkt = QZMetricPacket.parse("Changed Grade 5.0");
        assertNotNull(pkt);
        assertEquals(QZMetricPacket.Metric.GRADE, pkt.metric);
        assertEquals(5.0f, pkt.value, 0.001f);
    }

    // ── parse: null on unknown prefix ─────────────────────────────────────────

    @Test
    public void parse_unknownPrefix_returnsNull() {
        assertNull(QZMetricPacket.parse("SomeUnknownPrefix 42.0"));
    }

    @Test
    public void parse_emptyString_returnsNull() {
        assertNull(QZMetricPacket.parse(""));
    }

    @Test
    public void parse_prefixOnlyNoValue_throwsNumberFormatException() {
        // Prefix matches but value portion is empty — malformed packet, not an unknown metric.
        try {
            QZMetricPacket.parse("Changed KPH ");
            fail("Expected NumberFormatException for empty value after known prefix");
        } catch (NumberFormatException expected) {
            // correct
        }
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    public void roundTrip_kph_serializeThenParse_valuePreserved() {
        QZMetricPacket original = new QZMetricPacket(QZMetricPacket.Metric.KPH, 12.5f);
        QZMetricPacket parsed   = QZMetricPacket.parse(original.serialize());
        assertNotNull(parsed);
        assertEquals(original.metric, parsed.metric);
        assertEquals(original.value, parsed.value, 0.001f);
    }

    @Test
    public void roundTrip_watts_serializeThenParse_valueTruncated() {
        // 180.7 → "Changed Watts 180" → 180.0
        QZMetricPacket original = new QZMetricPacket(QZMetricPacket.Metric.WATTS, 180.7f);
        QZMetricPacket parsed   = QZMetricPacket.parse(original.serialize());
        assertNotNull(parsed);
        assertEquals(original.metric, parsed.metric);
        assertEquals(180.0f, parsed.value, 0.001f);
    }

    @Test
    public void roundTrip_allMetrics_allParseable() {
        for (QZMetricPacket.Metric m : QZMetricPacket.Metric.values()) {
            QZMetricPacket pkt    = new QZMetricPacket(m, 10.0f);
            QZMetricPacket parsed = QZMetricPacket.parse(pkt.serialize());
            assertNotNull("round-trip failed for " + m, parsed);
            assertEquals("metric mismatch for " + m, m, parsed.metric);
        }
    }

    // ── wire-format identity: mirrors legacy QZTelemetryUnicastingService ────

    @Test
    public void serialize_kph_matchesLegacyFloatConcatenation() {
        float v = 12.11f;
        assertEquals("Changed KPH " + v, new QZMetricPacket(QZMetricPacket.Metric.KPH, v).serialize());
    }

    @Test
    public void serialize_watts_matchesLegacyIntConcatenation() {
        Float v = 128.0f;
        assertEquals("Changed Watts " + v.intValue(), new QZMetricPacket(QZMetricPacket.Metric.WATTS, v).serialize());
    }

    @Test
    public void serialize_heartRate_matchesLegacyIntConcatenation() {
        Float v = 72.0f;
        assertEquals("HeartRateDataUpdate " + v.intValue(), new QZMetricPacket(QZMetricPacket.Metric.HEART_RATE, v).serialize());
    }
}
