package com.raman.carriermod;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextInputEditText carrierInput = findViewById(R.id.carrierInput);
        MaterialSwitch chargingSwitch = findViewById(R.id.chargingSwitch);
        Button saveButton = findViewById(R.id.saveButton);
        Button resetButton = findViewById(R.id.resetButton);
        Button restartButton = findViewById(R.id.restartButton);

        String currentName = getSystemProperty("persist.sys.custom_carrier");
        if (currentName.equals(" ")) {
            carrierInput.setText("");
        } else if (!currentName.isEmpty()) {
            carrierInput.setText(currentName);
        }

        boolean isChargingModOn = getSystemProperty("persist.sys.charging_mod").equals("true");
        chargingSwitch.setChecked(isChargingModOn);

        saveButton.setOnClickListener(v -> {
            String newName = carrierInput.getText().toString();
            if (newName.isEmpty()) newName = " ";
            executeRootCommand("setprop persist.sys.custom_carrier '" + newName + "'");
            Toast.makeText(this, "Saved! Press Restart SystemUI to apply.", Toast.LENGTH_SHORT).show();
        });

        resetButton.setOnClickListener(v -> {
            carrierInput.setText("");
            executeRootCommand("setprop persist.sys.custom_carrier ''");
            Toast.makeText(this, "Reset! Press Restart SystemUI to apply.", Toast.LENGTH_SHORT).show();
        });

        chargingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            executeRootCommand("setprop persist.sys.charging_mod '" + (isChecked ? "true" : "false") + "'");
        });

        restartButton.setOnClickListener(v -> {
            Toast.makeText(this, "Restarting SystemUI...", Toast.LENGTH_SHORT).show();
            executeRootCommand("killall com.android.systemui");
        });
    }

    private void executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            Toast.makeText(this, "Action failed. Missing Root access?", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private String getSystemProperty(String key) {
        try {
            Process process = Runtime.getRuntime().exec("getprop " + key);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return (line != null) ? line : "";
        } catch (Exception e) {
            return "";
        }
    }
}