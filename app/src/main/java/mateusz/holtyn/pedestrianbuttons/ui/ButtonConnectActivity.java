package mateusz.holtyn.pedestrianbuttons.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import mateusz.holtyn.pedestrianbuttons.R;
import mateusz.holtyn.pedestrianbuttons.bluetooth.BleConnectService;
import mateusz.holtyn.pedestrianbuttons.entity.ButtonEntity;
import mateusz.holtyn.pedestrianbuttons.entity.ButtonList;

public class ButtonConnectActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    private static final String PINCODE = "9318";
    private String deviceName;
    private String deviceAddress;
    private Integer deviceId;
    private double distance;
    private Timer mTimer;
    private Handler beepHandler;
    private MediaPlayer mp;
    private Button lowButton, midButton, highButton;
    private EditText passwordText;
    private int ledBrightness;
    private int mInterval = 100;
    private boolean backRequested = false;
    private BleConnectService bluetoothLeAdapter;
    private TextToSpeech textToSpeech;
    private SharedPreferences sharedPref;
    private String location;
    private boolean isBeeping = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetoothLeAdapter = ((BleConnectService.LocalBinder) service).getService();
            bluetoothLeAdapter.setActivityHandler(messageHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeAdapter = null;
        }
    };

    @SuppressLint("HandlerLeak")
    private Handler messageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle;
            byte[] b;

            switch (msg.what) {
                case BleConnectService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleConnectService.PARCEL_TEXT);
                    showMsg(text);
                    break;
                case BleConnectService.GATT_CONNECTED:
                    ButtonConnectActivity.this.findViewById(R.id.startBeepingButton).setEnabled(true);
                    // we're connected
                    showMsg("CONNECTED");
                    if (bluetoothLeAdapter != null) {
                        bluetoothLeAdapter.discoverServices();
                    }
                    break;

                case BleConnectService.GATT_DISCONNECT:
                    // we're disconnected
                    showMsg("DISCONNECTED");
                    ButtonConnectActivity.this.findViewById(R.id.startBeepingButton).setEnabled(false);
                    // stop the rssi reading timer
                    stopTimer();

                    if (backRequested) {
                        ButtonConnectActivity.this.finish();
                    }
                    break;

                case BleConnectService.GATT_SERVICES_DISCOVERED:

                    // validate services and if ok....
                    List<BluetoothGattService> sList = bluetoothLeAdapter.getSupportedGattServices();
                    boolean ledBrightnessService = false;

                    for (BluetoothGattService svc : sList) {
                        Log.d(ButtonListActivity.TAG,
                                "UUID=" + svc.getUuid().toString().toUpperCase() + " INSTANCE=" + svc.getInstanceId());
                        if (svc.getUuid().toString().equalsIgnoreCase(BleConnectService.LED_BRIGHTNESS_SERVICE)) {
                            ledBrightnessService = true;
                        }

                    }

                    if (ledBrightnessService) {
                        showMsg("Device has expected services");
                        startReadRssiTimer();


                        bluetoothLeAdapter.readCharacteristic(BleConnectService.LED_BRIGHTNESS_SERVICE,
                                BleConnectService.LED_BRIGHTNESS_CHARACTERISTIC);

                    } else {
                        showMsg("Device does not have expected GATT services");
                    }
                    break;

                case BleConnectService.GATT_REMOTE_RSSI:
                    bundle = msg.getData();
                    int rssi = bundle.getInt(BleConnectService.PARCEL_RSSI);
                    ButtonConnectActivity.this.updateRssi(rssi);
                    Log.d(ButtonListActivity.TAG, "reading remote rssi " + rssi);
                    break;

                case BleConnectService.GATT_CHARACTERISTIC_READ:
                    bundle = msg.getData();
                    Log.d(ButtonListActivity.TAG,
                            "Service=" + Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_SERVICE_UUID)).toString().toUpperCase()
                                    + " Characteristic="
                                    + Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_CHARACTERISTIC_UUID)).toString().toUpperCase());
                    if (Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_CHARACTERISTIC_UUID)).toString().toUpperCase()
                            .equals(BleConnectService.LED_BRIGHTNESS_CHARACTERISTIC)
                            && Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_SERVICE_UUID)).toString().toUpperCase()
                            .equals(BleConnectService.LED_BRIGHTNESS_SERVICE)) {
                        b = bundle.getByteArray(BleConnectService.PARCEL_VALUE);
                        assert b != null;
                        if (b.length > 0) {
                            Log.d(ButtonListActivity.TAG, "b.length if entered (GATT_CHARACTERISTIC_READ) b[0]: " + b[0]);
                            ButtonConnectActivity.this.findViewById(R.id.rectangle)
                                    .setVisibility(View.VISIBLE);

                            // start off the rssi reading timer
                            startReadRssiTimer();
                        }
                    }
                    break;
                case BleConnectService.GATT_CHARACTERISTIC_WRITTEN:
                    bundle = msg.getData();
                    Log.d(ButtonListActivity.TAG,
                            "Service=" + Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_SERVICE_UUID)).toString().toUpperCase()
                                    + " Characteristic="
                                    + Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_CHARACTERISTIC_UUID)).toString().toUpperCase());
                    if (Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_CHARACTERISTIC_UUID)).toString().toUpperCase()
                            .equals(BleConnectService.LED_BRIGHTNESS_CHARACTERISTIC)
                            && Objects.requireNonNull(bundle.get(BleConnectService.PARCEL_SERVICE_UUID)).toString().toUpperCase()
                            .equals(BleConnectService.LED_BRIGHTNESS_SERVICE)) {
                        b = bundle.getByteArray(BleConnectService.PARCEL_VALUE);
                        if (b != null && b.length > 0) {
                            ButtonConnectActivity.this.setLedBrightness((int) b[0]);
                            Log.d(ButtonListActivity.TAG, "b.length if entered (GATT_CHARACTERISTIC_WRITTEN) b[0]: " + b[0]);
                        }
                    }
                    break;

            }
        }
    };

    private void closeKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peripheral_control);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        String nameAddress;
        beepHandler = new Handler();
        Handler timeDelay = new Handler();
        lowButton = findViewById(R.id.lowButton);
        midButton = findViewById(R.id.midButton);
        highButton = findViewById(R.id.highButton);
        passwordText = findViewById(R.id.passwordSettings);
        passwordText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (passwordText.getText().toString().equals(PINCODE)) {
                    closeKeyboard();
                    lowButton.setVisibility(View.VISIBLE);
                    lowButton.setEnabled(true);
                    midButton.setVisibility(View.VISIBLE);
                    midButton.setEnabled(true);
                    highButton.setVisibility(View.VISIBLE);
                    highButton.setEnabled(true);
                    passwordText.setVisibility(View.INVISIBLE);
                    simpleToast("PIN correct");
                } else if (passwordText.getText().toString().isEmpty()) {
                    passwordText.setText(null);
                    closeKeyboard();
                    simpleToast("PIN empty");
                } else {
                    passwordText.setText(null);
                    closeKeyboard();
                    simpleToast("Wrong pin");
                }

                return true;
            }
        });
        // read intent data
        final Intent intent = getIntent();
        deviceName = intent.getStringExtra(EXTRA_NAME);
        deviceAddress = intent.getStringExtra(EXTRA_ID);
        // get device id from name
        getId();
        //get button's location based on id
        getLocation();
        //initialize tts
        initTTS();
        // show the device name
        nameAddress = "DEVICE NAME: " + deviceName;

        ((TextView) this.findViewById(R.id.nameTextView))
                .setText(nameAddress);

        final Button startBeeping = this.findViewById(R.id.startBeepingButton);
        mp = MediaPlayer.create(this, R.raw.beeploud);
        startBeeping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isBeeping) {
                    startBeep();
                    startBeeping.setText("Stop beeping");
                    isBeeping = true;
                } else {
                    stopBeep();
                    startBeeping.setText("Start beeping");
                    isBeeping = false;
                }

            }
        });

        // connect to the Bluetooth adapter service
        Intent gattServiceIntent = new Intent(this, BleConnectService.class);
        bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);

        resetVersion();
        showMsg("Ready");
        timeDelay.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectToButton();
            }
        }, 100);

    }

    private void setLedBrightness(int ledBrightness) {
        this.ledBrightness = ledBrightness;
        lowButton.setTextColor(Color.BLACK);
        midButton.setTextColor(Color.BLACK);
        highButton.setTextColor(Color.BLACK);
        switch (ledBrightness) {
            case 0:
                lowButton.setTextColor(Color.RED);
                break;
            case 1:
                midButton.setTextColor(Color.RED);
                break;
            case 2:
                highButton.setTextColor(Color.RED);
                break;
        }
    }

    //helper method to reset version programmatically, used for testing
    public void resetVersion() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("version", 0);
        editor.apply();
    }

    public void onBackPressed() {
        Log.d(ButtonListActivity.TAG, "onBackPressed");
        backRequested = true;
        if (bluetoothLeAdapter.isConnected()) {
            try {
                bluetoothLeAdapter.disconnect();
            } catch (Exception e) {
                Log.d(ButtonListActivity.TAG, "error on going back");
            }
        } else {
            finish();
        }
    }


    private Runnable mBeeper = new Runnable() {
        @Override
        public void run() {
            try {
                mp.start(); //this function can change value of mInterval.
            } finally {
                beepHandler.postDelayed(mBeeper, mInterval);
            }
        }
    };

    private void getLocation() {
        String locations = sharedPref.getString("locations", "no locations");
        Gson gson = new Gson();
        ButtonList newButtonList = new ButtonList();
        newButtonList.setButtonList(new ArrayList<ButtonEntity>());
        newButtonList = gson.fromJson(locations, ButtonList.class);
        List<ButtonEntity> newBList = newButtonList.getButtonList();
        location = newBList.get(deviceId).getLocation();
    }

    private void startBeep() {
        mBeeper.run();
    }

    private void stopBeep() {
        beepHandler.removeCallbacks(mBeeper);
    }

    public void onTTS(View view) {
        readDeviceName();

    }

    private void readDeviceName() {
        updateVoice();
        if (deviceName != null) {
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
        Log.d(ButtonListActivity.TAG, msg);
    }

    private void connectToButton() {
        showMsg("Connecting...");
        if (bluetoothLeAdapter != null) {
            if (bluetoothLeAdapter.connect(deviceAddress)) {
                if (sharedPref.getBoolean(SettingsActivity.KEY_PREF_AUTOVOICE, true)) {
                    readDeviceName();
                }
            } else {
                showMsg("connectToButton: failed to connect");
            }
        } else {
            showMsg("connectToButton: bluetoothLeAdapter=null");
        }
    }


    private void startReadRssiTimer() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                bluetoothLeAdapter.readRemoteRssi();
            }

        }, 0, 500); //timer that sets how often rssi is read
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

    private double calculateDistance(int rssi) {
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
        distance = calculateDistance(rssi);
        String distanceRssi = truncateDecimal(distance) + "m";
        ((TextView) findViewById(R.id.distanceTextView)).setText(distanceRssi);
        LinearLayout layout = ButtonConnectActivity.this.findViewById(R.id.rectangle);

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

        if (rssi < -80) {
            layout.setBackgroundColor(0xFFF44336);
        } else if (rssi < -70) {
            layout.setBackgroundColor(0xFFFF5722);
        } else if (rssi < -60) {
            layout.setBackgroundColor(0xFFFFEB3B);
        } else if (rssi < -55) {
            layout.setBackgroundColor(0xFFCDDC39);
        } else {
            layout.setBackgroundColor(0xFF8BC34A);
        }
        layout.invalidate();

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
        unbindService(serviceConnection);
        bluetoothLeAdapter = null;
    }

    private void updateVoice() {
        int voiceSpeed;
        float voiceSpeedFloat;
        String voiceName = sharedPref.getString(SettingsActivity.KEY_PREF_VOICE, "gbf1");
        voiceSpeed = sharedPref.getInt(SettingsActivity.KEY_PREF_SPEED, 10);
        voiceSpeedFloat = (float) (voiceSpeed * 0.1);
        textToSpeech.setSpeechRate(voiceSpeedFloat);
        switch (voiceName) {
            case "gbf1":
                Voice voice = new Voice("en-gb-x-fis#female_1-local", Locale.getDefault(), 1, 1, false, null);
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
        String idZerosString = deviceName.substring(3); //remove KSK from the name to get id with leading zeroes
        idZerosString = idZerosString.replaceFirst("^0+(?!$)", ""); //remove leading zeroes using regex
        deviceId = Integer.parseInt(idZerosString); //convert id string to final integer id
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
                    simpleToast("TTS Initialization failed!");
                }
            }
        });
    }

    private void simpleToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    public void onLow(View view) {
        bluetoothLeAdapter.writeCharacteristic(BleConnectService.LED_BRIGHTNESS_SERVICE,
                BleConnectService.LED_BRIGHTNESS_CHARACTERISTIC, BleConnectService.LED_BRIGHTNESS_LOW);
    }

    public void onMid(View view) {
        bluetoothLeAdapter.writeCharacteristic(BleConnectService.LED_BRIGHTNESS_SERVICE,
                BleConnectService.LED_BRIGHTNESS_CHARACTERISTIC, BleConnectService.LED_BRIGHTNESS_MID);
    }

    public void onHigh(View view) {
        bluetoothLeAdapter.writeCharacteristic(BleConnectService.LED_BRIGHTNESS_SERVICE,
                BleConnectService.LED_BRIGHTNESS_CHARACTERISTIC, BleConnectService.LED_BRIGHTNESS_HIGH);
    }

}
