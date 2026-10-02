package com.salah.reminder;

/** Hijri date via the platform's ICU calendar, with a plain-text fallback. */
final class HijriDate {
    private static final String[] MONTHS = {
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"};

    private HijriDate() { }

    /** e.g. "20 Rabi' al-Thani 1448", or null if the platform can't tell us. */
    static String format(long millis) {
        try {
            android.icu.util.IslamicCalendar c = new android.icu.util.IslamicCalendar();
            c.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            c.setTimeInMillis(millis);
            int m = c.get(android.icu.util.Calendar.MONTH);
            if (m < 0 || m > 11) return null;
            return c.get(android.icu.util.Calendar.DATE) + " " + MONTHS[m]
                    + " " + c.get(android.icu.util.Calendar.YEAR);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
