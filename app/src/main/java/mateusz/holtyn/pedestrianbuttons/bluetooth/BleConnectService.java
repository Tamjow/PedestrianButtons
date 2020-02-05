package mateusz.holtyn.pedestrianbuttons.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.util.List;

import mateusz.holtyn.pedestrianbuttons.ui.ButtonListActivity;

public class BleConnectService extends Service {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothManager bluetoothManager;
    private Handler activityHandler = null;
    private boolean connected = false;
    // messages sent back to activity
    public static final int GATT_CONNECTED = 1;
    public static final int GATT_DISCONNECT = 2;
    public static final int GATT_SERVICES_DISCOVERED = 3;
    public static final int GATT_CHARACTERISTIC_READ = 4;
    public static final int GATT_CHARACTERISTIC_WRITTEN = 5;
    public static final int GATT_REMOTE_RSSI = 6;
    public static final int MESSAGE = 7;
    public static final int NOTIFICATION_OR_INDICATION_RECEIVED = 8;
    // message params
    public static final String PARCEL_CHARACTERISTIC_UUID = "CHARACTERISTIC_UUID";
    public static final String PARCEL_SERVICE_UUID = "SERVICE_UUID";
    public static final String PARCEL_VALUE = "VALUE";
    public static final String PARCEL_RSSI = "RSSI";
    public static final String PARCEL_TEXT = "TEXT";

    public static final String LED_BRIGHTNESS_SERVICE = "02366E80-CF3A-11E1-9AB4-0002A5D5C51B";
    public static final String LED_BRIGHTNESS_CHARACTERISTIC = "E23E78A0-CF4A-11E1-8FFC-0002A5D5C51B";
    public static final byte[] LED_BRIGHTNESS_LOW = {(byte) 0x00};
    public static final byte[] LED_BRIGHTNESS_MID = {(byte) 0x01};
    public static final byte[] LED_BRIGHTNESS_HIGH = {(byte) 0x02};

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public BleConnectService getService() {
            return BleConnectService.this;
        }
    }

    public void setActivityHandler(Handler handler) {
        activityHandler = handler;
    }

    private void sendConsoleMessage(String text) {
        Message msg = Message.obtain(activityHandler, MESSAGE);
        Bundle data = new Bundle();
        data.putString(PARCEL_TEXT, text);
        msg.setData(data);
        msg.sendToTarget();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public void onCreate() {
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                return;
            }
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            return;
        }
    }

    // connect to the device
    public boolean connect(final String address) {

        if (bluetoothAdapter == null || address == null) {
            sendConsoleMessage("connect: bluetoothAdapter=null");
            return false;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            sendConsoleMessage("connect: device=null");
            return false;
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        return true;
    }

    // disconnect from device
    public void disconnect() {
        sendConsoleMessage("disconnecting");
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            sendConsoleMessage("disconnect: bluetoothAdapter|bluetoothGatt null");
            return;
        }
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    public void readRemoteRssi() {
        Log.d(ButtonListActivity.TAG, "readRemoteRssi triggered");
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.readRemoteRssi();
    }

    public void discoverServices() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            return;
        }
        Log.d(ButtonListActivity.TAG, "Discovering GATT services");
        bluetoothGatt.discoverServices();
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null)
            return null;
        return bluetoothGatt.getServices();
    }

    public boolean readCharacteristic(String serviceUuid, String characteristicUuid) {
        Log.d(ButtonListActivity.TAG, "readCharacteristic:" + characteristicUuid + " of " + serviceUuid);
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            sendConsoleMessage("readCharacteristic: bluetoothAdapter|bluetoothGatt null");
            return false;
        }

        BluetoothGattService gattService = bluetoothGatt.getService(java.util.UUID.fromString(serviceUuid));
        if (gattService == null) {
            sendConsoleMessage("readCharacteristic: gattService null");
            return false;
        }
        BluetoothGattCharacteristic gattChar = gattService
                .getCharacteristic(java.util.UUID.fromString(characteristicUuid));
        if (gattChar == null) {
            sendConsoleMessage("readCharacteristic: gattChar null");
            return false;
        }
        return bluetoothGatt.readCharacteristic(gattChar);
    }

    public boolean writeCharacteristic(String serviceUuid, String characteristicUuid, byte[] value) {

        Log.d(ButtonListActivity.TAG, "writeCharacteristic:" + characteristicUuid + " of " + serviceUuid);
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            sendConsoleMessage("writeCharacteristic: bluetoothAdapter|bluetoothGatt null");
            return false;
        }

        BluetoothGattService gattService = bluetoothGatt.getService(java.util.UUID.fromString(serviceUuid));
        if (gattService == null) {
            sendConsoleMessage("writeCharacteristic: gattService null");
            return false;
        }
        BluetoothGattCharacteristic gattChar = gattService
                .getCharacteristic(java.util.UUID.fromString(characteristicUuid));
        if (gattChar == null) {
            sendConsoleMessage("writeCharacteristic: gattChar null");
            return false;
        }
        gattChar.setValue(value);

        return bluetoothGatt.writeCharacteristic(gattChar);

    }


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(ButtonListActivity.TAG, "onConnectionStateChange: status=" + status);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(ButtonListActivity.TAG, "onConnectionStateChange: CONNECTED");
                connected = true;
                Message msg = Message.obtain(activityHandler, GATT_CONNECTED);
                msg.sendToTarget();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(ButtonListActivity.TAG, "onConnectionStateChange: DISCONNECTED");
                Message msg = Message.obtain(activityHandler, GATT_DISCONNECT);
                msg.sendToTarget();
                if (bluetoothGatt != null) {
                    Log.d(ButtonListActivity.TAG, "Closing and destroying BluetoothGatt object");
                    connected = false;
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendConsoleMessage("RSSI read OK");
                Bundle bundle = new Bundle();
                bundle.putInt(PARCEL_RSSI, rssi);
                Message msg = Message.obtain(activityHandler, GATT_REMOTE_RSSI);
                msg.setData(bundle);
                msg.sendToTarget();
            } else {
                sendConsoleMessage("RSSI read err:" + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            sendConsoleMessage("Services Discovered");
            Message msg = Message.obtain(activityHandler, GATT_SERVICES_DISCOVERED);
            msg.sendToTarget();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Bundle bundle = new Bundle();
                bundle.putString(PARCEL_CHARACTERISTIC_UUID, characteristic.getUuid().toString());
                bundle.putString(PARCEL_SERVICE_UUID, characteristic.getService().getUuid().toString());
                bundle.putByteArray(PARCEL_VALUE, characteristic.getValue());
                Message msg = Message.obtain(activityHandler, GATT_CHARACTERISTIC_READ);
                msg.setData(bundle);
                msg.sendToTarget();
            } else {
                Log.d(ButtonListActivity.TAG, "failed to read characteristic:" + characteristic.getUuid().toString()
                        + " of service " + characteristic.getService().getUuid().toString() + " : status=" + status);
                sendConsoleMessage("characteristic read err:" + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Bundle bundle = new Bundle();
            bundle.putString(PARCEL_CHARACTERISTIC_UUID, characteristic.getUuid().toString());
            bundle.putString(PARCEL_SERVICE_UUID, characteristic.getService().getUuid().toString());
            bundle.putByteArray(PARCEL_VALUE, characteristic.getValue());
            // notifications and indications are both communicated from here in this way
            Message msg = Message.obtain(activityHandler, NOTIFICATION_OR_INDICATION_RECEIVED);
            msg.setData(bundle);
            msg.sendToTarget();
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(ButtonListActivity.TAG, "onCharacteristicWrite");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Bundle bundle = new Bundle();
                bundle.putString(PARCEL_CHARACTERISTIC_UUID, characteristic.getUuid().toString());
                bundle.putString(PARCEL_SERVICE_UUID, characteristic.getService().getUuid().toString());
                bundle.putByteArray(PARCEL_VALUE, characteristic.getValue());
                Message msg = Message.obtain(activityHandler, GATT_CHARACTERISTIC_WRITTEN);
                msg.setData(bundle);
                msg.sendToTarget();
            } else {
                sendConsoleMessage("characteristic write err:" + status);
            }
        }
    };

}