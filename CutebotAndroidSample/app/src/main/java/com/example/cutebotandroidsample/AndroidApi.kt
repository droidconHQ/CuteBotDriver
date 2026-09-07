package com.example.cutebotandroidsample

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import java.util.UUID

// ==========================================
// Strongly-Typed Telemetry Data Models
// ==========================================
sealed class CutebotTelemetry {
    data class Distance(val cm: Int) : CutebotTelemetry()
    data class LineTracker(val code: Int, val description: String) : CutebotTelemetry()
    data class Compass(val degrees: Int) : CutebotTelemetry()
    data class Acceleration(val x: Int, val y: Int, val z: Int) : CutebotTelemetry()
    data class LightLevel(val level: Int) : CutebotTelemetry()
    data class Temperature(val celsius: Int) : CutebotTelemetry()
    object Pong : CutebotTelemetry()
    data class Raw(val text: String) : CutebotTelemetry()
}

// Optional listener interface for telemetry callbacks
interface CutebotTelemetryListener {
    fun onDistance(cm: Int) {}
    fun onLineStatus(code: Int, description: String) {}
    fun onCompass(degrees: Int) {}
    fun onAcceleration(x: Int, y: Int, z: Int) {}
    fun onLightLevel(level: Int) {}
    fun onTemperature(celsius: Int) {}
    fun onPong() {}
    fun onRawTelemetry(raw: String) {}
}

object CutebotController {
    val UART_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val UART_RX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Phone writes here
    val UART_TX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Phone receives notifications here
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Callbacks for received telemetry
    var telemetryListener: CutebotTelemetryListener? = null
    var onTelemetry: ((CutebotTelemetry) -> Unit)? = null

    // Internal buffer for BLE packet assembly
    private var rxBuffer = ""

    // Core transmission function
    @SuppressLint("MissingPermission")
    fun sendRawCommand(gatt: BluetoothGatt, command: String) {
        val service = gatt.getService(UART_SERVICE_UUID) ?: return
        val rxChar = service.getCharacteristic(UART_RX_CHAR_UUID) ?: run {
            android.util.Log.e("BLE", "Failed to find RX Characteristic!")
            return
        }
        android.util.Log.d("BLE", "Transmitting: $command")
        val payload = "$command#".toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(rxChar, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            rxChar.value = payload
            @Suppress("DEPRECATION")
            rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(rxChar)
        }
    }

    // Enable telemetry notifications on the TX characteristic
    @SuppressLint("MissingPermission")
    fun enableNotifications(gatt: BluetoothGatt, enable: Boolean = true): Boolean {
        val service = gatt.getService(UART_SERVICE_UUID) ?: return false
        val txChar = service.getCharacteristic(UART_TX_CHAR_UUID) ?: run {
            android.util.Log.e("BLE", "Failed to find TX Characteristic!")
            return false
        }
        if (!gatt.setCharacteristicNotification(txChar, enable)) {
            return false
        }
        val descriptor = txChar.getDescriptor(CCCD_UUID) ?: return false
        val descriptorVal = if (enable) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, descriptorVal) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = descriptorVal
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    // ==========================================
    // Telemetry Reception & Packet Parsing
    // ==========================================

    /**
     * Call this from BluetoothGattCallback.onCharacteristicChanged to parse incoming telemetry
     */
    fun handleNotification(bytes: ByteArray) {
        val chunk = String(bytes, Charsets.UTF_8)
        rxBuffer += chunk
        while (rxBuffer.contains("#")) {
            val packet = rxBuffer.substringBefore("#").trim()
            rxBuffer = rxBuffer.substringAfter("#")
            if (packet.isNotEmpty()) {
                val parsed = parsePacket(packet)
                dispatchTelemetry(parsed)
            }
        }
    }

    /**
     * Reset the incoming buffer (e.g. on disconnect)
     */
    fun resetBuffer() {
        rxBuffer = ""
    }

    private fun parsePacket(packet: String): CutebotTelemetry {
        val clean = packet.trim()
        return when {
            clean.startsWith("DIST:") -> {
                val cm = clean.removePrefix("DIST:").toIntOrNull() ?: 0
                CutebotTelemetry.Distance(cm)
            }
            clean.startsWith("LINE:") -> {
                val code = clean.removePrefix("LINE:").toIntOrNull() ?: 0
                val desc = when (code) {
                    0 -> "White (0)"
                    1 -> "Right Black (1)"
                    2 -> "Left Black (2)"
                    3 -> "Both Black (3)"
                    else -> "Unknown ($code)"
                }
                CutebotTelemetry.LineTracker(code, desc)
            }
            clean.startsWith("COMPASS:") -> {
                val deg = clean.removePrefix("COMPASS:").toIntOrNull() ?: 0
                CutebotTelemetry.Compass(deg)
            }
            clean.startsWith("ACCEL:") -> {
                val coords = clean.removePrefix("ACCEL:").split(",")
                val x = coords.getOrNull(0)?.toIntOrNull() ?: 0
                val y = coords.getOrNull(1)?.toIntOrNull() ?: 0
                val z = coords.getOrNull(2)?.toIntOrNull() ?: 0
                CutebotTelemetry.Acceleration(x, y, z)
            }
            clean.startsWith("LIGHT:") -> {
                val level = clean.removePrefix("LIGHT:").toIntOrNull() ?: 0
                CutebotTelemetry.LightLevel(level)
            }
            clean.startsWith("TEMP:") -> {
                val temp = clean.removePrefix("TEMP:").toIntOrNull() ?: 0
                CutebotTelemetry.Temperature(temp)
            }
            clean.startsWith("PONG") -> {
                CutebotTelemetry.Pong
            }
            else -> CutebotTelemetry.Raw(clean)
        }
    }

    private fun dispatchTelemetry(telemetry: CutebotTelemetry) {
        onTelemetry?.invoke(telemetry)

        val l = telemetryListener ?: return
        when (telemetry) {
            is CutebotTelemetry.Distance -> l.onDistance(telemetry.cm)
            is CutebotTelemetry.LineTracker -> l.onLineStatus(telemetry.code, telemetry.description)
            is CutebotTelemetry.Compass -> l.onCompass(telemetry.degrees)
            is CutebotTelemetry.Acceleration -> l.onAcceleration(telemetry.x, telemetry.y, telemetry.z)
            is CutebotTelemetry.LightLevel -> l.onLightLevel(telemetry.level)
            is CutebotTelemetry.Temperature -> l.onTemperature(telemetry.celsius)
            is CutebotTelemetry.Pong -> l.onPong()
            is CutebotTelemetry.Raw -> l.onRawTelemetry(telemetry.text)
        }
    }

    // ==========================================
    // Movement Controls
    // ==========================================
    fun moveForward(gatt: BluetoothGatt, speed: Int = 50) =
        if (speed == 50) sendRawCommand(gatt, "F") else sendRawCommand(gatt, "F,$speed")
    fun moveBackward(gatt: BluetoothGatt, speed: Int = 50) =
        if (speed == 50) sendRawCommand(gatt, "B") else sendRawCommand(gatt, "B,$speed")
    fun turnLeft(gatt: BluetoothGatt, speed: Int = 50) =
        if (speed == 50) sendRawCommand(gatt, "L") else sendRawCommand(gatt, "L,$speed")
    fun turnRight(gatt: BluetoothGatt, speed: Int = 50) =
        if (speed == 50) sendRawCommand(gatt, "R") else sendRawCommand(gatt, "R,$speed")
    fun stop(gatt: BluetoothGatt) = sendRawCommand(gatt, "S")

    // Fine-grained motor control (-100 to 100)
    fun setLeftMotor(gatt: BluetoothGatt, speed: Int) = sendRawCommand(gatt, "ML,$speed")
    fun setRightMotor(gatt: BluetoothGatt, speed: Int) = sendRawCommand(gatt, "MR,$speed")
    fun setMotorSpeeds(gatt: BluetoothGatt, left: Int, right: Int) = sendRawCommand(gatt, "MS,$left,$right")

    // ==========================================
    // Headlights & Underglow (RGB: 0 to 255)
    // ==========================================
    fun setHeadlights(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "HL,$r,$g,$b")
    fun setLeftHeadlight(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "HLL,$r,$g,$b")
    fun setRightHeadlight(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "HLR,$r,$g,$b")
    fun turnLightsOff(gatt: BluetoothGatt) = sendRawCommand(gatt, "HO")

    fun setUnderglow(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "UG,$r,$g,$b")
    fun setLeftUnderglow(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "UGL,$r,$g,$b")
    fun setRightUnderglow(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "UGR,$r,$g,$b")
    fun turnUnderglowOff(gatt: BluetoothGatt) = sendRawCommand(gatt, "UGO")

    // ==========================================
    // Audio & Buzzer Controls
    // ==========================================
    fun playHorn(gatt: BluetoothGatt) = sendRawCommand(gatt, "HORN")
    fun playBeep(gatt: BluetoothGatt) = sendRawCommand(gatt, "BEEP")
    fun playTone(gatt: BluetoothGatt, frequency: Int, durationMs: Int) = sendRawCommand(gatt, "TONE,$frequency,$durationMs")
    fun stopSound(gatt: BluetoothGatt) = sendRawCommand(gatt, "QUIET")

    // ==========================================
    // micro:bit 5x5 Display Controls
    // ==========================================
    fun displayText(gatt: BluetoothGatt, text: String) = sendRawCommand(gatt, "DISP,$text")
    fun displayIcon(gatt: BluetoothGatt, iconName: String) = sendRawCommand(gatt, "ICON,$iconName")
    fun clearDisplay(gatt: BluetoothGatt) = sendRawCommand(gatt, "CLS")

    // ==========================================
    // Sensor Requests
    // ==========================================
    fun requestDistance(gatt: BluetoothGatt) = sendRawCommand(gatt, "?DIST")
    fun requestLineStatus(gatt: BluetoothGatt) = sendRawCommand(gatt, "?LINE")
    fun requestCompass(gatt: BluetoothGatt) = sendRawCommand(gatt, "?COMPASS")
    fun requestAcceleration(gatt: BluetoothGatt) = sendRawCommand(gatt, "?ACCEL")
    fun requestLightLevel(gatt: BluetoothGatt) = sendRawCommand(gatt, "?LIGHT")
    fun requestTemperature(gatt: BluetoothGatt) = sendRawCommand(gatt, "?TEMP")

    // ==========================================
    // Diagnostics / Ping
    // ==========================================
    fun ping(gatt: BluetoothGatt) = sendRawCommand(gatt, "PING")
}