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

    // ---------------- rounding ----------------

    @Test
    public void everyTimeLandsOnAWholeMinute() {
        for (long t : times(noonIn("Asia/Riyadh", 2026, 3, 15), MAKKAH_LAT, MAKKAH_LNG, MWL, false)) {
            assertEquals("times are shown to the minute, so they must sit on one", 0, t % 60000);
        }
    }

    @Test
    public void theTimesMatchAPublishedTimetableToTheMinute() {
        // Amman, 20 October 2026, Muslim World League. The true instants, checked against an
        // independent high-precision solar position, are 05:21:40, 12:21:09, 15:32:47,
        // 17:58:35 and 19:15:27 — so a timetable that never shows a prayer early reads:
        Calendar day = noonIn("Asia/Amman", 2026, 10, 20);
        long[] t = times(day, 31.9539, 35.9106, MWL, false);
        String[] expected = {"05:22", "12:22", "15:33", "17:59", "19:16"};
        for (int i = 0; i < 5; i++) {
            assertEquals(PrayerCalc.NAMES[i], expected[i], clock(t[i], "Asia/Amman"));
        }
    }

    @Test
    public void sunriseRoundsTheOtherWay() {
        // Sunrise ends Fajr, so rounding it up would claim there is still time to pray.
        // The true sunrise that morning is 06:43:15.
        Calendar day = noonIn("Asia/Amman", 2026, 10, 20);
        long sr = PrayerCalc.sunrise(day, 31.9539, 35.9106);
        assertEquals(0, sr % 60000);
        assertEquals("06:43", clock(sr, "Asia/Amman"));
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
