//Simple API to send command to the Cutebot via a bluetooth connection 

let leftSpeed = 0
let rightSpeed = 0

let strip = neopixel.create(DigitalPin.P15, 2, NeoPixelMode.RGB)
bluetooth.startUartService()
basic.showIcon(IconNames.Happy)

bluetooth.onUartDataReceived(serial.delimiters(Delimiters.NewLine), function () {
    let rawStr = bluetooth.uartReadUntil(serial.delimiters(Delimiters.NewLine))

    let parts = rawStr.split(",")
    let cmd = parts[0]

    // 1. Movement Controls
    if (cmd == "F") {
        leftSpeed = 50
        rightSpeed = 50
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "B") {
        leftSpeed = -50
        rightSpeed = -50
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "L") {
        leftSpeed = -50
        rightSpeed = 50
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "R") {
        leftSpeed = 50
        rightSpeed = -50
        cuteBot.motors(leftSpeed, rightSpeed)
    } else if (cmd == "S") {
        leftSpeed = 0
        rightSpeed = 0
        cuteBot.motors(leftSpeed, rightSpeed)
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

    } else if (cmd == "HL") {
        if (parts.length > 3) {
            let rHead = parseInt(parts[1])
            let gHead = parseInt(parts[2])
            let bHead = parseInt(parts[3])
            cuteBot.singleheadlights(cuteBot.RGBLights.RGB_L, rHead, gHead, bHead)
            cuteBot.singleheadlights(cuteBot.RGBLights.RGB_R, rHead, gHead, bHead)
        }
    } else if (cmd == "HO") {
        cuteBot.closeheadlights()
        strip.clear()
        strip.show()
    } else if (cmd == "UG") {
        if (parts.length > 3) {
            let rUnder = parseInt(parts[1])
            let gUnder = parseInt(parts[2])
            let bUnder = parseInt(parts[3])
            strip.showColor(neopixel.rgb(rUnder, gUnder, bUnder))
        }

    } else if (cmd == "BEEP") {
        music.playTone(523, music.beat(BeatFraction.Quarter))
    } else if (cmd == "SIREN") {
        for (let i = 0; i < 2; i++) {
            music.playTone(880, music.beat(BeatFraction.Quarter))
            music.playTone(587, music.beat(BeatFraction.Quarter))
        }
    } else if (cmd == "?DIST") {
        let distance = cuteBot.ultrasonic(cuteBot.SonarUnit.Centimeters)
        bluetooth.uartWriteString("DIST:" + distance + "\n")
    } else if (cmd == "?LINE") {
        let lineStatus = 0
        // Evaluate bottom infrastructure line sensors
        // Return codes: 0 = both white, 1 = right black, 2 = left black, 3 = both black
        if (cuteBot.tracking(cuteBot.TrackingState.L_R_line)) {
            lineStatus = 3
        } else if (cuteBot.tracking(cuteBot.TrackingState.L_line_R_unline)) {
            lineStatus = 2
        } else if (cuteBot.tracking(cuteBot.TrackingState.L_unline_R_line)) {
            lineStatus = 1
        }

        bluetooth.uartWriteString("LINE:" + lineStatus + "\n")
    }
})