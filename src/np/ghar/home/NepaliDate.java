package np.ghar.home;

import java.util.Calendar;

/**
 * Bikram Sambat dates and Nepali festivals.
 * BS months have no formula: each year's month lengths come from the published calendar.
 * 2083 was checked against the government holiday list (Poush 10 = 25 Dec 2026, Magh 1 = 15 Jan 2027,
 * Falgun 7 = 19 Feb 2027, Chaitra 8 = 22 Mar 2027, 1 Baisakh 2084 = 14 Apr 2027).
 * 2084 is the commonly published prediction and still has to be checked when its calendar is out.
 */
final class NepaliDate {

    static final String[] MONTHS = {"Baisakh", "Jestha", "Ashadh", "Shrawan", "Bhadra", "Ashoj",
            "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"};

    private static final int FIRST_YEAR = 2083;
    private static final int[][] DAYS = {
            {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30},   // 2083 (verified)
            {31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30},   // 2084 (to verify)
    };
    /** 1 Baisakh 2083 = 14 April 2026. */
    private static final long ANCHOR = dayNumber(2026, 4, 14);

    /** Festivals by their English date: {year, month, day}. Lunar ones move every year, so this list is per year. */
    private static final int[][] FEST_DATES = {
            {2026, 12, 24}, {2026, 12, 25}, {2026, 12, 30}, {2027, 1, 11}, {2027, 1, 15}, {2027, 1, 30},
            {2027, 2, 7}, {2027, 2, 19}, {2027, 3, 6}, {2027, 3, 9}, {2027, 3, 22}, {2027, 4, 6}, {2027, 4, 14},
    };
    private static final String[] FEST_NAMES = {
            "Yomari Punhi", "Christmas", "Tamu Lhosar", "Prithvi Jayanti", "Maghe Sankranti", "Martyrs' Day",
            "Sonam Lhosar", "Democracy Day", "Maha Shivaratri", "Gyalpo Lhosar", "Fagu Purnima (Holi)", "Ghode Jatra",
            "Nepali New Year 2084",
    };

    private NepaliDate() { }

    /** Days since a fixed point, from a calendar date (no time zones involved). */
    static long dayNumber(int y, int m, int d) {
        int a = (14 - m) / 12;
        long yy = y + 4800 - a;
        long mm = m + 12 * a - 3;
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    private static long today(Calendar c) {
        return dayNumber(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** "Mangsir 3, 2083 BS", or null when the date is outside the years this file knows. */
    static String format(Calendar c) {
        long n = today(c) - ANCHOR;
        if (n < 0) return null;
        for (int y = 0; y < DAYS.length; y++) {
            for (int m = 0; m < 12; m++) {
                if (n < DAYS[y][m]) return MONTHS[m] + " " + (n + 1) + ", " + (FIRST_YEAR + y) + " BS";
                n -= DAYS[y][m];
            }
        }
        return null;
    }

    /** "Today: Maghe Sankranti", "Maghe Sankranti tomorrow", "Maghe Sankranti in 12 days", or null. */
    static String festival(Calendar c) {
        long now = today(c);
        for (int i = 0; i < FEST_DATES.length; i++) {
            long left = dayNumber(FEST_DATES[i][0], FEST_DATES[i][1], FEST_DATES[i][2]) - now;
            if (left < 0) continue;
            if (left == 0) return "Today: " + FEST_NAMES[i];
            if (left == 1) return FEST_NAMES[i] + " tomorrow";
            if (left <= 60) return FEST_NAMES[i] + " in " + left + " days";
            return null;
        }
        return null;
    }
}
