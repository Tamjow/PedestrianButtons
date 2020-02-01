package mateusz.holtyn.pedestrianbuttons.ui;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;

import mateusz.holtyn.pedestrianbuttons.R;
import mateusz.holtyn.pedestrianbuttons.entity.ButtonEntity;
import mateusz.holtyn.pedestrianbuttons.entity.ButtonList;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_PREF_VOICE = "voicelist";
    public static final String KEY_PREF_AUTOVOICE = "autovoice";
    public static final String KEY_PREF_SPEED = "voicespeed";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        //setTheme(R.style.CustomPreferenceTheme);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private SeekBarPreference seekBar;
        private static final String TAG_HTTP_URL_CONNECTION = "HTTP_URL_CONNECTION";// Debug log tag.
        private static final int REQUEST_CODE_SHOW_RESPONSE_TEXT = 1;// Child thread sent message type value to activity main thread Handler.
        private static final String KEY_RESPONSE_TEXT = "KEY_RESPONSE_TEXT";// The key of message stored server returned data.
        private static final String REQUEST_METHOD_GET = "GET";// Request method GET. The value must be uppercase.
        private Handler uiUpdater = null;
        private Gson gson;
        private Preference updateButton;
        private SharedPreferences sharedPreferences;
        private SharedPreferences.Editor editor;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            initHandler();
            gson = new Gson();
            seekBar = findPreference("voicespeed");
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Objects.requireNonNull(getActivity()));
            editor = sharedPreferences.edit();
            String valueString = String.valueOf(seekBar.getValue());
            seekBar.setSummary(convertValue(valueString));
            seekBar.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    seekBar.setSummary(convertValue(newValue.toString()));
                    return true;
                }
            });
            updateButton = findPreference("checkUpdateButton");
            updateButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    updateButton.setSummary("Checking for updates");
                    onGetJson();
                    return true;
                }
            });
        }


        String convertValue(String value) {
            float valueFloat = (float) (Float.parseFloat(value) * 0.1);
            return "×" + valueFloat;
        }

        @SuppressLint("HandlerLeak")
        private void initHandler() {
            if (uiUpdater == null) {
                uiUpdater = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        if (msg.what == REQUEST_CODE_SHOW_RESPONSE_TEXT) {
                            Bundle bundle = msg.getData();
                            if (bundle != null) {
                                Integer savedVersion = sharedPreferences.getInt("version", 1);
                                String responseText = bundle.getString(KEY_RESPONSE_TEXT);
                                ButtonList newButtonList = new ButtonList();
                                newButtonList.setButtonList(new ArrayList<ButtonEntity>());
                                newButtonList = gson.fromJson(responseText, ButtonList.class);
                                Integer downloadedVersion = newButtonList.getVersion();
                                if (downloadedVersion > savedVersion) {
                                    editor.putInt("version", downloadedVersion);
                                    editor.putString("locations", responseText);
                                    editor.commit();
                                    String updateSummary = "Update found and applied, old version: " + savedVersion + " new version: " + downloadedVersion;
                                    updateButton.setSummary(updateSummary);
                                } else {
                                    String noUpdateSummary = "Your database is up to date!, current version: " + savedVersion + ", server version: " + downloadedVersion;
                                    updateButton.setSummary(noUpdateSummary);
                                }
                            }
                        }
                    }
                };
            }
        }

        private void onGetJson() {
            String reqUrl = "http://185.107.143.31:8080/jerseymoxy/json";
            startSendHttpRequestThread(reqUrl);
        }

        private void startSendHttpRequestThread(final String reqUrl) {
            Thread sendHttpRequestThread = new Thread() {
                @Override
                public void run() {
                    HttpURLConnection httpConn = null;// Maintain http url connection.
                    InputStreamReader isReader = null;// Read text input stream.
                    BufferedReader bufReader = null;// Read text into buffer.
                    StringBuilder readTextBuf = new StringBuilder();// Save server response text.

                    try {
                        URL url = new URL(reqUrl);// Create a URL object use page url.
                        httpConn = (HttpURLConnection) url.openConnection();// Open http connection to web server.
                        httpConn.setRequestMethod(REQUEST_METHOD_GET);// Set http request method to get.
                        httpConn.setConnectTimeout(10000); // Set connection timeout and read timeout value.
                        httpConn.setReadTimeout(10000);
                        InputStream inputStream = httpConn.getInputStream();// Get input stream from web url connection.
                        isReader = new InputStreamReader(inputStream);// Create input stream reader based on url connection input stream.
                        bufReader = new BufferedReader(isReader);// Create buffered reader.
                        String line = bufReader.readLine();// Read line of text from server response.
                        while (line != null) {// Loop while return line is not null.
                            readTextBuf.append(line);// Append the text to string buffer.
                            line = bufReader.readLine();// Continue to read text line.
                        }
                        Message message = new Message();// Send message to main thread to update response text in TextView after read all.
                        message.what = REQUEST_CODE_SHOW_RESPONSE_TEXT;// Set message type.
                        Bundle bundle = new Bundle();// Create a bundle object.
                        bundle.putString(KEY_RESPONSE_TEXT, readTextBuf.toString());// Put response text in the bundle with the special key.
                        message.setData(bundle); // Set bundle data in message.
                        uiUpdater.sendMessage(message); // Send message to main thread Handler to process.
                    } catch (IOException ex) {
                        Log.e(TAG_HTTP_URL_CONNECTION, ex.getMessage(), ex);
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateButton.setSummary("Can't connect, database potentially down");
                                Toast.makeText(getContext(), "Can't connect, database potentially down", Toast.LENGTH_SHORT).show();
                            }
                        });

                    } finally {
                        try {
                            if (bufReader != null) {
                                bufReader.close();
                            }
                            if (isReader != null) {
                                isReader.close();
                            }
                            if (httpConn != null) {
                                httpConn.disconnect();
                            }
                        } catch (IOException ex) {
                            Log.e(TAG_HTTP_URL_CONNECTION, ex.getMessage(), ex);
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateButton.setSummary("Can't connect, database potentially down");
                                    Toast.makeText(getContext(), "Can't connect, database potentially down", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }
            };
            sendHttpRequestThread.start();// Start the child thread to request web page.
        }
    }
}