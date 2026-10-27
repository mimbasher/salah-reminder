package com.salah.reminder;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which timetable a country keeps, so the times are right without anyone picking an authority
 * out of a list of seventeen. Unlisted countries keep the Muslim World League's 18/17, so a
 * row here means a country that departs from it.
 */
final class Region {
    private Region() { }

    static final class Profile {
        /** Index into {@link PrayerCalc#METHODS}. */
        final int method;
        final boolean hanafi;
        /** Minutes this authority prints on top of the astronomy, in {@link PrayerCalc#NAMES} order. */
        final int[] offset;
        final String authority;

        private Profile(int method, boolean hanafi, String authority, int[] offset) {
            this.method = method;
            this.hanafi = hanafi;
            this.authority = authority;
            this.offset = offset;
        }

        boolean hasOffsets() {
            for (int m : offset) if (m != 0) return true;
            return false;
        }
    }

    private static final int[] NO_OFFSET = {0, 0, 0, 0, 0};

    static final Profile DEFAULT = new Profile(1, false, "Muslim World League", NO_OFFSET);

    private static final Map<String, Profile> BY_COUNTRY = new HashMap<>();

    /** @param codes one or more ISO 3166-1 alpha-2 codes, space separated */
    private static void put(String codes, int method, boolean hanafi, String authority,
                            int... offset) {
        Profile p = new Profile(method, hanafi, authority,
                offset.length == 5 ? offset : NO_OFFSET);
        for (String code : codes.split(" ")) BY_COUNTRY.put(code, p);
    }

    static {
        put("SA", 3, false, "Umm al-Qura, Makkah");
        put("AE", 6, false, "Awqaf and Islamic Affairs, Dubai");
        put("KW", 8, false, "Ministry of Awqaf, Kuwait");
        put("QA", 9, false, "Ministry of Awqaf, Qatar");
        put("BH OM", 7, false, "Gulf Region");
        put("EG LY SD YE SY LB IQ MR SO DJ KM", 2, false,
                "Egyptian General Authority of Survey");

        // Karachi's angles, but Maghrib waits five minutes past sunset.
        put("JO", 4, false, "Ministry of Awqaf, Jordan", 0, 0, 0, 5, 0);

        // Temkin on top of 18/17, measured against Diyanet's own published sunset and true
        // noon. Its Ikindi is the 1x shadow, whatever the fiqh of the country.
        put("TR", 5, false, "Diyanet İşleri Başkanlığı, Turkey", 0, 5, 4, 7, 0);

        put("MA", 11, false, "Ministry of Habous, Morocco");
        put("TN", 4, false, "Tunisia");
        put("PK IN BD AF", 4, true, "University of Islamic Sciences, Karachi");
        put("SG MY BN ID", 10, false, "MUIS, JAKIM and Kemenag");
        put("IR", 14, false, "Institute of Geophysics, University of Tehran");
        put("FR", 12, false, "Union des Organisations Islamiques de France");
        put("RU", 13, false, "Spiritual Administration of Muslims of Russia");
        put("US CA", 0, false, "Islamic Society of North America");
        // Algeria's own parameters are 18/17 and Palestine's tables follow the League too,
        // so both are left to DEFAULT on purpose.
    }

    static Profile of(String code) {
        if (code == null || code.isEmpty()) return DEFAULT;
        Profile p = BY_COUNTRY.get(code.toUpperCase(Locale.US));
        return p == null ? DEFAULT : p;
    }

    /** What the settings screen says the automatic setting has chosen, and why. */
    static String describe(String code, Profile p) {
        StringBuilder sb = new StringBuilder();
        if (code != null && !code.isEmpty()) sb.append(countryName(code)).append(" keeps ");
        else sb.append("Without a country to go on, this follows ");
        sb.append(p.authority);
        double[] a = PrayerCalc.anglesOf(p.method);
        sb.append(": Fajr ").append(trim(a[0])).append('°');
        sb.append(a[1] < 0 ? ", Isha " + (int) -a[1] + " min after Maghrib"
                : ", Isha " + trim(a[1]) + "°");
        if (p.hanafi) sb.append(", the Hanafi Asr");
        for (int i = 0; i < 5; i++) {
            if (p.offset[i] == 0) continue;
            sb.append(", ").append(PrayerCalc.NAMES[i]).append(' ')
              .append(p.offset[i] > 0 ? "+" : "").append(p.offset[i]).append(" min");
        }
        return sb.append('.').toString();
    }

    /** Android names PS oddly, the same fix the city list makes. */
    private static String countryName(String code) {
        if ("PS".equals(code)) return "Palestine";
        String s = new Locale("", code).getDisplayCountry();
        return s == null || s.isEmpty() ? code : s;
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    /**
     * The country of the bundled city nearest {@code lat,lng}.
     *
     * GPS hands over coordinates, not a country, and the phone's geocoder needs the network to
     * turn one into the other — which the Play build holds no permission for. The city list is
     * already in the APK with a country against every entry, so the nearest of those answers
     * it offline.
     *
     * @return the ISO code, or null if the list could not be read
     */
    static String countryAt(Context c, double lat, double lng) {
        String best = null;
        double nearest = Double.MAX_VALUE;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getAssets().open("cities.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 5) continue;
                try {
                    double dLat = Double.parseDouble(f[3]) - lat;
                    double dLng = (Double.parseDouble(f[4]) - lng) * Math.cos(Math.toRadians(lat));
                    double d = dLat * dLat + dLng * dLng;
                    if (d < nearest) { nearest = d; best = f[0]; }
                } catch (NumberFormatException ignored) { }
            }
        } catch (Exception e) {
            return null;
        }
        return best;
    }
}
