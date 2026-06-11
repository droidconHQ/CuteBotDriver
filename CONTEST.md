# 🏁 The droidcon & fluttercon Robot Rally 2026

Welcome to the ultimate test of mobile development and robotics! We are challenging you to take our custom-built Cutebot platform and code the fastest, most innovative control app to navigate our obstacle course.

## The Challenge
Using the provided Bluetooth Low Energy (BLE) API, you will build a mobile application to remote control an Elecfreaks Cutebot. 

* **Languages & Platforms:** Use any technology you prefer. We provide starter examples in **Kotlin (Android)** and **Dart (Flutter)** to get you started quickly.
* **The Goal:** Navigate our custom-laid track in the shortest time possible.
* **The Rules:** You have all of Thursday and Friday up until the race time to test and refine your code on one of our 10 official test robots.

---

## 📅 The Race Schedule

| Event | Time | Location |
| :--- | :--- | :--- |
| **Development Period** | Thursday & Friday until 15:35 | Workshop Zone |
| **Timed Trial Race** | **Friday @ 15:35 Break** | Workshop Zone |

**Note:** During the race, you will only have **one attempt**. Make it count!

---

## 🏆 Prizes
The winners will be determined by the lowest finish time, adjusted by bonus points.

* **1st Place:** A LEGO set AND your own Cutebot robot!
* **2nd Place:** Your own Cutebot robot.
* **3rd Place:** Your own Cutebot robot.

---

## 💡 How to Earn Bonus Time
Speed isn't everything! Our judge will be awarding bonus time (deducted from your final race time) for creativity and technical execution. Impress the judge with:

1.  **API Mastery:** Making use of all available API methods (sensors, lights, motors).
2.  **Illumination:** Incorporating interesting and creative use of the robot's onboard lighting.
3.  **App Innovation:** Including unique features beyond a simple remote control (e.g., voice control, automated obstacle avoidance, telemetry data visualization, or UI flair).

*Bonus points are awarded at the judge’s discretion.*

---

## 🛠 Getting Started

### 1. Hardware Setup
* Connect to your a robot using the MAC address printed on the chassis sticker.
* Ensure your robot is powered **ON** (switch on the back of the black chassis).

### 2. The API Specification
The robot uses a standard Nordic UART Service (Service UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`). Write your commands to the **TX Characteristic** (`0003`) followed by a **`#`** delimiter.

| Command | Description |
| :--- | :--- |
| `F`, `B`, `L`, `R`, `S` | Forward, Backward, Left, Right, Stop |
| `ML,[speed]` / `MR,[speed]` | Set motor speed (-100 to 100) |
| `HL,[r],[g],[b]` | Set headlights |
| `UG,[r],[g],[b]` | Set underglow lights |
| `HO` | Lights off |
| `?DIST` | Request distance sensor data |
| `?LINE` | Request line tracker status |

### 3. Starter Code
Clone our repository to grab the boilerplate code for Android and Flutter:
`git clone https://github.com/droidconHQ/CuteBotDriver`

**Happy coding, and may the fastest build win!**