package com.salah.reminder;

import android.Manifest;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Everything configurable, grouped and saved the moment you change it. */
public class SettingsActivity extends ThemedActivity {
    private static final int RQ_LOC = 2, RQ_AUDIO = 3, RQ_CITY = 4;
    private static final int[] PRE_CHOICES = {0, 5, 10, 15, 20, 30, 45};
    private static final int[] NAG_CHOICES = {5, 10, 15, 20, 30, 60};

    private interface Pick { void on(int value); }

    private TextView placeView, soundView, updateStatus, updateButton, qiyamHint, dhuhaHint;
    private Switch adhanSwitch, qiyamSwitch, dhuhaSwitch;
    private LinearLayout preRow, nagRow, qiyamRow, qiyamBeforeRow, themeRow;
    private LinearLayout dhuhaRow, dhuhaAfterRow;
    private View qiyamBeforeBox, qiyamOptions2, dhuhaOptions, dhuhaAfterBox;
    private View qiyamOptions;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        head.addView(Ui.iconButton(this, R.drawable.ic_back, "Back", v -> finish()));
        TextView title = Ui.text(this, "Settings", 21, Ui.TEXT, Ui.MEDIUM);
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title);
        root.addView(head, Ui.lp(-1, -2));

        // ---- location ----
        root.addView(Ui.sectionTitle(this, "Where you are"));
        LinearLayout loc = Ui.card(this);
        placeView = Ui.text(this, "", 15, Ui.TEXT, Ui.MEDIUM);
        loc.addView(placeView, Ui.lp(-1, -2));
        loc.addView(Ui.primary(this, "Choose your city",
                v -> startActivityForResult(new Intent(this, CityPickerActivity.class), RQ_CITY)),
                Ui.stacked(this, 14));
        loc.addView(Ui.secondary(this, "Use my current location", v -> askLocation()),
                Ui.stacked(this, 10));
        root.addView(loc, Ui.lp(-1, -2));

        // ---- calculation ----
        root.addView(Ui.sectionTitle(this, "Calculation"));
        LinearLayout calc = Ui.card(this);
        calc.addView(fieldLabel("Method"));
        int method = Math.min(Math.max(0, prefs().getInt("method", 0)), PrayerCalc.METHODS.length - 1);
        calc.addView(wrapField(dropdown(PrayerCalc.METHODS, method, i -> {
            prefs().edit().putInt("method", i).apply();
            reschedule("Method updated");
        })), Ui.lp(-1, -2));
        calc.addView(fieldLabel("Asr"));
        calc.addView(wrapField(dropdown(new String[]{"Standard (Shafi, Maliki, Hanbali)", "Hanafi"},
                prefs().getBoolean("hanafi", false) ? 1 : 0, i -> {
            prefs().edit().putBoolean("hanafi", i == 1).apply();
            reschedule("Asr updated");
        })), Ui.lp(-1, -2));
        root.addView(calc, Ui.lp(-1, -2));

        // ---- adhan ----
        root.addView(Ui.sectionTitle(this, "Adhan sound"));
        LinearLayout ad = Ui.card(this);
        adhanSwitch = switchRow("Play the adhan at prayer time", prefs().getBoolean("adhan_on", true),
                on -> prefs().edit().putBoolean("adhan_on", on).apply());
        ad.addView(adhanSwitch, Ui.lp(-1, -2));
        soundView = Ui.text(this, "", 12.5f, Ui.MUTED, null);
        soundView.setPadding(0, dp(8), 0, 0);
        ad.addView(soundView, Ui.lp(-1, -2));

        LinearLayout pickRow = Ui.rowOf(this);
        pickRow.addView(Ui.secondary(this, "Choose a file", v -> pickAudio()), Ui.lp(0, -2, 1));
        pickRow.addView(spacer(), Ui.lp(dp(8), 1));
        pickRow.addView(Ui.secondary(this, "Use alarm tone", v -> {
            prefs().edit().remove("adhan_uri").remove("adhan_name").apply();
            refresh();
        }), Ui.lp(0, -2, 1));
        ad.addView(pickRow, Ui.stacked(this, 12));

        LinearLayout testRow = Ui.rowOf(this);
        testRow.addView(Ui.secondary(this, "Test",
                v -> AdhanService.start(this, prefs().getInt("next_i", 1))), Ui.lp(0, -2, 1));
        testRow.addView(spacer(), Ui.lp(dp(8), 1));
        testRow.addView(Ui.secondary(this, "Stop", v -> AdhanService.stop(this)), Ui.lp(0, -2, 1));
        ad.addView(testRow, Ui.stacked(this, 8));
        root.addView(ad, Ui.lp(-1, -2));

        // ---- reminders ----
        root.addView(Ui.sectionTitle(this, "Reminders"));
        LinearLayout rem = Ui.card(this);
        rem.addView(fieldLabel("Heads-up before the adhan"));
        preRow = Ui.rowOf(this);
        rem.addView(scrollRow(preRow), Ui.lp(-1, -2));
        rem.addView(Ui.divider(this));
        rem.addView(fieldLabel("Repeat until I tap Prayed"));
        nagRow = Ui.rowOf(this);
        rem.addView(scrollRow(nagRow), Ui.lp(-1, -2));
        root.addView(rem, Ui.lp(-1, -2));

        // ---- dhuha ----
        root.addView(Ui.sectionTitle(this, "Dhuha"));
        LinearLayout dhuha = Ui.card(this);
        dhuhaSwitch = switchRow("Remind me for the forenoon prayer",
                prefs().getInt("dhuha", 0) > 0, this::setDhuhaEnabled);
        dhuha.addView(dhuhaSwitch, Ui.lp(-1, -2));

        LinearLayout dhuhaOpts = Ui.column(this);
        dhuhaOpts.addView(fieldLabel("Remind me"));
        dhuhaRow = Ui.rowOf(this);
        dhuhaOpts.addView(scrollRow(dhuhaRow), Ui.lp(-1, -2));

        LinearLayout afterBox = Ui.column(this);
        afterBox.addView(fieldLabel("How long after sunrise"));
        dhuhaAfterRow = Ui.rowOf(this);
        afterBox.addView(scrollRow(dhuhaAfterRow), Ui.lp(-1, -2));
        dhuhaAfterBox = afterBox;
        dhuhaOpts.addView(afterBox, Ui.lp(-1, -2));

        dhuhaHint = Ui.text(this, "", 12.5f, Ui.ACCENT, null);
        dhuhaHint.setPadding(0, dp(10), 0, 0);
        dhuhaOpts.addView(dhuhaHint, Ui.lp(-1, -2));
        dhuhaOptions = dhuhaOpts;
        dhuha.addView(dhuhaOpts, Ui.lp(-1, -2));
        root.addView(dhuha, Ui.lp(-1, -2));

        // ---- qiyam al-layl ----
        root.addView(Ui.sectionTitle(this, "Qiyam al-Layl"));
        LinearLayout qiyam = Ui.card(this);
        qiyamSwitch = switchRow("Remind me for the night prayer",
                prefs().getInt("qiyam", 0) > 0, this::setQiyamEnabled);
        qiyam.addView(qiyamSwitch, Ui.lp(-1, -2));

        LinearLayout options = Ui.column(this);
        options.addView(fieldLabel("Wake me at"));
        qiyamRow = Ui.rowOf(this);
        options.addView(scrollRow(qiyamRow), Ui.lp(-1, -2));
        LinearLayout beforeBox = Ui.column(this);
        beforeBox.addView(fieldLabel("How long before Fajr"));
        qiyamBeforeRow = Ui.rowOf(this);
        beforeBox.addView(scrollRow(qiyamBeforeRow), Ui.lp(-1, -2));
        qiyamBeforeBox = beforeBox;
        options.addView(beforeBox, Ui.lp(-1, -2));

        qiyamHint = Ui.text(this, "", 12.5f, Ui.ACCENT, null);
        qiyamHint.setPadding(0, dp(10), 0, 0);
        options.addView(qiyamHint, Ui.lp(-1, -2));
        qiyamOptions = options;
        qiyam.addView(options, Ui.lp(-1, -2));
        root.addView(qiyam, Ui.lp(-1, -2));

        // ---- appearance ----
        root.addView(Ui.sectionTitle(this, "Appearance"));
        LinearLayout look = Ui.card(this);
        look.addView(fieldLabel("Theme"));
        themeRow = Ui.rowOf(this);
        look.addView(scrollRow(themeRow), Ui.lp(-1, -2));
        root.addView(look, Ui.lp(-1, -2));

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
                    toast("Open Android settings, then Apps, then Alarms & reminders");
                }
            }), Ui.stacked(this, 10));
        }
        rel.addView(Ui.secondary(this, "Notification settings", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (Exception e) {
                toast("Open Android settings, then Apps, then Notifications");
            }
        }), Ui.stacked(this, 10));
        rel.addView(Ui.primary(this, "Test a full reminder now", v -> testNow()), Ui.stacked(this, 14));
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
        app.addView(Ui.secondary(this, "Check for updates now", v -> checkUpdate()), Ui.stacked(this, 12));
        updateButton = Ui.primary(this, "Download and install", v -> Updater.install(this));
        updateButton.setVisibility(View.GONE);
        app.addView(updateButton, Ui.stacked(this, 10));
        app.addView(Ui.secondary(this, "Open the releases page",
                v -> Updater.openReleasesPage(this)), Ui.stacked(this, 10));
        root.addView(app, Ui.lp(-1, -2));

        TextView about = Ui.text(this,
                "Times are computed on your phone from the sun's position, so no internet is "
                        + "needed once your city is set.", 11.5f, Ui.MUTED, null);
        about.setGravity(Gravity.CENTER);
        about.setPadding(dp(8), dp(22), dp(8), 0);
        root.addView(about, Ui.lp(-1, -2));

        refresh();
    }

    // ---------------- small builders ----------------

    private View spacer() { return new View(this); }

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

    private interface Toggled { void on(boolean checked); }

    private Switch switchRow(String label, boolean checked, Toggled listener) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(15);
        s.setTextColor(Ui.TEXT);
        s.setChecked(checked);                       // set before listening, so it stays quiet
        s.setOnCheckedChangeListener((v, on) -> listener.on(on));
        return s;
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

    private void chips(LinearLayout row, String[] labels, int[] values, int current, Pick onPick) {
        row.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            final int value = values == null ? i : values[i];
            TextView chip = Ui.chip(this, labels[i], value == current, v -> onPick.on(value));
            LinearLayout.LayoutParams p = Ui.lp(-2, -2);
            p.rightMargin = dp(8);
            row.addView(chip, p);
        }
    }

    private void addChip(LinearLayout row, String label, boolean on, Runnable action) {
        TextView chip = Ui.chip(this, label, on, v -> action.run());
        LinearLayout.LayoutParams p = Ui.lp(-2, -2);
        p.rightMargin = dp(8);
        row.addView(chip, p);
    }

    /**
     * Preset durations plus a Custom option. A value that matches no preset gets its own
     * selected chip, so what is set is always on screen.
     */
    private void offsetChips(LinearLayout row, int[] presets, int current, String title, Pick onPick) {
        row.removeAllViews();
        boolean matched = false;
        for (int v : presets) if (v == current) matched = true;
        for (int v : presets) {
            final int value = v;
            addChip(row, Scheduler.humanMinutes(v), v == current, () -> onPick.on(value));
        }
        if (!matched) {
            addChip(row, Scheduler.humanMinutes(current), true, () -> askCustom(title, current, onPick));
        }
        addChip(row, "Custom…", false, () -> askCustom(title, current, onPick));
    }

    private void askCustom(String title, int current, Pick onPick) {
        EditText input = Ui.input(this, "Minutes, e.g. 75");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(current));
        input.setSelection(input.getText().length());

        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(22), dp(10), dp(22), 0);
        box.addView(input);

        new AlertDialog.Builder(this, Ui.dark ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(title)
                .setMessage("In minutes — 90 means 1 hour 30 min.")
                .setView(box)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        onPick.on(Integer.parseInt(input.getText().toString().trim()));
                    } catch (Exception e) {
                        toast("Type a number of minutes");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String[] minuteLabels(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i] == 0 ? "Off" : values[i] + " min";
        }
        return out;
    }

    // ---------------- state ----------------

    private void refresh() {
        SharedPreferences p = prefs();

        placeView.setText(Scheduler.hasLocation(this)
                ? p.getString("place", "Custom location")
                + String.format(Locale.US, "  (%.3f, %.3f)", Scheduler.lat(this), Scheduler.lng(this))
                : "No city set yet");

        String name = p.getString("adhan_name", null);
        soundView.setText("Current sound: " + (name != null ? name : "your phone's alarm tone"));

        chips(preRow, minuteLabels(PRE_CHOICES), PRE_CHOICES, p.getInt("pre", 10), v -> {
            prefs().edit().putInt("pre", v).apply();
            reschedule(v == 0 ? "Heads-up turned off" : "Heads-up set to " + v + " min before");
            refresh();
        });
        chips(nagRow, minuteLabels(NAG_CHOICES), NAG_CHOICES, Scheduler.nagMinutes(this), v -> {
            prefs().edit().putInt("nag", v).apply();
            toast("Repeating every " + v + " min");
            refresh();
        });

        paintDhuha();
        paintQiyam();

        chips(themeRow, Ui.THEME_NAMES, null, Ui.themeChoice(this), v -> {
            prefs().edit().putInt("theme", v).apply();
            recreate();                              // redraw this screen in the new theme at once
        });

        showUpdateButton();
        String ready = Updater.available(this);
        if (ready != null) updateStatus.setText("Version " + ready + " is ready to install");
    }

    private void paintQiyam() {
        int mode = prefs().getInt("qiyam", 0);
        boolean on = mode > 0;
        qiyamOptions.setVisibility(on ? View.VISIBLE : View.GONE);
        if (qiyamSwitch.isChecked() != on) qiyamSwitch.setChecked(on);
        if (!on) return;

        String[] labels = {Scheduler.QIYAM_MODES[1], Scheduler.QIYAM_MODES[2], Scheduler.QIYAM_MODES[3]};
        chips(qiyamRow, labels, new int[]{1, 2, 3}, mode, v -> {
            prefs().edit().putInt("qiyam", v).putInt("qiyam_mode", v).apply();
            Scheduler.scheduleQiyam(this);
            paintQiyam();
        });

        qiyamBeforeBox.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
        if (mode == 3) {
            offsetChips(qiyamBeforeRow, Scheduler.QIYAM_BEFORE, Scheduler.qiyamBefore(this),
                    "How long before Fajr", v -> {
                prefs().edit().putInt("qiyam_before", v).apply();
                Scheduler.scheduleQiyam(this);
                paintQiyam();
            });
        }

        long next = Scheduler.nextQiyam(this, System.currentTimeMillis());
        qiyamHint.setText(next > 0 ? "Next reminder at " + Scheduler.fmt(next)
                : Scheduler.hasLocation(this) ? "No night window tonight at this latitude"
                : "Set your city to see the time");
    }

    private void paintDhuha() {
        int mode = prefs().getInt("dhuha", 0);
        boolean on = mode > 0;
        dhuhaOptions.setVisibility(on ? View.VISIBLE : View.GONE);
        if (dhuhaSwitch.isChecked() != on) dhuhaSwitch.setChecked(on);
        if (!on) return;

        chips(dhuhaRow, new String[]{Scheduler.DHUHA_MODES[1], Scheduler.DHUHA_MODES[2]},
                new int[]{1, 2}, mode, v -> {
            prefs().edit().putInt("dhuha", v).putInt("dhuha_mode", v).apply();
            Scheduler.scheduleDhuha(this);
            paintDhuha();
        });

        dhuhaAfterBox.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        if (mode == 1) {
            offsetChips(dhuhaAfterRow, Scheduler.DHUHA_AFTER, Scheduler.dhuhaAfter(this),
                    "How long after sunrise", v -> {
                prefs().edit().putInt("dhuha_after", v).apply();
                Scheduler.scheduleDhuha(this);
                paintDhuha();
            });
        }

        long next = Scheduler.nextDhuha(this, System.currentTimeMillis());
        dhuhaHint.setText(next > 0 ? "Next reminder at " + Scheduler.fmt(next)
                : Scheduler.hasLocation(this) ? "No sunrise today at this latitude"
                : "Set your city to see the time");
    }

    private void setDhuhaEnabled(boolean on) {
        int mode = Math.max(1, Math.min(2, prefs().getInt("dhuha_mode", 1)));
        prefs().edit().putInt("dhuha", on ? mode : 0).apply();
        Scheduler.scheduleDhuha(this);
        paintDhuha();
    }

    private void setQiyamEnabled(boolean on) {
        int mode = Math.max(1, Math.min(3, prefs().getInt("qiyam_mode", 1)));
        prefs().edit().putInt("qiyam", on ? mode : 0).apply();
        Scheduler.scheduleQiyam(this);
        paintQiyam();
    }

    private void reschedule(String msg) {
        Scheduler.scheduleNext(this, System.currentTimeMillis());
        Scheduler.scheduleQiyam(this);
        Scheduler.scheduleDhuha(this);
        toast(msg);
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
        updateButton.setVisibility(Updater.available(this) == null ? View.GONE : View.VISIBLE);
    }

    // ---------------- location ----------------

    private void setPlace(String name, double lat, double lng) {
        prefs().edit()
                .putString("lat", String.format(Locale.US, "%.4f", lat))
                .putString("lng", String.format(Locale.US, "%.4f", lng))
                .putString("place", name == null ? "Selected location" : name).apply();
        Scheduler.scheduleNext(this, System.currentTimeMillis());
        Scheduler.scheduleQiyam(this);
        Scheduler.scheduleDhuha(this);
        refresh();
        toast("Location set: " + (name == null ? "selected location" : name));
    }

    private static String placeName(Address a) {
        String city = a.getLocality() != null ? a.getLocality()
                : a.getSubAdminArea() != null ? a.getSubAdminArea() : a.getFeatureName();
        String country = a.getCountryName();
        if (city == null) return country != null ? country : "Selected location";
        return country != null ? city + ", " + country : city;
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
            else toast("No location permission — pick your city from the list instead");
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
        adhanSwitch.setChecked(true);
        refresh();
        toast("Adhan sound set");
    }

    // ---------------- misc ----------------

    private void askBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast("Already allowed");
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
        if (!Scheduler.hasLocation(this)) { toast("Choose your city first"); return; }
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
