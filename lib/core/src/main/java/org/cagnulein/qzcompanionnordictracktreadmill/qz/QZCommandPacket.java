package org.cagnulein.qzcompanionnordictracktreadmill.qz;

public class QZCommandPacket {
    public static final char   DELIMITER      = ';';
    public static final float  NO_COMMAND     = -100f;
    public static final float  NO_RESISTANCE  = -1f;
    public static final String END_OF_RIDE    = "-1" + DELIMITER + "-100";
    public static final String CALSWIPE_PREFIX = "CALSWIPE:";
    public static final String SNAP_ORIGIN     = "SNAP_ORIGIN";

    private final String[] fields;

    private QZCommandPacket(String[] fields) {
        this.fields = fields;
    }

    public static QZCommandPacket parse(String raw) {
        return new QZCommandPacket(raw.split(String.valueOf(DELIMITER)));
    }

    public int fieldCount() { return fields.length; }

    public String rawField(int i) { return fields[i]; }

    public String raw() {
        return String.join(String.valueOf(DELIMITER), fields);
    }

    public static Float parseField(String part) {
        String s = part.trim();
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            try { return Float.parseFloat(s.replace(',', '.')); }
            catch (NumberFormatException e2) { return null; }
        }
    }

    public static float roundToOneDecimal(float value) {
        return Math.round(value * 10) / 10.0f;
    }
}
