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
5. Switch the editor from Blocks to **JavaScript / TypeScript** mode.
6. Copy the code from `microbitapi.js` in this repo and paste it into the editor.
7. Go to **Project Settings** (the gear icon) and ensure that **"No Pairing Required: Anyone can connect via Bluetooth"** is enabled.
8. Click **Download** to flash the `.hex` file onto your micro:bit via USB.

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
| `?COMPASS#` | Magnetometer / Compass heading (0° - 359°) | `COMPASS:[deg]#\n` | `COMPASS:180#\n` |
| `?ACCEL#` | 3-axis accelerometer (X, Y, Z mg forces) | `ACCEL:[x],[y],[z]#\n` | `ACCEL:0,25,1020#\n` |
| `?LIGHT#` | Ambient light sensor level (0 - 255) | `LIGHT:[level]#\n` | `LIGHT:128#\n` |
| `?TEMP#` | Onboard temperature sensor (°C) | `TEMP:[celsius]#\n` | `TEMP:24#\n` |
| `PING#` | Connectivity / latency verification | `PONG#\n` | `PONG#\n` |

---

## Client Integration Examples

Look inside the client application directories for boilerplate helper classes that implement this exact API:

* **`AndroidApi.kt`**: Contains a standard Android BLE wrapper leveraging `BluetoothGatt`.
* **`FlutterApi.dart`**: Contains a cross-platform implementation utilizing the `flutter_blue_plus` package ecosystem.

* **`CutebotAndroidSample`**: Contains a complete example application using the `AndroidApi.kt`.
* **`CutebotFlutterSample`**: Contains a complete example application using the `FlutterApi.kt`. Please note this sample has only been tested on Android

## Connecting to the Robot
Make sure you have first paired the robot with your phone before running your app, the robot will need to be connected first. 
Robots ID and MAC address are written on the bottom, they will show up in the bluetooth menu as: `BBC micro:bit[ID]` for example `BBC micro:bit[povap]`

### Best Practices for Developers
* **Throttling:** When linking UI joysticks or sliders to the `ML` and `MR` commands, throttle your output to send a packet no quicker than every 50ms to 100ms. Flooding the BLE buffer will cause command latency.
* **BLE UUID Reference:**
  * **UART Service:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  * **RX Characteristic (Write):** `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
  * **TX Characteristic (Notify):** `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`