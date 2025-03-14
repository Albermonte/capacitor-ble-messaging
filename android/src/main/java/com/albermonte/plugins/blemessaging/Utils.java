package com.albermonte.plugins.blemessaging;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class Utils {
  private static final String TAG = "BLEMessaging/Utils";
  public static final UUID MESSAGE_CHAR_UUID = UUID.fromString("08590F7E-DB05-467E-8757-72F6FAEB13D4");
  public static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
  public static final int MAX_CHUNK_SIZE = 20; // BLE packet size limit
  public static final String EOM_MARKER = "EOM"; // End of message marker
  /**
   * Checks if a device with the specified address is in the connected devices
   * list
   * 
   * @param uuid The Bluetooth address to check
   * @return true if the device is connected, false otherwise
   */
  public static boolean isDeviceConnected(String uuid, List<BluetoothDevice> connectedDevices, Context context) {
    Log.d(TAG, "Checking if device is connected: " + uuid);
    Log.d(TAG, "Connected devices: " + connectedDevices.size());
    if (uuid == null || uuid.isEmpty() || connectedDevices.isEmpty()) {
      return false;
    }

    for (BluetoothDevice device : connectedDevices) {
      if (ActivityCompat.checkSelfPermission(context,
          Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Log.e(TAG, "BLUETOOTH_CONNECT permission missing");
        return false;
      }

      if (getDeviceUUID(device.getAddress()).equals(uuid)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Creates a deterministic UUID from a device address
   * 
   * @param address The Bluetooth MAC address
   * @return A UUID string derived from the MAC address
   */
  public static String getDeviceUUID(String address) {
    return UUID.nameUUIDFromBytes(address.getBytes(StandardCharsets.UTF_8)).toString().toUpperCase();
  }

}
