package com.salah.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/** All scheduling, state and notification logic. */
public class Scheduler {
    static final String PREFS = "salah";
    static final String A_PRAYER = "com.salah.reminder.PRAYER";
    static final String A_PRE = "com.salah.reminder.PRE";
    static final String A_NAG = "com.salah.reminder.NAG";
    static final String A_PRAYED = "com.salah.reminder.PRAYED";
    static final String A_REPIN = "com.salah.reminder.REPIN";
    static final String A_QIYAM = "com.salah.reminder.QIYAM";
    static final String A_DHUHA = "com.salah.reminder.DHUHA";

    static final String CH_ALERT = "prayer_alert_v1";   // nag reminders (sound)
    static final String CH_PIN = "prayer_pinned_v1";    // pinned card (silent)
    static final String CH_PRE = "prayer_pre_v1";       // "Asr in 10 min" heads-up
    static final String CH_ADHAN = "adhan_v1";          // adhan player card
    static final String CH_QIYAM = "qiyam_v1";          // night prayer reminder
    static final String CH_DHUHA = "dhuha_v1";          // forenoon prayer reminder
    static final String CH_UPDATE = "update_v1";        // "a new version is out" (silent)

    static final int ACCENT = 0xFF34D8A5;

    static final int RC_PRAYER = 100, RC_PRE = 101, RC_NAG = 200, RC_QIYAM = 102, RC_DHUHA = 103;
    static final int NID_PRE = 900, NID_QIYAM = 950, NID_DHUHA = 960, NID_DUA = 970;
    static final int NID_UPDATE = 980;
    static final int NID_ADHAN = 2000;

    /** Said after a prayer: "may Allah accept it from us and from you". */
    static final String DUA_AR = "تَقبَّلَ اللهُ "
            + "مِنّا وَمِنكم";
    static final String DUA_EN = "Taqabbal Allahu minna wa minkum";
    static final String DUA_MEANING = "May Allah accept it from us and from you.";

    static final String[] QIYAM_MODES = {"Off", "Last third of the night", "Middle of the night",
            "Before Fajr"};

    /** How long before Fajr the "Before Fajr" mode can be set to, in minutes. */
    static final int[] QIYAM_BEFORE = {30, 60, 90, 120, 180, 240};

    /** Dhuha (forenoon) reminder modes, in the order the settings screen shows them. */
    static final String[] DHUHA_MODES = {"Off", "After sunrise", "Mid-morning"};

    /** How long after sunrise the "After sunrise" mode can be set to, in minutes. */
    static final int[] DHUHA_AFTER = {15, 20, 30, 45, 60, 90};

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasLocation(Context c) { return prefs(c).contains("lat"); }

    static double lat(Context c) { return Double.parseDouble(prefs(c).getString("lat", "0")); }
    static double lng(Context c) { return Double.parseDouble(prefs(c).getString("lng", "0")); }

    static long[] timesFor(Context c, Calendar day) {
        SharedPreferences p = prefs(c);
        return PrayerCalc.times(day, lat(c), lng(c), p.getInt("method", 0), p.getBoolean("hanafi", false));
    }

    /** {index, time} of the first prayer after {@code now}, rolling into tomorrow. */
    static long[] next(Context c, long now) {
        if (!hasLocation(c)) return null;
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        for (int k = 0; k < 2; k++) {
            long[] t = timesFor(c, day);
            for (int i = 0; i < 5; i++) if (t[i] > now) return new long[]{i, t[i]};
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    /** The most recent prayer at or before {@code now}, reaching back to yesterday's Isha. */
    static long prevTime(Context c, long now) {
        if (!hasLocation(c)) return now;
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        for (int k = 0; k < 2; k++) {
            long[] t = timesFor(c, day);
            for (int i = 4; i >= 0; i--) if (t[i] <= now) return t[i];
            day.add(Calendar.DAY_OF_MONTH, -1);
        }
        return now;
    }

    /**
     * When the chosen part of the night begins, for the night that starts on {@code day}.
     * The night runs from that day's Maghrib to the next day's Fajr. Returns 0 if it can't
     * be worked out (polar summer, say).
     */
    static long qiyamTime(Context c, Calendar day, int mode) {
        if (mode <= 0 || !hasLocation(c)) return 0;
        Calendar next = (Calendar) day.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        long maghrib = timesFor(c, day)[3];
        long fajr = timesFor(c, next)[0];
        long night = fajr - maghrib;
        if (night <= 0) return 0;
        switch (mode) {
            case 1: return fajr - night / 3;         // last third
            case 2: return maghrib + night / 2;      // midpoint
            case 3: return fajr - qiyamBefore(c) * 60000L;
            default: return 0;
        }
    }

    static int qiyamBefore(Context c) {
        int m = prefs(c).getInt("qiyam_before", 60);
        return Math.max(5, Math.min(600, m));
    }

    /** "Last third of the night", or "1 hour 30 min before Fajr" for the custom mode. */
    static String qiyamLabel(Context c) {
        int mode = prefs(c).getInt("qiyam", 0);
        if (mode == 3) return humanMinutes(qiyamBefore(c)) + " before Fajr";
        return QIYAM_MODES[Math.max(0, Math.min(mode, QIYAM_MODES.length - 1))];
    }

    static String humanMinutes(int m) {
        if (m < 60) return m + " min";
        int h = m / 60, rest = m % 60;
        String hours = h + (h == 1 ? " hour" : " hours");
        return rest == 0 ? hours : hours + " " + rest + " min";
    }

    /** The next Qiyam time after {@code now}, or 0 when the reminder is off. */
    static long nextQiyam(Context c, long now) {
        int mode = prefs(c).getInt("qiyam", 0);
        if (mode <= 0 || !hasLocation(c)) return 0;
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        day.add(Calendar.DAY_OF_MONTH, -1);          // last night's window may still be running
        for (int k = 0; k < 3; k++) {
            long t = qiyamTime(c, day, mode);
            if (t > now + 1000) return t;
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    // ---------- dhuha (forenoon prayer) ----------

    static int dhuhaAfter(Context c) {
        int m = prefs(c).getInt("dhuha_after", 20);
        return Math.max(5, Math.min(240, m));
    }

    static String dhuhaLabel(Context c) {
        int mode = prefs(c).getInt("dhuha", 0);
        if (mode == 1) return humanMinutes(dhuhaAfter(c)) + " after sunrise";
        return DHUHA_MODES[Math.max(0, Math.min(mode, DHUHA_MODES.length - 1))];
    }

    /**
     * When Dhuha begins on {@code day}: a chosen gap after sunrise, or halfway between sunrise
     * and Dhuhr. Returns 0 when the sun doesn't rise that day.
     */
    static long dhuhaTime(Context c, Calendar day, int mode) {
        if (mode <= 0 || !hasLocation(c)) return 0;
        long sunrise = PrayerCalc.sunrise(day, lat(c), lng(c));
        if (sunrise == 0) return 0;
        long dhuhr = timesFor(c, day)[1];
        switch (mode) {
            case 1: return sunrise + dhuhaAfter(c) * 60000L;
            case 2: return dhuhr > sunrise ? sunrise + (dhuhr - sunrise) / 2 : 0;
            default: return 0;
        }
    }

    /** The next Dhuha time after {@code now}, or 0 when the reminder is off. */
    static long nextDhuha(Context c, long now) {
        int mode = prefs(c).getInt("dhuha", 0);
        if (mode <= 0 || !hasLocation(c)) return 0;
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        for (int k = 0; k < 3; k++) {
            long t = dhuhaTime(c, day, mode);
            if (t > now + 1000) return t;
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    static void scheduleDhuha(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(bcast(c, RC_DHUHA, A_DHUHA, 0, 0));
        long t = nextDhuha(c, System.currentTimeMillis());
        if (t > 0) setExact(c, t, bcast(c, RC_DHUHA, A_DHUHA, 0, t));
    }

    static void onDhuha(Context c, long t) {
        ensureChannels(c);
        if (prefs(c).getInt("dhuha", 0) > 0) {
            long[] nx = next(c, System.currentTimeMillis());
            String until = nx != null && nx[0] == 1
                    ? "Pray it before Dhuhr at " + fmt(nx[1]) : "Pray it before Dhuhr";
            Notification n = new Notification.Builder(c, CH_DHUHA)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Dhuha")
                    .setContentText(dhuhaLabel(c) + " \u00b7 " + until)
                    .setStyle(new Notification.BigTextStyle().bigText(
                            "The time for Dhuha has come in \u2014 " + dhuhaLabel(c).toLowerCase()
                                    + ". " + until + "."))
                    .setColor(ACCENT)
                    .setContentIntent(openApp(c, 810))
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build();
            c.getSystemService(NotificationManager.class).notify(NID_DHUHA, n);
        }
        scheduleDhuha(c);
    }

    static void scheduleQiyam(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(bcast(c, RC_QIYAM, A_QIYAM, 0, 0));
        long t = nextQiyam(c, System.currentTimeMillis());
        if (t > 0) setExact(c, t, bcast(c, RC_QIYAM, A_QIYAM, 0, t));
    }

    static void onQiyam(Context c, long t) {
        ensureChannels(c);
        int mode = prefs(c).getInt("qiyam", 0);
        if (mode > 0) {
            long fajr = 0;
            long[] nx = next(c, System.currentTimeMillis());
            if (nx != null && nx[0] == 0) fajr = nx[1];
            String when = fajr > 0 ? "Fajr is at " + fmt(fajr) : "Pray before Fajr";
            Notification n = new Notification.Builder(c, CH_QIYAM)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Qiyam al-Layl")
                    .setContentText(qiyamLabel(c) + " \u00b7 " + when)
                    .setStyle(new Notification.BigTextStyle().bigText(
                            "Time to stand for the night prayer \u2014 " + qiyamLabel(c).toLowerCase()
                                    + ". " + when + "."))
                    .setColor(ACCENT)
                    .setContentIntent(openApp(c, 800))
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build();
            c.getSystemService(NotificationManager.class).notify(NID_QIYAM, n);
        }
        scheduleQiyam(c);
    }

    static String fmt(long t) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(t));
    }

    static int dayKey(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        return k.get(Calendar.YEAR) * 10000 + (k.get(Calendar.MONTH) + 1) * 100 + k.get(Calendar.DAY_OF_MONTH);
    }

    // ---------- alarms ----------

    static PendingIntent bcast(Context c, int code, String action, int i, long t) {
        Intent it = new Intent(c, AlarmReceiver.class).setAction(action).putExtra("i", i).putExtra("t", t);
        return PendingIntent.getBroadcast(c, code, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void setExact(Context c, long at, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    /** Schedules the next prayer alarm (and its heads-up) after {@code after}. */
    static void scheduleNext(Context c, long after) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(bcast(c, RC_PRE, A_PRE, 0, 0));
        if (!hasLocation(c)) return;
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(after);
        for (int k = 0; k < 2; k++) {
            long[] t = timesFor(c, day);
            for (int i = 0; i < 5; i++) {
                if (t[i] > after + 1000) {
                    setExact(c, t[i], bcast(c, RC_PRAYER, A_PRAYER, i, t[i]));
                    int pre = prefs(c).getInt("pre", 10);
                    long preAt = t[i] - pre * 60000L;
                    if (pre > 0 && preAt > System.currentTimeMillis()) {
                        setExact(c, preAt, bcast(c, RC_PRE, A_PRE, i, t[i]));
                    }
                    prefs(c).edit().putLong("next_t", t[i]).putInt("next_i", i).apply();
                    return;
                }
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    static void rescheduleAll(Context c) {
        scheduleNext(c, System.currentTimeMillis());
        scheduleQiyam(c);
        scheduleDhuha(c);
        int nagI = prefs(c).getInt("nag_i", -1);
        for (int i = 0; i < 5; i++) if (pending(c, i) != 0) post(c, i, false);
        if (nagI >= 0 && pending(c, nagI) != 0) scheduleNag(c, nagI);
    }

    // ---------- events ----------

    static long pending(Context c, int i) { return prefs(c).getLong("pending_" + i, 0); }

    /** The "you prayed this" flag for prayer {@code i} on the day of {@code t}. */
    static String doneKey(long t, int i) { return "done_" + dayKey(t) + "_" + i; }

    static boolean isDone(Context c, long t, int i) {
        return prefs(c).getBoolean(doneKey(t, i), false);
    }

    /**
     * "I prayed this", from inside the app. Works whether a card is still waiting for this
     * prayer or it passed unconfirmed, so a missed notification is never a dead end.
     */
    static void markPrayed(Context c, int i, long t) {
        if (pending(c, i) != 0) {
            onPrayed(c, i);
            return;
        }
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putBoolean(doneKey(t, i), true);
        pruneDone(p, e);
        e.apply();
    }

    /** Takes a tick back, for a mis-tap. */
    static void unmarkPrayed(Context c, int i, long t) {
        prefs(c).edit().remove(doneKey(t, i)).apply();
    }

    /** How many days of ticks to keep. The home screen only ever shows today's. */
    private static final int DONE_KEEP_DAYS = 7;

    /** Drops ticks older than a week, so the prefs file doesn't grow for the app's lifetime. */
    private static void pruneDone(SharedPreferences p, SharedPreferences.Editor e) {
        Calendar cut = Calendar.getInstance();
        cut.add(Calendar.DAY_OF_MONTH, -DONE_KEEP_DAYS);
        int oldest = dayKey(cut.getTimeInMillis());
        for (String key : p.getAll().keySet()) {
            if (!key.startsWith("done_")) continue;
            int sep = key.indexOf('_', 5);
            if (sep < 0) continue;
            try {
                if (Integer.parseInt(key.substring(5, sep)) < oldest) e.remove(key);
            } catch (NumberFormatException ignored) { }
        }
    }

    static void onPrayerTime(Context c, int i, long t) {
        prefs(c).edit().putLong("pending_" + i, t).putInt("nag_i", i).apply();
        c.getSystemService(NotificationManager.class).cancel(NID_PRE);
        boolean adhan = prefs(c).getBoolean("adhan_on", true);
        post(c, i, !adhan);                 // adhan plays instead of a notification beep
        if (adhan) AdhanService.start(c, i);
        scheduleNag(c, i);
        scheduleNext(c, t + 60000);
    }

    static void onPre(Context c, int i, long t) {
        ensureChannels(c);
        int mins = Math.max(1, Math.round((t - System.currentTimeMillis()) / 60000f));
        Notification n = new Notification.Builder(c, CH_PRE)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(PrayerCalc.NAMES[i] + " in " + mins + " min")
                .setContentText("Adhan at " + fmt(t) + " — time to get ready")
                .setColor(ACCENT)
                .setContentIntent(openApp(c, 600))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        c.getSystemService(NotificationManager.class).notify(NID_PRE, n);
    }

    static int nagMinutes(Context c) { return Math.max(1, prefs(c).getInt("nag", 10)); }

    static void scheduleNag(Context c, int i) {
        setExact(c, System.currentTimeMillis() + nagMinutes(c) * 60000L, bcast(c, RC_NAG, A_NAG, i, 0));
    }

    static void onNag(Context c, int i) {
        if (pending(c, i) != 0 && prefs(c).getInt("nag_i", -1) == i) {
            post(c, i, true);
            scheduleNag(c, i);
        }
    }

    static void onPrayed(Context c, int i) {
        long t = pending(c, i);
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().remove("pending_" + i);
        if (t != 0) e.putBoolean(doneKey(t, i), true);
        pruneDone(p, e);
        if (prefs(c).getInt("nag_i", -1) == i) {
            c.getSystemService(AlarmManager.class).cancel(bcast(c, RC_NAG, A_NAG, i, 0));
            e.remove("nag_i");
        }
        e.apply();
        c.getSystemService(NotificationManager.class).cancel(1000 + i);
        AdhanService.stop(c);
        sayDua(c);
    }

    /** A quiet, self-clearing card with the dua, for when "Prayed" is tapped from the shade. */
    static void sayDua(Context c) {
        ensureChannels(c);
        Notification n = new Notification.Builder(c, CH_PIN)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(DUA_AR)
                .setContentText(DUA_EN)
                .setStyle(new Notification.BigTextStyle()
                        .setBigContentTitle(DUA_AR)
                        .bigText(DUA_EN + "\n" + DUA_MEANING))
                .setColor(ACCENT)
                .setContentIntent(openApp(c, 820))
                .setAutoCancel(true)
                .setTimeoutAfter(180000)             // clears itself after three minutes
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        c.getSystemService(NotificationManager.class).notify(NID_DUA, n);
    }

    static void onRepin(Context c, int i) {
        if (pending(c, i) != 0) post(c, i, false);
    }

    // ---------- notifications ----------

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel alert = new NotificationChannel(CH_ALERT, "Prayer reminders (repeat)",
                NotificationManager.IMPORTANCE_HIGH);
        alert.enableVibration(true);
        alert.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alert);

        NotificationChannel pin = new NotificationChannel(CH_PIN, "Pinned prayer card",
                NotificationManager.IMPORTANCE_DEFAULT);
        pin.setSound(null, null);
        pin.enableVibration(false);
        pin.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(pin);

        NotificationChannel pre = new NotificationChannel(CH_PRE, "Before adhan heads-up",
                NotificationManager.IMPORTANCE_HIGH);
        pre.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(pre);

        NotificationChannel ad = new NotificationChannel(CH_ADHAN, "Adhan playing",
                NotificationManager.IMPORTANCE_LOW);
        ad.setSound(null, null);
        nm.createNotificationChannel(ad);

        NotificationChannel qiyam = new NotificationChannel(CH_QIYAM, "Qiyam al-Layl",
                NotificationManager.IMPORTANCE_HIGH);
        qiyam.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(qiyam);

        NotificationChannel dhuha = new NotificationChannel(CH_DHUHA, "Dhuha",
                NotificationManager.IMPORTANCE_DEFAULT);
        dhuha.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(dhuha);

        // An update is worth knowing about, never worth interrupting a prayer for: no sound.
        NotificationChannel update = new NotificationChannel(CH_UPDATE, "App updates",
                NotificationManager.IMPORTANCE_LOW);
        update.setSound(null, null);
        update.enableVibration(false);
        nm.createNotificationChannel(update);
    }

    static PendingIntent openApp(Context c, int code) {
        Intent it = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, code, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Pinned card that can't be cleared until "Prayed" is tapped. */
    static void post(Context c, int i, boolean alert) {
        ensureChannels(c);
        long t = pending(c, i);
        if (t == 0) return;
        PendingIntent done = bcast(c, 300 + i, A_PRAYED, i, t);
        PendingIntent repin = bcast(c, 400 + i, A_REPIN, i, t);
        Notification.Action act = new Notification.Action.Builder(
                Icon.createWithResource(c, R.drawable.ic_notif), "✓ Prayed", done).build();
        Notification n = new Notification.Builder(c, alert ? CH_ALERT : CH_PIN)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(PrayerCalc.NAMES[i] + " · " + fmt(t))
                .setContentText("Tap ✓ Prayed when you have prayed")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "It is time for " + PrayerCalc.NAMES[i] + ". This reminder stays here, and "
                                + "returns every " + nagMinutes(c) + " min, until you tap ✓ Prayed."))
                .setColor(ACCENT)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openApp(c, 500 + i))
                .setDeleteIntent(repin)       // if swiped away anyway (Android 14+), it comes right back
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(t)
                .addAction(act)
                .build();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        c.getSystemService(NotificationManager.class).notify(1000 + i, n);
    }
}
