package com.salah.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores alarms and pinned cards after reboot, app update, or time/zone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent it) {
        Scheduler.rescheduleAll(c);
    }
}
