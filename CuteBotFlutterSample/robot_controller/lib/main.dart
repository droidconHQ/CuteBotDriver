import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'flutterapi.dart'; 

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Robot Controller',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const RobotControllerScreen(),
    );
  }
}

class RobotControllerScreen extends StatefulWidget {
  const RobotControllerScreen({Key? key}) : super(key: key);

  @override
  State<RobotControllerScreen> createState() => _RobotControllerScreenState();
}

class _RobotControllerScreenState extends State<RobotControllerScreen> {
  BluetoothDevice? activeDevice;
  CutebotController? controller;
  String connectionStatus = "Disconnected";
  
  // MAC address of the robot you are connecting to
  final String deviceAddress = "FF:1C:0A:C8:87:BE"; 

  void connectToRobot() async {
    // 1. Request Bluetooth permissions at runtime
    Map<Permission, PermissionStatus> statuses = await [
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
    ].request();

    // 2. Check if permission was granted
    if (statuses[Permission.bluetoothConnect] != PermissionStatus.granted) {
      setState(() {
        connectionStatus = "Permission Denied!";
      });
      return; // Stop here if user said no
    }

    // 3. If granted, proceed with connection
    setState(() {
      connectionStatus = "Connecting...";
    });
    
    try {
      BluetoothDevice device = BluetoothDevice.fromId(deviceAddress);
      
      // Attempt the direct connection
      await device.connect(autoConnect: false);
      
      setState(() {
        connectionStatus = "Connected! Discovering services...";
        activeDevice = device;
      });

      // Initialize our custom controller wrapper to find the UART characteristics
      controller = CutebotController(device);
      await controller!.init();

      setState(() {
        connectionStatus = "Ready to Drive";
      });

      // Listen for unexpected disconnects to reset the UI
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
      print("BLE Connection Error: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  "droidcon Robot Controller",
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 8),
                Text(
                  "Status: $connectionStatus",
                  style: TextStyle(
                    color: activeDevice != null ? Colors.green : Colors.red,
                    fontSize: 16,
                  ),
                ),
                const SizedBox(height: 32),
                
                // Connection Button
                ElevatedButton(
                  onPressed: connectToRobot,
                  child: const Text("Connect to Robot"),
                ),
                
                const SizedBox(height: 48),
                
                // D-Pad Control Grid
                _buildGridControls(),
                
                const SizedBox(height: 48),
                
                // Action Buttons Row 1
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.red,
                        foregroundColor: Colors.white,
                      ),
                      onPressed: () => controller?.setHeadlights(255, 0, 0),
                      child: const Text("Red Lights"),
                    ),
                    const SizedBox(width: 16),
                    ElevatedButton(
                      onPressed: () => controller?.turnLightsOff(),
                      child: const Text("Light Off"),
                    ),
                    const SizedBox(width: 16),
                    ElevatedButton(
                      onPressed: () => controller?.setUnderglow(255, 0, 0),
                      child: const Text("UnderGlow"),
                    ),
                  ],
                ),
                
                const SizedBox(height: 16),
                
                // Action Buttons Row 2
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    ElevatedButton(
                      onPressed: () => controller?.setLeftMotor(50),
                      child: const Text("Left"),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildGridControls() {
    return Column(
      children: [
        SizedBox(
          width: 80,
          height: 80,
          child: ElevatedButton(
            onPressed: () => controller?.moveForward(),
            child: const Text("W"),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(
              width: 80,
              height: 80,
              child: ElevatedButton(
                onPressed: () => controller?.turnLeft(),
                child: const Text("A"),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(
              width: 80,
              height: 80,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.grey,
                  foregroundColor: Colors.white,
                ),
                onPressed: () => controller?.stop(),
                child: const Text("STOP"),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(
              width: 80,
              height: 80,
              child: ElevatedButton(
                onPressed: () => controller?.turnRight(),
                child: const Text("D"),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        SizedBox(
          width: 80,
          height: 80,
          child: ElevatedButton(
            onPressed: () => controller?.moveBackward(),
            child: const Text("S"),
          ),
        ),
      ],
    );
  }
}