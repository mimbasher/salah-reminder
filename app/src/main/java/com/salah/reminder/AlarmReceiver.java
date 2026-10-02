package com.salah.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent it) {
        String a = it.getAction();
        if (a == null) return;
        int i = it.getIntExtra("i", 0);
        long t = it.getLongExtra("t", 0);
        switch (a) {
            case Scheduler.A_PRAYER:
                Scheduler.onPrayerTime(c, i, t);
                lookForUpdate(c);
                break;
            case Scheduler.A_PRE:    Scheduler.onPre(c, i, t); break;
            case Scheduler.A_NAG:    Scheduler.onNag(c, i); break;
            case Scheduler.A_PRAYED: Scheduler.onPrayed(c, i); break;
            case Scheduler.A_REPIN:  Scheduler.onRepin(c, i); break;
            case Scheduler.A_QIYAM:  Scheduler.onQiyam(c, t); break;
            case Scheduler.A_DHUHA:  Scheduler.onDhuha(c, t); break;
        }
    }

    /**
     * The prayer alarm is the one moment the app is certain to be awake, so the update check
     * rides along with it rather than waking the phone on its own. goAsync keeps the process
     * alive for the request; Updater throttles itself to one every six hours, so five alarms a
     * day cost at most four.
     */
    private void lookForUpdate(Context c) {
        final PendingResult pending = goAsync();
        try {
            Updater.checkInBackground(c, pending::finish);
        } catch (Exception e) {
            pending.finish();
        }
    }
}
