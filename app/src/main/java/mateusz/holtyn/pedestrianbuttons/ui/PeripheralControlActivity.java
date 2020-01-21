package mateusz.holtyn.pedestrianbuttons.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import mateusz.holtyn.pedestrianbuttons.R;
import mateusz.holtyn.pedestrianbuttons.bluetooth.BleAdapterService;
import mateusz.holtyn.pedestrianbuttons.entity.ButtonEntity;
import mateusz.holtyn.pedestrianbuttons.entity.ButtonList;

public class PeripheralControlActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    private String device_name;
    private String device_address;
    private Integer device_id;
    private double distance;
    private Timer mTimer;
    private Handler beepHandler;
    private Handler timeDelay;
    private MediaPlayer mp;
    private String voiceName;
    private Voice voice;
    private int mInterval = 100;
    private boolean back_requested = false;
    private BleAdapterService bluetooth_le_adapter;
    private TextToSpeech textToSpeech;
    private SharedPreferences sharedPref;
    private Gson gson;
    private String location;
    private final ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetooth_le_adapter = ((BleAdapterService.LocalBinder) service).getService();
            bluetooth_le_adapter.setActivityHandler(message_handler);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_le_adapter = null;
        }
    };

    @SuppressLint("HandlerLeak")
    private Handler message_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            byte[] b = null;

            switch (msg.what) {
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(text);
                    break;
                case BleAdapterService.GATT_CONNECTED:
                    PeripheralControlActivity.this.findViewById(R.id.connectButton).setEnabled(false);
                    PeripheralControlActivity.this.findViewById(R.id.startBeepingButton).setEnabled(true);
                    // we're connected
                    showMsg("CONNECTED");
                    bluetooth_le_adapter.discoverServices();
                    break;

                case BleAdapterService.GATT_DISCONNECT:
                    PeripheralControlActivity.this.findViewById(R.id.connectButton).setEnabled(true);
                    // we're disconnected
                    showMsg("DISCONNECTED");
                    // hide the rssi distance colored rectangle
                    PeripheralControlActivity.this.findViewById(R.id.rectangle)
                            .setVisibility(View.INVISIBLE);
                    PeripheralControlActivity.this.findViewById(R.id.startBeepingButton).setEnabled(false);
                    // stop the rssi reading timer
                    stopTimer();

                    if (back_requested) {
                        PeripheralControlActivity.this.finish();
                    }
                    break;

                case BleAdapterService.GATT_SERVICES_DISCOVERED:

                    // validate services and if ok....
                    List<BluetoothGattService> slist = bluetooth_le_adapter.getSupportedGattServices();
                    boolean custom_service_one = false;
                    boolean custom_service_two = false;

                    for (BluetoothGattService svc : slist) {
                        Log.d(ButtonPage.TAG,
                                "UUID=" + svc.getUuid().toString().toUpperCase() + " INSTANCE=" + svc.getInstanceId());
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.CUSTOM_SERVICE_ONE)) {
                            custom_service_one = true;
                        }
                        if (svc.getUuid().toString().equalsIgnoreCase(BleAdapterService.CUSTOM_SERVICE_TWO)) {
                            custom_service_two = true;
                        }
                    }

                    if (custom_service_one && custom_service_two) {
                        showMsg("Device has expected services");
                        startReadRssiTimer();
                        // show the rssi distance colored rectangle
                        PeripheralControlActivity.this.findViewById(R.id.rectangle)
                                .setVisibility(View.VISIBLE);

                        bluetooth_le_adapter.readCharacteristic(BleAdapterService.CUSTOM_SERVICE_ONE,
                                BleAdapterService.CUSTOM_SERVICE_ONE_CHARACTERISTIC);

                    } else {
                        showMsg("Device does not have expected GATT services");
                    }
                    break;

                case BleAdapterService.GATT_REMOTE_RSSI:
                    bundle = msg.getData();
                    int rssi = bundle.getInt(BleAdapterService.PARCEL_RSSI);
                    PeripheralControlActivity.this.updateRssi(rssi);
                    Log.d(ButtonPage.TAG, "reading remote rssi " + rssi);
                    break;

                case BleAdapterService.GATT_CHARACTERISTIC_READ:
                    bundle = msg.getData();
                    Log.d(ButtonPage.TAG,
                            "Service=" + bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase()
                                    + " Characteristic="
                                    + bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase());
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase()
                            .equals(BleAdapterService.CUSTOM_SERVICE_ONE_CHARACTERISTIC)
                            && bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase()
                            .equals(BleAdapterService.CUSTOM_SERVICE_ONE)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        assert b != null;
                        if (b.length > 0) {
                            Log.d(ButtonPage.TAG, "b.length if entered (GATT_CHARACTERISTIC_READ) b[0]: " + b[0]);
                            // show the rssi distance colored rectangle
                            PeripheralControlActivity.this.findViewById(R.id.rectangle)
                                    .setVisibility(View.VISIBLE);

                            // start off the rssi reading timer
                            startReadRssiTimer();
                        }
                    }
                    break;
                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
                    bundle = msg.getData();
                    Log.d(ButtonPage.TAG,
                            "Service=" + bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase()
                                    + " Characteristic="
                                    + bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase());
                    if (bundle.get(BleAdapterService.PARCEL_CHARACTERISTIC_UUID).toString().toUpperCase()
                            .equals(BleAdapterService.CUSTOM_SERVICE_ONE_CHARACTERISTIC)
                            && bundle.get(BleAdapterService.PARCEL_SERVICE_UUID).toString().toUpperCase()
                            .equals(BleAdapterService.CUSTOM_SERVICE_ONE)) {
                        b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                        Log.d(ButtonPage.TAG, "b.length if entered (GATT_CHARACTERISTIC_WRITTEN) b[0]: " + b[0]);
                    }
                    break;

            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peripheral_control);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        String nameAddress;
        beepHandler = new Handler();
        timeDelay = new Handler();
        // read intent data
        final Intent intent = getIntent();
        device_name = intent.getStringExtra(EXTRA_NAME);
        device_address = intent.getStringExtra(EXTRA_ID);
        // get device id from name
        getId();
        //get button's location based on id
        getLocation();
        //initialize tts
        initTTS();
        // show the device name
        nameAddress = "Device : " + device_name + " [" + device_address + "] id: " + device_id;

        ((TextView) this.findViewById(R.id.nameTextView))
                .setText(nameAddress);
        // hide the coloured rectangle used to show green/amber/red rssi distance
        this.findViewById(R.id.rectangle).setVisibility(View.INVISIBLE);
        Button stopBeeping = this.findViewById(R.id.stopBeepingButton);
        Button startBeeping = this.findViewById(R.id.startBeepingButton);
        mp = MediaPlayer.create(this, R.raw.beeploud);
        startBeeping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBeep();
                ((Button) findViewById(R.id.stopBeepingButton)).setText("OH GOD STOP THE BEEP");
                findViewById(R.id.stopBeepingButton).setEnabled(true);
                findViewById(R.id.startBeepingButton).setEnabled(false);
            }
        });
        stopBeeping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBeep();
                ((Button) findViewById(R.id.stopBeepingButton)).setText(":)");
                findViewById(R.id.stopBeepingButton).setEnabled(false);
                findViewById(R.id.startBeepingButton).setEnabled(true);
            }
        });


        // connect to the Bluetooth adapter service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, service_connection, BIND_AUTO_CREATE);


        showMsg("Ready");
        timeDelay.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectToButton();
            }
        }, 100);

    }

    public void resetVersion() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("version", 0);
        editor.apply();
    }
    public void onBackPressed() {
        Log.d(ButtonPage.TAG, "onBackPressed");
        back_requested = true;
        if (bluetooth_le_adapter.isConnected()) {
            try {
                bluetooth_le_adapter.disconnect();
            } catch (Exception e) {
            }
        } else {
            finish();
        }
    }


    Runnable mBeeper = new Runnable() {
        @Override
        public void run() {
            try {
                mp.start(); //this function can change value of mInterval.
            } finally {
                // 100% guarantee that this always happens, even if
                // the update method throws an exception
                beepHandler.postDelayed(mBeeper, mInterval);
            }
        }
    };

    void getLocation() {
        String locations = sharedPref.getString("locations", "no locations");
        gson = new Gson();
        ButtonList newButtonList = new ButtonList();
        newButtonList.setButtonList(new ArrayList<ButtonEntity>());
        newButtonList = gson.fromJson(locations, ButtonList.class);
        List<ButtonEntity> newBList = newButtonList.getButtonList();
        location = newBList.get(device_id).getLocation();
    }
    void startBeep() {
        mBeeper.run();
    }

    void stopBeep() {
        beepHandler.removeCallbacks(mBeeper);
    }

    public void onTTS(View view) {
        readDeviceName();

    }

    public void readDeviceName() {
        updateVoice();
        if (device_name != null) {
            String ttsString = "you are on " + location;
            Log.i("TTS", "button clicked: " + ttsString);
            int speechStatus = textToSpeech.speak(ttsString, TextToSpeech.QUEUE_FLUSH, null, "deviceNameTts");
            if (speechStatus == TextToSpeech.ERROR) {
                Log.e("TTS", "Error in converting Text to Speech!");
            }
        } else {
            int speechStatus = textToSpeech.speak("location not found", TextToSpeech.QUEUE_FLUSH, null, "deviceNameEmptyTts");
            if (speechStatus == TextToSpeech.ERROR) {
                Log.e("TTS", "Error in converting Text to Speech!");
            }
        }

    }

    private void showMsg(final String msg) {
        Log.d(ButtonPage.TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.msgTextView)).setText(msg);
            }
        });
    }

    public void connectToButton() {
        showMsg("Connecting...");
        if (bluetooth_le_adapter != null) {
            if (bluetooth_le_adapter.connect(device_address)) {
                PeripheralControlActivity.this.findViewById(R.id.connectButton).setEnabled(false);
                if (sharedPref.getBoolean(SettingsActivity.KEY_PREF_AUTOVOICE, true)) {
                    readDeviceName();
                }
            } else {
                showMsg("connectToButton: failed to connect");
            }
        } else {
            showMsg("connectToButton: bluetooth_le_adapter=null");
        }
    }

    public void onConnect(View view) {
        connectToButton();
    }

    private void startReadRssiTimer() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                bluetooth_le_adapter.readRemoteRssi();
            }

        }, 0, 500);
    }

    private void stopTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
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
            distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
            return distance;
        }
    }

    private void updateRssi(int rssi) {
        //((TextView) findViewById(R.id.rssiTextView)).setText("RSSI = " + Integer.toString(rssi));
        distance = calculateDistance(rssi);
        String distanceRssi = "distance = " + truncateDecimal(distance) + "m rssi: " + rssi;
        ((TextView) findViewById(R.id.rssiTextView)).setText(distanceRssi);
        LinearLayout layout = PeripheralControlActivity.this.findViewById(R.id.rectangle);

        if (rssi < -80) {
            mInterval = 500;
        } else if (rssi < -70) {
            mInterval = 400;
        } else if (rssi < -60) {
            mInterval = 300;
        } else if (rssi < -50) {
            mInterval = 175;
        } else if (rssi < -45) {
            mInterval = 50;
        }

        byte proximity_band = 3;
        if (rssi < -80) {
            layout.setBackgroundColor(0xFFFF0000);
        } else if (rssi < -60) {
            layout.setBackgroundColor(0xFFFF8A01);
            proximity_band = 2;
        } else {
            layout.setBackgroundColor(0xFF00FF00);
            proximity_band = 1;
        }
        layout.invalidate();

    }

    public String byteArrayAsHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        int l = bytes.length;
        StringBuffer hex = new StringBuffer();
        for (int i = 0; i < l; i++) {
            if ((bytes[i] >= 0) & (bytes[i] < 16))
                hex.append("0");
            hex.append(Integer.toString(bytes[i] & 0xff, 16).toUpperCase());
        }
        return hex.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        stopBeep();
        stopTimer();
        unbindService(service_connection);
        bluetooth_le_adapter = null;
    }

    public void updateVoice() {
        int voiceSpeed;
        Float voiceSpeedFloat;
        voiceName = sharedPref.getString(SettingsActivity.KEY_PREF_VOICE, "gbf1");
        voiceSpeed = sharedPref.getInt(SettingsActivity.KEY_PREF_SPEED, 10);
        voiceSpeedFloat = (float) (voiceSpeed * 0.1);
        textToSpeech.setSpeechRate(voiceSpeedFloat);
        switch (voiceName) {
            case "gbf1":
                voice = new Voice("en-gb-x-fis#female_1-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
            case "gbf2":
                voice = new Voice("en-gb-x-fis#female_3-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
            case "gbm1":
                voice = new Voice("en-gb-x-fis#male_1-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
            case "gbm2":
                voice = new Voice("en-gb-x-fis#male_2-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
            case "usf1":
                voice = new Voice("en-us-x-sfg#female_1-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
            case "usf2":
                voice = new Voice("en-us-x-sfg#female_2-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
            case "usm1":
                voice = new Voice("en-us-x-sfg#male_1-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
            case "usm2":
                voice = new Voice("en-us-x-sfg#male_2-local", Locale.getDefault(), 1, 1, false, null);
                textToSpeech.setVoice(voice);
                break;
        }
    }

    private void getId() {
        String idZerosString = device_name.substring(3); //remove KSK from the name to get id with leading zeroes
        idZerosString = idZerosString.replaceFirst("^0+(?!$)", ""); //remove leading zeroes using regex
        device_id = Integer.parseInt(idZerosString); //convert id string to final integer id
    }

    private void initTTS() {
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int ttsLang = textToSpeech.setLanguage(Locale.ENGLISH);

                    if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                            || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!");
                    } else {
                        Log.i("TTS", "Language Supported.");
                    }
                    Log.i("TTS", "Initialization success.");
                } else {
                    Toast.makeText(getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


}
