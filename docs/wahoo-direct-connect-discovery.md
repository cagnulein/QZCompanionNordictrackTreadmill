# Wahoo Direct Connect Discovery

QZ Companion can speak directly to Zwift over LAN by emulating the Wahoo Direct
Connect trainer protocol. This bypasses the previous chain:

```text
QZ Companion -> QZ app over UDP -> BLE FTMS -> Zwift
```

The working direct path is:

```text
QZ Companion -> mDNS + DIRCON TCP -> Zwift
```

This has been validated against Zwift with QZ Companion running on an S22i /
iFit2 bike console. Zwift discovered the trainer, paired Power Source, Cadence,
and Resistance, received live speed/cadence signal, and sent grade commands that
QZ Companion applied through the existing GlassOS incline path.

## Confirmed Zwift Behavior

- Zwift discovers LAN trainers through mDNS service type `_wahoo-fitness-tnp._tcp`.
- The service must advertise a TCP port; the Wahoo default is `36866`.
- Advertising as `Wahoo KICKR 0000` works and appears in Zwift with the LAN icon.
- Zwift opens a TCP session to port `36866` and performs a BLE/GATT-like service
  discovery over the Wahoo Trainer Network Protocol frame format.
- Zwift paired these tiles successfully:
  - Power Source
  - Cadence
  - Resistance, which is Zwift's bike controllable trainer path
- If measurement notifications are not sent continuously, Zwift initially shows
  Connected, then overlays No Signal and reconnects about every 30 seconds.
- A 1 Hz measurement heartbeat fixes the No Signal state.
- For control, Zwift writes FTMS control point packets to characteristic `0x2AD9`.
  Simulation grade writes decode to `InclineCommand` values that match Zwift's
  two-decimal grade display.

## Protocol Shape

The implementation is based on QZ's reverse-engineered DIRCON code, ported into
QZ Companion's Java core.

Network layer:

- Android `NsdManager` advertises `_wahoo-fitness-tnp._tcp.`.
- TCP server listens on `36866`.
- Only one active Zwift client is accepted at a time.
- Additional clients are rejected while the active session is open.

mDNS TXT records:

- `mac-address`
- `serial-number`
- `ble-service-uuids`

Advertised BLE services for the bike MVP:

- Fitness Machine Service `0x1826`
- Cycling Power Service `0x1818`
- Cycling Speed and Cadence Service `0x1816`

Characteristics currently exposed:

- `0x2ACC` Fitness Machine Feature, read
- `0x2AD6` Supported Resistance Level Range, read
- `0x2AD9` FTMS Control Point, write + indicate
- `0xE005` Wahoo control point, write
- `0x2AD2` Indoor Bike Data, notify
- `0x2AD3` Training Status, read
- `0x2A65` Cycling Power Feature, read
- `0x2A63` Cycling Power Measurement, notify
- `0x2A5C` CSC Feature, read
- `0x2A5B` CSC Measurement, notify
- `0x2A5D` Sensor Location, read

## QZ Companion Architecture

Direct Connect is an additional Zwift-facing adapter. It does not replace or
remove the existing QZ UDP bridge.

Key implementation pieces:

- `ZwiftDirectConnectService`
  - owns Android mDNS registration
  - owns TCP `36866`
  - manages the single Zwift client session
  - subscribes to `TelemetryHub`
  - sends 1 Hz measurement notifications
- `DirectConnectPacket`
  - Java port of the Wahoo DIRCON packet framing
  - handles service discovery, characteristic discovery, reads, writes,
    notification enablement, and unsolicited notifications
- `DirectConnectProfile`
  - defines the bike BLE service/characteristic profile exposed over DIRCON
  - encodes FTMS Indoor Bike Data, Cycling Power Measurement, and CSC Measurement
  - decodes FTMS control point writes into existing QZ Companion command objects
- `DirectConnectCommandBridge`
  - routes decoded control commands into the active `DeviceController`
- `DeviceController.enqueueCommand`
  - lets non-QZ ingress adapters reuse the existing dispatcher/throttle/device
    execution path

Telemetry flow:

```text
iFit telemetry reader
  -> TelemetryHub
  -> ZwiftDirectConnectService
  -> DIRCON notifications
  -> Zwift Power Source / Cadence
```

Control flow:

```text
Zwift Resistance tile
  -> DIRCON TCP write, usually FTMS 0x2AD9
  -> DirectConnectProfile
  -> InclineCommand or ResistanceCommand
  -> DeviceController
  -> iFit2 GlassOS gRPC or iFit1 gesture path
```

## Validated Test Results

Environment:

- QZ Companion debug build on S22i / iFit2 bike
- Zwift on LAN client `192.168.1.170`
- QZ Companion tablet `192.168.1.213`

Observed logs during successful startup:

```text
QZ:DirCon: Direct Connect service started
QZ:DirCon: TCP listening on 36866
QZ:DirCon: mDNS registered: Wahoo KICKR 0000
```

Observed successful Zwift pairing:

- Power Source connected
- Cadence connected
- Resistance connected
- Speed/cadence signal remained active after adding 1 Hz measurement heartbeat

Observed control path:

```text
QZ:DirCon: control: incline=0.12
QZ:Dispatch: enqueue: incline=0.12 depth=1/5
QZ:Dispatch: drain: incline=0.12
QZ:Dispatch: glassos applied: incline=0.12
```

The incline value matched Zwift's two-decimal grade display, so the FTMS
simulation-grade scaling is currently correct.

## Pairing Procedure

1. Enable `Zwift Direct Connect` from QZ Companion's overflow menu.
2. Confirm the status card reports advertising on port `36866`.
3. In Zwift pairing, select the LAN-icon `Wahoo KICKR 0000` entry for:
   - Power Source
   - Cadence
   - Resistance
4. If Zwift has cached an older failed session, unpair the old `Wahoo KICKR 0000`
   entries and pair again.

## Current Limitations

- The validated MVP is bike mode only.
- ERG target-power writes are acknowledged and remembered, but not yet mapped to
  hardware behavior.
- Virtual shifting / Zwift Play emulation is not implemented.
- Treadmill Direct Connect is not implemented.
- Wahoo DIRCON is not a public standard. The implementation follows behavior
  observed in Zwift plus QZ's reverse-engineered implementation.
- Android warns that TXT record keys longer than 9 characters are discouraged;
  these keys match QZ/Wahoo-style discovery and worked in validation.

## Useful Commands

Verify service state on the tablet:

```bash
adb -s <tablet>:5555 shell 'logcat -d -v threadtime | grep "QZ:DirCon"'
adb -s <tablet>:5555 shell netstat -an
```

Expected port state:

```text
tcp  0  0 :::36866  :::*  LISTEN
```

Force-enable the feature in debug builds:

```bash
adb -s <tablet>:5555 shell 'run-as org.cagnulein.qzcompanionnordictracktreadmill sh -c "printf '\''<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>\n<map>\n    <boolean name=\"directConnectEnabled\" value=\"true\" />\n</map>\n'\'' > shared_prefs/QZ.xml"'
adb -s <tablet>:5555 shell am force-stop org.cagnulein.qzcompanionnordictracktreadmill
adb -s <tablet>:5555 shell am start -n org.cagnulein.qzcompanionnordictracktreadmill/.ui.MainActivity
```
