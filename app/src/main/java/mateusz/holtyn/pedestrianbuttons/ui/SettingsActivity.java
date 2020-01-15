package mateusz.holtyn.pedestrianbuttons.ui;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import mateusz.holtyn.pedestrianbuttons.R;

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
        private SeekBarPreference seekbar;
        private String valueString;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            seekbar = findPreference("voicespeed");
            valueString = String.valueOf(seekbar.getValue());
            seekbar.setSummary(convertValue(valueString));
            seekbar.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    seekbar.setSummary(convertValue(newValue.toString()));
                    return true;
                }
            });
        }

        public String convertValue(String value) {
            Float valueFloat = (float) (Float.parseFloat(value) * 0.1);
            String valueString = "×" + (valueFloat);
            return valueString;
        }


    }
}