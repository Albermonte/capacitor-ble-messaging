import { PluginListenerHandle } from "@capacitor/core";

export interface BLEMessagingPlugin {
  startAdvertising(options: { serviceUUID: string }): Promise<void>;
  stopAdvertising(): Promise<void>;
  /**
   * Start scanning for devices advertising the specified service UUID.
   * @param options.serviceUUID The service UUID to scan for.
   * @param options.scanTimeout The number of seconds to scan for devices. If not provided, the default is 30 seconds. Set to 0 to scan indefinitely.
   */
  startScan(options: { serviceUUID: string, scanTimeout?: number }): Promise<void>;
  /**
   * Stop scanning for devices.
   */
  stopScan(): Promise<void>;
  /**
   * Connect to a device with the specified UUID.
   * @param options.uuid The UUID of the device to connect to. You can get the UUID of a device from the onDeviceFound event.
   */
  connectToDevice(options: { uuid: string }): Promise<void>;
  /**
   * Disconnect from a connected device.
   * @param options.uuid The UUID of the device to disconnect from. You can get the UUID of a device from the onDeviceFound event.
   */
  disconnectFromDevice(options: { uuid: string }): Promise<void>;
  /**
   * Send a message to a connected device.
   * @param options.to The UUID of the device to send the message to. You can get the UUID of a device from the onDeviceFound event.
   * @param options.message The message to send.
   */
  sendMessage(options: { to: string, message: string }): Promise<void>;
  /**
   * Check if the device is currently advertising.
  */
  isAdvertising(): Promise<{ isAdvertising: boolean }>;
  /**
   * Check if the device is currently scanning.
  */
  isScanning(): Promise<{ isScanning: boolean }>;

  addListener(eventName: 'onScanStarted', listenerFunc: () => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'onScanStopped', listenerFunc: () => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'onScanFailed', listenerFunc: () => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'onAdvertisingStarted', listenerFunc: () => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'onAdvertisingStopped', listenerFunc: () => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'onAdvertisingFailed', listenerFunc: () => void): Promise<PluginListenerHandle>;
  /**
   * Emitted when a device advertising the specified service UUID is found.
   * @param uuid The UUID of the device that was found. Use this UUID to connect to the device and send messages to it.
   */
  addListener(eventName: 'onDeviceFound', listenerFunc: ({ uuid }: { uuid: string }) => void): Promise<PluginListenerHandle>;
  /**
   * Emitted when a device is connected.
   * @param uuid The UUID of the device that was connected.
   */
  addListener(eventName: 'onDeviceConnected', listenerFunc: ({ uuid }: { uuid: string }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'onDeviceDisconnected', listenerFunc: ({ uuid }: { uuid: string }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'onMessageReceived', listenerFunc: ({ from, message, timestamp }: { from: string, message: string, timestamp: number }) => void): Promise<PluginListenerHandle>;
}
