package com.rodneyhertz.hvactools;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

/*
 * Magnetômetro -> app, sem depender de recursos internos do Capacitor:
 *  - a página avisa que quer dados definindo window.__magWant = true;
 *  - o Java pergunta isso à página a cada ~200 ms (evaluateJavascript);
 *  - quando a página quer, o Java liga o sensor e envia lotes de leituras
 *    chamando window.onNativeMag([[x,y,z],...]).
 */
public class MainActivity extends BridgeActivity implements SensorEventListener {
    private SensorManager sm;
    private Sensor sensor;
    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder buf = new StringBuilder();
    private int count = 0;
    private int tick = 0;
    private boolean listening = false;
    private boolean looping = false;

    private final Runnable pump = new Runnable() {
        @Override
        public void run() {
            if (!looping) return;
            tick++;
            if (tick % 4 == 1) askPage();
            if (listening && count > 0) {
                final String js = "window.onNativeMag&&window.onNativeMag([" + buf + "])";
                buf.setLength(0);
                count = 0;
                web.evaluateJavascript(js, null);
            }
            handler.postDelayed(this, 50);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = getBridge().getWebView();
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public void onResume() {
        super.onResume();
        looping = true;
        handler.removeCallbacks(pump);
        handler.post(pump);
    }

    @Override
    public void onPause() {
        looping = false;
        handler.removeCallbacks(pump);
        setListening(false);
        super.onPause();
    }

    private void askPage() {
        if (web == null) return;
        web.evaluateJavascript("(function(){return window.__magWant===true;})()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                setListening("true".equals(value));
            }
        });
    }

    private void setListening(boolean on) {
        if (on && !listening && sensor != null) {
            sm.registerListener(this, sensor, 10000);
            listening = true;
        } else if (!on && listening) {
            sm.unregisterListener(this);
            listening = false;
            buf.setLength(0);
            count = 0;
        }
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
