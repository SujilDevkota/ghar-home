package np.ghar.home;

/**
 * The tithi (lunar day) at sunrise in Kathmandu, worked out from the positions of the sun and the moon.
 * A tithi is each 12 degrees the moon gains on the sun: 15 in the bright half (Shukla), 15 in the dark half (Krishna).
 * The formulas are the shortened ones from Meeus, "Astronomical Algorithms": good to a few minutes of time,
 * so on a day when the tithi changes right at sunrise this can differ from a printed patro.
 */
final class Tithi {

    static final String[] NAMES = {"Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami",
            "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi"};
    static final String[] NAMES_NE = {"प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पञ्चमी", "षष्ठी", "सप्तमी", "अष्टमी",
            "नवमी", "दशमी", "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी"};

    private static final double LAT = 27.7172, LON = 85.3240;      // Kathmandu

    private Tithi() { }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }

    private static double norm(double deg) {
        deg %= 360;
        return deg < 0 ? deg + 360 : deg;
    }

    private static double sun(double jd) {
        double t = (jd - 2451545.0) / 36525, m = 357.5291092 + 35999.0502909 * t;
        return norm(280.46646 + 36000.76983 * t + (1.914602 - 0.004817 * t) * sin(m) + 0.019993 * sin(2 * m) + 0.000289 * sin(3 * m));
    }

    private static double moon(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double l = 218.3164477 + 481267.88123421 * t, d = 297.8501921 + 445267.1114034 * t, m = 357.5291092 + 35999.0502909 * t;
        double a = 134.9633964 + 477198.8675055 * t, f = 93.2720950 + 483202.0175233 * t;
        double sum = 6288774 * sin(a) + 1274027 * sin(2 * d - a) + 658314 * sin(2 * d) + 213618 * sin(2 * a) - 185116 * sin(m)
                - 114332 * sin(2 * f) + 58793 * sin(2 * d - 2 * a) + 57066 * sin(2 * d - m - a) + 53322 * sin(2 * d + a)
                + 45758 * sin(2 * d - m) - 40923 * sin(m - a) - 34720 * sin(d) - 30383 * sin(m + a) + 15327 * sin(2 * d - 2 * f)
                - 12528 * sin(a + 2 * f) + 10980 * sin(a - 2 * f) + 10675 * sin(4 * d - a) + 10034 * sin(3 * a)
                + 8548 * sin(4 * d - 2 * a) - 7888 * sin(2 * d + m - a) - 6766 * sin(2 * d + m) - 5163 * sin(d - a)
                + 4987 * sin(d + m) + 4036 * sin(2 * d - m + a) + 3994 * sin(2 * d + 2 * a) + 3861 * sin(4 * d) + 3665 * sin(2 * d - 3 * a);
        return norm(l + sum / 1000000.0);
    }

    /** The moment of sunrise in Kathmandu on a day (a Julian day number, as NepaliDate.dayNumber gives). */
    private static double sunrise(long day) {
        double noon = day - LON / 360;                      // local solar noon, in universal time
        double dec = Math.asin(sin(23.44) * sin(sun(noon)));
        double lat = Math.toRadians(LAT);
        double h = Math.acos((Math.sin(Math.toRadians(-0.833)) - Math.sin(lat) * Math.sin(dec)) / (Math.cos(lat) * Math.cos(dec)));
        return noon - Math.toDegrees(h) / 360;
    }

    /** 0 to 29: 0-14 are the bright half (14 = Purnima), 15-29 the dark half (29 = Aunsi). */
    static int index(long day) {
        double jd = sunrise(day);
        return (int) (norm(moon(jd) - sun(jd)) / 12);
    }

    /** "Shukla Dashami", "Purnima", "Aunsi" (or the same in Nepali). */
    static String name(long day, boolean ne) {
        int i = index(day);
        if (i == 14) return ne ? "पूर्णिमा" : "Purnima";
        if (i == 29) return ne ? "औंसी" : "Aunsi";
        String half = i < 15 ? (ne ? "शुक्ल" : "Shukla") : (ne ? "कृष्ण" : "Krishna");
        return half + " " + (ne ? NAMES_NE : NAMES)[i % 15];
    }

    /** The tithi alone, short enough for a calendar cell. */
    static String brief(long day, boolean ne) {
        int i = index(day);
        if (i == 14) return ne ? "पूर्णिमा" : "Purnima";
        if (i == 29) return ne ? "औंसी" : "Aunsi";
        return (ne ? NAMES_NE : NAMES)[i % 15];
    }
}
