package org.cagnulein.qzcompanionnordictracktreadmill.qz;

/**
 * Represents a single outbound metric update sent to the QZ app via UDP (port 8002).
 *
 * Wire format is byte-for-byte identical to what QZTelemetryUnicastingService
 * previously produced via raw string concatenation:
 *
 *   Float metrics: prefix + Float.toString(value)   e.g. "Changed KPH 12.5"
 *   Int   metrics: prefix + (int)value              e.g. "Changed Watts 180"
 *
 * No Android dependencies — safe for pure-JVM unit tests.
 */
public class QZMetricPacket {

    public enum Metric {
        KPH         ("Changed KPH ",         false),
        GRADE       ("Changed Grade ",        false),
        RPM         ("Changed RPM ",          false),
        CURRENT_GEAR("Changed CurrentGear ",  false),
        RESISTANCE  ("Changed Resistance ",   false),
        WATTS       ("Changed Watts ",        true),
        HEART_RATE  ("HeartRateDataUpdate ",  true);

        public final String  prefix;
        public final boolean isInteger;

        Metric(String prefix, boolean isInteger) {
            this.prefix    = prefix;
            this.isInteger = isInteger;
        }

        /** Returns the Metric whose prefix matches the start of {@code raw}, or null. */
        public static Metric fromRaw(String raw) {
            for (Metric m : values()) {
                if (raw.startsWith(m.prefix)) return m;
            }
            return null;
        }
    }

    public final Metric metric;
    public final float  value;

    public QZMetricPacket(Metric metric, float value) {
        this.metric = metric;
        this.value  = value;
    }

    /**
     * Serializes to the wire-format string expected by the QZ app.
     *
     * Uses Java's default Float.toString() (same as string concatenation), matching
     * the legacy sendIfChanged / sendIfChangedInt output byte-for-byte.
     */
    public String serialize() {
        return metric.isInteger ? metric.prefix + (int) value
                                : metric.prefix + value;
    }

    /**
     * Parses a wire-format string into a QZMetricPacket.
     *
     * @return the parsed packet, or null if the prefix is unrecognized
     * @throws NumberFormatException if the prefix matches but the value cannot be parsed
     */
    public static QZMetricPacket parse(String raw) {
        Metric m = Metric.fromRaw(raw);
        if (m == null) return null;
        return new QZMetricPacket(m, Float.parseFloat(raw.substring(m.prefix.length()).trim()));
    }
}
