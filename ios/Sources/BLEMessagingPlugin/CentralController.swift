import CoreBluetooth
import os

class CentralController: NSObject {
    // Properties
    var centralManager: CBCentralManager!
    var discoveredPeripherals: [CBPeripheral] = []
    var connectedPeripheral: CBPeripheral?
    var transferCharacteristic: CBCharacteristic?
    
    // Message handling
    var data = Data()
    var receivingMessage = ""
    var pendingMessage: String?
    var messageIndex: Int = 0
    
    public weak var plugin: BLEMessagingPlugin?
    private var _isScanning: Bool = false
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: true])
    }
    
    func cleanup() {
        self.stopScan()
        os_log("Cleaning up CentralController")
        
        if let connectedPeripheral = connectedPeripheral {
            centralManager.cancelPeripheralConnection(connectedPeripheral)
        }
        
        data.removeAll(keepingCapacity: false)
    }
    
    // MARK: - Public Methods
    
    func startScan(timeout: TimeInterval) -> Bool {
        guard centralManager.state == .poweredOn else {
            os_log("Central manager not ready to scan")
            return false
        }
        
        // Start scanning for peripherals advertising our service
        centralManager.scanForPeripherals(withServices: [Utils.serviceUUID],
                                          options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        _isScanning = true
        
        // Notify about scan started
        NotificationCenter.default.post(name: NSNotification.Name("scanStarted"), object: nil)
        
        // Set timeout if provided
        if timeout > 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + timeout) { [weak self] in
                self?.stopScan()
            }
        }
        
        return true
    }
    
    func stopScan() -> Bool {
        os_log("Stopping scan, isScanning: %d", _isScanning)
        guard _isScanning else {
            return true
        }
        
        centralManager.stopScan()
        _isScanning = false
        // Clear discovered peripherals
        discoveredPeripherals.removeAll(keepingCapacity: false)
        
        // Notify about scan stopped
        NotificationCenter.default.post(name: NSNotification.Name("scanStopped"), object: nil)
        
        return true
    }
    
    func isScanning() -> Bool {
        return _isScanning
    }
    
    func connectToDevice(uuid: String) -> Bool {
        // Find the peripheral with the given UUID
        guard let peripheral = discoveredPeripherals.first(where: { $0.identifier.uuidString == uuid }) else {
            os_log("No peripheral found with UUID: %@", uuid)
            return false
        }
        
        os_log("Connecting to peripheral: %@", peripheral)
        centralManager.connect(peripheral, options: nil)
        return true
    }
    
    func sendMessage(to uuid: String, message: String) -> Bool {
        guard let peripheral = connectedPeripheral, 
              peripheral.identifier.uuidString == uuid,
              let characteristic = transferCharacteristic else {
            os_log("Cannot send message: no connected peripheral or characteristic")
            return false
        }
        
        // Prepare message for sending
        pendingMessage = message
        messageIndex = 0
        
        // Start sending message in chunks
        return sendNextChunk()
    }
    
    // MARK: - Private Methods
    
    private func sendNextChunk() -> Bool {
        guard let pendingMessage = pendingMessage,
              let peripheral = connectedPeripheral,
              let characteristic = transferCharacteristic else {
            return false
        }
        
        // Check if we're done with the message
        if messageIndex >= pendingMessage.count {
            // Send EOM marker
            sendEOMMarker()
            return true
        }
        
        // Calculate chunk size
        let endIndex = min(messageIndex + 20, pendingMessage.count)
        let startIndex = pendingMessage.index(pendingMessage.startIndex, offsetBy: messageIndex)
        let endIndexString = pendingMessage.index(pendingMessage.startIndex, offsetBy: endIndex)
        let chunk = String(pendingMessage[startIndex..<endIndexString])
        
        // Update index for next chunk
        messageIndex = endIndex
        
        os_log("Sending chunk: %@ (%d/%d)", chunk, messageIndex, pendingMessage.count)
        
        // Send the chunk
        guard let data = chunk.data(using: .utf8) else {
            os_log("Could not convert chunk to data")
            return false
        }
        
        peripheral.writeValue(data, for: characteristic, type: .withoutResponse)
        
        // If peripheral can accept more data, continue sending
        if peripheral.canSendWriteWithoutResponse {
            return sendNextChunk()
        }
        
        return true
    }
    
    private func sendEOMMarker() {
        guard let peripheral = connectedPeripheral,
              let characteristic = transferCharacteristic else {
            return
        }
        
        os_log("Sending EOM marker")
        
        if let data = Utils.EOM_MARKER.data(using: .utf8) {
            peripheral.writeValue(data, for: characteristic, type: .withoutResponse)
        }
        
        // Reset sending state
        pendingMessage = nil
        messageIndex = 0
    }
}

extension CentralController: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        // Post notification about state change
        NotificationCenter.default.post(name: NSNotification.Name("centralStateUpdate"), object: nil)

        switch central.state {
        case .poweredOn:
            os_log("CBManager is powered on")
        case .poweredOff:
            os_log("CBManager is not powered on")
            _isScanning = false
        case .resetting:
            os_log("CBManager is resetting")
        case .unauthorized:
            if #available(iOS 13.0, *) {
                switch central.authorization {
                case .denied:
                    os_log("You are not authorized to use Bluetooth")
                case .restricted:
                    os_log("Bluetooth is restricted")
                default:
                    os_log("Unexpected authorization")
                }
            }
        case .unknown:
            os_log("CBManager state is unknown")
        case .unsupported:
            os_log("Bluetooth is not supported on this device")
        @unknown default:
            os_log("A previously unknown central manager state occurred")
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {        
        // Check if we've already discovered this peripheral
        if !discoveredPeripherals.contains(peripheral) {
            os_log("Discovered %s at %d", String(describing: peripheral.identifier.uuidString), RSSI.intValue)
            // Add to our list of discovered peripherals
            discoveredPeripherals.append(peripheral)
            os_log("Added new peripheral to list: %@", peripheral)
            
            // Notify about the discovery
            NotificationCenter.default.post(
                name: NSNotification.Name("deviceFound"), 
                object: nil, 
                userInfo: ["uuid": peripheral.identifier.uuidString]
            )
        }
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        os_log("Failed to connect to %@. %s", peripheral, String(describing: error))
        NotificationCenter.default.post(
            name: NSNotification.Name("deviceDisconnected"), 
            object: nil, 
            userInfo: ["uuid": peripheral.identifier.uuidString]
        )
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        os_log("Peripheral Connected: %@", peripheral)
        
        // Store as the currently connected peripheral
        connectedPeripheral = peripheral
        
        // Stop scanning
        // if _isScanning {
        //     self.stopScan()
        // }
        
        // Clear the data
        data.removeAll(keepingCapacity: false)
        
        // Set up the peripheral delegate
        peripheral.delegate = self
        
        // Search for services
        peripheral.discoverServices([Utils.serviceUUID])
        
        // Notify about the connection
        NotificationCenter.default.post(
            name: NSNotification.Name("deviceConnected"), 
            object: nil,
            userInfo: ["uuid": peripheral.identifier.uuidString]
        )
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        os_log("Peripheral Disconnected: %@", peripheral)
        
        // Clear the connected peripheral reference
        if connectedPeripheral == peripheral {
            connectedPeripheral = nil
        }
        
        // Notify about disconnection
        NotificationCenter.default.post(
            name: NSNotification.Name("deviceDisconnected"), 
            object: nil,
            userInfo: ["uuid": peripheral.identifier.uuidString]
        )
    }
}

extension CentralController: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            os_log("Error discovering services: %s", error.localizedDescription)
            return
        }
        
        guard let peripheralServices = peripheral.services else { return }
        for service in peripheralServices {
            peripheral.discoverCharacteristics([Utils.characteristicUUID], for: service)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error = error {
            os_log("Error discovering characteristics: %s", error.localizedDescription)
            return
        }
        
        guard let serviceCharacteristics = service.characteristics else { 
            os_log("No characteristics found for service")
            return 
        }
        
        // Log all discovered characteristics to help debugging
        for characteristic in serviceCharacteristics {
            os_log("Found characteristic: %@ with properties: %d", characteristic.uuid.uuidString, characteristic.properties.rawValue)
        }
        
        // Find our target characteristic
        for characteristic in serviceCharacteristics where characteristic.uuid == Utils.characteristicUUID {
            os_log("Found target characteristic: %@", characteristic.uuid.uuidString)
            
            // Check if characteristic supports notifications
            if characteristic.properties.contains(.notify) {
                transferCharacteristic = characteristic
                os_log("Setting up notifications for characteristic")
                peripheral.setNotifyValue(true, for: characteristic)
            } else {
                os_log("⚠️ Characteristic doesn't support notifications! Properties: %d", characteristic.properties.rawValue)
                // Store the characteristic anyway, we might need it for writing
                transferCharacteristic = characteristic
            }
        }
        
        if transferCharacteristic == nil {
            os_log("⚠️ Target characteristic not found in service")
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            os_log("Error updating characteristic value: %s", error.localizedDescription)
            return
        }
        
        guard let characteristicData = characteristic.value,
              let stringFromData = String(data: characteristicData, encoding: .utf8) else { return }
        
        os_log("Received %d bytes: %s", characteristicData.count, stringFromData)
        
        // Check for end-of-message marker
        if stringFromData == Utils.EOM_MARKER {
            // Process the complete message
            let message = String(data: self.data, encoding: .utf8) ?? ""
            
            // Notify about received message
            NotificationCenter.default.post(
                name: NSNotification.Name("receivedMessage"), 
                object: nil, 
                userInfo: [
                    "message": message, 
                    "from": peripheral.identifier.uuidString
                ]
            )
            
            // Reset for next message
            data.removeAll(keepingCapacity: false)
        } else {
            // Append chunk to the accumulating message
            data.append(characteristicData)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            os_log("Error changing notification state: %s", error.localizedDescription)
            return
        }
        
        if !characteristic.isNotifying {
            os_log("Notification stopped on %@", characteristic)
        }
    }
    
    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        // Continue sending message chunks if there are any pending
        if pendingMessage != nil && messageIndex <= (pendingMessage?.count ?? 0) {
            _ = sendNextChunk()
        }
    }
}
