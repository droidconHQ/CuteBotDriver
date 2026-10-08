# Cutebot Bluetooth API & Starter Firmware

Welcome to the droidcon / fluttercon Robot Workshop starter repository! This repository contains the official micro:bit V2 firmware and native client examples (Kotlin and Dart) to get your mobile application communicating with the Elecfreaks Cutebot over Bluetooth Low Energy (BLE).

This setup allows you to send text commands from your smartphone app to control the robot's motors, headlights, underglow, and buzzer, as well as read real-time sensor data back.

*NOTE* if you are doing this at a droidCon/FlutterCon event the robots will already be flashed with the correct Micro:bit Firmware, skip this step.

---

## Hardware Requirements

To run this project, you will need:
* **BBC micro:bit V2** (The main microcontroller board)
* **Elecfreaks Cutebot Smart Car** (The robot chassis)
* **Ultrasonic Distance Sensor** (Plugged into the front of the Cutebot)
* **3x AAA Batteries** or a rechargeable lithium pack for power

---

## Micro:bit Firmware Setup

If you need to re-flash or inspect the robot's firmware, follow these steps:

1. Open the [Microsoft MakeCode Editor](https://makecode.microbit.org/).
2. Create a new project and click on **Advanced** -> **Extensions**.
3. Search for and add the official **`cutebot`** extension.
4. Search for and add the official **`neopixel`** extension.
5. Search for and add the official **`bluetooth`** extension. When prompted, accept the removal of the incompatible **`radio`** extension.
6. Switch the editor from Blocks to **JavaScript / TypeScript** mode.
7. Copy the code from `microbitapi.js` in this repo and paste it into the editor.
8. Go to **Project Settings** (the gear icon) and ensure that **"No Pairing Required: Anyone can connect via Bluetooth"** is enabled.
9. Click **Download** to flash the `.hex` file onto your micro:bit via USB.

When the firmware boots successfully or connects to a phone, the micro:bit LED matrix will display a **Happy Face**. If the Bluetooth connection drops, the firmware triggers a **safety auto-stop** (motors halt, lights off, buzzer quieted, and a Sad Face is displayed) to prevent runaway robot crashes.

---

## Bluetooth UART API Specification

The micro:bit hosts a standard Nordic UART Service. Commands are sent from the mobile app as plain text strings written to the **RX Characteristic** (`0003`). Every command must be terminated by a hash character (`#`).

Responses from the robot are broadcast via the **TX Characteristic** (`0002`) notification channel and are terminated with a hash and newline (`#\n`).

### 1. Movement Commands

| Command | Description | Example Payload |
| :--- | :--- | :--- |
| `F` or `F,[speed]` | Drive forward (speed optional: 1 to 100, default 50) | `F#` or `F,80#` |
| `B` or `B,[speed]` | Drive backward (speed optional: 1 to 100, default 50) | `B#` or `B,70#` |
| `L` or `L,[speed]` | Spin left in place (speed optional, default 50) | `L#` or `L,40#` |
| `R` or `R,[speed]` | Spin right in place (speed optional, default 50) | `R#` or `R,40#` |
| `S` | Stop both motors immediately | `S#` |
| `ML,[speed]` | Set Left Motor speed (-100 to 100) | `ML,75#` |
| `MR,[speed]` | Set Right Motor speed (-100 to 100) | `MR,-40#` |
| `MS,[left-speed],[right-speed]` | Set both Motor speeds (-100 to 100) | `MS,-40,40#` |

### 2. Lighting Commands

Headlights and underglow accept raw integer values for Red, Green, and Blue channels ranging from `0` to `255`.

| Command | Description | Example Payload |
| :--- | :--- | :--- |
| `HL,[R],[G],[B]` | Set both front headlights (L & R) | `HL,255,0,0#` (Solid Red) |
| `HLL,[R],[G],[B]` | Set front left headlight only | `HLL,255,165,0#` (Amber turn signal) |
| `HLR,[R],[G],[B]` | Set front right headlight only | `HLR,255,165,0#` (Amber turn signal) |
| `HO` | Turn off all headlights and underglow | `HO#` |
| `UG,[R],[G],[B]` | Set both bottom underglow NeoPixels | `UG,0,255,0#` (Solid Green) |
| `UGL,[R],[G],[B]` | Set left underglow NeoPixel only (pixel 0) | `UGL,0,0,255#` (Blue) |
| `UGR,[R],[G],[B]` | Set right underglow NeoPixel only (pixel 1) | `UGR,255,0,255#` (Magenta) |
| `UGO` | Turn off underglow NeoPixels only | `UGO#` |

### 3. Audio & Buzzer Commands

Cutebot has an onboard buzzer connected via Pin 0 (and micro:bit V2 internal speaker).

| Command | Description | Example Payload |
| :--- | :--- | :--- |
| `HORN` | Play quick cheerful car horn (440Hz for 200ms) | `HORN#` |
| `BEEP` | Play short alert beep (880Hz for 100ms) | `BEEP#` |
| `TONE,[freq],[durationMs]` | Play custom frequency (Hz) for duration (ms) | `TONE,523,250#` |
| `QUIET` or `MUTE` | Stop all active audio immediately | `QUIET#` |

### 4. micro:bit 5x5 LED Display Commands

| Command | Description | Example Payload |
| :--- | :--- | :--- |
| `DISP,[text]` | Scroll text across the 5x5 LED matrix | `DISP,CAR 1#` |
| `ICON,[name]` | Display built-in icon (`HAPPY`, `SAD`, `HEART`, `YES`, `NO`, `SKULL`) | `ICON,HEART#` |
| `CLS` | Clear the 5x5 LED screen | `CLS#` |

### 5. Sensor Feedback & Telemetry (Two-Way Requests)

Send any query string to the RX characteristic (`#` terminated); the micro:bit processes the request and responds asynchronously via the TX Characteristic notification channel terminated by `#\n`.

| Query | Description | Response Format | Example Response |
| :--- | :--- | :--- | :--- |
| `?DIST#` | Ultrasonic distance sensor in centimeters | `DIST:[cm]#\n` | `DIST:42#\n` |
| `?LINE#` | Line tracker status (`0`=white, `1`=R black, `2`=L black, `3`=both black) | `LINE:[code]#\n` | `LINE:3#\n` |
| `?COMPASS#` | Magnetometer / Compass heading (0° - 359°)* | `COMPASS:[deg]#\n` | `COMPASS:180#\n` |
| `?ACCEL#` | 3-axis accelerometer (X, Y, Z mg forces) | `ACCEL:[x],[y],[z]#\n` | `ACCEL:0,25,1020#\n` |
| `?LIGHT#` | Ambient light sensor level (0 - 255) | `LIGHT:[level]#\n` | `LIGHT:128#\n` |
| `?TEMP#` | Onboard temperature sensor (°C) | `TEMP:[celsius]#\n` | `TEMP:24#\n` |
| `PING#` | Connectivity / latency verification | `PONG#\n` | `PONG#\n` |

*\* Note: If the micro:bit compass has not yet been calibrated, the first `?COMPASS#` query will trigger the one-time `TILT TO FILL SCREEN` calibration routine. However, due to vertical mounting and DC motor proximity, compass readings on the Cutebot are unreliable. See [Calibrating the micro:bit Compass](#calibrating-the-microbit-compass) below for details.*

---

## Calibrating the micro:bit Compass

> [!WARNING]
> ### 🧭 Compass Reliability Warning: Vertical Mounting & Motor Interference
> **Compass readings (`?COMPASS`) are unreliable and noisy while the micro:bit is plugged into the Cutebot.**
> 1. **Vertical Mounting (Gimbal Lock):** The micro:bit runtime's built-in `compassHeading()` algorithm is mathematically designed for a board lying horizontally (flat on a table). When slotted vertically into the Cutebot chassis, the board's pitch is ~90°, which triggers an Euler angle mathematical singularity (gimbal lock in the tilt-compensation math). This causes readings to jump erratically, freeze, or drift with tiny tilts.
> 2. **DC Motor Magnetic Interference:** The Cutebot's two brushed DC drive motors sit directly beneath the micro:bit connector. Their strong permanent magnets distort the local magnetic field far beyond Earth's natural geomagnetic field.
>
> **Recommended Alternative:** For straight-line navigation or autonomous assist, we strongly recommend using the bottom infrared line-tracking sensors (`?LINE`) or balancing differential motor speeds (`MS,left,right`) rather than relying on the compass.

The BBC micro:bit V2 includes an onboard magnetometer (compass). To provide accurate heading readings (0°–359°), the compass must be calibrated before its first use or after flashing new firmware.

If the robot has not been calibrated, sending the `?COMPASS#` telemetry request will automatically initiate the calibration routine on the micro:bit.

For official documentation, see the [micro:bit Compass Calibration Guide](https://support.microbit.org/support/solutions/articles/19000008874-calibrating-the-micro-bit-compass).

### What Happens During Calibration?
When `?COMPASS#` (or `input.compassHeading()`) is called for the first time:
1. The micro:bit pauses normal operation and scrolls:
   > **`TILT TO FILL SCREEN`**
2. A single lit pixel will appear on the 5x5 LED matrix, acting like a rolling ball or bubble level.

### Step-by-Step Calibration Instructions
1. **Pick up the robot:** Hold the Cutebot car horizontally with both hands.
2. **Tilt in all directions:** Tilt the Cutebot smoothly in circles and all directions to guide the dot across the 5x5 LED matrix. Each unlit LED reached by the dot will illuminate and stay lit.
3. **Fill the screen:** Continue tilting until all 25 LEDs on the matrix are filled.
4. **Completion:** Once every pixel is lit, the screen will clear or display a smile, and the robot will resume normal operation and respond with the heading value (`COMPASS:[deg]#\n`).

> **Tip:** You do not have to wait for the words **`TILT TO FILL SCREEN`** to finish scrolling. The calibration routine starts in the background immediately, so you can begin tilting the robot right away to complete the process faster.

### Calibration Tips & Best Practices
* **Calibrate on the Cutebot with Accessories Connected:** Always calibrate with the micro:bit plugged into the Cutebot chassis, with the battery pack switched on and the ultrasonic sensor connected. Surrounding chassis electronics and motors generate localized magnetic fields that the calibration routine must detect and compensate for.
* **Calibrate in Your Operating Environment:** Magnetic environments vary by room and location. Calibrate the robot in the same room or arena where you plan to drive it.
* **Avoid Metal Surfaces:** Do not calibrate or operate the robot on metal desks or near large metal structures (such as steel beams or speaker magnets), as metal will distort the magnetometer readings.
* **Horizontal Orientation:** The compass is designed for horizontal orientation (like an analog compass). On the micro:bit V2, the magnetometer is sensitive, so tilt smoothly.
* **Calibration Memory & Persistence:** When flashed with MakeCode, calibration data is saved in persistent memory and remains saved across reboots and battery power cycles. Calibration is only cleared when a new firmware `.hex` file is flashed to the micro:bit.

---

## Client Integration Examples

Look inside the repository and client application directories for boilerplate helper classes that implement this exact API:

* **`AndroidApi.kt`**: Contains a standard Android BLE wrapper leveraging `BluetoothGatt` with typed `CutebotTelemetry` models.
* **`FlutterApi.dart`**: Contains a cross-platform Flutter implementation utilizing `flutter_blue_plus` with a typed `CutebotTelemetry` stream.
* **`IosApi.swift`**: Contains a pure Swift / `CoreBluetooth` implementation for iOS with `@Published` telemetry and zero third-party dependencies.
* **`ReactNativeApi.ts`**: Contains a TypeScript implementation for React Native utilizing `react-native-ble-plx`.

### Sample Applications
* **`CutebotAndroidSample`**: Complete Jetpack Compose native Android controller app.
* **`CuteBotFlutterSample`**: Complete cross-platform Flutter Material 3 controller app.
* **`CutebotIosSample`**: Complete native SwiftUI iOS controller app with nearby device scanner.
* **`CutebotReactNativeSample`**: Complete React Native mobile controller app.

## Connecting to the Robot
Robots ID and MAC address are written on the bottom, and they advertise over Bluetooth as: `BBC micro:bit [ID]` (for example `BBC micro:bit [povap]`).

* **iOS:** Do **not** attempt to pair in iOS Settings > Bluetooth (iOS intentionally filters out standard GATT UART devices). Connect directly from within the app scanner using CoreBluetooth.
* **Android:** Ensure **Location Services (GPS) is turned ON** in your quick settings (required by Android OS for BLE scanning) and grant `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` permissions when prompted.

### Best Practices & Hardware Gotchas
* **Motor Deadband (Minimum Usable Speed):** Due to internal gearbox friction, sending speeds between `1` and `20` will usually cause motor whine without rotating the wheels. The effective starting speed is around **`25 – 30`**. When designing joysticks or sliders, map your active speed range to start at 25+.
* **Command Throttling & Telemetry Round-Robin:**
  * When streaming joystick or slider updates (`F`, `B`, `ML`, `MR`, `MS`), throttle outgoing BLE packets to no faster than **every 50ms to 100ms**.
  * Avoid blasting multiple sensor queries (`?DIST#`, `?LINE#`, `?ACCEL#`) simultaneously in the same frame. Round-robin your queries (e.g., query `?LINE` on tick 1, `?DIST` on tick 2) to avoid saturating the micro:bit's Nordic UART buffer and introducing command latency.
* **Battery Brownouts & Sudden Disconnects:** If the robot suddenly disconnects and displays a Sad Face followed by a Happy Face reboot right as you press the throttle or reverse from a stop, this is a **battery brownout** (motor inrush current pulling the 3x AAA voltage below ~3.0V), **not** a Bluetooth software bug. Ask event staff for fresh AAA batteries.
* **BLE UUID Reference:**
  * **UART Service:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  * **RX Characteristic (Write):** `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
  * **TX Characteristic (Notify / Indicate):** `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`