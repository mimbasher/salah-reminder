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

    static final String CH_ALERT = "prayer_alert_v1";   // nag reminders (sound)
    static final String CH_PIN = "prayer_pinned_v1";    // pinned card (silent)
    static final String CH_PRE = "prayer_pre_v1";       // "Asr in 10 min" heads-up
    static final String CH_ADHAN = "adhan_v1";          // adhan player card

    static final int RC_PRAYER = 100, RC_PRE = 101, RC_NAG = 200;
    static final int NID_PRE = 900, NID_ADHAN = 2000;

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
        int nagI = prefs(c).getInt("nag_i", -1);
        for (int i = 0; i < 5; i++) if (pending(c, i) != 0) post(c, i, false);
        if (nagI >= 0 && pending(c, nagI) != 0) scheduleNag(c, nagI);
    }

    // ---------- events ----------

    static long pending(Context c, int i) { return prefs(c).getLong("pending_" + i, 0); }

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
        SharedPreferences.Editor e = prefs(c).edit().remove("pending_" + i);
        if (t != 0) e.putBoolean("done_" + dayKey(t) + "_" + i, true);
        if (prefs(c).getInt("nag_i", -1) == i) {
            c.getSystemService(AlarmManager.class).cancel(bcast(c, RC_NAG, A_NAG, i, 0));
            e.remove("nag_i");
        }
        e.apply();
        c.getSystemService(NotificationManager.class).cancel(1000 + i);
        AdhanService.stop(c);
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
                .setContentTitle("Time for " + PrayerCalc.NAMES[i])
                .setContentText("Adhan was at " + fmt(t) + " · tap ✓ Prayed when done")
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
