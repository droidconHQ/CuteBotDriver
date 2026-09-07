import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'flutterapi.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Cutebot Controller',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const RobotControllerScreen(),
    );
  }
}

class RobotControllerScreen extends StatefulWidget {
  const RobotControllerScreen({super.key});

  @override
  State<RobotControllerScreen> createState() => _RobotControllerScreenState();
}

class _RobotControllerScreenState extends State<RobotControllerScreen> {
  BluetoothDevice? activeDevice;
  CutebotController? controller;
  String connectionStatus = "Disconnected";
  StreamSubscription<CutebotTelemetry>? _telemetrySub;

  // MAC address of the robot you are connecting to
  final TextEditingController _addressController =
      TextEditingController(text: "FF:1C:0A:C8:87:BE");

  double driveSpeed = 60;

  // Live Telemetry State (Updated via CutebotController.telemetryStream)
  String distReading = "--";
  String lineReading = "--";
  String compassReading = "--";
  String accelReading = "--";
  String lightReading = "--";
  String tempReading = "--";
  String pingReading = "--";

  @override
  void dispose() {
    _telemetrySub?.cancel();
    controller?.dispose();
    _addressController.dispose();
    super.dispose();
  }

  void _handleTelemetry(CutebotTelemetry event) {
    setState(() {
      switch (event) {
        case DistanceTelemetry(:final cm):
          distReading = "$cm cm";
        case LineTelemetry(:final description):
          lineReading = description;
        case CompassTelemetry(:final degrees):
          compassReading = "$degrees°";
        case AccelerationTelemetry(:final x, :final y, :final z):
          accelReading = "$x, $y, $z";
        case LightTelemetry(:final level):
          lightReading = "$level";
        case TemperatureTelemetry(:final celsius):
          tempReading = "$celsius°C";
        case PongTelemetry():
          pingReading = "PONG";
        case RawTelemetry():
          break;
      }
    });
  }

  void connectToRobot() async {
    // 1. Request Bluetooth permissions at runtime
    Map<Permission, PermissionStatus> statuses = await [
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
    ].request();

    if (statuses[Permission.bluetoothConnect] != PermissionStatus.granted) {
      setState(() {
        connectionStatus = "Permission Denied!";
      });
      return;
    }

    setState(() {
      connectionStatus = "Connecting...";
    });

    try {
      final deviceAddress = _addressController.text.trim();
      BluetoothDevice device = BluetoothDevice.fromId(deviceAddress);

      await device.connect(autoConnect: false);

      setState(() {
        connectionStatus = "Discovering services...";
        activeDevice = device;
      });

      controller = CutebotController(device);
      await controller!.init();

      // Listen for strongly-typed telemetry from CutebotController API
      _telemetrySub?.cancel();
      _telemetrySub = controller!.telemetryStream.listen(_handleTelemetry);

      setState(() {
        connectionStatus = "Ready to Drive";
      });

      device.connectionState.listen((BluetoothConnectionState state) {
        if (state == BluetoothConnectionState.disconnected) {
          if (mounted) {
            setState(() {
              connectionStatus = "Disconnected";
              activeDevice = null;
              controller = null;
            });
          }
        }
      });
    } catch (e) {
      setState(() {
        connectionStatus = "Connection Failed";
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isConnected = activeDevice != null;

    return Scaffold(
      appBar: AppBar(
        title: const Text("Cutebot Robot Controller"),
        centerTitle: true,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Connection Status & Controls
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    isConnected ? Icons.bluetooth_connected : Icons.bluetooth_disabled,
                    color: isConnected ? Colors.green : Colors.red,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    "Status: $connectionStatus",
                    style: TextStyle(
                      color: isConnected ? Colors.green[800] : Colors.red[800],
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _addressController,
                decoration: const InputDecoration(
                  labelText: "Robot MAC Address",
                  border: OutlineInputBorder(),
                  contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                ),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: connectToRobot,
                  icon: const Icon(Icons.link),
                  label: Text(isConnected ? "Reconnect" : "Connect to Robot"),
                ),
              ),
              const Divider(height: 32),

              // Drive Speed Slider
              Text(
                "Motor Speed: ${driveSpeed.toInt()}%",
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              Slider(
                value: driveSpeed,
                min: 20,
                max: 100,
                divisions: 8,
                label: "${driveSpeed.toInt()}%",
                onChanged: (val) => setState(() => driveSpeed = val),
              ),
              const SizedBox(height: 8),

              // D-Pad Grid
              _buildGridControls(driveSpeed.toInt()),
              const Divider(height: 32),

              // Headlights & Underglow
              _buildSectionTitle("Headlights & Underglow"),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
                    onPressed: () => controller?.setHeadlights(255, 0, 0),
                    child: const Text("Red Both"),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.orange, foregroundColor: Colors.white),
                    onPressed: () => controller?.setLeftHeadlight(255, 165, 0),
                    child: const Text("Turn L"),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.orange, foregroundColor: Colors.white),
                    onPressed: () => controller?.setRightHeadlight(255, 165, 0),
                    child: const Text("Turn R"),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.green, foregroundColor: Colors.white),
                    onPressed: () => controller?.setUnderglow(0, 255, 0),
                    child: const Text("Underglow Green"),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.grey[800], foregroundColor: Colors.white),
                    onPressed: () => controller?.turnLightsOff(),
                    child: const Text("Lights Off"),
                  ),
                ],
              ),
              const Divider(height: 32),

              // Audio & Horn
              _buildSectionTitle("Audio & Horn"),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  ElevatedButton.icon(
                    icon: const Icon(Icons.volume_up),
                    label: const Text("Horn"),
                    onPressed: () => controller?.playHorn(),
                  ),
                  ElevatedButton.icon(
                    icon: const Icon(Icons.notifications_active),
                    label: const Text("Beep"),
                    onPressed: () => controller?.playBeep(),
                  ),
                  ElevatedButton.icon(
                    icon: const Icon(Icons.music_note),
                    label: const Text("Tone 1kHz"),
                    onPressed: () => controller?.playTone(1000, 300),
                  ),
                  ElevatedButton.icon(
                    icon: const Icon(Icons.volume_off),
                    label: const Text("Quiet"),
                    onPressed: () => controller?.stopSound(),
                  ),
                ],
              ),
              const Divider(height: 32),

              // micro:bit 5x5 Display
              _buildSectionTitle("micro:bit 5x5 LED Display"),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  ElevatedButton(
                    onPressed: () => controller?.displayText("NEXT"),
                    child: const Text("Text 'NEXT'"),
                  ),
                  ElevatedButton(
                    onPressed: () => controller?.displayIcon("HEART"),
                    child: const Text("Heart"),
                  ),
                  ElevatedButton(
                    onPressed: () => controller?.displayIcon("SKULL"),
                    child: const Text("Skull"),
                  ),
                  ElevatedButton(
                    onPressed: () => controller?.clearDisplay(),
                    child: const Text("Clear"),
                  ),
                ],
              ),
              const Divider(height: 32),

              // Sensors & Diagnostics
              _buildSectionTitle("Sensor Telemetry & Diagnostics"),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  ElevatedButton(onPressed: () => controller?.requestDistance(), child: const Text("?Dist")),
                  ElevatedButton(onPressed: () => controller?.requestLineStatus(), child: const Text("?Line")),
                  ElevatedButton(onPressed: () => controller?.requestCompass(), child: const Text("?Heading")),
                  ElevatedButton(onPressed: () => controller?.requestAcceleration(), child: const Text("?Accel")),
                  ElevatedButton(onPressed: () => controller?.requestLightLevel(), child: const Text("?Light")),
                  ElevatedButton(onPressed: () => controller?.requestTemperature(), child: const Text("?Temp")),
                  ElevatedButton(onPressed: () => controller?.ping(), child: const Text("Ping")),
                ],
              ),
              const SizedBox(height: 12),

              // Telemetry Readout Card
              Card(
                elevation: 2,
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      const Text(
                        "Live Telemetry Readings",
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                      const Divider(),
                      _buildTelemetryRow("Distance:", distReading),
                      _buildTelemetryRow("Line Tracker:", lineReading),
                      _buildTelemetryRow("Compass Heading:", compassReading),
                      _buildTelemetryRow("Accelerometer (X,Y,Z):", accelReading),
                      _buildTelemetryRow("Ambient Light:", lightReading),
                      _buildTelemetryRow("Temperature:", tempReading),
                      _buildTelemetryRow("Ping / Pong:", pingReading),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8.0),
      child: Text(
        title,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
      ),
    );
  }

  Widget _buildTelemetryRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: Colors.grey[700])),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }

  Widget _buildGridControls(int speed) {
    return Column(
      children: [
        SizedBox(
          width: 76,
          height: 76,
          child: ElevatedButton(
            onPressed: () => controller?.moveForward(speed),
            child: const Text("W"),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(
              width: 76,
              height: 76,
              child: ElevatedButton(
                onPressed: () => controller?.turnLeft(speed),
                child: const Text("A"),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(
              width: 76,
              height: 76,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red,
                  foregroundColor: Colors.white,
                ),
                onPressed: () => controller?.stop(),
                child: const Text("STOP"),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(
              width: 76,
              height: 76,
              child: ElevatedButton(
                onPressed: () => controller?.turnRight(speed),
                child: const Text("D"),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        SizedBox(
          width: 76,
          height: 76,
          child: ElevatedButton(
            onPressed: () => controller?.moveBackward(speed),
            child: const Text("S"),
          ),
        ),
      ],
    );
  }
}