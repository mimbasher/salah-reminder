package com.salah.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Everything configurable, grouped and saved the moment you change it. */
public class SettingsActivity extends Activity {
    private static final int RQ_LOC = 2, RQ_AUDIO = 3, RQ_CITY = 4;
    private static final int[] PRE_CHOICES = {0, 5, 10, 15, 20, 30, 45};
    private static final int[] NAG_CHOICES = {5, 10, 15, 20, 30, 60};

    private interface Pick { void on(int value); }

    private TextView placeView, soundView, updateStatus, updateButton;
    private EditText cityE, latE, lngE;
    private Spinner methodS, asrS;
    private Switch adhanSw;
    private LinearLayout preRow, nagRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.apply(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private SharedPreferences prefs() { return Scheduler.prefs(this); }

    // ---------------- layout ----------------

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.setPadding(dp(18), dp(16), dp(18), dp(36));
        sv.addView(root, Ui.lp(-1, -2));
        setContentView(sv);

        LinearLayout head = Ui.rowOf(this);
        head.addView(Ui.iconButton(this, "←", v -> finish()));
        TextView title = Ui.text(this, "Settings", 21, Ui.TEXT, Ui.MEDIUM);
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title);
        root.addView(head, Ui.lp(-1, -2));

        // ---- location ----
        root.addView(Ui.sectionTitle(this, "Where you are"));
        LinearLayout loc = Ui.card(this);
        placeView = Ui.text(this, "", 15, Ui.TEXT, Ui.MEDIUM);
        loc.addView(placeView);

        loc.addView(Ui.primary(this, "🌍  Choose your city",
                v -> startActivityForResult(new Intent(this, CityPickerActivity.class), RQ_CITY)),
                Ui.stacked(this, 14));
        loc.addView(Ui.secondary(this, "📍  Use my current location",
                v -> askLocation()), Ui.stacked(this, 10));

        loc.addView(Ui.divider(this));
        TextView advLabel = Ui.text(this, "If your town is not in the list", 12.5f, Ui.MUTED, null);
        advLabel.setPadding(0, dp(6), 0, dp(8));
        loc.addView(advLabel);

        LinearLayout search = Ui.rowOf(this);
        cityE = Ui.input(this, "Search online by name");
        search.addView(cityE, Ui.lp(0, -2, 1));
        TextView go = Ui.secondary(this, "Search", v -> searchCity());
        LinearLayout.LayoutParams gp = Ui.lp(-2, -2);
        gp.leftMargin = dp(8);
        search.addView(go, gp);
        loc.addView(search, Ui.lp(-1, -2));

        TextView coordLabel = Ui.text(this, "Or exact coordinates", 12.5f, Ui.MUTED, null);
        coordLabel.setPadding(0, dp(14), 0, dp(8));
        loc.addView(coordLabel);
        int signedDec = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
        LinearLayout coords = Ui.rowOf(this);
        latE = Ui.input(this, "Latitude");
        latE.setInputType(signedDec);
        lngE = Ui.input(this, "Longitude");
        lngE.setInputType(signedDec);
        coords.addView(latE, Ui.lp(0, -2, 1));
        View gap = new View(this);
        coords.addView(gap, Ui.lp(dp(8), 1));
        coords.addView(lngE, Ui.lp(0, -2, 1));
        loc.addView(coords, Ui.lp(-1, -2));
        loc.addView(Ui.secondary(this, "Use these coordinates", v -> applyCoords()),
                Ui.stacked(this, 10));
        root.addView(loc, Ui.lp(-1, -2));

        // ---- calculation ----
        root.addView(Ui.sectionTitle(this, "Calculation"));
        LinearLayout calc = Ui.card(this);
        calc.addView(fieldLabel("Method"));
        int method = Math.min(Math.max(0, prefs().getInt("method", 0)), PrayerCalc.METHODS.length - 1);
        methodS = dropdown(PrayerCalc.METHODS, method, i -> {
            prefs().edit().putInt("method", i).apply();
            reschedule("Method updated");
        });
        calc.addView(wrapField(methodS), Ui.lp(-1, -2));
        calc.addView(fieldLabel("Asr"));
        asrS = dropdown(new String[]{"Standard (Shafi, Maliki, Hanbali)", "Hanafi"},
                prefs().getBoolean("hanafi", false) ? 1 : 0, i -> {
            prefs().edit().putBoolean("hanafi", i == 1).apply();
            reschedule("Asr updated");
        });
        calc.addView(wrapField(asrS), Ui.lp(-1, -2));
        root.addView(calc, Ui.lp(-1, -2));

        // ---- adhan ----
        root.addView(Ui.sectionTitle(this, "Adhan sound"));
        LinearLayout ad = Ui.card(this);
        adhanSw = new Switch(this);
        adhanSw.setText("Play the adhan at prayer time");
        adhanSw.setTextSize(15);
        adhanSw.setTextColor(Ui.TEXT);
        adhanSw.setChecked(prefs().getBoolean("adhan_on", true));
        adhanSw.setOnCheckedChangeListener((v, on) ->
                prefs().edit().putBoolean("adhan_on", on).apply());
        ad.addView(adhanSw, Ui.lp(-1, -2));
        soundView = Ui.text(this, "", 12.5f, Ui.MUTED, null);
        soundView.setPadding(0, dp(8), 0, 0);
        ad.addView(soundView);

        LinearLayout pickRow = Ui.rowOf(this);
        pickRow.addView(Ui.secondary(this, "Choose a file", v -> pickAudio()), Ui.lp(0, -2, 1));
        View g2 = new View(this);
        pickRow.addView(g2, Ui.lp(dp(8), 1));
        pickRow.addView(Ui.secondary(this, "Use alarm tone", v -> {
            prefs().edit().remove("adhan_uri").remove("adhan_name").apply();
            refresh();
        }), Ui.lp(0, -2, 1));
        ad.addView(pickRow, Ui.stacked(this, 12));

        LinearLayout testRow = Ui.rowOf(this);
        testRow.addView(Ui.secondary(this, "▶  Test",
                v -> AdhanService.start(this, prefs().getInt("next_i", 1))), Ui.lp(0, -2, 1));
        View g3 = new View(this);
        testRow.addView(g3, Ui.lp(dp(8), 1));
        testRow.addView(Ui.secondary(this, "■  Stop",
                v -> AdhanService.stop(this)), Ui.lp(0, -2, 1));
        ad.addView(testRow, Ui.stacked(this, 8));
        root.addView(ad, Ui.lp(-1, -2));

        // ---- reminders ----
        root.addView(Ui.sectionTitle(this, "Reminders"));
        LinearLayout rem = Ui.card(this);
        rem.addView(fieldLabel("Heads-up before the adhan"));
        preRow = Ui.rowOf(this);
        rem.addView(scrollRow(preRow), Ui.lp(-1, -2));
        rem.addView(Ui.divider(this));
        rem.addView(fieldLabel("Repeat until I tap ✓ Prayed"));
        nagRow = Ui.rowOf(this);
        rem.addView(scrollRow(nagRow), Ui.lp(-1, -2));
        root.addView(rem, Ui.lp(-1, -2));

        // ---- reliability ----
        root.addView(Ui.sectionTitle(this, "Make it reliable"));
        LinearLayout rel = Ui.card(this);
        rel.addView(Ui.secondary(this, "Allow background running (battery)", v -> askBattery()),
                Ui.lp(-1, -2));
        if (Build.VERSION.SDK_INT >= 31) {
            rel.addView(Ui.secondary(this, "Allow exact alarms", v -> {
                try {
                    startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    toast("Open Android settings → Apps → Alarms & reminders");
                }
            }), Ui.stacked(this, 10));
        }
        rel.addView(Ui.secondary(this, "Notification settings", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (Exception e) {
                toast("Open Android settings → Apps → Notifications");
            }
        }), Ui.stacked(this, 10));
        rel.addView(Ui.primary(this, "Test a full reminder now", v -> testNow()),
                Ui.stacked(this, 14));
        root.addView(rel, Ui.lp(-1, -2));

        // ---- app & updates ----
        root.addView(Ui.sectionTitle(this, "App"));
        LinearLayout app = Ui.card(this);
        app.addView(Ui.text(this, "Version " + Updater.installedVersion(this), 15, Ui.TEXT, Ui.MEDIUM),
                Ui.lp(-1, -2));
        updateStatus = Ui.text(this, "Checked automatically when you open the app",
                12.5f, Ui.MUTED, null);
        updateStatus.setPadding(0, dp(6), 0, 0);
        app.addView(updateStatus, Ui.lp(-1, -2));
        app.addView(Ui.secondary(this, "Check for updates now", v -> checkUpdate()),
                Ui.stacked(this, 12));
        updateButton = Ui.primary(this, "Download and install", v -> Updater.install(this));
        updateButton.setVisibility(View.GONE);
        app.addView(updateButton, Ui.stacked(this, 10));
        app.addView(Ui.secondary(this, "Open the releases page",
                v -> Updater.openReleasesPage(this)), Ui.stacked(this, 10));
        root.addView(app, Ui.lp(-1, -2));

        TextView about = Ui.text(this,
                "Times are computed on your phone from the sun's position — no internet needed "
                        + "once your location is set.", 11.5f, Ui.MUTED, null);
        about.setGravity(Gravity.CENTER);
        about.setPadding(dp(8), dp(22), dp(8), 0);
        root.addView(about, Ui.lp(-1, -2));

        loadFields();
    }

    private TextView fieldLabel(String s) {
        TextView t = Ui.text(this, s, 12.5f, Ui.MUTED, null);
        t.setPadding(0, dp(10), 0, dp(8));
        return t;
    }

    private View wrapField(View v) {
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.outlined(Ui.SURFACE_2, Ui.LINE, this, 14));
        box.setPadding(dp(6), dp(2), dp(6), dp(2));
        box.addView(v, Ui.lp(-1, -2));
        return box;
    }

    private View scrollRow(LinearLayout row) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(row, Ui.lp(-2, -2));
        return h;
    }

    /** Spinner that reports only real user changes, never its own initial selection. */
    private Spinner dropdown(String[] items, int initial, Pick onPick) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setSelection(initial);
        final int[] last = {initial};
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == last[0]) return;
                last[0] = pos;
                onPick.on(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        return s;
    }

    private void paintChips(LinearLayout row, int[] values, int current, boolean minutes, Pick onPick) {
        row.removeAllViews();
        for (int v : values) {
            final int value = v;
            String label = v == 0 ? "Off" : v + (minutes ? " min" : "");
            TextView chip = Ui.chip(this, label, v == current, x -> onPick.on(value));
            LinearLayout.LayoutParams p = Ui.lp(-2, -2);
            p.rightMargin = dp(8);
            row.addView(chip, p);
        }
    }

    private void loadFields() {
        SharedPreferences p = prefs();
        latE.setText(p.getString("lat", ""));
        lngE.setText(p.getString("lng", ""));
        refresh();
    }

    private void refresh() {
        SharedPreferences p = prefs();
        showUpdateButton();
        String ready = Updater.available(this);
        if (ready != null) updateStatus.setText("Version " + ready + " is ready to install");
        placeView.setText(Scheduler.hasLocation(this)
                ? "📍  " + p.getString("place", "Custom location")
                + String.format(Locale.US, "  (%.3f, %.3f)", Scheduler.lat(this), Scheduler.lng(this))
                : "📍  Location not set yet");
        String name = p.getString("adhan_name", null);
        soundView.setText("Current sound: " + (name != null ? name : "your phone's alarm tone"));
        paintChips(preRow, PRE_CHOICES, p.getInt("pre", 10), true, v -> {
            prefs().edit().putInt("pre", v).apply();
            reschedule(v == 0 ? "Heads-up turned off" : "Heads-up set to " + v + " min before");
            refresh();
        });
        paintChips(nagRow, NAG_CHOICES, Scheduler.nagMinutes(this), true, v -> {
            prefs().edit().putInt("nag", v).apply();
            toast("Repeating every " + v + " min");
            refresh();
        });
    }

    private void checkUpdate() {
        updateStatus.setText("Checking…");
        Updater.check(this, true, (version, error) -> {
            if (error != null) updateStatus.setText(error);
            else if (version != null) updateStatus.setText("Version " + version + " is ready to install");
            else updateStatus.setText("You are on the latest version");
            showUpdateButton();
        });
    }

    private void showUpdateButton() {
        String version = Updater.available(this);
        updateButton.setVisibility(version == null ? View.GONE : View.VISIBLE);
    }

    private void reschedule(String msg) {
        Scheduler.scheduleNext(this, System.currentTimeMillis());
        toast(msg);
    }

    // ---------------- location ----------------

    private void applyCoords() {
        String la = latE.getText().toString().trim().replace(',', '.');
        String lo = lngE.getText().toString().trim().replace(',', '.');
        if (la.isEmpty() || lo.isEmpty()) { toast("Fill in both latitude and longitude"); return; }
        try {
            double lat = Double.parseDouble(la), lng = Double.parseDouble(lo);
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new NumberFormatException();
            setPlace("Custom location", lat, lng);
        } catch (NumberFormatException ex) {
            toast("Those coordinates don't look right");
        }
    }

    private void setPlace(String name, double lat, double lng) {
        prefs().edit()
                .putString("lat", String.format(Locale.US, "%.4f", lat))
                .putString("lng", String.format(Locale.US, "%.4f", lng))
                .putString("place", name).apply();
        latE.setText(String.format(Locale.US, "%.4f", lat));
        lngE.setText(String.format(Locale.US, "%.4f", lng));
        Scheduler.scheduleNext(this, System.currentTimeMillis());
        refresh();
        toast("Location set: " + name);
    }

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
            toast("City search isn't available here — use GPS or coordinates");
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
            toast("No match (needs internet). Try \"City, Country\".");
            return;
        }
        List<Address> ok = new ArrayList<>();
        for (Address a : res) if (a.hasLatitude() && a.hasLongitude()) ok.add(a);
        if (ok.isEmpty()) { toast("No match with coordinates"); return; }
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
        new AlertDialog.Builder(this, Ui.dark ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Which one?")
                .setItems(names, (d, w) -> {
                    Address a = ok.get(w);
                    setPlace(placeName(a), a.getLatitude(), a.getLongitude());
                }).show();
    }

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

    @Override
    public void onRequestPermissionsResult(int rq, String[] perms, int[] res) {
        if (rq == RQ_LOC) {
            boolean ok = false;
            for (int r : res) ok |= r == PackageManager.PERMISSION_GRANTED;
            if (ok) fetchLocation();
            else toast("No location permission — search your city instead");
        }
    }

    // ---------------- adhan file ----------------

    private void pickAudio() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("audio/*");
        try {
            startActivityForResult(it, RQ_AUDIO);
        } catch (Exception e) {
            toast("No file picker found on this phone");
        }
    }

    @Override
    protected void onActivityResult(int rq, int res, Intent data) {
        super.onActivityResult(rq, res, data);
        if (rq == RQ_CITY) {
            if (res == RESULT_OK && data != null) {
                setPlace(data.getStringExtra(CityPickerActivity.EX_NAME),
                        data.getDoubleExtra(CityPickerActivity.EX_LAT, 0),
                        data.getDoubleExtra(CityPickerActivity.EX_LNG, 0));
            }
            return;
        }
        if (rq != RQ_AUDIO || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        String name = "Custom sound";
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        prefs().edit().putString("adhan_uri", u.toString()).putString("adhan_name", name)
                .putBoolean("adhan_on", true).apply();
        adhanSw.setChecked(true);
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

    private void testNow() {
        if (!Scheduler.hasLocation(this)) { toast("Set your location first"); return; }
        long now = System.currentTimeMillis();
        long[] t = Scheduler.timesFor(this, Calendar.getInstance());
        int i = 0;
        for (int k = 0; k < 5; k++) if (t[k] <= now) i = k;
        Scheduler.onPrayerTime(this, i, t[i]);
        toast("Reminder fired — check your notifications");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
