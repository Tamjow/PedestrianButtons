package mateusz.holtyn.pedestrianbuttons.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.math.BigDecimal;
import java.util.ArrayList;

import mateusz.holtyn.pedestrianbuttons.R;
import mateusz.holtyn.pedestrianbuttons.bluetooth.BleScanner;
import mateusz.holtyn.pedestrianbuttons.bluetooth.ScanResultsConsumer;

public class ButtonList extends AppCompatActivity implements ScanResultsConsumer {

    private boolean ble_scanning = false;
    private ListAdapter ble_device_list_adapter;
    private BleScanner ble_scanner;
    private static final long SCAN_TIMEOUT = 5000;
    private static final int REQUEST_LOCATION = 0;
    private boolean permissions_granted = false;
    private int device_count = 0;
    private Toast toast;

    static class ViewHolder {
        public TextView text;
        public TextView bdaddr;
    }

    public static final String TAG = "PedestrianButtons";
    public static final String FIND = "FIND BLE DEVICES";
    public static final String STOP_SCANNING = "Stop Scanning";
    public static final String SCANNING = "Scanning";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button_list);

        setButtonText();
        ble_device_list_adapter = new ListAdapter();
        ListView listView = (ListView) this.findViewById(R.id.deviceList);
        listView.setAdapter(ble_device_list_adapter);
        ble_scanner = new BleScanner(this.getApplicationContext());
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (ble_scanning) {
                    setScanState(false);
                    ble_scanner.stopScanning();
                }
                BluetoothDevice device = ble_device_list_adapter.getDevice(position);
                if (toast != null) {
                    toast.cancel();
                }

                Intent intent = new Intent(ButtonList.this,
                        PeripheralControlActivity.class);
                intent.putExtra(PeripheralControlActivity.EXTRA_NAME, device.getName());
                intent.putExtra(PeripheralControlActivity.EXTRA_ID, device.getAddress());

                startActivity(intent);
            }
        });
    }

    private void setButtonText() {
        String text = "";
        text = FIND;
        final String button_text = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) ButtonList.this.findViewById(R.id.scanButton)).setText(button_text);
            }
        });

    }

    private void setScanState(boolean value) {
        ble_scanning = value;
        Log.d(TAG, "Setting scan state to " + value);
        ((Button) this.findViewById(R.id.scanButton)).setText(value ? STOP_SCANNING : FIND);
    }


    @Override
    public void scanningStarted() {
        setScanState(true);
    }

    @Override
    public void scanningStopped() {
        if (toast != null) {
            toast.cancel();
        }
        setScanState(false);
    }

    private static BigDecimal truncateDecimal(double x, int numberofDecimals) {
        if (x > 0) {
            return new BigDecimal(String.valueOf(x)).setScale(numberofDecimals, BigDecimal.ROUND_FLOOR);
        } else {
            return new BigDecimal(String.valueOf(x)).setScale(numberofDecimals, BigDecimal.ROUND_CEILING);
        }
    }

    public double calculateDistance(int rssi) {
        int txPower = -59; //hard coded power value. Usually ranges between -59 to -65

        if (rssi == 0) {
            return -1.0;
        }

        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            double distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
            return distance;
        }
    }

    @Override
    public void candidateBleDevice(final BluetoothDevice device, byte[] scan_record, final int rssi) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (device.getName() != null && device.getName().contains("KSK")) {
                    ble_device_list_adapter.addDevice(device, rssi);
                    Log.d(TAG, "candidate ble device being added, rssi: " + rssi + " addr: " + device.getAddress());
                    ble_device_list_adapter.notifyDataSetChanged();
                    device_count++;

                    // rssiVal=rssi;
                }
            }
        });
    }

    private class ListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> ble_devices;
        private ArrayList<Integer> rssi_values;

        public ListAdapter() {
            super();
            ble_devices = new ArrayList<BluetoothDevice>();
            rssi_values = new ArrayList<Integer>();
        }

        public void addDevice(BluetoothDevice device, Integer rssiValue) {
            if (!ble_devices.contains(device)) {
                Log.d("addDevice", "rssiValue: " + rssiValue + " device name: " + device.getName());
                ble_devices.add(device);
                rssi_values.add(rssiValue);
            }
        }

        public boolean contains(BluetoothDevice device) {
            return ble_devices.contains(device);
        }

        public BluetoothDevice getDevice(int position) {
            return ble_devices.get(position);
        }

        public void clear() {
            ble_devices.clear();
            rssi_values.clear();
        }

        @Override
        public int getCount() {
            return ble_devices.size();
        }

        @Override
        public Object getItem(int i) {
            return ble_devices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = ButtonList.this.getLayoutInflater().inflate(R.layout.list,
                        null);
                viewHolder = new ViewHolder();
                viewHolder.text = (TextView) view.findViewById(R.id.textView);
                viewHolder.bdaddr = (TextView) view.findViewById(R.id.bdaddr);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            BluetoothDevice device = ble_devices.get(i);
            Integer rssi_val = rssi_values.get(i);
            String deviceName = device.getName();
            Double distance = calculateDistance(rssi_val);
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.text.setText(deviceName);
            } else {
                viewHolder.text.setText("unknown device");
            }
            // viewHolder.bdaddr.setText("RSSI = "+rssi_val);
            //viewHolder.bdaddr.setText("distance = "+truncateDecimal(distance,2) +"m"+" RSSI = "+rssi_val);
            viewHolder.bdaddr.setText(truncateDecimal(distance, 2) + "m");
            return view;
        }
    }

    public void onScan(View view) {
        if (!ble_scanner.isScanning()) {
            Log.d(TAG, "Not currently scanning");
            device_count = 0;
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions_granted = false;
                requestLocationPermission();
            } else {
                Log.i(TAG, "Location permission has already been granted. Starting scanning.");
                permissions_granted = true;
            }

            startScanning();
        } else {
            Log.d(TAG, "Already scanning");
            ble_scanner.stopScanning();
        }
    }

    private void requestLocationPermission() {
        Log.i(TAG, "Location permission has NOT yet been granted. Requesting permission.");
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.i(TAG, "Displaying location permission rationale to provide additional context.");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permission Required");
            builder.setMessage("Please grant Location access so this application can perform Bluetooth scanning");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(TAG, "Requesting permissions after explanation");
                    ActivityCompat.requestPermissions(ButtonList.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
                }
            });
            builder.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            Log.i(TAG, "Received response for location permission request.");
            // Check if the only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission has been granted
                Log.i(TAG, "Location permission has now been granted. Scanning.....");
                permissions_granted = true;
                if (ble_scanner.isScanning()) {
                    startScanning();
                }
            } else {
                Log.i(TAG, "Location permission was NOT granted.");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void simpleToast(String message, int duration) {
        toast = Toast.makeText(this, message, duration);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    private void startScanning() {
        if (permissions_granted) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ble_device_list_adapter.clear();
                    ble_device_list_adapter.notifyDataSetChanged();
                }
            });
            simpleToast(SCANNING, 2000);
            ble_scanner.startScanning(this, SCAN_TIMEOUT);
        } else {
            Log.i(TAG, "Permission to perform Bluetooth scanning was not yet granted");
        }
    }
}
