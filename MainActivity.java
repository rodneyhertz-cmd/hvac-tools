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
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

public class MainActivity extends BridgeActivity {
    private MagBridge mag;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Plugin nativo do Capacitor (caminho oficial): precisa ser registrado ANTES do super.onCreate
        registerPlugin(MagPlugin.class);
        super.onCreate(savedInstanceState);
        // Ponte antiga (window.AndroidMag), mantida como reserva
        final WebView web = getBridge().getWebView();
        mag = new MagBridge(this, web);
        web.addJavascriptInterface(mag, "AndroidMag");
    }

    @Override
    public void onPause() {
        if (mag != null) mag.stop();
        super.onPause();
    }

    /* ===== Plugin Capacitor "MagBridge": magnetômetro -> app ===== */
    @CapacitorPlugin(name = "MagBridge")
    public static class MagPlugin extends Plugin implements SensorEventListener {
        private SensorManager sm;
        private Sensor sensor;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final JSArray batch = new JSArray();
        private boolean running = false;

        private final Runnable flush = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                if (batch.length() > 0) {
                    JSObject ret = new JSObject();
                    ret.put("d", batch);
                    notifyListeners("mag", ret);
                    clearBatch();
                }
                handler.postDelayed(this, 50);
            }
        };

        private void clearBatch() {
            while (batch.length() > 0) batch.remove(batch.length() - 1);
        }

        @Override
        public void load() {
            sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        @PluginMethod
        public void has(PluginCall call) {
            JSObject r = new JSObject();
            r.put("value", sensor != null);
            call.resolve(r);
        }

        @PluginMethod
        public void start(PluginCall call) {
            if (sensor != null && !running) {
                running = true;
                sm.registerListener(this, sensor, 10000);
                handler.postDelayed(flush, 50);
            }
            call.resolve();
        }

        @PluginMethod
        public void stop(PluginCall call) {
            stopSensing();
            call.resolve();
        }

        private void stopSensing() {
            if (!running) return;
            running = false;
            sm.unregisterListener(this);
            handler.removeCallbacks(flush);
            clearBatch();
        }

        @Override
        protected void handleOnPause() {
            stopSensing();
        }

        @Override
        public void onSensorChanged(SensorEvent e) {
            JSArray p = new JSArray();
            try {
                p.put((double) e.values[0]);
                p.put((double) e.values[1]);
                p.put((double) e.values[2]);
            } catch (Exception ex) { return; }
            batch.put(p);
        }

        @Override
        public void onAccuracyChanged(Sensor s, int accuracy) { }
    }

    /* ===== Ponte antiga window.AndroidMag (reserva) ===== */
    public static class MagBridge implements SensorEventListener {
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

        public MagBridge(Context c, WebView w) {
            sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
            sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            web = w;
        }

        @JavascriptInterface
        public boolean has() { return sensor != null; }

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
}
