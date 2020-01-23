package mateusz.holtyn.pedestrianbuttons.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import mateusz.holtyn.pedestrianbuttons.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button listButton = findViewById(R.id.button1);
        listButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openButtonPage();
            }
        });
        Button settingsButton = findViewById(R.id.button2);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

    }

    private void openButtonPage() {
        Intent intent = new Intent(this, ButtonPage.class);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}
