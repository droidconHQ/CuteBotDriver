import Foundation
import CoreBluetooth

// ==========================================
// Strongly-Typed Telemetry Data Models
// ==========================================
public enum CutebotTelemetry: Equatable {
    case distance(cm: Int)
    case lineTracker(code: Int, description: String)
    case compass(degrees: Int)
    case acceleration(x: Int, y: Int, z: Int)
    case lightLevel(level: Int)
    case temperature(celsius: Int)
    case pong
    case raw(text: String)
}

// ==========================================
// Cutebot Controller Class (CoreBluetooth)
// ==========================================
public class CutebotController: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    public static let uartServiceUUID = CBUUID(string: "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    public static let uartRxUUID = CBUUID(string: "6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Phone writes here
    public static let uartTxUUID = CBUUID(string: "6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Phone receives notifications here

    @Published public var connectionStatus: String = "Disconnected"
    @Published public var isConnected: Bool = false
    @Published public var discoveredPeripherals: [CBPeripheral] = []
    @Published public var latestTelemetry: CutebotTelemetry?

    public var onTelemetry: ((CutebotTelemetry) -> Void)?

    private var centralManager: CBCentralManager!
    private var targetPeripheral: CBPeripheral?
    private var rxCharacteristic: CBCharacteristic? // Write
    private var txCharacteristic: CBCharacteristic? // Notify
    private var rxBuffer: String = ""

    public override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    // ==========================================
    // Central Manager & Connection
    // ==========================================
    public func startScanning() {
        guard centralManager.state == .poweredOn else { return }
        discoveredPeripherals.removeAll()
        connectionStatus = "Scanning for micro:bit..."
        centralManager.scanForPeripherals(withServices: [CutebotController.uartServiceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    public func stopScanning() {
        centralManager.stopScan()
        if !isConnected {
            connectionStatus = "Scan stopped"
        }
    }

    public func connect(to peripheral: CBPeripheral) {
        stopScanning()
        targetPeripheral = peripheral
        targetPeripheral?.delegate = self
        connectionStatus = "Connecting to \(peripheral.name ?? "micro:bit")..."
        centralManager.connect(peripheral, options: nil)
    }

    public func disconnect() {
        if let targetPeripheral = targetPeripheral {
            centralManager.cancelPeripheralConnection(targetPeripheral)
        }
        resetState()
    }

    private func resetState() {
        rxCharacteristic = nil
        txCharacteristic = nil
        rxBuffer = ""
        isConnected = false
        connectionStatus = "Disconnected"
    }

    // CBCentralManagerDelegate
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            connectionStatus = "Bluetooth Ready"
        case .poweredOff:
            connectionStatus = "Bluetooth is Off"
            resetState()
        case .unauthorized:
            connectionStatus = "Bluetooth Unauthorized"
        case .unsupported:
            connectionStatus = "Bluetooth Unsupported"
        default:
            connectionStatus = "Bluetooth Initializing..."
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        if !discoveredPeripherals.contains(where: { $0.identifier == peripheral.identifier }) {
            discoveredPeripherals.append(peripheral)
        }
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        isConnected = true
        connectionStatus = "Connected! Discovering services..."
        peripheral.discoverServices([CutebotController.uartServiceUUID])
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        resetState()
        connectionStatus = "Connection Failed"
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        resetState()
        connectionStatus = "Disconnected"
    }

    // CBPeripheralDelegate
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services where service.uuid == CutebotController.uartServiceUUID {
            peripheral.discoverCharacteristics([CutebotController.uartRxUUID, CutebotController.uartTxUUID], for: service)
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        for characteristic in characteristics {
            if characteristic.uuid == CutebotController.uartRxUUID {
                rxCharacteristic = characteristic
            } else if characteristic.uuid == CutebotController.uartTxUUID {
                txCharacteristic = characteristic
                // Automatically subscribe to TX notifications
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }
        if rxCharacteristic != nil && txCharacteristic != nil {
            connectionStatus = "Ready to Drive"
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == CutebotController.uartTxUUID,
              let data = characteristic.value,
              let chunk = String(data: data, encoding: .utf8) else {
            return
        }

        rxBuffer += chunk
        while rxBuffer.contains("#") {
            guard let hashIndex = rxBuffer.firstIndex(of: "#") else { break }
            let packet = String(rxBuffer[..<hashIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
            rxBuffer = String(rxBuffer[rxBuffer.index(after: hashIndex)...])

            if !packet.isEmpty {
                let parsed = parseTelemetryPacket(packet)
                DispatchQueue.main.async { [weak self] in
                    self?.latestTelemetry = parsed
                    self?.onTelemetry?(parsed)
                }
            }
        }
    }

    private func parseTelemetryPacket(_ packet: String) -> CutebotTelemetry {
        let clean = packet.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.hasPrefix("DIST:") {
            let cm = Int(clean.dropFirst(5)) ?? 0
            return .distance(cm: cm)
        } else if clean.hasPrefix("LINE:") {
            let code = Int(clean.dropFirst(5)) ?? 0
            let desc: String
            switch code {
            case 0: desc = "White (0)"
            case 1: desc = "Right Black (1)"
            case 2: desc = "Left Black (2)"
            case 3: desc = "Both Black (3)"
            default: desc = "Unknown (\(code))"
            }
            return .lineTracker(code: code, description: desc)
        } else if clean.hasPrefix("COMPASS:") {
            let deg = Int(clean.dropFirst(8)) ?? 0
            return .compass(degrees: deg)
        } else if clean.hasPrefix("ACCEL:") {
            let coords = clean.dropFirst(6).split(separator: ",")
            let x = coords.count > 0 ? Int(coords[0]) ?? 0 : 0
            let y = coords.count > 1 ? Int(coords[1]) ?? 0 : 0
            let z = coords.count > 2 ? Int(coords[2]) ?? 0 : 0
            return .acceleration(x: x, y: y, z: z)
        } else if clean.hasPrefix("LIGHT:") {
            let level = Int(clean.dropFirst(6)) ?? 0
            return .lightLevel(level: level)
        } else if clean.hasPrefix("TEMP:") {
            let temp = Int(clean.dropFirst(5)) ?? 0
            return .temperature(celsius: temp)
        } else if clean.hasPrefix("PONG") {
            return .pong
        }
        return .raw(text: clean)
    }

    // ==========================================
    // Transmission
    // ==========================================
    public func sendRawCommand(_ command: String) {
        guard let targetPeripheral = targetPeripheral,
              let rxCharacteristic = rxCharacteristic,
              let data = "\(command)#".data(using: .utf8) else {
            return
        }
        targetPeripheral.writeValue(data, for: rxCharacteristic, type: .withoutResponse)
    }

    // ==========================================
    // Movement Controls
    // ==========================================
    public func moveForward(speed: Int = 50) {
        speed == 50 ? sendRawCommand("F") : sendRawCommand("F,\(speed)")
    }

    public func moveBackward(speed: Int = 50) {
        speed == 50 ? sendRawCommand("B") : sendRawCommand("B,\(speed)")
    }

    public func turnLeft(speed: Int = 50) {
        speed == 50 ? sendRawCommand("L") : sendRawCommand("L,\(speed)")
    }

    public func turnRight(speed: Int = 50) {
        speed == 50 ? sendRawCommand("R") : sendRawCommand("R,\(speed)")
    }

    public func stop() {
        sendRawCommand("S")
    }

    public func setLeftMotor(speed: Int) {
        sendRawCommand("ML,\(speed)")
    }

    public func setRightMotor(speed: Int) {
        sendRawCommand("MR,\(speed)")
    }

    public func setMotorSpeeds(left: Int, right: Int) {
        sendRawCommand("MS,\(left),\(right)")
    }

    // ==========================================
    // Headlights & Underglow (RGB: 0 to 255)
    // ==========================================
    public func setHeadlights(r: Int, g: Int, b: Int) {
        sendRawCommand("HL,\(r),\(g),\(b)")
    }

    public func setLeftHeadlight(r: Int, g: Int, b: Int) {
        sendRawCommand("HLL,\(r),\(g),\(b)")
    }

    public func setRightHeadlight(r: Int, g: Int, b: Int) {
        sendRawCommand("HLR,\(r),\(g),\(b)")
    }

    public func turnLightsOff() {
        sendRawCommand("HO")
    }

    public func setUnderglow(r: Int, g: Int, b: Int) {
        sendRawCommand("UG,\(r),\(g),\(b)")
    }

    public func setLeftUnderglow(r: Int, g: Int, b: Int) {
        sendRawCommand("UGL,\(r),\(g),\(b)")
    }

    public func setRightUnderglow(r: Int, g: Int, b: Int) {
        sendRawCommand("UGR,\(r),\(g),\(b)")
    }

    public func turnUnderglowOff() {
        sendRawCommand("UGO")
    }

    // ==========================================
    // Audio & Buzzer Controls
    // ==========================================
    public func playHorn() {
        sendRawCommand("HORN")
    }

    public func playBeep() {
        sendRawCommand("BEEP")
    }

    public func playTone(frequency: Int, durationMs: Int) {
        sendRawCommand("TONE,\(frequency),\(durationMs)")
    }

    public func stopSound() {
        sendRawCommand("QUIET")
    }

    // ==========================================
    // micro:bit 5x5 Display Controls
    // ==========================================
    public func displayText(_ text: String) {
        sendRawCommand("DISP,\(text)")
    }

    public func displayIcon(_ iconName: String) {
        sendRawCommand("ICON,\(iconName)")
    }

    public func clearDisplay() {
        sendRawCommand("CLS")
    }

    // ==========================================
    // Sensor Requests
    // ==========================================
    public func requestDistance() {
        sendRawCommand("?DIST")
    }

    public func requestLineStatus() {
        sendRawCommand("?LINE")
    }

    public func requestCompass() {
        sendRawCommand("?COMPASS")
    }

    public func requestAcceleration() {
        sendRawCommand("?ACCEL")
    }

    public func requestLightLevel() {
        sendRawCommand("?LIGHT")
    }

    public func requestTemperature() {
        sendRawCommand("?TEMP")
    }

    // ==========================================
    // Diagnostics / Ping
    // ==========================================
    public func ping() {
        sendRawCommand("PING")
    }
}
