package np.ghar.home;

import java.io.File;
import java.util.HashMap;

/** The two languages: English text is the key, and Nepali is looked up from it. */
final class T {

    static boolean ne;

    private static final HashMap<String, String> NE = new HashMap<String, String>();

    static {
        String[] pairs = {
                "Favourites", "मनपर्ने",
                "All apps", "सबै एपहरू",
                "Remote", "रिमोट",
                "Patro", "पात्रो",
                "Wallpaper", "वालपेपर",
                "Settings", "सेटिङ",
                "TV home", "टिभी होम",
                "Hidden", "लुकाइएका",
                "Sound: on", "आवाज: खुला",
                "Sound: off", "आवाज: बन्द",
                "Open", "खोल्नुहोस्",
                "Add to Favourites", "मनपर्नेमा थप्नुहोस्",
                "Remove from Favourites", "मनपर्नेबाट हटाउनुहोस्",
                "Move left", "बायाँ सार्नुहोस्",
                "Move right", "दायाँ सार्नुहोस्",
                "Hide from this screen", "यो स्क्रिनबाट लुकाउनुहोस्",
                "App info", "एपको जानकारी",
                "Uninstall", "हटाउनुहोस्",
                "Hold OK on an app below to add it here.", "तलको एपमा OK थिचिराखेर यहाँ थप्नुहोस्।",
                "No internet", "इन्टरनेट छैन",
                "Show again", "फेरि देखाउनुहोस्",
                "No hidden apps. Hold OK on an app to hide it.", "लुकाइएका एप छैनन्। लुकाउन एपमा OK थिचिराख्नुहोस्।",
                "Phone remote", "फोन रिमोट",
                "Close", "बन्द",
                "Scan with your phone's camera.", "फोनको क्यामेराले स्क्यान गर्नुहोस्।",
                "The phone must be on the same Wi-Fi as the TV.", "फोन र टिभी एउटै Wi-Fi मा हुनुपर्छ।",
                "Or open", "वा खोल्नुहोस्",
                "and enter PIN", "र PIN हाल्नुहोस्",
                "The TV is not on a network", "टिभी नेटवर्कमा छैन",
                "No other home screen found", "अर्को होम स्क्रिन भेटिएन",
                "Photo from phone", "फोनको फोटो",
                "Left / Right: change month     Back: close", "बायाँ / दायाँ: महिना फेर्नुहोस्     Back: बन्द",
                "This TV has no Nepali font", "यो टिभीमा नेपाली फन्ट छैन",
                "Home button opens Ghar Home", "Home बटनले घर होम खोल्छ",
                "Phone in other apps", "अरू एपमा फोन",
                "The Home button opens the TV's own home screen", "Home बटनले टिभीको आफ्नै होम खोल्छ",
                "The Home button opens Ghar Home", "Home बटनले घर होम खोल्छ",
                "on", "खुला", "off", "बन्द", "choose keyboard", "किबोर्ड छान्नुहोस्",
                "This TV's settings have no such screen", "यो टिभीको सेटिङमा त्यो स्क्रिन छैन",
                "Volume", "आवाज", "USB connected", "USB जोडिएको",
                "Apps", "एपहरू",
                "OK changes the highlighted setting. Press Back when you are done.", "OK ले छानिएको सेटिङ फेर्छ। सकिएपछि Back थिच्नुहोस्।",
                "Favourite: yes", "मनपर्ने: हो", "Favourite: no", "मनपर्ने: होइन",
                "Move up", "माथि सार्नुहोस्", "Move down", "तल सार्नुहोस्", "Hide", "लुकाउनुहोस्", "Show", "देखाउनुहोस्",
                "Nepali TV", "नेपाली टिभी",
                "SmartTube is needed to watch this", "यो हेर्न SmartTube चाहिन्छ",
                "Options", "विकल्प", "Language", "भाषा",
                "Today", "आज", "The forecast could not be loaded.", "पूर्वानुमान लोड हुन सकेन।",
                "Get YouTube (SmartTube)", "YouTube (SmartTube) राख्नुहोस्",
                "Google's YouTube app no longer works on old TVs. SmartTube is a free YouTube app that does.", "पुराना टिभीमा Google को YouTube एप चल्दैन। SmartTube निःशुल्क YouTube एप हो, जुन चल्छ।",
                "Open the phone remote, choose \"Install an app on the TV\" and follow the steps there.", "फोन रिमोट खोल्नुहोस्, \"Install an app on the TV\" छान्नुहोस् र त्यहाँका चरण पछ्याउनुहोस्।",
                "Starry night", "ताराले भरिएको रात", "Snowfall", "हिमपात", "Fireflies", "जुनकिरी", "animated", "चल्ने", "Sounds", "आवाज",
                "Sunny", "घमाइलो", "Clear", "सफा", "Mostly sunny", "प्रायः घमाइलो", "Mostly clear", "प्रायः सफा",
                "Partly cloudy", "आंशिक बदली", "Cloudy", "बदली", "Fog", "हुस्सु", "Drizzle", "सिमसिम पानी",
                "Rain", "वर्षा", "Snow", "हिउँ", "Showers", "झरी", "Snow showers", "हिमपात", "Thunderstorm", "चट्याङसहित वर्षा",
                "Night sky", "रातको आकाश", "Himalayan dawn", "हिमाली बिहानी", "Forest", "जंगल", "Sunset", "सूर्यास्त",
                "Deep ocean", "गहिरो सागर", "Rhododendron", "लालीगुराँस", "Charcoal", "कालो",
        };
        for (int i = 0; i < pairs.length; i += 2) NE.put(pairs[i], pairs[i + 1]);
    }

    private T() { }

    static String t(String s) {
        if (!ne) return s;
        String r = NE.get(s);
        return r == null ? s : r;
    }

    /** Digits as Nepali numerals, in Nepali. */
    static String num(String s) {
        if (!ne) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '0' && c <= '9' ? (char) ('०' + (c - '0')) : c);
        }
        return b.toString();
    }

    /** Whether the TV can draw Devanagari: some old TVs ship without the font, and would show empty boxes. */
    static boolean fontOk() {
        if (android.os.Build.VERSION.SDK_INT >= 21) return true;
        try {
            String[] fonts = new File("/system/fonts").list();
            if (fonts != null) {
                for (String f : fonts) {
                    String low = f.toLowerCase(java.util.Locale.US);
                    if (low.contains("devanagari") || low.contains("lohit") || low.contains("hindi")) return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }
}
