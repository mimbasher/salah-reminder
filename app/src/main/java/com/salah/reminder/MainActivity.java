package com.salah.reminder;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Home: today's prayer times, a live countdown to the next one, and the Qibla shortcut. */
public class MainActivity extends Activity {
    private static final int RQ_NOTIF = 1;
    static final String[] GLYPHS = {"🌄", "🌞", "⛅", "🌆", "🌙"};

    private TextView dateView, placeView, heroName, heroTime, heroLabel;
    private RingView ring;
    private LinearLayout listCard, warnCard;
    private TextView warnText, footer;
    private View heroAction;

    /** What the screen currently shows; while it is unchanged only the countdown is redrawn. */
    private String shown = "";

    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            paint();
            tick.postDelayed(this, 1000);
        }
    };

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
        shown = "";
        paint();
        tick.removeCallbacks(ticker);
        tick.postDelayed(ticker, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        tick.removeCallbacks(ticker);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    // ---------------- layout ----------------

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        LinearLayout root = Ui.column(this);
        root.setPadding(dp(18), dp(16), dp(18), dp(28));
        sv.addView(root, Ui.lp(-1, -1));
        setContentView(sv);

        // header
        LinearLayout head = Ui.rowOf(this);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, "Salah Reminder", 21, Ui.TEXT, Ui.MEDIUM));
        dateView = Ui.text(this, "", 12.5f, Ui.MUTED, null);
        dateView.setPadding(0, dp(3), 0, 0);
        titles.addView(dateView);
        head.addView(titles, Ui.lp(0, -2, 1));
        head.addView(Ui.iconButton(this, "⚙", v -> open(SettingsActivity.class)));
        root.addView(head, Ui.lp(-1, -2));

        // hero
        LinearLayout hero = Ui.column(this);
        hero.setBackground(Ui.gradient(this, Ui.HERO_A, Ui.HERO_B, 26));
        hero.setPadding(dp(20), dp(20), dp(20), dp(18));
        hero.setElevation(dp(6));
        LinearLayout heroRow = Ui.rowOf(this);
        LinearLayout heroCol = Ui.column(this);
        heroLabel = Ui.overline(this, "Next prayer", Ui.ON_HERO_2);
        heroCol.addView(heroLabel);
        heroName = Ui.text(this, "—", 34, Ui.ON_HERO, Ui.LIGHT);
        heroName.setPadding(0, dp(4), 0, 0);
        heroCol.addView(heroName);
        heroTime = Ui.text(this, "", 16, Ui.ON_HERO_2, Ui.MEDIUM);
        heroCol.addView(heroTime);
        heroRow.addView(heroCol, Ui.lp(0, -2, 1));
        ring = new RingView(this);
        heroRow.addView(ring, Ui.lp(dp(104), dp(104)));
        hero.addView(heroRow, Ui.lp(-1, -2));

        placeView = Ui.text(this, "", 12.5f, Ui.ON_HERO_2, null);
        placeView.setPadding(0, dp(14), 0, 0);
        hero.addView(placeView);

        heroAction = Ui.primary(this, "Set your location", v -> open(SettingsActivity.class));
        hero.addView(heroAction, Ui.stacked(this, 14));
        root.addView(hero, Ui.stacked(this, 18));

        // reliability warning (hidden unless something is actually wrong)
        warnCard = Ui.column(this);
        warnCard.setBackground(Ui.outlined(Ui.alpha(Ui.AMBER, 0x1F), Ui.alpha(Ui.AMBER, 0x66), this, 16));
        warnCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        warnText = Ui.text(this, "", 13, 0xFFFFD79A, null);
        warnCard.addView(warnText);
        warnCard.setVisibility(View.GONE);
        root.addView(warnCard, Ui.stacked(this, 12));

        root.addView(Ui.sectionTitle(this, "Today"));
        listCard = Ui.card(this);
        listCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(listCard, Ui.lp(-1, -2));

        LinearLayout actions = Ui.rowOf(this);
        TextView qibla = Ui.secondary(this, "🧭  Qibla", v -> open(QiblaActivity.class));
        TextView settings = Ui.secondary(this, "⚙  Settings", v -> open(SettingsActivity.class));
        actions.addView(qibla, Ui.lp(0, -2, 1));
        View gap = new View(this);
        actions.addView(gap, Ui.lp(dp(10), 1));
        actions.addView(settings, Ui.lp(0, -2, 1));
        root.addView(actions, Ui.stacked(this, 16));

        footer = Ui.text(this, "", 11.5f, Ui.MUTED, null);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer, Ui.lp(-1, -2));
    }

    private void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }

    // ---------------- state -> screen ----------------

    private void paint() {
        SharedPreferences p = Scheduler.prefs(this);
        long now = System.currentTimeMillis();

        if (!Scheduler.hasLocation(this)) {
            if ("empty".equals(shown)) return;
            shown = "empty";
            paintEmpty();
            return;
        }

        long[] nx = Scheduler.next(this, now);
        long prev = Scheduler.prevTime(this, now);
        int ni = nx == null ? 0 : (int) nx[0];
        long nt = nx == null ? now : nx[1];
        ring.set(span(prev, nt, now), ringLabel(Math.max(0, nt - now)));

        // everything below changes at most once a minute, or when a prayer is marked
        String key = (now / 60000) + "|" + ni + "|" + nt + "|" + stateMask(now);
        if (key.equals(shown)) return;
        shown = key;

        String greg = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(now));
        String hijri = HijriDate.format(now);
        dateView.setText(hijri == null ? greg : greg + "  ·  " + hijri);

        placeView.setVisibility(View.VISIBLE);
        ring.setVisibility(View.VISIBLE);
        heroAction.setVisibility(View.GONE);
        placeView.setText("📍  " + p.getString("place", "Custom location"));

        heroLabel.setText("NEXT PRAYER");
        heroName.setTextSize(34);
        heroName.setText(PrayerCalc.NAMES[ni]);
        heroTime.setText(Scheduler.fmt(nt) + (sameDay(nt, now) ? "" : "  ·  tomorrow"));

        paintList(now, ni, nt);
        paintWarning();

        String[] madhab = {"Standard Asr", "Hanafi Asr"};
        int mi = Math.min(Math.max(0, p.getInt("method", 0)), PrayerCalc.METHODS.length - 1);
        footer.setText(PrayerCalc.METHODS[mi] + "  ·  "
                + madhab[p.getBoolean("hanafi", false) ? 1 : 0]);
    }

    private void paintEmpty() {
        heroLabel.setText("WELCOME");
        heroName.setText("As-salamu alaykum");
        heroName.setTextSize(24);
        heroTime.setText("Tell me where you are and I'll keep your times right.");
        placeView.setVisibility(View.GONE);
        ring.setVisibility(View.GONE);
        heroAction.setVisibility(View.VISIBLE);
        listCard.removeAllViews();
        listCard.addView(hint("Prayer times appear here once your location is set."));
        warnCard.setVisibility(View.GONE);
        footer.setText("");
    }

    /** Changes whenever a prayer is marked prayed or a new card appears. */
    private int stateMask(long now) {
        long[] t = Scheduler.timesFor(this, Calendar.getInstance());
        SharedPreferences p = Scheduler.prefs(this);
        int m = 0;
        for (int i = 0; i < 5; i++) {
            if (Scheduler.pending(this, i) != 0) m |= 1 << i;
            if (p.getBoolean("done_" + Scheduler.dayKey(t[i]) + "_" + i, false)) m |= 1 << (i + 8);
        }
        return m;
    }

    private TextView hint(String s) {
        TextView t = Ui.text(this, s, 13.5f, Ui.MUTED, null);
        t.setPadding(dp(10), dp(14), dp(10), dp(14));
        return t;
    }

    private void paintList(long now, int nextIndex, long nextTime) {
        listCard.removeAllViews();
        SharedPreferences p = Scheduler.prefs(this);
        long[] t = Scheduler.timesFor(this, Calendar.getInstance());
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            boolean pending = Scheduler.pending(this, i) != 0;
            boolean done = p.getBoolean("done_" + Scheduler.dayKey(t[i]) + "_" + i, false);
            boolean isNext = i == nextIndex && sameDay(nextTime, now);

            LinearLayout row = Ui.rowOf(this);
            row.setPadding(dp(10), dp(12), dp(12), dp(12));
            if (pending) row.setBackground(Ui.round(Ui.alpha(Ui.AMBER, 0x1A), this, 14));
            else if (isNext) row.setBackground(Ui.round(Ui.SURFACE_2, this, 14));

            int bubble = pending ? Ui.alpha(Ui.AMBER, 0x33)
                    : done ? Ui.alpha(Ui.ACCENT, 0x2B)
                    : isNext ? Ui.alpha(Ui.ACCENT, 0x24) : 0xFF1A2432;
            row.addView(Ui.badge(this, GLYPHS[i], bubble, Ui.TEXT, 38));

            LinearLayout col = Ui.column(this);
            col.setPadding(dp(12), 0, dp(8), 0);
            col.addView(Ui.text(this, PrayerCalc.NAMES[i], 16,
                    done ? Ui.MUTED : Ui.TEXT, Ui.MEDIUM));
            String sub;
            int subColor = Ui.MUTED;
            if (pending) { sub = "Waiting — mark it when you've prayed"; subColor = Ui.AMBER; }
            else if (done) sub = "Prayed ✓";
            else if (isNext) { sub = "in " + compact(Math.max(0, t[i] - now)); subColor = Ui.ACCENT; }
            else if (t[i] <= now) sub = "Passed";
            else sub = "Upcoming";
            TextView subView = Ui.text(this, sub, 12, subColor, null);
            subView.setPadding(0, dp(2), 0, 0);
            col.addView(subView);
            row.addView(col, Ui.lp(0, -2, 1));

            TextView time = Ui.text(this, Scheduler.fmt(t[i]), 16.5f,
                    done ? Ui.MUTED : isNext ? Ui.ACCENT : Ui.TEXT, Ui.MEDIUM);
            row.addView(time);

            if (pending) {
                TextView mark = Ui.text(this, "✓", 15, 0xFF04231B, Ui.MEDIUM);
                mark.setGravity(Gravity.CENTER);
                int s = dp(34);
                LinearLayout.LayoutParams mp = Ui.lp(s, s);
                mp.leftMargin = dp(10);
                mark.setLayoutParams(mp);
                Ui.clickable(mark, Ui.circle(Ui.ACCENT), v -> {
                    Scheduler.onPrayed(this, idx);
                    shown = "";
                    paint();
                    toast(PrayerCalc.NAMES[idx] + " marked as prayed");
                });
                row.addView(mark);
            } else if (done) {
                TextView ok = Ui.text(this, "✓", 15, Ui.ACCENT, Ui.MEDIUM);
                LinearLayout.LayoutParams op = Ui.lp(dp(34), dp(34));
                op.leftMargin = dp(10);
                ok.setLayoutParams(op);
                ok.setGravity(Gravity.CENTER);
                row.addView(ok);
            } else {
                View pad = new View(this);
                LinearLayout.LayoutParams pp = Ui.lp(dp(34), dp(1));
                pp.leftMargin = dp(10);
                pad.setLayoutParams(pp);
                row.addView(pad);
            }

            LinearLayout.LayoutParams rp = Ui.lp(-1, -2);
            rp.topMargin = i == 0 ? 0 : dp(2);
            listCard.addView(row, rp);
        }
    }

    /** Shows the single most important thing that could stop the adhan firing. */
    private void paintWarning() {
        String msg = null;
        final Runnable fix;
        NotificationManager nm = getSystemService(NotificationManager.class);
        AlarmManager am = getSystemService(AlarmManager.class);
        PowerManager pm = getSystemService(PowerManager.class);

        if (!nm.areNotificationsEnabled()) {
            msg = "Notifications are switched off — tap to turn them on.";
            fix = () -> {
                Intent it = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(it);
            };
        } else if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            msg = "Exact alarms are blocked, so the adhan may be late. Tap to allow.";
            fix = () -> startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                    Uri.parse("package:" + getPackageName())));
        } else if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            msg = "Battery saving may delay reminders. Tap to allow background running.";
            fix = () -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            };
        } else {
            fix = null;
        }

        if (msg == null) {
            warnCard.setVisibility(View.GONE);
            return;
        }
        warnText.setText("⚠  " + msg);
        warnCard.setVisibility(View.VISIBLE);
        Ui.clickable(warnCard,
                Ui.outlined(Ui.alpha(Ui.AMBER, 0x1F), Ui.alpha(Ui.AMBER, 0x66), this, 16),
                v -> { try { fix.run(); } catch (Exception e) { toast("Open it from Android settings"); } });
    }

    // ---------------- small helpers ----------------

    private static boolean sameDay(long a, long b) {
        return Scheduler.dayKey(a) == Scheduler.dayKey(b);
    }

    private static float span(long from, long to, long now) {
        if (to <= from) return 1f;
        return Math.max(0f, Math.min(1f, (now - from) / (float) (to - from)));
    }

    /** Tight form for the dial: "2:06", "14m", "now". */
    private static String ringLabel(long ms) {
        long mins = ms / 60000;
        if (mins >= 60) return String.format(Locale.US, "%d:%02d", mins / 60, mins % 60);
        if (mins >= 1) return mins + "m";
        return "now";
    }

    /** "2h 06m", "14 min", "under a minute". */
    static String compact(long ms) {
        long mins = ms / 60000;
        if (mins >= 60) return String.format(Locale.US, "%dh %02dm", mins / 60, mins % 60);
        if (mins >= 1) return mins + " min";
        return "under a minute";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** Countdown dial: an arc that fills as the next prayer approaches. */
    static class RingView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float progress;
        private String label = "";

        RingView(Context c) { super(c); }

        void set(float progress, String label) {
            this.progress = progress;
            this.label = label;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float stroke = w * 0.085f;
            box.set(stroke / 2 + 1, stroke / 2 + 1, w - stroke / 2 - 1, h - stroke / 2 - 1);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x33FFFFFF);
            c.drawArc(box, 0, 360, false, p);

            p.setColor(Ui.ACCENT);
            c.drawArc(box, -90, Math.max(2f, progress * 360f), false, p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Ui.MEDIUM);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(w * 0.165f);
            c.drawText(label, w / 2, h / 2 + w * 0.045f, p);
            p.setTypeface(Ui.MEDIUM);
            p.setColor(0x99FFFFFF);
            p.setTextSize(w * 0.085f);
            p.setLetterSpacing(0.14f);
            c.drawText("LEFT", w / 2, h / 2 + w * 0.195f, p);
            p.setLetterSpacing(0f);
        }
    }
}
