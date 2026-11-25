package com.quicktorch.app.service;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.quicktorch.app.PrefsManager;

public class VolumeKeyAccessibilityService extends AccessibilityService {

    private static volatile boolean connected = false;

    private PrefsManager prefs;
    private PowerManager powerManager;
    private KeyguardManager keyguardManager;

    private boolean volUpHeld = false;
    private boolean volDownHeld = false;
    private long volUpDownTime = 0L;
    private long volDownDownTime = 0L;
    private long lastComboTriggerTime = 0L;

    public static boolean isConnected() {
        return connected;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = new PrefsManager(this);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        connected = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used; this service only listens for Volume Up + Down key combos
    }

    @Override
    public void onInterrupt() {
        // Not used
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (prefs == null || !prefs.isVolumeTriggerEnabled()) {
            return false;
        }

        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        // Respect Lock-Screen / Screen-Off Only mode
        if (prefs.isScreenOffOnly()) {
            boolean interactive = powerManager != null && powerManager.isInteractive();
            boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
            if (interactive && !locked) {
                return false;
            }
        }

        long now = SystemClock.elapsedRealtime();
        int action = event.getAction();

        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                volUpHeld = true;
                volUpDownTime = now;
                if ((volDownHeld || (now - volDownDownTime) <= 300L)
                        && (now - lastComboTriggerTime) > 600L) {
                    lastComboTriggerTime = now;
                    TorchService.sendToggle(this, "volume combo");
                    return true;
                }
            } else {
                volDownHeld = true;
                volDownDownTime = now;
                if ((volUpHeld || (now - volUpDownTime) <= 300L)
                        && (now - lastComboTriggerTime) > 600L) {
                    lastComboTriggerTime = now;
                    TorchService.sendToggle(this, "volume combo");
                    return true;
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                volUpHeld = false;
            } else {
                volDownHeld = false;
            }
        }

        return false;
    }

    @Override
    public void onDestroy() {
        connected = false;
        super.onDestroy();
    }
}
