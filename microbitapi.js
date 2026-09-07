// Elecfreaks Cutebot BLE UART API Firmware for BBC micro:bit V2
// Controls Cutebot motors, headlights, underglow NeoPixels, buzzer, 
// micro:bit 5x5 LED display, and broadcasts real-time sensor telemetry.

let leftSpeed = 0
let rightSpeed = 0

// Initialize Underglow NeoPixels (Pin 15 on Cutebot, 2 RGB LEDs)
let strip = neopixel.create(DigitalPin.P15, 2, NeoPixelMode.RGB)

// Start Nordic UART Service
bluetooth.startUartService()
basic.showIcon(IconNames.Happy)

// Safety Fail-safe: Auto-stop motors and turn off lights if BLE connection drops
bluetooth.onBluetoothDisconnected(function () {
    cuteBot.motors(0, 0)
    leftSpeed = 0
    rightSpeed = 0
    cuteBot.closeheadlights()
    strip.clear()
    strip.show()
    music.stopAllSounds()
    basic.showIcon(IconNames.Sad)
})

bluetooth.onBluetoothConnected(function () {
    basic.showIcon(IconNames.Happy)
})

// Main UART Command Receiver (commands delimited by '#')
bluetooth.onUartDataReceived("#", function () {
    let rawStr = bluetooth.uartReadUntil("#").trim()
    if (rawStr.length == 0) {
        return
    }

    let parts = rawStr.split(",")
    let cmd = parts[0]

    // ==========================================
    // 1. Movement Controls
    // ==========================================
    if (cmd == "F") {
        let spd = parts.length > 1 ? parseInt(parts[1]) : 50
        leftSpeed = spd
        rightSpeed = spd
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "B") {
        let spd = parts.length > 1 ? parseInt(parts[1]) : 50
        leftSpeed = -spd
        rightSpeed = -spd
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "L") {
        let spd = parts.length > 1 ? parseInt(parts[1]) : 50
        leftSpeed = -spd
        rightSpeed = spd
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "R") {
        let spd = parts.length > 1 ? parseInt(parts[1]) : 50
        leftSpeed = spd
        rightSpeed = -spd
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "S") {
        leftSpeed = 0
        rightSpeed = 0
        cuteBot.motors(0, 0)
    } else if (cmd == "ML") {
        if (parts.length > 1) {
            leftSpeed = parseInt(parts[1])
            cuteBot.motors(leftSpeed, rightSpeed)
        }
    } else if (cmd == "MR") {
        if (parts.length > 1) {
            rightSpeed = parseInt(parts[1])
            cuteBot.motors(leftSpeed, rightSpeed)
        }
    } else if (cmd == "MS") {
        if (parts.length > 2) {
            leftSpeed = parseInt(parts[1])
            rightSpeed = parseInt(parts[2])
            cuteBot.motors(leftSpeed, rightSpeed)
        }
    }

    // ==========================================
    // 2. Headlights & Underglow Lighting
    // ==========================================
    else if (cmd == "HL") {
        // Set both headlights
        if (parts.length > 3) {
            let rHead = parseInt(parts[1])
            let gHead = parseInt(parts[2])
            let bHead = parseInt(parts[3])
            cuteBot.singleheadlights(cuteBot.RGBLights.RGB_L, rHead, gHead, bHead)
            cuteBot.singleheadlights(cuteBot.RGBLights.RGB_R, rHead, gHead, bHead)
        }
    } else if (cmd == "HLL") {
        // Left headlight only
        if (parts.length > 3) {
            let rHead = parseInt(parts[1])
            let gHead = parseInt(parts[2])
            let bHead = parseInt(parts[3])
            cuteBot.singleheadlights(cuteBot.RGBLights.RGB_L, rHead, gHead, bHead)
        }
    } else if (cmd == "HLR") {
        // Right headlight only
        if (parts.length > 3) {
            let rHead = parseInt(parts[1])
            let gHead = parseInt(parts[2])
            let bHead = parseInt(parts[3])
            cuteBot.singleheadlights(cuteBot.RGBLights.RGB_R, rHead, gHead, bHead)
        }
    } else if (cmd == "HO") {
        // Headlights and underglow off
        cuteBot.closeheadlights()
        strip.clear()
        strip.show()
    } else if (cmd == "UG") {
        // Set both underglow NeoPixels
        if (parts.length > 3) {
            let rUnder = parseInt(parts[1])
            let gUnder = parseInt(parts[2])
            let bUnder = parseInt(parts[3])
            strip.showColor(neopixel.rgb(rUnder, gUnder, bUnder))
        }
    } else if (cmd == "UGL") {
        // Left underglow NeoPixel only (pixel 0)
        if (parts.length > 3) {
            let rUnder = parseInt(parts[1])
            let gUnder = parseInt(parts[2])
            let bUnder = parseInt(parts[3])
            strip.setPixelColor(0, neopixel.rgb(rUnder, gUnder, bUnder))
            strip.show()
        }
    } else if (cmd == "UGR") {
        // Right underglow NeoPixel only (pixel 1)
        if (parts.length > 3) {
            let rUnder = parseInt(parts[1])
            let gUnder = parseInt(parts[2])
            let bUnder = parseInt(parts[3])
            strip.setPixelColor(1, neopixel.rgb(rUnder, gUnder, bUnder))
            strip.show()
        }
    } else if (cmd == "UGO") {
        // Underglow off only
        strip.clear()
        strip.show()
    }

    // ==========================================
    // 3. Audio & Buzzer Controls
    // ==========================================
    else if (cmd == "HORN") {
        music.playTone(440, 200)
    } else if (cmd == "BEEP") {
        music.playTone(880, 100)
    } else if (cmd == "TONE") {
        if (parts.length > 2) {
            let freq = parseInt(parts[1])
            let dur = parseInt(parts[2])
            music.playTone(freq, dur)
        }
    } else if (cmd == "QUIET" || cmd == "MUTE") {
        music.stopAllSounds()
    }

    // ==========================================
    // 4. micro:bit 5x5 LED Display Controls
    // ==========================================
    else if (cmd == "DISP") {
        if (parts.length > 1) {
            let msg = parts.slice(1).join(",")
            basic.showString(msg)
        }
    } else if (cmd == "ICON") {
        if (parts.length > 1) {
            let iconName = parts[1].toUpperCase()
            if (iconName == "HAPPY") {
                basic.showIcon(IconNames.Happy)
            } else if (iconName == "SAD") {
                basic.showIcon(IconNames.Sad)
            } else if (iconName == "HEART") {
                basic.showIcon(IconNames.Heart)
            } else if (iconName == "YES") {
                basic.showIcon(IconNames.Yes)
            } else if (iconName == "NO") {
                basic.showIcon(IconNames.No)
            } else if (iconName == "SKULL") {
                basic.showIcon(IconNames.Skull)
            }
        }
    } else if (cmd == "CLS") {
        basic.clearScreen()
    }

    // ==========================================
    // 5. Sensor Telemetry & Diagnostics
    // ==========================================
    else if (cmd == "?DIST") {
        let distance = cuteBot.ultrasonic(cuteBot.SonarUnit.Centimeters)
        bluetooth.uartWriteString("DIST:" + distance + "#\n")
    } else if (cmd == "?LINE") {
        let lineStatus = 0
        // Evaluate bottom infrared line sensors
        // Return codes: 0 = both white, 1 = right black, 2 = left black, 3 = both black
        if (cuteBot.tracking(cuteBot.TrackingState.L_R_line)) {
            lineStatus = 3
        } else if (cuteBot.tracking(cuteBot.TrackingState.L_line_R_unline)) {
            lineStatus = 2
        } else if (cuteBot.tracking(cuteBot.TrackingState.L_unline_R_line)) {
            lineStatus = 1
        }
        bluetooth.uartWriteString("LINE:" + lineStatus + "#\n")
    } else if (cmd == "?COMPASS") {
        let heading = input.compassHeading()
        bluetooth.uartWriteString("COMPASS:" + heading + "#\n")
    } else if (cmd == "?ACCEL") {
        let ax = input.acceleration(Dimension.X)
        let ay = input.acceleration(Dimension.Y)
        let az = input.acceleration(Dimension.Z)
        bluetooth.uartWriteString("ACCEL:" + ax + "," + ay + "," + az + "#\n")
    } else if (cmd == "?LIGHT") {
        let light = input.lightLevel()
        bluetooth.uartWriteString("LIGHT:" + light + "#\n")
    } else if (cmd == "?TEMP") {
        let temp = input.temperature()
        bluetooth.uartWriteString("TEMP:" + temp + "#\n")
    }

    // ==========================================
    // 6. Ping / Connectivity Check
    // ==========================================
    else if (cmd == "PING") {
        bluetooth.uartWriteString("PONG#\n")
    }
})