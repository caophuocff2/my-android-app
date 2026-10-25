package com.caophuoc.clonewindows;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Android 9 rooted-device window controller; never modifies clone executable code. */
public class WindowService extends Service {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<Resize> pending = new AtomicReference<>();
    private final Map<String, Integer> known = new HashMap<>();
    private final ArrayList<View> edges = new ArrayList<>();
    private static final Pattern TASK = Pattern.compile("taskId=(\\d+): (com\\.roblox\\.clone\\d{2})/[^\\s]+ bounds=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\].*visible=(true|false)");
    private SharedPreferences prefs;
    private WindowManager wm;
    private int screenW, screenH, grip;
    private volatile boolean dragging, alive;
    private int shownTask = -1;
    private Rect shownBounds;
    private long nextPoll;
    private Process root;
    private BufferedReader rootOutput;
    private BufferedWriter rootInput;
    private int commandId;

    private static class Resize {
        final int task;
        final String pkg;
        final Rect bounds;
        Resize(int task, String pkg, Rect bounds) {
            this.task = task; this.pkg = pkg; this.bounds = new Rect(bounds);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("windows", 0);
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels; screenH = dm.heightPixels;
        grip = Math.max(8, Math.round(8 * dm.density));
        NotificationManager notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel("windows", "Clone windows", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "windows")
                .setSmallIcon(android.R.drawable.ic_menu_crop).setContentTitle("Clone Windows")
                .setContentText("Giữ kích thước và kéo bốn cạnh cửa sổ")
                .setContentIntent(open).setOngoing(true).build());
        alive = true;
        worker.execute(() -> {
            try {
                if (!shell("id").contains("uid=0")) throw new Exception("Thiết bị chưa cấp root cho Clone Windows.");
                if (!Settings.canDrawOverlays(this)) throw new Exception("Cần quyền hiển thị trên ứng dụng khác.");
                shell("settings put global enable_freeform_support 1; settings put global force_resizable_activities 1");
                prefs.edit().remove("error").apply();
                worker.scheduleWithFixedDelay(this::tick, 0, 100, TimeUnit.MILLISECONDS);
            } catch (Exception e) { fail(e); }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String pkg = intent.getStringExtra("launch");
            if (pkg != null && pkg.matches("com\\.roblox\\.clone(0[1-9]|1[01])")) {
                worker.execute(() -> {
                    try {
                        shell("am start --windowingMode 5 -n " + pkg + "/com.roblox.client.startup.LauncherAliasMain");
                        known.remove(pkg);
                        nextPoll = 0;
                    } catch (Exception e) { fail(e); }
                });
            }
        }
        return START_STICKY;
    }

    private String shell(String command) throws Exception {
        boolean first = root == null;
        if (first) {
            root = new ProcessBuilder("su").redirectErrorStream(true).start();
            rootOutput = new BufferedReader(new InputStreamReader(root.getInputStream(), StandardCharsets.UTF_8));
            rootInput = new BufferedWriter(new OutputStreamWriter(root.getOutputStream(), StandardCharsets.UTF_8));
        }
        Process active = root;
        ScheduledFuture<?> timeout = deadlines.schedule(active::destroyForcibly, first ? 60 : 8, TimeUnit.SECONDS);
        String marker = "__CLONE_WINDOWS_" + (++commandId) + "_";
        StringBuilder output = new StringBuilder();
        try {
            rootInput.write(command + "\nprintf '\\n" + marker + "%s__\\n' \"$?\"\n");
            rootInput.flush();
            String line;
            while ((line = rootOutput.readLine()) != null) {
                if (line.startsWith(marker)) {
                    if (!line.equals(marker + "0__") || output.indexOf("Exception") >= 0 || output.indexOf("Error:") >= 0)
                        throw new Exception("Lỗi cửa sổ: " + output.substring(0, Math.min(240, output.length())));
                    return output.toString();
                }
                output.append(line).append('\n');
            }
            throw new Exception("Phiên root đã đóng hoặc hết thời gian. Hãy cấp root rồi bật lại.");
        } finally {
            timeout.cancel(false);
        }
    }

    private void tick() {
        if (!alive) return;
        try {
            Resize change = pending.getAndSet(null);
            if (change != null) {
                resize(change.task, change.bounds);
                save(change.pkg, change.bounds);
            }
            if (dragging || System.currentTimeMillis() < nextPoll) return;
            nextPoll = System.currentTimeMillis() + 1000;
            String dump = shell("am stack list");
            Set<String> seen = new HashSet<>();
            Resize front = null;
            boolean freeform = false;
            for (String line : dump.split("\n")) {
                if (line.startsWith("Stack id=")) freeform = false;
                if (line.contains("mWindowingMode=")) freeform = line.contains("mWindowingMode=freeform");
                Matcher m = TASK.matcher(line);
                if (!m.find()) continue;
                int task = Integer.parseInt(m.group(1));
                String pkg = m.group(2);
                seen.add(pkg);
                Rect current = new Rect(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                        Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
                boolean visible = Boolean.parseBoolean(m.group(7));
                boolean fresh = !Integer.valueOf(task).equals(known.get(pkg));
                if (visible && (fresh || !freeform || current.width() >= screenW || current.height() >= screenH)) {
                    current = saved(pkg);
                    if (!freeform) moveToFreeform(task);
                    resize(task, current);
                    known.put(pkg, task);
                } else if (visible && freeform) {
                    save(pkg, current);
                }
                if (front == null && visible) front = new Resize(task, pkg, current);
            }
            known.keySet().retainAll(seen);
            Resize selected = front;
            ui.post(() -> {
                if (!alive || dragging) return;
                if (selected == null) clearEdges(); else showEdges(selected);
            });
        } catch (Exception e) { fail(e); }
    }

    private void resize(int task, Rect r) throws Exception {
        shell(String.format(Locale.US, "am task resize %d %d %d %d %d", task, r.left, r.top, r.right, r.bottom));
    }
    private void moveToFreeform(int task) throws Exception {
        shell("am start --windowingMode 5 -f 0x18000000 -n " + getPackageName() + "/.CarrierActivity");
        String dump = shell("am stack list");
        int stack = -1, carrierStack = -1;
        for (String line : dump.split("\n")) {
            if (line.startsWith("Stack id=")) {
                int end = line.indexOf(' ', 9);
                stack = Integer.parseInt(line.substring(9, end));
            }
            if (line.contains(getPackageName() + "/") && line.contains("CarrierActivity")) carrierStack = stack;
        }
        if (carrierStack < 0) throw new Exception("Không tạo được cửa sổ nổi trên thiết bị này.");
        try {
            shell("am stack move-task " + task + " " + carrierStack + " true");
        } finally {
            ui.post(() -> { if (CarrierActivity.active != null) CarrierActivity.active.finishAndRemoveTask(); });
        }
    }
    private Rect saved(String pkg) {
        String value = prefs.getString(pkg, "");
        Rect r = value.isEmpty() ? null : Rect.unflattenFromString(value);
        if (r == null) r = new Rect(screenW / 4, screenH / 4, screenW * 3 / 4, screenH * 3 / 4);
        int width = Math.min(Math.max(r.width(), Math.min(240, screenW / 2)), screenW - grip * 2);
        int height = Math.min(Math.max(r.height(), Math.min(160, screenH / 2)), screenH - grip * 2 - 32);
        int left = Math.max(grip, Math.min(r.left, screenW - width - grip));
        int top = Math.max(32, Math.min(r.top, screenH - height - grip));
        return new Rect(left, top, left + width, top + height);
    }
    private void save(String pkg, Rect r) {
        String text = r.flattenToString();
        if (!text.equals(prefs.getString(pkg, ""))) prefs.edit().putString(pkg, text).apply();
    }

    private void showEdges(Resize target) {
        if (target.task == shownTask && target.bounds.equals(shownBounds)) return;
        clearEdges();
        shownTask = target.task; shownBounds = new Rect(target.bounds);
        Rect b = target.bounds;
        edge(target, 1, b.left - grip, b.top, grip, b.height());
        edge(target, 2, b.right, b.top, grip, b.height());
        edge(target, 4, b.left - grip, b.top - grip, b.width() + grip * 2, grip);
        edge(target, 8, b.left - grip, b.bottom, b.width() + grip * 2, grip);
    }
    private void edge(Resize target, int side, int x, int y, int width, int height) {
        View view = new View(this);
        view.setBackgroundColor(Color.argb(120, 0, 180, 210));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT; lp.x = x; lp.y = y;
        lp.setTitle("CloneWindows-" + side);
        view.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            Rect start, last;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragging = true;
                    startX = event.getRawX(); startY = event.getRawY();
                    start = new Rect(shownBounds); last = new Rect(start);
                    return true;
                }
                if (start == null) return false;
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE || event.getActionMasked() == MotionEvent.ACTION_UP) {
                    Rect r = new Rect(start);
                    int dx = Math.round(event.getRawX() - startX), dy = Math.round(event.getRawY() - startY);
                    if (side == 1) r.left = Math.max(grip, Math.min(start.left + dx, start.right - 240));
                    if (side == 2) r.right = Math.min(screenW - grip, Math.max(start.right + dx, start.left + 240));
                    if (side == 4) r.top = Math.max(32, Math.min(start.top + dy, start.bottom - 160));
                    if (side == 8) r.bottom = Math.min(screenH - grip, Math.max(start.bottom + dy, start.top + 160));
                    last = r;
                    pending.set(new Resize(target.task, target.pkg, r));
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    pending.set(new Resize(target.task, target.pkg, last));
                    shownBounds = new Rect(last);
                    dragging = false;
                    // Recreate grips only after the active touch stream has ended.
                    ui.postDelayed(() -> { if (alive && !dragging) { shownTask = -1; showEdges(new Resize(target.task, target.pkg, last)); } }, 150);
                }
                return true;
            }
        });
        wm.addView(view, lp);
        edges.add(view);
    }
    private void clearEdges() {
        for (View edge : edges) {
            try { wm.removeView(edge); } catch (IllegalArgumentException ignored) { }
        }
        edges.clear(); shownTask = -1; shownBounds = null;
    }
    private void fail(Exception e) {
        Log.e("CloneWindows", "Window controller stopped", e);
        prefs.edit().putString("error", e.getMessage()).putBoolean("enabled", false).apply();
        ui.post(this::stopSelf);
    }
    @Override public void onDestroy() {
        alive = false;
        worker.shutdownNow();
        if (root != null) root.destroyForcibly();
        deadlines.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        clearEdges();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
