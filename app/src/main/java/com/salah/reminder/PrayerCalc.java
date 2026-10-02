package com.salah.reminder;

import java.util.Calendar;
import java.util.TimeZone;

/** Astronomical prayer-time calculation (same method as PrayTimes.org). */
public class PrayerCalc {
    public static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    public static final String[] METHODS = {
            "ISNA (North America)", "Muslim World League", "Egyptian Authority",
            "Umm al-Qura (Makkah)", "Karachi"};
    // {fajr angle, isha angle}; a negative isha value = minutes after Maghrib
    private static final double[][] ANGLES = {{15, 15}, {18, 17}, {19.5, 17.5}, {18.5, -90}, {18, 18}};

    public static final double KAABA_LAT = 21.4225, KAABA_LNG = 39.8262;

    /** Returns the five prayer times (epoch millis) for the calendar day of {@code day}. */
    public static long[] times(Calendar day, double lat, double lng, int method, boolean hanafi) {
        Calendar mid = (Calendar) day.clone();
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 0);
        mid.set(Calendar.SECOND, 0);
        mid.set(Calendar.MILLISECOND, 0);
        double jd = julian(mid.get(Calendar.YEAR), mid.get(Calendar.MONTH) + 1,
                mid.get(Calendar.DAY_OF_MONTH)) - lng / (15 * 24.0);

        double fajrA = ANGLES[method][0], ishaA = ANGLES[method][1];
        double fajr = sunAngleTime(jd, lat, fajrA, 5 / 24.0, true);
        double sunrise = sunAngleTime(jd, lat, 0.833, 6 / 24.0, true);
        double dhuhr = midDay(jd, 12 / 24.0);
        double asr = asrTime(jd, lat, hanafi ? 2 : 1, 13 / 24.0);
        double sunset = sunAngleTime(jd, lat, 0.833, 18 / 24.0, false);
        double maghrib = sunset;
        double isha = ishaA < 0 ? maghrib + (-ishaA) / 60.0
                : sunAngleTime(jd, lat, ishaA, 18 / 24.0, false);

        // High-latitude safety (angle-based night portion)
        double night = 24 - (sunset - sunrise);
        double fp = fajrA / 60.0 * night;
        if (Double.isNaN(fajr) || sunrise - fajr > fp) fajr = sunrise - fp;
        if (ishaA > 0) {
            double ip = ishaA / 60.0 * night;
            if (Double.isNaN(isha) || isha - sunset > ip) isha = sunset + ip;
        }

        double tz = TimeZone.getDefault().getOffset(mid.getTimeInMillis() + 12 * 3600000L) / 3600000.0;
        double[] h = {fajr, dhuhr, asr, maghrib, isha};
        long[] out = new long[5];
        for (int i = 0; i < 5; i++) {
            double t = h[i] + tz - lng / 15.0;
            out[i] = mid.getTimeInMillis() + Math.round(t * 3600000.0);
        }
        return out;
    }

    /** Qibla bearing in degrees from true north. */
    public static double qibla(double lat, double lng) {
        double phi = Math.toRadians(lat), k = Math.toRadians(KAABA_LAT);
        double dl = Math.toRadians(KAABA_LNG - lng);
        double b = Math.toDegrees(Math.atan2(Math.sin(dl),
                Math.cos(phi) * Math.tan(k) - Math.sin(phi) * Math.cos(dl)));
        return (b + 360) % 360;
    }

    // ---- astronomy helpers ----
    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** returns {declination, equation of time} */
    private static double[] sun(double jd) {
        double D = jd - 2451545.0;
        double g = fixA(357.529 + 0.98560028 * D);
        double q = fixA(280.459 + 0.98564736 * D);
        double L = fixA(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double ra = Math.toDegrees(Math.atan2(dcos(e) * dsin(L), dcos(L))) / 15;
        double eqt = q / 15 - fixH(ra);
        double decl = Math.toDegrees(Math.asin(dsin(e) * dsin(L)));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        return fixH(12 - sun(jd + t)[1]);
    }

    private static double sunAngleTime(double jd, double lat, double angle, double t, boolean ccw) {
        double decl = sun(jd + t)[0];
        double noon = midDay(jd, t);
        double T = Math.toDegrees(Math.acos((-dsin(angle) - dsin(decl) * dsin(lat))
                / (dcos(decl) * dcos(lat)))) / 15;
        return noon + (ccw ? -T : T);
    }

    private static double asrTime(double jd, double lat, int factor, double t) {
        double decl = sun(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + dtan(Math.abs(lat - decl)))));
        return sunAngleTime(jd, lat, angle, t, false);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double fixA(double a) { a = a % 360; return a < 0 ? a + 360 : a; }
    private static double fixH(double a) { a = a % 24; return a < 0 ? a + 24 : a; }
}
