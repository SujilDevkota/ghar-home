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
    static final String[] MONTHS_NE = {"वैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
            "कात्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत"};
    static final String[] DAYS_EN = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    static final String[] DAYS_NE = {"आइत", "सोम", "मंगल", "बुध", "बिही", "शुक्र", "शनि"};
    static final String[] AD_MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    static final int FIRST_YEAR = 2083;
    private static final int[][] DAYS = {
            {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30},   // 2083 (verified)
            {31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30},   // 2084 (to verify)
    };
    static final int LAST_YEAR = FIRST_YEAR + DAYS.length - 1;
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
    private static final String[] FEST_NAMES_NE = {
            "योमरी पुन्ही", "क्रिसमस", "तमु ल्होसार", "पृथ्वी जयन्ती", "माघे संक्रान्ति", "शहीद दिवस",
            "सोनाम ल्होसार", "प्रजातन्त्र दिवस", "महाशिवरात्रि", "ग्याल्पो ल्होसार", "फागु पूर्णिमा (होली)", "घोडेजात्रा",
            "नयाँ वर्ष २०८४",
    };

    private NepaliDate() { }

    /** Days since a fixed point (the Julian day number), from a calendar date: no time zones involved. */
    static long dayNumber(int y, int m, int d) {
        int a = (14 - m) / 12;
        long yy = y + 4800 - a;
        long mm = m + 12 * a - 3;
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    /** The English date of a day number: {year, month, day}. */
    static int[] english(long n) {
        long a = n + 32044, b = (4 * a + 3) / 146097, c = a - 146097 * b / 4;
        long d = (4 * c + 3) / 1461, e = c - 1461 * d / 4, m = (5 * e + 2) / 153;
        return new int[] {(int) (100 * b + d - 4800 + m / 10), (int) (m + 3 - 12 * (m / 10)), (int) (e - (153 * m + 2) / 5 + 1)};
    }

    /** 0 = Sunday. */
    static int weekday(long n) { return (int) ((n + 1) % 7); }

    static long today(Calendar c) {
        return dayNumber(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** {BS year, month 0-11, day} of a day number, or null outside the years this file knows. */
    static int[] bs(long n) {
        long left = n - ANCHOR;
        if (left < 0) return null;
        for (int y = 0; y < DAYS.length; y++) {
            for (int m = 0; m < 12; m++) {
                if (left < DAYS[y][m]) return new int[] {FIRST_YEAR + y, m, (int) left + 1};
                left -= DAYS[y][m];
            }
        }
        return null;
    }

    static int daysIn(int year, int month) { return DAYS[year - FIRST_YEAR][month]; }

    /** The day number of the first day of a BS month. */
    static long monthStart(int year, int month) {
        long n = ANCHOR;
        for (int y = FIRST_YEAR; y <= year; y++) {
            for (int m = 0; m < 12; m++) {
                if (y == year && m == month) return n;
                n += DAYS[y - FIRST_YEAR][m];
            }
        }
        return n;
    }

    /** The festival on a day, or null. */
    static String festivalOn(long n, boolean ne) {
        for (int i = 0; i < FEST_DATES.length; i++) {
            if (dayNumber(FEST_DATES[i][0], FEST_DATES[i][1], FEST_DATES[i][2]) == n) return ne ? FEST_NAMES_NE[i] : FEST_NAMES[i];
        }
        return null;
    }

    /** "Mangsir 3, 2083 BS" (or "मंसिर ३, २०८३"), or null when the date is outside the years this file knows. */
    static String format(Calendar c, boolean ne) {
        int[] d = bs(today(c));
        if (d == null) return null;
        return ne ? MONTHS_NE[d[1]] + " " + T.num("" + d[2]) + ", " + T.num("" + d[0])
                : MONTHS[d[1]] + " " + d[2] + ", " + d[0] + " BS";
    }

    /** "Today: Maghe Sankranti", "Maghe Sankranti tomorrow", "Maghe Sankranti in 12 days", or null. */
    static String festival(Calendar c, boolean ne) {
        long now = today(c);
        for (int i = 0; i < FEST_DATES.length; i++) {
            long left = dayNumber(FEST_DATES[i][0], FEST_DATES[i][1], FEST_DATES[i][2]) - now;
            if (left < 0) continue;
            String name = ne ? FEST_NAMES_NE[i] : FEST_NAMES[i];
            if (left == 0) return ne ? "आज: " + name : "Today: " + name;
            if (left == 1) return ne ? "भोलि: " + name : name + " tomorrow";
            if (left <= 60) return ne ? name + " — " + T.num("" + left) + " दिन बाँकी" : name + " in " + left + " days";
            return null;
        }
        return null;
    }
}
