package com.salah.reminder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** A compass that points to the Kaaba, with clear "turn left / turn right" guidance. */
public class QiblaActivity extends Activity implements SensorEventListener {
    private SensorManager sm;
    private Sensor rot, acc, mag;
    private final float[] accV = new float[3], magV = new float[3];
    private boolean hasAcc, hasMag, wasAligned;
    private float heading = Float.NaN, declination;
    private double qibla;
    private CompassView compass;
    private TextView status, detail, hint;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(dp(18), dp(16), dp(18), dp(22));

        LinearLayout head = Ui.rowOf(this);
        head.addView(Ui.iconButton(this, "←", v -> finish()));
        TextView title = Ui.text(this, "Qibla", 21, Ui.TEXT, Ui.MEDIUM);
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title);
        root.addView(head, Ui.lp(-1, -2));

        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        status = Ui.text(this, "Looking for north…", 22, Ui.TEXT, Ui.LIGHT);
        status.setGravity(Gravity.CENTER);
        card.addView(status);
        detail = Ui.text(this, "", 12.5f, Ui.MUTED, null);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(6), 0, 0);
        card.addView(detail);
        root.addView(card, Ui.stacked(this, 16));

        compass = new CompassView(this);
        root.addView(compass, Ui.lp(-1, 0, 1));

        hint = Ui.text(this, "Hold the phone flat, away from metal and magnets.", 12, Ui.MUTED, null);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(10), dp(8), dp(10), 0);
        root.addView(hint, Ui.lp(-1, -2));
        setContentView(root);

        if (!Scheduler.hasLocation(this)) {
            status.setText("Set your location first");
            detail.setText("The Qibla direction depends on where you are.");
            card.addView(Ui.primary(this, "Open settings",
                    v -> startActivity(new Intent(this, SettingsActivity.class))), Ui.stacked(this, 14));
            return;
        }

        double lat = Scheduler.lat(this), lng = Scheduler.lng(this);
        qibla = PrayerCalc.qibla(lat, lng);
        declination = new GeomagneticField((float) lat, (float) lng, 0,
                System.currentTimeMillis()).getDeclination();
        compass.qibla = (float) qibla;
        detail.setText(String.format(Locale.US, "Qibla %.0f° from true north · %s",
                qibla, Scheduler.prefs(this).getString("place", "your location")));

        sm = getSystemService(SensorManager.class);
        rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (rot == null && (acc == null || mag == null)) {
            status.setText(String.format(Locale.US, "%.0f° from north", qibla));
            hint.setText("This phone has no compass sensor, so point it using the angle above.");
        }
    }

    private int dp(float v) { return Ui.dp(this, v); }

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
            if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(e.values, 0, accV, 0, 3);
                hasAcc = true;
            } else {
                System.arraycopy(e.values, 0, magV, 0, 3);
                hasMag = true;
            }
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

        float off = (float) ((((qibla - heading) % 360) + 540) % 360 - 180);
        boolean aligned = Math.abs(off) < 5;
        compass.aligned = aligned;
        compass.invalidate();

        status.setText(aligned ? "✓ Facing the Qibla"
                : off > 0 ? "Turn right " + Math.round(off) + "°"
                : "Turn left " + Math.round(-off) + "°");
        status.setTextColor(aligned ? Ui.ACCENT : Ui.TEXT);
        if (aligned && !wasAligned) buzz();
        wasAligned = aligned;
    }

    private void buzz() {
        try {
            Vibrator vb = getSystemService(Vibrator.class);
            if (vb != null && vb.hasVibrator()) {
                vb.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            hint.setText("⚠ Compass needs calibrating — wave the phone in a figure-8.");
            hint.setTextColor(Ui.AMBER);
        } else {
            hint.setText("Hold the phone flat, away from metal and magnets.");
            hint.setTextColor(Ui.MUTED);
        }
    }

    /** The dial: ticks and letters turn with the world, the gold needle points at Makkah. */
    static class CompassView extends View {
        float heading = 0, qibla = 0;
        boolean aligned;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CompassView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) * 0.84f;
            if (r <= 0) return;

            // face
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.SURFACE);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.02f);
            p.setColor(aligned ? Ui.ACCENT : Ui.LINE);
            c.drawCircle(cx, cy, r, p);
            if (aligned) {
                p.setColor(Ui.alpha(Ui.ACCENT, 0x44));
                p.setStrokeWidth(r * 0.06f);
                c.drawCircle(cx, cy, r * 1.04f, p);
            }

            // the marker at the top shows where the phone is pointing
            p.setStyle(Paint.Style.FILL);
            p.setColor(aligned ? Ui.ACCENT : Ui.MUTED);
            path.reset();
            path.moveTo(cx, cy - r * 0.92f);
            path.lineTo(cx - r * 0.065f, cy - r * 1.02f);
            path.lineTo(cx + r * 0.065f, cy - r * 1.02f);
            path.close();
            c.drawPath(path, p);

            c.save();
            c.rotate(-heading, cx, cy);          // dial follows true north

            // ticks
            for (int deg = 0; deg < 360; deg += 6) {
                boolean major = deg % 30 == 0;
                float len = major ? r * 0.12f : r * 0.055f;
                p.setStrokeWidth(major ? r * 0.016f : r * 0.008f);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(major ? 0xFF6B7C93 : 0xFF3A4759);
                c.drawLine(cx, cy - r * 0.9f, cx, cy - r * 0.9f + len, p);
                c.rotate(6, cx, cy);
            }

            // cardinal letters
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Ui.MEDIUM);
            p.setTextSize(r * 0.15f);
            String[] dirs = {"N", "E", "S", "W"};
            for (int k = 0; k < 4; k++) {
                p.setColor(k == 0 ? Ui.GOLD : Ui.MUTED);
                c.drawText(dirs[k], cx, cy - r * 0.66f, p);
                c.rotate(90, cx, cy);
            }

            // the Qibla needle
            c.rotate(qibla, cx, cy);
            p.setColor(aligned ? Ui.ACCENT : Ui.GOLD);
            path.reset();
            path.moveTo(cx, cy - r * 0.56f);
            path.lineTo(cx - r * 0.085f, cy - r * 0.34f);
            path.lineTo(cx - r * 0.026f, cy - r * 0.34f);
            path.lineTo(cx - r * 0.026f, cy + r * 0.3f);
            path.lineTo(cx + r * 0.026f, cy + r * 0.3f);
            path.lineTo(cx + r * 0.026f, cy - r * 0.34f);
            path.lineTo(cx + r * 0.085f, cy - r * 0.34f);
            path.close();
            c.drawPath(path, p);

            // the Kaaba at the tip
            float s = r * 0.085f;
            float top = cy - r * 0.56f;
            p.setColor(0xFF0A0A0A);
            c.drawRect(cx - s, top - 2.5f * s, cx + s, top - 0.4f * s, p);
            p.setColor(Ui.GOLD);
            c.drawRect(cx - s, top - 1.85f * s, cx + s, top - 1.55f * s, p);
            c.restore();

            // hub
            p.setColor(Ui.SURFACE_2);
            c.drawCircle(cx, cy, r * 0.07f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.012f);
            p.setColor(Ui.LINE);
            c.drawCircle(cx, cy, r * 0.07f, p);
        }
    }
}
