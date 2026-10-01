package com.salah.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int RQ_NOTIF = 1, RQ_LOC = 2, RQ_AUDIO = 3;
    private static final int GREEN = Color.parseColor("#0F6E56");

    private LinearLayout root;
    private TextView timesView, placeView, adhanFileView;
    private EditText cityE, latE, lngE, preE, nagE;
    private Spinner methodS, asrS;
    private CheckBox adhanCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Scheduler.ensureChannels(this);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RQ_NOTIF);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------------- UI ----------------

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(GREEN);
        t.setPadding(0, dp(22), 0, dp(6));
        root.addView(t);
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(8), 0, 0);
        root.addView(t);
        return t;
    }

    private Button button(String s, View.OnClickListener l, LinearLayout parent) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setAllCaps(false);
        btn.setOnClickListener(l);
        if (parent == null) root.addView(btn);
        else parent.addView(btn, new LinearLayout.LayoutParams(0, -2, 1));
        return btn;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(r);
        return r;
    }

    private EditText field(String hint, int type, LinearLayout parent) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        if (parent == null) root.addView(e);
        else parent.addView(e, new LinearLayout.LayoutParams(0, -2, 1));
        return e;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        root.addView(s);
        return s;
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(40));
        sv.addView(root);
        setContentView(sv);
        int num = InputType.TYPE_CLASS_NUMBER;
        int signedDec = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED;

        placeView = new TextView(this);
        placeView.setTextSize(15);
        root.addView(placeView);

        timesView = new TextView(this);
        timesView.setTextSize(18);
        timesView.setTypeface(Typeface.MONOSPACE);
        timesView.setPadding(0, dp(10), 0, dp(6));
        root.addView(timesView);

        Button q = button("🧭  Qibla direction", v -> startActivity(new Intent(this, QiblaActivity.class)), null);
        q.setTextColor(Color.WHITE);
        q.setBackgroundColor(GREEN);

        // ---- Location ----
        header("Where are you?");
        LinearLayout r1 = row();
        cityE = field("City, e.g. Cairo or London", InputType.TYPE_CLASS_TEXT, r1);
        Button s = button("Search", v -> searchCity(), r1);
        ((LinearLayout.LayoutParams) s.getLayoutParams()).weight = 0;
        s.getLayoutParams().width = -2;
        button("📍 Use my current location (GPS)", v -> askLocation(), null);
        label("Or enter coordinates:");
        LinearLayout r2 = row();
        latE = field("Latitude", signedDec, r2);
        lngE = field("Longitude", signedDec, r2);

        // ---- Calculation ----
        header("Calculation");
        label("Method");
        methodS = spinner(PrayerCalc.METHODS);
        label("Asr");
        asrS = spinner(new String[]{"Standard (Shafi, Maliki, Hanbali)", "Hanafi"});

        // ---- Adhan ----
        header("Adhan sound");
        adhanCb = new CheckBox(this);
        adhanCb.setText("Play adhan at prayer time");
        root.addView(adhanCb);
        adhanFileView = label("");
        LinearLayout r3 = row();
        button("Choose sound file", v -> pickAudio(), r3);
        button("Use phone alarm", v -> {
            Scheduler.prefs(this).edit().remove("adhan_uri").remove("adhan_name").apply();
            refresh();
        }, r3);
        LinearLayout r4 = row();
        button("▶ Test adhan", v -> AdhanService.start(this, nextIndex()), r4);
        button("■ Stop", v -> AdhanService.stop(this), r4);

        // ---- Reminders ----
        header("Reminders");
        label("Heads-up before each adhan (minutes, 0 = off)");
        preE = field("10", num, null);
        label("Repeat reminder every (minutes) until I tap Prayed");
        nagE = field("10", num, null);

        Button save = button("Save settings", v -> save(), null);
        save.setTextColor(Color.WHITE);
        save.setBackgroundColor(GREEN);

        header("Make it reliable");
        button("Allow running in background (battery)", v -> askBattery(), null);
        button("Test full reminder now", v -> testNow(), null);

        loadFields();
    }

    private void loadFields() {
        android.content.SharedPreferences p = Scheduler.prefs(this);
        latE.setText(p.getString("lat", ""));
        lngE.setText(p.getString("lng", ""));
        methodS.setSelection(p.getInt("method", 0));
        asrS.setSelection(p.getBoolean("hanafi", false) ? 1 : 0);
        adhanCb.setChecked(p.getBoolean("adhan_on", true));
        preE.setText(String.valueOf(p.getInt("pre", 10)));
        nagE.setText(String.valueOf(p.getInt("nag", 10)));
    }

    private void refresh() {
        android.content.SharedPreferences p = Scheduler.prefs(this);
        String name = p.getString("adhan_name", null);
        adhanFileView.setText("Sound: " + (name != null ? name : "phone alarm tone"));
        if (!Scheduler.hasLocation(this)) {
            placeView.setText("📍 Location not set");
            timesView.setText("Search your city or use GPS below to see prayer times.");
            return;
        }
        placeView.setText("📍 " + p.getString("place", "Custom location") + String.format(Locale.US,
                "  (%.3f, %.3f)", Scheduler.lat(this), Scheduler.lng(this)));
        long now = System.currentTimeMillis();
        long[] t = Scheduler.timesFor(this, Calendar.getInstance());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            String st;
            if (Scheduler.pending(this, i) == t[i]) st = "⏳";
            else if (p.getBoolean("done_" + Scheduler.dayKey(t[i]) + "_" + i, false)) st = "✓";
            else st = "";
            sb.append(String.format(Locale.US, "%-8s %9s  %s%n", PrayerCalc.NAMES[i], Scheduler.fmt(t[i]), st));
        }
        if (p.contains("next_t")) {
            sb.append("\nNext: ").append(PrayerCalc.NAMES[p.getInt("next_i", 0)])
                    .append(" at ").append(Scheduler.fmt(p.getLong("next_t", now)));
        }
        timesView.setText(sb.toString().trim());
    }

    private int nextIndex() {
        return Scheduler.prefs(this).getInt("next_i", 1);
    }

    private void save() {
        android.content.SharedPreferences.Editor e = Scheduler.prefs(this).edit();
        String la = latE.getText().toString().trim().replace(',', '.');
        String lo = lngE.getText().toString().trim().replace(',', '.');
        if (!la.isEmpty() || !lo.isEmpty()) {
            try {
                double lat = Double.parseDouble(la), lng = Double.parseDouble(lo);
                if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new NumberFormatException();
                boolean changed = !la.equals(Scheduler.prefs(this).getString("lat", ""))
                        || !lo.equals(Scheduler.prefs(this).getString("lng", ""));
                e.putString("lat", la).putString("lng", lo);
                if (changed) e.putString("place", "Custom location");
            } catch (NumberFormatException ex) {
                toast("Latitude/longitude don't look right");
                return;
            }
        }
        e.putInt("method", methodS.getSelectedItemPosition());
        e.putBoolean("hanafi", asrS.getSelectedItemPosition() == 1);
        e.putBoolean("adhan_on", adhanCb.isChecked());
        e.putInt("pre", parseInt(preE, 10, 0, 120));
        e.putInt("nag", parseInt(nagE, 10, 1, 120));
        e.apply();
        Scheduler.scheduleNext(this, System.currentTimeMillis());
        toast("Saved — reminders scheduled");
        refresh();
    }

    private int parseInt(EditText e, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim())));
        } catch (Exception ex) {
            return def;
        }
    }

    private void setPlace(String name, double lat, double lng) {
        String la = String.format(Locale.US, "%.4f", lat), lo = String.format(Locale.US, "%.4f", lng);
        Scheduler.prefs(this).edit().putString("lat", la).putString("lng", lo).putString("place", name).apply();
        latE.setText(la);
        lngE.setText(lo);
        Scheduler.scheduleNext(this, System.currentTimeMillis());
        refresh();
        toast("Location set: " + name);
    }

    private void testNow() {
        if (!Scheduler.hasLocation(this)) { toast("Set your location first"); return; }
        long now = System.currentTimeMillis();
        long[] t = Scheduler.timesFor(this, Calendar.getInstance());
        int i = 0;
        for (int k = 0; k < 5; k++) if (t[k] <= now) i = k;
        Scheduler.onPrayerTime(this, i, t[i]);
        refresh();
    }

    // ---------------- City search ----------------

    private static String placeName(Address a) {
        String city = a.getLocality() != null ? a.getLocality()
                : a.getSubAdminArea() != null ? a.getSubAdminArea() : a.getFeatureName();
        String country = a.getCountryName();
        if (city == null) return country != null ? country : "Selected location";
        return country != null ? city + ", " + country : city;
    }

    @SuppressWarnings("deprecation")
    private void searchCity() {
        String q = cityE.getText().toString().trim();
        if (q.isEmpty()) { toast("Type a city name"); return; }
        if (!Geocoder.isPresent()) {
            toast("City search isn't available on this phone — use GPS or coordinates");
            return;
        }
        toast("Searching…");
        new Thread(() -> {
            List<Address> res = null;
            try { res = new Geocoder(this, Locale.getDefault()).getFromLocationName(q, 6); }
            catch (Exception ignored) { }
            final List<Address> found = res;
            runOnUiThread(() -> showResults(found));
        }).start();
    }

    private void showResults(List<Address> res) {
        if (res == null || res.isEmpty()) {
            toast("No match found (needs internet). Try \"City, Country\".");
            return;
        }
        List<Address> ok = new ArrayList<>();
        for (Address a : res) if (a.hasLatitude() && a.hasLongitude()) ok.add(a);
        if (ok.size() == 1) {
            Address a = ok.get(0);
            setPlace(placeName(a), a.getLatitude(), a.getLongitude());
            return;
        }
        String[] names = new String[ok.size()];
        for (int k = 0; k < ok.size(); k++) {
            Address a = ok.get(k);
            String region = a.getAdminArea() != null ? " (" + a.getAdminArea() + ")" : "";
            names[k] = placeName(a) + region;
        }
        new AlertDialog.Builder(this).setTitle("Which one?")
                .setItems(names, (d, w) -> {
                    Address a = ok.get(w);
                    setPlace(placeName(a), a.getLatitude(), a.getLongitude());
                }).show();
    }

    // ---------------- GPS ----------------

    private void askLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, RQ_LOC);
        } else {
            fetchLocation();
        }
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private void fetchLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) { }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 6 * 3600000L) {
            gotLocation(best);
            return;
        }
        String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (prov == null) {
            if (best != null) { gotLocation(best); return; }
            toast("Turn on Location in your phone settings");
            return;
        }
        toast("Getting your location…");
        try {
            lm.requestSingleUpdate(prov, new LocationListener() {
                @Override public void onLocationChanged(Location l) { gotLocation(l); }
                @Override public void onStatusChanged(String p, int s, Bundle e) { }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onProviderDisabled(String p) { }
            }, Looper.getMainLooper());
        } catch (SecurityException ignored) { }
    }

    @SuppressWarnings("deprecation")
    private void gotLocation(Location l) {
        double lat = l.getLatitude(), lng = l.getLongitude();
        new Thread(() -> {
            String name = "My location";
            try {
                if (Geocoder.isPresent()) {
                    List<Address> r = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lng, 1);
                    if (r != null && !r.isEmpty()) name = placeName(r.get(0));
                }
            } catch (Exception ignored) { }
            final String n = name;
            runOnUiThread(() -> setPlace(n, lat, lng));
        }).start();
    }

    // ---------------- Adhan file ----------------

    private void pickAudio() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("audio/*");
        startActivityForResult(it, RQ_AUDIO);
    }

    @Override
    protected void onActivityResult(int rq, int res, Intent data) {
        super.onActivityResult(rq, res, data);
        if (rq != RQ_AUDIO || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        String name = "Custom sound";
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        Scheduler.prefs(this).edit().putString("adhan_uri", u.toString()).putString("adhan_name", name)
                .putBoolean("adhan_on", true).apply();
        adhanCb.setChecked(true);
        refresh();
        toast("Adhan sound set");
    }

    // ---------------- misc ----------------

    private void askBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast("Already allowed ✓");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] perms, int[] res) {
        if (rq == RQ_LOC) {
            boolean ok = false;
            for (int r : res) ok |= r == PackageManager.PERMISSION_GRANTED;
            if (ok) fetchLocation();
            else toast("No location permission — search your city instead");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
