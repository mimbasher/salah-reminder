package com.salah.reminder;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Compass that points toward the Kaaba. */
public class QiblaActivity extends Activity implements SensorEventListener {
    private SensorManager sm;
    private Sensor rot, acc, mag;
    private final float[] accV = new float[3], magV = new float[3];
    private boolean hasAcc, hasMag;
    private float heading = Float.NaN, declination;
    private double qibla;
    private CompassView compass;
    private TextView info, hint;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        info = new TextView(this);
        info.setTextSize(20);
        info.setGravity(Gravity.CENTER);
        root.addView(info);

        compass = new CompassView(this);
        root.addView(compass, new LinearLayout.LayoutParams(-1, 0, 1));

        hint = new TextView(this);
        hint.setGravity(Gravity.CENTER);
        hint.setText("Hold the phone flat, away from metal and magnets.\nIf it seems off, move the phone in a figure-8.");
        root.addView(hint);
        setContentView(root);

        if (!Scheduler.hasLocation(this)) {
            info.setText("Set your location in the main screen first.");
            return;
        }
        double lat = Scheduler.lat(this), lng = Scheduler.lng(this);
        qibla = PrayerCalc.qibla(lat, lng);
        declination = new GeomagneticField((float) lat, (float) lng, 0, System.currentTimeMillis()).getDeclination();
        compass.qibla = (float) qibla;

        sm = getSystemService(SensorManager.class);
        rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (rot == null && (acc == null || mag == null)) {
            info.setText(String.format(Locale.US, "Qibla: %.0f° from true north\n(no compass sensor on this phone)", qibla));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm == null) return;
        if (rot != null) sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_UI);
        else if (acc != null && mag != null) {
            sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
            sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float[] r = new float[9], o = new float[3];
        if (e.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(r, e.values);
        } else {
            if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) { System.arraycopy(e.values, 0, accV, 0, 3); hasAcc = true; }
            else { System.arraycopy(e.values, 0, magV, 0, 3); hasMag = true; }
            if (!hasAcc || !hasMag || !SensorManager.getRotationMatrix(r, null, accV, magV)) return;
        }
        SensorManager.getOrientation(r, o);
        float h = (float) ((Math.toDegrees(o[0]) + declination + 360) % 360);
        if (Float.isNaN(heading)) heading = h;
        else {
            float d = ((h - heading + 540) % 360) - 180;   // shortest way round
            heading = (heading + d * 0.15f + 360) % 360;   // smoothing
        }
        compass.heading = heading;
        compass.invalidate();
        float off = (float) ((((qibla - heading) % 360) + 540) % 360 - 180);
        boolean aligned = Math.abs(off) < 5;
        info.setText(String.format(Locale.US, "Qibla: %.0f° from north\n%s", qibla,
                aligned ? "✓ You are facing the Qibla"
                        : off > 0 ? "Turn right " + Math.round(off) + "°" : "Turn left " + Math.round(-off) + "°"));
        info.setTextColor(aligned ? Color.parseColor("#0F6E56") : Color.DKGRAY);
        compass.aligned = aligned;
    }

    @Override
    public void onAccuracyChanged(Sensor s, int acc) {
        if (acc <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            hint.setText("⚠ Compass needs calibration: move the phone in a figure-8 a few times.");
        }
    }

    /** Draws a dial that rotates with the phone and a Kaaba pointer. */
    static class CompassView extends View {
        float heading = 0, qibla = 0;
        boolean aligned;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        CompassView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) * 0.85f;
            int green = Color.parseColor("#0F6E56");

            // fixed marker at top = where the phone points
            p.setStyle(Paint.Style.FILL);
            p.setColor(aligned ? green : Color.GRAY);
            Path top = new Path();
            top.moveTo(cx, cy - r - 4);
            top.lineTo(cx - 14, cy - r - 30);
            top.lineTo(cx + 14, cy - r - 30);
            top.close();
            c.drawPath(top, p);

            c.save();
            c.rotate(-heading, cx, cy);          // dial: north follows real north
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            p.setColor(Color.LTGRAY);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r * 0.14f);
            String[] dirs = {"N", "E", "S", "W"};
            for (int k = 0; k < 4; k++) {
                p.setColor(k == 0 ? Color.RED : Color.DKGRAY);
                c.drawText(dirs[k], cx, cy - r * 0.78f, p);
                c.rotate(90, cx, cy);
            }

            c.rotate(qibla, cx, cy);             // arrow toward the Kaaba
            p.setColor(green);
            Path arrow = new Path();
            arrow.moveTo(cx, cy - r * 0.62f);
            arrow.lineTo(cx - r * 0.1f, cy - r * 0.38f);
            arrow.lineTo(cx - r * 0.03f, cy - r * 0.38f);
            arrow.lineTo(cx - r * 0.03f, cy + r * 0.25f);
            arrow.lineTo(cx + r * 0.03f, cy + r * 0.25f);
            arrow.lineTo(cx + r * 0.03f, cy - r * 0.38f);
            arrow.lineTo(cx + r * 0.1f, cy - r * 0.38f);
            arrow.close();
            c.drawPath(arrow, p);
            // Kaaba cube
            float s = r * 0.09f;
            p.setColor(Color.BLACK);
            c.drawRect(cx - s, cy - r * 0.62f - 2.4f * s, cx + s, cy - r * 0.62f - 0.4f * s, p);
            p.setColor(Color.parseColor("#D4AF37"));
            c.drawRect(cx - s, cy - r * 0.62f - 1.9f * s, cx + s, cy - r * 0.62f - 1.6f * s, p);
            c.restore();

            p.setColor(Color.DKGRAY);
            c.drawCircle(cx, cy, r * 0.04f, p);
        }
    }
}
