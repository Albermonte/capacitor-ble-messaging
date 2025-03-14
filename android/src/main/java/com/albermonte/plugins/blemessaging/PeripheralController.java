package com.albermonte.plugins.blemessaging;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PeripheralController {
  private static final String TAG = "BLEMessaging/PeripheralController";
  private final UUID serviceUUID;
  private final Context context;
  private final BLEMessagingCallback callback;

  private final BluetoothManager bluetoothManager;
  private final BluetoothAdapter bluetoothAdapter;
  private BluetoothGattServer bluetoothGattServer;
  private BluetoothGatt bluetoothGattClient;
  private BluetoothLeAdvertiser advertiser;
  private List<BluetoothDevice> connectedDevices = new ArrayList<>();
  private Boolean isAdvertising = false;

  // Constants for chunked messaging
  private static final int MAX_CHUNK_SIZE = 20; // BLE packet size limit, adjust as needed
  private static final String EOM_MARKER = "EOM"; // End of message marker

  // Variables to track message sending state
  private String pendingMessage = null;
  private int messageIndex = 0;
  private String currentDeviceUuid = null;

  public PeripheralController(Context context, BluetoothManager bluetoothManager, BluetoothAdapter bluetoothAdapter,
      UUID uuid, BLEMessagingCallback callback) {
    Log.d(PeripheralController.TAG, "Initializing PeripheralController");
    this.bluetoothManager = bluetoothManager;
    this.bluetoothAdapter = bluetoothAdapter;
    this.serviceUUID = uuid;
    this.context = context;
    this.callback = callback;
    Log.d(PeripheralController.TAG, "Initialized PeripheralController");
  }

  public boolean startAdvertising() {
    Log.d(TAG, "Starting advertising");

    advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
    if (advertiser == null) {
      Log.e(TAG, "Failed to create advertiser");
      return false;
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
      throw new RuntimeException("Advertising not supported on Android Oreo or older");
    }

    AdvertiseSettings settings = new AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        // TODO: get this from capacitor
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(true)
        .build();

    AdvertiseData data = new AdvertiseData.Builder()
        .addServiceUuid(new ParcelUuid(serviceUUID))
        .build();

    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
      throw new RuntimeException("BLUETOOTH_ADVERTISE permission missing");
    }

    if (!bluetoothAdapter.isLeExtendedAdvertisingSupported()) {
      throw new RuntimeException("LE Extended Advertising not supported");
    }

    setupGattServer();

    advertiser.startAdvertising(
        settings,
        data,
        advertiseCallback);

    return true;
  }

  public boolean stopAdvertising() {
    Log.d(TAG, "Stopping advertising");

    if (advertiser == null) {
      Log.e(TAG, "Advertiser not found");
      return false;
    }

      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
          return false;
      }
      advertiser.stopAdvertising(advertiseCallback);
    isAdvertising = false;
    if (callback != null) {
      callback.notifyEvent("onAdvertisingStopped", null);
    }
    return true;
  }

  public boolean sendMessage(String uuid, String message) {
    if (bluetoothGattServer == null) {
      Log.e(TAG, "GATT server not initialized");
      return false;
    }

    BluetoothGattService service = bluetoothGattServer.getService(serviceUUID);
    if (service == null) {
      Log.e(TAG, "Service not found");
      return false;
    }

    if (uuid == null || uuid.isEmpty()) {
      Log.e(TAG, "Invalid UUID");
      throw new RuntimeException("Invalid UUID");
    }
    
    if (message == null || message.isEmpty()) {
      Log.e(TAG, "Invalid message");
      throw new RuntimeException("Invalid message");
    }
    
    BluetoothDevice targetDevice = null;
    for (BluetoothDevice device : connectedDevices) {
      if (Utils.getDeviceUUID(device.getAddress()).equals(uuid)) {
        targetDevice = device;
        break;
      }
    }

    if (targetDevice == null) {
      Log.e(TAG, "Device not connected");
      throw new RuntimeException("Device not connected");
    }

    BluetoothGattCharacteristic messageChar = service.getCharacteristic(Utils.MESSAGE_CHAR_UUID);
    if (messageChar == null) {
      Log.e(TAG, "Characteristic not found");
      return false;
    }

    // Split the message into chunks and send them one by one
    int messageLength = message.length();
    int chunkSize = MAX_CHUNK_SIZE;
    int offset = 0;
    
    while (offset < messageLength) {
      int endIndex = Math.min(offset + chunkSize, messageLength);
      String chunk = message.substring(offset, endIndex);
      
      // Set the chunk as the characteristic value
      messageChar.setValue(chunk.getBytes(StandardCharsets.UTF_8));
      
      // Send notification to the central
      if (ActivityCompat.checkSelfPermission(context,
          Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
      }
      
      Log.d(TAG, "Sending chunk: " + chunk + " (" + offset + "-" + endIndex + " of " + messageLength + ")");
      boolean success = bluetoothGattServer.notifyCharacteristicChanged(
          targetDevice,
          messageChar,
          false // No acknowledgment needed (use true for indications)
      );
      
      if (!success) {
        Log.e(TAG, "Failed to send notification");
        return false;
      }
      
      // Move to next chunk
      offset = endIndex;
      
      // Small delay to prevent packet loss
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Log.e(TAG, "Sleep interrupted", e);
      }
    }
    
    // Send EOM marker
    messageChar.setValue(EOM_MARKER.getBytes(StandardCharsets.UTF_8));
    
    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
    }
    
    Log.d(TAG, "Sending EOM marker");
    boolean success = bluetoothGattServer.notifyCharacteristicChanged(
        targetDevice,
        messageChar,
        false
    );
    
    return success;
  }

  private void setupGattServer() {
    if (bluetoothGattServer != null) {
      return;
    }
    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
    }
    bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback);

    // Create custom service
    BluetoothGattService service = new BluetoothGattService(
        serviceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY);

    // Configure characteristic to hold your message
    BluetoothGattCharacteristic messageChar = new BluetoothGattCharacteristic(
        Utils.MESSAGE_CHAR_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
    
    // Add Client Characteristic Configuration Descriptor (CCCD) - required for notifications
    BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
        Utils.CCCD_UUID,
        BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
    messageChar.addDescriptor(descriptor);

    // Add the characteristic to the service
    service.addCharacteristic(messageChar);

    // Add service to GATT server
    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
    }
    bluetoothGattServer.addService(service);
  }

  private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        // Store connected device
        connectedDevices.add(device);
        Log.d(TAG, "Connected to " + Utils.getDeviceUUID(device.getAddress()));
        if (callback != null) {
          JSObject ret = new JSObject();
          ret.put("uuid", Utils.getDeviceUUID(device.getAddress()));
          callback.notifyEvent("onDeviceConnected", ret);
        }
      }
      if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        // Remove disconnected device
        connectedDevices.remove(device);
        Log.d(TAG, "Disconnected from " + Utils.getDeviceUUID(device.getAddress()));
        if (callback != null) {
          JSObject ret = new JSObject();
          ret.put("uuid", Utils.getDeviceUUID(device.getAddress()));
          callback.notifyEvent("onDeviceDisconnected", ret);
        }
      }
    }

    @Override
    public void onCharacteristicReadRequest(
        BluetoothDevice device,
        int requestId,
        int offset,
        BluetoothGattCharacteristic characteristic) {
      // Send the stored message when central reads the characteristic
      Log.d(TAG, "Characteristic read request");
      if (ActivityCompat.checkSelfPermission(context,
          Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
      }
      bluetoothGattServer.sendResponse(
          device,
          requestId,
          BluetoothGatt.GATT_SUCCESS,
          offset,
          characteristic.getValue() // Your string bytes
      );
    }
    
    @Override
    public void onCharacteristicWriteRequest(
        BluetoothDevice device,
        int requestId,
        BluetoothGattCharacteristic characteristic,
        boolean preparedWrite,
        boolean responseNeeded,
        int offset,
        byte[] value) {
      
      Log.d(TAG, "Write request received");
      
      if (ActivityCompat.checkSelfPermission(context,
          Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
      }
      
      // Always send a response if responseNeeded
      if (responseNeeded) {
        bluetoothGattServer.sendResponse(
            device, 
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            offset,
            value);
      }
      
      String message = new String(value, StandardCharsets.UTF_8);
      String deviceUUID = Utils.getDeviceUUID(device.getAddress());
      Log.d(TAG, "Received message: " + message + " from " + deviceUUID);
      
      if (EOM_MARKER.equals(message)) {
        // End of message reached, notify listeners
        if (callback != null) {
          JSObject ret = new JSObject();
          ret.put("from", deviceUUID);
          ret.put("message", pendingMessage != null ? pendingMessage : "");
          callback.notifyEvent("onMessageReceived", ret);
        }
        // Reset pending message
        pendingMessage = null;
      } else {
        // Append to or create pending message
        if (pendingMessage == null) {
          pendingMessage = message;
        } else {
          pendingMessage += message;
        }
      }
    }
    
    @Override
    public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                       BluetoothGattDescriptor descriptor) {
      Log.d(TAG, "Descriptor read request");
      if (ActivityCompat.checkSelfPermission(context,
          Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
      }
      
      // For CCCD descriptor, return the notification/indication status
      if (descriptor.getUuid().equals(Utils.CCCD_UUID)) {
        byte[] value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
      }
    }

    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                       BluetoothGattDescriptor descriptor, boolean preparedWrite, 
                                       boolean responseNeeded, int offset, byte[] value) {
      Log.d(TAG, "Descriptor write request");
      if (ActivityCompat.checkSelfPermission(context,
          Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
      }
      
      // For CCCD descriptor, handle enabling/disabling notifications
      if (descriptor.getUuid().equals(Utils.CCCD_UUID)) {
        // value is BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE or DISABLE_NOTIFICATION_VALUE
        if (responseNeeded) {
          bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }
        
        // Log the notification state
        if (java.util.Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
          Log.d(TAG, "Notifications enabled for " + Utils.getDeviceUUID(device.getAddress()));
        } else if (java.util.Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
          Log.d(TAG, "Notifications disabled for " + Utils.getDeviceUUID(device.getAddress()));
        }
      }
    }

    @Override
    public void onServiceAdded(int status, BluetoothGattService service) {
      Log.d(TAG, "Service added: " + service.getUuid() + " status: " + status);
    }

    @Override
    public void onMtuChanged(BluetoothDevice device, int mtu) {
      Log.d(TAG, "MTU changed: " + mtu);
    }
  };

  private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
      Log.d(TAG, "Advertising started successfully");
      isAdvertising = true;
      if (callback != null) {
        callback.notifyEvent("onAdvertisingStarted", null);
      }
    }

    @Override
    public void onStartFailure(int errorCode) {
      Log.e(TAG, "Advertising failed with error code: " + errorCode);
      isAdvertising = false;
      if (callback != null) {
        callback.notifyEvent("onAdvertisingFailed", null);
      }
    }
  };

  private void sendNextChunk() {
    if (pendingMessage == null || currentDeviceUuid == null) {
      return;
    }

    Log.d(TAG, "Sending next chunk" + " (" + (messageIndex) + "/" + pendingMessage.length() + ")");
    // Check if we're done with the message
    if (messageIndex >= pendingMessage.length()) {
      // Send EOM marker
      sendEOMMarker();
      return;
    }

    BluetoothGattService service = bluetoothGattClient.getService(serviceUUID);
    if (service == null) {
      Log.e(TAG, "Service not found");
      return;
    }

    BluetoothGattCharacteristic messageChar = service.getCharacteristic(Utils.MESSAGE_CHAR_UUID);
    if (messageChar == null) {
      Log.e(TAG, "Characteristic not found");
      return;
    }

    // Calculate chunk size
    int endIndex = Math.min(messageIndex + MAX_CHUNK_SIZE, pendingMessage.length());
    String chunk = pendingMessage.substring(messageIndex, endIndex);

    // Update the message index for next chunk
    messageIndex = endIndex;

    Log.d(TAG, "Sending chunk: " + chunk + " (" + (messageIndex) + "/" + pendingMessage.length() + ")");

    // Send the chunk
    messageChar.setValue(chunk.getBytes(StandardCharsets.UTF_8));

    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
    }

    boolean success = bluetoothGattClient.writeCharacteristic(messageChar);

    if (!success) {
      Log.e(TAG, "Failed to write characteristic");
      // Reset sending state
      pendingMessage = null;
      messageIndex = 0;
      currentDeviceUuid = null;
    }
  }

  private void sendEOMMarker() {
    BluetoothGattService service = bluetoothGattClient.getService(serviceUUID);
    if (service == null) {
      Log.e(TAG, "Service not found");
      return;
    }

    BluetoothGattCharacteristic messageChar = service.getCharacteristic(Utils.MESSAGE_CHAR_UUID);
    if (messageChar == null) {
      Log.e(TAG, "Characteristic not found");
      return;
    }

    Log.d(TAG, "Sending EOM marker");

    messageChar.setValue(EOM_MARKER.getBytes(StandardCharsets.UTF_8));

    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
    }

    boolean success = bluetoothGattClient.writeCharacteristic(messageChar);

    // Reset sending state regardless of success
    pendingMessage = null;
    messageIndex = 0;
    currentDeviceUuid = null;
  }

  public boolean isAdvertising() {
    return isAdvertising;
  }
}
