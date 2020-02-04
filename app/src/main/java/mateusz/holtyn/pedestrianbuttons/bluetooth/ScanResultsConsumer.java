package mateusz.holtyn.pedestrianbuttons.bluetooth;

import android.bluetooth.BluetoothDevice;

public interface ScanResultsConsumer {

    void candidateBleDevice(BluetoothDevice device, byte[] scanRecord, int rssi);

    void scanningStarted();

    void scanningStopped();
}
