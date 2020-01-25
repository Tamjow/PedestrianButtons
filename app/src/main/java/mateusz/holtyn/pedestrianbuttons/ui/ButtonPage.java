package mateusz.holtyn.pedestrianbuttons.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

public class ButtonPage extends AppCompatActivity implements ScanResultsConsumer {

    private boolean ble_scanning = false;
    private ListAdapter ble_device_list_adapter;
    private BleScanner ble_scanner;
    private static final long SCAN_TIMEOUT = 5000;
    private static final int REQUEST_LOCATION = 0;
    private boolean permissions_granted = false;
    private Toast toast;
    static class ViewHolder {
        TextView text;
        TextView distance;
    }

    public static final String TAG = "PedestrianButtons";
    public static final String FIND = "SEARCH AGAIN";
    public static final String STOP_SCANNING = "Stop Scanning";
    public static final String SCANNING = "Scanning";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button_page);
        setButtonText();
        ble_scanner = new BleScanner(this.getApplicationContext());
        ble_device_list_adapter = new ListAdapter();
        ListView listView = this.findViewById(R.id.deviceList);
        listView.setAdapter(ble_device_list_adapter);
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

                Intent intent = new Intent(ButtonPage.this,
                        PeripheralControlActivity.class);
                intent.putExtra(PeripheralControlActivity.EXTRA_NAME, device.getName());
                intent.putExtra(PeripheralControlActivity.EXTRA_ID, device.getAddress());
                startActivity(intent);
            }
        });
        onScanNoArg();

    }

    private void setButtonText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) ButtonPage.this.findViewById(R.id.scanButton)).setText(FIND);
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

    private static BigDecimal truncateDecimal(double x) {
        if (x > 0) {
            return new BigDecimal(String.valueOf(x)).setScale(2, BigDecimal.ROUND_FLOOR);
        } else {
            return new BigDecimal(String.valueOf(x)).setScale(2, BigDecimal.ROUND_CEILING);
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

                if (device.getName() != null && device.getName().contains("KSK") && rssi > -70) {
                    ble_device_list_adapter.addDevice(device, rssi);
                    Log.d(TAG, "candidate ble device being added, rssi: " + rssi + " addr: " + device.getAddress());
                    ble_device_list_adapter.notifyDataSetChanged();

                }
            }
        });
    }

    private class ListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> ble_devices;
        private ArrayList<Integer> rssi_values;

        ListAdapter() {
            super();
            ble_devices = new ArrayList<>();
            rssi_values = new ArrayList<>();
        }

        void addDevice(BluetoothDevice device, Integer rssiValue) {
            if (!ble_devices.contains(device)) {
                Log.d("addDevice", "rssiValue: " + rssiValue + " device name: " + device.getName());
                ble_devices.add(device);
                rssi_values.add(rssiValue);
            }
        }


        BluetoothDevice getDevice(int position) {
            return ble_devices.get(position);
        }

        void clear() {
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
                view = ButtonPage.this.getLayoutInflater().inflate(R.layout.list,
                        null);
                viewHolder = new ViewHolder();
                viewHolder.text = view.findViewById(R.id.textView);
                viewHolder.distance = view.findViewById(R.id.distanceText);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            BluetoothDevice device = ble_devices.get(i);
            Integer rssi_val = rssi_values.get(i);
            String deviceName = device.getName();
            double distance = calculateDistance(rssi_val);
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.text.setText(deviceName);
            } else {
//                viewHolder.text.setText("Unknown device");
                viewHolder.text.setText("TEST123");
            }
            viewHolder.distance.setText(truncateDecimal(distance) + "m");
            if (i % 2 == 1) {
                view.setBackgroundColor(Color.parseColor("#4CAF50"));
                viewHolder.distance.setTextColor(Color.BLACK);
                viewHolder.text.setTextColor(Color.BLACK);
            } else {
                view.setBackgroundColor(Color.parseColor("#3C3F41"));
                viewHolder.distance.setTextColor(Color.WHITE);
                viewHolder.text.setTextColor(Color.WHITE);
            }
            return view;
        }
    }

    public void onScan(View view) {
        if (!ble_scanner.isScanning()) {
            Log.d(TAG, "Not currently scanning");
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

    public void onScanNoArg() {

        if (!ble_scanner.isScanning()) {
            Log.d(TAG, "Not currently scanning");
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
                    ActivityCompat.requestPermissions(ButtonPage.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
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

    private void simpleToast(String message) {
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
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
            simpleToast(SCANNING);
            ble_scanner.startScanning(this, SCAN_TIMEOUT);
        } else {
            Log.i(TAG, "Permission to perform Bluetooth scanning was not yet granted");
        }
    }


}
