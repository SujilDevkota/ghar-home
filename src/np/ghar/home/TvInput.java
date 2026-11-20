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

    /** Every input a TV of this kind can have; each TV says which ones it really has. */
    static final String[] NAMES = {"TV", "Digital TV", "HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "AV", "YPbPr", "VGA"};
    /** MStar's numbers for them, which are also the positions in its EnumInputSource. */
    private static final int[] CODES = {1, 28, 23, 24, 25, 26, 2, 16, 0};
    private static final String MANAGER = "com.mstar.android.tv.TvCommonManager";
    private static final String PLAYER = "com.mstar.tv.tvplayer.ui";

    private TvInput() { }

    private static Object manager() throws Exception {
        return Class.forName(MANAGER).getMethod("getInstance").invoke(null);
    }

    /**
     * The inputs this TV has, as positions in NAMES. Empty on a TV without MStar's classes.
     * MStar's getSourceList is one number per input, above zero where the TV has it.
     */
    static int[] inputs() {
        try {
            Object m = manager();
            int[] list = null;
            try { list = (int[]) m.getClass().getMethod("getSourceList").invoke(m); } catch (Throwable ignored) { }
            int[] out = new int[NAMES.length];
            int n = 0;
            for (int i = 0; i < NAMES.length; i++) {
                boolean has;
                if (list == null || list.length == 0) has = i == 0 || (i >= 2 && i <= 4) || i == 6;     // no list: the usual ones
                else if (list.length > 28) has = list[CODES[i]] > 0;
                else {
                    has = false;
                    for (int v : list) has |= v == CODES[i];
                }
                if (has) out[n++] = i;
            }
            return java.util.Arrays.copyOf(out, n);
        } catch (Throwable t) {
            return new int[0];
        }
    }

    /** The name shown for an input: the one the user gave it ("Caro TV"), or the TV's. */
    static String name(Context c, int i) {
        String own = c.getSharedPreferences("ghar", Context.MODE_PRIVATE).getString("input_name_" + i, "");
        return own.length() > 0 ? own : NAMES[i];
    }

    /** Whether something is plugged into an input and sending a picture, where the TV can tell. */
    static boolean live(int i) {
        try {
            Object m = manager();
            boolean[] on = (boolean[]) m.getClass().getMethod("GetInputSourceStatus").invoke(m);
            return on != null && CODES[i] < on.length && on[CODES[i]];
        } catch (Throwable t) {
            return false;
        }
    }

    /** Switches to an input and shows it. Null when it worked, otherwise what went wrong. */
    static String show(Context c, int i) {
        String problem = null;
        try {
            Object m = manager();
            m.getClass().getMethod("setInputSource", int.class).invoke(m, CODES[i]);
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
                Intent app = new Intent(Intent.ACTION_MAIN);
                app.setClassName(PLAYER, PLAYER + ".RootActivity");
                c.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t2) {
                problem = (problem == null ? "" : problem + "; ") + "player: " + cause(t) + " / " + cause(t2);
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

        try {
            Object m = manager();
            b.append("getSourceList = ").append(java.util.Arrays.toString((int[]) m.getClass().getMethod("getSourceList").invoke(m))).append('\n');
            b.append("GetInputSourceStatus = ").append(java.util.Arrays.toString((boolean[]) m.getClass().getMethod("GetInputSourceStatus").invoke(m))).append('\n');
        } catch (Throwable t) { b.append("lists: ").append(cause(t)).append('\n'); }
        b.append("inputs() =");
        for (int i : inputs()) b.append(' ').append(NAMES[i]).append(live(i) ? "*" : "");
        b.append('\n');

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

        b.append("\n[memory]\n");
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            b.append("TV total ").append(mi.totalMem / 1048576).append(" MB, free ").append(mi.availMem / 1048576)
                    .append(" MB, low = ").append(mi.lowMemory).append(", low line ").append(mi.threshold / 1048576).append(" MB\n");
            b.append("app memory class ").append(am.getMemoryClass()).append(" MB\n");
            android.os.Debug.MemoryInfo me = new android.os.Debug.MemoryInfo();
            android.os.Debug.getMemoryInfo(me);
            b.append("Ghar Home uses ").append(me.getTotalPss() / 1024).append(" MB (java ").append(me.dalvikPss / 1024)
                    .append(", native ").append(me.nativePss / 1024).append(", other ").append(me.otherPss / 1024).append(")\n");
            b.append("Ghar Home in front = ").append(HomeActivity.showing != null).append('\n');
        } catch (Throwable t) { b.append(cause(t)).append('\n'); }

        b.append("\n[audio outputs]\n");
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            b.append("wired headset on = ").append(am.isWiredHeadsetOn()).append('\n');
            b.append("bluetooth a2dp on = ").append(am.isBluetoothA2dpOn()).append('\n');
            b.append("speakerphone on = ").append(am.isSpeakerphoneOn()).append('\n');
            android.content.Intent plug = c.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_HEADSET_PLUG));
            b.append("headset plug = ").append(plug == null ? "never announced" : "state " + plug.getIntExtra("state", -1) + " name " + plug.getStringExtra("name")).append('\n');
            android.bluetooth.BluetoothAdapter bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            b.append("bluetooth = ").append(bt == null ? "none" : bt.isEnabled() ? "on" : "off").append('\n');
        } catch (Throwable t) { b.append(cause(t)).append('\n'); }

        b.append("\n[volume]\n");
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            String[] names = {"voice", "system", "ring", "music", "alarm", "notification"};
            for (int s = 0; s < names.length; s++) b.append(names[s]).append(' ').append(am.getStreamVolume(s)).append('/').append(am.getStreamMaxVolume(s)).append('\n');
        } catch (Throwable t) { b.append(cause(t)).append('\n'); }
        return b.toString();
    }
}
