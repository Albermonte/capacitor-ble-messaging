# capacitor-ble-messaging

Messaging between BLE supporting devices

## Install

```bash
npm install capacitor-ble-messaging
npx cap sync
```

## Permissions

### Android
Add the following permissions to your `AndroidManifest.xml` file:

```xml
<!-- Request legacy Bluetooth permissions on older devices. -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<!-- Include "neverForLocation" only if you can strongly assert that your app never derives physical location from Bluetooth scan results. -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- Needed only if your app makes the device discoverable to Bluetooth devices. -->
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

### iOS
Add the following permissions to your `Info.plist` file:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>We use bluetooth to communicate with nearby devices</string>
```

## API

<docgen-index>

* [`startAdvertising(...)`](#startadvertising)
* [`stopAdvertising()`](#stopadvertising)
* [`startScan(...)`](#startscan)
* [`stopScan()`](#stopscan)
* [`connectToDevice(...)`](#connecttodevice)
* [`disconnectFromDevice(...)`](#disconnectfromdevice)
* [`sendMessage(...)`](#sendmessage)
* [`isAdvertising()`](#isadvertising)
* [`isScanning()`](#isscanning)
* [`addListener('onScanStarted', ...)`](#addlisteneronscanstarted-)
* [`addListener('onScanStopped', ...)`](#addlisteneronscanstopped-)
* [`addListener('onScanFailed', ...)`](#addlisteneronscanfailed-)
* [`addListener('onAdvertisingStarted', ...)`](#addlisteneronadvertisingstarted-)
* [`addListener('onAdvertisingStopped', ...)`](#addlisteneronadvertisingstopped-)
* [`addListener('onAdvertisingFailed', ...)`](#addlisteneronadvertisingfailed-)
* [`addListener('onDeviceFound', ...)`](#addlistenerondevicefound-)
* [`addListener('onDeviceConnected', ...)`](#addlistenerondeviceconnected-)
* [`addListener('onDeviceDisconnected', ...)`](#addlistenerondevicedisconnected-)
* [`addListener('onMessageReceived', ...)`](#addlisteneronmessagereceived-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### startAdvertising(...)

```typescript
startAdvertising(options: { serviceUUID: string; }) => any
```

| Param         | Type                                  |
| ------------- | ------------------------------------- |
| **`options`** | <code>{ serviceUUID: string; }</code> |

**Returns:** <code>any</code>

--------------------


### stopAdvertising()

```typescript
stopAdvertising() => any
```

**Returns:** <code>any</code>

--------------------


### startScan(...)

```typescript
startScan(options: { serviceUUID: string; scanTimeout?: number; }) => any
```

Start scanning for devices advertising the specified service UUID.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code>{ serviceUUID: string; scanTimeout?: number; }</code> |

**Returns:** <code>any</code>

--------------------


### stopScan()

```typescript
stopScan() => any
```

Stop scanning for devices.

**Returns:** <code>any</code>

--------------------


### connectToDevice(...)

```typescript
connectToDevice(options: { uuid: string; }) => any
```

Connect to a device with the specified UUID.

| Param         | Type                           |
| ------------- | ------------------------------ |
| **`options`** | <code>{ uuid: string; }</code> |

**Returns:** <code>any</code>

--------------------


### disconnectFromDevice(...)

```typescript
disconnectFromDevice(options: { uuid: string; }) => any
```

Disconnect from a connected device.

| Param         | Type                           |
| ------------- | ------------------------------ |
| **`options`** | <code>{ uuid: string; }</code> |

**Returns:** <code>any</code>

--------------------


### sendMessage(...)

```typescript
sendMessage(options: { to: string; message: string; }) => any
```

Send a message to a connected device.

| Param         | Type                                          |
| ------------- | --------------------------------------------- |
| **`options`** | <code>{ to: string; message: string; }</code> |

**Returns:** <code>any</code>

--------------------


### isAdvertising()

```typescript
isAdvertising() => any
```

Check if the device is currently advertising.

**Returns:** <code>any</code>

--------------------


### isScanning()

```typescript
isScanning() => any
```

Check if the device is currently scanning.

**Returns:** <code>any</code>

--------------------


### addListener('onScanStarted', ...)

```typescript
addListener(eventName: 'onScanStarted', listenerFunc: () => void) => any
```

| Param              | Type                         |
| ------------------ | ---------------------------- |
| **`eventName`**    | <code>'onScanStarted'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>   |

**Returns:** <code>any</code>

--------------------


### addListener('onScanStopped', ...)

```typescript
addListener(eventName: 'onScanStopped', listenerFunc: () => void) => any
```

| Param              | Type                         |
| ------------------ | ---------------------------- |
| **`eventName`**    | <code>'onScanStopped'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>   |

**Returns:** <code>any</code>

--------------------


### addListener('onScanFailed', ...)

```typescript
addListener(eventName: 'onScanFailed', listenerFunc: () => void) => any
```

| Param              | Type                        |
| ------------------ | --------------------------- |
| **`eventName`**    | <code>'onScanFailed'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>  |

**Returns:** <code>any</code>

--------------------


### addListener('onAdvertisingStarted', ...)

```typescript
addListener(eventName: 'onAdvertisingStarted', listenerFunc: () => void) => any
```

| Param              | Type                                |
| ------------------ | ----------------------------------- |
| **`eventName`**    | <code>'onAdvertisingStarted'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>          |

**Returns:** <code>any</code>

--------------------


### addListener('onAdvertisingStopped', ...)

```typescript
addListener(eventName: 'onAdvertisingStopped', listenerFunc: () => void) => any
```

| Param              | Type                                |
| ------------------ | ----------------------------------- |
| **`eventName`**    | <code>'onAdvertisingStopped'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>          |

**Returns:** <code>any</code>

--------------------


### addListener('onAdvertisingFailed', ...)

```typescript
addListener(eventName: 'onAdvertisingFailed', listenerFunc: () => void) => any
```

| Param              | Type                               |
| ------------------ | ---------------------------------- |
| **`eventName`**    | <code>'onAdvertisingFailed'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>         |

**Returns:** <code>any</code>

--------------------


### addListener('onDeviceFound', ...)

```typescript
addListener(eventName: 'onDeviceFound', listenerFunc: ({ uuid }: { uuid: string; }) => void) => any
```

Emitted when a device advertising the specified service UUID is found.

| Param              | Type                                                  |
| ------------------ | ----------------------------------------------------- |
| **`eventName`**    | <code>'onDeviceFound'</code>                          |
| **`listenerFunc`** | <code>({ uuid }: { uuid: string; }) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### addListener('onDeviceConnected', ...)

```typescript
addListener(eventName: 'onDeviceConnected', listenerFunc: ({ uuid }: { uuid: string; }) => void) => any
```

Emitted when a device is connected.

| Param              | Type                                                  |
| ------------------ | ----------------------------------------------------- |
| **`eventName`**    | <code>'onDeviceConnected'</code>                      |
| **`listenerFunc`** | <code>({ uuid }: { uuid: string; }) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### addListener('onDeviceDisconnected', ...)

```typescript
addListener(eventName: 'onDeviceDisconnected', listenerFunc: ({ uuid }: { uuid: string; }) => void) => any
```

| Param              | Type                                                  |
| ------------------ | ----------------------------------------------------- |
| **`eventName`**    | <code>'onDeviceDisconnected'</code>                   |
| **`listenerFunc`** | <code>({ uuid }: { uuid: string; }) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### addListener('onMessageReceived', ...)

```typescript
addListener(eventName: 'onMessageReceived', listenerFunc: ({ from, message, timestamp }: { from: string; message: string; timestamp: number; }) => void) => any
```

| Param              | Type                                                                                                          |
| ------------------ | ------------------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'onMessageReceived'</code>                                                                              |
| **`listenerFunc`** | <code>({ from, message, timestamp }: { from: string; message: string; timestamp: number; }) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => any
```

**Returns:** <code>any</code>

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                      |
| ------------ | ------------------------- |
| **`remove`** | <code>() =&gt; any</code> |

</docgen-api>
