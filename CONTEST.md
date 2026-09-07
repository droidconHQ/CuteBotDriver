# 🏁 The Next App Robot Rallye 2026

Welcome to the official **Next App Robot Rallye**! ([nextappcon.com](https://www.nextappcon.com/))

We are challenging developers across every ecosystem—**Android**, **iOS**, **Flutter**, and **React Native**—to take our custom-built Elecfreaks Cutebot robot car platform and code the fastest, smartest, and most innovative mobile controller app to conquer our race track printed on the floor.

---

## 🎯 The Challenge

Using Bluetooth Low Energy (BLE), you will build a mobile application to steer and navigate an Elecfreaks Cutebot robot car around the official race track (printed directly on the floor in the workshop area) in the shortest adjusted lap time.

* **Choose Your Weapon:** Build in whichever mobile framework you love most! We provide complete starter projects and production-ready BLE API drivers for:
  * 🤖 **Android** (Kotlin + Jetpack Compose): [`CutebotAndroidSample`](file:///CutebotAndroidSample) & [`AndroidApi.kt`](file:///AndroidApi.kt)
  * 🍏 **iOS** (Swift + SwiftUI + CoreBluetooth): [`CutebotIosSample`](file:///CutebotIosSample) & [`IosApi.swift`](file:///IosApi.swift)
  * 💙 **Flutter** (Dart + Material 3): [`CuteBotFlutterSample`](file:///CuteBotFlutterSample) & [`FlutterApi.dart`](file:///FlutterApi.dart)
  * ⚛️ **React Native** (TypeScript + `react-native-ble-plx`): [`CutebotReactNativeSample`](file:///CutebotReactNativeSample) & [`ReactNativeApi.ts`](file:///ReactNativeApi.ts)
* **The Goal:** Complete the floor-printed race track course in the shortest time.
* **The Contest Duration:** The event runs **across all three days of the conference**! You can test, tune, and refine your code on the official test robots throughout Wednesday, Thursday, and Friday.

> [!WARNING]
> ### 🛑 STRICT ROBOT POLICY: Robots Never Leave the Workshop Area!
> Official test robots **MUST NEVER leave the Workshop / Robotics Track Area** under any circumstances. They are shared hardware resources provided for all attendees to test with on-site. **These robots are NOT for you to take away, carry to other rooms, or take home.** Please respect your fellow developers and keep them at the workshop track tables!

---

## 📅 Schedule of Events

The challenge runs throughout Next App DevCon, culminating in the live championship races on Friday afternoon:

| Day & Time | Event | Location | Details |
| :--- | :--- | :--- | :--- |
| **Wednesday** (All Day) | **Hack & Test Kickoff** | Robotics / Workshop Zone | Pick up a robot, pair over Bluetooth, and test baseline steering. |
| **Thursday** (All Day) | **Open Track Practice & Tuning** | Competition Track (Workshop) | Practice cornering, calibrate line sensors, and fine-tune motor throttling. |
| **Friday Morning** | **Qualifying & Final Polish** | Competition Track (Workshop) | Final test laps, qualifying check-ins, and race roster registration. |
| **Friday Afternoon** *(Time TBC)* | **🏁 The Robot Rallye Finals** | Competition Track Main Stage | **Official timed trials!** Live leaderboard, judge evaluations, and awards ceremony. |

> [!IMPORTANT]
> The exact start time for Friday afternoon's finals will be confirmed during Friday morning announcements and posted at the Track Booth. Make sure your team is registered before Friday midday!
>
> During the official finals, each team will have **one timed attempt**. Make it count!

---

## 🏆 Prizes

Winners are determined by the lowest **Adjusted Lap Time** (Raw Time minus Earned Bonus Deductions):

* 🥇 **1st Place:** Grand Prize Trophy + LEGO Robotics Set + your very own Elecfreaks Cutebot Robot Kit!
* 🥈 **2nd Place:** Official Elecfreaks Cutebot Robot Kit + Conference Swag Pack.
* 🥉 **3rd Place:** Official Elecfreaks Cutebot Robot Kit.
* 🎖️ **Judge's Innovation Award:** Special prize for the most creative UI, telemetry visualization, or autonomous line-following capability!

---

## ⏱️ How to Earn Time Bonuses (The "Speed Isn't Everything" Rule)

Raw driving speed is only half the battle. Our judges will award **bonus seconds deducted directly from your final lap time** for technical excellence, sensor integration, and creative execution:

### 1. 📡 Two-Way Telemetry & Autonomous Assist (Up to -10s)
* **Line Tracking Assist / Autonomous Steering:** Use the bottom infrared line sensors (`?LINE`) to stay centered on the track lines printed on the floor or implement an automated line-following lap.
* **Heading Stabilization:** Use the onboard magnetometer compass (`?COMPASS`) to auto-correct steering and maintain a straight heading down the straightaways.
* **Telemetry Dashboard:** Display real-time gauges for line sensor status (`?LINE`), battery/temperature (`?TEMP`), ambient light (`?LIGHT`), or accelerometer g-forces (`?ACCEL`).
* **Telemetry Speedometer & Latency Tracking:** Measure connection ping (`PING`) and estimate vehicle speed/drift using onboard sensors.

### 2. 🚨 Illumination & Signaling (Up to -5s)
* **Dynamic Turn Signals:** Flash the left headlight (`HLL,255,165,0`) or right headlight (`HLR,255,165,0`) amber when steering.
* **Brake & Reverse Lights:** Glow bright red on deceleration or reversing.
* **Underglow Ground Effects:** Drive RGB underglow animations (`UG`, `UGL`, `UGR`) that react to speed, turns, or connection health.

### 3. 🔊 Audio & Visual Feedback (Up to -5s)
* **Honking & Alerts:** Honk the horn (`HORN`) or play sound effects (`BEEP`, `TONE`) when overtaking or crossing track milestones.
* **5x5 LED Matrix Screen:** Scroll team names (`DISP,TEAM 1`) or display animated status icons (`ICON,HAPPY`, `ICON,HEART`, `ICON,YES`).

### 4. 🎮 App Innovation & UX Flair (Up to -10s)
* **Novel Control Modes:** Gyroscopic/accelerometer phone tilt-steering, floating on-screen joysticks, or physical Bluetooth gamepad support.
* **Polished Design:** A sleek, responsive UI with fluid animations, haptic feedback, dark mode, and low-latency controls.

*All bonus deductions are evaluated and awarded at the judges' discretion.*

---

## 🛠️ Hardware & Connectivity

### 1. Robot Identification & Care
* Each test robot has a unique 5-letter name and MAC address labeled on the chassis (e.g., `BBC micro:bit [tuzov]`).
* Power switch is located on the rear of the black Cutebot chassis.
* When powered on and connected to Bluetooth, the 5x5 LED screen shows a **Happy Face** 😄. If Bluetooth disconnects, it displays a **Sad Face** 😢 and triggers an **automatic safety motor halt**.
* **Reminder:** All testing takes place within the Workshop / Track area. Robots cannot leave the room and must be returned to the charging/test tables when not in active use.

> [!NOTE]
> All robots at the conference are already pre-flashed with the official firmware (`microbitapi.js`). You do **not** need to re-flash anything—just pair your phone and drive!

### 2. Bluetooth Low Energy Service Architecture
The Cutebot uses the Nordic UART Service over standard BLE:

| Service / Characteristic | UUID | Direction / Purpose |
| :--- | :--- | :--- |
| **Nordic UART Service** | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | Primary GATT Service |
| **RX Characteristic** | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` | **Write Without Response**: Phone sends commands to robot (terminated with `#`) |
| **TX Characteristic** | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` | **Notify**: Robot streams telemetry back to phone (terminated with `#\n`) |

---

## 📜 Full API Command Reference

All commands sent to the **RX Characteristic** must end with a hash symbol (`#`).

### 1. Movement & Steering
| Command | Description | Example |
| :--- | :--- | :--- |
| `F` or `F,[speed]` | Drive forward (speed: 1 to 100, default: 50) | `F,80#` |
| `B` or `B,[speed]` | Drive backward (speed: 1 to 100, default: 50) | `B,60#` |
| `L` or `L,[speed]` | Spin left in place (speed: 1 to 100, default: 50) | `L,45#` |
| `R` or `R,[speed]` | Spin right in place (speed: 1 to 100, default: 50) | `R,45#` |
| `S` | Stop both motors immediately | `S#` |
| `ML,[speed]` | Set individual Left Motor speed (-100 to 100) | `ML,70#` |
| `MR,[speed]` | Set individual Right Motor speed (-100 to 100) | `MR,70#` |
| `MS,[left],[right]` | Set dual Motor differential steering (-100 to 100) | `MS,60,90#` (curve right) |

### 2. Headlights & Underglow LEDs
| Command | Description | Example |
| :--- | :--- | :--- |
| `HL,[r],[g],[b]` | Set both front headlights (RGB: 0-255) | `HL,255,255,255#` (bright white) |
| `HLL,[r],[g],[b]` | Set left headlight only (ideal for turn signals) | `HLL,255,165,0#` (amber) |
| `HLR,[r],[g],[b]` | Set right headlight only (ideal for turn signals) | `HLR,255,165,0#` (amber) |
| `UG,[r],[g],[b]` | Set both underglow NeoPixels (RGB: 0-255) | `UG,0,255,128#` (cyan) |
| `UGL,[r],[g],[b]` | Set left underglow NeoPixel only | `UGL,0,0,255#` (blue) |
| `UGR,[r],[g],[b]` | Set right underglow NeoPixel only | `UGR,255,0,255#` (magenta) |
| `HO` | Turn off all headlights and underglow | `HO#` |
| `UGO` | Turn off underglow only | `UGO#` |

### 3. Horn & Sound
| Command | Description | Example |
| :--- | :--- | :--- |
| `HORN` | Play car horn (440Hz for 200ms) | `HORN#` |
| `BEEP` | Play alert beep (880Hz for 100ms) | `BEEP#` |
| `TONE,[freq],[durMs]` | Play custom tone frequency in Hz and duration in ms | `TONE,523,300#` |
| `QUIET` or `MUTE` | Silence all active audio immediately | `QUIET#` |

### 4. micro:bit 5x5 LED Screen
| Command | Description | Example |
| :--- | :--- | :--- |
| `DISP,[text]` | Scroll text message across the display | `DISP,TEAM ALPHA#` |
| `ICON,[name]` | Show built-in icon (`HAPPY`, `SAD`, `HEART`, `YES`, `NO`, `SKULL`) | `ICON,HEART#` |
| `CLS` | Clear the 5x5 display | `CLS#` |

### 5. Telemetry Queries (Two-Way Requests)
Send query to RX (`#`); receive asynchronous notification on TX (`#\n`):

| Query | Description | Incoming Response Format | Example |
| :--- | :--- | :--- | :--- |
| `?DIST#` | Ultrasonic distance sensor in centimeters | `DIST:[cm]#\n` | `DIST:28#\n` |
| `?LINE#` | Infrared line sensor (0=white, 1=R, 2=L, 3=both) | `LINE:[code]#\n` | `LINE:3#\n` |
| `?COMPASS#` | Magnetometer compass heading (0° - 359°) | `COMPASS:[deg]#\n` | `COMPASS:182#\n` |
| `?ACCEL#` | 3-axis accelerometer (X, Y, Z mg forces) | `ACCEL:[x],[y],[z]#\n` | `ACCEL:0,12,1018#\n` |
| `?LIGHT#` | Ambient light sensor level (0 - 255) | `LIGHT:[level]#\n` | `LIGHT:140#\n` |
| `?TEMP#` | Onboard temperature sensor in °C | `TEMP:[celsius]#\n` | `TEMP:23#\n` |
| `PING#` | Latency / heartbeat verification | `PONG#\n` | `PONG#\n` |

---

## 🚦 Getting Started in 4 Easy Steps

1. **Clone the Repo:**
   ```bash
   git clone https://github.com/droidconHQ/CuteBotDriver.git
   cd CuteBotDriver
   ```

2. **Open Your Preferred Platform Sample:**
   * **Android:** Open `CutebotAndroidSample/` in Android Studio.
   * **iOS:** Open `CutebotIosSample/CutebotIosSample.xcodeproj` in Xcode.
   * **Flutter:** Run `flutter run` inside `CuteBotFlutterSample/robot_controller/`.
   * **React Native:** Run `npm install` inside `CutebotReactNativeSample/`.

3. **Pair and Test:**
   * Power on your test robot and connect from your mobile app.
   * Verify forward/backward controls, turn signals, and test live telemetry readings.

4. **Visit the Track:**
   * Head over to the Workshop & Track Zone to try practice runs on the official printed floor track!

> [!TIP]
> **Pro-Tip on BLE Command Throttling:** When using a joystick, slider, or sensor polling loop, throttle your outgoing BLE commands to no faster than once every 50ms – 100ms. Sending hundreds of commands per second can saturate the micro:bit's BLE buffer and create control lag!

**Good luck, have fun, and may the best app win! 🏎️💨**