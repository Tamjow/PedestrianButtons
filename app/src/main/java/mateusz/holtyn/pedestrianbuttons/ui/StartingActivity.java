package mateusz.holtyn.pedestrianbuttons.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import mateusz.holtyn.pedestrianbuttons.R;

public class StartingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BluetoothAdapter bluetooth_adapter = BluetoothAdapter.getDefaultAdapter();
        Button listButton = findViewById(R.id.button1);
        listButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openButtonList();
            }
        });
        Button settingsButton = findViewById(R.id.button2);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

        if (bluetooth_adapter == null || !bluetooth_adapter.isEnabled()) {
            Log.d(ButtonListActivity.TAG, "Bluetooth is NOT switched on");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(enableBtIntent);
        }

    }

    private void openButtonList() {
        Intent intent = new Intent(this, ButtonListActivity.class);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}
