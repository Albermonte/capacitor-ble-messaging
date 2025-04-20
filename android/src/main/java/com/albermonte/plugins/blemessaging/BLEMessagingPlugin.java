package com.albermonte.plugins.blemessaging;

import android.Manifest;
import android.os.Build;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@CapacitorPlugin(name = "BLEMessaging", permissions = {
        @Permission(alias = "BLUETOOTH", strings = {
                Manifest.permission.BLUETOOTH
        }),
        @Permission(alias = "BLUETOOTH_ADMIN", strings = {
                Manifest.permission.BLUETOOTH_ADMIN
        }),
        @Permission(alias = "ACCESS_COARSE_LOCATION", strings = {
                Manifest.permission.ACCESS_COARSE_LOCATION
        }),
        @Permission(alias = "ACCESS_FINE_LOCATION", strings = {
                Manifest.permission.ACCESS_FINE_LOCATION
        }),
        @Permission(alias = "BLUETOOTH_ADVERTISE", strings = {
                Manifest.permission.BLUETOOTH_ADVERTISE
        }),
        @Permission(alias = "BLUETOOTH_SCAN", strings = {
                Manifest.permission.BLUETOOTH_SCAN
        }),
        @Permission(alias = "BLUETOOTH_CONNECT", strings = {
                Manifest.permission.BLUETOOTH_CONNECT
        })
})
public class BLEMessagingPlugin extends Plugin implements BLEMessagingCallback {
    private String TAG = "BLEMessagingPlugin";
    private CentralController centralImplementation;
    private PeripheralController peripheralImplementation;
    private String[] aliases;
    private UUID serviceUUID;
    private Boolean isPeripheral;
    private Long scanTimeout = 30000L;

    private void initializePeripheral(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            this.aliases = new String[] { "ACCESS_FINE_LOCATION", "BLUETOOTH_ADVERTISE", "BLUETOOTH",
                    "BLUETOOTH_ADMIN" };
        } else {
            this.aliases = new String[] { "ACCESS_FINE_LOCATION", "BLUETOOTH_ADVERTISE" };
        }

        requestPermissionForAliases(this.aliases, call, "permissionCallback");
    }

    private void initializeCentral(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            this.aliases = new String[] { "ACCESS_FINE_LOCATION", "BLUETOOTH_SCAN", "BLUETOOTH", "BLUETOOTH_ADMIN",
                    "BLUETOOTH_CONNECT" };
        } else {
            this.aliases = new String[] { "ACCESS_FINE_LOCATION", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT" };
        }

        requestPermissionForAliases(this.aliases, call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        List<String> notGrantedAliases = new ArrayList<>();
        for (String alias : aliases) {
            if (getPermissionState(alias) != PermissionState.GRANTED) {
                notGrantedAliases.add(alias);
            }
        }

        if (notGrantedAliases.isEmpty()) {
            runInitialization(call);
        } else {
            call.reject("Permissions not granted: " + notGrantedAliases);
        }
    }

    @PluginMethod
    public void startAdvertising(PluginCall call) {
        isPeripheral = true;
        getOptionsVariables(call);
        initializePeripheral(call);
    }

    @PluginMethod
    public void stopAdvertising(PluginCall call) {
        if (peripheralImplementation == null) {
            call.reject("Plugin not initialized.");
            return;
        }

        if (peripheralImplementation.stopAdvertising()) {
            call.resolve();
        } else {
            call.reject("Unable to stop advertising");
        }
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        isPeripheral = false;
        getOptionsVariables(call);
        initializeCentral(call);
    }

    // TODO: do this also on destroy
    @PluginMethod
    public void stopScan(PluginCall call) {
        if (centralImplementation == null) {
            call.reject("Plugin not initialized.");
            return;
        }

        if (centralImplementation.stopScan()) {
            call.resolve();
        } else {
            call.reject("Unable to stop scanning");
        }
    }

    @PluginMethod
    public void connectToDevice(PluginCall call) {
        if (centralImplementation == null) {
            call.reject("Plugin not initialized.");
            return;
        }
        var uuid = call.getString("uuid");
        if (uuid == null) {
            call.reject("UUID is required");
            return;
        }
        try {
            if (centralImplementation.connectToDeviceByUUID(uuid)) {
                call.resolve();
            } else {
                call.reject("Unable to connect to device");
            }
        } catch (Exception e) {
            call.reject("Error connecting to device: " + e.getMessage());
        }
    }

    @PluginMethod
    public void sendMessage(PluginCall call) {
        var uuid = call.getString("to");
        if (uuid == null) {
            call.reject("UUID to is required");
            return;
        }
        var message = call.getString("message");
        if (message == null) {
            call.reject("Message is required");
            return;
        }
        try {
            if (isPeripheral && peripheralImplementation != null) {
                if (peripheralImplementation.sendMessage(uuid, message)) {
                    call.resolve();
                } else {
                    call.reject("Unable to send message");
                }
                return;
            } else if (!isPeripheral && centralImplementation != null) {
                if (centralImplementation.sendMessage(uuid, message)) {
                    call.resolve();
                } else {
                    call.reject("Unable to send message");
                }
                return;
            } else {
                call.reject("Plugin not initialized.");
            }
        } catch (Exception e) {
            call.reject("Error sending message: " + e.getMessage());
        }
    }

    private void getOptionsVariables(PluginCall call) {
        var uuid = call.getString("serviceUUID");
        if (uuid == null) {
            call.reject("Service UUID is required");
            return;
        }
        serviceUUID = parseUuidString(uuid);
        if (serviceUUID == null) {
            call.reject("Invalid service UUID");
            return;
        }

        var timeout = call.getInt("scanTimeout");
        
        if (timeout != null) {
            scanTimeout = timeout * 1000L;
        }
    }

    private void runInitialization(PluginCall call) {
        Log.d(TAG, "Initializing plugin");
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            call.reject("BLE is not supported.");
            return;
        }
        // if(!BluetoothAdapter.getDefaultAdapter().isLeExtendedAdvertisingSupported())
        // {
        // call.reject("BLE advertising is not supported.");
        // return;
        // }

        Log.d(TAG, "Initializing BLE messaging");
        BluetoothManager bluetoothManager = (BluetoothManager) getActivity()
                .getSystemService(Context.BLUETOOTH_SERVICE);
        Log.d(TAG, "Bluetooth manager initialized");
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        Log.d(TAG, "Bluetooth adapter initialized");

        if (bluetoothAdapter == null) {
            call.reject("BLE is not available.");
            return;
        }
        Log.d(TAG, "Initializing BLEMessaging implementation");

        if (isPeripheral) {
            peripheralImplementation = new PeripheralController(getContext(), bluetoothManager, bluetoothAdapter,
                    serviceUUID, this);
            Log.d(TAG, "PeripheralController implementation initialized");
            if (peripheralImplementation.startAdvertising()) {
                call.resolve();
            } else {
                call.reject("Unable to start advertising");
            }
        } else {
            centralImplementation = new CentralController(getContext(), bluetoothAdapter, serviceUUID, this);
            Log.d(TAG, "CentralController implementation initialized");
            if (centralImplementation.startScan(scanTimeout)) {
                call.resolve();
            } else {
                call.reject("Unable to start scanning");
            }
        }
    }

    private UUID parseUuidString(String uuid) {
        if (uuid == null) {
            return null;
        }

        try {
            return UUID.fromString(uuid);
        } catch (RuntimeException e) {
            // First attempt failed, try with the standard Bluetooth UUID format
        }

        try {
            return UUID.fromString("0000" + uuid + "-0000-1000-8000-00805F9B34FB");
        } catch (RuntimeException e) {
            // Both attempts failed
        }

        return null;
    }

    @Override
    public void notifyEvent(String eventName, JSObject data) {
        notifyListeners(eventName, data);
    }

    @PluginMethod
    public void isAdvertising(PluginCall call) {
        JSObject data = new JSObject();
        data.put("isAdvertising", peripheralImplementation != null && peripheralImplementation.isAdvertising());
        call.resolve(data);
    }

    @PluginMethod
    public void isScanning(PluginCall call) {
        JSObject data = new JSObject();
        data.put("isScanning", centralImplementation != null && centralImplementation.isScanning());
        call.resolve(data);
    }

    @Override
    protected void handleOnDestroy() {
        if (centralImplementation != null) {
            centralImplementation.cleanup();
        }
        if (peripheralImplementation != null) {
            peripheralImplementation.cleanup();
        }
        super.handleOnDestroy();
    }
}
