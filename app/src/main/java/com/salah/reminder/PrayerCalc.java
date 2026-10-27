package com.salah.reminder;

import java.util.Calendar;
import java.util.TimeZone;

/** Astronomical prayer-time calculation (same method as PrayTimes.org). */
public class PrayerCalc {
    public static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    /**
     * Calculation methods, in the order the settings screen lists them. The first five must
     * keep their places: a phone that has been through an older version has the index saved.
     */
    public static final String[] METHODS = {
            "ISNA (North America)", "Muslim World League", "Egyptian Authority",
            "Umm al-Qura (Makkah)", "Karachi (Pakistan, India, Bangladesh)",
            "Diyanet (Turkey)", "Dubai (UAE)", "Gulf Region", "Kuwait", "Qatar",
            "Singapore, Malaysia, Indonesia", "Morocco", "France (UOIF)",
            "Russia (Spiritual Administration)", "Tehran (Institute of Geophysics)",
            "Jafari (Shia Ithna-Ashari)", "Custom angles"};

    /** The index of {@link #METHODS}{@code [CUSTOM]}, whose angles come from settings. */
    public static final int CUSTOM = 16;

    /**
     * What a method's own notice board says, where the angles alone do not tell the whole
     * story. Some authorities print a few extra minutes of their own on top of the astronomy
     * — temkin in Turkey — and anyone comparing with their timetable should know to expect
     * it, and that the fine-tuning rows are how to match it. Null where there is nothing to say.
     */
    public static final String[] METHOD_NOTES = new String[METHODS.length];

    static {
        METHOD_NOTES[5] = "Turkey's printed timetables add a few minutes of temkin to Dhuhr, "
                + "Asr and Maghrib. Fine-tune those three below to match your local table.";
        METHOD_NOTES[6] = "Dubai's printed timetables add a couple of minutes to Dhuhr and "
                + "Maghrib. Fine-tune them below to match.";
        METHOD_NOTES[3] = "Isha is a fixed 90 minutes after Maghrib, as Makkah times it, not "
                + "a sun angle.";
        METHOD_NOTES[15] = "Maghrib waits until the redness has left the sky, as the Jafari "
                + "school holds.";
    }

    /**
     * {fajr angle, isha angle, maghrib angle} for each method.
     *
     * A negative isha is minutes after Maghrib rather than an angle — Umm al-Qura and Qatar
     * time Isha with a clock, not with the sky. A maghrib angle of 0 means plain sunset,
     * which is every method but the two Shia ones, who hold Maghrib until the redness goes.
     */
    private static final double[][] ANGLES = {
            {15, 15, 0},        // ISNA
            {18, 17, 0},        // Muslim World League
            {19.5, 17.5, 0},    // Egyptian General Authority of Survey
            {18.5, -90, 0},     // Umm al-Qura: Isha 90 min after Maghrib
            {18, 18, 0},        // University of Islamic Sciences, Karachi
            {18, 17, 0},        // Diyanet İşleri Başkanlığı
            {18.2, 18.2, 0},    // Dubai
            {19.5, -90, 0},     // Gulf Region
            {18, 17.5, 0},      // Kuwait
            {18, -90, 0},       // Qatar
            {20, 18, 0},        // Singapore / JAKIM / Kemenag
            {19, 17, 0},        // Morocco, Ministry of Habous
            {12, 12, 0},        // Union des Organisations Islamiques de France
            {16, 15, 0},        // Spiritual Administration of Muslims of Russia
            {17.7, 14, 4.5},    // Institute of Geophysics, University of Tehran
            {16, 14, 4},        // Jafari
            {18, 17, 0},        // Custom — overwritten from settings
    };

    /**
     * What to do where the sun never reaches the Fajr or Isha angle on a short night. There is
     * no "none" here on purpose: a reminder app has to name a time for every prayer, and
     * "there is no Isha tonight" is not one.
     */
    public static final String[] HIGH_LAT = {
            "Angle-based (default)", "Middle of the night", "One-seventh of the night"};
    public static final int HL_ANGLE = 0, HL_MIDDLE = 1, HL_SEVENTH = 2;

    public static final double KAABA_LAT = 21.4225, KAABA_LNG = 39.8262;

    /** Slots in the raw hour table below. Maghrib is sunset, so it has no slot of its own. */
    private static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, SUNSET = 4, ISHA = 5;

    /**
     * Everything the person can change about how the times come out: which authority's angles,
     * which Asr shadow, what to do on a short night, which clock the answer is in, and the
     * minutes they have added or taken off each prayer to match the mosque they pray at.
     */
    public static class Config {
        public int method;
        public boolean hanafi;
        public int highLat = HL_ANGLE;
        /** Per-prayer adjustment in minutes, in {@link #NAMES} order. */
        public final int[] tune = new int[5];
        /** Used only when {@code method == CUSTOM}. */
        public double customFajr = 18, customIsha = 17;

        public Config() { }

        public Config(int method, boolean hanafi) {
            this.method = method;
            this.hanafi = hanafi;
        }

        double[] angles() {
            double[] a = ANGLES[Math.min(Math.max(0, method), ANGLES.length - 1)].clone();
            if (method == CUSTOM) { a[0] = customFajr; a[1] = customIsha; }
            return a;
        }
    }

    /**
     * Returns the five prayer times (epoch millis) for the calendar date {@code day} falls on.
     *
     * Only the date is read, never the clock: an instant is an instant, and which zone the
     * caller keeps has no bearing on when the sun is where. Pass a calendar on the clock of
     * the place whose times these are, so that "today" means today there.
     *
     * These are exact instants, seconds and all, so an alarm set from one waits for the
     * prayer rather than beating it. Screens round them with {@link #displayMinute}.
     */
    public static long[] times(Calendar day, double lat, double lng, Config cfg) {
        long dayStart = utcMidnight(day);
        double jd = julian(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1,
                day.get(Calendar.DAY_OF_MONTH)) - lng / (15 * 24.0);

        double[] ang = cfg.angles();
        double fajrA = ang[0], ishaA = ang[1], maghribA = ang[2];
        double[] h = dayTimes(jd, lat, fajrA, ishaA, maghribA, cfg.hanafi);

        // Polar day or night: there is no sunrise or sunset to reason from, and the night-portion
        // rule below needs both. Borrow them from the nearest day that has them (aqrab al-ayyam).
        if (!usableDay(h) && !borrowNearestDay(h, jd, lat, fajrA, ishaA, maghribA, cfg.hanafi)) {
            // Within a degree of the pole the sun's altitude barely changes all year, so no day
            // is usable. Last resort: the nearest latitude that has ordinary days (aqrab
            // al-bilad). Dhuhr does not depend on latitude, so it stays exact either way.
            h = dayTimes(jd, Math.copySign(48, lat), fajrA, ishaA, maghribA, cfg.hanafi);
        }

        adjustHighLats(h, fajrA, ishaA, cfg.highLat);

        double[] out5 = {h[FAJR], h[DHUHR], h[ASR], h[SUNSET], h[ISHA]};
        long[] out = new long[5];
        for (int i = 0; i < 5; i++) {
            out[i] = instant(dayStart, out5[i], lng) + cfg.tune[i] * 60000L;
        }
        return out;
    }

    /**
     * The minute a clock should show for {@code t}: the nearest one, which is how the printed
     * timetables and every other app round a Maghrib at 17:58:35 — 17:59. Measured against a
     * reference implementation over 120 times, nearest agreed 105 times where truncating
     * agreed 51, so this is what makes the app and the timetable on the wall read alike.
     */
    public static long displayMinute(long t) {
        long shifted = t + 30000L;
        return shifted - Math.floorMod(shifted, 60000L);
    }

    /** The {fajr, isha, maghrib} angles a method is built on. */
    public static double[] anglesOf(int method) {
        return ANGLES[Math.min(Math.max(0, method), ANGLES.length - 1)].clone();
    }

    /**
     * The hours above are local mean solar time at {@code lng}; subtracting the longitude
     * gives UT, which is an absolute instant. No time zone enters into it.
     *
     * Anchoring on the local midnight instead, as this once did, asks for a moment that does
     * not always exist: Santiago, Havana and Asunción put their clocks forward at midnight,
     * and on that one night of the year every prayer came out an hour late.
     */
    private static long instant(long utcDayStart, double solarHour, double lng) {
        return utcDayStart + Math.round((solarHour - lng / 15.0) * 3600000.0);
    }

    /** Midnight UT on {@code day}'s calendar date — a fixed point to count hours from. */
    private static long utcMidnight(Calendar day) {
        Calendar mid = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        mid.clear();
        mid.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
        return mid.getTimeInMillis();
    }

    /**
     * High-latitude safety, for a night too short for the sun to reach the Fajr or Isha angle
     * at all — and for the nights either side of it, where it does reach the angle but so
     * close to midnight that the time is no use. Each rule caps how much of the night may sit
     * between the angle and the horizon.
     */
    private static void adjustHighLats(double[] h, double fajrA, double ishaA, int rule) {
        double night = 24 - (h[SUNSET] - h[SUNRISE]);
        double fp, ip;
        switch (rule) {
            case HL_MIDDLE:  fp = ip = night / 2; break;
            case HL_SEVENTH: fp = ip = night / 7; break;
            default:         fp = fajrA / 60.0 * night; ip = ishaA / 60.0 * night; break;
        }
        if (Double.isNaN(h[FAJR]) || h[SUNRISE] - h[FAJR] > fp) h[FAJR] = h[SUNRISE] - fp;
        if (ishaA > 0 && (Double.isNaN(h[ISHA]) || h[ISHA] - h[SUNSET] > ip)) {
            h[ISHA] = h[SUNSET] + ip;
        }
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
                                     double maghribA, boolean hanafi) {
        double[] h = new double[6];
        h[FAJR] = sunAngleTime(jd, lat, fajrA, 5 / 24.0, true);
        h[SUNRISE] = sunAngleTime(jd, lat, 0.833, 6 / 24.0, true);
        h[DHUHR] = midDay(jd, 12 / 24.0);
        h[ASR] = asrTime(jd, lat, hanafi ? 2 : 1, 13 / 24.0);
        double sunset = sunAngleTime(jd, lat, 0.833, 18 / 24.0, false);
        // The two Shia methods hold Maghrib back until the redness has gone from the sky, which
        // is an angle below the horizon like any other. Everyone else prays it at sunset.
        h[SUNSET] = maghribA > 0 ? sunAngleTime(jd, lat, maghribA, 18 / 24.0, false) : sunset;
        h[ISHA] = ishaA < 0 ? h[SUNSET] + (-ishaA) / 60.0
                : sunAngleTime(jd, lat, ishaA, 18 / 24.0, false);
        // On a night short enough that the redness never clears, fall back to plain sunset:
        // a Maghrib an hour late is wrong, a missing one is worse.
        if (maghribA > 0 && Double.isNaN(h[SUNSET])) h[SUNSET] = sunset;
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
                                            double ishaA, double maghribA, boolean hanafi) {
        for (int step = 1; step <= 183; step++) {
            for (int dir = -1; dir <= 1; dir += 2) {
                double[] alt = dayTimes(jd + dir * step, lat, fajrA, ishaA, maghribA, hanafi);
                if (!usableDay(alt)) continue;
                System.arraycopy(alt, 0, h, 0, h.length);
                return true;
            }
        }
        return false;
    }

    /**
     * Sunrise for the calendar day of {@code day}, as epoch millis, or 0 if the sun never
     * rises. An exact instant, which a clock truncates — the safe way for the end of Fajr.
     */
    public static long sunrise(Calendar day, double lat, double lng) {
        double jd = julian(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1,
                day.get(Calendar.DAY_OF_MONTH)) - lng / (15 * 24.0);
        double sr = sunAngleTime(jd, lat, 0.833, 6 / 24.0, true);
        if (Double.isNaN(sr)) return 0;
        return instant(utcMidnight(day), sr, lng);
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
