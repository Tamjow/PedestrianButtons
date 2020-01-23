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
    private BluetoothAdapter bluetooth_adapter;
    private Handler handler = new Handler();
    private ScanResultsConsumer scan_results_consumer;
    private boolean scanning = false;

    public BleScanner(Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetooth_adapter = bluetoothManager.getAdapter();
        }
// check bluetooth is available and on
        if (bluetooth_adapter == null || !bluetooth_adapter.isEnabled()) {
            Log.d(ButtonPage.TAG, "Bluetooth is NOT switched on");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }
        Log.d(ButtonPage.TAG, "Bluetooth is switched on");
    }

    public void startScanning(final ScanResultsConsumer scan_results_consumer, long stop_after_ms) {
        if (scanning) {
            Log.d(ButtonPage.TAG, "Already scanning so ignoring startScanning request");
            return;
        }
        Log.d(ButtonPage.TAG, "Scanning...");
        if (scanner == null) {
            scanner = bluetooth_adapter.getBluetoothLeScanner();
            Log.d(ButtonPage.TAG, "Created BluetoothScanner object");
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    Log.d(ButtonPage.TAG, "Stopping scanning");
                    scanner.stopScan(scan_callback);
                    setScanning(false);
                }
            }
        }, stop_after_ms);

        this.scan_results_consumer = scan_results_consumer;
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        setScanning(true);
        scanner.startScan(null, settings, scan_callback);
    }

    public void stopScanning() {
        setScanning(false);
        Log.d(ButtonPage.TAG, "Stopping scanning");
        scanner.stopScan(scan_callback);
    }

    private ScanCallback scan_callback = new ScanCallback() {
        public void onScanResult(int callbackType, final ScanResult result) {
            if (!scanning) {
                return;
            }
            scan_results_consumer.candidateBleDevice(result.getDevice(), Objects.requireNonNull(result.getScanRecord()).getBytes(), result.getRssi());
            Log.d(ButtonPage.TAG, "scan callback device added, rssi: " + result.getRssi());
        }
    };

    public boolean isScanning() {
        return scanning;
    }

    private void setScanning(boolean scanning) {
        this.scanning = scanning;
        if (!scanning) {
            scan_results_consumer.scanningStopped();
        } else {
            scan_results_consumer.scanningStarted();
        }
    }
}
