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

    /** Slots in the raw hour table below. Maghrib is sunset, so it has no slot of its own. */
    private static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, SUNSET = 4, ISHA = 5;

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
        double[] h = dayTimes(jd, lat, fajrA, ishaA, hanafi);

        // Polar day or night: there is no sunrise or sunset to reason from, and the night-portion
        // rule below needs both. Borrow them from the nearest day that has them (aqrab al-ayyam).
        if (!usableDay(h) && !borrowNearestDay(h, jd, lat, fajrA, ishaA, hanafi)) {
            // Within a degree of the pole the sun's altitude barely changes all year, so no day
            // is usable. Last resort: the nearest latitude that has ordinary days (aqrab
            // al-bilad). Dhuhr does not depend on latitude, so it stays exact either way.
            h = dayTimes(jd, Math.copySign(48, lat), fajrA, ishaA, hanafi);
        }

        // High-latitude safety (angle-based night portion), for nights too short for the angle.
        double night = 24 - (h[SUNSET] - h[SUNRISE]);
        double fp = fajrA / 60.0 * night;
        if (Double.isNaN(h[FAJR]) || h[SUNRISE] - h[FAJR] > fp) h[FAJR] = h[SUNRISE] - fp;
        if (ishaA > 0) {
            double ip = ishaA / 60.0 * night;
            if (Double.isNaN(h[ISHA]) || h[ISHA] - h[SUNSET] > ip) h[ISHA] = h[SUNSET] + ip;
        }

        double tz = TimeZone.getDefault().getOffset(mid.getTimeInMillis() + 12 * 3600000L) / 3600000.0;
        double[] out5 = {h[FAJR], h[DHUHR], h[ASR], h[SUNSET], h[ISHA]};
        long[] out = new long[5];
        for (int i = 0; i < 5; i++) {
            out[i] = mid.getTimeInMillis() + Math.round((out5[i] + tz - lng / 15.0) * 3600000.0);
        }
        return out;
    }

    /**
     * Whether a day can be borrowed from: a real sunrise and sunset with noon and Asr between
     * them. Within a few days of a polar night the sun clears the horizon for minutes only, and
     * the altitude Asr is measured from is then reached after sunset — ordered times, but not
     * ones to copy. NaN fails every comparison here, so this covers the missing-sun case too.
     */
    private static boolean usableDay(double[] h) {
        return h[SUNRISE] < h[DHUHR] && h[DHUHR] < h[ASR] && h[ASR] < h[SUNSET];
    }

    /** The raw local-solar hours for one day, with NaN wherever the sun never reaches the angle. */
    private static double[] dayTimes(double jd, double lat, double fajrA, double ishaA,
                                     boolean hanafi) {
        double[] h = new double[6];
        h[FAJR] = sunAngleTime(jd, lat, fajrA, 5 / 24.0, true);
        h[SUNRISE] = sunAngleTime(jd, lat, 0.833, 6 / 24.0, true);
        h[DHUHR] = midDay(jd, 12 / 24.0);
        h[ASR] = asrTime(jd, lat, hanafi ? 2 : 1, 13 / 24.0);
        h[SUNSET] = sunAngleTime(jd, lat, 0.833, 18 / 24.0, false);
        h[ISHA] = ishaA < 0 ? h[SUNSET] + (-ishaA) / 60.0
                : sunAngleTime(jd, lat, ishaA, 18 / 24.0, false);
        return h;
    }

    /**
     * Nearest-day substitution: walks outwards from {@code jd}, the past first, for a day whose
     * sun both rises and sets, and takes that day's whole timetable.
     *
     * It has to be the whole day, not only the missing times. Under a polar night the sun is
     * below the horizon all day yet still crosses the negative altitude Asr is measured from,
     * so today's Asr is a real number — and pairing it with a borrowed Maghrib put Asr after
     * sunset at Tromsø in December. One real day's times stay in order among themselves.
     *
     * @return false if half a year holds no usable day, which only happens at the poles.
     */
    private static boolean borrowNearestDay(double[] h, double jd, double lat, double fajrA,
                                            double ishaA, boolean hanafi) {
        for (int step = 1; step <= 183; step++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                double[] alt = dayTimes(jd + dir * step, lat, fajrA, ishaA, hanafi);
                if (!usableDay(alt)) continue;
                System.arraycopy(alt, 0, h, 0, h.length);
                return true;
            }
        }
        return false;
    }

    /** Sunrise for the calendar day of {@code day}, as epoch millis, or 0 if the sun never rises. */
    public static long sunrise(Calendar day, double lat, double lng) {
        Calendar mid = (Calendar) day.clone();
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 0);
        mid.set(Calendar.SECOND, 0);
        mid.set(Calendar.MILLISECOND, 0);
        double jd = julian(mid.get(Calendar.YEAR), mid.get(Calendar.MONTH) + 1,
                mid.get(Calendar.DAY_OF_MONTH)) - lng / (15 * 24.0);
        double sr = sunAngleTime(jd, lat, 0.833, 6 / 24.0, true);
        if (Double.isNaN(sr)) return 0;
        double tz = TimeZone.getDefault().getOffset(mid.getTimeInMillis() + 12 * 3600000L) / 3600000.0;
        return mid.getTimeInMillis() + Math.round((sr + tz - lng / 15.0) * 3600000.0);
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
