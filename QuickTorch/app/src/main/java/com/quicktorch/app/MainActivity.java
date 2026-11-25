package com.quicktorch.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.quicktorch.app.service.TorchService;
import com.quicktorch.app.service.VolumeKeyAccessibilityService;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private PrefsManager prefs;
    private int maxTorchStrength = 1;

    private ImageButton btnTorchToggle;
    private TextView tvTorchState;
    private TextView tvFeedbackMsg;
    private TextView tvSubtitleStatus;
    private TextView tvServiceBadge;

    private ChipGroup chipGroupMode;
    private LinearLayout layoutStrobeSpeed;
    private TextView tvStrobeSpeedValue;
    private Slider sliderStrobeSpeed;

    private TextView tvBrightnessValue;
    private Slider sliderBrightness;
    private TextView tvBrightnessHint;

    private MaterialSwitch switchPowerTrigger;
    private TextView tvClickWindowValue;
    private Slider sliderClickWindow;

    private MaterialSwitch switchVolumeTrigger;
    private TextView tvAccessibilityStatus;
    private MaterialButton btnOpenAccessibility;

    private MaterialSwitch switchShakeTrigger;
    private ChipGroup chipGroupShakeSens;

    private MaterialSwitch switchPocketProtection;
    private MaterialSwitch switchScreenOffOnly;
    private MaterialSwitch switchHaptic;

    private ChipGroup chipGroupAutoOff;

    private MaterialSwitch switchMasterService;
    private MaterialSwitch switchAutoBoot;
    private MaterialSwitch switchLowBattery;
    private TextView tvLowBatteryValue;
    private Slider sliderLowBattery;
    private MaterialButton btnBatteryOpt;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (prefs.isServiceEnabled()) {
                    TorchService.start(this);
                }
            });

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            boolean torchOn = intent.getBooleanExtra(TorchService.EXTRA_TORCH_ON, false);
            boolean serviceRunning = intent.getBooleanExtra(TorchService.EXTRA_SERVICE_RUNNING, false);
            String message = intent.getStringExtra(TorchService.EXTRA_MESSAGE);

            updateTorchUi(torchOn);
            updateServiceUi(serviceRunning);
            if (message != null && !message.isEmpty()) {
                tvFeedbackMsg.setText(message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        maxTorchStrength = TorchController.getDeviceMaxStrengthLevel(this);

        bindViews();
        loadSavedSettings();
        setupListeners();
        requestNotifPermissionAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(TorchService.BROADCAST_STATE);
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        updateTorchUi(TorchService.isTorchActive());
        updateServiceUi(TorchService.isRunning());
        updateAccessibilityBadge();
        updateBatteryOptButton();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(stateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void bindViews() {
        btnTorchToggle = findViewById(R.id.btnTorchToggle);
        tvTorchState = findViewById(R.id.tvTorchState);
        tvFeedbackMsg = findViewById(R.id.tvFeedbackMsg);
        tvSubtitleStatus = findViewById(R.id.tvSubtitleStatus);
        tvServiceBadge = findViewById(R.id.tvServiceBadge);

        chipGroupMode = findViewById(R.id.chipGroupMode);
        layoutStrobeSpeed = findViewById(R.id.layoutStrobeSpeed);
        tvStrobeSpeedValue = findViewById(R.id.tvStrobeSpeedValue);
        sliderStrobeSpeed = findViewById(R.id.sliderStrobeSpeed);

        tvBrightnessValue = findViewById(R.id.tvBrightnessValue);
        sliderBrightness = findViewById(R.id.sliderBrightness);
        tvBrightnessHint = findViewById(R.id.tvBrightnessHint);

        switchPowerTrigger = findViewById(R.id.switchPowerTrigger);
        tvClickWindowValue = findViewById(R.id.tvClickWindowValue);
        sliderClickWindow = findViewById(R.id.sliderClickWindow);

        switchVolumeTrigger = findViewById(R.id.switchVolumeTrigger);
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);

        switchShakeTrigger = findViewById(R.id.switchShakeTrigger);
        chipGroupShakeSens = findViewById(R.id.chipGroupShakeSens);

        switchPocketProtection = findViewById(R.id.switchPocketProtection);
        switchScreenOffOnly = findViewById(R.id.switchScreenOffOnly);
        switchHaptic = findViewById(R.id.switchHaptic);

        chipGroupAutoOff = findViewById(R.id.chipGroupAutoOff);

        switchMasterService = findViewById(R.id.switchMasterService);
        switchAutoBoot = findViewById(R.id.switchAutoBoot);
        switchLowBattery = findViewById(R.id.switchLowBattery);
        tvLowBatteryValue = findViewById(R.id.tvLowBatteryValue);
        sliderLowBattery = findViewById(R.id.sliderLowBattery);
        btnBatteryOpt = findViewById(R.id.btnBatteryOpt);
    }

    private void loadSavedSettings() {
        // Torch Mode
        int mode = prefs.getTorchMode();
        if (mode == PrefsManager.MODE_STROBE) {
            chipGroupMode.check(R.id.chipModeStrobe);
            layoutStrobeSpeed.setVisibility(View.VISIBLE);
        } else if (mode == PrefsManager.MODE_SOS) {
            chipGroupMode.check(R.id.chipModeSos);
            layoutStrobeSpeed.setVisibility(View.GONE);
        } else {
            chipGroupMode.check(R.id.chipModeSteady);
            layoutStrobeSpeed.setVisibility(View.GONE);
        }

        int hz = prefs.getStrobeHz();
        sliderStrobeSpeed.setValue(hz);
        tvStrobeSpeedValue.setText(hz + " Hz");

        // Brightness (Android 13+ hardware check)
        if (maxTorchStrength > 1) {
            int max = maxTorchStrength;
            int current = prefs.getBrightnessLevel(max);
            sliderBrightness.setValue(1f);
            sliderBrightness.setValueFrom(1f);
            sliderBrightness.setValueTo((float) max);
            sliderBrightness.setStepSize(1f);
            sliderBrightness.setValue((float) current);
            sliderBrightness.setEnabled(true);
            tvBrightnessValue.setText(current + " / " + max);
            tvBrightnessHint.setVisibility(View.GONE);
        } else {
            sliderBrightness.setEnabled(false);
            tvBrightnessValue.setText("Standard");
            tvBrightnessHint.setText("Your device or OS only exposes standard on/off torch brightness.");
            tvBrightnessHint.setVisibility(View.VISIBLE);
        }

        // Power trigger
        switchPowerTrigger.setChecked(prefs.isPowerTriggerEnabled());
        int windowMs = prefs.getClickWindowMs();
        sliderClickWindow.setValue(Math.max(800, Math.min(2500, windowMs)));
        tvClickWindowValue.setText(String.format(Locale.US, "%.1f s", windowMs / 1000f));

        // Volume & Shake
        switchVolumeTrigger.setChecked(prefs.isVolumeTriggerEnabled());
        switchShakeTrigger.setChecked(prefs.isShakeTriggerEnabled());
        int sens = prefs.getShakeSensitivity();
        if (sens == PrefsManager.SENS_FIRM) {
            chipGroupShakeSens.check(R.id.chipSensLow);
        } else if (sens == PrefsManager.SENS_SENSITIVE) {
            chipGroupShakeSens.check(R.id.chipSensHigh);
        } else {
            chipGroupShakeSens.check(R.id.chipSensMed);
        }

        // Anti-mistake
        switchPocketProtection.setChecked(prefs.isPocketProtectionEnabled());
        switchScreenOffOnly.setChecked(prefs.isScreenOffOnly());
        switchHaptic.setChecked(prefs.isHapticEnabled());

        // Auto-off timer
        int mins = prefs.getAutoOffMinutes();
        if (mins == 0) chipGroupAutoOff.check(R.id.chipTimerOff);
        else if (mins == 1) chipGroupAutoOff.check(R.id.chipTimer1m);
        else if (mins == 2) chipGroupAutoOff.check(R.id.chipTimer2m);
        else if (mins == 10) chipGroupAutoOff.check(R.id.chipTimer10m);
        else if (mins == 15) chipGroupAutoOff.check(R.id.chipTimer15m);
        else chipGroupAutoOff.check(R.id.chipTimer5m);

        // Background & battery
        switchMasterService.setChecked(prefs.isServiceEnabled());
        switchAutoBoot.setChecked(prefs.isAutoBootEnabled());
        switchLowBattery.setChecked(prefs.isLowBatteryCutoffEnabled());
        int cutoff = prefs.getLowBatteryThreshold();
        sliderLowBattery.setValue(Math.max(5, Math.min(30, cutoff)));
        tvLowBatteryValue.setText(cutoff + "%");
    }

    private void setupListeners() {
        btnTorchToggle.setOnClickListener(v -> TorchService.sendToggle(this, "ui"));

        chipGroupMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipModeStrobe) {
                prefs.setTorchMode(PrefsManager.MODE_STROBE);
                layoutStrobeSpeed.setVisibility(View.VISIBLE);
            } else if (id == R.id.chipModeSos) {
                prefs.setTorchMode(PrefsManager.MODE_SOS);
                layoutStrobeSpeed.setVisibility(View.GONE);
            } else {
                prefs.setTorchMode(PrefsManager.MODE_STEADY);
                layoutStrobeSpeed.setVisibility(View.GONE);
            }
            TorchService.updateLiveTorch(this);
        });

        sliderStrobeSpeed.addOnChangeListener((slider, value, fromUser) -> {
            int hz = Math.round(value);
            tvStrobeSpeedValue.setText(hz + " Hz");
            if (fromUser) {
                prefs.setStrobeHz(hz);
                TorchService.updateLiveTorch(this);
            }
        });

        sliderBrightness.addOnChangeListener((slider, value, fromUser) -> {
            int level = Math.round(value);
            int max = maxTorchStrength;
            tvBrightnessValue.setText(level + " / " + max);
            if (fromUser) {
                prefs.setBrightnessLevel(level);
                TorchService.updateLiveTorch(this);
            }
        });

        switchPowerTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setPowerTriggerEnabled(isChecked);
            TorchService.reloadPrefs(this);
        });

        sliderClickWindow.addOnChangeListener((slider, value, fromUser) -> {
            int ms = Math.round(value);
            tvClickWindowValue.setText(String.format(Locale.US, "%.1f s", ms / 1000f));
            if (fromUser) {
                prefs.setClickWindowMs(ms);
            }
        });

        switchVolumeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setVolumeTriggerEnabled(isChecked);
            TorchService.reloadPrefs(this);
            if (isChecked && !VolumeKeyAccessibilityService.isConnected()) {
                Toast.makeText(this, "Tap Enable to grant Accessibility permission for Volume Combo", Toast.LENGTH_LONG).show();
            }
        });

        btnOpenAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        switchShakeTrigger.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setShakeTriggerEnabled(isChecked);
            TorchService.reloadPrefs(this);
        });

        chipGroupShakeSens.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipSensLow) prefs.setShakeSensitivity(PrefsManager.SENS_FIRM);
            else if (id == R.id.chipSensHigh) prefs.setShakeSensitivity(PrefsManager.SENS_SENSITIVE);
            else prefs.setShakeSensitivity(PrefsManager.SENS_MEDIUM);
        });

        switchPocketProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setPocketProtectionEnabled(isChecked);
            TorchService.reloadPrefs(this);
        });

        switchScreenOffOnly.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.setScreenOffOnly(isChecked));

        switchHaptic.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.setHapticEnabled(isChecked));

        chipGroupAutoOff.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipTimerOff) prefs.setAutoOffMinutes(0);
            else if (id == R.id.chipTimer1m) prefs.setAutoOffMinutes(1);
            else if (id == R.id.chipTimer2m) prefs.setAutoOffMinutes(2);
            else if (id == R.id.chipTimer10m) prefs.setAutoOffMinutes(10);
            else if (id == R.id.chipTimer15m) prefs.setAutoOffMinutes(15);
            else prefs.setAutoOffMinutes(5);
        });

        switchMasterService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setServiceEnabled(isChecked);
            if (isChecked) {
                TorchService.start(this);
            } else {
                TorchService.stop(this);
            }
        });

        switchAutoBoot.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.setAutoBootEnabled(isChecked));

        switchLowBattery.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.setLowBatteryCutoffEnabled(isChecked));

        sliderLowBattery.addOnChangeListener((slider, value, fromUser) -> {
            int pct = Math.round(value);
            tvLowBatteryValue.setText(pct + "%");
            if (fromUser) {
                prefs.setLowBatteryThreshold(pct);
            }
        });

        btnBatteryOpt.setOnClickListener(v -> {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            } else {
                Toast.makeText(this, "Battery optimization is already disabled for QuickTorch", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestNotifPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        if (prefs.isServiceEnabled()) {
            TorchService.start(this);
        }
    }

    private void updateTorchUi(boolean on) {
        if (on) {
            btnTorchToggle.setBackgroundResource(R.drawable.bg_power_btn_on);
            btnTorchToggle.setColorFilter(ContextCompat.getColor(this, R.color.bg_dark));
            tvTorchState.setText("FLASHLIGHT ON");
            tvTorchState.setTextColor(ContextCompat.getColor(this, R.color.amber_primary));
        } else {
            btnTorchToggle.setBackgroundResource(R.drawable.bg_power_btn_off);
            btnTorchToggle.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
            tvTorchState.setText("FLASHLIGHT OFF");
            tvTorchState.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void updateServiceUi(boolean running) {
        if (running) {
            tvServiceBadge.setText("ACTIVE");
            tvServiceBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            tvSubtitleStatus.setText("Listening in background for triggers");
        } else {
            tvServiceBadge.setText("STOPPED");
            tvServiceBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvSubtitleStatus.setText("Background listener inactive");
        }
    }

    private void updateAccessibilityBadge() {
        boolean enabled = VolumeKeyAccessibilityService.isConnected();
        if (enabled) {
            tvAccessibilityStatus.setText("Accessibility service: Enabled");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green));
            btnOpenAccessibility.setText("Settings");
        } else {
            tvAccessibilityStatus.setText("Accessibility service: Not enabled");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            btnOpenAccessibility.setText("Enable");
        }
    }

    private void updateBatteryOptButton() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            btnBatteryOpt.setText("Unrestricted Background Battery: Enabled");
            btnBatteryOpt.setTextColor(ContextCompat.getColor(this, R.color.status_green));
        } else {
            btnBatteryOpt.setText("Allow Unrestricted Background Battery");
            btnBatteryOpt.setTextColor(ContextCompat.getColor(this, R.color.amber_primary));
        }
    }
}
