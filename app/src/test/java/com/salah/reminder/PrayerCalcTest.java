package com.salah.reminder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Tests for the one part of the app where a silent error would be serious.
 *
 * These assert physics and documented method behaviour rather than a captured table of
 * numbers: times in the right order, Dhuhr at solar noon, Umm al-Qura's fixed 90-minute
 * Isha, a later Hanafi Asr, and Qibla bearings that are exact by geometry. A refactor that
 * breaks the astronomy fails here; one that shifts a time by a second does not.
 */
public class PrayerCalcTest {
    private static final double MAKKAH_LAT = 21.4225, MAKKAH_LNG = 39.8262;
    private static final int ISNA = 0, MWL = 1, EGYPT = 2, UMM_AL_QURA = 3, JAFARI = 15;

    private TimeZone original;

    @Before public void rememberZone() { original = TimeZone.getDefault(); }
    @After public void restoreZone() { TimeZone.setDefault(original); }

    /** PrayerCalc reads the default zone, so each case states the one it means. */
    private static Calendar noonIn(String zone, int year, int month, int dayOfMonth) {
        TimeZone tz = TimeZone.getTimeZone(zone);
        TimeZone.setDefault(tz);
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(year, month - 1, dayOfMonth, 12, 0);
        return c;
    }

    /** The old five-argument shape, for the cases that care about nothing else. */
    private static long[] times(Calendar day, double lat, double lng, int method, boolean hanafi) {
        return PrayerCalc.times(day, lat, lng, new PrayerCalc.Config(method, hanafi));
    }

    private static long midnightOf(Calendar day) {
        Calendar mid = (Calendar) day.clone();
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 0);
        mid.set(Calendar.SECOND, 0);
        mid.set(Calendar.MILLISECOND, 0);
        return mid.getTimeInMillis();
    }

    /** The time a clock in {@code zone} would show, to the minute. */
    private static String clock(long millis, String zone) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(zone));
        c.setTimeInMillis(millis);
        return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private static double localHour(long millis, String zone) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(zone));
        c.setTimeInMillis(millis);
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0;
    }

    private static void assertOrdered(long[] t) {
        for (int i = 1; i < t.length; i++) {
            assertTrue(PrayerCalc.NAMES[i] + " must come after " + PrayerCalc.NAMES[i - 1],
                    t[i] > t[i - 1]);
        }
    }

    // ---------------- prayer times ----------------

    @Test
    public void theFiveTimesRunInOrder() {
        long[] t = times(noonIn("Asia/Riyadh", 2026, 3, 15),
                MAKKAH_LAT, MAKKAH_LNG, UMM_AL_QURA, false);
        assertOrdered(t);
    }

    @Test
    public void dhuhrSitsAtSolarNoon() {
        // Makkah is 2h39m of longitude east of Greenwich but keeps UTC+3, so mean solar noon
        // falls around 12:21 local, and the equation of time moves it by at most a quarter hour.
        long[] t = times(noonIn("Asia/Riyadh", 2026, 3, 15),
                MAKKAH_LAT, MAKKAH_LNG, UMM_AL_QURA, false);
        double hour = localHour(t[1], "Asia/Riyadh");
        assertTrue("Dhuhr at " + hour + "h is not near solar noon", hour > 12.0 && hour < 13.0);
    }

    @Test
    public void ummAlQuraPutsIshaNinetyMinutesAfterMaghrib() {
        // The defining quirk of the method: a fixed interval, not a sun angle.
        long[] t = times(noonIn("Asia/Riyadh", 2026, 3, 15),
                MAKKAH_LAT, MAKKAH_LNG, UMM_AL_QURA, false);
        assertEquals(90, (t[4] - t[3]) / 60000);
    }

    @Test
    public void aBiggerFajrAngleMeansAnEarlierFajr() {
        Calendar day = noonIn("Asia/Riyadh", 2026, 3, 15);
        long isna = times(day, MAKKAH_LAT, MAKKAH_LNG, ISNA, false)[0];       // 15°
        long egypt = times(day, MAKKAH_LAT, MAKKAH_LNG, EGYPT, false)[0];    // 19.5°
        assertTrue("Egypt's lower sun angle must put Fajr earlier", egypt < isna);
    }

    @Test
    public void hanafiAsrIsLaterThanStandardAsr() {
        Calendar day = noonIn("Asia/Riyadh", 2026, 3, 15);
        long standard = times(day, MAKKAH_LAT, MAKKAH_LNG, ISNA, false)[2];
        long hanafi = times(day, MAKKAH_LAT, MAKKAH_LNG, ISNA, true)[2];
        assertTrue("Hanafi Asr must fall after the standard one", hanafi > standard);
    }

    @Test
    public void everyMonthStaysOrderedAndNearNoonAcrossDaylightSaving() {
        // London moves on and off BST, which is where an off-by-an-hour bug would show up.
        for (int month = 1; month <= 12; month++) {
            long[] t = times(noonIn("Europe/London", 2026, month, 15),
                    51.5074, -0.1278, MWL, false);
            assertOrdered(t);
            double hour = localHour(t[1], "Europe/London");
            assertTrue("month " + month + ": Dhuhr at " + hour + "h",
                    hour > 11.5 && hour < 13.5);
        }
    }

    @Test
    public void sunriseFallsBetweenFajrAndDhuhr() {
        Calendar day = noonIn("Asia/Riyadh", 2026, 3, 15);
        long[] t = times(day, MAKKAH_LAT, MAKKAH_LNG, ISNA, false);
        long sunrise = PrayerCalc.sunrise(day, MAKKAH_LAT, MAKKAH_LNG);
        assertTrue(t[0] < sunrise && sunrise < t[1]);
    }

    @Test
    public void sunriseReportsZeroWhenTheSunNeverRises() {
        // Scheduler.dhuhaTime relies on this to switch the Dhuha reminder off, so it is a contract.
        assertEquals(0, PrayerCalc.sunrise(
                noonIn("Europe/Oslo", 2026, 6, 21), 69.6496, 18.9560));
    }

    /** Tromsø, Longyearbyen, Alert (the northernmost inhabited place) and its southern twin. */
    private static final double[][] POLAR = {
            {69.6496, 18.9560}, {78.2232, 15.6267}, {82.5018, -62.3481}, {-77.8419, 166.6863}};

    @Test
    public void polarPlacesStayOrderedAtBothSolstices() {
        for (double[] place : POLAR) {
            for (int[] date : new int[][]{{6, 21}, {12, 21}}) {
                assertOrdered(times(noonIn("Europe/Oslo", 2026, date[0], date[1]),
                        place[0], place[1], MWL, false));
            }
        }
    }

    @Test
    public void polarPlacesNeverFallBackToMidnight() {
        // The bug this guards: a NaN sunset reached Math.round, which is 0, so Maghrib, Fajr
        // and Isha all came out as exactly local midnight — and the alarms went with them.
        for (double[] place : POLAR) {
            for (int month = 1; month <= 12; month++) {
                Calendar day = noonIn("Europe/Oslo", 2026, month, 15);
                long midnight = midnightOf(day);
                for (long t : times(day, place[0], place[1], MWL, false)) {
                    assertTrue("lat " + place[0] + " month " + month + " landed on midnight",
                            t != midnight);
                }
            }
        }
    }

    @Test
    public void everyMonthOfAPolarNightStaysOrdered() {
        // Longyearbyen's polar night runs from late October to mid-February.
        for (int month = 1; month <= 12; month++) {
            assertOrdered(times(noonIn("Europe/Oslo", 2026, month, 15),
                    78.2232, 15.6267, MWL, false));
        }
    }

    @Test
    public void theNorthPoleFallsBackToTheNearestLatitude() {
        // No day of the year there has a usable sunrise-to-sunset run, so nearest-day finds
        // nothing and the latitude fallback takes over. It still has to produce a timetable.
        for (int[] date : new int[][]{{6, 21}, {12, 21}}) {
            assertOrdered(times(noonIn("UTC", 2026, date[0], date[1]), 89.9, 0, MWL, false));
        }
    }

    // ---------------- the same method, everywhere ----------------

    /**
     * Twelve cities on six continents at four points in the year, each checked against an
     * independent implementation of the Muslim World League's method. One line per case:
     * zone, date, latitude, longitude, city, then Fajr, Dhuhr, Asr, Maghrib, Isha and sunrise.
     */
    private static final String[] WORLD = {
            "Asia/Riyadh|2026-03-15|21.4225|39.8262|Makkah|05:16,12:30,15:53,18:30,19:40,06:30",
            "Asia/Riyadh|2026-06-21|21.4225|39.8262|Makkah|04:14,12:22,15:42,19:06,20:26,05:39",
            "Asia/Riyadh|2026-10-27|21.4225|39.8262|Makkah|05:06,12:05,15:22,17:48,18:58,06:21",
            "Asia/Riyadh|2026-12-21|21.4225|39.8262|Makkah|05:34,12:19,15:23,17:44,18:58,06:54",
            "Africa/Cairo|2026-03-15|30.0444|31.2357|Cairo|04:46,12:04,15:28,18:03,19:18,06:05",
            "Africa/Cairo|2026-06-21|30.0444|31.2357|Cairo|04:18,12:57,16:32,19:59,21:30,05:54",
            "Africa/Cairo|2026-10-27|30.0444|31.2357|Cairo|05:45,12:39,15:49,18:12,19:28,07:05",
            "Africa/Cairo|2026-12-21|30.0444|31.2357|Cairo|05:21,11:53,14:41,16:59,18:20,06:47",
            "Europe/Istanbul|2026-03-15|41.0082|28.9784|Istanbul|05:45,13:13,16:33,19:11,20:37,07:16",
            "Europe/Istanbul|2026-06-21|41.0082|28.9784|Istanbul|03:24,13:06,17:07,20:40,22:38,05:32",
            "Europe/Istanbul|2026-10-27|41.0082|28.9784|Istanbul|05:56,12:48,15:42,18:06,19:34,07:29",
            "Europe/Istanbul|2026-12-21|41.0082|28.9784|Istanbul|06:46,13:02,15:21,17:39,19:13,08:25",
            "Asia/Karachi|2026-03-15|24.8607|67.0011|Karachi|05:26,12:41,16:05,18:41,19:52,06:41",
            "Asia/Karachi|2026-06-21|24.8607|67.0011|Karachi|04:14,12:34,15:55,19:24,20:48,05:43",
            "Asia/Karachi|2026-10-27|24.8607|67.0011|Karachi|05:19,12:16,15:31,17:55,19:08,06:36",
            "Asia/Karachi|2026-12-21|24.8607|67.0011|Karachi|05:51,12:30,15:28,17:48,19:05,07:12",
            "Asia/Jakarta|2026-03-15|-6.2088|106.8456|Jakarta|04:48,12:02,15:10,18:06,19:11,05:57",
            "Asia/Jakarta|2026-06-21|-6.2088|106.8456|Jakarta|04:46,11:54,15:16,17:47,18:58,06:01",
            "Asia/Jakarta|2026-10-27|-6.2088|106.8456|Jakarta|04:16,11:37,14:51,17:46,18:53,05:27",
            "Asia/Jakarta|2026-12-21|-6.2088|106.8456|Jakarta|04:20,11:51,15:18,18:05,19:17,05:36",
            "Europe/London|2026-03-15|51.5074|-0.1278|London|04:23,12:09,15:19,18:05,19:51,06:15",
            "Europe/London|2026-06-21|51.5074|-0.1278|London|02:31,13:02,17:25,21:22,23:27,04:43",
            "Europe/London|2026-10-27|51.5074|-0.1278|London|04:53,11:44,14:15,16:43,18:29,06:45",
            "Europe/London|2026-12-21|51.5074|-0.1278|London|05:59,11:59,13:38,15:53,17:51,08:04",
            "America/New_York|2026-03-15|40.7128|-74.006|New York|05:36,13:05,16:26,19:03,20:29,07:08",
            "America/New_York|2026-06-21|40.7128|-74.006|New York|03:19,12:58,16:58,20:31,22:28,05:25",
            "America/New_York|2026-10-27|40.7128|-74.006|New York|05:49,12:40,15:34,17:58,19:25,07:21",
            "America/New_York|2026-12-21|40.7128|-74.006|New York|05:38,11:54,14:14,16:32,18:05,07:17",
            "Africa/Lagos|2026-03-15|6.5244|3.3792|Lagos|05:44,12:55,16:08,18:58,20:03,06:53",
            "Africa/Lagos|2026-06-21|6.5244|3.3792|Lagos|05:17,12:48,16:16,19:03,20:15,06:33",
            "Africa/Lagos|2026-10-27|6.5244|3.3792|Lagos|05:22,12:30,15:52,18:28,19:34,06:33",
            "Africa/Lagos|2026-12-21|6.5244|3.3792|Lagos|05:37,12:45,16:06,18:37,19:48,06:52",
            "Australia/Sydney|2026-03-15|-33.8688|151.2093|Sydney|05:30,13:04,16:35,19:14,20:33,06:54",
            "Australia/Sydney|2026-06-21|-33.8688|151.2093|Sydney|05:30,11:57,14:36,16:54,18:18,07:00",
            "Australia/Sydney|2026-10-27|-33.8688|151.2093|Sydney|04:31,12:39,16:19,19:18,20:43,06:00",
            "Australia/Sydney|2026-12-21|-33.8688|151.2093|Sydney|03:56,12:53,16:38,20:05,21:43,05:41",
            "Europe/Moscow|2026-03-15|55.7558|37.6173|Moscow|04:40,12:38,15:42,18:33,20:30,06:45",
            "Europe/Moscow|2026-06-21|55.7558|37.6173|Moscow|01:49,12:31,17:03,21:18,23:08,03:45",
            "Europe/Moscow|2026-10-27|55.7558|37.6173|Moscow|05:20,12:13,14:31,17:01,18:59,07:25",
            "Europe/Moscow|2026-12-21|55.7558|37.6173|Moscow|06:36,12:28,13:45,15:58,18:11,08:57",
            "America/Argentina/Buenos_Aires|2026-03-15|-34.6037|-58.3816|Buenos Aires|05:28,13:02,16:33,19:12,20:31,06:53",
            "America/Argentina/Buenos_Aires|2026-06-21|-34.6037|-58.3816|Buenos Aires|06:30,12:55,15:33,17:50,19:16,08:00",
            "America/Argentina/Buenos_Aires|2026-10-27|-34.6037|-58.3816|Buenos Aires|04:26,12:37,16:19,19:18,20:44,05:57",
            "America/Argentina/Buenos_Aires|2026-12-21|-34.6037|-58.3816|Buenos Aires|03:51,12:52,16:38,20:06,21:45,05:37",
            "Africa/Nairobi|2026-03-15|-1.2921|36.8219|Nairobi|05:29,12:42,15:43,18:45,19:50,06:38",
            "Africa/Nairobi|2026-06-21|-1.2921|36.8219|Nairobi|05:18,12:35,16:00,18:36,19:47,06:33",
            "Africa/Nairobi|2026-10-27|-1.2921|36.8219|Nairobi|05:01,12:17,15:35,18:21,19:28,06:12",
            "Africa/Nairobi|2026-12-21|-1.2921|36.8219|Nairobi|05:09,12:31,15:57,18:37,19:48,06:25"
    };

    @Test
    public void theTimesAgreeWithAnIndependentImplementationWorldwide() {
        for (String row : WORLD) {
            String[] f = row.split("\\|");
            String zone = f[0], city = f[4];
            String[] ymd = f[1].split("-"), ref = f[5].split(",");
            Calendar day = noonIn(zone, Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]),
                    Integer.parseInt(ymd[2]));
            long[] t = times(day, Double.parseDouble(f[2]), Double.parseDouble(f[3]), MWL, false);
            for (int i = 0; i < 5; i++) {
                // Asr gets two minutes of room. Where the sun is low its altitude changes so
                // slowly that a fraction of an arcminute of solar position moves Asr by whole
                // minutes, which is what London and Moscow do in late autumn. The other four
                // land on the reference minute everywhere.
                int allowed = i == 2 ? 2 : 1;
                assertWithin(city + " " + f[1] + " " + PrayerCalc.NAMES[i],
                        ref[i], t[i], zone, allowed);
            }
        }
    }

    @Test
    public void sunriseAgreesWithTheSameReferenceWorldwide() {
        for (String row : WORLD) {
            String[] f = row.split("\\|");
            String[] ymd = f[1].split("-"), ref = f[5].split(",");
            Calendar day = noonIn(f[0], Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]),
                    Integer.parseInt(ymd[2]));
            long sr = PrayerCalc.sunrise(day, Double.parseDouble(f[2]), Double.parseDouble(f[3]));
            assertWithin(f[4] + " " + f[1] + " sunrise", ref[5], sr, f[0], 1);
        }
    }

    /** Fails unless the clock shows {@code expected}, give or take {@code allowed} minutes. */
    private static void assertWithin(String what, String expected, long actual, String zone,
                                     int allowed) {
        String shown = clock(PrayerCalc.displayMinute(actual), zone);
        if (shown.equals(expected)) return;
        int mine = Integer.parseInt(shown.substring(0, 2)) * 60
                + Integer.parseInt(shown.substring(3));
        int theirs = Integer.parseInt(expected.substring(0, 2)) * 60
                + Integer.parseInt(expected.substring(3));
        int off = ((mine - theirs + 720) % 1440) - 720;
        assertTrue(what + ": " + shown + " against " + expected + ", " + off + " min apart",
                Math.abs(off) <= allowed);
    }

    // ---------------- rounding ----------------

    @Test
    public void theTimesKeepTheirSecondsSoNoAlarmComesEarly() {
        boolean anyWithSeconds = false;
        for (long t : times(noonIn("Asia/Riyadh", 2026, 3, 15), MAKKAH_LAT, MAKKAH_LNG, MWL, false)) {
            if (t % 60000 != 0) anyWithSeconds = true;
        }
        assertTrue("a prayer begins at an instant, and the alarm is set from this one",
                anyWithSeconds);
    }

    @Test
    public void theDisplayedMinuteIsTheNearestOne() {
        // The rounding rule itself, with no place or date in it: the table above is what
        // checks the times. A Maghrib at 17:58:35 reads 17:59, and one at 17:58:20 reads 17:58.
        long minute = 17 * 3600000L + 58 * 60000L;
        assertEquals(minute, PrayerCalc.displayMinute(minute));
        assertEquals(minute, PrayerCalc.displayMinute(minute + 20000L));
        assertEquals(minute, PrayerCalc.displayMinute(minute + 29999L));
        assertEquals(minute + 60000L, PrayerCalc.displayMinute(minute + 30000L));
        assertEquals(minute + 60000L, PrayerCalc.displayMinute(minute + 35000L));
        assertEquals(minute + 60000L, PrayerCalc.displayMinute(minute + 59999L));
    }

    // ---------------- the mosque down the road ----------------

    @Test
    public void tuningShiftsOnePrayerAndLeavesTheRestAlone() {
        Calendar day = noonIn("Asia/Riyadh", 2026, 3, 15);
        long[] plain = times(day, MAKKAH_LAT, MAKKAH_LNG, MWL, false);

        PrayerCalc.Config cfg = new PrayerCalc.Config(MWL, false);
        cfg.tune[0] = -3;
        cfg.tune[4] = 5;
        long[] tuned = PrayerCalc.times(day, MAKKAH_LAT, MAKKAH_LNG, cfg);

        assertEquals(-3, (tuned[0] - plain[0]) / 60000);
        assertEquals(5, (tuned[4] - plain[4]) / 60000);
        for (int i = 1; i <= 3; i++) assertEquals(plain[i], tuned[i]);
    }

    @Test
    public void customAnglesMatchTheMethodThatUsesThem() {
        Calendar day = noonIn("Asia/Riyadh", 2026, 3, 15);
        PrayerCalc.Config cfg = new PrayerCalc.Config(PrayerCalc.CUSTOM, false);
        cfg.customFajr = 18;                       // the Muslim World League pair
        cfg.customIsha = 17;
        assertArrayEquals(times(day, MAKKAH_LAT, MAKKAH_LNG, MWL, false),
                PrayerCalc.times(day, MAKKAH_LAT, MAKKAH_LNG, cfg));
    }

    @Test
    public void jafariHoldsMaghribUntilAfterSunset() {
        Calendar day = noonIn("Asia/Tehran", 2026, 3, 15);
        long sunset = times(day, 35.6892, 51.3890, MWL, false)[3];
        long maghrib = times(day, 35.6892, 51.3890, JAFARI, false)[3];
        assertTrue("Jafari Maghrib waits for the redness to go", maghrib > sunset);
        assertTrue("but not by more than half an hour", maghrib - sunset < 30 * 60000L);
    }

    // ---------------- the clock of the place, not of the phone ----------------

    @Test
    public void theAnswerDoesNotDependOnWhereThePhoneIs() {
        // Makkah's prayers happen when they happen. Looking them up on a phone set to London
        // must not move them, which is what reading the phone's own zone used to do.
        // (noonIn sets the default zone, so these two calls are made from different places.)
        long[] fromHome = PrayerCalc.times(noonIn("Asia/Riyadh", 2026, 3, 15),
                MAKKAH_LAT, MAKKAH_LNG, new PrayerCalc.Config(MWL, false));
        long[] fromLondon = PrayerCalc.times(noonIn("Europe/London", 2026, 3, 15),
                MAKKAH_LAT, MAKKAH_LNG, new PrayerCalc.Config(MWL, false));
        assertArrayEquals(fromHome, fromLondon);
    }

    @Test
    public void aClockThatSkipsMidnightDoesNotMoveThePrayers() {
        // Santiago puts its clocks forward at midnight, so 00:00 does not exist that day.
        // Anchoring the day's maths on local midnight used to push every prayer an hour late.
        Calendar shift = noonIn("America/Santiago", 2026, 9, 6);
        Calendar before = noonIn("America/Santiago", 2026, 9, 5);
        long[] t = PrayerCalc.times(shift, -33.4489, -70.6693, new PrayerCalc.Config(MWL, false));
        long[] y = PrayerCalc.times(before, -33.4489, -70.6693, new PrayerCalc.Config(MWL, false));
        assertOrdered(t);
        // Dhuhr tracks the sun, not the clock, so from one day to the next it barely moves.
        assertTrue("Dhuhr jumped " + (t[1] - y[1]) / 60000 + " min across the change",
                Math.abs(t[1] - y[1] - 24 * 3600000L) < 5 * 60000L);
    }

    // ---------------- short summer nights ----------------

    @Test
    public void everyShortNightRuleGivesOrderedTimesInMidsummer() {
        // Stockholm in June: the sky never reaches 18° of darkness, so each rule has to invent
        // a Fajr and an Isha, and each one has to stay inside the night it belongs to.
        for (int rule = 0; rule < PrayerCalc.HIGH_LAT.length; rule++) {
            PrayerCalc.Config cfg = new PrayerCalc.Config(MWL, false);
            cfg.highLat = rule;
            long[] t = PrayerCalc.times(noonIn("Europe/Stockholm", 2026, 6, 21),
                    59.3293, 18.0686, cfg);
            assertOrdered(t);
            for (long x : t) assertTrue(PrayerCalc.HIGH_LAT[rule] + " produced a 1970 time", x > 0);
        }
    }

    @Test
    public void theMiddleOfTheNightRuleSplitsTheNightInHalf() {
        // Half the night after sunset, half before sunrise: tonight's Isha and tomorrow's
        // Fajr are then the same moment, the middle of the night.
        PrayerCalc.Config cfg = new PrayerCalc.Config(MWL, false);
        cfg.highLat = PrayerCalc.HL_MIDDLE;
        Calendar day = noonIn("Europe/Stockholm", 2026, 6, 21);
        Calendar tomorrow = (Calendar) day.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        long isha = PrayerCalc.times(day, 59.3293, 18.0686, cfg)[4];
        long fajr = PrayerCalc.times(tomorrow, 59.3293, 18.0686, cfg)[0];
        assertEquals("the two halves must meet", 0, Math.abs(fajr - isha) / 60000, 2);
    }

    // ---------------- qibla ----------------

    @Test
    public void qiblaIsDueSouthFromTheKaabasOwnMeridian() {
        assertEquals(180.0, PrayerCalc.qibla(40, PrayerCalc.KAABA_LNG), 1e-6);
    }

    @Test
    public void qiblaIsDueNorthFromSouthOfTheKaaba() {
        assertEquals(0.0, PrayerCalc.qibla(0, PrayerCalc.KAABA_LNG), 1e-6);
    }

    @Test
    public void qiblaIsSymmetricAboutThatMeridian() {
        double east = PrayerCalc.qibla(30, PrayerCalc.KAABA_LNG + 20);
        double west = PrayerCalc.qibla(30, PrayerCalc.KAABA_LNG - 20);
        assertEquals(360.0, east + west, 1e-6);
    }

    @Test
    public void qiblaMatchesThePublishedBearings() {
        assertEquals(136.1, PrayerCalc.qibla(30.0444, 31.2357), 0.5);    // Cairo
        assertEquals(119.0, PrayerCalc.qibla(51.5074, -0.1278), 0.5);    // London
        assertEquals(295.2, PrayerCalc.qibla(-6.2088, 106.8456), 0.5);   // Jakarta
        assertEquals(58.5, PrayerCalc.qibla(40.7128, -74.0060), 0.5);    // New York
    }
}
