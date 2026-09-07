package com.example.cutebotandroidsample

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private var activeGatt: BluetoothGatt? by mutableStateOf(null)
    private var connectionStatus by mutableStateOf("Disconnected")

    // MAC address of the robot you are connecting to
    var deviceAddress by mutableStateOf("FF:1C:0A:C8:87:BE")

    // Live Sensor Telemetry State (Updated via CutebotController.onTelemetry)
    private var telemetryDistance by mutableStateOf("--")
    private var telemetryLine by mutableStateOf("--")
    private var telemetryCompass by mutableStateOf("--")
    private var telemetryAccel by mutableStateOf("--")
    private var telemetryLight by mutableStateOf("--")
    private var telemetryTemp by mutableStateOf("--")
    private var telemetryPing by mutableStateOf("--")

    // The BLE Callback to handle connection state and delegate sensor notifications to the API
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionStatus = "Connected! Discovering services..."
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionStatus = "Disconnected"
                activeGatt = null
                CutebotController.resetBuffer()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectionStatus = "Ready to Drive"
                activeGatt = gatt
                // Automatically subscribe to TX characteristic notifications for telemetry
                CutebotController.enableNotifications(gatt, true)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            CutebotController.handleNotification(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { CutebotController.handleNotification(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup telemetry listener from CutebotController API
        CutebotController.onTelemetry = { telemetry ->
            when (telemetry) {
                is CutebotTelemetry.Distance -> telemetryDistance = "${telemetry.cm} cm"
                is CutebotTelemetry.LineTracker -> telemetryLine = telemetry.description
                is CutebotTelemetry.Compass -> telemetryCompass = "${telemetry.degrees}°"
                is CutebotTelemetry.Acceleration -> telemetryAccel = "${telemetry.x}, ${telemetry.y}, ${telemetry.z}"
                is CutebotTelemetry.LightLevel -> telemetryLight = "${telemetry.level}"
                is CutebotTelemetry.Temperature -> telemetryTemp = "${telemetry.celsius}°C"
                is CutebotTelemetry.Pong -> telemetryPing = "PONG"
                is CutebotTelemetry.Raw -> {}
            }
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Permissions callback */ }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            )
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RobotControllerScreen()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToRobot(address: String) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val device = adapter.getRemoteDevice(address)

        connectionStatus = "Connecting..."
        device.connectGatt(this, false, gattCallback)
    }

    @Composable
    fun RobotControllerScreen() {
        var driveSpeed by remember { mutableFloatStateOf(60f) }
        val isConnected = activeGatt != null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cutebot Robot Controller",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Status: $connectionStatus",
                color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Card
            OutlinedTextField(
                value = deviceAddress,
                onValueChange = { deviceAddress = it },
                label = { Text("Robot MAC Address") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { connectToRobot(deviceAddress) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isConnected) "Reconnect to Robot" else "Connect to Robot")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Speed Slider
            Text(
                text = "Speed: ${driveSpeed.toInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = driveSpeed,
                onValueChange = { driveSpeed = it },
                valueRange = 20f..100f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // D-Pad Drive Controls
            Text("Movement Controls", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            GridControls(speed = driveSpeed.toInt())

            Spacer(modifier = Modifier.height(24.dp))

            // Lighting & Signals
            SectionHeader(title = "Headlights & Underglow")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { activeGatt?.let { CutebotController.setHeadlights(it, 255, 0, 0) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Red Both") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.setLeftHeadlight(it, 255, 165, 0) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) { Text("Turn L") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.setRightHeadlight(it, 255, 165, 0) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) { Text("Turn R") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { activeGatt?.let { CutebotController.setUnderglow(it, 0, 255, 0) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("Underglow Green") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.turnLightsOff(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) { Text("Lights Off") }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio & Horn
            SectionHeader(title = "Audio & Horn")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { activeGatt?.let { CutebotController.playHorn(it) } }
                ) { Text("Horn") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.playBeep(it) } }
                ) { Text("Beep") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.playTone(it, 1000, 300) } }
                ) { Text("Tone 1kHz") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.stopSound(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) { Text("Quiet") }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // micro:bit 5x5 LED Display
            SectionHeader(title = "micro:bit 5x5 LED Display")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { activeGatt?.let { CutebotController.displayText(it, "NEXT") } }) {
                    Text("Text 'NEXT'")
                }
                Button(onClick = { activeGatt?.let { CutebotController.displayIcon(it, "HEART") } }) {
                    Text("Heart")
                }
                Button(onClick = { activeGatt?.let { CutebotController.displayIcon(it, "SKULL") } }) {
                    Text("Skull")
                }
                Button(onClick = { activeGatt?.let { CutebotController.clearDisplay(it) } }) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sensor Telemetry & Diagnostics
            SectionHeader(title = "Sensor Telemetry & Diagnostics")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { activeGatt?.let { CutebotController.requestDistance(it) } }) {
                    Text("?Dist")
                }
                Button(onClick = { activeGatt?.let { CutebotController.requestLineStatus(it) } }) {
                    Text("?Line")
                }
                Button(onClick = { activeGatt?.let { CutebotController.requestCompass(it) } }) {
                    Text("?Heading")
                }
                Button(onClick = { activeGatt?.let { CutebotController.ping(it) } }) {
                    Text("Ping")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { activeGatt?.let { CutebotController.requestAcceleration(it) } }) {
                    Text("?Accel")
                }
                Button(onClick = { activeGatt?.let { CutebotController.requestLightLevel(it) } }) {
                    Text("?Light")
                }
                Button(onClick = { activeGatt?.let { CutebotController.requestTemperature(it) } }) {
                    Text("?Temp")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Telemetry Readout Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live Telemetry Readings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryRow(label = "Distance:", value = telemetryDistance)
                    TelemetryRow(label = "Line Tracker:", value = telemetryLine)
                    TelemetryRow(label = "Compass Heading:", value = telemetryCompass)
                    TelemetryRow(label = "Accelerometer (X,Y,Z):", value = telemetryAccel)
                    TelemetryRow(label = "Ambient Light:", value = telemetryLight)
                    TelemetryRow(label = "Temperature:", value = telemetryTemp)
                    TelemetryRow(label = "Ping / Pong:", value = telemetryPing)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    @Composable
    fun SectionHeader(title: String) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    }

    @Composable
    fun TelemetryRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    fun GridControls(speed: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { activeGatt?.let { CutebotController.moveForward(it, speed) } },
                modifier = Modifier.size(76.dp)
            ) { Text("W") }

            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeGatt?.let { CutebotController.turnLeft(it, speed) } },
                    modifier = Modifier.size(76.dp)
                ) { Text("A") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.stop(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.size(76.dp)
                ) { Text("STOP") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.turnRight(it, speed) } },
                    modifier = Modifier.size(76.dp)
                ) { Text("D") }
            }

            Button(
                onClick = { activeGatt?.let { CutebotController.moveBackward(it, speed) } },
                modifier = Modifier.size(76.dp)
            ) { Text("S") }
        }
    }
}