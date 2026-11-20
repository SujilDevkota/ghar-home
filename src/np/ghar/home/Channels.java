package np.ghar.home;

import java.io.InputStream;
import java.net.HttpURLConnection;

/**
 * Nepali TV channels, on their own official YouTube channels. Nothing is rebroadcast: a tile opens the
 * broadcaster's live stream when there is one, and its channel page when there isn't.
 * Each channel id was read from the broadcaster's channel on 2026-11-20.
 */
final class Channels {

    static final String[] NAMES = {"Kantipur TV", "News24", "Nepal Television", "Himalaya TV", "AP1 HD", "Avenues Khabar",
            "Galaxy 4K", "TV Today", "Yoho TV", "Indigenous TV", "Bhakti Darshan"};
    static final String[] IDS = {"UC3yDoaqQzOd1bNP74ZrGPTA", "UCjG2HX7jfwqIjzTlaF1CPGA", "UCTGVQIvtPu5kqNI5ABmN8Fw",
            "UCczsYSRGukY9LA7Fd_govMw", "UCFlPB2adH_uHaJfUCODTdrQ", "UCMDzPE_7fcZSRJgpwIVor_A", "UCbs0xk6PavMVDt5UJqxGV6g",
            "UCNSPYrl_sdPfOIS4e1HjhMg", "UCQ8137CMrLUCX-YkwYoJjWA", "UCjScyLNVwqNEQ_I0TVDzcDQ", "UCbpZ2cu74MjsOJbiuiwRzlg"};
    static final int[] COLORS = {0xFF7A1F2B, 0xFF1F4E7A, 0xFF2B6A4B, 0xFF6A4B1F, 0xFF4B2B7A, 0xFF7A2B5E, 0xFF1F6A6A,
            0xFF5E5E1F, 0xFF7A3E1F, 0xFF2B4B7A, 0xFF6A1F5E};

    private Channels() { }

    static String channelUrl(int i) { return "https://www.youtube.com/channel/" + IDS[i]; }

    static String videoUrl(String video) { return "https://www.youtube.com/watch?v=" + video; }

    /**
     * The video id of a channel's live stream, or null when it isn't streaming (or can't be reached).
     * YouTube's "/live" page names the live video in its first few kilobytes when asked as an old browser,
     * so only the start of the page is read. Call off the main thread.
     */
    static String liveVideo(int i) {
        HttpURLConnection c = null;
        try {
            c = Net.open(channelUrl(i) + "/live", false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 4.4.4; SmartTV) AppleWebKit/537.36 Chrome/33.0 Safari/537.36");
            c.setRequestProperty("Accept-Language", "en");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            byte[] buf = new byte[24 * 1024];
            int n = 0;
            for (int r; n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0; ) n += r;
            String head = new String(buf, 0, n, "UTF-8");
            String mark = "<link rel=\"canonical\" href=\"https://www.youtube.com/watch?v=";
            int at = head.indexOf(mark);
            if (at < 0) return null;
            String id = head.substring(at + mark.length(), Math.min(head.length(), at + mark.length() + 11));
            return id.matches("[A-Za-z0-9_-]{11}") ? id : null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
