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

import mateusz.holtyn.pedestrianbuttons.ui.ButtonPage;

public class BleScanner {
    private BluetoothLeScanner scanner = null;
    private BluetoothAdapter bluetoothAdapter;
    private Handler handler = new Handler();
    private ScanResultsConsumer scanResultsConsumer;
    private boolean scanning = false;

    public BleScanner(Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
// check bluetooth is available and on
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.d(ButtonPage.TAG, "Bluetooth is NOT switched on");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }
        Log.d(ButtonPage.TAG, "Bluetooth is switched on");
    }

    public void startScanning(final ScanResultsConsumer scanResultsConsumer, long stopAfterMs) {
        if (scanning) {
            Log.d(ButtonPage.TAG, "Already scanning so ignoring startScanning request");
            return;
        }
        Log.d(ButtonPage.TAG, "Scanning...");
        if (scanner == null) {
            scanner = bluetoothAdapter.getBluetoothLeScanner();
            Log.d(ButtonPage.TAG, "Created BluetoothScanner object");
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    Log.d(ButtonPage.TAG, "Stopping scanning");
                    scanner.stopScan(scanCallback);
                    setScanning(false);
                }
            }
        }, stopAfterMs);

        this.scanResultsConsumer = scanResultsConsumer;
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        setScanning(true);
        scanner.startScan(null, settings, scanCallback);
    }

    public void stopScanning() {
        setScanning(false);
        Log.d(ButtonPage.TAG, "Stopping scanning");
        scanner.stopScan(scanCallback);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        public void onScanResult(int callbackType, final ScanResult result) {
            if (!scanning) {
                return;
            }
            scanResultsConsumer.candidateBleDevice(result.getDevice(), Objects.requireNonNull(result.getScanRecord()).getBytes(), result.getRssi());
            Log.d(ButtonPage.TAG, "scan callback device added, rssi: " + result.getRssi());
        }
    };

    public boolean isScanning() {
        return scanning;
    }

    private void setScanning(boolean scanning) {
        this.scanning = scanning;
        if (!scanning) {
            scanResultsConsumer.scanningStopped();
        } else {
            scanResultsConsumer.scanningStarted();
        }
    }
}
