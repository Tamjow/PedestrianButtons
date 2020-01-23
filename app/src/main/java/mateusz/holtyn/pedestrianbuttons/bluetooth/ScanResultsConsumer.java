package mateusz.holtyn.pedestrianbuttons.bluetooth;

import android.bluetooth.BluetoothDevice;

public interface ScanResultsConsumer {

    void candidateBleDevice(BluetoothDevice device, byte[] scan_record, int rssi);

    void scanningStarted();

    void scanningStopped();
}
