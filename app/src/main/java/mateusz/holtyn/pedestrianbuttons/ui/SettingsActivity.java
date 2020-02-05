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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private SeekBarPreference seekBar;
        private Handler uiUpdater = null;
        private Gson gson;
        private Preference updateButton;
        private SharedPreferences sharedPreferences;
        private SharedPreferences.Editor editor;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            updateHandler();
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
        private void updateHandler() {
            if (uiUpdater == null) {
                uiUpdater = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        if (msg.what == 1) {
                            Bundle bundle = msg.getData();
                            if (bundle != null) {
                                Integer savedVersion = sharedPreferences.getInt("version", 1);
                                String responseText = bundle.getString("responseText");
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
            String requestUrl = "http://185.107.143.31:8080/jerseymoxy/json";
            requestHttp(requestUrl);
        }

        private void requestHttp(final String requestUrl) {
            Thread requestHttpThread = new Thread() {
                @Override
                public void run() {
                    HttpURLConnection httpURLConnection = null;
                    InputStreamReader inputStreamReader = null;
                    BufferedReader bufferedReader = null;
                    StringBuilder stringBuilder = new StringBuilder();

                    try {
                        URL url = new URL(requestUrl);
                        httpURLConnection = (HttpURLConnection) url.openConnection();
                        httpURLConnection.setRequestMethod("GET");
                        httpURLConnection.setConnectTimeout(10000);
                        httpURLConnection.setReadTimeout(10000);
                        InputStream inputStream = httpURLConnection.getInputStream();
                        inputStreamReader = new InputStreamReader(inputStream);
                        bufferedReader = new BufferedReader(inputStreamReader);
                        String line = bufferedReader.readLine();
                        while (line != null) {
                            stringBuilder.append(line);
                            line = bufferedReader.readLine();
                        }
                        Message message = new Message(); //Create message to be sent to updateHandler
                        message.what = 1; //Message code to identify the message correctly
                        Bundle bundle = new Bundle();
                        bundle.putString("responseText", stringBuilder.toString()); //put the response inside of a bundle identified by a key
                        message.setData(bundle);
                        uiUpdater.sendMessage(message); // Send message to updateHandler
                    } catch (IOException ex) {
                        Log.e("requestHttp", ex.getMessage(), ex);
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateButton.setSummary("Can't connect, database potentially down");
                                Toast.makeText(getContext(), "Can't connect, database potentially down", Toast.LENGTH_SHORT).show();
                            }
                        });

                    } finally {
                        try {
                            if (bufferedReader != null) {
                                bufferedReader.close();
                            }
                            if (inputStreamReader != null) {
                                inputStreamReader.close();
                            }
                            if (httpURLConnection != null) {
                                httpURLConnection.disconnect();
                            }
                        } catch (IOException ex) {
                            Log.e("requestHttp", ex.getMessage(), ex);
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
            requestHttpThread.start();
        }
    }
}