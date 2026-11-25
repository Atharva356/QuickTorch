package com.quicktorch.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.quicktorch.app.PrefsManager;
import com.quicktorch.app.service.TorchService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            PrefsManager prefs = new PrefsManager(context);
            if (prefs.isServiceEnabled() && prefs.isAutoBootEnabled()) {
                try {
                    TorchService.start(context);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
