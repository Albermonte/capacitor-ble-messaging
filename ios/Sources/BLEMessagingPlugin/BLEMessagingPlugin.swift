import Foundation
import Capacitor
import CoreBluetooth

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(BLEMessagingPlugin)
public class BLEMessagingPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "BLEMessagingPlugin"
    public let jsName = "BLEMessaging"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startAdvertising", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopAdvertising", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "connectToDevice", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "sendMessage", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isAdvertising", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isScanning", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cleanup", returnType: CAPPluginReturnPromise),
    ]
    
    // Controllers
    private var peripheralController: PeripheralController?
    private var centralController: CentralController?
    
    // State tracking
    private var pendingAdvertisingCall: CAPPluginCall? = nil
    private var pendingScanCall: CAPPluginCall? = nil
    private var isPeripheral: Bool = false
    private var serviceUUID: String?

    // MARK: - Peripheral Methods
    
    @objc func startAdvertising(_ call: CAPPluginCall) {
        // Get service UUID from options
        guard let uuidString = call.getString("serviceUUID") else {
            call.reject("Service UUID is required")
            return
        }
        
        // Validate the UUID format (but store as string)
        if UUID(uuidString: uuidString) != nil {
            serviceUUID = uuidString
        } else {
            call.reject("Invalid service UUID")
            return
        }
        
        // Set as peripheral mode
        isPeripheral = true
        
        // Retain the call for later use
        call.keepAlive = true
        pendingAdvertisingCall = call
        
        DispatchQueue.main.async {
            // Initialize peripheral if not already initialized
            if self.peripheralController == nil {
                self.peripheralController = PeripheralController()
                self.peripheralController?.plugin = self
            }
            
            // Check state and start advertising if ready
            self.checkStateAndAdvertise()
        }
    }

    @objc func stopAdvertising(_ call: CAPPluginCall) {
        guard let peripheralController = peripheralController else {
            call.reject("Peripheral not initialized")
            return
        }
        
        peripheralController.peripheralManager.stopAdvertising()
        notifyListeners("onAdvertisingStopped", data: [:])
        call.resolve()
    }

    // MARK: - Central Methods
    
    @objc func startScan(_ call: CAPPluginCall) {
        // Get service UUID from options
        guard let uuidString = call.getString("serviceUUID") else {
            call.reject("Service UUID is required")
            return
        }
        
        // Validate the UUID format (but store as string)
        if UUID(uuidString: uuidString) != nil {
            serviceUUID = uuidString
        } else {
            call.reject("Invalid service UUID")
            return
        }
        
        // Get optional timeout
        let timeout = call.getDouble("scanTimeout")
        
        // Set as central mode
        isPeripheral = false
        
        // Retain the call for later use
        call.keepAlive = true
        pendingScanCall = call
        
        DispatchQueue.main.async {
            // Initialize central if not already initialized
            if self.centralController == nil {
                self.centralController = CentralController()
                self.centralController?.plugin = self
            }
            
            // Check state and start scanning if ready
            self.checkStateAndStartScan(timeout: timeout)
        }
    }
    
    @objc func stopScan(_ call: CAPPluginCall) {
        guard let centralController = centralController else {
            call.reject("Central not initialized")
            return
        }
        
        if centralController.stopScan() {
            call.resolve()
        } else {
            call.reject("Failed to stop scanning")
        }
    }
    
    @objc func connectToDevice(_ call: CAPPluginCall) {
        guard let centralController = centralController else {
            call.reject("Central not initialized")
            return
        }
        
        guard let uuid = call.getString("uuid") else {
            call.reject("UUID is required")
            return
        }
        
        if centralController.connectToDevice(uuid: uuid) {
            call.resolve()
        } else {
            call.reject("Failed to connect to device")
        }
    }
    
    // MARK: - Common Methods
    
    @objc func sendMessage(_ call: CAPPluginCall) {
        guard let message = call.getString("message") else {
            call.reject("Message is required")
            return
        }
        
        guard let uuid = call.getString("to") else {
            call.reject("UUID is required")
            return
        }
        
        DispatchQueue.main.async {
            if self.isPeripheral, let peripheralController = self.peripheralController {
                peripheralController.sendMessage(message)
                call.resolve()
            } else if !self.isPeripheral, let centralController = self.centralController {
                if centralController.sendMessage(to: uuid, message: message) {
                    call.resolve()
                } else {
                    call.reject("Failed to send message")
                }
            } else {
                call.reject("Plugin not initialized")
            }
        }
    }
    
    @objc func isAdvertising(_ call: CAPPluginCall) {
        guard let peripheralController = peripheralController else {
            call.resolve([
                "isAdvertising": false
            ])
            return
        }
        
        call.resolve([
            "isAdvertising": peripheralController.peripheralManager.isAdvertising
        ])
    }
    
    @objc func isScanning(_ call: CAPPluginCall) {
        guard let centralController = centralController else {
            call.resolve([
                "isScanning": false
            ])
            return
        }
        
        call.resolve([
            "isScanning": centralController.isScanning()
        ])
    }
    
    // MARK: - State Checking Methods
    
    private func checkStateAndAdvertise() {
        guard let peripheralController = peripheralController,
              let pendingCall = pendingAdvertisingCall,
              let serviceUUIDString = serviceUUID else {
            return
        }
        
        let state = peripheralController.peripheralManager.state
        
        if state == .poweredOn {
            // Convert string to CBUUID only when needed
            let uuid = CBUUID(string: serviceUUIDString)
            peripheralController.peripheralManager.startAdvertising([
                CBAdvertisementDataServiceUUIDsKey: [uuid]
            ])
            pendingCall.resolve()
            pendingAdvertisingCall = nil
        } else if state == .poweredOff {
            pendingCall.reject("Bluetooth is turned off")
            pendingAdvertisingCall = nil
        } else if state == .unsupported {
            pendingCall.reject("Bluetooth is not supported on this device")
            pendingAdvertisingCall = nil
        } else if state == .unauthorized {
            pendingCall.reject("App is not authorized to use Bluetooth")
            pendingAdvertisingCall = nil
        }
        // For states like .resetting and .unknown, we'll wait for another state update
    }
    
    private func checkStateAndStartScan(timeout: Double?) {
        guard let centralController = centralController,
              let pendingCall = pendingScanCall else {
            return
        }
        
        let state = centralController.centralManager.state
        
        if state == .poweredOn {
            // Default is 30 seconds if no timeout is provided
            if centralController.startScan(timeout: timeout != nil ? TimeInterval(timeout!) : TimeInterval(30)) {
                pendingCall.resolve()
                pendingScanCall = nil
            } else {
                pendingCall.reject("Failed to start scanning")
                pendingScanCall = nil
            }
        } else if state == .poweredOff {
            pendingCall.reject("Bluetooth is turned off")
            pendingScanCall = nil
        } else if state == .unsupported {
            pendingCall.reject("Bluetooth is not supported on this device")
            pendingScanCall = nil
        } else if state == .unauthorized {
            pendingCall.reject("App is not authorized to use Bluetooth")
            pendingScanCall = nil
        }
        // For states like .resetting and .unknown, we'll wait for another state update
    }
    
    // MARK: - Notification Handlers
    
    @objc func handleAdvertisingStarted() {
        notifyListeners("onAdvertisingStarted", data: [:])
    }

    @objc func handleAdvertisingFailed() {
        notifyListeners("onAdvertisingFailed", data: [:])
    }

    @objc func handleCentralConnected(_ uuid: String) {
        notifyListeners("onDeviceConnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleCentralDisconnected(_ uuid: String) {
        notifyListeners("onDeviceDisconnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handlePeripheralStateUpdate() {
        checkStateAndAdvertise()
    }
    
    @objc func handleScanStarted() {
        notifyListeners("onScanStarted", data: [:])
    }
    
    @objc func handleScanStopped() {
        notifyListeners("onScanStopped", data: [:])
    }
    
    @objc func handleDeviceFound(_ uuid: String) {
        notifyListeners("onDeviceFound", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleDeviceConnected(_ uuid: String) {
        notifyListeners("onDeviceConnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleDeviceDisconnected(_ uuid: String) {
        notifyListeners("onDeviceDisconnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleCentralStateUpdate() {
        if let pendingCall = pendingScanCall {
            checkStateAndStartScan(timeout: pendingCall.getDouble("scanTimeout"))
        }
    }

    @objc func handleReceivedMessage(message: String, from: String) {
        var data: [String: Any] = [:]
        
        data["message"] = message
        data["from"] = from
        data["timestamp"] = Date().timeIntervalSince1970
        
        notifyListeners("onMessageReceived", data: data)
    }

    @objc func cleanup(_ call: CAPPluginCall) {
        peripheralController?.cleanup()
        centralController?.cleanup()
        peripheralController = nil
        centralController = nil
        pendingAdvertisingCall = nil
        pendingScanCall = nil
        call.resolve()
    }
    
    deinit {
        peripheralController?.cleanup()
        centralController?.cleanup()
        peripheralController = nil
        centralController = nil
        pendingAdvertisingCall = nil
        pendingScanCall = nil
    }
}
