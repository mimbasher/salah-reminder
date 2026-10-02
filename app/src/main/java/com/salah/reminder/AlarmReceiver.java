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
            case Scheduler.A_PRAYER: Scheduler.onPrayerTime(c, i, t); break;
            case Scheduler.A_PRE:    Scheduler.onPre(c, i, t); break;
            case Scheduler.A_NAG:    Scheduler.onNag(c, i); break;
            case Scheduler.A_PRAYED: Scheduler.onPrayed(c, i); break;
            case Scheduler.A_REPIN:  Scheduler.onRepin(c, i); break;
            case Scheduler.A_QIYAM:  Scheduler.onQiyam(c, t); break;
            case Scheduler.A_DHUHA:  Scheduler.onDhuha(c, t); break;
        }
    }
}
