package mateusz.holtyn.pedestrianbuttons.ui;

import android.Manifest;
import android.annotation.SuppressLint;
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
import mateusz.holtyn.pedestrianbuttons.bluetooth.ScanInterface;

public class ButtonListActivity extends AppCompatActivity implements ScanInterface {

    private boolean bleScanning = false;
    private ListAdapter bleDeviceListAdapter;
    private BleScanner bleScanner;
    private static final long SCAN_TIMEOUT = 5000;
    private static final int REQUEST_LOCATION = 0;
    private boolean permissionsGranted = false;
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
        bleScanner = new BleScanner(this.getApplicationContext());
        bleDeviceListAdapter = new ListAdapter();
        ListView listView = this.findViewById(R.id.deviceList);
        listView.setAdapter(bleDeviceListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (bleScanning) {
                    setScanState(false);
                    bleScanner.stopScanning();
                }
                BluetoothDevice device = bleDeviceListAdapter.getDevice(position);
                if (toast != null) {
                    toast.cancel();
                }

                Intent intent = new Intent(ButtonListActivity.this,
                        ButtonConnectActivity.class);
                intent.putExtra(ButtonConnectActivity.EXTRA_NAME, device.getName());
                intent.putExtra(ButtonConnectActivity.EXTRA_ID, device.getAddress());
                startActivity(intent);
            }
        });
        Button scanBut = findViewById(R.id.scanButton);
        scanBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onScan();
            }
        });
        onScan();

    }

    private void setButtonText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) ButtonListActivity.this.findViewById(R.id.scanButton)).setText(FIND);
            }
        });

    }

    private void setScanState(boolean value) {
        bleScanning = value;
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

    // distance function data from https://github.com/AltBeacon/android-beacon-library

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
    public void candidateBleDevice(final BluetoothDevice device, final int rssi) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (device.getName() != null && device.getName().contains("KSK") && rssi > -70) {
                    bleDeviceListAdapter.addDevice(device, rssi);
                    Log.d(TAG, "candidate ble device being added, rssi: " + rssi + " addr: " + device.getAddress());
                    bleDeviceListAdapter.notifyDataSetChanged();

                }
            }
        });
    }

    private class ListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> bleDevices;
        private ArrayList<Integer> rssiValues;

        ListAdapter() {
            super();
            bleDevices = new ArrayList<>();
            rssiValues = new ArrayList<>();
        }

        void addDevice(BluetoothDevice device, Integer rssiValue) {
            if (!bleDevices.contains(device)) {
                Log.d("addDevice", "rssiValue: " + rssiValue + " device name: " + device.getName());
                bleDevices.add(device);
                rssiValues.add(rssiValue);
            }
        }


        BluetoothDevice getDevice(int position) {
            return bleDevices.get(position);
        }

        void clear() {
            bleDevices.clear();
            rssiValues.clear();
        }

        @Override
        public int getCount() {
            return bleDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return bleDevices.get(i);
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
                view = ButtonListActivity.this.getLayoutInflater().inflate(R.layout.list,
                        null);
                viewHolder = new ViewHolder();
                viewHolder.text = view.findViewById(R.id.textView);
                viewHolder.distance = view.findViewById(R.id.distanceText);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            BluetoothDevice device = bleDevices.get(i);
            Integer rssiVal = rssiValues.get(i);
            String deviceName = device.getName();
            double distance = calculateDistance(rssiVal);
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.text.setText(deviceName);
            } else {
                viewHolder.text.setText("Unknown");
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


    public void onScan() {

        if (!bleScanner.isScanning()) {
            Log.d(TAG, "Not currently scanning");
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                requestLocationPermission();
            } else {
                Log.i(TAG, "Location permission has already been granted. Starting scanning.");
                permissionsGranted = true;
            }

            startScanning();
        } else {
            Log.d(TAG, "Already scanning");
            bleScanner.stopScanning();
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
                    ActivityCompat.requestPermissions(ButtonListActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
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
                permissionsGranted = true;
                if (bleScanner.isScanning()) {
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
        if (permissionsGranted) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bleDeviceListAdapter.clear();
                    bleDeviceListAdapter.notifyDataSetChanged();
                }
            });
            simpleToast(SCANNING);
            bleScanner.startScanning(this, SCAN_TIMEOUT);
        } else {
            Log.i(TAG, "Permission to perform Bluetooth scanning was not yet granted");
        }
    }


}
