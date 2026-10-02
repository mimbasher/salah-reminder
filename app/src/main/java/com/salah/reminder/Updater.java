package com.salah.reminder;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the app up to date from its GitHub releases: checks for a newer APK, downloads it
 * with Android's own download manager, and hands it to the system installer.
 */
final class Updater {
    static final String REPO = "mimbasher/salah-reminder";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases/latest";

    private static final long EVERY = 6 * 3600000L;     // don't nag GitHub more often than this
    private static final String FILE = "update.apk";

    /** Called on the main thread once a check finishes. {@code version} is null when up to date. */
    interface Done { void onChecked(String version, String error); }

    private Updater() { }

    // ---------------- version ----------------

    static String installedVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** True when {@code remote} is a higher dotted-number version than {@code local}. */
    static boolean isNewer(String remote, String local) {
        if (remote == null) return false;
        String[] r = clean(remote), l = clean(local);
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int a = part(r, i), b = part(l, i);
            if (a != b) return a > b;
        }
        return false;
    }

    private static String[] clean(String v) {
        return (v == null ? "0" : v).trim().replaceAll("^[vV]", "").split("\\.");
    }

    private static int part(String[] parts, int i) {
        if (i >= parts.length) return 0;
        StringBuilder digits = new StringBuilder();
        for (char ch : parts[i].toCharArray()) {
            if (ch >= '0' && ch <= '9') digits.append(ch);
            else break;
        }
        try {
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The cached newer version, or null when there's nothing to install. */
    static String available(Context c) {
        String tag = Scheduler.prefs(c).getString("upd_tag", null);
        return isNewer(tag, installedVersion(c)) ? tag : null;
    }

    // ---------------- checking ----------------

    static void check(Context c, boolean force, Done done) {
        SharedPreferences p = Scheduler.prefs(c);
        long last = p.getLong("upd_checked", 0);
        if (!force && System.currentTimeMillis() - last < EVERY) {
            if (done != null) done.onChecked(available(c), null);
            return;
        }
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String error = null;
            try {
                JSONObject json = new JSONObject(get(LATEST));
                String tag = json.optString("tag_name", "").replaceAll("^[vV]", "");
                String url = null;
                JSONArray assets = json.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name", "").toLowerCase().endsWith(".apk")) {
                        url = a.optString("browser_download_url", null);
                        break;
                    }
                }
                if (tag.isEmpty() || url == null) {
                    error = "That release has no APK yet";
                } else {
                    p.edit().putString("upd_tag", tag).putString("upd_url", url)
                            .putLong("upd_checked", System.currentTimeMillis()).apply();
                }
            } catch (Exception e) {
                error = "Couldn't reach GitHub";
            }
            final String err = error;
            if (done != null) main.post(() -> done.onChecked(available(c), err));
        }).start();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setRequestProperty("User-Agent", "SalahReminder");
            con.setConnectTimeout(12000);
            con.setReadTimeout(12000);
            if (con.getResponseCode() / 100 != 2) throw new Exception("HTTP " + con.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    // ---------------- downloading & installing ----------------

    /** Starts the update: asks for install permission, downloads, then opens the installer. */
    static void install(Activity a) {
        String url = Scheduler.prefs(a).getString("upd_url", null);
        if (available(a) == null || url == null) {
            toast(a, "Already up to date");
            return;
        }
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            toast(a, "Allow this app to install updates, then tap Update again");
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) { }
            return;
        }
        Uri ready = finishedDownload(a);
        if (ready != null) {                     // already on disk
            openInstaller(a, ready);
            return;
        }
        File old = new File(a.getExternalFilesDir(null), FILE);
        if (old.exists() && !old.delete()) old.deleteOnExit();
        try {
            DownloadManager dm = a.getSystemService(DownloadManager.class);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Salah Reminder " + Scheduler.prefs(a).getString("upd_tag", ""))
                    .setDescription("Downloading the update")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(a, null, FILE);
            long id = dm.enqueue(r);
            Scheduler.prefs(a).edit().putLong("upd_download", id).apply();
            toast(a, "Downloading the update…");
        } catch (Exception e) {
            toast(a, "Couldn't start the download — opening the releases page");
            openReleasesPage(a);
        }
    }

    /**
     * Call from onResume. Opens the installer once for a finished download — not again every
     * time the app comes back, and not at all once the update is already installed.
     */
    static void resume(Activity a) {
        SharedPreferences p = Scheduler.prefs(a);
        if (available(a) == null) {
            forget(a);
            return;
        }
        Uri uri = finishedDownload(a);
        if (uri == null) return;
        long id = p.getLong("upd_download", -1);
        if (p.getLong("upd_prompted", Long.MIN_VALUE) == id) return;   // already offered
        p.edit().putLong("upd_prompted", id).apply();
        openInstaller(a, uri);
    }

    /** Drops a finished or stale download, and the file with it. */
    private static void forget(Context c) {
        SharedPreferences p = Scheduler.prefs(c);
        if (!p.contains("upd_download")) return;
        p.edit().remove("upd_download").remove("upd_prompted").apply();
        File f = new File(c.getExternalFilesDir(null), FILE);
        if (f.exists() && !f.delete()) f.deleteOnExit();
    }

    private static Uri finishedDownload(Context c) {
        long id = Scheduler.prefs(c).getLong("upd_download", -1);
        if (id < 0) return null;
        DownloadManager dm = c.getSystemService(DownloadManager.class);
        try (Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cur == null || !cur.moveToFirst()) return null;
            int status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_FAILED) {
                Scheduler.prefs(c).edit().remove("upd_download").apply();
                return null;
            }
            if (status != DownloadManager.STATUS_SUCCESSFUL) return null;
            return dm.getUriForDownloadedFile(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static void openInstaller(Activity a, Uri uri) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast(a, "Open the downloaded APK from your Files app to install it");
        }
    }

    static void openReleasesPage(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    private static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }
}
