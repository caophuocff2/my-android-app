package com.caophuoc.clonewindows;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) &&
                Settings.canDrawOverlays(context) &&
                context.getSharedPreferences("windows", 0).getBoolean("enabled", false)) {
            context.startForegroundService(new Intent(context, WindowService.class));
        }
    }
}
