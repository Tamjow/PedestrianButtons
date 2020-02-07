package mateusz.holtyn.pedestrianbuttons.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.util.Objects;

import mateusz.holtyn.pedestrianbuttons.ui.ButtonListActivity;

public class BleScanner {
    private BluetoothLeScanner scanner = null;
    private BluetoothAdapter bluetoothAdapter;
    private Handler handler = new Handler();
    private ScanInterface scanInterface;
    private boolean scanning = false;

    public BleScanner(Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
// check bluetooth is available and on
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.d(ButtonListActivity.TAG, "Bluetooth is NOT switched on");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }
        Log.d(ButtonListActivity.TAG, "Bluetooth is switched on");
    }

    public void startScanning(final ScanInterface scanInterface, long stopAfterMs) {
        if (scanning) {
            Log.d(ButtonListActivity.TAG, "Already scanning so ignoring startScanning request");
            return;
        }
        Log.d(ButtonListActivity.TAG, "Scanning...");
        if (scanner == null) {
            scanner = bluetoothAdapter.getBluetoothLeScanner();
            Log.d(ButtonListActivity.TAG, "Created BluetoothScanner object");
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    Log.d(ButtonListActivity.TAG, "Stopping scanning");
                    scanner.stopScan(scanCallback);
                    setScanning(false);
                }
            }
        }, stopAfterMs);

        this.scanInterface = scanInterface;
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        setScanning(true);
        scanner.startScan(null, settings, scanCallback);
    }

    public void stopScanning() {
        setScanning(false);
        Log.d(ButtonListActivity.TAG, "Stopping scanning");
        scanner.stopScan(scanCallback);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        public void onScanResult(int callbackType, final ScanResult result) {
            if (!scanning) {
                return;
            }
            scanInterface.candidateBleDevice(result.getDevice(), result.getRssi());
            Log.d(ButtonListActivity.TAG, "scan callback device added, rssi: " + result.getRssi());
        }
    };

    public boolean isScanning() {
        return scanning;
    }

    private void setScanning(boolean scanning) {
        this.scanning = scanning;
        if (!scanning) {
            scanInterface.scanningStopped();
        } else {
            scanInterface.scanningStarted();
        }
    }
}
