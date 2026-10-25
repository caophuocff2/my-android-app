package com.caophuoc.clonewindows;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Briefly creates a freeform stack for an already-running fullscreen task. */
public class CarrierActivity extends Activity {
    static CarrierActivity active;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        active = this;
        TextView label = new TextView(this);
        label.setText("Đang khôi phục cửa sổ…");
        label.setPadding(24, 24, 24, 24);
        setContentView(label);
    }
    @Override public void onDestroy() {
        if (active == this) active = null;
        super.onDestroy();
    }
}
