# 📋 CuteBot Driver & Firmware Roadmap (TODO)

This document outlines planned improvements, optimizations, and architectural enhancements for the Cutebot starter firmware (`microbitapi.js`), BLE API protocol, and native client apps for future hackathons and robotics contests.

---

## 🎯 1. Directional Feedback & Heading Stabilization (Option B)

### Background
The standard BBC micro:bit `input.compassHeading()` API assumes the board is lying horizontally (flat on a table). When mounted vertically in the Cutebot chassis (pitch $\approx 90^\circ$), the tilt-compensation math encounters Euler angle gimbal lock, and the proximity to the chassis DC drive motors creates hard-iron magnetic distortion.

### Planned Implementation: Relative Yaw Drift ("Option B")
Instead of trying to determine absolute geographic North, robots driving on a competition track need to know if they are drifting off-course relative to their starting heading when beginning a straightaway.

1. **Tare / Zero Command (`TARE#` or `ZERO_HEADING#`)**:
   * Takes a baseline snapshot of the horizontal magnetic field components in the robot's driving plane:
     * $X$ (lateral / left-right)
     * $Z$ (longitudinal / forward-backward)
   * Stores $X_0$ and $Z_0$ in memory as the reference angle: $\theta_0 = \text{atan2}(X_0, Z_0)$.
   * Acknowledges with `ZEROED#\n`.

2. **Relative Heading Telemetry (`?HEADING#` or `?YAW#`)**:
   * Reads current magnetic force $X$ and $Z$.
   * Computes current angle: $\theta = \text{atan2}(X, Z)$.
   * Returns signed drift angle: $\Delta\theta = (\theta - \theta_0)$ normalized to $[-180^\circ, +180^\circ]$.
   * Response format: `YAW:[deg]#\n` (e.g. `YAW:-12#\n` indicates drifting $12^\circ$ to the left).

3. **Raw Magnetometer Telemetry (`?MAG#`)**:
   * Query raw magnetic force vectors: `input.magneticForce(Dimension.X)`, `Y`, `Z`.
   * Response format: `MAG:[x],[y],[z]#\n` (in $\mu\text{T}$).
   * Allows advanced participants to implement custom sensor fusion filters (Kalman, Madgwick) on their mobile apps.

4. **Onboard Steering Stabilization Mode (`AUTO_STRAIGHT,[speed]#`)**:
   * Optional autonomous assist where the micro:bit monitors $X$ and $Z$ and applies closed-loop PID differential motor correction (`cuteBot.motors(leftSpeed + correction, rightSpeed - correction)`) to hold a straight line automatically.

---

## ⚡ 2. Firmware Latency & Processing Optimizations (`microbitapi.js`)

Profiling the current `microbitapi.js` reveals several key areas where command processing latency can be dramatically reduced:

### A. Non-Blocking Audio & LED Display (Critical)
* **Problem:** `music.playTone(freq, dur)` and `basic.showString(msg)` are synchronous and **block the entire micro:bit fiber**. If a horn plays for 200ms or text scrolls for 2 seconds (`DISP,TEAM 1`), incoming motor commands (`S#`, `F#`) cannot be processed and buffer up, causing severe control lag or delayed emergency stops.
* **Fix:**
  * Wrap all display and audio calls in background fibers:
    ```typescript
    control.inBackground(() => {
        music.playTone(freq, dur)
    })
    ```
  * Or use non-blocking sound modes: `music.play(sound, MusicPlayMode.InBackground)`.

### B. Allocation-Free & Fast-Path Command Parsing
* **Problem:** Every incoming packet executes:
  ```typescript
  let rawStr = bluetooth.uartReadUntil("#").trim()
  let parts = rawStr.split(",")
  let cmd = parts[0].trim()
  ```
  On an ARM Cortex-M4 with 128KB RAM, calling `trim()`, `split()`, and creating arrays at 20Hz generates excessive heap allocations, triggering MakeCode garbage collection pauses.
* **Fix:**
  * Avoid `.split(",")` for single commands without parameters (`F`, `B`, `S`, `PING`, `?LINE`, `?DIST`, `HO`, etc.).
  * Only parse parameters if `rawStr.indexOf(",") >= 0`.
  * Index directly by the first character (`cmd.charCodeAt(0)`) to switch quickly into category handlers (`Movement`, `Lighting`, `Sound`, `Telemetry`) rather than evaluating 30 linear `if/else` string comparisons.

### C. Drain UART Buffer in a Loop
* **Problem:** `bluetooth.onUartDataReceived("#", ...)` fires when a delimiter arrives. If multiple packets arrived in the same BLE transfer (e.g. `F,60#?LINE#`), handling only one per event leaves queued commands in the buffer until the next event.
* **Fix:** Use a loop to drain all pending commands in the buffer:
  ```typescript
  bluetooth.onUartDataReceived("#", function () {
      while (true) {
          let rawStr = bluetooth.uartReadUntil("#")
          if (rawStr.length == 0) break
          processCommand(rawStr)
      }
  })
  ```

### D. Single-Roundtrip Batch Telemetry Query (`?ALL#`)
* **Problem:** Mobile apps wanting full dashboards currently send 6 distinct BLE queries (`?DIST#`, `?LINE#`, `?ACCEL#`, `?LIGHT#`, `?TEMP#`, `?COMPASS#`). This requires 6 separate BLE roundtrips, queuing delays, and notification packets.
* **Fix:** Add a composite query:
  * Command: `?ALL#`
  * Response: `ALL:[dist],[line],[ax],[ay],[az],[light],[temp]#\n`
  * Reduces BLE buffer traffic and packet round-trips by >80%.

### E. Ultrasonic Ping Timeout Protection
* **Problem:** `cuteBot.ultrasonic(Centimeters)` uses `pulseIn`, which can block for up to 30ms–50ms if pointing into empty space or if the ultrasonic transducer is unplugged.
* **Fix:** Cache distance in a background fiber polled at 10Hz, so `?DIST#` returns immediately from memory without blocking UART handling.

---

## 📱 3. Client Starter App & SDK Enhancements

1. **Software Steering Trim Slider:**
   * Add a persistent UI trim slider in all sample apps (`CutebotAndroidSample`, `CuteBotFlutterSample`, `CutebotIosSample`, `CutebotReactNativeSample`).
   * Automatically maps steering commands to `MS,left + trim, right - trim` to correct for un-encoded DC motor manufacturing variances.

2. **Automated BLE Queue Throttler:**
   * Build a lightweight token-bucket or queue scheduler into `AndroidApi.kt`, `FlutterApi.dart`, `IosApi.swift`, and `ReactNativeApi.ts`.
   * Automatically rate-limits outgoing motor packets to 60ms–80ms and interleaves telemetry requests so beginners don't accidentally flood the Nordic UART buffer.

3. **Battery Brownout Detection in Telemetry:**
   * Detect sudden disconnection followed by reconnect within 3 seconds, displaying a friendly prompt: *"Robot may have experienced a battery brownout. Check AAA batteries!"*