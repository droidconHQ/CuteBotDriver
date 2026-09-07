import 'dart:async';
import 'dart:convert';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

// ==========================================
// Strongly-Typed Telemetry Data Models
// ==========================================
sealed class CutebotTelemetry {
  const CutebotTelemetry();
}

class DistanceTelemetry extends CutebotTelemetry {
  final int cm;
  const DistanceTelemetry(this.cm);
}

class LineTelemetry extends CutebotTelemetry {
  final int code;
  final String description;
  const LineTelemetry(this.code, this.description);
}

class CompassTelemetry extends CutebotTelemetry {
  final int degrees;
  const CompassTelemetry(this.degrees);
}

class AccelerationTelemetry extends CutebotTelemetry {
  final int x;
  final int y;
  final int z;
  const AccelerationTelemetry(this.x, this.y, this.z);
}

class LightTelemetry extends CutebotTelemetry {
  final int level;
  const LightTelemetry(this.level);
}

class TemperatureTelemetry extends CutebotTelemetry {
  final int celsius;
  const TemperatureTelemetry(this.celsius);
}

class PongTelemetry extends CutebotTelemetry {
  const PongTelemetry();
}

class RawTelemetry extends CutebotTelemetry {
  final String raw;
  const RawTelemetry(this.raw);
}

// ==========================================
// Cutebot Controller Class
// ==========================================
class CutebotController {
  static const String uartServiceUuid = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
  static const String uartRxUuid = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"; // Phone writes here
  static const String uartTxUuid = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"; // Phone receives notifications here

  final BluetoothDevice device;
  BluetoothCharacteristic? _writeCharacteristic;
  BluetoothCharacteristic? _notifyCharacteristic;
  StreamSubscription<List<int>>? _notifySubscription;

  final StreamController<CutebotTelemetry> _telemetryController =
      StreamController<CutebotTelemetry>.broadcast();
  Stream<CutebotTelemetry> get telemetryStream => _telemetryController.stream;

  String _rxBuffer = "";

  CutebotController(this.device);

  Future<void> init() async {
    List<BluetoothService> services = await device.discoverServices();
    for (var service in services) {
      if (service.uuid.toString().toLowerCase() == uartServiceUuid) {
        for (var characteristic in service.characteristics) {
          final uuid = characteristic.uuid.toString().toLowerCase();
          if (uuid == uartRxUuid) {
            _writeCharacteristic = characteristic;
          } else if (uuid == uartTxUuid) {
            _notifyCharacteristic = characteristic;
          }
        }
      }
    }

    if (_notifyCharacteristic != null) {
      try {
        await _notifyCharacteristic!.setNotifyValue(true);
        _notifySubscription = _notifyCharacteristic!.onValueReceived.listen((bytes) {
          _rxBuffer += utf8.decode(bytes, allowMalformed: true);
          while (_rxBuffer.contains("#")) {
            final parts = _rxBuffer.split("#");
            final packet = parts[0].trim();
            _rxBuffer = parts.sublist(1).join("#");
            if (packet.isNotEmpty) {
              final telemetry = _parsePacket(packet);
              _telemetryController.add(telemetry);
            }
          }
        });
      } catch (_) {
        // Notification subscription error
      }
    }
  }

  CutebotTelemetry _parsePacket(String packet) {
    final clean = packet.trim();
    if (clean.startsWith("DIST:")) {
      final cm = int.tryParse(clean.substring(5)) ?? 0;
      return DistanceTelemetry(cm);
    } else if (clean.startsWith("LINE:")) {
      final code = int.tryParse(clean.substring(5)) ?? 0;
      final desc = switch (code) {
        0 => "White (0)",
        1 => "Right Black (1)",
        2 => "Left Black (2)",
        3 => "Both Black (3)",
        _ => "Unknown ($code)",
      };
      return LineTelemetry(code, desc);
    } else if (clean.startsWith("COMPASS:")) {
      final deg = int.tryParse(clean.substring(8)) ?? 0;
      return CompassTelemetry(deg);
    } else if (clean.startsWith("ACCEL:")) {
      final parts = clean.substring(6).split(",");
      final x = parts.isNotEmpty ? int.tryParse(parts[0]) ?? 0 : 0;
      final y = parts.length > 1 ? int.tryParse(parts[1]) ?? 0 : 0;
      final z = parts.length > 2 ? int.tryParse(parts[2]) ?? 0 : 0;
      return AccelerationTelemetry(x, y, z);
    } else if (clean.startsWith("LIGHT:")) {
      final level = int.tryParse(clean.substring(6)) ?? 0;
      return LightTelemetry(level);
    } else if (clean.startsWith("TEMP:")) {
      final temp = int.tryParse(clean.substring(5)) ?? 0;
      return TemperatureTelemetry(temp);
    } else if (clean.startsWith("PONG")) {
      return const PongTelemetry();
    }
    return RawTelemetry(clean);
  }

  Future<void> dispose() async {
    await _notifySubscription?.cancel();
    await _telemetryController.close();
  }

  Future<void> sendRawCommand(String command) async {
    if (_writeCharacteristic == null) {
      return;
    }
    final bytes = utf8.encode("$command#");
    await _writeCharacteristic!.write(bytes, withoutResponse: true);
  }

  // --- Movement Controls ---
  Future<void> moveForward([int speed = 50]) =>
      speed == 50 ? sendRawCommand("F") : sendRawCommand("F,$speed");
  Future<void> moveBackward([int speed = 50]) =>
      speed == 50 ? sendRawCommand("B") : sendRawCommand("B,$speed");
  Future<void> turnLeft([int speed = 50]) =>
      speed == 50 ? sendRawCommand("L") : sendRawCommand("L,$speed");
  Future<void> turnRight([int speed = 50]) =>
      speed == 50 ? sendRawCommand("R") : sendRawCommand("R,$speed");
  Future<void> stop() => sendRawCommand("S");

  // Fine-grained motor control (-100 to 100)
  Future<void> setLeftMotor(int speed) => sendRawCommand("ML,$speed");
  Future<void> setRightMotor(int speed) => sendRawCommand("MR,$speed");
  Future<void> setMotorSpeeds(int left, int right) => sendRawCommand("MS,$left,$right");

  // --- Headlights & Underglow (RGB: 0 to 255) ---
  Future<void> setHeadlights(int r, int g, int b) => sendRawCommand("HL,$r,$g,$b");
  Future<void> setLeftHeadlight(int r, int g, int b) => sendRawCommand("HLL,$r,$g,$b");
  Future<void> setRightHeadlight(int r, int g, int b) => sendRawCommand("HLR,$r,$g,$b");
  Future<void> turnLightsOff() => sendRawCommand("HO");

  Future<void> setUnderglow(int r, int g, int b) => sendRawCommand("UG,$r,$g,$b");
  Future<void> setLeftUnderglow(int r, int g, int b) => sendRawCommand("UGL,$r,$g,$b");
  Future<void> setRightUnderglow(int r, int g, int b) => sendRawCommand("UGR,$r,$g,$b");
  Future<void> turnUnderglowOff() => sendRawCommand("UGO");

  // --- Audio & Buzzer Controls ---
  Future<void> playHorn() => sendRawCommand("HORN");
  Future<void> playBeep() => sendRawCommand("BEEP");
  Future<void> playTone(int frequency, int durationMs) => sendRawCommand("TONE,$frequency,$durationMs");
  Future<void> stopSound() => sendRawCommand("QUIET");

  // --- micro:bit 5x5 LED Display Controls ---
  Future<void> displayText(String text) => sendRawCommand("DISP,$text");
  Future<void> displayIcon(String iconName) => sendRawCommand("ICON,$iconName");
  Future<void> clearDisplay() => sendRawCommand("CLS");

  // --- Sensor Requests (Responses arrive via telemetryStream) ---
  Future<void> requestDistance() => sendRawCommand("?DIST");
  Future<void> requestLineStatus() => sendRawCommand("?LINE");
  Future<void> requestCompass() => sendRawCommand("?COMPASS");
  Future<void> requestAcceleration() => sendRawCommand("?ACCEL");
  Future<void> requestLightLevel() => sendRawCommand("?LIGHT");
  Future<void> requestTemperature() => sendRawCommand("?TEMP");

  // --- Diagnostics / Ping ---
  Future<void> ping() => sendRawCommand("PING");
}