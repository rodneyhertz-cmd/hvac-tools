package com.rodneyhertz.hvactools;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private MagBridge mag;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView web = getBridge().getWebView();
        mag = new MagBridge(this, web);
        web.addJavascriptInterface(mag, "AndroidMag");
    }

    @Override
    public void onPause() {
        if (mag != null) mag.stop();
        super.onPause();
    }
}

/* Ponte entre o magnetômetro do Android e o app (window.AndroidMag) */
class MagBridge implements SensorEventListener {
    private final SensorManager sm;
    private final Sensor sensor;
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder buf = new StringBuilder();
    private int count = 0;
    private boolean running = false;

    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (count > 0) {
                final String js = "window.onNativeMag&&window.onNativeMag([" + buf + "])";
                buf.setLength(0);
                count = 0;
                web.evaluateJavascript(js, null);
            }
            handler.postDelayed(this, 50);
        }
    };

    MagBridge(Context c, WebView w) {
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        web = w;
    }

    @JavascriptInterface
    public boolean has() {
        return sensor != null;
    }

    @JavascriptInterface
    public void start() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (sensor == null || running) return;
                running = true;
                sm.registerListener(MagBridge.this, sensor, 10000);
                handler.postDelayed(flush, 50);
            }
        });
    }

    @JavascriptInterface
    public void stop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                running = false;
                sm.unregisterListener(MagBridge.this);
                handler.removeCallbacks(flush);
                buf.setLength(0);
                count = 0;
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (count > 0) buf.append(',');
        buf.append('[').append(e.values[0]).append(',').append(e.values[1]).append(',').append(e.values[2]).append(']');
        count++;
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) { }
}
