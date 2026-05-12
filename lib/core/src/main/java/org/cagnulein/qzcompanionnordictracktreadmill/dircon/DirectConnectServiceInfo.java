package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DirectConnectServiceInfo {
    public static final String SERVICE_TYPE = "_wahoo-fitness-tnp._tcp.";
    public static final String DEFAULT_NAME = "Wahoo KICKR 0000";
    public static final int DEFAULT_PORT = 36866;

    public final String name;
    public final String type;
    public final int port;
    public final Map<String, String> txtRecords;

    public DirectConnectServiceInfo(String name, int port) {
        this.name = name;
        this.type = SERVICE_TYPE;
        this.port = port;
        LinkedHashMap<String, String> records = new LinkedHashMap<>();
        records.put("mac-address", "00:11:22:33:44");
        records.put("serial-number", "0");
        records.put("ble-service-uuids",
                "00001826-0000-1000-8000-00805F9B34FB,"
                        + "00001818-0000-1000-8000-00805F9B34FB,"
                        + "00001816-0000-1000-8000-00805F9B34FB");
        txtRecords = Collections.unmodifiableMap(records);
    }

    public static DirectConnectServiceInfo defaultInfo() {
        return new DirectConnectServiceInfo(DEFAULT_NAME, DEFAULT_PORT);
    }
}
