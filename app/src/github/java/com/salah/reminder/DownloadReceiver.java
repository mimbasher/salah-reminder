package com.salah.reminder;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Android's download manager says the APK has landed. Without this the finished download sat
 * there until the app was next opened, which rather defeats being told about it in the shade.
 */
public class DownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent it) {
        long id = it.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        // Only ours: the id has to match the download we started, and the status is re-read
        // from the download manager rather than trusted from the broadcast.
        if (id < 0 || id != Scheduler.prefs(c).getLong("upd_download", -1)) return;
        Updater.onDownloadFinished(c, id);
    }
}
