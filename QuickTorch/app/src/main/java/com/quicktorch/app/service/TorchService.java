package com.quicktorch.app.service;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.quicktorch.app.MainActivity;
import com.quicktorch.app.PrefsManager;
import com.quicktorch.app.R;
import com.quicktorch.app.TorchController;

import java.util.ArrayDeque;
import java.util.Deque;

public class TorchService extends Service implements SensorEventListener {

    public static final String ACTION_START = "com.quicktorch.app.ACTION_START";
    public static final String ACTION_STOP = "com.quicktorch.app.ACTION_STOP";
    public static final String ACTION_TOGGLE_TORCH = "com.quicktorch.app.ACTION_TOGGLE_TORCH";
    public static final String ACTION_RELOAD_PREFS = "com.quicktorch.app.ACTION_RELOAD_PREFS";
    public static final String ACTION_UPDATE_LIVE = "com.quicktorch.app.ACTION_UPDATE_LIVE";
    public static final String EXTRA_SOURCE = "extra_source";

    public static final String BROADCAST_STATE = "com.quicktorch.app.BROADCAST_STATE";
    public static final String EXTRA_TORCH_ON = "extra_torch_on";
    public static final String EXTRA_SERVICE_RUNNING = "extra_service_running";
    public static final String EXTRA_MESSAGE = "extra_message";

    private static final String CHANNEL_ID = "quicktorch_listener_channel";
    private static final int NOTIFICATION_ID = 1042;

    private static volatile boolean running = false;
    private static volatile boolean currentTorchActive = false;

    private PrefsManager prefs;
    private TorchController torchController;
    private SensorManager sensorManager;
    private PowerManager powerManager;
    private KeyguardManager keyguardManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Power button triple-click tracking
    private final Deque<Long> powerClickTimes = new ArrayDeque<>();
    private boolean startedFromLockedScreen = false;
    private long lastPowerTriggerTime = 0L;

    // Proximity pocket protection
    private Sensor proximitySensor;
    private boolean proximityRegistered = false;
    private volatile boolean isProximityNear = false;

    // Double-chop / shake detection
    private Sensor accelSensor;
    private boolean usingLinearAccel = false;
    private boolean accelRegistered = false;
    private final float[] gravity = new float[]{0f, 0f, 0f};
    private long firstChopTime = 0L;
    private long lastShakeTriggerTime = 0L;
    private long lastPeakTime = 0L;

    private final Runnable autoOffRunnable = () -> {
        if (torchController != null && torchController.isActive()) {
            torchController.turnOff();
            vibrateOff();
            broadcastState("Auto-off timer turned flashlight off");
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (!Intent.ACTION_SCREEN_ON.equals(action) && !Intent.ACTION_SCREEN_OFF.equals(action)) {
                return;
            }
            if (!prefs.isPowerTriggerEnabled()) return;

            long now = SystemClock.elapsedRealtime();
            if (now - lastPowerTriggerTime < 800L) {
                return;
            }

            int windowMs = prefs.getClickWindowMs();
            while (!powerClickTimes.isEmpty() && (now - powerClickTimes.peekFirst()) > windowMs) {
                powerClickTimes.pollFirst();
            }

            if (powerClickTimes.isEmpty()) {
                // Track whether the triple-click gesture began while locked/screen-off
                boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
                startedFromLockedScreen = locked && Intent.ACTION_SCREEN_ON.equals(action);
            }

            powerClickTimes.addLast(now);

            if (powerClickTimes.size() >= 3) {
                powerClickTimes.clear();
                lastPowerTriggerTime = now;
                if (prefs.isScreenOffOnly() && !startedFromLockedScreen) {
                    broadcastState("Ignored: Lock-screen only mode");
                    return;
                }
                handleTorchToggle("power");
            }
        }
    };

    public static boolean isRunning() {
        return running;
    }

    public static boolean isTorchActive() {
        return currentTorchActive;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, TorchService.class);
        intent.setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        if (!running) return;
        Intent intent = new Intent(context, TorchService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static void sendToggle(Context context, String source) {
        Intent intent = new Intent(context, TorchService.class);
        intent.setAction(ACTION_TOGGLE_TORCH);
        intent.putExtra(EXTRA_SOURCE, source);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void reloadPrefs(Context context) {
        if (!running) return;
        Intent intent = new Intent(context, TorchService.class);
        intent.setAction(ACTION_RELOAD_PREFS);
        context.startService(intent);
    }

    public static void updateLiveTorch(Context context) {
        if (!running) return;
        Intent intent = new Intent(context, TorchService.class);
        intent.setAction(ACTION_UPDATE_LIVE);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefsManager(this);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        torchController = new TorchController(this);
        torchController.setStateListener(active -> {
            currentTorchActive = active;
            if (!active) {
                handler.removeCallbacks(autoOffRunnable);
            }
            updateNotification();
            broadcastState(null);
        });

        createNotificationChannel();
        startAsForeground();

        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        ContextCompat.registerReceiver(this, screenReceiver, screenFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        refreshSensors();
        running = true;
        broadcastState("Background listener active");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_STOP:
                    if (torchController != null && torchController.isActive()) {
                        torchController.turnOff();
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                    return START_NOT_STICKY;

                case ACTION_TOGGLE_TORCH:
                    String source = intent.getStringExtra(EXTRA_SOURCE);
                    handleTorchToggle(source != null ? source : "ui");
                    break;

                case ACTION_RELOAD_PREFS:
                    refreshSensors();
                    updateNotification();
                    break;

                case ACTION_UPDATE_LIVE:
                    if (torchController != null) {
                        int max = torchController.getMaxStrengthLevel();
                        torchController.updateLiveSettings(
                                prefs.getTorchMode(),
                                prefs.getBrightnessLevel(max),
                                prefs.getStrobeHz()
                        );
                    }
                    break;

                case ACTION_START:
                default:
                    refreshSensors();
                    updateNotification();
                    break;
            }
        }
        return START_STICKY;
    }

    private void handleTorchToggle(String source) {
        if (torchController == null || !torchController.hasFlash()) {
            broadcastState("No camera flash detected on this device");
            return;
        }

        // Turning OFF is always allowed immediately
        if (torchController.isActive()) {
            torchController.turnOff();
            handler.removeCallbacks(autoOffRunnable);
            vibrateOff();
            broadcastState("Flashlight turned OFF (" + source + ")");
            return;
        }

        boolean isManualUi = "ui".equals(source) || "notification".equals(source);

        // Pocket Protection check for hardware/gesture triggers
        if (!isManualUi && prefs.isPocketProtectionEnabled() && isProximityNear) {
            broadcastState("Blocked by Pocket Protection (sensor covered)");
            return;
        }

        // Low-Battery Cutoff check
        if (prefs.isLowBatteryCutoffEnabled()) {
            int batteryPct = getBatteryPercentage();
            if (!isCharging() && batteryPct >= 0 && batteryPct <= prefs.getLowBatteryThreshold()) {
                vibrateBlocked();
                broadcastState("Blocked: Battery at " + batteryPct + "% (cutoff " + prefs.getLowBatteryThreshold() + "%)");
                return;
            }
        }

        int maxLevel = torchController.getMaxStrengthLevel();
        int brightness = prefs.getBrightnessLevel(maxLevel);
        int mode = prefs.getTorchMode();
        int strobeHz = prefs.getStrobeHz();

        boolean started = torchController.turnOn(mode, brightness, strobeHz);
        if (started) {
            vibrateOn();
            scheduleAutoOff();
            broadcastState("Flashlight ON via " + source);
        }
    }

    private void scheduleAutoOff() {
        handler.removeCallbacks(autoOffRunnable);
        int minutes = prefs.getAutoOffMinutes();
        if (minutes > 0) {
            handler.postDelayed(autoOffRunnable, minutes * 60_000L);
        }
    }

    private void refreshSensors() {
        if (sensorManager == null) return;

        // Proximity sensor for Pocket Protection
        if (prefs.isPocketProtectionEnabled()) {
            if (!proximityRegistered) {
                proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                if (proximitySensor != null) {
                    sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
                    proximityRegistered = true;
                }
            }
        } else if (proximityRegistered) {
            sensorManager.unregisterListener(this, proximitySensor);
            proximityRegistered = false;
            isProximityNear = false;
        }

        // Accelerometer for Chop / Shake Gesture
        if (prefs.isShakeTriggerEnabled()) {
            if (!accelRegistered) {
                accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                if (accelSensor != null) {
                    usingLinearAccel = true;
                } else {
                    accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                    usingLinearAccel = false;
                }
                if (accelSensor != null) {
                    sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME);
                    accelRegistered = true;
                }
            }
        } else if (accelRegistered) {
            sensorManager.unregisterListener(this, accelSensor);
            accelRegistered = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null) return;

        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            float maxRange = event.sensor.getMaximumRange();
            float threshold = Math.min(maxRange, 5.0f);
            isProximityNear = event.values[0] < threshold;
            return;
        }

        if (prefs.isShakeTriggerEnabled()
                && (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION
                || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)) {
            float ax, ay, az;
            if (usingLinearAccel) {
                ax = event.values[0];
                ay = event.values[1];
                az = event.values[2];
            } else {
                final float alpha = 0.8f;
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
                ax = event.values[0] - gravity[0];
                ay = event.values[1] - gravity[1];
                az = event.values[2] - gravity[2];
            }

            float magnitude = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            float threshold = prefs.getShakeThresholdMs2();
            long now = SystemClock.elapsedRealtime();

            if (magnitude > threshold && (now - lastShakeTriggerTime) > 1200L) {
                if ((now - lastPeakTime) > 130L) {
                    lastPeakTime = now;
                    if (firstChopTime == 0L || (now - firstChopTime) > 750L) {
                        firstChopTime = now;
                    } else {
                        // Second crisp chop within 130..750ms
                        firstChopTime = 0L;
                        lastShakeTriggerTime = now;
                        if (prefs.isScreenOffOnly() && !isScreenOffOrLocked()) {
                            return;
                        }
                        handleTorchToggle("shake");
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    private boolean isScreenOffOrLocked() {
        boolean interactive = powerManager != null && powerManager.isInteractive();
        boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        return !interactive || locked;
    }

    private int getBatteryPercentage() {
        Intent status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) return -1;
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return -1;
        return Math.round((level * 100f) / scale);
    }

    private boolean isCharging() {
        Intent status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) return false;
        int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return plugged != 0;
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    private void vibrateOn() {
        if (!prefs.isHapticEnabled()) return;
        Vibrator v = getVibrator();
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void vibrateOff() {
        if (!prefs.isHapticEnabled()) return;
        Vibrator v = getVibrator();
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 30, 55, 30}, -1));
        }
    }

    private void vibrateBlocked() {
        if (!prefs.isHapticEnabled()) return;
        Vibrator v = getVibrator();
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 25, 40, 25, 40, 25}, -1));
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notif_channel_desc));
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggleIntent = new Intent(this, TorchService.class);
        toggleIntent.setAction(ACTION_TOGGLE_TORCH);
        toggleIntent.putExtra(EXTRA_SOURCE, "notification");
        PendingIntent togglePi = PendingIntent.getService(
                this, 1, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, TorchService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        boolean active = torchController != null && torchController.isActive();
        String title = active ? "QuickTorch: Flashlight ON" : "QuickTorch Listener Active";
        StringBuilder triggers = new StringBuilder();
        if (prefs.isPowerTriggerEnabled()) triggers.append("3x Power");
        if (prefs.isVolumeTriggerEnabled()) {
            if (triggers.length() > 0) triggers.append(" • ");
            triggers.append("Vol Combo");
        }
        if (prefs.isShakeTriggerEnabled()) {
            if (triggers.length() > 0) triggers.append(" • ");
            triggers.append("Chop Shake");
        }
        String text = triggers.length() > 0 ? "Triggers: " + triggers : "Ready for manual toggle";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flashlight)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPi)
                .addAction(R.drawable.ic_flashlight, active ? "Turn OFF" : "Turn ON", togglePi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
                .build();
    }

    private void startAsForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private void broadcastState(@Nullable String message) {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_TORCH_ON, torchController != null && torchController.isActive());
        intent.putExtra(EXTRA_SERVICE_RUNNING, running);
        if (message != null) {
            intent.putExtra(EXTRA_MESSAGE, message);
        }
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        running = false;
        currentTorchActive = false;
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (torchController != null) {
            torchController.release();
        }
        broadcastState("Background listener stopped");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
