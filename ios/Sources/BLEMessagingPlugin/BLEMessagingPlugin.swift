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
                
                // Set up notification handlers for events from PeripheralController
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleAdvertisingStarted(_:)), name: NSNotification.Name("advertisingStarted"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleAdvertisingFailed(_:)), name: NSNotification.Name("advertisingFailed"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleCentralConnected(_:)), name: NSNotification.Name("centralConnected"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleCentralDisconnected(_:)), name: NSNotification.Name("centralDisconnected"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handlePeripheralStateUpdate(_:)), name: NSNotification.Name("peripheralStateUpdate"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleReceivedMessage(_:)), name: NSNotification.Name("receivedMessage"), object: nil)
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
                
                // Set up notification handlers for events from CentralController
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleScanStarted(_:)), name: NSNotification.Name("scanStarted"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleScanStopped(_:)), name: NSNotification.Name("scanStopped"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleDeviceFound(_:)), name: NSNotification.Name("deviceFound"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleDeviceConnected(_:)), name: NSNotification.Name("deviceConnected"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleDeviceDisconnected(_:)), name: NSNotification.Name("deviceDisconnected"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleCentralStateUpdate(_:)), name: NSNotification.Name("centralStateUpdate"), object: nil)
                NotificationCenter.default.addObserver(self, selector: #selector(self.handleReceivedMessage(_:)), name: NSNotification.Name("receivedMessage"), object: nil)
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
              let pendingCall = pendingScanCall,
              let serviceUUIDString = serviceUUID else {
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
    
    @objc func handleAdvertisingStarted(_ notification: Notification) {
        notifyListeners("onAdvertisingStarted", data: [:])
    }

    @objc func handleAdvertisingFailed(_ notification: Notification) {
        notifyListeners("onAdvertisingFailed", data: [:])
    }

    @objc func handleCentralConnected(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let uuid = userInfo["uuid"] as? String else {
            return
        }
        
        notifyListeners("onDeviceConnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleCentralDisconnected(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let uuid = userInfo["uuid"] as? String else {
            return
        }
                
        notifyListeners("onDeviceDisconnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handlePeripheralStateUpdate(_ notification: Notification) {
        checkStateAndAdvertise()
    }
    
    @objc func handleScanStarted(_ notification: Notification) {
        notifyListeners("onScanStarted", data: [:])
    }
    
    @objc func handleScanStopped(_ notification: Notification) {
        notifyListeners("onScanStopped", data: [:])
    }
    
    @objc func handleDeviceFound(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let uuid = userInfo["uuid"] as? String else {
            return
        }
        
        notifyListeners("onDeviceFound", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleDeviceConnected(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let uuid = userInfo["uuid"] as? String else {
            return
        }
        
        notifyListeners("onDeviceConnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleDeviceDisconnected(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let uuid = userInfo["uuid"] as? String else {
            return
        }
        
        notifyListeners("onDeviceDisconnected", data: [
            "uuid": uuid
        ])
    }
    
    @objc func handleCentralStateUpdate(_ notification: Notification) {
        if let pendingCall = pendingScanCall {
            checkStateAndStartScan(timeout: pendingCall.getDouble("scanTimeout"))
        }
    }

    @objc func handleReceivedMessage(_ notification: Notification) {
        guard let userInfo = notification.userInfo else {
            return
        }
        
        var data: [String: Any] = [:]
        
        if let message = userInfo["message"] as? String {
            data["message"] = message
        }
        
        if let uuid = userInfo["from"] as? String {
            data["from"] = uuid
        } else if let uuid = userInfo["uuid"] as? String {
            data["from"] = uuid
        }
        
        data["timestamp"] = Date().timeIntervalSince1970
        
        notifyListeners("onMessageReceived", data: data)
    }
    
    deinit {
        peripheralController?.cleanup()
        centralController?.cleanup()
        NotificationCenter.default.removeObserver(self)
    }
}
