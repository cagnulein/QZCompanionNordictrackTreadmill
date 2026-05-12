package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectConnectServiceInfoTest {
    @Test
    public void defaultInfo_matchesWahooDiscoveryShape() {
        DirectConnectServiceInfo info = DirectConnectServiceInfo.defaultInfo();

        assertEquals("_wahoo-fitness-tnp._tcp.", info.type);
        assertEquals(36866, info.port);
        assertEquals("Wahoo KICKR 0000", info.name);
        assertTrue(info.txtRecords.containsKey("ble-service-uuids"));
    }
}
