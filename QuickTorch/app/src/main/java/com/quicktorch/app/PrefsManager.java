package com.quicktorch.app;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String PREFS_NAME = "quicktorch_prefs";

    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_POWER_TRIGGER = "power_trigger";
    private static final String KEY_CLICK_WINDOW_MS = "click_window_ms";
    private static final String KEY_VOLUME_TRIGGER = "volume_trigger";
    private static final String KEY_SHAKE_TRIGGER = "shake_trigger";
    private static final String KEY_SHAKE_SENSITIVITY = "shake_sensitivity"; // 0=Firm, 1=Medium, 2=Sensitive
    private static final String KEY_POCKET_PROTECTION = "pocket_protection";
    private static final String KEY_SCREEN_OFF_ONLY = "screen_off_only";
    private static final String KEY_HAPTIC_ENABLED = "haptic_enabled";
    private static final String KEY_AUTO_OFF_MINUTES = "auto_off_minutes";
    private static final String KEY_AUTO_BOOT = "auto_boot";
    private static final String KEY_LOW_BATTERY_CUTOFF = "low_battery_cutoff";
    private static final String KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold";
    private static final String KEY_TORCH_MODE = "torch_mode"; // 0=Steady, 1=Strobe, 2=SOS
    private static final String KEY_STROBE_HZ = "strobe_hz";
    private static final String KEY_BRIGHTNESS_LEVEL = "brightness_level";

    public static final int MODE_STEADY = 0;
    public static final int MODE_STROBE = 1;
    public static final int MODE_SOS = 2;

    public static final int SENS_FIRM = 0;
    public static final int SENS_MEDIUM = 1;
    public static final int SENS_SENSITIVE = 2;

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isServiceEnabled() {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, true);
    }

    public void setServiceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }

    public boolean isPowerTriggerEnabled() {
        return prefs.getBoolean(KEY_POWER_TRIGGER, true);
    }

    public void setPowerTriggerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_POWER_TRIGGER, enabled).apply();
    }

    public int getClickWindowMs() {
        return prefs.getInt(KEY_CLICK_WINDOW_MS, 1500);
    }

    public void setClickWindowMs(int ms) {
        prefs.edit().putInt(KEY_CLICK_WINDOW_MS, ms).apply();
    }

    public boolean isVolumeTriggerEnabled() {
        return prefs.getBoolean(KEY_VOLUME_TRIGGER, false);
    }

    public void setVolumeTriggerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOLUME_TRIGGER, enabled).apply();
    }

    public boolean isShakeTriggerEnabled() {
        return prefs.getBoolean(KEY_SHAKE_TRIGGER, false);
    }

    public void setShakeTriggerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHAKE_TRIGGER, enabled).apply();
    }

    public int getShakeSensitivity() {
        return prefs.getInt(KEY_SHAKE_SENSITIVITY, SENS_MEDIUM);
    }

    public void setShakeSensitivity(int sens) {
        prefs.edit().putInt(KEY_SHAKE_SENSITIVITY, sens).apply();
    }

    public float getShakeThresholdMs2() {
        switch (getShakeSensitivity()) {
            case SENS_FIRM:
                return 18.5f;
            case SENS_SENSITIVE:
                return 10.5f;
            case SENS_MEDIUM:
            default:
                return 14.0f;
        }
    }

    public boolean isPocketProtectionEnabled() {
        return prefs.getBoolean(KEY_POCKET_PROTECTION, true);
    }

    public void setPocketProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_POCKET_PROTECTION, enabled).apply();
    }

    public boolean isScreenOffOnly() {
        return prefs.getBoolean(KEY_SCREEN_OFF_ONLY, false);
    }

    public void setScreenOffOnly(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREEN_OFF_ONLY, enabled).apply();
    }

    public boolean isHapticEnabled() {
        return prefs.getBoolean(KEY_HAPTIC_ENABLED, true);
    }

    public void setHapticEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply();
    }

    public int getAutoOffMinutes() {
        return prefs.getInt(KEY_AUTO_OFF_MINUTES, 5);
    }

    public void setAutoOffMinutes(int minutes) {
        prefs.edit().putInt(KEY_AUTO_OFF_MINUTES, minutes).apply();
    }

    public boolean isAutoBootEnabled() {
        return prefs.getBoolean(KEY_AUTO_BOOT, true);
    }

    public void setAutoBootEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BOOT, enabled).apply();
    }

    public boolean isLowBatteryCutoffEnabled() {
        return prefs.getBoolean(KEY_LOW_BATTERY_CUTOFF, true);
    }

    public void setLowBatteryCutoffEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOW_BATTERY_CUTOFF, enabled).apply();
    }

    public int getLowBatteryThreshold() {
        return prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, 15);
    }

    public void setLowBatteryThreshold(int pct) {
        prefs.edit().putInt(KEY_LOW_BATTERY_THRESHOLD, pct).apply();
    }

    public int getTorchMode() {
        return prefs.getInt(KEY_TORCH_MODE, MODE_STEADY);
    }

    public void setTorchMode(int mode) {
        prefs.edit().putInt(KEY_TORCH_MODE, mode).apply();
    }

    public int getStrobeHz() {
        return prefs.getInt(KEY_STROBE_HZ, 5);
    }

    public void setStrobeHz(int hz) {
        prefs.edit().putInt(KEY_STROBE_HZ, Math.max(1, Math.min(10, hz))).apply();
    }

    public int getBrightnessLevel(int maxLevel) {
        int saved = prefs.getInt(KEY_BRIGHTNESS_LEVEL, maxLevel);
        return Math.max(1, Math.min(maxLevel, saved));
    }

    public void setBrightnessLevel(int level) {
        prefs.edit().putInt(KEY_BRIGHTNESS_LEVEL, level).apply();
    }
}
