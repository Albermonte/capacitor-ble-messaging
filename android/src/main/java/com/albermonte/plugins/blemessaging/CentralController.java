package com.albermonte.plugins.blemessaging;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CentralController {
  private static final String TAG = "BLEMessaging/CentralController";
  private final UUID serviceUUID;
  private final Context context;
  private final BLEMessagingCallback callback;

  private final BluetoothAdapter bluetoothAdapter;
  private BluetoothGattServer bluetoothGattServer;
  private BluetoothGatt bluetoothGattClient;
  private BluetoothLeAdvertiser advertiser;
  private BluetoothLeScanner bleScanner;
  private Boolean isScanning = false;

  private final List<BluetoothDevice> foundDevices = new ArrayList<>();
  private final List<BluetoothDevice> connectedDevices = new ArrayList<>();

  private String pendingMessage = null;
  private int messageIndex = 0;
  private String currentDeviceUuid = null;

  private final List<String> receivingMessage = new ArrayList<>();

  public CentralController(Context context, BluetoothAdapter bluetoothAdapter,
      UUID uuid, BLEMessagingCallback callback) {
    Log.d(CentralController.TAG, "Initializing CentralController");
    this.bluetoothAdapter = bluetoothAdapter;
    this.serviceUUID = uuid;
    this.context = context;
    this.callback = callback;
    Log.d(CentralController.TAG, "Initialized CentralController");
  }

  public boolean startScan(Long timeout) {
    if (isScanning) {
      Log.d(TAG, "Already scanning");
      return false;
    }
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
      throw new RuntimeException("Bluetooth adapter not available");
    }

    bleScanner = bluetoothAdapter.getBluetoothLeScanner();
    if (bleScanner == null) {
      throw new RuntimeException("BLE scanner not available");
    }

    if (!isLocationEnabled(context)) {
      new AlertDialog.Builder(context)
          .setTitle("Location Required")
          .setMessage("Please enable location to scan for devices.")
          .setPositiveButton("Open Settings", (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            context.startActivity(intent);
          })
          .setNegativeButton("Cancel", null)
          .show();
      return false;
    }

    // Create scan filters
    List<ScanFilter> filters = new ArrayList<>();
    ScanFilter filter = new ScanFilter.Builder()
        .setServiceUuid(new ParcelUuid(serviceUUID))
        .build();
    filters.add(filter);

    // Configure scan settings
    ScanSettings settings = new ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build();

    // Start scan
    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
      throw new RuntimeException("BLUETOOTH_SCAN permission missing");
    }
    bleScanner.startScan(filters, settings, scanCallback);
    isScanning = true;
    if (callback != null) {
      callback.notifyEvent("onScanStarted", null);
    }
    Log.d(TAG, "Scanning started, timeout: " + timeout);

    if (timeout != null && timeout > 0) {
      new android.os.Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
          stopScan();
        }
      }, timeout);
    }

    return true;
  }

  public boolean stopScan() {
    if (isScanning && bleScanner != null) {
      if (ActivityCompat.checkSelfPermission(context,
          Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        throw new RuntimeException("BLUETOOTH_SCAN permission missing");
      }
      bleScanner.stopScan(scanCallback);
      isScanning = false;
      if (callback != null) {
        callback.notifyEvent("onScanStopped", null);
      }
      Log.d(TAG, "Scanning stopped");
      return true;
    }
    return false;
  }

  public boolean connectToDeviceByUUID(String uuid) {
    if (foundDevices.isEmpty()) {
      Log.d(TAG, "No devices found");
      throw new RuntimeException("No devices found");
    }

    BluetoothDevice deviceToConnect = null;
    for (BluetoothDevice device : foundDevices) {
      if (Utils.getDeviceUUID(device.getAddress()).equals(uuid)) {
        deviceToConnect = device;
        break;
      }
    }
    if (deviceToConnect != null) {
      connectToDevice(deviceToConnect);
      Log.d(TAG, "Connecting to " + uuid);
      return true;
    } else {
      Log.d(TAG, "Device not found: " + uuid);
      throw new RuntimeException("Device not found: " + uuid);
    }
  }

  public boolean sendMessage(String uuid, String message) {
    if (bluetoothGattClient == null) {
      Log.e(TAG, "Client not connected");
      throw new RuntimeException("Client not connected");
    }

    if (uuid == null || uuid.isEmpty()) {
      Log.e(TAG, "Invalid UUID");
      throw new RuntimeException("Invalid UUID");
    }

    if (message == null || message.isEmpty()) {
      Log.e(TAG, "Invalid message");
      throw new RuntimeException("Invalid message");
    }

    if (!Utils.isDeviceConnected(uuid, connectedDevices, context)) {
      Log.e(TAG, "Device not connected");
      throw new RuntimeException("Device not connected");
    }

    // Set up the chunked message sending
    pendingMessage = message;
    messageIndex = 0;
    currentDeviceUuid = uuid;

    // Start sending the first chunk
    return sendNextChunk();
  }

  private boolean sendNextChunk() {
    if (pendingMessage == null || currentDeviceUuid == null) {
      return false;
    }

    Log.d(TAG, "Sending next chunk" + " (" + (messageIndex) + "/" + pendingMessage.length() + ")");
    // Check if we're done with the message
    if (messageIndex >= pendingMessage.length()) {
      // Send EOM marker
      sendEOMMarker();
      return true;
    }

    BluetoothGattService service = bluetoothGattClient.getService(serviceUUID);
    if (service == null) {
      Log.e(TAG, "Service not found");
      return false;
    }

    BluetoothGattCharacteristic messageChar = service.getCharacteristic(Utils.MESSAGE_CHAR_UUID);
    if (messageChar == null) {
      Log.e(TAG, "Characteristic not found");
      return false;
    }

    // Calculate chunk size
    int endIndex = Math.min(messageIndex + Utils.MAX_CHUNK_SIZE, pendingMessage.length());
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
      return false;
    }
    return true;
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

    messageChar.setValue(Utils.EOM_MARKER.getBytes(StandardCharsets.UTF_8));

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

  private final ScanCallback scanCallback = new ScanCallback() {
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
      super.onScanResult(callbackType, result);
      BluetoothDevice device = result.getDevice();

      if (foundDevices.contains(device)) {
        return;
      }
      foundDevices.add(device);

      Log.d(TAG, "Found device: " + Utils.getDeviceUUID(device.getAddress()));
      if (callback != null) {
        JSObject ret = new JSObject();
        ret.put("uuid", Utils.getDeviceUUID(device.getAddress()));
        callback.notifyEvent("onDeviceFound", ret);
      }
    }

    @Override
    public void onScanFailed(int errorCode) {
      super.onScanFailed(errorCode);
      isScanning = false;
      if (callback != null) {
        callback.notifyEvent("onScanFailed", null);
      }
      Log.e(TAG, "Scan failed with error: " + errorCode);
    }
  };

  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      super.onConnectionStateChange(gatt, status, newState);
      BluetoothDevice device = gatt.getDevice();
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        Log.d(TAG, "Connected");
        if (ActivityCompat.checkSelfPermission(context,
            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
          throw new RuntimeException("BLUETOOTH_CONNECT permission missing");
        }
        connectedDevices.add(device);
        gatt.discoverServices();
        if (callback != null) {
          JSObject ret = new JSObject();
          ret.put("uuid", Utils.getDeviceUUID(device.getAddress()));
          callback.notifyEvent("onDeviceConnected", ret);
        }
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        Log.d(TAG, "Disconnected");
        connectedDevices.remove(device);
        if (callback != null) {
          JSObject ret = new JSObject();
          ret.put("uuid", Utils.getDeviceUUID(device.getAddress()));
          callback.notifyEvent("onDeviceDisconnected", ret);
        }
      }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      super.onServicesDiscovered(gatt, status);
      if (status == BluetoothGatt.GATT_SUCCESS) {
        BluetoothGattService service = gatt.getService(serviceUUID);
        if (service != null) {
          BluetoothGattCharacteristic messageChar = service.getCharacteristic(Utils.MESSAGE_CHAR_UUID);
          if (messageChar != null) {
            enableNotifications(gatt, messageChar);
          }
        }
      }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      super.onCharacteristicChanged(gatt, characteristic);
      byte[] data = characteristic.getValue();
      if (data != null) {
        String message = new String(data);
        Log.d(TAG, "Received message: " + message);
        if (message.equals(Utils.EOM_MARKER)) {
          if (callback != null) {
            JSObject ret = new JSObject();
            ret.put("from", Utils.getDeviceUUID(gatt.getDevice().getAddress()));
            ret.put("message", String.join("", receivingMessage));
            callback.notifyEvent("onMessageReceived", ret);
          }
          receivingMessage.clear();
        } else {
          receivingMessage.add(message);
        }
      }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
      super.onMtuChanged(gatt, mtu, status);

      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.d("BLE", "Negotiated MTU size: " + mtu);
      } else {
        Log.e("BLE", "MTU negotiation failed with status: " + status);
      }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
        BluetoothGattCharacteristic characteristic,
        int status) {
      super.onCharacteristicWrite(gatt, characteristic, status);

      if (status == BluetoothGatt.GATT_SUCCESS) {
        // If we have more chunks to send, send the next one
        if (pendingMessage != null && messageIndex <= pendingMessage.length()) {
          sendNextChunk();
        }
      } else {
        Log.e(TAG, "Write characteristic failed: " + status);
        // Reset sending state
        pendingMessage = null;
        messageIndex = 0;
        currentDeviceUuid = null;
      }
    }
  };

  private void connectToDevice(BluetoothDevice device) {
    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    Log.d(TAG, "Connecting to: " + Utils.getDeviceUUID(device.getAddress()));
    bluetoothGattClient = device.connectGatt(context, false, gattCallback);
  }

  private void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    if (ActivityCompat.checkSelfPermission(context,
        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    gatt.setCharacteristicNotification(characteristic, true);

    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Utils.CCCD_UUID);
    if (descriptor != null) {
      descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
      gatt.writeDescriptor(descriptor);
    }
  }

  private static boolean isLocationEnabled(Context context) {
    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      // For Android 9 (API 28) and above
      return locationManager.isLocationEnabled();
    } else {
      // For older versions
      boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
      boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
      return gpsEnabled || networkEnabled;
    }
  }

  public boolean isScanning() {
    return isScanning;
  }
}
