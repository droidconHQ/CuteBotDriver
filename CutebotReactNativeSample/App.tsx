import React, { useState, useEffect, useRef } from 'react';
import {
  SafeAreaView,
  ScrollView,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  PermissionsAndroid,
  Platform,
  Alert,
} from 'react-native';
import { BleManager, Device } from 'react-native-ble-plx';
import { CutebotController, CutebotTelemetry } from './ReactNativeApi';

export default function App(): JSX.Element {
  const bleManagerRef = useRef<BleManager | null>(null);
  const [controller, setController] = useState<CutebotController | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<string>('Disconnected');
  const [isConnected, setIsConnected] = useState<boolean>(false);
  const [driveSpeed, setDriveSpeed] = useState<number>(60);

  // Live Telemetry State
  const [distReading, setDistReading] = useState<string>('--');
  const [lineReading, setLineReading] = useState<string>('--');
  const [compassReading, setCompassReading] = useState<string>('--');
  const [accelReading, setAccelReading] = useState<string>('--');
  const [lightReading, setLightReading] = useState<string>('--');
  const [tempReading, setTempReading] = useState<string>('--');
  const [pingReading, setPingReading] = useState<string>('--');

  useEffect(() => {
    bleManagerRef.current = new BleManager();
    return () => {
      controller?.destroy();
      bleManagerRef.current?.destroy();
    };
  }, []);

  const requestPermissions = async (): Promise<boolean> => {
    if (Platform.OS === 'android') {
      if (Platform.Version >= 31) {
        const result = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        ]);
        return (
          result[PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN] === PermissionsAndroid.RESULTS.GRANTED &&
          result[PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT] === PermissionsAndroid.RESULTS.GRANTED
        );
      } else {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
        );
        return granted === PermissionsAndroid.RESULTS.GRANTED;
      }
    }
    return true;
  };

  const handleTelemetry = (telemetry: CutebotTelemetry) => {
    switch (telemetry.type) {
      case 'distance':
        setDistReading(`${telemetry.cm} cm`);
        break;
      case 'line':
        setLineReading(telemetry.description);
        break;
      case 'compass':
        setCompassReading(`${telemetry.degrees}°`);
        break;
      case 'accel':
        setAccelReading(`${telemetry.x}, ${telemetry.y}, ${telemetry.z}`);
        break;
      case 'light':
        setLightReading(`${telemetry.level}`);
        break;
      case 'temp':
        setTempReading(`${telemetry.celsius}°C`);
        break;
      case 'pong':
        setPingReading('PONG');
        break;
      case 'raw':
        break;
    }
  };

  const scanAndConnect = async () => {
    const hasPermission = await requestPermissions();
    if (!hasPermission) {
      Alert.alert('Permission Denied', 'Bluetooth permissions are required.');
      return;
    }

    setConnectionStatus('Scanning for micro:bit...');
    const manager = bleManagerRef.current;
    if (!manager) return;

    manager.startDeviceScan(
      [CutebotController.UART_SERVICE_UUID],
      null,
      async (error, device) => {
        if (error) {
          setConnectionStatus(`Scan Error: ${error.message}`);
          return;
        }

        if (device && (device.name?.includes('micro:bit') || device.name?.includes('BBC'))) {
          manager.stopDeviceScan();
          setConnectionStatus(`Connecting to ${device.name}...`);

          try {
            const connectedDevice = await device.connect();
            setConnectionStatus('Connected! Discovering services...');

            const ctrl = new CutebotController(connectedDevice);
            await ctrl.init();

            ctrl.onTelemetry = handleTelemetry;

            setController(ctrl);
            setIsConnected(true);
            setConnectionStatus('Ready to Drive');

            connectedDevice.onDisconnected(() => {
              setConnectionStatus('Disconnected');
              setIsConnected(false);
              setController(null);
            });
          } catch (connErr: any) {
            setConnectionStatus(`Connection Failed: ${connErr.message}`);
          }
        }
      }
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>Cutebot Robot Controller</Text>
        <Text
          style={[
            styles.status,
            { color: isConnected ? '#2E7D32' : '#C62828' },
          ]}>
          Status: {connectionStatus}
        </Text>

        {/* Connection Button */}
        <TouchableOpacity
          style={[styles.primaryButton, isConnected && styles.connectedButton]}
          onPress={scanAndConnect}>
          <Text style={styles.primaryButtonText}>
            {isConnected ? 'Reconnect to Robot' : 'Scan & Connect'}
          </Text>
        </TouchableOpacity>

        {/* Speed Selector */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Motor Speed: {driveSpeed}%</Text>
          <View style={styles.speedRow}>
            {[30, 50, 70, 100].map((s) => (
              <TouchableOpacity
                key={s}
                style={[
                  styles.speedButton,
                  driveSpeed === s && styles.speedButtonActive,
                ]}
                onPress={() => setDriveSpeed(s)}>
                <Text
                  style={[
                    styles.speedButtonText,
                    driveSpeed === s && styles.speedButtonTextActive,
                  ]}>
                  {s}%
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Movement Controls (D-Pad) */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Movement Controls</Text>
          <View style={styles.dpadContainer}>
            <TouchableOpacity
              style={styles.dpadButton}
              onPress={() => controller?.moveForward(driveSpeed)}>
              <Text style={styles.dpadText}>W</Text>
            </TouchableOpacity>

            <View style={styles.dpadRow}>
              <TouchableOpacity
                style={styles.dpadButton}
                onPress={() => controller?.turnLeft(driveSpeed)}>
                <Text style={styles.dpadText}>A</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.dpadButton, styles.stopButton]}
                onPress={() => controller?.stop()}>
                <Text style={styles.stopText}>STOP</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.dpadButton}
                onPress={() => controller?.turnRight(driveSpeed)}>
                <Text style={styles.dpadText}>D</Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              style={styles.dpadButton}
              onPress={() => controller?.moveBackward(driveSpeed)}>
              <Text style={styles.dpadText}>S</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Headlights & Underglow */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Headlights & Underglow</Text>
          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: '#D32F2F' }]}
              onPress={() => controller?.setHeadlights(255, 0, 0)}>
              <Text style={styles.buttonText}>Red Both</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: '#F57C00' }]}
              onPress={() => controller?.setLeftHeadlight(255, 165, 0)}>
              <Text style={styles.buttonText}>Turn L</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: '#F57C00' }]}
              onPress={() => controller?.setRightHeadlight(255, 165, 0)}>
              <Text style={styles.buttonText}>Turn R</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: '#388E3C' }]}
              onPress={() => controller?.setUnderglow(0, 255, 0)}>
              <Text style={styles.buttonText}>Underglow Green</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.actionButton, { backgroundColor: '#424242' }]}
              onPress={() => controller?.turnLightsOff()}>
              <Text style={styles.buttonText}>Lights Off</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Audio & Horn */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Audio & Horn</Text>
          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.playHorn()}>
              <Text style={styles.actionButtonText}>Horn</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.playBeep()}>
              <Text style={styles.actionButtonText}>Beep</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.playTone(1000, 300)}>
              <Text style={styles.actionButtonText}>Tone 1kHz</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.stopSound()}>
              <Text style={styles.actionButtonText}>Quiet</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* micro:bit 5x5 LED Display */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>micro:bit 5x5 LED Display</Text>
          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.displayText('NEXT')}>
              <Text style={styles.actionButtonText}>Text 'NEXT'</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.displayIcon('HEART')}>
              <Text style={styles.actionButtonText}>Heart</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.displayIcon('SKULL')}>
              <Text style={styles.actionButtonText}>Skull</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => controller?.clearDisplay()}>
              <Text style={styles.actionButtonText}>Clear</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Sensors & Diagnostics */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Sensor Telemetry & Diagnostics</Text>
          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={styles.queryButton}
              onPress={() => controller?.requestDistance()}>
              <Text style={styles.queryButtonText}>?Dist</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.queryButton}
              onPress={() => controller?.requestLineStatus()}>
              <Text style={styles.queryButtonText}>?Line</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.queryButton}
              onPress={() => controller?.requestCompass()}>
              <Text style={styles.queryButtonText}>?Heading</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.queryButton}
              onPress={() => controller?.ping()}>
              <Text style={styles.queryButtonText}>Ping</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={styles.queryButton}
              onPress={() => controller?.requestAcceleration()}>
              <Text style={styles.queryButtonText}>?Accel</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.queryButton}
              onPress={() => controller?.requestLightLevel()}>
              <Text style={styles.queryButtonText}>?Light</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.queryButton}
              onPress={() => controller?.requestTemperature()}>
              <Text style={styles.queryButtonText}>?Temp</Text>
            </TouchableOpacity>
          </View>

          {/* Telemetry Card */}
          <View style={styles.telemetryCard}>
            <Text style={styles.telemetryTitle}>Live Telemetry Readings</Text>
            <View style={styles.telemetryRow}>
              <Text style={styles.telemetryLabel}>Distance:</Text>
              <Text style={styles.telemetryValue}>{distReading}</Text>
            </View>
            <View style={styles.telemetryRow}>
              <Text style={styles.telemetryLabel}>Line Tracker:</Text>
              <Text style={styles.telemetryValue}>{lineReading}</Text>
            </View>
            <View style={styles.telemetryRow}>
              <Text style={styles.telemetryLabel}>Compass Heading:</Text>
              <Text style={styles.telemetryValue}>{compassReading}</Text>
            </View>
            <View style={styles.telemetryRow}>
              <Text style={styles.telemetryLabel}>Accelerometer (X,Y,Z):</Text>
              <Text style={styles.telemetryValue}>{accelReading}</Text>
            </View>
            <View style={styles.telemetryRow}>
              <Text style={styles.telemetryLabel}>Ambient Light:</Text>
              <Text style={styles.telemetryValue}>{lightReading}</Text>
            </View>
            <View style={styles.telemetryRow}>
              <Text style={styles.telemetryLabel}>Temperature:</Text>
              <Text style={styles.telemetryValue}>{tempReading}</Text>
            </View>
            <View style={styles.telemetryRow}>
              <Text style={styles.telemetryLabel}>Ping / Pong:</Text>
              <Text style={styles.telemetryValue}>{pingReading}</Text>
            </View>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  scrollContent: {
    padding: 16,
    alignItems: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#212121',
  },
  status: {
    fontSize: 16,
    fontWeight: '600',
    marginTop: 4,
    marginBottom: 16,
  },
  primaryButton: {
    backgroundColor: '#6200EE',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 8,
    width: '100%',
    alignItems: 'center',
    marginBottom: 20,
  },
  connectedButton: {
    backgroundColor: '#2E7D32',
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: 'bold',
  },
  section: {
    width: '100%',
    marginBottom: 20,
    alignItems: 'center',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#333333',
    alignSelf: 'flex-start',
  },
  speedRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
  },
  speedButton: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 6,
    backgroundColor: '#E0E0E0',
  },
  speedButtonActive: {
    backgroundColor: '#6200EE',
  },
  speedButtonText: {
    color: '#333333',
    fontWeight: 'bold',
  },
  speedButtonTextActive: {
    color: '#FFFFFF',
  },
  dpadContainer: {
    alignItems: 'center',
    marginVertical: 8,
  },
  dpadRow: {
    flexDirection: 'row',
    marginVertical: 8,
  },
  dpadButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#3F51B5',
    justifyContent: 'center',
    alignItems: 'center',
    marginHorizontal: 8,
  },
  stopButton: {
    backgroundColor: '#D32F2F',
  },
  dpadText: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: 'bold',
  },
  stopText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: 'bold',
  },
  buttonRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    marginVertical: 4,
  },
  actionButton: {
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 6,
    backgroundColor: '#ECEFF1',
    margin: 4,
  },
  actionButtonText: {
    color: '#37474F',
    fontWeight: '600',
  },
  buttonText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  queryButton: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 6,
    backgroundColor: '#E8EAF6',
    margin: 4,
  },
  queryButtonText: {
    color: '#283593',
    fontWeight: '600',
  },
  telemetryCard: {
    width: '100%',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginTop: 12,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  telemetryTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#212121',
  },
  telemetryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 4,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#EEEEEE',
  },
  telemetryLabel: {
    color: '#757575',
  },
  telemetryValue: {
    fontWeight: '600',
    color: '#212121',
  },
});
