import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import java.util.UUID

object CutebotController {
    private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val UART_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    // Core transmission function
    private fun sendRawCommand(gatt: BluetoothGatt, command: String) {
        val service = gatt.getService(UART_SERVICE_UUID) ?: return
        val rxChar = service.getCharacteristic(UART_RX_UUID) ?: return
        val payload = "$command\n".toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(rxChar, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            rxChar.value = payload
            rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            gatt.writeCharacteristic(rxChar)
        }
    }

    // --- API Commands ---

    // Movement
    fun moveForward(gatt: BluetoothGatt) = sendRawCommand(gatt, "F")
    fun moveBackward(gatt: BluetoothGatt) = sendRawCommand(gatt, "B")
    fun turnLeft(gatt: BluetoothGatt) = sendRawCommand(gatt, "L")
    fun turnRight(gatt: BluetoothGatt) = sendRawCommand(gatt, "R")
    fun stop(gatt: BluetoothGatt) = sendRawCommand(gatt, "S")
    
    // Fine-grained motor control (-100 to 100)
    fun setLeftMotor(gatt: BluetoothGatt, speed: Int) = sendRawCommand(gatt, "ML,$speed")
    fun setRightMotor(gatt: BluetoothGatt, speed: Int) = sendRawCommand(gatt, "MR,$speed")

    // Lights (RGB: 0 to 255)
    fun setHeadlights(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "HL,$r,$g,$b")
    fun turnLightsOff(gatt: BluetoothGatt) = sendRawCommand(gatt, "HO")
    fun setUnderglow(gatt: BluetoothGatt, r: Int, g: Int, b: Int) = sendRawCommand(gatt, "UG,$r,$g,$b")

    // Audio
    fun triggerBeep(gatt: BluetoothGatt) = sendRawCommand(gatt, "BEEP")
    fun triggerSiren(gatt: BluetoothGatt) = sendRawCommand(gatt, "SIREN")

    // Sensor Requests (Responses arrive via GATT characteristic notifications)
    fun requestDistance(gatt: BluetoothGatt) = sendRawCommand(gatt, "?DIST")
    fun requestLineStatus(gatt: BluetoothGatt) = sendRawCommand(gatt, "?LINE")
}