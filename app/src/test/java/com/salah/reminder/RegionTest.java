package com.salah.reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The country-to-timetable table, checked against each authority's published parameters. */
public class RegionTest {

    private static Region.Profile of(String cc) {
        return Region.of(cc);
    }

    // ---------------- the fallback ----------------

    @Test
    public void anUnlistedCountryKeepsTheLeague() {
        assertEquals(1, of("DE").method);
        assertFalse(of("DE").hasOffsets());
    }

    @Test
    public void aMissingCountryIsNotACrash() {
        assertSame(Region.DEFAULT, of(null));
        assertSame(Region.DEFAULT, of(""));
        assertSame(Region.DEFAULT, of("ZZ"));
    }

    @Test
    public void theCodeIsReadWhicheverWayItIsCased() {
        assertEquals(of("JO").method, of("jo").method);
        assertEquals(of("JO").authority, of("jo").authority);
    }

    // ---------------- the rows that carry printed minutes ----------------

    @Test
    public void jordanHoldsMaghribFiveMinutesPastSunset() {
        // Ministry of Awqaf: Fajr 18, Isha 18 — Karachi's angles — and Maghrib sunset + 5.
        Region.Profile p = of("JO");
        assertEquals(4, p.method);
        assertArrayOffsets(p, 0, 0, 0, 5, 0);
        double[] a = PrayerCalc.anglesOf(p.method);
        assertEquals(18.0, a[0], 1e-9);
        assertEquals(18.0, a[1], 1e-9);
    }

    @Test
    public void turkeyCarriesItsTemkin() {
        // Measured against Diyanet's own published sunset and true noon.
        Region.Profile p = of("TR");
        assertEquals(5, p.method);
        assertArrayOffsets(p, 0, 5, 4, 7, 0);
    }

    @Test
    public void turkeysAsrIsTheStandardShadowWhateverTheFiqh() {
        // Diyanet prints the 1x shadow, an hour off the Hanafi one.
        assertFalse(of("TR").hanafi);
    }

    // ---------------- the rows that are angles alone ----------------

    @Test
    public void southAsiaGetsKarachisAnglesAndTheHanafiShadow() {
        for (String cc : new String[]{"PK", "IN", "BD", "AF"}) {
            assertEquals(cc, 4, of(cc).method);
            assertTrue(cc + " prints the Hanafi Asr", of(cc).hanafi);
            assertFalse(cc, of(cc).hasOffsets());
        }
    }

    @Test
    public void theGulfStatesEachKeepTheirOwn() {
        assertEquals(3, of("SA").method);               // Umm al-Qura, Fajr 18.5, Isha 90 min
        assertEquals(6, of("AE").method);               // Dubai, 18.2/18.2
        assertEquals(8, of("KW").method);               // Kuwait, 18/17.5
        assertEquals(9, of("QA").method);               // Qatar, 18 and Isha 90 min
        assertEquals(7, of("BH").method);               // Gulf Region, 19.5 and Isha 90 min
        assertEquals(7, of("OM").method);
    }

    @Test
    public void theEgyptianTableCoversTheCountriesThatPrintIt() {
        for (String cc : new String[]{"EG", "LY", "SD", "YE", "SY", "LB", "IQ"}) {
            assertEquals(cc, 2, of(cc).method);
        }
    }

    @Test
    public void northAmericaIsWhereIsnaBelongs() {
        assertEquals(0, of("US").method);
        assertEquals(0, of("CA").method);
    }

    @Test
    public void theRestOfTheNamedAuthoritiesLandOnTheirOwnMethod() {
        assertEquals(14, of("IR").method);              // Tehran
        assertEquals(12, of("FR").method);              // UOIF
        assertEquals(13, of("RU").method);              // Spiritual Administration
        assertEquals(11, of("MA").method);              // Morocco
        assertEquals(10, of("ID").method);              // Kemenag, 20/18
        assertEquals(10, of("MY").method);              // JAKIM, the same parameters
        assertEquals(10, of("SG").method);              // MUIS, likewise
    }

    @Test
    public void algeriaAndPalestineSitOnTheLeaguesAnglesDeliberately() {
        // Algeria's published parameters are 18/17, the League's exactly, and Palestine's
        // tables follow the League too — so neither needs a row, and both must resolve to it.
        assertEquals(1, of("DZ").method);
        assertEquals(1, of("PS").method);
    }

    // ---------------- the table as a whole ----------------

    @Test
    public void everyRowPointsAtARealMethod() {
        for (String cc : new String[]{"SA", "AE", "KW", "QA", "BH", "OM", "EG", "LY", "SD",
                "YE", "SY", "LB", "IQ", "MR", "SO", "DJ", "KM", "JO", "MA", "TN", "TR", "PK",
                "IN", "BD", "AF", "SG", "MY", "BN", "ID", "IR", "FR", "RU", "US", "CA", "DE"}) {
            Region.Profile p = of(cc);
            assertTrue(cc + " method out of range", p.method >= 0
                    && p.method < PrayerCalc.METHODS.length);
            assertEquals(cc + " offsets must cover the five prayers", 5, p.offset.length);
            assertTrue(cc + " needs an authority to name", p.authority.length() > 0);
        }
    }

    @Test
    public void noRowQuietlyPointsAtTheCustomAngles() {
        // CUSTOM reads its angles from settings the person typed, so a country resolving to it
        // would hand them whatever was last in those two boxes.
        for (String cc : new String[]{"SA", "JO", "TR", "PK", "EG", "ID", "US", "DE", "XX"}) {
            assertFalse(cc, of(cc).method == PrayerCalc.CUSTOM);
        }
    }

    // ---------------- what the settings screen says ----------------

    @Test
    public void theDescriptionNamesTheAuthorityAndItsAngles() {
        String s = Region.describe("JO", of("JO"));
        assertTrue(s, s.contains("Ministry of Awqaf, Jordan"));
        assertTrue(s, s.contains("Fajr 18"));
        assertTrue(s, s.contains("Maghrib +5 min"));
    }

    @Test
    public void theDescriptionSpellsOutAFixedIshaRatherThanAnAngle() {
        String s = Region.describe("SA", of("SA"));
        assertTrue(s, s.contains("90 min after Maghrib"));
    }

    @Test
    public void theDescriptionSaysSoWhenTheCountryIsUnknown() {
        String s = Region.describe(null, Region.DEFAULT);
        assertTrue(s, s.contains("Without a country"));
        assertTrue(s, s.contains("Muslim World League"));
    }

    @Test
    public void theDescriptionCallsPalestineByItsName() {
        assertTrue(Region.describe("PS", of("PS")).startsWith("Palestine keeps"));
    }

    private static void assertArrayOffsets(Region.Profile p, int... expected) {
        for (int i = 0; i < 5; i++) {
            assertEquals(PrayerCalc.NAMES[i] + " offset", expected[i], p.offset[i]);
        }
        assertTrue(p.hasOffsets());
    }
}
