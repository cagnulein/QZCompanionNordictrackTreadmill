package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectConnectPacketTest {
    @Test
    public void discoverServicesRoundTrip() {
        DirectConnectPacket request = new DirectConnectPacket();
        request.request = true;
        request.identifier = DirectConnectPacket.MSG_DISCOVER_SERVICES;

        byte[] encoded = request.encode(7);
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(encoded.length, result.consumed);
        assertTrue(result.packet.request);
        assertEquals(DirectConnectPacket.MSG_DISCOVER_SERVICES, result.packet.identifier);
    }

    @Test
    public void notificationEncodesCharacteristicPayload() {
        DirectConnectProfile profile = new DirectConnectProfile();

        byte[] encoded = profile.notification(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA,
                new byte[] { 0x64, 0x02 });
        DirectConnectPacket.ParseResult result = DirectConnectPacket.parse(encoded, 0);

        assertEquals(DirectConnectPacket.MSG_NOTIFICATION, result.packet.identifier);
        assertEquals(DirectConnectProfile.CHAR_INDOOR_BIKE_DATA, result.packet.uuid);
        assertArrayEquals(new byte[] { 0x64, 0x02 }, result.packet.additionalData);
    }
}
