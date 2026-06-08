import 'dart:convert';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

class CutebotController {
  static const String uartServiceUuid = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
  // Updated to point to the 0003 characteristic to match the micro:bit firmware bug
  static const String uartTxUuid = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"; 

  final BluetoothDevice device;
  BluetoothCharacteristic? _txCharacteristic;

  CutebotController(this.device);

  Future<void> init() async {
    List<BluetoothService> services = await device.discoverServices();
    for (var service in services) {
      if (service.uuid.toString().toLowerCase() == uartServiceUuid) {
        for (var characteristic in service.characteristics) {
          if (characteristic.uuid.toString().toLowerCase() == uartTxUuid) {
            _txCharacteristic = characteristic;
            break;
          }
        }
      }
    }
  }

  Future<void> _sendCommand(String command) async {
    if (_txCharacteristic == null) {
      print("BLE: Failed to find TX Characteristic!");
      return;
    }
    
    print("BLE: Transmitting: $command");
    // Append the strict hash delimiter just like the Kotlin payload
    final bytes = utf8.encode("$command#");
    await _txCharacteristic!.write(bytes, withoutResponse: true);
  }

  // --- API Commands ---

  // Movement
  Future<void> moveForward() => _sendCommand("F");
  Future<void> moveBackward() => _sendCommand("B");
  Future<void> turnLeft() => _sendCommand("L");
  Future<void> turnRight() => _sendCommand("R");
  Future<void> stop() => _sendCommand("S");

  // Fine-grained motor control (-100 to 100)
  Future<void> setLeftMotor(int speed) => _sendCommand("ML,$speed");
  Future<void> setRightMotor(int speed) => _sendCommand("MR,$speed");

  // Lights (RGB: 0 to 255)
  Future<void> setHeadlights(int r, int g, int b) => _sendCommand("HL,$r,$g,$b");
  Future<void> turnLightsOff() => _sendCommand("HO");
  Future<void> setUnderglow(int r, int g, int b) => _sendCommand("UG,$r,$g,$b");

  // Sensor Requests
  Future<void> requestDistance() => _sendCommand("?DIST");
  Future<void> requestLineStatus() => _sendCommand("?LINE");
}