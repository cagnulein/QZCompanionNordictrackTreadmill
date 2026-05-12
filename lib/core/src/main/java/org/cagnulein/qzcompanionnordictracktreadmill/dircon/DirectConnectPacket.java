package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DirectConnectPacket {
    public static final int HEADER_LENGTH = 6;

    public static final int CHAR_READ = 0x01;
    public static final int CHAR_WRITE = 0x02;
    public static final int CHAR_NOTIFY = 0x04;
    public static final int CHAR_INDICATE = 0x08;

    public static final int MSG_ERROR = 0xFF;
    public static final int MSG_DISCOVER_SERVICES = 0x01;
    public static final int MSG_DISCOVER_CHARACTERISTICS = 0x02;
    public static final int MSG_READ_CHARACTERISTIC = 0x03;
    public static final int MSG_WRITE_CHARACTERISTIC = 0x04;
    public static final int MSG_ENABLE_NOTIFICATIONS = 0x05;
    public static final int MSG_NOTIFICATION = 0x06;
    public static final int MSG_UNKNOWN_0X07 = 0x07;

    public static final int RESP_SUCCESS = 0x00;
    public static final int RESP_UNKNOWN_MESSAGE_TYPE = 0x01;
    public static final int RESP_UNEXPECTED_ERROR = 0x02;
    public static final int RESP_SERVICE_NOT_FOUND = 0x03;
    public static final int RESP_CHARACTERISTIC_NOT_FOUND = 0x04;
    public static final int RESP_OPERATION_NOT_SUPPORTED = 0x05;
    public static final int RESP_WRITE_FAILED = 0x06;

    private static final int UUID_SHORT_OFFSET_HI = 2;
    private static final int UUID_SHORT_OFFSET_LO = 3;
    private static final byte[] BASE_UUID = new byte[] {
            0x00, 0x00, 0x18, 0x26, 0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00, 0x00, (byte) 0x80, 0x5F, (byte) 0x9B, 0x34, (byte) 0xFB
    };
    private static final byte[] ZWIFT_PLAY_UUID = new byte[] {
            0x00, 0x00, 0x00, 0x04, 0x19, (byte) 0xCA, 0x46, 0x51,
            (byte) 0x86, (byte) 0xE5, (byte) 0xFA, 0x29, (byte) 0xDC, (byte) 0xDD, 0x09, (byte) 0xD1
    };

    public int messageVersion = 1;
    public int identifier = MSG_ERROR;
    public int sequenceNumber = 0;
    public int responseCode = RESP_SUCCESS;
    public int length = 0;
    public int uuid = 0;
    public final List<Integer> uuids = new ArrayList<>();
    public byte[] additionalData = new byte[0];
    public boolean request = false;

    public static final class ParseResult {
        public static final int WAIT = -1;
        public static final int ERROR = -2;

        public final int status;
        public final int consumed;
        public final DirectConnectPacket packet;

        private ParseResult(int status, int consumed, DirectConnectPacket packet) {
            this.status = status;
            this.consumed = consumed;
            this.packet = packet;
        }

        public static ParseResult waitForMore() { return new ParseResult(WAIT, 0, null); }
        public static ParseResult error(int consumed, DirectConnectPacket packet) {
            return new ParseResult(ERROR, consumed, packet);
        }
        public static ParseResult ok(int consumed, DirectConnectPacket packet) {
            return new ParseResult(consumed, consumed, packet);
        }
    }

    public static ParseResult parse(byte[] buffer, int lastSequenceNumber) {
        if (buffer.length < HEADER_LENGTH) return ParseResult.waitForMore();

        DirectConnectPacket packet = new DirectConnectPacket();
        packet.messageVersion = u8(buffer[0]);
        packet.identifier = u8(buffer[1]);
        packet.sequenceNumber = u8(buffer[2]);
        packet.responseCode = u8(buffer[3]);
        packet.length = (u8(buffer[4]) << 8) | u8(buffer[5]);
        int consumed = HEADER_LENGTH + packet.length;
        if (buffer.length < consumed) return ParseResult.waitForMore();
        if (packet.responseCode != RESP_SUCCESS) return ParseResult.ok(consumed, packet);

        byte[] payload = Arrays.copyOfRange(buffer, HEADER_LENGTH, consumed);
        boolean valid = packet.parsePayload(payload, lastSequenceNumber);
        return valid ? ParseResult.ok(consumed, packet) : ParseResult.error(consumed, packet);
    }

    private boolean parsePayload(byte[] payload, int lastSequenceNumber) {
        if (identifier == MSG_DISCOVER_SERVICES) {
            if (length == 0) {
                request = checkIsRequest(lastSequenceNumber);
                return true;
            }
            if (length % 16 != 0) return false;
            for (int i = 0; i < payload.length; i += 16) uuids.add(shortUuid(payload, i));
            return true;
        }
        if (identifier == MSG_DISCOVER_CHARACTERISTICS) {
            if (length < 16) return false;
            uuid = shortUuid(payload, 0);
            if (length == 16) {
                request = checkIsRequest(lastSequenceNumber);
                return true;
            }
            if ((length - 16) % 17 != 0) return false;
            ByteArrayOutputStream properties = new ByteArrayOutputStream();
            for (int i = 16; i + 16 < payload.length; i += 17) {
                uuids.add(shortUuid(payload, i));
                properties.write(payload[i + 16]);
            }
            additionalData = properties.toByteArray();
            return true;
        }
        if (identifier == MSG_READ_CHARACTERISTIC) {
            if (length < 16) return false;
            uuid = shortUuid(payload, 0);
            if (length == 16) request = checkIsRequest(lastSequenceNumber);
            else additionalData = Arrays.copyOfRange(payload, 16, payload.length);
            return true;
        }
        if (identifier == MSG_WRITE_CHARACTERISTIC) {
            if (length <= 16) return false;
            uuid = shortUuid(payload, 0);
            additionalData = Arrays.copyOfRange(payload, 16, payload.length);
            request = checkIsRequest(lastSequenceNumber);
            return true;
        }
        if (identifier == MSG_ENABLE_NOTIFICATIONS) {
            if (length < 16) return false;
            uuid = shortUuid(payload, 0);
            if (length >= 17) {
                request = true;
                additionalData = new byte[] { payload[16] };
            }
            return true;
        }
        if (identifier == MSG_NOTIFICATION) {
            if (length <= 16) return false;
            uuid = shortUuid(payload, 0);
            additionalData = Arrays.copyOfRange(payload, 16, payload.length);
            return true;
        }
        if (identifier == MSG_UNKNOWN_0X07) {
            if (length != 0) return false;
            request = checkIsRequest(lastSequenceNumber);
            return true;
        }
        return false;
    }

    public byte[] encode(int lastSequenceNumber) {
        if (identifier == MSG_ERROR) return new byte[0];
        sequenceNumber = request ? (lastSequenceNumber & 0xFF)
                : identifier == MSG_NOTIFICATION ? 0
                : lastSequenceNumber & 0xFF;

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        if (!request && responseCode != RESP_SUCCESS) {
            return frame(new byte[0]);
        }
        if (identifier == MSG_DISCOVER_SERVICES) {
            if (!request) for (Integer u : uuids) writeUuid(payload, u);
        } else if (identifier == MSG_UNKNOWN_0X07) {
            return frame(new byte[0]);
        } else if (identifier == MSG_DISCOVER_CHARACTERISTICS && !request) {
            writeUuid(payload, uuid);
            for (int i = 0; i < uuids.size(); i++) {
                writeUuid(payload, uuids.get(i));
                payload.write(i < additionalData.length ? additionalData[i] : 0);
            }
        } else if (((identifier == MSG_READ_CHARACTERISTIC || identifier == MSG_DISCOVER_CHARACTERISTICS) && request)
                || (identifier == MSG_ENABLE_NOTIFICATIONS && !request)) {
            writeUuid(payload, uuid);
        } else if (identifier == MSG_WRITE_CHARACTERISTIC
                || identifier == MSG_NOTIFICATION
                || (identifier == MSG_READ_CHARACTERISTIC && !request)
                || (identifier == MSG_ENABLE_NOTIFICATIONS && request)) {
            writeUuid(payload, uuid);
            payload.write(additionalData, 0, additionalData.length);
        }
        return frame(payload.toByteArray());
    }

    private byte[] frame(byte[] payload) {
        length = payload.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(messageVersion);
        out.write(identifier);
        out.write(sequenceNumber);
        out.write(responseCode);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    private boolean checkIsRequest(int lastSequenceNumber) {
        return responseCode == RESP_SUCCESS
                && (lastSequenceNumber <= 0 || lastSequenceNumber != sequenceNumber);
    }

    private static int shortUuid(byte[] bytes, int offset) {
        return (u8(bytes[offset + UUID_SHORT_OFFSET_HI]) << 8)
                | u8(bytes[offset + UUID_SHORT_OFFSET_LO]);
    }

    private static void writeUuid(ByteArrayOutputStream out, int uuid) {
        byte[] bytes = (uuid >= 1 && uuid <= 4)
                ? Arrays.copyOf(ZWIFT_PLAY_UUID, ZWIFT_PLAY_UUID.length)
                : Arrays.copyOf(BASE_UUID, BASE_UUID.length);
        bytes[UUID_SHORT_OFFSET_HI] = (byte) ((uuid >> 8) & 0xFF);
        bytes[UUID_SHORT_OFFSET_LO] = (byte) (uuid & 0xFF);
        out.write(bytes, 0, bytes.length);
    }

    private static int u8(byte value) {
        return value & 0xFF;
    }
}
