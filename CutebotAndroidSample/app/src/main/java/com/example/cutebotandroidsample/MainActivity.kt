package com.example.cutebotandroidsample

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var activeGatt: BluetoothGatt? by mutableStateOf(null)
    private var connectionStatus by mutableStateOf("Disconnected")

    //MAC address of the robot your connecting to
    var deviceAddress = "FF:1C:0A:C8:87:BE"

    // The BLE Callback to handle the connection state
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionStatus = "Connected! Discovering services..."
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionStatus = "Disconnected"
                activeGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectionStatus = "Ready to Drive"
                activeGatt = gatt
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request required BLE permissions on launch
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                // Permissions granted
            }
        }

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
    private fun connectToRobot(deviceAddress: String) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val device = adapter.getRemoteDevice(deviceAddress)

        connectionStatus = "Connecting..."
        device.connectGatt(this, false, gattCallback)
    }

    @Composable
    fun RobotControllerScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "droidcon Robot Controller",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Status: $connectionStatus",
                color = if (activeGatt != null) Color.Green else Color.Red
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Connection Button (Replace with the specific MAC address of your micro:bit)
            Button(onClick = {
                connectToRobot(deviceAddress) }) {
                Text("Connect to Robot")
            }

            Spacer(modifier = Modifier.height(48.dp))

            // D-Pad Control Grid
            GridControls()

            Spacer(modifier = Modifier.height(48.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { activeGatt?.let { CutebotController.setHeadlights(it, 255, 0, 0) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Red Lights")
                }
                Button(onClick = { activeGatt?.let { CutebotController.turnLightsOff(it) } }) {
                    Text("Light Off")
                }
                Button(onClick = { activeGatt?.let { CutebotController.setUnderglow(it, 255, 0, 0) } }) {
                    Text("UnderGlow")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                Button(
                    onClick = { activeGatt?.let { CutebotController.setLeftMotor(it, 50) } },
                ) {
                    Text("Left")
                }
            }
        }
    }

    @Composable
    fun GridControls() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { activeGatt?.let { CutebotController.moveForward(it) } },
                modifier = Modifier.size(80.dp)
            ) { Text("W") }

            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeGatt?.let { CutebotController.turnLeft(it) } },
                    modifier = Modifier.size(80.dp)
                ) { Text("A") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.stop(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.size(80.dp)
                ) { Text("STOP") }

                Button(
                    onClick = { activeGatt?.let { CutebotController.turnRight(it) } },
                    modifier = Modifier.size(80.dp)
                ) { Text("D") }
            }

            Button(
                onClick = { activeGatt?.let { CutebotController.moveBackward(it) } },
                modifier = Modifier.size(80.dp)
            ) { Text("S") }
        }
    }
}