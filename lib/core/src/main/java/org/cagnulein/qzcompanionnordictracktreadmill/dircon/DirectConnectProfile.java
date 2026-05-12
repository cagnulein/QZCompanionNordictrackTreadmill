package org.cagnulein.qzcompanionnordictracktreadmill.dircon;

import org.cagnulein.qzcompanionnordictracktreadmill.command.Command;
import org.cagnulein.qzcompanionnordictracktreadmill.command.InclineCommand;
import org.cagnulein.qzcompanionnordictracktreadmill.command.ResistanceCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DirectConnectProfile {
    public static final int UUID_FITNESS_MACHINE = 0x1826;
    public static final int UUID_CYCLING_POWER = 0x1818;
    public static final int UUID_CSC = 0x1816;

    public static final int CHAR_FITNESS_MACHINE_FEATURE = 0x2ACC;
    public static final int CHAR_SUPPORTED_RESISTANCE_RANGE = 0x2AD6;
    public static final int CHAR_FTMS_CONTROL_POINT = 0x2AD9;
    public static final int CHAR_WAHOO_CONTROL_POINT = 0xE005;
    public static final int CHAR_INDOOR_BIKE_DATA = 0x2AD2;
    public static final int CHAR_TRAINING_STATUS = 0x2AD3;
    public static final int CHAR_CYCLING_POWER_FEATURE = 0x2A65;
    public static final int CHAR_SENSOR_LOCATION = 0x2A5D;
    public static final int CHAR_CYCLING_POWER_MEASUREMENT = 0x2A63;
    public static final int CHAR_CSC_FEATURE = 0x2A5C;
    public static final int CHAR_CSC_MEASUREMENT = 0x2A5B;

    private static final int FTMS_REQUEST_CONTROL = 0x00;
    private static final int FTMS_SET_TARGET_RESISTANCE_LEVEL = 0x04;
    private static final int FTMS_SET_TARGET_POWER = 0x05;
    private static final int FTMS_START_RESUME = 0x07;
    private static final int FTMS_STOP_PAUSE = 0x08;
    private static final int FTMS_SET_INDOOR_BIKE_SIMULATION_PARAMS = 0x11;
    private static final int FTMS_RESPONSE_CODE = 0x80;
    private static final int FTMS_SUCCESS = 0x01;
    private static final int FTMS_NOT_SUPPORTED = 0x02;

    private final List<Service> services = new ArrayList<>();
    private byte[] lastControlPointAnswer = new byte[0];
    private Float lastTargetPowerWatts = null;
    private double crankRevolutions = 0.0;
    private int crankEventTime = 0;
    private long lastMeasurementMs = 0;

    public DirectConnectProfile() {
        Service fitness = new Service(UUID_FITNESS_MACHINE);
        fitness.add(CHAR_FITNESS_MACHINE_FEATURE, DirectConnectPacket.CHAR_READ,
                bytes(0x83, 0x14, 0x00, 0x00, 0x0C, 0xE0, 0x00, 0x00));
        fitness.add(CHAR_SUPPORTED_RESISTANCE_RANGE, DirectConnectPacket.CHAR_READ,
                bytes(0x0A, 0x00, 0x96, 0x00, 0x0A, 0x00));
        fitness.add(CHAR_FTMS_CONTROL_POINT, DirectConnectPacket.CHAR_WRITE | DirectConnectPacket.CHAR_INDICATE,
                bytes(0x00));
        fitness.add(CHAR_WAHOO_CONTROL_POINT, DirectConnectPacket.CHAR_WRITE, bytes(0x00));
        fitness.add(CHAR_INDOOR_BIKE_DATA, DirectConnectPacket.CHAR_NOTIFY, bytes(0x00));
        fitness.add(CHAR_TRAINING_STATUS, DirectConnectPacket.CHAR_READ, bytes(0x00, 0x01));
        services.add(fitness);

        Service power = new Service(UUID_CYCLING_POWER);
        power.add(CHAR_CYCLING_POWER_FEATURE, DirectConnectPacket.CHAR_READ, bytes(0x08, 0x00, 0x00, 0x00));
        power.add(CHAR_SENSOR_LOCATION, DirectConnectPacket.CHAR_READ, bytes(0x0D));
        power.add(CHAR_CYCLING_POWER_MEASUREMENT, DirectConnectPacket.CHAR_NOTIFY, bytes(0x00));
        services.add(power);

        Service csc = new Service(UUID_CSC);
        csc.add(CHAR_CSC_FEATURE, DirectConnectPacket.CHAR_READ, bytes(0x02, 0x00));
        csc.add(CHAR_SENSOR_LOCATION, DirectConnectPacket.CHAR_READ, bytes(0x0D));
        csc.add(CHAR_CSC_MEASUREMENT, DirectConnectPacket.CHAR_NOTIFY, bytes(0x00));
        services.add(csc);
    }

    public Result process(DirectConnectPacket packet, Set<Integer> enabledNotifications) {
        DirectConnectPacket out = new DirectConnectPacket();
        out.request = false;
        out.identifier = packet.identifier;
        List<Command> commands = new ArrayList<>();

        if (!packet.request) return new Result(out, commands);

        if (packet.identifier == DirectConnectPacket.MSG_DISCOVER_SERVICES) {
            out.responseCode = DirectConnectPacket.RESP_SUCCESS;
            for (Service service : services) out.uuids.add(service.uuid);
        } else if (packet.identifier == DirectConnectPacket.MSG_DISCOVER_CHARACTERISTICS) {
            Service service = service(packet.uuid);
            if (service == null) {
                out.responseCode = DirectConnectPacket.RESP_SERVICE_NOT_FOUND;
            } else {
                out.responseCode = DirectConnectPacket.RESP_SUCCESS;
                out.uuid = packet.uuid;
                for (Characteristic characteristic : service.characteristics.values()) {
                    out.uuids.add(characteristic.uuid);
                    out.additionalData = append(out.additionalData, (byte) characteristic.properties);
                }
            }
        } else if (packet.identifier == DirectConnectPacket.MSG_READ_CHARACTERISTIC) {
            Characteristic characteristic = characteristic(packet.uuid);
            if (characteristic == null) {
                out.responseCode = DirectConnectPacket.RESP_CHARACTERISTIC_NOT_FOUND;
            } else if ((characteristic.properties & DirectConnectPacket.CHAR_READ) == 0) {
                out.responseCode = DirectConnectPacket.RESP_OPERATION_NOT_SUPPORTED;
            } else {
                out.responseCode = DirectConnectPacket.RESP_SUCCESS;
                out.uuid = packet.uuid;
                out.additionalData = characteristic.readValue;
            }
        } else if (packet.identifier == DirectConnectPacket.MSG_WRITE_CHARACTERISTIC) {
            Characteristic characteristic = characteristic(packet.uuid);
            if (characteristic == null) {
                out.responseCode = DirectConnectPacket.RESP_CHARACTERISTIC_NOT_FOUND;
            } else if ((characteristic.properties & DirectConnectPacket.CHAR_WRITE) == 0) {
                out.responseCode = DirectConnectPacket.RESP_OPERATION_NOT_SUPPORTED;
            } else {
                out.responseCode = DirectConnectPacket.RESP_SUCCESS;
                out.uuid = packet.uuid;
                commands.addAll(handleWrite(packet.uuid, packet.additionalData));
            }
        } else if (packet.identifier == DirectConnectPacket.MSG_ENABLE_NOTIFICATIONS) {
            Characteristic characteristic = characteristic(packet.uuid);
            if (characteristic == null) {
                out.responseCode = DirectConnectPacket.RESP_CHARACTERISTIC_NOT_FOUND;
            } else if ((characteristic.properties & (DirectConnectPacket.CHAR_NOTIFY | DirectConnectPacket.CHAR_INDICATE)) == 0) {
                out.responseCode = DirectConnectPacket.RESP_OPERATION_NOT_SUPPORTED;
            } else {
                out.responseCode = DirectConnectPacket.RESP_SUCCESS;
                out.uuid = packet.uuid;
                boolean enabled = packet.additionalData.length > 0 && packet.additionalData[0] != 0;
                if (enabled) enabledNotifications.add(packet.uuid);
                else enabledNotifications.remove(packet.uuid);
            }
        } else if (packet.identifier == DirectConnectPacket.MSG_UNKNOWN_0X07) {
            out.responseCode = DirectConnectPacket.RESP_SUCCESS;
        } else {
            out.responseCode = DirectConnectPacket.RESP_UNKNOWN_MESSAGE_TYPE;
        }
        return new Result(out, commands);
    }

    public byte[] notification(int uuid, byte[] data) {
        DirectConnectPacket packet = new DirectConnectPacket();
        packet.request = false;
        packet.identifier = DirectConnectPacket.MSG_NOTIFICATION;
        packet.responseCode = DirectConnectPacket.RESP_SUCCESS;
        packet.uuid = uuid;
        packet.additionalData = data;
        return packet.encode(0);
    }

    public byte[] indoorBikeData(DirectConnectTrainerState state) {
        int speed = Math.max(0, Math.round(value(state.speedKph) * 100f));
        int cadence = Math.max(0, Math.round(value(state.cadenceRpm)));
        int resistance = Math.max(0, Math.round(value(state.resistance)));
        int watts = Math.max(0, Math.round(value(state.watts)));
        int heart = Math.max(0, Math.round(value(state.heartRate)));
        return bytes(
                0x64, 0x02,
                speed & 0xFF, (speed >> 8) & 0xFF,
                cadence & 0xFF, (cadence >> 8) & 0xFF,
                resistance & 0xFF, (resistance >> 8) & 0xFF,
                watts & 0xFF, (watts >> 8) & 0xFF,
                heart & 0xFF, 0x00
        );
    }

    public byte[] cyclingPowerMeasurement(DirectConnectTrainerState state, long nowMs) {
        advanceCrank(state, nowMs);
        int watts = Math.max(0, Math.round(value(state.watts)));
        long crankCount = Math.round(crankRevolutions);
        long wheelCount = crankCount * 3L;
        int wheelEventTime = (crankEventTime * 2) & 0xFFFF;
        return bytes(
                0x30, 0x00,
                watts & 0xFF, (watts >> 8) & 0xFF,
                (int) (wheelCount & 0xFF), (int) ((wheelCount >> 8) & 0xFF),
                (int) ((wheelCount >> 16) & 0xFF), (int) ((wheelCount >> 24) & 0xFF),
                wheelEventTime & 0xFF, (wheelEventTime >> 8) & 0xFF,
                (int) (crankCount & 0xFF), (int) ((crankCount >> 8) & 0xFF),
                crankEventTime & 0xFF, (crankEventTime >> 8) & 0xFF
        );
    }

    public byte[] cscMeasurement(DirectConnectTrainerState state, long nowMs) {
        advanceCrank(state, nowMs);
        long crankCount = Math.round(crankRevolutions);
        return bytes(
                0x02,
                (int) (crankCount & 0xFF), (int) ((crankCount >> 8) & 0xFF),
                crankEventTime & 0xFF, (crankEventTime >> 8) & 0xFF
        );
    }

    public byte[] controlPointAnswer() {
        return lastControlPointAnswer;
    }

    public Float lastTargetPowerWatts() {
        return lastTargetPowerWatts;
    }

    private List<Command> handleWrite(int uuid, byte[] data) {
        List<Command> commands = new ArrayList<>();
        if (uuid != CHAR_FTMS_CONTROL_POINT && uuid != CHAR_WAHOO_CONTROL_POINT) return commands;
        if (data.length == 0) {
            lastControlPointAnswer = new byte[0];
            return commands;
        }
        int op = data[0] & 0xFF;
        int result = FTMS_SUCCESS;
        if (op == FTMS_SET_TARGET_RESISTANCE_LEVEL && data.length >= 2) {
            commands.add(new ResistanceCommand((data[1] & 0xFF) / 10.0f));
        } else if (op == FTMS_SET_INDOOR_BIKE_SIMULATION_PARAMS && data.length >= 5) {
            int rawGrade = (data[3] & 0xFF) | (data[4] << 8);
            commands.add(new InclineCommand(rawGrade / 100.0f));
        } else if (op == FTMS_SET_TARGET_POWER && data.length >= 3) {
            lastTargetPowerWatts = (float) ((data[1] & 0xFF) | ((data[2] & 0xFF) << 8));
            result = FTMS_SUCCESS;
        } else if (op == FTMS_REQUEST_CONTROL || op == FTMS_START_RESUME || op == FTMS_STOP_PAUSE) {
            result = FTMS_SUCCESS;
        } else {
            result = FTMS_NOT_SUPPORTED;
        }
        lastControlPointAnswer = bytes(FTMS_RESPONSE_CODE, op, result);
        return commands;
    }

    private Service service(int uuid) {
        for (Service service : services) if (service.uuid == uuid) return service;
        return null;
    }

    private Characteristic characteristic(int uuid) {
        for (Service service : services) {
            Characteristic characteristic = service.characteristics.get(uuid);
            if (characteristic != null) return characteristic;
        }
        return null;
    }

    private static float value(Float value) {
        return value == null ? 0f : value;
    }

    private void advanceCrank(DirectConnectTrainerState state, long nowMs) {
        if (lastMeasurementMs == 0) {
            lastMeasurementMs = nowMs;
            return;
        }
        long elapsedMs = Math.max(0, nowMs - lastMeasurementMs);
        lastMeasurementMs = nowMs;
        float cadence = Math.max(0f, value(state.cadenceRpm));
        crankRevolutions += cadence * elapsedMs / 60_000.0;
        crankEventTime = (crankEventTime + (int) Math.round(elapsedMs * 1024.0 / 1000.0)) & 0xFFFF;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static byte[] append(byte[] data, byte value) {
        byte[] out = Arrays.copyOf(data, data.length + 1);
        out[out.length - 1] = value;
        return out;
    }

    public static final class Result {
        public final DirectConnectPacket response;
        public final List<Command> commands;

        private Result(DirectConnectPacket response, List<Command> commands) {
            this.response = response;
            this.commands = commands;
        }
    }

    private static final class Service {
        final int uuid;
        final Map<Integer, Characteristic> characteristics = new LinkedHashMap<>();

        Service(int uuid) { this.uuid = uuid; }

        void add(int uuid, int properties, byte[] readValue) {
            characteristics.put(uuid, new Characteristic(uuid, properties, readValue));
        }
    }

    private static final class Characteristic {
        final int uuid;
        final int properties;
        final byte[] readValue;

        Characteristic(int uuid, int properties, byte[] readValue) {
            this.uuid = uuid;
            this.properties = properties;
            this.readValue = readValue;
        }
    }
}
