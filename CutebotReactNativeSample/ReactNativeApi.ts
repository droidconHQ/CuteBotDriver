// Cutebot Bluetooth Low Energy (BLE) API for React Native
// Using react-native-ble-plx

import type { Device, Subscription } from 'react-native-ble-plx';

// ==========================================
// Strongly-Typed Telemetry Data Models
// ==========================================
export type CutebotTelemetry =
  | { type: 'distance'; cm: number }
  | { type: 'line'; code: number; description: string }
  | { type: 'compass'; degrees: number }
  | { type: 'accel'; x: number; y: number; z: number }
  | { type: 'light'; level: number }
  | { type: 'temp'; celsius: number }
  | { type: 'pong' }
  | { type: 'raw'; text: string };

export interface CutebotTelemetryListener {
  onDistance?: (cm: number) => void;
  onLineStatus?: (code: number, description: string) => void;
  onCompass?: (degrees: number) => void;
  onAcceleration?: (x: number, y: number, z: number) => void;
  onLightLevel?: (level: number) => void;
  onTemperature?: (celsius: number) => void;
  onPong?: () => void;
  onRawTelemetry?: (raw: string) => void;
}

// Inline Base64 Encoders / Decoders for React Native
function base64ToUtf8(base64: string): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  let str = '';
  let buffer = 0,
    bits = 0;
  for (let i = 0; i < base64.length; i++) {
    const idx = chars.indexOf(base64.charAt(i));
    if (idx === -1) continue;
    buffer = (buffer << 6) | idx;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      str += String.fromCharCode((buffer >> bits) & 0xff);
    }
  }
  return str;
}

function utf8ToBase64(str: string): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  let output = '';
  let buffer = 0,
    bits = 0;
  for (let i = 0; i < str.length; i++) {
    buffer = (buffer << 8) | str.charCodeAt(i);
    bits += 8;
    while (bits >= 6) {
      bits -= 6;
      output += chars.charAt((buffer >> bits) & 0x3f);
    }
  }
  if (bits > 0) {
    output += chars.charAt((buffer << (6 - bits)) & 0x3f);
    while (output.length % 4 !== 0) output += '=';
  }
  return output;
}

// ==========================================
// Cutebot Controller Class
// ==========================================
export class CutebotController {
  public static readonly UART_SERVICE_UUID = '6e400001-b5a3-f393-e0a9-e50e24dcca9e';
  public static readonly UART_RX_CHAR_UUID = '6e400003-b5a3-f393-e0a9-e50e24dcca9e'; // Phone writes here
  public static readonly UART_TX_CHAR_UUID = '6e400002-b5a3-f393-e0a9-e50e24dcca9e'; // Phone receives notifications here

  private device: Device;
  private monitorSubscription: Subscription | null = null;
  private rxBuffer = '';

  public onTelemetry?: (telemetry: CutebotTelemetry) => void;
  public telemetryListener?: CutebotTelemetryListener;

  constructor(device: Device) {
    this.device = device;
  }

  /**
   * Initializes the connection by discovering services and monitoring the TX characteristic.
   */
  public async init(): Promise<void> {
    await this.device.discoverAllServicesAndCharacteristics();

    // Subscribe to incoming notifications on TX characteristic
    this.monitorSubscription = this.device.monitorCharacteristicForService(
      CutebotController.UART_SERVICE_UUID,
      CutebotController.UART_TX_CHAR_UUID,
      (error, characteristic) => {
        if (error || !characteristic?.value) {
          return;
        }
        const chunk = base64ToUtf8(characteristic.value);
        this.handleIncomingChunk(chunk);
      }
    );
  }

  /**
   * Cleanup telemetry subscription
   */
  public destroy(): void {
    if (this.monitorSubscription) {
      this.monitorSubscription.remove();
      this.monitorSubscription = null;
    }
    this.rxBuffer = '';
  }

  private handleIncomingChunk(chunk: string): void {
    this.rxBuffer += chunk;
    while (this.rxBuffer.includes('#')) {
      const hashIndex = this.rxBuffer.indexOf('#');
      const packet = this.rxBuffer.slice(0, hashIndex).trim();
      this.rxBuffer = this.rxBuffer.slice(hashIndex + 1);

      if (packet.length > 0) {
        const telemetry = this.parseTelemetryPacket(packet);
        this.dispatchTelemetry(telemetry);
      }
    }
  }

  private parseTelemetryPacket(packet: string): CutebotTelemetry {
    const clean = packet.trim();
    if (clean.startsWith('DIST:')) {
      const cm = parseInt(clean.substring(5), 10) || 0;
      return { type: 'distance', cm };
    } else if (clean.startsWith('LINE:')) {
      const code = parseInt(clean.substring(5), 10) || 0;
      let description: string;
      switch (code) {
        case 0:
          description = 'White (0)';
          break;
        case 1:
          description = 'Right Black (1)';
          break;
        case 2:
          description = 'Left Black (2)';
          break;
        case 3:
          description = 'Both Black (3)';
          break;
        default:
          description = `Unknown (${code})`;
      }
      return { type: 'line', code, description };
    } else if (clean.startsWith('COMPASS:')) {
      const degrees = parseInt(clean.substring(8), 10) || 0;
      return { type: 'compass', degrees };
    } else if (clean.startsWith('ACCEL:')) {
      const coords = clean.substring(6).split(',');
      const x = parseInt(coords[0], 10) || 0;
      const y = parseInt(coords[1], 10) || 0;
      const z = parseInt(coords[2], 10) || 0;
      return { type: 'accel', x, y, z };
    } else if (clean.startsWith('LIGHT:')) {
      const level = parseInt(clean.substring(6), 10) || 0;
      return { type: 'light', level };
    } else if (clean.startsWith('TEMP:')) {
      const celsius = parseInt(clean.substring(5), 10) || 0;
      return { type: 'temp', celsius };
    } else if (clean.startsWith('PONG')) {
      return { type: 'pong' };
    }
    return { type: 'raw', text: clean };
  }

  private dispatchTelemetry(telemetry: CutebotTelemetry): void {
    if (this.onTelemetry) {
      this.onTelemetry(telemetry);
    }
    const l = this.telemetryListener;
    if (!l) return;

    switch (telemetry.type) {
      case 'distance':
        l.onDistance?.(telemetry.cm);
        break;
      case 'line':
        l.onLineStatus?.(telemetry.code, telemetry.description);
        break;
      case 'compass':
        l.onCompass?.(telemetry.degrees);
        break;
      case 'accel':
        l.onAcceleration?.(telemetry.x, telemetry.y, telemetry.z);
        break;
      case 'light':
        l.onLightLevel?.(telemetry.level);
        break;
      case 'temp':
        l.onTemperature?.(telemetry.celsius);
        break;
      case 'pong':
        l.onPong?.();
        break;
      case 'raw':
        l.onRawTelemetry?.(telemetry.text);
        break;
    }
  }

  /**
   * Sends a raw UTF-8 command to the robot terminated by '#'.
   */
  public async sendRawCommand(command: string): Promise<void> {
    const base64Payload = utf8ToBase64(`${command}#`);
    await this.device.writeCharacteristicWithoutResponseForService(
      CutebotController.UART_SERVICE_UUID,
      CutebotController.UART_RX_CHAR_UUID,
      base64Payload
    );
  }

  // ==========================================
  // Movement Controls
  // ==========================================
  public moveForward(speed: number = 50): Promise<void> {
    return speed === 50 ? this.sendRawCommand('F') : this.sendRawCommand(`F,${speed}`);
  }

  public moveBackward(speed: number = 50): Promise<void> {
    return speed === 50 ? this.sendRawCommand('B') : this.sendRawCommand(`B,${speed}`);
  }

  public turnLeft(speed: number = 50): Promise<void> {
    return speed === 50 ? this.sendRawCommand('L') : this.sendRawCommand(`L,${speed}`);
  }

  public turnRight(speed: number = 50): Promise<void> {
    return speed === 50 ? this.sendRawCommand('R') : this.sendRawCommand(`R,${speed}`);
  }

  public stop(): Promise<void> {
    return this.sendRawCommand('S');
  }

  public setLeftMotor(speed: number): Promise<void> {
    return this.sendRawCommand(`ML,${speed}`);
  }

  public setRightMotor(speed: number): Promise<void> {
    return this.sendRawCommand(`MR,${speed}`);
  }

  public setMotorSpeeds(left: number, right: number): Promise<void> {
    return this.sendRawCommand(`MS,${left},${right}`);
  }

  // ==========================================
  // Headlights & Underglow (RGB: 0 to 255)
  // ==========================================
  public setHeadlights(r: number, g: number, b: number): Promise<void> {
    return this.sendRawCommand(`HL,${r},${g},${b}`);
  }

  public setLeftHeadlight(r: number, g: number, b: number): Promise<void> {
    return this.sendRawCommand(`HLL,${r},${g},${b}`);
  }

  public setRightHeadlight(r: number, g: number, b: number): Promise<void> {
    return this.sendRawCommand(`HLR,${r},${g},${b}`);
  }

  public turnLightsOff(): Promise<void> {
    return this.sendRawCommand('HO');
  }

  public setUnderglow(r: number, g: number, b: number): Promise<void> {
    return this.sendRawCommand(`UG,${r},${g},${b}`);
  }

  public setLeftUnderglow(r: number, g: number, b: number): Promise<void> {
    return this.sendRawCommand(`UGL,${r},${g},${b}`);
  }

  public setRightUnderglow(r: number, g: number, b: number): Promise<void> {
    return this.sendRawCommand(`UGR,${r},${g},${b}`);
  }

  public turnUnderglowOff(): Promise<void> {
    return this.sendRawCommand('UGO');
  }

  // ==========================================
  // Audio & Buzzer Controls
  // ==========================================
  public playHorn(): Promise<void> {
    return this.sendRawCommand('HORN');
  }

  public playBeep(): Promise<void> {
    return this.sendRawCommand('BEEP');
  }

  public playTone(frequency: number, durationMs: number): Promise<void> {
    return this.sendRawCommand(`TONE,${frequency},${durationMs}`);
  }

  public stopSound(): Promise<void> {
    return this.sendRawCommand('QUIET');
  }

  // ==========================================
  // micro:bit 5x5 Display Controls
  // ==========================================
  public displayText(text: string): Promise<void> {
    return this.sendRawCommand(`DISP,${text}`);
  }

  public displayIcon(iconName: string): Promise<void> {
    return this.sendRawCommand(`ICON,${iconName}`);
  }

  public clearDisplay(): Promise<void> {
    return this.sendRawCommand('CLS');
  }

  // ==========================================
  // Sensor Requests
  // ==========================================
  public requestDistance(): Promise<void> {
    return this.sendRawCommand('?DIST');
  }

  public requestLineStatus(): Promise<void> {
    return this.sendRawCommand('?LINE');
  }

  public requestCompass(): Promise<void> {
    return this.sendRawCommand('?COMPASS');
  }

  public requestAcceleration(): Promise<void> {
    return this.sendRawCommand('?ACCEL');
  }

  public requestLightLevel(): Promise<void> {
    return this.sendRawCommand('?LIGHT');
  }

  public requestTemperature(): Promise<void> {
    return this.sendRawCommand('?TEMP');
  }

  // ==========================================
  // Diagnostics / Ping
  // ==========================================
  public ping(): Promise<void> {
    return this.sendRawCommand('PING');
  }
}
