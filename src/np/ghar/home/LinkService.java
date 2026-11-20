package np.ghar.home;

import android.app.Instrumentation;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.KeyEvent;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * The link to the home network: a tiny web server on the TV (port 8765).
 * A phone opens it as a remote control; a computer sends it an APK and the TV's installer opens.
 * Every action needs the PIN shown on the TV, and an install still has to be confirmed on the TV.
 */
public class LinkService extends Service {

    static final int PORT = 8765;
    private static final long MAX_APK = 300L * 1024 * 1024, MAX_PHOTO = 25L * 1024 * 1024;

    private ServerSocket server;
    private volatile boolean running;

    /** The PIN, made once on this TV. */
    static String pin(Context c) {
        SharedPreferences p = c.getSharedPreferences("ghar", MODE_PRIVATE);
        String pin = p.getString("pin", null);
        if (pin == null) {
            pin = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1000000));
            p.edit().putString("pin", pin).commit();
        }
        return pin;
    }

    /** The TV's address on the home network, or null when it has none. */
    static String address() {
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all != null && all.hasMoreElements()) {
                NetworkInterface n = all.nextElement();
                if (n.isLoopback() || !n.isUp()) continue;
                Enumeration<InetAddress> addrs = n.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        new Thread(new Runnable() {
            @Override public void run() { serve(); }
        }, "link").start();
        if (Build.VERSION.SDK_INT < 21) {
            new Thread(new Runnable() {
                @Override public void run() { watchHome(); }
            }, "home-watch").start();
        }
    }

    @Override public void onDestroy() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        super.onDestroy();
    }

    /**
     * Android 4.4 TVs that never ask which home screen to use: when the maker's one comes to the front,
     * Ghar Home takes its place, so the Home button and the TV's start both land here.
     * Not while the user asked for the original ("TV home"), not after a crash, and not when switched off.
     */
    private void watchHome() {
        java.util.HashSet<String> homes = new java.util.HashSet<String>();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : getPackageManager().queryIntentActivities(home, 0)) {
                String p = r.activityInfo == null ? null : r.activityInfo.packageName;
                if (p != null && !p.equals(getPackageName()) && !p.equals("com.android.settings")) homes.add(p);
            }
        } catch (Exception ignored) { }
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        SharedPreferences prefs = getSharedPreferences("ghar", MODE_PRIVATE);
        while (running && !homes.isEmpty()) {
            try {
                Thread.sleep(700);
                if (HomeActivity.showing != null || GharAccess.letOriginal) continue;
                if (!prefs.getBoolean("take_home", true) || prefs.getInt("crashes", 0) > 0) continue;
                List<android.app.ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) continue;
                if (homes.contains(tasks.get(0).topActivity.getPackageName())) goHome();
            } catch (Throwable ignored) { }
        }
    }

    private void serve() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
            while (running) {
                final Socket s = server.accept();
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { handle(s); } catch (Throwable ignored) { }
                        try { s.close(); } catch (IOException ignored) { }
                    }
                }, "link-client").start();
            }
        } catch (IOException ignored) { }
    }

    // ---------------------------------------------------------------- one request

    private void handle(Socket s) throws IOException {
        s.setSoTimeout(30000);
        InputStream in = new BufferedInputStream(s.getInputStream(), 65536);
        OutputStream out = s.getOutputStream();

        String first = line(in);
        if (first == null) return;
        String[] parts = first.split(" ");
        if (parts.length < 2) return;
        String method = parts[0];
        long length = 0;
        boolean expect = false;
        for (String h = line(in); h != null && h.length() > 0; h = line(in)) {
            String low = h.toLowerCase(Locale.US);
            if (low.startsWith("content-length:")) {
                try { length = Long.parseLong(h.substring(15).trim()); } catch (NumberFormatException e) { length = -1; }
            } else if (low.startsWith("expect:") && low.contains("100-continue")) {
                expect = true;
            }
        }

        String path = parts[1];
        HashMap<String, String> query = new HashMap<String, String>();
        int q = path.indexOf('?');
        if (q >= 0) {
            for (String pair : path.substring(q + 1).split("&")) {
                int e = pair.indexOf('=');
                if (e > 0) query.put(pair.substring(0, e), URLDecoder.decode(pair.substring(e + 1), "UTF-8"));
            }
            path = path.substring(0, q);
        }

        if (path.equals("/") || path.equals("/index.html")) {
            reply(out, 200, "text/html; charset=utf-8", asset("remote.html"));
            return;
        }
        if (!path.startsWith("/api/")) {
            json(out, 404, "Not found");
            return;
        }
        if (!pin(this).equals(query.get("pin"))) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }   // slows PIN guessing
            json(out, 403, "Wrong PIN");
            return;
        }

        if (path.equals("/api/info")) {
            StringBuilder b = new StringBuilder("{\"name\":\"Ghar Home\",\"version\":\"0.8\",\"themes\":[");
            for (int i = 0; i < HomeActivity.THEME_NAMES.length; i++) {
                b.append(i > 0 ? "," : "").append('"').append(esc(HomeActivity.THEME_NAMES[i])).append('"');
            }
            b.append("],\"theme\":").append(getSharedPreferences("ghar", MODE_PRIVATE).getInt(HomeActivity.KEY_THEME, 0))
                    .append(",\"city\":\"").append(esc(Weather.city(getSharedPreferences("ghar", MODE_PRIVATE)))).append("\",\"access\":").append(GharAccess.active != null)
                    .append(",\"keyboard\":").append(GharKeyboard.active != null).append(",\"apps\":[");
            List<String[]> apps = apps();
            for (int i = 0; i < apps.size(); i++) {
                if (i > 0) b.append(',');
                b.append("{\"pkg\":\"").append(esc(apps.get(i)[0])).append("\",\"label\":\"").append(esc(apps.get(i)[1])).append("\"}");
            }
            reply(out, 200, "application/json", b.append("]}").toString().getBytes("UTF-8"));
        } else if (path.equals("/api/key")) {
            json(out, 200, key(query.get("k")));
        } else if (path.equals("/api/open")) {
            json(out, 200, open(query.get("pkg")));
        } else if (path.equals("/api/say")) {
            json(out, 200, say(query.get("q")));
        } else if (path.equals("/api/city")) {
            String name = query.get("q");
            String found = name == null || name.trim().length() == 0 ? null
                    : Weather.setCity(getSharedPreferences("ghar", MODE_PRIVATE), name.trim());
            final HomeActivity home = HomeActivity.showing;
            if (found != null && home != null) {
                home.runOnUiThread(new Runnable() { @Override public void run() { home.refreshWeather(true); } });
            }
            json(out, 200, found == null ? "City not found" : "Weather set to " + found);
        } else if (path.equals("/api/report")) {
            reply(out, 200, "text/plain; charset=utf-8", TvInput.report(this).getBytes("UTF-8"));
        } else if (path.equals("/api/source")) {
            int i = -1;
            try { i = Integer.parseInt(query.get("i")); } catch (Exception ignored) { }
            if (i < 0 || i >= TvInput.NAMES.length) json(out, 200, "No such input");
            else {
                String problem = TvInput.show(this, i);
                json(out, 200, problem == null ? TvInput.NAMES[i] : problem);
            }
        } else if (path.equals("/api/type")) {
            json(out, 200, type(query.get("q"), query.get("k")));
        } else if (path.equals("/api/theme")) {
            int i = -9;
            try { i = Integer.parseInt(query.get("i")); } catch (Exception ignored) { }
            if (i < -1 || i >= HomeActivity.THEME_NAMES.length || (i == -1 && !HomeActivity.photoFile(this).exists())) {
                json(out, 200, "No such wallpaper");
            } else {
                getSharedPreferences("ghar", MODE_PRIVATE).edit().putInt(HomeActivity.KEY_THEME, i).commit();
                refreshBackground();
                json(out, 200, i == -1 ? "Your photo" : HomeActivity.THEME_NAMES[i]);
            }
        } else if (path.equals("/api/wallpaper") && method.equals("POST")) {
            if (length <= 0 || length > MAX_PHOTO) {
                json(out, 400, "Send a photo up to 25 MB");
                return;
            }
            if (expect) {
                out.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes("US-ASCII"));
                out.flush();
            }
            json(out, 200, wallpaper(in, length));
        } else if (path.equals("/api/install") && method.equals("POST")) {
            if (length <= 0 || length > MAX_APK) {
                json(out, 400, "Send the APK as the request body");
                return;
            }
            if (expect) {
                out.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes("US-ASCII"));
                out.flush();
            }
            json(out, 200, install(in, length));
        } else {
            json(out, 404, "Not found");
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') return b.toString("UTF-8").trim();
            if (b.size() > 8192) return null;
            b.write(c);
        }
        return b.size() > 0 ? b.toString("UTF-8").trim() : null;
    }

    private static void reply(OutputStream out, int code, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes("US-ASCII"));
        out.write(body);
        out.flush();
    }

    private static void json(OutputStream out, int code, String msg) throws IOException {
        reply(out, code, "application/json", ("{\"ok\":" + (code == 200) + ",\"msg\":\"" + esc(msg) + "\"}").getBytes("UTF-8"));
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(' ');
            else b.append(c);
        }
        return b.toString();
    }

    private byte[] asset(String name) throws IOException {
        InputStream in = getAssets().open(name);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return b.toByteArray();
        } finally {
            in.close();
        }
    }

    // ---------------------------------------------------------------- actions

    /** {package, label, activity} of every app the TV can open, by name. */
    private List<String[]> apps() {
        PackageManager pm = getPackageManager();
        HashMap<String, String[]> seen = new HashMap<String, String[]>();
        List<String[]> out = new ArrayList<String[]>();
        for (String category : new String[] {Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER"}) {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(category);
                for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                    ActivityInfo a = r.activityInfo;
                    if (a == null || a.packageName.equals(getPackageName()) || seen.containsKey(a.packageName)) continue;
                    CharSequence label = r.loadLabel(pm);
                    String[] app = {a.packageName, label == null ? a.packageName : label.toString().trim(), a.name};
                    seen.put(a.packageName, app);
                    out.add(app);
                }
            } catch (Exception ignored) { }
        }
        Collections.sort(out, new Comparator<String[]>() {
            @Override public int compare(String[] x, String[] y) { return x[1].compareToIgnoreCase(y[1]); }
        });
        return out;
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private String key(String k) {
        int code;
        if ("up".equals(k)) code = KeyEvent.KEYCODE_DPAD_UP;
        else if ("down".equals(k)) code = KeyEvent.KEYCODE_DPAD_DOWN;
        else if ("left".equals(k)) code = KeyEvent.KEYCODE_DPAD_LEFT;
        else if ("right".equals(k)) code = KeyEvent.KEYCODE_DPAD_RIGHT;
        else if ("ok".equals(k)) code = KeyEvent.KEYCODE_DPAD_CENTER;
        else if ("back".equals(k)) code = KeyEvent.KEYCODE_BACK;
        else if ("menu".equals(k)) code = KeyEvent.KEYCODE_MENU;
        else if ("home".equals(k)) { goHome(); return "Home"; }
        else return "Unknown key";
        try {
            // Android lets an app press keys only in its own screens, so this works while Ghar Home shows.
            new Instrumentation().sendKeyDownUpSync(code);
            return "";
        } catch (Throwable t) {
            // Some Android versions refuse even that: then the home screen moves its own focus.
            HomeActivity home = HomeActivity.showing;
            if (home != null && home.remoteKey(code)) return "";
            // Another app is in front: the accessibility service acts there, when the user has turned it on.
            GharAccess access = GharAccess.active;
            if (access != null) return access.key(code) ? "" : "Nothing to do there";
            GharKeyboard keyboard = GharKeyboard.active;
            if (keyboard != null && keyboard.remoteKey(code)) return "";
            return "To use the remote in other apps, choose the Ghar Home keyboard at Remote on the TV.";
        }
    }

    private String open(String pkg) {
        if (pkg == null) return "No app given";
        for (String[] app : apps()) {
            if (!app[0].equals(pkg)) continue;
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setComponent(new ComponentName(app[0], app[2]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
                return "Opened " + app[1];
            } catch (Exception e) {
                return app[1] + " did not open";
            }
        }
        return "That app is not on the TV";
    }

    /** "open smarttube", "youtube", "settings", "home": opens the app whose name fits best. */
    private String say(String text) {
        if (text == null) return "";
        String q = text.toLowerCase(Locale.US).trim();
        for (String lead : new String[] {"open ", "launch ", "start ", "go to ", "play "}) {
            if (q.startsWith(lead)) q = q.substring(lead.length()).trim();
        }
        q = q.replaceAll("[.!?]+$", "");
        if (q.length() == 0) return "";
        if (q.equals("home")) { goHome(); return "Home"; }
        if (q.equals("settings") || q.equals("setting")) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return "Opened Settings";
            } catch (Exception ignored) { }
        }
        String squeezed = q.replace(" ", "");
        String[] best = null;
        for (String[] app : apps()) {
            String label = app[1].toLowerCase(Locale.US);
            if (label.equals(q)) { best = app; break; }
            if (best == null && (label.contains(q) || label.replace(" ", "").contains(squeezed))) best = app;
        }
        return best == null ? "No app called \"" + text.trim() + "\"" : open(best[0]);
    }

    /** Text from the phone into the text box that is open on the TV, in any app. */
    private String type(String text, String key) {
        GharKeyboard keyboard = GharKeyboard.active;
        if (keyboard != null && keyboard.inputActive) {
            keyboard.remote(text, key);
            return "";
        }
        GharAccess access = GharAccess.active;
        if (text != null && text.length() > 0 && access != null && access.type(text)) return "";
        if (keyboard == null) return "To type from the phone, choose the Ghar Home keyboard at Remote on the TV.";
        return "Select a text box on the TV first.";
    }

    private void refreshBackground() {
        final HomeActivity home = HomeActivity.showing;
        if (home != null) home.runOnUiThread(new Runnable() { @Override public void run() { home.applyBackground(); } });
    }

    private static void save(InputStream in, long length, File to) throws IOException {
        FileOutputStream f = new FileOutputStream(to);
        try {
            byte[] buf = new byte[65536];
            long left = length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("The upload stopped early");
                f.write(buf, 0, n);
                left -= n;
            }
        } finally {
            f.close();
        }
    }

    /** A photo from the phone becomes the wallpaper: cut to the screen's shape and saved at the screen's size. */
    private String wallpaper(InputStream in, long length) {
        File raw = new File(getCacheDir(), "photo.upload");
        try {
            save(in, length, raw);
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(raw.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return "That file is not a photo";
            int sample = 1;
            while (o.outWidth / (sample * 2) >= sw && o.outHeight / (sample * 2) >= sh) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(raw.getPath(), o);
            if (b == null) return "That file is not a photo";
            // Center crop to the screen's shape.
            float scale = Math.max(sw / (float) b.getWidth(), sh / (float) b.getHeight());
            int cw = Math.min(b.getWidth(), Math.round(sw / scale)), ch = Math.min(b.getHeight(), Math.round(sh / scale));
            Bitmap cropped = Bitmap.createBitmap(b, (b.getWidth() - cw) / 2, (b.getHeight() - ch) / 2, cw, ch);
            Bitmap sized = Bitmap.createScaledBitmap(cropped, sw, sh, true);
            File tmp = new File(getFilesDir(), "wallpaper.tmp");
            FileOutputStream f = new FileOutputStream(tmp);
            try { sized.compress(Bitmap.CompressFormat.JPEG, 88, f); } finally { f.close(); }
            if (!tmp.renameTo(HomeActivity.photoFile(this))) return "The photo could not be saved";
            getSharedPreferences("ghar", MODE_PRIVATE).edit().putInt(HomeActivity.KEY_THEME, -1).commit();
            refreshBackground();
            return "Your photo is the wallpaper";
        } catch (Throwable t) {
            return "The photo could not be used";
        } finally {
            raw.delete();
        }
    }

    /** Saves the APK where the TV's installer can read it, and opens the installer on the TV. */
    private String install(InputStream in, long length) {
        try {
            File dir = getExternalCacheDir();
            if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) dir = getCacheDir();
            File apk = new File(dir, "received.apk");
            save(in, length, apk);
            apk.setReadable(true, false);
            if (getPackageManager().getPackageArchiveInfo(apk.getPath(), 0) == null) {
                apk.delete();
                return "That file is not an Android app";
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return "Received. Confirm the install on the TV.";
        } catch (Throwable t) {
            return "Install failed: " + t.getMessage();
        }
    }
}
