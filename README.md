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
6. Copy the code from `firmware/main.ts` in this repo and paste it into the editor.
7. Go to **Project Settings** (the gear icon) and ensure that **"No Pairing Required: Anyone can connect via Bluetooth"** is enabled.
8. Click **Download** to flash the `.hex` file onto your micro:bit via USB.

When the firmware boots successfully, the micro:bit LED matrix will display a **Happy Face** to indicate it is ready for a Bluetooth connection.

---

## Bluetooth UART API Specification

The micro:bit hosts a standard Nordic UART Service. Commands are sent from the mobile app as plain text strings written to the **RX Characteristic**. Every command must be terminated by a hash character (`#`).

### 1. Movement Commands

| Command | Description | Example Payload |
| :--- | :--- | :--- |
| `F` | Drive forward at 50% power | `F#` |
| `B` | Drive backward at 50% power | `B#` |
| `L` | Spin left in place | `L#` |
| `R` | Spin right in place | `R#` |
| `S` | Stop both motors immediately | `S#` |
| `ML,[speed]` | Set Left Motor speed (-100 to 100) | `ML,75#` |
| `MR,[speed]` | Set Right Motor speed (-100 to 100) | `MR,-40#` |

### 2. Lighting Commands

Headlights and underglow accept raw integer values for Red, Green, and Blue channels ranging from `0` to `255`.

| Command | Description | Example Payload |
| :--- | :--- | :--- |
| `HL,[R],[G],[B]` | Set front left and right headlights | `HL,255,0,0#` (Solid Red) |
| `HO` | Turn off all headlights and underglow | `HO#` |
| `UG,[R],[G],[B]` | Set both bottom underglow NeoPixels | `UG,0,255,0#` (Solid Green) |

### 3. Sensor Feedback (Two-Way Requests)

When you write a sensor query to the RX characteristic, the micro:bit processes the hardware reading and broadcasts the result back to your app asynchronously via the **TX Characteristic** notification channel.

* **Distance Query:** Send `?DIST#`
  * The micro:bit responds with: `DIST:[value]#` (where value is an integer string in centimeters)
  * Example response: `DIST:42#`

* **Line Tracker Query:** Send `?LINE#`
  * The micro:bit responds with: `LINE:[code]#`
  * `0`: Both sensors are on a white surface
  * `1`: Right sensor detects a black line
  * `2`: Left sensor detects a black line
  * `3`: Both sensors detect a black line
  * Example response: `LINE:3#`

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