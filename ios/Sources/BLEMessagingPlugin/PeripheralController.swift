import CoreBluetooth
import os

class PeripheralController: NSObject {
    var peripheralManager: CBPeripheralManager!
    
    var transferCharacteristic: CBMutableCharacteristic?
    var connectedCentral: CBCentral?
    var dataToSend = Data()
    var sendDataIndex: Int = 0
    
    // Array of receiving message by UUID
    var receivingMessage: [String: String] = [:]
    
    public weak var plugin: BLEMessagingPlugin?
    
    // MARK: - Initialization & Cleanup
    
    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(
            delegate: self, 
            queue: nil, 
            options: [CBPeripheralManagerOptionShowPowerAlertKey: true]
        )
    }
    
    func cleanup() {
        // Stop advertising when cleaning up
        peripheralManager.stopAdvertising()
    }
    
    // MARK: - Helper Methods

    /*
     *  Sends the next amount of data to the connected central
     */
    static var sendingEOM = false
    
    private func sendData() {
		
		guard let transferCharacteristic = transferCharacteristic else {
			return
		}
		
        // First up, check if we're meant to be sending an EOM
        if PeripheralController.sendingEOM {
            // send it
            let didSend = peripheralManager.updateValue(Utils.EOM_MARKER.data(using: .utf8)!, for: transferCharacteristic, onSubscribedCentrals: nil)
            // Did it send?
            if didSend {
                // It did, so mark it as sent
                PeripheralController.sendingEOM = false
                os_log("Sent: %@", Utils.EOM_MARKER)
            }
            // It didn't send, so we'll exit and wait for peripheralManagerIsReadyToUpdateSubscribers to call sendData again
            return
        }
        
        // We're not sending an EOM, so we're sending data
        // Is there any left to send?
        if sendDataIndex >= dataToSend.count {
            // No data left.  Do nothing
            return
        }
        
        // There's data left, so send until the callback fails, or we're done.
        var didSend = true
        while didSend {
            
            // Work out how big it should be
            var amountToSend = dataToSend.count - sendDataIndex
            if let mtu = connectedCentral?.maximumUpdateValueLength {
                amountToSend = min(amountToSend, mtu)
            }
            
            // Copy out the data we want
            let chunk = dataToSend.subdata(in: sendDataIndex..<(sendDataIndex + amountToSend))
            
            // Send it
            didSend = peripheralManager.updateValue(chunk, for: transferCharacteristic, onSubscribedCentrals: nil)
            
            // If it didn't work, drop out and wait for the callback
            if !didSend {
                return
            }
            
            let stringFromData = String(data: chunk, encoding: .utf8)
            os_log("Sent %d bytes: %s", chunk.count, String(describing: stringFromData))
            
            // It did send, so update our index
            sendDataIndex += amountToSend
            // Was it the last one?
            if sendDataIndex >= dataToSend.count {
                // It was - send an EOM
                
                // Set this so if the send fails, we'll send it next time
                PeripheralController.sendingEOM = true
                
                //Send it
                let eomSent = peripheralManager.updateValue(Utils.EOM_MARKER.data(using: .utf8)!,
                                                             for: transferCharacteristic, onSubscribedCentrals: nil)
                
                if eomSent {
                    // It sent; we're all done
                    PeripheralController.sendingEOM = false
                    os_log("Sent: %@", Utils.EOM_MARKER)
                }
                return
            }
        }
    }

    private func setupPeripheral() {
        
        // Build our service.
        
        // Start with the CBMutableCharacteristic.
        let transferCharacteristic = CBMutableCharacteristic(type: Utils.characteristicUUID,
                                                         properties: [.notify, .writeWithoutResponse],
                                                         value: nil,
                                                         permissions: [.readable, .writeable])
        
        // Create a service from the characteristic.
        let transferService = CBMutableService(type: Utils.serviceUUID, primary: true)
        
        // Add the characteristic to the service.
        transferService.characteristics = [transferCharacteristic]
        
        // And add it to the peripheral manager.
        peripheralManager.add(transferService)
        
        // Save the characteristic for later.
        self.transferCharacteristic = transferCharacteristic

    }

    public func sendMessage(_ message: String) {
        // Assign string to data
        dataToSend = message.data(using: .utf8)!
        
        // Reset the index
        sendDataIndex = 0
        
        // Start sending
        sendData()
    }
}

extension PeripheralController: CBPeripheralManagerDelegate {
    // implementations of the CBPeripheralManagerDelegate methods

    /*
     *  Required protocol method.  A full app should take care of all the possible states,
     *  but we're just waiting for to know when the CBPeripheralManager is ready
     *
     *  Starting from iOS 13.0, if the state is CBManagerStateUnauthorized, you
     *  are also required to check for the authorization state of the peripheral to ensure that
     *  your app is allowed to use bluetooth
     */
    internal func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {        
        // Post notification about state change
        NotificationCenter.default.post(name: NSNotification.Name("peripheralStateUpdate"), object: nil)
        
        switch peripheral.state {
        case .poweredOn:
            // ... so start working with the peripheral
            os_log("CBManager is powered on")
            setupPeripheral()
        case .poweredOff:
            os_log("CBManager is not powered on")
            // In a real app, you'd deal with all the states accordingly
            return
        case .resetting:
            os_log("CBManager is resetting")
            // In a real app, you'd deal with all the states accordingly
            return
        case .unauthorized:
            // In a real app, you'd deal with all the states accordingly
            if #available(iOS 13.0, *) {
                switch peripheral.authorization {
                case .denied:
                    os_log("You are not authorized to use Bluetooth")
                case .restricted:
                    os_log("Bluetooth is restricted")
                default:
                    os_log("Unexpected authorization")
                }
            } else {
                // Fallback on earlier versions
            }
            return
        case .unknown:
            os_log("CBManager state is unknown")
            // In a real app, you'd deal with all the states accordingly
            return
        case .unsupported:
            os_log("Bluetooth is not supported on this device")
            // In a real app, you'd deal with all the states accordingly
            return
        @unknown default:
            os_log("A previously unknown peripheral manager state occurred")
            // In a real app, you'd deal with yet unknown cases that might occur in the future
            return
        }
    }

    /*
     *  Catch when someone subscribes to our characteristic, then start sending them data
     */
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        os_log("Central subscribed to characteristic")        
        connectedCentral = central
        NotificationCenter.default.post(name: NSNotification.Name("centralConnected"), object: nil, userInfo: ["uuid": central.identifier.uuidString])
    }
    
    /*
     *  Recognize when the central unsubscribes
     */
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        os_log("Central unsubscribed from characteristic")
        connectedCentral = nil
        NotificationCenter.default.post(name: NSNotification.Name("centralDisconnected"), object: nil, userInfo: ["uuid": central.identifier.uuidString])
    }
    
    /*
     *  This callback comes in when the PeripheralManager is ready to send the next chunk of data.
     *  This is to ensure that packets will arrive in the order they are sent
     */
    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        // Start sending again
        sendData()
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            os_log("Failed to advertise: %s", error.localizedDescription)
            NotificationCenter.default.post(name: NSNotification.Name("advertisingFailed"), object: nil)
        } else {
            os_log("Successfully advertised")
            NotificationCenter.default.post(name: NSNotification.Name("advertisingStarted"), object: nil)
        }
    }
    
    /*
     * This callback comes in when the PeripheralManager received write to characteristics
     */
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for aRequest in requests {
            guard let requestValue = aRequest.value,
                let stringFromData = String(data: requestValue, encoding: .utf8) else {
                    continue
            }
            
            os_log("Received write request of %d bytes: %s", requestValue.count, stringFromData)
            let uuid = aRequest.central.identifier.uuidString
            if(stringFromData == Utils.EOM_MARKER) {
                // End of message
                // Post notification about received message
                NotificationCenter.default.post(name: NSNotification.Name("receivedMessage"), object: nil, userInfo: ["message": receivingMessage[uuid] ?? "", "uuid": uuid])
                receivingMessage[aRequest.central.identifier.uuidString] = ""
            } else {
                // Append to pending message
                receivingMessage[aRequest.central.identifier.uuidString] = (receivingMessage[uuid] ?? "") + stringFromData
            }
        }
    }
}
