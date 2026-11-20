package np.ghar.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * The TV's own inputs (aerial/cable, HDMI, AV) and what is plugged in.
 * TV makers keep these outside Android: this TV's board (MStar) has its own classes for them, which are
 * reached by name here so the app still installs and runs on TVs that don't have them.
 */
final class TvInput {

    static final String[] NAMES = {"TV", "HDMI 1", "HDMI 2", "HDMI 3", "AV"};
    /** MStar's numbers for those inputs (TvCommonManager.INPUT_SOURCE_ATV, _HDMI, _HDMI2, _HDMI3, _CVBS). */
    private static final int[] CODES = {1, 23, 24, 25, 2};
    private static final String[] ENUMS = {"E_INPUT_SOURCE_ATV", "E_INPUT_SOURCE_HDMI", "E_INPUT_SOURCE_HDMI2",
            "E_INPUT_SOURCE_HDMI3", "E_INPUT_SOURCE_CVBS"};
    private static final String MANAGER = "com.mstar.android.tv.TvCommonManager";
    private static final String PLAYER = "com.mstar.tv.tvplayer.ui";

    private TvInput() { }

    private static Object manager() throws Exception {
        return Class.forName(MANAGER).getMethod("getInstance").invoke(null);
    }

    /** Whether this TV has inputs this app knows how to switch. */
    static boolean available(Context c) {
        try {
            manager();
            return true;
        } catch (Throwable t) {
            try {
                c.getPackageManager().getPackageInfo(PLAYER, 0);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** Switches to an input and shows it. Null when it worked, otherwise what went wrong. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String show(Context c, int i) {
        String problem = null;
        try {
            Object m = manager();
            try {
                m.getClass().getMethod("setInputSource", int.class).invoke(m, CODES[i]);
            } catch (NoSuchMethodException old) {
                Class en = Class.forName("com.mstar.android.tvapi.common.vo.TvOsType$EnumInputSource");
                m.getClass().getMethod("setInputSource", en).invoke(m, Enum.valueOf(en, ENUMS[i]));
            }
        } catch (Throwable t) {
            problem = "switch: " + cause(t);
        }
        try {
            Intent player = new Intent("com.mstar.android.intent.action.START_TV_PLAYER");
            player.putExtra("task_tag", "input_source_changed");
            player.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(player);
        } catch (Throwable t) {
            try {
                Intent app = c.getPackageManager().getLaunchIntentForPackage(PLAYER);
                c.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t2) {
                problem = (problem == null ? "" : problem + "; ") + "player: " + cause(t);
            }
        }
        return problem;
    }

    private static String cause(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " " + t.getMessage());
    }

    /** Whether a USB drive (or a card) is plugged in, from the list of mounted disks. */
    static boolean usb() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/mounts"));
            try {
                for (String line; (line = r.readLine()) != null; ) {
                    String[] f = line.split(" ");
                    if (f.length < 3) continue;
                    String at = f[1].toLowerCase(Locale.US), fs = f[2];
                    if (at.contains("emulated") || at.contains("/asec") || at.contains("/obb") || at.contains("secure")
                            || at.endsWith("/sdcard") || at.endsWith("/sdcard0") || at.contains("shell")) continue;
                    boolean disk = fs.equals("vfat") || fs.equals("ntfs") || fs.equals("exfat") || fs.equals("fuseblk")
                            || fs.equals("ntfs3g") || fs.equals("tntfs") || fs.equals("texfat");
                    if (disk && (at.contains("usb") || at.contains("udisk") || at.startsWith("/mnt/") || at.startsWith("/storage/"))) return true;
                }
            } finally {
                r.close();
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** A plain-text report on how this TV handles inputs, for adapting the app to it. Read over the PIN-protected link. */
    static String report(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("model ").append(Build.MODEL).append(" | board ").append(Build.BOARD).append(" | hardware ").append(Build.HARDWARE)
                .append(" | build ").append(Build.DISPLAY).append(" | sdk ").append(Build.VERSION.SDK_INT).append('\n');
        PackageManager pm = c.getPackageManager();
        try {
            b.append("\n[shared libraries]\n");
            String[] libs = pm.getSystemSharedLibraryNames();
            if (libs != null) for (String l : libs) b.append(l).append('\n');
        } catch (Throwable t) { b.append(cause(t)).append('\n'); }

        b.append("\n[classes]\n");
        String[] classes = {MANAGER, "com.mstar.android.tvapi.common.TvManager", "com.mstar.android.tv.TvChannelManager",
                "com.mstar.android.tvapi.common.vo.TvOsType$EnumInputSource", "com.mstar.android.tv.TvS3DManager",
                "com.cultraview.tv.CtvCommonManager", "com.cultraview.tv.CtvChannelManager", "com.cultraview.tv.common.vo.CtvEnumInputSource",
                "com.cultraview.tv.CtvPictureManager", "android.media.tv.TvInputManager"};
        for (String name : classes) {
            try {
                Class<?> k = Class.forName(name);
                b.append("found ").append(name).append('\n');
                if (k.isEnum()) {
                    b.append("   values:");
                    for (Object v : k.getEnumConstants()) b.append(' ').append(v);
                    b.append('\n');
                }
                for (Method m : k.getMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (n.contains("source") || n.contains("getinstance")) {
                        b.append("   ").append(m.getName()).append('(');
                        Class<?>[] ps = m.getParameterTypes();
                        for (int i = 0; i < ps.length; i++) b.append(i > 0 ? "," : "").append(ps[i].getSimpleName());
                        b.append(") -> ").append(m.getReturnType().getSimpleName()).append('\n');
                    }
                }
            } catch (Throwable t) {
                b.append("no    ").append(name).append("  (").append(t.getClass().getSimpleName()).append(")\n");
            }
        }
        try {
            Object m = manager();
            for (String get : new String[] {"getCurrentTvInputSource", "getCurrentInputSource"}) {
                try { b.append(get).append(" = ").append(m.getClass().getMethod(get).invoke(m)).append('\n'); } catch (Throwable t) { b.append(get).append(": ").append(cause(t)).append('\n'); }
            }
        } catch (Throwable t) { b.append("manager: ").append(cause(t)).append('\n'); }

        b.append("\n[tv packages]\n");
        try {
            for (PackageInfo p : pm.getInstalledPackages(0)) {
                String n = p.packageName.toLowerCase(Locale.US);
                if (!(n.contains("mstar") || n.contains("cultraview") || n.contains("ctv") || n.contains(".cv.") || n.contains("assem")
                        || n.contains("tvplayer") || n.contains("source") || n.contains("launcher") || n.contains("hdmi"))) continue;
                b.append(p.packageName).append("  ").append(p.versionName).append('\n');
                try {
                    ActivityInfo[] acts = pm.getPackageInfo(p.packageName, PackageManager.GET_ACTIVITIES).activities;
                    if (acts != null) for (ActivityInfo a : acts) b.append("   ").append(a.exported ? "open   " : "closed ").append(a.name).append('\n');
                } catch (Throwable t) { b.append("   ").append(cause(t)).append('\n'); }
            }
        } catch (Throwable t) { b.append(cause(t)).append('\n'); }

        b.append("\n[mounted disks]\n");
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/mounts"));
            for (String line; (line = r.readLine()) != null; ) {
                if (line.contains("/mnt") || line.contains("/storage") || line.contains("usb")) b.append(line).append('\n');
            }
            r.close();
        } catch (Throwable t) { b.append(cause(t)).append('\n'); }
        b.append("usb() = ").append(usb()).append('\n');

        b.append("\n[volume]\n");
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            String[] names = {"voice", "system", "ring", "music", "alarm", "notification"};
            for (int s = 0; s < names.length; s++) b.append(names[s]).append(' ').append(am.getStreamVolume(s)).append('/').append(am.getStreamMaxVolume(s)).append('\n');
        } catch (Throwable t) { b.append(cause(t)).append('\n'); }
        return b.toString();
    }
}
