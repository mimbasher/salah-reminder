package com.salah.reminder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * The Play build's stand-in for the GitHub build's self-updater.
 *
 * Google Play updates its own apps, and its policy forbids an app that downloads and installs
 * code outside the store — so this build does none of it, asks for no internet permission,
 * and never contacts GitHub. The screens that know about updates call the same five methods
 * either way; here they do nothing, and {@link #SELF_UPDATES} tells the settings screen to
 * leave that part of the page out.
 */
final class Updater {
    /** Whether this build can update itself. Off here; Play does it. */
    static final boolean SELF_UPDATES = false;

    static final String EXTRA_INSTALL = "com.salah.reminder.INSTALL_UPDATE";

    private static final String STORE_PAGE =
            "https://play.google.com/store/apps/details?id=com.salah.reminder";

    interface Done { void onChecked(String version, String error); }

    private Updater() { }

    private static String installed;

    /** Our own versionName, looked up once per process. */
    static String installedVersion(Context c) {
        if (installed == null) {
            try {
                installed = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (Exception e) {
                installed = "0";
            }
        }
        return installed;
    }

    /** Never anything waiting, so no banner and no button anywhere. */
    static String available(Context c) { return null; }

    static void check(Context c, boolean force, Done done) {
        if (done != null) done.onChecked(null, null);
    }

    static void checkInBackground(Context c, Runnable whenDone) {
        if (whenDone != null) whenDone.run();
    }

    static void clearNotice(Context c) { }

    static void resume(Activity a) { }

    static void install(Activity a) { }

    /** The one thing still worth offering: the store page, for the rating and the changelog. */
    static void openReleasesPage(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(STORE_PAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }
}
