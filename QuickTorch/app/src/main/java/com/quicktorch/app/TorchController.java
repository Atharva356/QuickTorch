package com.quicktorch.app;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

public class TorchController {
    private static final String TAG = "TorchController";

    public interface StateListener {
        void onTorchStateChanged(boolean active);
    }

    private final CameraManager cameraManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String cameraId;
    private int maxStrengthLevel = 1;
    private boolean supportsVariableBrightness = false;

    private boolean isLogicalActive = false;
    private boolean isHardwareOn = false;
    private boolean patternRunning = false;
    private int currentMode = PrefsManager.MODE_STEADY;
    private int currentBrightness = 1;
    private int currentStrobeHz = 5;

    private StateListener stateListener;

    // SOS Morse timings in ms: {duration, isOn (1 or 0)}
    private static final int[][] SOS_PATTERN = {
            // S: . . .
            {150, 1}, {150, 0}, {150, 1}, {150, 0}, {150, 1}, {450, 0},
            // O: - - -
            {450, 1}, {150, 0}, {450, 1}, {150, 0}, {450, 1}, {450, 0},
            // S: . . .
            {150, 1}, {150, 0}, {150, 1}, {150, 0}, {150, 1}, {1050, 0}
    };
    private int sosIndex = 0;
    private boolean strobePhase = false;

    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(@NonNull String camId, boolean enabled) {
            if (cameraId != null && cameraId.equals(camId)) {
                isHardwareOn = enabled;
                // Only sync external Quick Settings toggles when we aren't running a blink pattern
                if (!patternRunning && isLogicalActive != enabled) {
                    isLogicalActive = enabled;
                    notifyState();
                }
            }
        }
    };

    private final Runnable strobeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!patternRunning || currentMode != PrefsManager.MODE_STROBE) return;
            strobePhase = !strobePhase;
            applyHardwareTorch(strobePhase, currentBrightness);
            long halfPeriodMs = Math.max(45L, 500L / Math.max(1, currentStrobeHz));
            handler.postDelayed(this, halfPeriodMs);
        }
    };

    private final Runnable sosRunnable = new Runnable() {
        @Override
        public void run() {
            if (!patternRunning || currentMode != PrefsManager.MODE_SOS) return;
            int[] step = SOS_PATTERN[sosIndex];
            sosIndex = (sosIndex + 1) % SOS_PATTERN.length;
            applyHardwareTorch(step[1] == 1, currentBrightness);
            handler.postDelayed(this, step[0]);
        }
    };

    public TorchController(Context context) {
        this.cameraManager = (CameraManager) context.getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);
        initCamera();
        if (cameraManager != null) {
            cameraManager.registerTorchCallback(torchCallback, handler);
        }
    }

    private void initCamera() {
        if (cameraManager == null) return;
        try {
            String fallbackId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) {
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id;
                        readStrengthCapabilities(chars);
                        return;
                    }
                    if (fallbackId == null) {
                        fallbackId = id;
                    }
                }
            }
            if (fallbackId != null) {
                cameraId = fallbackId;
                readStrengthCapabilities(cameraManager.getCameraCharacteristics(fallbackId));
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to inspect camera flash", e);
        }
    }

    private void readStrengthCapabilities(CameraCharacteristics chars) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Integer max = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
            if (max != null && max > 1) {
                maxStrengthLevel = max;
                supportsVariableBrightness = true;
                currentBrightness = maxStrengthLevel;
                return;
            }
        }
        maxStrengthLevel = 1;
        supportsVariableBrightness = false;
        currentBrightness = 1;
    }


    public static int getDeviceMaxStrengthLevel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return 1;
        }
        CameraManager cm = (CameraManager) context.getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) return 1;
        try {
            String fallbackId = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics chars = cm.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) {
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        Integer max = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                        return (max != null && max > 1) ? max : 1;
                    }
                    if (fallbackId == null) fallbackId = id;
                }
            }
            if (fallbackId != null) {
                CameraCharacteristics chars = cm.getCameraCharacteristics(fallbackId);
                Integer max = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                return (max != null && max > 1) ? max : 1;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to query max torch strength", e);
        }
        return 1;
    }

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    public boolean hasFlash() {
        return cameraId != null;
    }

    public boolean supportsVariableBrightness() {
        return supportsVariableBrightness;
    }

    public int getMaxStrengthLevel() {
        return maxStrengthLevel;
    }

    public boolean isActive() {
        return isLogicalActive;
    }

    public boolean turnOn(int mode, int brightnessLevel, int strobeHz) {
        if (cameraId == null) return false;
        stopPattern();

        currentMode = mode;
        currentBrightness = Math.max(1, Math.min(maxStrengthLevel, brightnessLevel));
        currentStrobeHz = Math.max(1, Math.min(10, strobeHz));
        isLogicalActive = true;

        if (mode == PrefsManager.MODE_STROBE) {
            patternRunning = true;
            strobePhase = false;
            handler.post(strobeRunnable);
        } else if (mode == PrefsManager.MODE_SOS) {
            patternRunning = true;
            sosIndex = 0;
            handler.post(sosRunnable);
        } else {
            applyHardwareTorch(true, currentBrightness);
        }

        notifyState();
        return true;
    }

    public void updateLiveSettings(int mode, int brightnessLevel, int strobeHz) {
        currentBrightness = Math.max(1, Math.min(maxStrengthLevel, brightnessLevel));
        currentStrobeHz = Math.max(1, Math.min(10, strobeHz));
        if (!isLogicalActive) {
            currentMode = mode;
            return;
        }
        if (mode != currentMode) {
            turnOn(mode, currentBrightness, currentStrobeHz);
        } else if (mode == PrefsManager.MODE_STEADY) {
            applyHardwareTorch(true, currentBrightness);
        }
    }

    public void turnOff() {
        stopPattern();
        isLogicalActive = false;
        applyHardwareTorch(false, currentBrightness);
        notifyState();
    }

    private void stopPattern() {
        patternRunning = false;
        handler.removeCallbacks(strobeRunnable);
        handler.removeCallbacks(sosRunnable);
    }

    private void applyHardwareTorch(boolean on, int strengthLevel) {
        if (cameraManager == null || cameraId == null) return;
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && supportsVariableBrightness) {
                    int clamped = Math.max(1, Math.min(maxStrengthLevel, strengthLevel));
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, clamped);
                } else {
                    cameraManager.setTorchMode(cameraId, true);
                }
            } else {
                cameraManager.setTorchMode(cameraId, false);
            }
            isHardwareOn = on;
        } catch (CameraAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Failed to change torch mode", e);
        }
    }

    private void notifyState() {
        if (stateListener != null) {
            stateListener.onTorchStateChanged(isLogicalActive);
        }
    }

    public void release() {
        stopPattern();
        if (isHardwareOn) {
            applyHardwareTorch(false, 1);
        }
        if (cameraManager != null) {
            cameraManager.unregisterTorchCallback(torchCallback);
        }
    }
}
