import SwiftUI
import CoreBluetooth

struct ContentView: View {
    @StateObject private var controller = CutebotController()

    @State private var driveSpeed: Double = 60
    @State private var showingDeviceSheet = false

    // Live Telemetry Readings
    @State private var distReading: String = "--"
    @State private var lineReading: String = "--"
    @State private var compassReading: String = "--"
    @State private var accelReading: String = "--"
    @State private var lightReading: String = "--"
    @State private var tempReading: String = "--"
    @State private var pingReading: String = "--"

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Status & Connection Header
                    VStack(spacing: 8) {
                        HStack {
                            Circle()
                                .fill(controller.isConnected ? Color.green : Color.red)
                                .frame(width: 12, height: 12)
                            Text("Status: \(controller.connectionStatus)")
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .foregroundColor(controller.isConnected ? .green : .red)
                        }

                        Button(action: {
                            controller.startScanning()
                            showingDeviceSheet = true
                        }) {
                            Label(controller.isConnected ? "Change Robot" : "Scan & Connect", systemImage: "antenna.radiowaves.left.and.right")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding()
                    .background(Color(UIColor.secondarySystemBackground))
                    .cornerRadius(12)

                    // Speed Control
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Motor Speed: \(Int(driveSpeed))%")
                            .font(.headline)
                        Slider(value: $driveSpeed, in: 20...100, step: 10)
                    }
                    .padding(.horizontal)

                    // Movement Controls (D-Pad)
                    VStack(spacing: 8) {
                        Text("Movement Controls")
                            .font(.headline)

                        Button("W") {
                            controller.moveForward(speed: Int(driveSpeed))
                        }
                        .buttonStyle(DirectionalButtonStyle())

                        HStack(spacing: 12) {
                            Button("A") {
                                controller.turnLeft(speed: Int(driveSpeed))
                            }
                            .buttonStyle(DirectionalButtonStyle())

                            Button("STOP") {
                                controller.stop()
                            }
                            .buttonStyle(DirectionalButtonStyle(backgroundColor: .red))

                            Button("D") {
                                controller.turnRight(speed: Int(driveSpeed))
                            }
                            .buttonStyle(DirectionalButtonStyle())
                        }

                        Button("S") {
                            controller.moveBackward(speed: Int(driveSpeed))
                        }
                        .buttonStyle(DirectionalButtonStyle())
                    }

                    Divider()

                    // Headlights & Underglow
                    VStack(spacing: 10) {
                        Text("Headlights & Underglow")
                            .font(.headline)

                        HStack(spacing: 8) {
                            Button("Red Both") {
                                controller.setHeadlights(r: 255, g: 0, b: 0)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.red)

                            Button("Turn L") {
                                controller.setLeftHeadlight(r: 255, g: 165, b: 0)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.orange)

                            Button("Turn R") {
                                controller.setRightHeadlight(r: 255, g: 165, b: 0)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.orange)
                        }

                        HStack(spacing: 8) {
                            Button("Underglow Green") {
                                controller.setUnderglow(r: 0, g: 255, b: 0)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.green)

                            Button("Lights Off") {
                                controller.turnLightsOff()
                            }
                            .buttonStyle(.bordered)
                        }
                    }

                    Divider()

                    // Audio & Horn
                    VStack(spacing: 10) {
                        Text("Audio & Horn")
                            .font(.headline)

                        HStack(spacing: 8) {
                            Button("Horn") { controller.playHorn() }
                                .buttonStyle(.bordered)
                            Button("Beep") { controller.playBeep() }
                                .buttonStyle(.bordered)
                            Button("Tone 1kHz") { controller.playTone(frequency: 1000, durationMs: 300) }
                                .buttonStyle(.bordered)
                            Button("Quiet") { controller.stopSound() }
                                .buttonStyle(.bordered)
                        }
                    }

                    Divider()

                    // micro:bit 5x5 LED Display
                    VStack(spacing: 10) {
                        Text("micro:bit 5x5 LED Display")
                            .font(.headline)

                        HStack(spacing: 8) {
                            Button("Text 'NEXT'") { controller.displayText("NEXT") }
                                .buttonStyle(.bordered)
                            Button("Heart") { controller.displayIcon("HEART") }
                                .buttonStyle(.bordered)
                            Button("Skull") { controller.displayIcon("SKULL") }
                                .buttonStyle(.bordered)
                            Button("Clear") { controller.clearDisplay() }
                                .buttonStyle(.bordered)
                        }
                    }

                    Divider()

                    // Sensor Telemetry & Diagnostics
                    VStack(spacing: 10) {
                        Text("Sensor Telemetry & Diagnostics")
                            .font(.headline)

                        HStack(spacing: 6) {
                            Button("?Dist") { controller.requestDistance() }.buttonStyle(.bordered)
                            Button("?Line") { controller.requestLineStatus() }.buttonStyle(.bordered)
                            Button("?Heading") { controller.requestCompass() }.buttonStyle(.bordered)
                            Button("Ping") { controller.ping() }.buttonStyle(.bordered)
                        }

                        HStack(spacing: 6) {
                            Button("?Accel") { controller.requestAcceleration() }.buttonStyle(.bordered)
                            Button("?Light") { controller.requestLightLevel() }.buttonStyle(.bordered)
                            Button("?Temp") { controller.requestTemperature() }.buttonStyle(.bordered)
                        }

                        // Telemetry Card
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Live Telemetry Readings")
                                .font(.subheadline)
                                .fontWeight(.bold)
                            Divider()
                            TelemetryRow(label: "Distance:", value: distReading)
                            TelemetryRow(label: "Line Tracker:", value: lineReading)
                            TelemetryRow(label: "Compass Heading:", value: compassReading)
                            TelemetryRow(label: "Accelerometer (X,Y,Z):", value: accelReading)
                            TelemetryRow(label: "Ambient Light:", value: lightReading)
                            TelemetryRow(label: "Temperature:", value: tempReading)
                            TelemetryRow(label: "Ping / Pong:", value: pingReading)
                        }
                        .padding()
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(12)
                    }
                }
                .padding()
            }
            .navigationTitle("Cutebot Controller")
            .sheet(isPresented: $showingDeviceSheet) {
                DeviceScanSheet(controller: controller, isPresented: $showingDeviceSheet)
            }
            .onReceive(controller.$latestTelemetry) { telemetry in
                guard let telemetry = telemetry else { return }
                switch telemetry {
                case .distance(let cm):
                    distReading = "\(cm) cm"
                case .lineTracker(_, let desc):
                    lineReading = desc
                case .compass(let deg):
                    compassReading = "\(deg)°"
                case .acceleration(let x, let y, let z):
                    accelReading = "\(x), \(y), \(z)"
                case .lightLevel(let level):
                    lightReading = "\(level)"
                case .temperature(let celsius):
                    tempReading = "\(celsius)°C"
                case .pong:
                    pingReading = "PONG"
                case .raw:
                    break
                }
            }
        }
    }
}

// Custom Directional D-Pad Button Style
struct DirectionalButtonStyle: ButtonStyle {
    var backgroundColor: Color = .blue

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .fontWeight(.bold)
            .foregroundColor(.white)
            .frame(width: 72, height: 72)
            .background(backgroundColor)
            .clipShape(Circle())
            .scaleEffect(configuration.isPressed ? 0.92 : 1.0)
    }
}

// Telemetry Row
struct TelemetryRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
        }
        .font(.footnote)
    }
}

// Device Scan Sheet
struct DeviceScanSheet: View {
    @ObservedObject var controller: CutebotController
    @Binding var isPresented: Bool

    var body: some View {
        NavigationView {
            List(controller.discoveredPeripherals, id: \.identifier) { peripheral in
                HStack {
                    VStack(alignment: .leading) {
                        Text(peripheral.name ?? "BBC micro:bit")
                            .font(.headline)
                        Text(peripheral.identifier.uuidString)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Button("Connect") {
                        controller.connect(to: peripheral)
                        isPresented = false
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .navigationTitle("Nearby Robots")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        controller.stopScanning()
                        isPresented = false
                    }
                }
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
