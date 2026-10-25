package com.caophuoc.clonewindows;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(24, 20, 24, 20);
        TextView title = new TextView(this);
        title.setText("Clone Windows");
        title.setTextSize(26);
        column.addView(title);
        status = new TextView(this);
        status.setPadding(0, 12, 0, 12);
        column.addView(status);
        TextView help = new TextView(this);
        help.setText("Giữ kích thước khi mở lại. Kéo viền xanh ở cạnh trái, phải, trên hoặc dưới để đổi kích thước. Cần root và quyền hiển thị trên ứng dụng khác.");
        column.addView(help);
        button(column, "Bật quản lý cửa sổ", v -> enable());
        button(column, "Tắt quản lý cửa sổ", v -> {
            getSharedPreferences("windows", 0).edit().putBoolean("enabled", false).apply();
            stopService(new Intent(this, WindowService.class));
            updateStatus();
        });
        for (int n = 1; n <= 11; n++) {
            final String pkg = String.format(java.util.Locale.US, "com.roblox.clone%02d", n);
            if (getPackageManager().getLaunchIntentForPackage(pkg) != null) {
                button(column, "Mở Clone " + n, v -> {
                    if (!Settings.canDrawOverlays(this)) { enable(); return; }
                    getSharedPreferences("windows", 0).edit().putBoolean("enabled", true).apply();
                    startForegroundService(new Intent(this, WindowService.class).putExtra("launch", pkg));
                    moveTaskToBack(true);
                });
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
    }
    private void button(LinearLayout column, String text, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(action);
        column.addView(b);
    }
    private void enable() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        getSharedPreferences("windows", 0).edit().putBoolean("enabled", true).apply();
        startForegroundService(new Intent(this, WindowService.class));
        updateStatus();
    }
    private void updateStatus() {
        String error = getSharedPreferences("windows", 0).getString("error", "");
        status.setText((getSharedPreferences("windows", 0).getBoolean("enabled", false) ? "Đang bật" : "Đang tắt") +
                (error.isEmpty() ? "" : "\n" + error));
    }
    @Override public void onResume() { super.onResume(); if (status != null) updateStatus(); }
}
