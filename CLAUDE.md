# QZ Companion NordicTrack Treadmill - Claude Documentation

## Project Structure
Android app for controlling NordicTrack and ProForm fitness devices via ADB/shell commands.

### Main Files
- `app/src/main/java/org/cagnulein/qzcompanionnordictracktreadmill/UDPListenerService.java` - Core UDP listener and device management
- `app/src/main/java/org/cagnulein/qzcompanionnordictracktreadmill/MainActivity.java` - Main UI and device selection handling
- `app/src/main/res/layout/activity_main.xml` - UI layout with radio buttons for device selection
- `app/build.gradle` - Android build configuration
- `app/src/main/AndroidManifest.xml` - Android manifest
- `.github/workflows/main.yml` - GitHub Actions CI/CD

## S22i Device Implementation

### S22i Standard Pattern
S22i devices are bike devices that control resistance/inclination via simulated touch coordinates.

#### 1. Device Enum (UDPListenerService.java:45-86)
```java
public enum _device {
    s22i,                    // Standard S22i
    s22i_NTEX02121_5,       // Existing variant
    s22i_NTEX02117_2,       // New device added
}
```

#### 2. Coordinate Configuration (UDPListenerService.java:139-155)
```java
case s22i:
    lastReqResistance = 0;
    y1Resistance = 618;     // Base Y coordinate
    break;
case s22i_NTEX02117_2:
    lastReqResistance = 0;
    y1Resistance = 618;     // Same coordinates as standard s22i
    break;
```

#### 3. Resistance Control Calculation (UDPListenerService.java:303-314)
```java
} else if (device == _device.s22i) {
    x1 = 75;
    y2 = (int) (616.18 - (17.223 * reqResistance));
} else if (device == _device.s22i_NTEX02117_2) {
    x1 = 75;
    y2 = (int) (616.18 - (17.223 * reqResistance)); // Identical formula
}
```

#### 4. UI Selection (activity_main.xml:196-200)
```xml
<RadioButton
    android:id="@+id/s22i_NTEX02117_2"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="S22i Bike (NTEX02117.2)" />
```

#### 5. Selection Handling (MainActivity.java:320-321)
```java
} else if(i == R.id.s22i_NTEX02117_2) {
    UDPListenerService.setDevice(UDPListenerService._device.s22i_NTEX02117_2);
```

#### 6. Bike Device Conditions (UDPListenerService.java:277, 358)
Add `|| device == _device.s22i_NTEX02117_2` to existing conditions.

### Key Difference: Command Execution

#### MainActivity.sendCommand() (Default for S22i)
- Uses ADB connection (`connection.queueCommand()`)
- Requires active ADB connection
- Pattern for most devices

#### shellRuntime.exec() (For S22i_NTEX02117_2)
- Direct shell execution via `Runtime.getRuntime()`
- Does not require ADB connection
- Pattern used by x22i and x14i

```java
String command = "input swipe " + x1 + " " + y1Resistance + " " + x1 + " " + y2 + " 200";
if (device == _device.s22i_NTEX02117_2) {
    shellRuntime.exec(command);
} else {
    MainActivity.sendCommand(command);
}
```

## Version Management

### Files to Update for Each Release
**ALWAYS update all 3 files for each release:**

1. **app/build.gradle**
   - `versionCode` (increment +1): `171 → 172`
   - `versionName` (semantic versioning): `"3.6.19" → "3.6.20"`

2. **AndroidManifest.xml**
   - `android:versionCode`: `"171" → "172"`
   - `android:versionName`: `"3.6.19" → "3.6.20"`

3. **.github/workflows/main.yml**
   - `tag_name`: `3.6.19 → 3.6.20`

### Version Bump Process
```bash
# 1. Patch release (bug fix)
3.6.19 → 3.6.20

# 2. Minor release (new feature)
3.6.19 → 3.7.0

# 3. Major release (breaking change)
3.6.19 → 4.0.0
```

## Device Naming Convention
- Pattern: `{series}{model}_{variant}` (e.g. `s22i_NTEX02117_2`)
- UI Text: `"{Series} Bike ({Model})"` (e.g. `"S22i Bike (NTEX02117.2)"`)
- NO strings.xml usage, text hardcoded directly in layout

## S22i Coordinates and Formulas
### S22i Standard and NTEX02117_2
- X: `75`
- Base Y: `618`
- Y Formula: `(int) (616.18 - (17.223 * reqResistance))`

### S22i NTEX02121_5 (Special Variant)
- X: `75`
- Base Y: `535`
- Dynamic Y formula based on current inclination

## New Device Implementation Pattern

### 1. Standard Device (ADB)
1. Add enum in `UDPListenerService._device`
2. Configure coordinates in switch case
3. Add control calculation
4. Add radio button UI
5. Add selection handling
6. Add to bike/treadmill conditions

### 2. Shell Device (Non-ADB)
Follow pattern above + modify command execution:
```java
if (device == _device.{new_device}) {
    shellRuntime.exec(command);
} else {
    MainActivity.sendCommand(command);
}
```

## OCR Pattern Recognition

### Supported OCR Patterns (QZService.java:getOCR())

#### Speed Patterns
1. **Standard Speed:** `"speed"` - Direct km/h value
2. **500m Split Time:** `"500 split"` or `"/500m"` - Converts seconds to km/h
   - Formula: `km/h = 1800 / seconds`
   - Example: 41 seconds → 43.9 km/h

#### Cadence Patterns
1. **Standard:** `"cadence"` or `"rpm"`
2. **Rowing:** `"strokes per min"` - Added for rowing machine support

#### Other Patterns
- **Incline:** `"incline"`
- **Resistance:** `"resistance"`
- **Watts:** `"watt"`

### Code Style Guidelines
- All comments must be in English
- Use descriptive variable names
- Follow existing indentation patterns

## Latest Implementation
**Feature:** OCR pattern support for rowing machines  
**Version:** 3.6.20 (versionCode 172)  
**Date:** 2025-08-07  
**Changes:** Added "STROKES PER MIN" cadence pattern and "500 SPLIT (/500M)" speed conversion