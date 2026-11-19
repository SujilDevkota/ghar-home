package np.ghar.home;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Current weather from Open-Meteo (free, no key, CC BY 4.0).
 * Plain http on purpose: Android 4.4 TVs don't trust the certificates today's https sites use.
 * Only a temperature and a word come back, and both are checked before they are shown.
 */
final class Weather {

    static final long FRESH = 30 * 60 * 1000L, STALE = 3 * 60 * 60 * 1000L;

    private Weather() { }

    static String city(SharedPreferences p) { return p.getString("w_city", "Kathmandu"); }

    /** Fetches and saves the weather for the saved place. Call off the main thread. */
    static boolean refresh(SharedPreferences p) {
        try {
            String lat = p.getString("w_lat", "27.7172"), lon = p.getString("w_lon", "85.3240");
            JSONObject cur = new JSONObject(get("http://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,is_day")).getJSONObject("current");
            double temp = cur.getDouble("temperature_2m");
            if (temp < -90 || temp > 60) return false;
            p.edit().putInt("w_temp", (int) Math.round(temp))
                    .putString("w_text", describe(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1))
                    .putString("w_kind", kind(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1))
                    .putLong("w_time", System.currentTimeMillis()).commit();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Looks a city up by name and saves it as the weather's place. Returns its full name, or null. */
    static String setCity(SharedPreferences p, String name) {
        try {
            JSONArray found = new JSONObject(get("http://geocoding-api.open-meteo.com/v1/search?count=1&name="
                    + URLEncoder.encode(name, "UTF-8"))).optJSONArray("results");
            if (found == null || found.length() == 0) return null;
            JSONObject c = found.getJSONObject(0);
            double lat = c.getDouble("latitude"), lon = c.getDouble("longitude");
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            String city = clean(c.getString("name")), country = clean(c.optString("country", ""));
            p.edit().putString("w_lat", String.format(Locale.US, "%.4f", lat))
                    .putString("w_lon", String.format(Locale.US, "%.4f", lon))
                    .putString("w_city", city).remove("w_time").commit();
            return country.length() > 0 ? city + ", " + country : city;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String clean(String s) {
        s = s.replaceAll("[\\p{Cntrl}<>\"]", "").trim();
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(false);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) {
                b.write(buf, 0, n);
                if (b.size() > 200000) throw new Exception("Too long");
            }
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** WMO weather codes, as the picture to draw. */
    private static String kind(int code, boolean day) {
        if (code <= 1) return day ? "sun" : "moon";
        if (code == 2) return day ? "partly" : "partly_night";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if (code >= 95) return "storm";
        if (code >= 51) return "rain";
        return "";
    }

    /** WMO weather codes, in a word or two. */
    private static String describe(int code, boolean day) {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code == 1) return day ? "Mostly sunny" : "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }
}
