package org.cagnulein.qzcompanionnordictracktreadmill.qz;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for QZCommandPacket: structural parse, field-level parsing, and rounding.
 *
 * Protocol-level decode (decodeBike, decodeTreadmill) is tested in
 * BikeDeviceTest and TreadmillDeviceTest via decodeCommand().
 */
public class QZCommandPacketTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    @Test
    public void qzCommandPacket_delimiter_isSemicolon() {
        assertEquals(';', QZCommandPacket.DELIMITER);
    }

    @Test
    public void qzCommandPacket_endOfRide_isCorrectSentinel() {
        assertEquals("-1;-100", QZCommandPacket.END_OF_RIDE);
    }

    @Test
    public void qzCommandPacket_noCommand_isMinusOneHundred() {
        assertEquals(-100f, QZCommandPacket.NO_COMMAND, 0.001f);
    }

    @Test
    public void qzCommandPacket_noResistance_isMinusOne() {
        assertEquals(-1f, QZCommandPacket.NO_RESISTANCE, 0.001f);
    }

    // ── QZCommandPacket.parse ─────────────────────────────────────────────────

    @Test
    public void parse_onePart_fieldCountIsOne() {
        assertEquals(1, QZCommandPacket.parse("8.0").fieldCount());
    }

    @Test
    public void parse_twoParts_fieldCountIsTwo() {
        assertEquals(2, QZCommandPacket.parse("8.0;5.0").fieldCount());
    }

    @Test
    public void parse_rawField_returnsCorrectPart() {
        QZCommandPacket pkt = QZCommandPacket.parse("8.0;5.0");
        assertEquals("8.0", pkt.rawField(0));
        assertEquals("5.0", pkt.rawField(1));
    }

    @Test
    public void parse_raw_reconstructsOriginal() {
        assertEquals("8.0;5.0", QZCommandPacket.parse("8.0;5.0").raw());
    }

    @Test
    public void parse_endOfRide_rawMatchesSentinel() {
        assertEquals(QZCommandPacket.END_OF_RIDE, QZCommandPacket.parse("-1;-100").raw());
    }

    // ── QZCommandPacket.parseField ────────────────────────────────────────────

    @Test
    public void parseField_dotDecimal_parsesCorrectly() {
        assertEquals(8.5f, QZCommandPacket.parseField("8.5"), 0.001f);
    }

    @Test
    public void parseField_negativeValue_parsesCorrectly() {
        assertEquals(-3.0f, QZCommandPacket.parseField("-3.0"), 0.001f);
    }

    @Test
    public void parseField_leadingTrailingWhitespace_trimmedAndParsed() {
        assertEquals(8.5f, QZCommandPacket.parseField("  8.5  "), 0.001f);
    }

    @Test
    public void parseField_commaDecimal_fallbackReplacesCommaAndParses() {
        assertEquals(8.5f, QZCommandPacket.parseField("8,5"), 0.001f);
    }

    @Test
    public void parseField_nonNumericInput_returnsNull() {
        assertNull(QZCommandPacket.parseField("abc"));
    }

    @Test
    public void parseField_emptyString_returnsNull() {
        assertNull(QZCommandPacket.parseField(""));
    }

    @Test
    public void parseField_whitespaceOnly_returnsNull() {
        assertNull(QZCommandPacket.parseField("   "));
    }

    @Test
    public void parseField_sentinelMinusOne_parsesAsMinusOne() {
        assertEquals(-1.0f, QZCommandPacket.parseField("-1"), 0.001f);
    }

    // ── QZCommandPacket.roundToOneDecimal ─────────────────────────────────────

    @Test
    public void roundToOneDecimal_roundsUpAtHalfway() {
        assertEquals(8.3f, QZCommandPacket.roundToOneDecimal(8.25f), 0.001f);
    }

    @Test
    public void roundToOneDecimal_roundsDownBelowHalfway() {
        assertEquals(8.2f, QZCommandPacket.roundToOneDecimal(8.24f), 0.001f);
    }

    @Test
    public void roundToOneDecimal_negativeValue_roundsTowardPositiveInfinity() {
        assertEquals(-8.2f, QZCommandPacket.roundToOneDecimal(-8.25f), 0.001f);
    }

    @Test
    public void roundToOneDecimal_zero_returnsZero() {
        assertEquals(0.0f, QZCommandPacket.roundToOneDecimal(0.0f), 0.001f);
    }

    @Test
    public void roundToOneDecimal_alreadyOneDecimalPlace_unchanged() {
        assertEquals(5.5f, QZCommandPacket.roundToOneDecimal(5.5f), 0.001f);
    }

    @Test
    public void roundToOneDecimal_halfBoundaryPositive_roundsUp() {
        assertEquals(5.4f, QZCommandPacket.roundToOneDecimal(5.35f), 0.001f);
    }
}
