package com.salah.reminder;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Plays the adhan (your chosen audio file, or the phone's alarm tone). */
public class AdhanService extends Service {
    static final String A_STOP = "com.salah.reminder.STOP_ADHAN";
    private MediaPlayer mp;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context c, int i) {
        try {
            c.startForegroundService(new Intent(c, AdhanService.class).putExtra("i", i));
        } catch (Exception ignored) { }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, AdhanService.class));
    }

    @Override
    public int onStartCommand(Intent it, int flags, int startId) {
        if (it != null && A_STOP.equals(it.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int i = it == null ? 0 : it.getIntExtra("i", 0);
        Scheduler.ensureChannels(this);
        PendingIntent stopPi = PendingIntent.getService(this, 700,
                new Intent(this, AdhanService.class).setAction(A_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, Scheduler.CH_ADHAN)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Adhan — " + PrayerCalc.NAMES[i])
                .setContentText("Playing adhan")
                .setColor(Scheduler.ACCENT)
                .setContentIntent(Scheduler.openApp(this, 701))
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_notif), "Stop adhan", stopPi).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Scheduler.NID_ADHAN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(Scheduler.NID_ADHAN, n);
        }
        play();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf, 8 * 60 * 1000L); // safety cap
        return START_NOT_STICKY;
    }

    private void play() {
        release();
        mp = new MediaPlayer();
        String saved = Scheduler.prefs(this).getString("adhan_uri", null);

        // In order: the file you chose, the adhan that ships with the app, then whatever the
        // phone uses for alarms. A chosen file can disappear, or its permission can be lost
        // when it is moved, so there has to be something behind it.
        Uri[] candidates = {
                saved == null ? null : Uri.parse(saved),
                bundledAdhan(),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        };
        boolean ready = false;
        for (Uri u : candidates) {
            if (u == null) continue;
            try {
                mp.reset();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                mp.setDataSource(this, u);
                ready = true;
                break;
            } catch (Exception ignored) { }
        }
        if (!ready) {
            stopSelf();
            return;
        }
        mp.setOnCompletionListener(p -> stopSelf());
        mp.setOnErrorListener((p, w, x) -> { stopSelf(); return true; });
        mp.setOnPreparedListener(MediaPlayer::start);
        mp.prepareAsync();
    }

    /** The adhan bundled with the app, used until you choose a file of your own. */
    private Uri bundledAdhan() {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(getPackageName())
                .appendPath(String.valueOf(R.raw.adhan))
                .build();
    }

    private void release() {
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { }
            mp.release();
            mp = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
