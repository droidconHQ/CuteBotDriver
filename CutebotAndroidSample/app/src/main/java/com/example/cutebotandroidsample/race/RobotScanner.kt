package com.example.cutebotandroidsample.race

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.cutebotandroidsample.CutebotController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "RobotScanner"

/** A Cutebot seen advertising nearby. */
data class DiscoveredRobot(
    val address: String,
    /** The chassis name, e.g. "BBC micro:bit [gezag]", when the advert carries one. */
    val name: String?,
    val rssi: Int,
    val device: BluetoothDevice,
) {
    /** The five-letter identifier printed on the chassis label, when it can be parsed. */
    val shortName: String? get() = name?.substringAfter('[', "")?.substringBefore(']')?.takeIf { it.isNotBlank() }
}

/**
 * Finds Cutebots over BLE rather than addressing them by typed MAC.
 *
 * This exists for a concrete reason beyond convenience. `BluetoothAdapter.getRemoteDevice(
 * String)` builds a device handle that assumes a **public** address, but the micro:bit
 * advertises with a *random static* one; connecting to the hand-built handle can therefore
 * fail against a robot that is powered on and advertising perfectly well. A
 * [BluetoothDevice] handed back by the scanner carries the correct address type, so
 * connecting to a scanned result is the reliable path.
 *
 * It also means a racer picks their car off a list by the name on its sticker instead of
 * transcribing twelve hex digits at a noisy booth.
 */
class RobotScanner(private val context: Context) {

    private val _results = MutableStateFlow<List<DiscoveredRobot>>(emptyList())
    val results: StateFlow<List<DiscoveredRobot>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val seen = linkedMapOf<String, DiscoveredRobot>()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = record(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::record)

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with code $errorCode")
            _scanning.value = false
            _error.value = "Scan failed (code $errorCode)"
        }
    }

    @SuppressLint("MissingPermission")
    private fun record(result: ScanResult) {
        val device = result.device ?: return
        val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()

        // Keep anything advertising the Nordic UART service, plus anything calling itself a
        // micro:bit — some firmware builds omit the service UUID from the advert payload
        // and only expose it after connection.
        val advertisesUart = result.scanRecord
            ?.serviceUuids
            ?.any { it.uuid == CutebotController.UART_SERVICE_UUID } == true
        val looksLikeMicrobit = name?.contains("micro:bit", ignoreCase = true) == true
        if (!advertisesUart && !looksLikeMicrobit) return

        val entry = DiscoveredRobot(
            address = device.address,
            name = name,
            rssi = result.rssi,
            device = device,
        )
        seen[device.address] = entry
        // Strongest signal first: the robot in your hand should top the list.
        _results.value = seen.values.sortedByDescending { it.rssi }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (_scanning.value) return
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _error.value = "Bluetooth is off"
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _error.value = "BLE scanning unavailable"
            return
        }

        seen.clear()
        _results.value = emptyList()
        _error.value = null
        _scanning.value = true

        // An unfiltered scan is used deliberately: filtering on the UART service UUID in
        // the scan filter would hide robots whose advert omits it, and the payload is
        // re-checked in `record` anyway.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { scanner.startScan(emptyList<ScanFilter>(), settings, callback) }
            .onFailure {
                Log.e(TAG, "startScan threw", it)
                _scanning.value = false
                _error.value = it.message ?: "Could not start scanning"
            }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!_scanning.value) return
        _scanning.value = false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = manager?.adapter?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(callback) }
    }

    /** Service UUID exposed for scan filters built elsewhere. */
    val uartServiceParcelUuid: ParcelUuid = ParcelUuid(CutebotController.UART_SERVICE_UUID)
}
