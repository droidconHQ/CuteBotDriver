package com.example.cutebotandroidsample.race

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.example.cutebotandroidsample.CutebotController
import com.example.cutebotandroidsample.CutebotTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "RobotLink"

enum class LinkState { IDLE, CONNECTING, DISCOVERING, READY, RECONNECTING, FAILED }

/** Latest reading per sensor, plus freshness stamps the dashboard greys out on. */
data class Telemetry(
    val distanceCm: Int? = null,
    val lineCode: Int? = null,
    val accelX: Int? = null,
    val accelY: Int? = null,
    val accelZ: Int? = null,
    val lightLevel: Int? = null,
    val temperatureC: Int? = null,
    val latencyMs: Long? = null,
    val packetsReceived: Long = 0L,
    val lastPacketAt: Long = 0L,
)

data class LinkStatus(
    val state: LinkState = LinkState.IDLE,
    val detail: String = "Idle",
    val address: String = "",
    /**
     * Set when a disconnect/reconnect pair lands inside the brownout window. CONTEST.md
     * attributes this to the 3xAAA pack sagging under motor inrush, not to BLE code.
     */
    val brownoutSuspected: Boolean = false,
    val stale: Boolean = false,
    val reconnectAttempt: Int = 0,
) {
    val isReady: Boolean get() = state == LinkState.READY
}

/**
 * Owns the GATT connection, the paced [CommandQueue] feeding it, and the telemetry stream
 * coming back.
 *
 * Beyond plumbing, it adds three race-day safeguards:
 *  - **Latency measurement.** `PING` writes are stamped on their way out so the `PONG`
 *    reply yields a real round-trip time, surfaced on the dashboard.
 *  - **Staleness detection.** If telemetry stops arriving while nominally connected, the
 *    link is flagged and the signalling layer flashes red underglow.
 *  - **Brownout attribution.** A disconnect followed by a reconnect inside
 *    [BROWNOUT_WINDOW_MS] is reported as a battery problem rather than a Bluetooth bug.
 */
class RobotLink(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(LinkStatus())
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    private var gatt: BluetoothGatt? = null
    /** Scanned handle, preferred over a MAC-built one because it carries the address type. */
    private var preferredDevice: BluetoothDevice? = null
    private var pingSentAt: Long = 0L
    private var lastDisconnectAt: Long = 0L
    private var intentionalDisconnect = false
    private var autoReconnect = true

    /** Raised when the firmware reports a line code, so the follower can react immediately. */
    var onLineCode: ((Int) -> Unit)? = null

    val queue = CommandQueue(scope = scope) { command ->
        val target = gatt
        if (target == null) {
            Log.w(TAG, "Dropping '$command' — no active GATT")
            return@CommandQueue
        }
        if (command == "PING") pingSentAt = System.currentTimeMillis()
        CutebotController.sendRawCommand(target, command)
    }

    init {
        CutebotController.onTelemetry = ::ingest
        scope.launch { watchFreshness() }
    }

    /**
     * @param userInitiated true when a human pressed Connect, which clears the retry
     *   budget. Automatic retries pass false so a failing robot still gives up eventually
     *   instead of looping forever.
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String, userInitiated: Boolean = true) {
        val clean = address.trim().uppercase()
        intentionalDisconnect = false
        autoReconnect = true
        if (userInitiated) _status.update { it.copy(reconnectAttempt = 0) }
        closeGatt()

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _status.value = LinkStatus(LinkState.FAILED, "Bluetooth is off", clean)
            return
        }

        // Prefer a handle discovered by the scanner: `getRemoteDevice(String)` assumes a
        // public address, while the micro:bit advertises a random static one, so the
        // hand-built handle can fail against a robot that is advertising perfectly well.
        val device = preferredDevice?.takeIf { it.address.equals(clean, ignoreCase = true) }
            ?: runCatching { adapter.getRemoteDevice(clean) }.getOrNull()
        if (device == null) {
            _status.value = LinkStatus(LinkState.FAILED, "Invalid MAC address", clean)
            return
        }

        _status.update { it.copy(state = LinkState.CONNECTING, detail = "Connecting…", address = clean) }
        CutebotController.resetBuffer()
        queue.reset()
        device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    /** Connects to a robot handed back by [RobotScanner], the reliable path. */
    fun connect(device: BluetoothDevice) {
        preferredDevice = device
        connect(device.address, userInitiated = true)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        intentionalDisconnect = true
        autoReconnect = false
        queue.sendNow("S")
        queue.stop()
        closeGatt()
        _status.update { it.copy(state = LinkState.IDLE, detail = "Disconnected", stale = false) }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    private fun ingest(packet: CutebotTelemetry) {
        val now = System.currentTimeMillis()
        _telemetry.update { current ->
            val next = when (packet) {
                is CutebotTelemetry.Distance -> current.copy(distanceCm = packet.cm)
                is CutebotTelemetry.LineTracker -> current.copy(lineCode = packet.code)
                is CutebotTelemetry.Acceleration ->
                    current.copy(accelX = packet.x, accelY = packet.y, accelZ = packet.z)
                is CutebotTelemetry.LightLevel -> current.copy(lightLevel = packet.level)
                is CutebotTelemetry.Temperature -> current.copy(temperatureC = packet.celsius)
                is CutebotTelemetry.Pong -> current.copy(
                    latencyMs = if (pingSentAt > 0) now - pingSentAt else current.latencyMs
                )
                is CutebotTelemetry.Compass -> current // vertical mount makes this unreliable
                is CutebotTelemetry.Raw -> current
            }
            next.copy(packetsReceived = current.packetsReceived + 1, lastPacketAt = now)
        }
        if (_status.value.stale) _status.update { it.copy(stale = false) }
        if (packet is CutebotTelemetry.LineTracker) onLineCode?.invoke(packet.code)
    }

    /** Flags the link as stale when a ready connection stops producing telemetry. */
    private suspend fun watchFreshness() {
        while (true) {
            delay(FRESHNESS_POLL_MS)
            val status = _status.value
            if (!status.isReady) continue
            val lastAt = _telemetry.value.lastPacketAt
            if (lastAt == 0L) continue
            val stale = System.currentTimeMillis() - lastAt > STALE_AFTER_MS
            if (stale != status.stale) _status.update { it.copy(stale = stale) }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    _status.update {
                        it.copy(state = LinkState.DISCOVERING, detail = "Discovering services…")
                    }
                    g.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val now = System.currentTimeMillis()
                    val wasReady = _status.value.isReady
                    val brownout = wasReady &&
                        lastDisconnectAt > 0L &&
                        now - lastDisconnectAt < BROWNOUT_WINDOW_MS
                    lastDisconnectAt = now

                    queue.stop()
                    queue.reset()
                    CutebotController.resetBuffer()
                    closeGatt()

                    if (intentionalDisconnect || !autoReconnect) {
                        _status.update {
                            it.copy(state = LinkState.IDLE, detail = "Disconnected", stale = false)
                        }
                    } else {
                        _status.update {
                            it.copy(
                                state = LinkState.RECONNECTING,
                                // A link that was never established is a robot that is off
                                // or out of range, not a dropout — say so plainly.
                                detail = if (wasReady) {
                                    "Link lost — reconnecting…"
                                } else {
                                    "No answer — is the robot powered on? Retrying…"
                                },
                                brownoutSuspected = it.brownoutSuspected || brownout,
                                stale = false,
                            )
                        }
                        scheduleReconnect()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _status.update { it.copy(state = LinkState.FAILED, detail = "Service discovery failed") }
                return
            }
            gatt = g
            CutebotController.enableNotifications(g, true)
            _status.update {
                it.copy(state = LinkState.READY, detail = "Ready to drive", reconnectAttempt = 0)
            }
            queue.start()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            CutebotController.handleNotification(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { CutebotController.handleNotification(it) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "CCCD write for ${descriptor.uuid}: status=$status")
        }
    }

    private fun scheduleReconnect() {
        val attempt = _status.value.reconnectAttempt + 1
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            _status.update {
                it.copy(
                state = LinkState.FAILED,
                detail = "Gave up after $MAX_RECONNECT_ATTEMPTS tries — check the robot is on, in range and has fresh batteries",
            )
            }
            return
        }
        _status.update { it.copy(reconnectAttempt = attempt) }
        scope.launch {
            delay(RECONNECT_BACKOFF_MS * attempt)
            val address = _status.value.address
            if (address.isNotEmpty() && autoReconnect) connect(address, userInitiated = false)
        }
    }

    fun clearBrownoutWarning() {
        _status.update { it.copy(brownoutSuspected = false) }
    }

    companion object {
        const val BROWNOUT_WINDOW_MS = 3_000L
        const val STALE_AFTER_MS = 1_500L
        private const val FRESHNESS_POLL_MS = 400L
        private const val RECONNECT_BACKOFF_MS = 600L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }
}
