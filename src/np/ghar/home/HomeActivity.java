package np.ghar.home;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Ghar Home: a small, clean home screen for old Android TVs (Android 4.4 and newer).
 * One Activity, plain Views built in code, no libraries. Everything here must exist on API 19.
 */
public class HomeActivity extends Activity {

    private static final String PREFS = "ghar";
    private static final String KEY_FAVS = "favs";
    private static final String KEY_CRASHES = "crashes";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_USES = "uses";
    private static final String KEY_SOUND = "sound";
    static final String KEY_THEME = "theme";
    private static final String KEY_LANG = "lang";
    private static final String KEY_CLUTTER = "clutter_hidden";
    /** System entries nobody opens from a home screen. Hidden once; "Hidden" brings any of them back. */
    private static final String[] CLUTTER = {"com.android.dummyactivity", "com.android.videoeditor", "com.android.contacts",
            "com.google.android.gms", "com.ctv.app", "com.assem.launcher", "com.android.providers.downloads.ui"};

    /** Hand-picked backgrounds: three colors from the top left to the bottom right, and a glow in the corner. */
    static final String[] THEME_NAMES = {"Night sky", "Himalayan dawn", "Forest", "Sunset", "Deep ocean", "Rhododendron", "Charcoal"};
    private static final int[][] THEMES = {
            {0xFF0B1220, 0xFF111B33, 0xFF0E2A3A, 0x552F6BFF},
            {0xFF1A1436, 0xFF3A2352, 0xFF7A3B5E, 0x55FF9E7A},
            {0xFF071A14, 0xFF0F2E24, 0xFF14463A, 0x5538C98A},
            {0xFF1F0F1E, 0xFF4A1D2C, 0xFF8A3B22, 0x66FFB03A},
            {0xFF04121F, 0xFF07304A, 0xFF0A4D63, 0x5530C8E8},
            {0xFF22091A, 0xFF4D0F2E, 0xFF7A1638, 0x55FF5C8A},
            {0xFF0A0A0C, 0xFF141418, 0xFF1E1E24, 0x33FFFFFF},
    };
    private static final String KEY_OLD_YOUTUBE = "old_youtube_hidden";
    /** Google's YouTube apps for TVs, which no longer run on old Android. */
    private static final String[] OLD_YOUTUBE = {"com.google.android.youtube.tv", "com.google.android.youtube.googletv"};
    private static final String KEY_SMARTTUBE = "smarttube_added";
    private static final int COLS = 6;

    /** Apps put in Favourites on the first start, when the TV has them. */
    private static final String SMARTTUBE = "org.smarttube.stable";
    private static final String[] FIRST_FAVS = {
            SMARTTUBE, "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.videomanager",
            "com.google.android.youtube.tv", "com.google.android.youtube", "com.netflix.mediaclient",
            "com.netflix.ninja", "org.videolan.vlc", "org.xbmc.kodi", "com.android.browser", "com.android.chrome",
    };

    static class AppItem {
        String pkg;
        String label;
        ComponentName component;
        Bitmap icon;
        Drawable banner;
        int color;
    }

    /** The home screen while it is in front, for the phone remote. */
    static volatile HomeActivity showing;

    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private int u;          // 1% of the screen height, in pixels: every size is a multiple of it
    private int screenW;

    private final List<AppItem> apps = new ArrayList<AppItem>();
    private final List<String> favs = new ArrayList<String>();
    private final List<String> hidden = new ArrayList<String>();
    private boolean entered;     // the entrance animation plays once
    private final HashMap<String, Integer> uses = new HashMap<String, Integer>();   // opens per app
    private boolean usesChanged;
    private boolean weatherBusy;
    private WeatherArt art;
    private Icon volumeIcon;
    private ImageView bluetoothIcon, wiredIcon, wifiView;
    private Icon btIcon, wifiIcon;
    private int statusTick;
    private TextView[] channelTiles;
    private final String[] liveVideo = new String[Channels.NAMES.length];   // the live stream of each channel, when it has one
    private long liveChecked;
    private boolean liveBusy;
    private int shownVolume = -1;
    private boolean quiet;
    private AudioTrack tickSound, chimeSound;
    private FrameLayout root;
    private View loadingView;
    private long loadingSince;
    private int shownTheme = -2;
    private long shownPhoto;
    private long weatherTried;

    private ScrollView scroll;
    private LinearLayout favRow;
    private LinearLayout grid;
    private TextView clock, date, net, bs, weatherBig, weatherSmall, usbText, volumeText;
    private String focusPkg;
    private boolean focusInFavs = true;
    private boolean loading;
    private int lastMinute = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            showVolume();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable settled = new Runnable() {
        @Override public void run() {
            prefs.edit().putInt(KEY_CRASHES, 0).apply();
        }
    };

    private final BroadcastReceiver plugged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            showStatus();
            handler.postDelayed(new Runnable() { @Override public void run() { showStatus(); } }, 1500);   // a disk takes a moment
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        installSafetyNet();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        u = Math.max(4, Math.min(dm.widthPixels, dm.heightPixels) / 100);

        // After two crashes in a row, show a plain screen with a way out instead of trying again.
        if (prefs.getInt(KEY_CRASHES, 0) >= 2) {
            prefs.edit().putInt(KEY_CRASHES, 0).apply();
            showSafeScreen();
            return;
        }
        T.ne = "ne".equals(prefs.getString(KEY_LANG, "en")) && T.fontOk();
        try {
            readFavs();
            buildScreen();
            try { startService(new Intent(this, LinkService.class)); } catch (Exception ignored) { }
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_CHANGED);
            f.addDataScheme("package");
            registerReceiver(packages, f);
            // Volume keys and a USB drive going in or out change the status row at once.
            registerReceiver(plugged, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
            IntentFilter disks = new IntentFilter();
            disks.addAction(Intent.ACTION_MEDIA_MOUNTED);
            disks.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            disks.addAction(Intent.ACTION_MEDIA_EJECT);
            disks.addAction(Intent.ACTION_MEDIA_REMOVED);
            disks.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
            disks.addDataScheme("file");
            registerReceiver(plugged, disks);
            loadApps();
        } catch (Throwable t) {
            showSafeScreen();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        showing = this;
        GharAccess.letOriginal = false;      // back here: the Home button is ours again
        applyBackground();
        if (usesChanged && favRow != null && !apps.isEmpty()) {    // back from an app: the order may have changed
            usesChanged = false;
            fill();
        }
        lastMinute = -1;
        showStatus();
        handler.removeCallbacks(tick);
        handler.post(tick);
        handler.removeCallbacks(settled);
        handler.postDelayed(settled, 10000);
    }

    @Override protected void onPause() {
        super.onPause();
        if (showing == this) showing = null;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(settled);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packages); } catch (Exception ignored) { }
        try { unregisterReceiver(plugged); } catch (Exception ignored) { }
        try { if (tickSound != null) tickSound.release(); } catch (Exception ignored) { }
        try { if (chimeSound != null) chimeSound.release(); } catch (Exception ignored) { }
    }

    /** Another app is in front, or the TV is short of memory: give back what is cheap to make again. */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN) return;
        try {
            if (tickSound != null) { tickSound.release(); tickSound = null; }
            if (chimeSound != null) { chimeSound.release(); chimeSound = null; }
        } catch (Throwable ignored) { }
        if (root != null && prefs.getInt(KEY_THEME, 0) != -1) {      // a theme is repainted in a moment; a photo is kept
            root.setBackgroundDrawable(null);
            root.setBackgroundColor(0xFF0B1220);
            shownTheme = -2;
        }
        System.gc();
    }

    /** Home pressed while the home screen is already showing: back to the top. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusFirst();
    }

    @Override public void onBackPressed() {
        focusFirst();   // a home screen has nowhere to go back to
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View v = getCurrentFocus();
            if (v != null && v.getTag() instanceof AppItem) {
                showOptions((AppItem) v.getTag());
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /** A key from the phone remote, acted on by hand: moves the focus, presses OK, opens the options. */
    boolean remoteKey(final int code) {
        if (favRow == null) return false;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                View v = getCurrentFocus();
                if (v == null) { focusFirst(); return; }
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                        : code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : code == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
                if (dir != 0) {
                    View next = v.focusSearch(dir);
                    if (next != null) next.requestFocus();
                } else if (code == KeyEvent.KEYCODE_DPAD_CENTER) {
                    v.performClick();
                } else if (code == KeyEvent.KEYCODE_MENU && v.getTag() instanceof AppItem) {
                    showOptions((AppItem) v.getTag());
                } else if (code == KeyEvent.KEYCODE_BACK) {
                    focusFirst();
                }
            }
        });
        return true;
    }

    // ---------------------------------------------------------------- safety net

    /** A crash must never leave the TV without a home screen: hand it back to the original one. */
    private void installSafetyNet() {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable error) {
                try {
                    prefs.edit().putInt(KEY_CRASHES, prefs.getInt(KEY_CRASHES, 0) + 1).commit();
                    openOriginalHome();
                } catch (Throwable ignored) { }
                if (old != null) old.uncaughtException(thread, error);
                else System.exit(10);
            }
        });
    }

    /** The TV's own home screen: any other app that answers the Home button. */
    private boolean openOriginalHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = getPackageManager().queryIntentActivities(home, 0);
            for (ResolveInfo r : list) {
                ActivityInfo a = r.activityInfo;
                if (a == null || a.packageName.equals(getPackageName())) continue;
                if (a.packageName.equals("com.android.settings")) continue;   // Android's placeholder home
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                i.setComponent(new ComponentName(a.packageName, a.name));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                GharAccess.letOriginal = true;      // asked for: don't jump straight back here
                startActivity(i);
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Settings did not open");
        }
    }

    private void showSafeScreen() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF0B1220);
        TextView t = text("Ghar Home had a problem.\nYou can open the TV's original home screen or Settings.", 3f, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 4 * u, 0, 0);
        View first = pill("Original home", new Runnable() { @Override public void run() { openOriginalHome(); } });
        row.addView(first);
        row.addView(pill("Settings", new Runnable() { @Override public void run() { openSettings(); } }));
        row.addView(pill("Try again", new Runnable() { @Override public void run() { recreate(); } }));
        box.addView(row);
        setContentView(box);
        first.requestFocus();
    }

    // ---------------------------------------------------------------- the screen

    private void buildScreen() {
        int side = side();

        root = new FrameLayout(this);
        applyBackground();

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        art = new WeatherArt(this);
        root.addView(art, new FrameLayout.LayoutParams(60 * u, 42 * u, Gravity.TOP | Gravity.RIGHT));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 5 * u, 0, 6 * u);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        // Header: the time, the date in both calendars and the next festival; the weather on the right.
        LinearLayout header = new LinearLayout(this);
        header.setPadding(side, 0, side, 0);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = text("", 8f, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        date = text("", 2.5f, 0xCCFFFFFF);
        date.setFocusable(true);
        date.setClickable(true);
        date.setPadding(u, u / 2, u, u / 2);
        focusLook(date, 0x00FFFFFF, 2 * u, 1.0f);
        date.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPatro(); }
        });
        bs = text("", 2.4f, 0xFFFFD27A);
        bs.setPadding(0, u / 2, 0, 0);
        left.addView(clock);
        LinearLayout.LayoutParams dateAt = new LinearLayout.LayoutParams(-2, -2);
        dateAt.leftMargin = -u;      // its text lines up with the clock, the focus outline sits outside
        left.addView(date, dateAt);
        left.addView(bs);
        header.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        weatherBig = text("", 6f, Color.WHITE);
        weatherBig.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherSmall = text("", 2.4f, 0xCCFFFFFF);
        right.addView(weatherBig);
        right.addView(weatherSmall);
        header.addView(right);
        page.addView(header);

        // Status row: the TV's own inputs on the left; what is plugged in and the volume on the right.
        LinearLayout status = new LinearLayout(this);
        status.setPadding(side - u / 2, 2 * u, side, 0);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setClipChildren(false);
        status.setClipToPadding(false);
        try {
            for (final int input : TvInput.inputs()) {
                TextView button = (TextView) pill(TvInput.name(this, input), new Runnable() {
                    @Override public void run() {
                        String problem = TvInput.show(HomeActivity.this, input);
                        if (problem != null) Toast.makeText(HomeActivity.this, problem, Toast.LENGTH_LONG).show();
                    }
                });
                // A green dot marks an input with something plugged in and switched on, such as a set-top box.
                if (TvInput.live(input)) {
                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setColor(0xFF3DDC97);
                    dot.setSize((int) (1.3f * u), (int) (1.3f * u));
                    button.setCompoundDrawablesWithIntrinsicBounds(null, null, dot, null);
                    button.setCompoundDrawablePadding((int) (1.2f * u));
                }
                status.addView(button);
            }
        } catch (Throwable ignored) { }      // a TV whose inputs this app can't reach: no buttons
        View gap = new View(this);
        status.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        usbText = (TextView) pill("USB", new Runnable() {
            @Override public void run() {
                // The TV's own file manager, where it has one; otherwise there is nothing to open.
                for (String pkg : new String[] {"com.cultraview.newfilemanager", "com.android.documentsui"}) {
                    Intent files = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (files == null) continue;
                    try {
                        startActivity(files.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return;
                    } catch (Exception ignored) { }
                }
            }
        });
        usbText.setTextColor(0xFF9FE8C0);
        usbText.setCompoundDrawablesWithIntrinsicBounds(new Icon(Icon.USB, (int) (2.4f * u), 0xFF9FE8C0), null, null, null);
        usbText.setCompoundDrawablePadding(u);
        usbText.setVisibility(View.GONE);
        ((LinearLayout.LayoutParams) usbText.getLayoutParams()).rightMargin = 3 * u;
        status.addView(usbText);
        volumeText = text("", 2.3f, 0xCCFFFFFF);
        wifiView = new ImageView(this);
        wifiIcon = new Icon(Icon.WIFI, (int) (3.2f * u), 0xCCFFFFFF);
        wifiView.setImageDrawable(wifiIcon);
        wifiView.setVisibility(View.GONE);
        LinearLayout.LayoutParams wifiAt = new LinearLayout.LayoutParams((int) (3.2f * u), (int) (3.2f * u));
        wifiAt.rightMargin = 2 * u;
        status.addView(wifiView, wifiAt);
        bluetoothIcon = new ImageView(this);
        btIcon = new Icon(Icon.BLUETOOTH, (int) (3.2f * u), 0xFF7FB2FF);
        bluetoothIcon.setImageDrawable(btIcon);
        bluetoothIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams iconAt = new LinearLayout.LayoutParams((int) (3.2f * u), (int) (3.2f * u));
        iconAt.rightMargin = 2 * u;
        status.addView(bluetoothIcon, iconAt);
        wiredIcon = new ImageView(this);
        wiredIcon.setImageDrawable(new Icon(Icon.WIRED, (int) (3.2f * u), 0xCCFFFFFF));
        wiredIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams wiredAt = new LinearLayout.LayoutParams((int) (3.2f * u), (int) (3.2f * u));
        wiredAt.rightMargin = 2 * u;
        status.addView(wiredIcon, wiredAt);
        volumeIcon = new Icon(Icon.SPEAKER, (int) (3.4f * u), 0xCCFFFFFF);
        volumeText.setCompoundDrawablesWithIntrinsicBounds(volumeIcon, null, null, null);
        volumeText.setCompoundDrawablePadding(u);
        volumeText.setGravity(Gravity.CENTER_VERTICAL);
        status.addView(volumeText);
        page.addView(status);

        page.addView(sectionTitle(T.t("Favourites"), side));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        favRow = new LinearLayout(this);
        favRow.setPadding(side, 2 * u, side, 2 * u);
        favRow.setClipChildren(false);
        favRow.setClipToPadding(false);
        hs.addView(favRow, new HorizontalScrollView.LayoutParams(-2, -2));
        page.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        // Nepali TV: each broadcaster's own YouTube channel, live when it is streaming.
        page.addView(sectionTitle(T.t("Nepali TV"), side));
        HorizontalScrollView tv = new HorizontalScrollView(this);
        tv.setHorizontalScrollBarEnabled(false);
        tv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tv.setClipChildren(false);
        LinearLayout tvRow = new LinearLayout(this);
        tvRow.setPadding(side, 2 * u, side, 2 * u);
        tvRow.setClipChildren(false);
        tvRow.setClipToPadding(false);
        channelTiles = new TextView[Channels.NAMES.length];
        for (int i = 0; i < Channels.NAMES.length; i++) {
            final int channel = i;
            TextView t = text(Channels.NAMES[i], 2.5f, Color.WHITE);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            t.setPadding(2 * u, 0, 2 * u, 0);
            t.setFocusable(true);
            t.setClickable(true);
            focusLook(t, Channels.COLORS[i], 2 * u, 1.06f);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openChannel(channel); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(25 * u, 11 * u);
            lp.rightMargin = (int) (1.6f * u);
            tvRow.addView(t, lp);
            channelTiles[i] = t;
        }
        tv.addView(tvRow, new HorizontalScrollView.LayoutParams(-2, -2));
        page.addView(tv, new LinearLayout.LayoutParams(-1, -2));

        page.addView(sectionTitle(T.t("All apps"), side));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(side, u, side, 0);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        page.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        // Footer: the things needed now and then.
        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(side - u / 2, 3 * u, side, 0);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setClipChildren(false);
        footer.setClipToPadding(false);
        footer.addView(pill("Remote", new Runnable() { @Override public void run() { showRemote(); } }));
        footer.addView(pill("Patro", new Runnable() { @Override public void run() { showPatro(); } }));
        footer.addView(pill("Apps", new Runnable() { @Override public void run() { showApps(); } }));
        footer.addView(pill("Settings", new Runnable() { @Override public void run() { openSettings(); } }));
        footer.addView(pill("TV home", new Runnable() {
            @Override public void run() {
                if (!openOriginalHome()) toast("No other home screen found");
            }
        }));
        footer.addView(pill("Options", new Runnable() { @Override public void run() { showOptions(); } }));
        // The language in use, as its flag: OK switches to the other one.
        TextView flag = (TextView) pill("", new Runnable() { @Override public void run() { switchLanguage(); } });
        flag.setCompoundDrawablesWithIntrinsicBounds(new Icon(T.ne ? Icon.FLAG_NEPAL : Icon.FLAG_UK, (int) (2.9f * u), Color.WHITE), null, null, null);
        flag.setCompoundDrawablePadding(0);
        flag.setPadding(2 * u, (int) (1.3f * u), 2 * u, (int) (1.3f * u));
        footer.addView(flag);
        net = text("", 2.2f, 0x99FFFFFF);
        net.setGravity(Gravity.RIGHT);
        net.setSingleLine(true);
        footer.addView(net, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(footer);

        // Loading: shown over everything until the apps are on screen.
        LoadingView loading = new LoadingView(this, u);
        root.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        loadingView = loading;
        loadingSince = android.os.SystemClock.uptimeMillis();

        setContentView(root);
        updateClock();
    }

    static File photoFile(Context c) { return new File(c.getFilesDir(), "wallpaper.jpg"); }

    /** The chosen background: one of the themes, or the photo sent from a phone under a dark veil. */
    void applyBackground() {
        if (root == null) return;
        int theme = prefs.getInt(KEY_THEME, 0);
        File photo = photoFile(this);
        if (theme == shownTheme && (theme != -1 || photo.lastModified() == shownPhoto)) return;
        Drawable back = null;
        if (theme == -1) {
            try {
                BitmapFactory.Options small = new BitmapFactory.Options();
                small.inSampleSize = 2;
                small.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeFile(photo.getPath(), small);
                if (b != null) {
                    GradientDrawable veil = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[] {0xA6000000, 0x73000000, 0xBF000000});
                    back = new LayerDrawable(new Drawable[] {new BitmapDrawable(getResources(), b), veil});
                }
            } catch (Throwable ignored) { }     // a photo too big for this TV: the theme below shows
        }
        if (back == null) {
            int[] t = THEMES[theme >= 0 && theme < THEMES.length ? theme : 0];
            GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {t[0], t[1], t[2]});
            GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {t[3], t[3] & 0x00FFFFFF});
            glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glow.setGradientRadius(screenW * 0.45f);
            glow.setGradientCenter(0.88f, 0.05f);
            LayerDrawable painted = new LayerDrawable(new Drawable[] {sky, glow});
            back = painted;
            try {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int w = Math.max(dm.widthPixels, dm.heightPixels), h = Math.min(dm.widthPixels, dm.heightPixels);
                Bitmap once = Bitmap.createBitmap(w / 4, h / 4, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(once);
                canvas.scale(0.25f, 0.25f);
                painted.setBounds(0, 0, w, h);
                painted.draw(canvas);
                BitmapDrawable picture = new BitmapDrawable(getResources(), once);
                picture.setFilterBitmap(true);
                back = picture;
            } catch (Throwable ignored) { }     // no memory for it: the gradients themselves are drawn
        }
        root.setBackgroundDrawable(back);
        shownTheme = theme;
        shownPhoto = photo.lastModified();
    }

    private void showWallpapers() {
        final boolean photo = photoFile(this).exists();
        String[] names = new String[THEME_NAMES.length + (photo ? 1 : 0)];
        for (int i = 0; i < THEME_NAMES.length; i++) names[i] = T.t(THEME_NAMES[i]);
        if (photo) names[THEME_NAMES.length] = T.t("Photo from phone");
        new AlertDialog.Builder(this).setTitle(T.t("Wallpaper"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().putInt(KEY_THEME, which < THEME_NAMES.length ? which : -1).apply();
                        applyBackground();
                    }
                }).show();
    }

    private void switchLanguage() {
        if (!T.fontOk()) {
            toast("This TV has no Nepali font");
            return;
        }
        prefs.edit().putString(KEY_LANG, T.ne ? "en" : "ne").commit();
        recreate();
    }

    /** Wallpaper and sounds in one menu, so the footer stays short. */
    private void showOptions() {
        final boolean sound = prefs.getBoolean(KEY_SOUND, true);
        String[] names = {T.t("Wallpaper"), T.t(sound ? "Sound: on" : "Sound: off")};
        new AlertDialog.Builder(this).setTitle(T.t("Options"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showWallpapers();
                            return;
                        }
                        prefs.edit().putBoolean(KEY_SOUND, !sound).apply();
                        tick();
                        showOptions();      // shown again with the new state, so the change is seen
                    }
                }).show();
    }

    /** The Nepali calendar, a month at a time. */
    private void showPatro() {
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final PatroView v = new PatroView(this, u);
        d.setContentView(v);
        d.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override public boolean onKey(DialogInterface dialog, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) { v.move(-1); tick(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN) { v.move(1); tick(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) { d.dismiss(); return true; }
                return false;
            }
        });
        d.show();
    }

    /** The margin at the left and right: old TVs cut a little off each edge. */
    private int side() { return screenW * 6 / 100; }

    private TextView sectionTitle(String s, int side) {
        TextView t = text(s, 2.6f, 0xCCFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(side, 4 * u, side, 0);
        return t;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable shape(int fill, int radius, int stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke > 0) g.setStroke(stroke, strokeColor);
        return g;
    }

    /** Focus look shared by every button and tile: a white outline and a small zoom. */
    private void focusLook(final View v, final int fill, final int radius, final float zoom) {
        final GradientDrawable normal = shape(fill, radius, 0, 0);
        final GradientDrawable focused = shape(lighten(fill), radius, Math.max(2, u / 3), Color.WHITE);
        v.setBackgroundDrawable(normal);
        // Android 8+ paints its own tint over a focused view; ours is the outline.
        if (Build.VERSION.SDK_INT >= 26) v.setDefaultFocusHighlightEnabled(false);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.setBackgroundDrawable(has ? focused : normal);
                view.animate().scaleX(has ? zoom : 1f).scaleY(has ? zoom : 1f)
                        .translationY(has && zoom > 1.05f ? -0.6f * u : 0f)
                        .setInterpolator(has ? new OvershootInterpolator(2.5f) : new DecelerateInterpolator())
                        .setDuration(has ? 220 : 140).withLayer().start();
                if (has && view.getTag() instanceof AppItem) {
                    focusPkg = ((AppItem) view.getTag()).pkg;
                    focusInFavs = view.getParent() == favRow;
                }
                if (has && !quiet) tick();
                // Back on Favourites: show the whole top of the page, clock and weather included.
                if (has && view.getParent() == favRow && scroll.getScrollY() != 0) {
                    scroll.post(new Runnable() { @Override public void run() { scroll.smoothScrollTo(0, 0); } });
                }
            }
        });
    }

    private View pill(String label, final Runnable action) {
        TextView t = text(T.t(label), 2.3f, Color.WHITE);
        t.setPadding(3 * u, (int) (1.3f * u), 3 * u, (int) (1.3f * u));
        t.setFocusable(true);
        t.setClickable(true);
        focusLook(t, 0x1FFFFFFF, 4 * u, 1.05f);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(u / 2, 0, u, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private View tile(final AppItem app, boolean fav, int w, int h) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(app);
        focusLook(card, fav ? app.color : 0x14FFFFFF, 2 * u, fav ? 1.06f : 1.08f);

        if (fav && app.banner != null) {
            ImageView b = new ImageView(this);
            b.setImageDrawable(app.banner);
            b.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int pad = Math.max(2, u / 3);
            card.setPadding(pad, pad, pad, pad);
            card.addView(b, new LinearLayout.LayoutParams(-1, -1));
        } else {
            int size = (fav ? 9 : 8) * u;
            ImageView icon = new ImageView(this);
            icon.setImageBitmap(app.icon);
            card.addView(icon, new LinearLayout.LayoutParams(size, size));
            TextView name = text(app.label, fav ? 2.4f : 2.1f, Color.WHITE);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setPadding(u, u, u, 0);
            card.addView(name, new LinearLayout.LayoutParams(-1, -2));
        }

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(app); }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showOptions(app); return true; }
        });
        card.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return card;
    }

    /** Fills Favourites and All apps from the loaded list, keeping the focus where it was. */
    private void fill() {
        favRow.removeAllViews();
        grid.removeAllViews();
        int gap = (int) (1.6f * u);

        HashMap<String, AppItem> byPkg = new HashMap<String, AppItem>();
        List<AppItem> shown = new ArrayList<AppItem>();
        for (AppItem a : apps) {
            if (hidden.contains(a.pkg)) continue;
            byPkg.put(a.pkg, a);
            shown.add(a);
        }
        // Most opened first; a tie keeps the alphabet.
        Collections.sort(shown, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) {
                int d = used(y.pkg) - used(x.pkg);
                return d != 0 ? d : x.label.compareToIgnoreCase(y.label);
            }
        });

        View wanted = null, firstFav = null;
        int favH = 19 * u, favW = favH * 16 / 9;
        for (String pkg : favs) {
            AppItem a = byPkg.get(pkg);
            if (a == null) continue;     // uninstalled: kept in the list in case it comes back
            View v = tile(a, true, favW, favH);
            ((LinearLayout.LayoutParams) v.getLayoutParams()).rightMargin = gap;
            favRow.addView(v);
            if (firstFav == null) firstFav = v;
            if (focusInFavs && a.pkg.equals(focusPkg)) wanted = v;
        }
        if (firstFav == null) {
            TextView hint = text(T.t("Hold OK on an app below to add it here."), 2.4f, 0x99FFFFFF);
            hint.setPadding(0, 3 * u, 0, 3 * u);
            favRow.addView(hint);
        }

        int side = side();
        int w = (screenW - 2 * side - (COLS - 1) * gap) / COLS, h = 15 * u;
        LinearLayout row = null;
        View firstApp = null;
        for (int i = 0; i < shown.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = gap;
                grid.addView(row, lp);
            }
            AppItem a = shown.get(i);
            View v = tile(a, false, w, h);
            if (i % COLS != COLS - 1) ((LinearLayout.LayoutParams) v.getLayoutParams()).rightMargin = gap;
            row.addView(v);
            if (firstApp == null) firstApp = v;
            if (!focusInFavs && a.pkg.equals(focusPkg)) wanted = v;
        }

        if (wanted == null) wanted = firstFav != null ? firstFav : firstApp;
        quiet = true;      // putting the focus back is not a move: no sound
        if (wanted != null) wanted.requestFocus();
        quiet = false;
        if (!entered) {
            entered = true;
            final View loading = loadingView;
            loadingView = null;
            if (loading != null) {
                // On screen for at least a second and a bit, so it reads as an animation and not a flicker.
                long onScreen = android.os.SystemClock.uptimeMillis() - loadingSince;
                loading.animate().alpha(0f).setDuration(400).setStartDelay(Math.max(250, 1300 - onScreen)).withEndAction(new Runnable() {
                    @Override public void run() {
                        ((LoadingView) loading).stop();
                        root.removeView(loading);
                    }
                }).start();
            }
            chime();
            enter();
        }
    }

    /** First show: the favourites swing in one after another, and the grid rows fade up. */
    private void enter() {
        for (int i = 0; i < favRow.getChildCount(); i++) {
            View v = favRow.getChildAt(i);
            v.setCameraDistance(screenW * 4f);
            v.setAlpha(0f);
            v.setRotationY(35f);
            v.setTranslationX(8 * u);
            v.animate().alpha(1f).rotationY(0f).translationX(0f).setStartDelay(80 + i * 70)
                    .setDuration(420).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        for (int i = 0; i < grid.getChildCount(); i++) {
            View row = grid.getChildAt(i);
            row.setAlpha(0f);
            row.setTranslationY(4 * u);
            row.animate().alpha(1f).translationY(0f).setStartDelay(300 + i * 90).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    private void focusFirst() {
        if (scroll == null || favRow == null) return;
        scroll.smoothScrollTo(0, 0);
        View v = favRow.getChildCount() > 0 ? favRow.getChildAt(0) : null;
        if (v == null || !v.isFocusable()) {
            v = grid.getChildCount() > 0 ? ((ViewGroup) grid.getChildAt(0)).getChildAt(0) : null;
        }
        if (v != null) v.requestFocus();
    }

    // ---------------------------------------------------------------- clock and status

    private void updateClock() {
        if (clock == null) return;
        Calendar c = Calendar.getInstance();
        int minute = c.get(Calendar.MINUTE);
        if (minute == lastMinute) return;
        lastMinute = minute;
        Date now = c.getTime();
        clock.setText(android.text.format.DateFormat.getTimeFormat(this).format(now));
        String miti = NepaliDate.format(c, T.ne), festival = NepaliDate.festival(c, T.ne);
        CharSequence english = android.text.format.DateFormat.format("EEEE, d MMMM", now);
        if (T.ne) {
            String weekday = NepaliDate.DAYS_NE[NepaliDate.weekday(NepaliDate.today(c))] + "\u092C\u093E\u0930";       // ...बार
            String adDate = T.num("" + c.get(Calendar.DAY_OF_MONTH)) + " " + NepaliDate.AD_MONTHS_NE[c.get(Calendar.MONTH)];
            date.setText(weekday + ", " + (miti == null ? adDate : miti + "   \u2022   " + adDate));
        } else {
            date.setText(miti == null ? english : english + "   \u2022   " + miti);
        }
        bs.setText(festival == null ? "" : festival);
        bs.setVisibility(festival == null ? View.GONE : View.VISIBLE);
        boolean online = false;
        try {
            NetworkInfo n = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            online = n != null && n.isConnected();
        } catch (Exception ignored) { }
        net.setText(online ? "Ghar Home 0.18" : T.t("No internet") + "   \u2022   Ghar Home 0.18");
        net.setTextColor(online ? 0x66FFFFFF : 0xFFFFB199);
        showWeather();
        showStatus();
        if (online) refreshWeather(false);
        if (online) refreshLive();
    }

    /** The volume, redrawn only when it has changed. Asked every second: this TV doesn't announce changes. */
    private void showVolume() {
        if (volumeText == null) return;
        if (statusTick++ % 5 == 0) showPlugs();
        showVolumeNumber();
    }

    private void showPlugs() {
        // Each icon shows only for what the TV has and can tell: nothing here is set by hand.
        // Wi-Fi: on TVs with Wi-Fi, plain when connected and crossed when not.
        try {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
                NetworkInfo wifi = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                boolean off = wifi == null || !wifi.isConnected();
                if (wifiIcon.off != off || wifiView.getVisibility() != View.VISIBLE) {
                    wifiIcon.off = off;
                    wifiView.setVisibility(View.VISIBLE);
                    wifiView.invalidate();
                }
            }
        } catch (Throwable ignored) { }
        // Bluetooth: on TVs with Bluetooth, plain when switched on and crossed when off.
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null) {
                boolean off = !bt.isEnabled();
                if (btIcon.off != off || bluetoothIcon.getVisibility() != View.VISIBLE) {
                    btIcon.off = off;
                    bluetoothIcon.setVisibility(View.VISIBLE);
                    bluetoothIcon.invalidate();
                }
            }
        } catch (Throwable ignored) { }
        // A speaker or headphones on the audio socket: only where the TV detects the plug.
        boolean wired = false;
        try { wired = ((AudioManager) getSystemService(AUDIO_SERVICE)).isWiredHeadsetOn(); } catch (Throwable ignored) { }
        wiredIcon.setVisibility(wired ? View.VISIBLE : View.GONE);
    }

    private void showVolumeNumber() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), now = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int percent = max <= 0 ? 0 : Math.round(now * 100f / max);
            if (percent == shownVolume) return;
            shownVolume = percent;
            volumeIcon.level = percent / 100f;
            volumeIcon.invalidateSelf();
            volumeText.setText(T.num("" + percent) + "%");
            volumeText.invalidate();
        } catch (Throwable t) {
            volumeText.setText("");
        }
    }

    /** The volume and whether a USB drive is in. */
    private void showStatus() {
        if (volumeText == null) return;
        showVolume();
        usbText.setVisibility(TvInput.usb() ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- Nepali TV

    /** Asks each channel whether it is streaming, a few kilobytes each, and marks the ones that are. */
    private void refreshLive() {
        if (liveBusy || channelTiles == null || System.currentTimeMillis() - liveChecked < 10 * 60 * 1000L) return;
        liveBusy = true;
        liveChecked = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                final String[] found = new String[Channels.NAMES.length];
                for (int i = 0; i < found.length; i++) found[i] = Channels.liveVideo(i);
                handler.post(new Runnable() {
                    @Override public void run() {
                        liveBusy = false;
                        if (channelTiles == null) return;
                        for (int i = 0; i < found.length; i++) {
                            liveVideo[i] = found[i];
                            GradientDrawable dot = null;
                            if (found[i] != null) {          // a red dot: on air now
                                dot = new GradientDrawable();
                                dot.setShape(GradientDrawable.OVAL);
                                dot.setColor(0xFFFF5252);
                                dot.setSize((int) (1.4f * u), (int) (1.4f * u));
                            }
                            channelTiles[i].setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
                            channelTiles[i].setCompoundDrawablePadding(u);
                        }
                    }
                });
            }
        }, "live").start();
    }

    /** Opens a channel: its live stream when it has one right now, otherwise its page of programmes. */
    private void openChannel(final int i) {
        toast("Opening " + Channels.NAMES[i]);
        new Thread(new Runnable() {
            @Override public void run() {
                final String video = Channels.liveVideo(i);
                handler.post(new Runnable() {
                    @Override public void run() {
                        liveVideo[i] = video;
                        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(video != null ? Channels.videoUrl(video) : Channels.channelUrl(i)))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            if (installed(SMARTTUBE)) open.setPackage(SMARTTUBE);
                            startActivity(open);
                        } catch (Exception e) {
                            toast("SmartTube is needed to watch this");
                        }
                    }
                });
            }
        }, "channel").start();
    }

    // ---------------------------------------------------------------- sound

    /** A short, quiet tick when the focus moves. Made here, so there is no sound file to ship. */
    private void tick() {
        if (!prefs.getBoolean(KEY_SOUND, true)) return;
        try {
            if (tickSound == null) {
                int rate = 22050, n = rate * 40 / 1000;
                short[] wave = new short[n];
                for (int i = 0; i < n; i++) {
                    double t = i / (double) rate;
                    wave[i] = (short) (Math.sin(2 * Math.PI * 740 * t) * Math.exp(-t / 0.009) * 0.16 * Short.MAX_VALUE);
                }
                tickSound = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, n * 2, AudioTrack.MODE_STATIC);
                tickSound.write(wave, 0, n);
            }
            if (tickSound.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) tickSound.stop();
            tickSound.reloadStaticData();
            tickSound.play();
        } catch (Throwable t) {
            tickSound = null;     // no sound is fine
        }
    }

    /** Two soft rising notes when the home screen has loaded. */
    private void chime() {
        if (!prefs.getBoolean(KEY_SOUND, true)) return;
        try {
            if (chimeSound == null) {
                int rate = 22050, note = rate * 170 / 1000, n = note * 2;
                short[] wave = new short[n];
                double[] pitch = {523.25, 783.99};
                for (int i = 0; i < n; i++) {
                    double t = (i % note) / (double) rate;
                    double fade = Math.min(1, t / 0.008) * Math.exp(-t / 0.07);
                    wave[i] = (short) (Math.sin(2 * Math.PI * pitch[i / note] * t) * fade * 0.20 * Short.MAX_VALUE);
                }
                chimeSound = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, n * 2, AudioTrack.MODE_STATIC);
                chimeSound.write(wave, 0, n);
            }
            if (chimeSound.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) chimeSound.stop();
            chimeSound.reloadStaticData();
            chimeSound.play();
        } catch (Throwable t) {
            chimeSound = null;
        }
    }

    // ---------------------------------------------------------------- weather

    private void showWeather() {
        long age = System.currentTimeMillis() - prefs.getLong("w_time", 0);
        if (age < 0 || age > Weather.STALE) {     // old weather is worse than none
            weatherBig.setText("");
            weatherSmall.setText("");
            art.setKind("");
            return;
        }
        weatherBig.setText(prefs.getInt("w_temp", 0) + "\u00B0");
        art.setKind(prefs.getString("w_kind", ""));
        String words = prefs.getString("w_text", "");
        weatherSmall.setText(words.length() > 0 ? T.t(words) + "   \u2022   " + Weather.city(prefs) : Weather.city(prefs));
    }

    /** Asks for new weather when the saved one is over half an hour old (or now, after a change of city). */
    void refreshWeather(boolean now) {
        long age = System.currentTimeMillis() - prefs.getLong("w_time", 0);
        if (weatherBusy || (!now && age >= 0 && age < Weather.FRESH)) return;
        if (!now && System.currentTimeMillis() - weatherTried < 5 * 60 * 1000L) return;   // failed a moment ago
        weatherBusy = true;
        weatherTried = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                Weather.refresh(prefs);
                handler.post(new Runnable() {
                    @Override public void run() {
                        weatherBusy = false;
                        if (weatherBig != null) showWeather();
                    }
                });
            }
        }, "weather").start();
    }

    // ---------------------------------------------------------------- phone remote

    private void openSetting(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("This TV's settings have no such screen");
        }
    }

    /** Whether the Ghar Home keyboard is switched on in the TV's list of keyboards. */
    private boolean keyboardEnabled() {
        try {
            for (InputMethodInfo i : ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).getEnabledInputMethodList()) {
                if (getPackageName().equals(i.getPackageName())) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    /** Whether it is the keyboard in use. */
    private boolean keyboardChosen() {
        try {
            String now = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            return now != null && now.startsWith(getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    /** A QR code that opens the remote on a phone, already signed in with the PIN. */
    private void showRemote() {
        String ip = LinkService.address();
        if (ip == null) {
            toast("The TV is not on a network");
            return;
        }
        String pin = LinkService.pin(this);
        String address = "http://" + ip + ":" + LinkService.PORT;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(4 * u, 4 * u, 4 * u, 4 * u);
        boolean[][] code = Qr.encode(address + "/?pin=" + pin);
        if (code != null) {
            int quiet = 4, cells = code.length + 2 * quiet, cell = Math.max(2, 38 * u / cells);
            Bitmap b = Bitmap.createBitmap(cells * cell, cells * cell, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.drawColor(Color.WHITE);
            Paint dark = new Paint();
            dark.setColor(Color.BLACK);
            for (int y = 0; y < code.length; y++) {
                for (int x = 0; x < code.length; x++) {
                    if (code[y][x]) c.drawRect((x + quiet) * cell, (y + quiet) * cell, (x + quiet + 1) * cell, (y + quiet + 1) * cell, dark);
                }
            }
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(b);
            box.addView(qr);
        }
        TextView how = text(T.t("Scan with your phone's camera.") + " " + T.t("The phone must be on the same Wi-Fi as the TV.")
                + "\n\n" + T.t("Or open") + "  " + address + "\n" + T.t("and enter PIN") + "  " + pin, 2.4f, Color.WHITE);
        how.setLineSpacing(0, 1.2f);
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(4 * u, 0, 0, 0);
        side.setClipChildren(false);
        side.setClipToPadding(false);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        side.addView(how);
        // The two switches that let the phone reach other apps. Both live in the TV's own settings.
        final AlertDialog[] dialog = new AlertDialog[1];
        final boolean enabled = keyboardEnabled(), chosen = keyboardChosen();
        final boolean take = prefs.getBoolean("take_home", true);
        View access = pill(T.t("Home button opens Ghar Home") + ": " + T.t(take ? "on" : "off"), new Runnable() {
            @Override public void run() {
                dialog[0].dismiss();
                prefs.edit().putBoolean("take_home", !take).apply();
                toast(take ? "The Home button opens the TV's own home screen" : "The Home button opens Ghar Home");
            }
        });
        View typing = pill(T.t("Phone in other apps") + ": " + T.t(chosen ? "on" : enabled ? "choose keyboard" : "off"), new Runnable() {
            @Override public void run() {
                dialog[0].dismiss();
                if (enabled) {
                    try {
                        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
                    } catch (Exception e) {
                        openSetting(Settings.ACTION_INPUT_METHOD_SETTINGS);
                    }
                } else {
                    openSetting(Settings.ACTION_INPUT_METHOD_SETTINGS);
                }
            }
        });
        ((LinearLayout.LayoutParams) access.getLayoutParams()).setMargins(0, 3 * u, 0, u);
        ((LinearLayout.LayoutParams) typing.getLayoutParams()).setMargins(0, u, 0, 0);
        side.addView(access);
        side.addView(typing);
        box.addView(side, new LinearLayout.LayoutParams(0, -2, 1f));
        dialog[0] = new AlertDialog.Builder(this).setTitle(T.t("Phone remote")).setView(box).setPositiveButton(T.t("Close"), null).show();
    }

    // ---------------------------------------------------------------- apps

    /** Reads the TV's apps off the main thread (icons are slow on old TVs), then fills the screen. */
    private void loadApps() {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppItem> found = queryApps();
                handler.post(new Runnable() {
                    @Override public void run() {
                        loading = false;
                        if (isFinishing() || favRow == null) return;
                        apps.clear();
                        apps.addAll(found);
                        if (!prefs.contains(KEY_FAVS)) firstFavs();
                        // SmartTube takes the first place the first time it shows up: it is the working YouTube.
                        if (installed(SMARTTUBE) && !prefs.getBoolean(KEY_SMARTTUBE, false)) {
                            favs.remove(SMARTTUBE);
                            favs.add(0, SMARTTUBE);
                            saveFavs();
                            prefs.edit().putBoolean(KEY_SMARTTUBE, true).apply();
                        }
                        if (!prefs.getBoolean(KEY_CLUTTER, false)) {
                            for (String pkg : CLUTTER) {
                                if (installed(pkg) && !hidden.contains(pkg) && !favs.contains(pkg)) hidden.add(pkg);
                            }
                            saveFavs();
                            prefs.edit().putBoolean(KEY_CLUTTER, true).apply();
                        }
                        // Google's own YouTube app no longer runs on Android 4.4 to 7. With SmartTube on the TV it is
                        // hidden, once; "Hidden apps" brings it back.
                        if (installed(SMARTTUBE) && Build.VERSION.SDK_INT < 26 && !prefs.getBoolean(KEY_OLD_YOUTUBE, false)) {
                            for (String pkg : OLD_YOUTUBE) {
                                if (!hidden.contains(pkg)) hidden.add(pkg);
                                favs.remove(pkg);
                            }
                            saveFavs();
                            prefs.edit().putBoolean(KEY_OLD_YOUTUBE, true).apply();
                        }
                        fill();
                    }
                });
            }
        }, "apps").start();
    }

    private List<AppItem> queryApps() {
        PackageManager pm = getPackageManager();
        HashMap<String, AppItem> seen = new HashMap<String, AppItem>();
        List<AppItem> out = new ArrayList<AppItem>();
        int iconSize = 9 * u;
        String[] categories = {Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER"};
        for (String category : categories) {
            List<ResolveInfo> list;
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(category);
                list = pm.queryIntentActivities(i, 0);
            } catch (Exception e) {
                continue;
            }
            if (list == null) continue;
            for (ResolveInfo r : list) {
                ActivityInfo a = r.activityInfo;
                if (a == null || a.packageName.equals(getPackageName())) continue;
                AppItem item = seen.get(a.packageName);
                if (item == null) {
                    item = new AppItem();
                    item.pkg = a.packageName;
                    item.component = new ComponentName(a.packageName, a.name);
                    try {
                        CharSequence label = r.loadLabel(pm);
                        item.label = label == null ? a.packageName : label.toString().trim();
                    } catch (Exception e) {
                        item.label = a.packageName;
                    }
                    try {
                        item.icon = toBitmap(r.loadIcon(pm), iconSize);
                    } catch (Throwable t) {
                        item.icon = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
                    }
                    item.color = cardColor(item.icon);
                    seen.put(a.packageName, item);
                    out.add(item);
                }
                // Banners (wide TV artwork) exist from Android 5 on.
                if (item.banner == null && Build.VERSION.SDK_INT >= 21) {
                    try { item.banner = a.loadBanner(pm); } catch (Throwable ignored) { }
                }
            }
        }
        Collections.sort(out, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return out;
    }

    private static Bitmap toBitmap(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return b;
    }

    /** A dark card color taken from the icon, so each favourite has its own tint. */
    private static int cardColor(Bitmap icon) {
        try {
            Bitmap s = Bitmap.createScaledBitmap(icon, 12, 12, true);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    int p = s.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++;
                }
            }
            if (n == 0) return 0xFF1C2742;
            // Mostly the icon's color, darkened and pulled toward the navy background.
            int cr = (int) (r / n * 0.45 + 0x1C * 0.35), cg = (int) (g / n * 0.45 + 0x27 * 0.35), cb = (int) (b / n * 0.45 + 0x42 * 0.35);
            return Color.rgb(Math.min(255, cr), Math.min(255, cg), Math.min(255, cb));
        } catch (Throwable t) {
            return 0xFF1C2742;
        }
    }

    private static int lighten(int color) {
        int a = Color.alpha(color);
        if (a < 255) return Color.argb(Math.min(255, a + 40), 255, 255, 255);
        return Color.rgb(Math.min(255, Color.red(color) + 28), Math.min(255, Color.green(color) + 28),
                Math.min(255, Color.blue(color) + 28));
    }

    private int used(String pkg) {
        Integer n = uses.get(pkg);
        return n == null ? 0 : n;
    }

    private void open(AppItem app) {
        uses.put(app.pkg, used(app.pkg) + 1);
        usesChanged = true;
        StringBuilder b = new StringBuilder();
        for (String pkg : uses.keySet()) b.append(pkg).append('=').append(uses.get(pkg)).append('\n');
        prefs.edit().putString(KEY_USES, b.toString()).apply();
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(app.component);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            toast(app.label + " did not open");
        }
    }

    // ---------------------------------------------------------------- favourites

    private void readFavs() {
        hidden.clear();
        for (String s : prefs.getString(KEY_HIDDEN, "").split("\n")) {
            if (s.length() > 0 && !hidden.contains(s)) hidden.add(s);
        }
        uses.clear();
        for (String s : prefs.getString(KEY_USES, "").split("\n")) {
            int e = s.lastIndexOf('=');
            try { if (e > 0) uses.put(s.substring(0, e), Integer.parseInt(s.substring(e + 1))); } catch (NumberFormatException ignored) { }
        }
        favs.clear();
        String saved = prefs.getString(KEY_FAVS, "");
        for (String s : saved.split("\n")) {
            if (s.length() > 0 && !favs.contains(s)) favs.add(s);
        }
    }

    private void saveFavs() {
        prefs.edit().putString(KEY_FAVS, TextUtils.join("\n", favs))
                .putString(KEY_HIDDEN, TextUtils.join("\n", hidden)).apply();
    }

    /**
     * One screen for arranging the apps: which are favourites, in what order, and which are hidden.
     * Every app is a row with its settings as buttons, so nothing has to be discovered by holding a key.
     */
    private void showApps() {
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF0B1220, 0xFF0E2A3A}));
        int side = side();
        screen.setPadding(side, 5 * u, side, 0);
        TextView title = text(T.t("Apps"), 5f, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        screen.addView(title);
        TextView hint = text(T.t("OK changes the highlighted setting. Press Back when you are done."), 2.3f, 0x99FFFFFF);
        hint.setPadding(0, u, 0, 2 * u);
        screen.addView(hint);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, u, 0, 6 * u);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        sv.addView(list);
        screen.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        final String[] keepApp = {null};      // where the focus goes back to after the list is rebuilt
        final String[] keepButton = {null};
        final Runnable[] build = new Runnable[1];
        build[0] = new Runnable() {
            @Override public void run() {
                list.removeAllViews();
                // Favourites first, in their order; then the rest by name; hidden ones last.
                List<AppItem> order = new ArrayList<AppItem>();
                for (String pkg : favs) {
                    for (AppItem a : apps) if (a.pkg.equals(pkg) && !hidden.contains(pkg)) order.add(a);
                }
                List<AppItem> rest = new ArrayList<AppItem>(apps);
                Collections.sort(rest, new Comparator<AppItem>() {
                    @Override public int compare(AppItem x, AppItem y) { return x.label.compareToIgnoreCase(y.label); }
                });
                for (AppItem a : rest) if (!favs.contains(a.pkg) && !hidden.contains(a.pkg)) order.add(a);
                for (AppItem a : rest) if (hidden.contains(a.pkg)) order.add(a);

                View want = null, first = null;
                for (final AppItem a : order) {
                    final boolean fav = favs.contains(a.pkg), hid = hidden.contains(a.pkg);
                    LinearLayout row = new LinearLayout(HomeActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, u, 0, u);
                    row.setClipChildren(false);
                    row.setClipToPadding(false);
                    ImageView icon = new ImageView(HomeActivity.this);
                    icon.setImageBitmap(a.icon);
                    icon.setAlpha(hid ? 0.35f : 1f);
                    row.addView(icon, new LinearLayout.LayoutParams(5 * u, 5 * u));
                    TextView name = text(a.label, 2.7f, hid ? 0x66FFFFFF : Color.WHITE);
                    name.setSingleLine(true);
                    name.setEllipsize(TextUtils.TruncateAt.END);
                    name.setPadding(2 * u, 0, 2 * u, 0);
                    row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

                    String[] buttons = hid ? new String[] {"Show"}
                            : fav ? new String[] {"Favourite: yes", "Move up", "Move down", "Hide"}
                            : new String[] {"Favourite: no", "Hide"};
                    for (final String button : buttons) {
                        View v = pill(button, new Runnable() {
                            @Override public void run() {
                                keepApp[0] = a.pkg;
                                keepButton[0] = button;
                                if (button.equals("Favourite: no")) { favs.add(a.pkg); keepButton[0] = "Favourite: yes"; }
                                else if (button.equals("Favourite: yes")) { favs.remove(a.pkg); keepButton[0] = "Favourite: no"; }
                                else if (button.equals("Hide")) { hidden.add(a.pkg); favs.remove(a.pkg); keepButton[0] = "Show"; }
                                else if (button.equals("Show")) { hidden.remove(a.pkg); keepButton[0] = "Hide"; }
                                else {
                                    int step = button.equals("Move up") ? -1 : 1, from = favs.indexOf(a.pkg), to = from + step;
                                    while (to >= 0 && to < favs.size() && (!installed(favs.get(to)) || hidden.contains(favs.get(to)))) to += step;
                                    if (from >= 0 && to >= 0 && to < favs.size()) Collections.swap(favs, from, to);
                                }
                                saveFavs();
                                build[0].run();
                            }
                        });
                        row.addView(v);
                        if (first == null) first = v;
                        if (a.pkg.equals(keepApp[0]) && button.equals(keepButton[0])) want = v;
                    }
                    list.addView(row);
                }
                quiet = true;
                if (want == null) want = first;
                if (want != null) want.requestFocus();
                quiet = false;
            }
        };
        build[0].run();
        d.setContentView(screen);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface dialog) {
                focusInFavs = true;
                fill();
            }
        });
        d.show();
    }

    /** The hidden apps, to bring one back. */
    private void showHidden() {
        final List<AppItem> list = new ArrayList<AppItem>();
        for (AppItem a : apps) if (hidden.contains(a.pkg)) list.add(a);
        if (list.isEmpty()) {
            toast("No hidden apps. Hold OK on an app to hide it.");
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(T.t("Show again"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        hidden.remove(list.get(which).pkg);
                        saveFavs();
                        fill();
                    }
                })
                .show();
    }

    private void firstFavs() {
        for (String pkg : FIRST_FAVS) {
            for (AppItem a : apps) {
                if (a.pkg.equals(pkg) && !favs.contains(pkg)) favs.add(pkg);
            }
        }
        for (AppItem a : apps) {     // top up to five with whatever the TV has
            if (favs.size() >= 5) break;
            if (!favs.contains(a.pkg)) favs.add(a.pkg);
        }
        saveFavs();
    }

    private void showOptions(final AppItem app) {
        final boolean isFav = favs.contains(app.pkg);
        final List<String> names = new ArrayList<String>();
        names.add("Open");
        names.add(isFav ? "Remove from Favourites" : "Add to Favourites");
        if (isFav) {
            names.add("Move left");
            names.add("Move right");
        }
        names.add("Hide from this screen");
        names.add("App info");
        names.add("Uninstall");
        String[] shownNames = new String[names.size()];
        for (int i = 0; i < shownNames.length; i++) shownNames[i] = T.t(names.get(i));
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(shownNames, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String choice = names.get(which);
                        if (choice.equals("Open")) {
                            open(app);
                        } else if (choice.equals("Add to Favourites")) {
                            favs.add(app.pkg);
                            saveFavs();
                            fill();
                        } else if (choice.equals("Remove from Favourites")) {
                            favs.remove(app.pkg);
                            focusInFavs = false;
                            saveFavs();
                            fill();
                        } else if (choice.equals("Move left") || choice.equals("Move right")) {
                            moveFav(app.pkg, choice.equals("Move left") ? -1 : 1);
                        } else if (choice.equals("Hide from this screen")) {
                            hidden.add(app.pkg);
                            favs.remove(app.pkg);
                            saveFavs();
                            fill();
                        } else if (choice.equals("App info")) {
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception e) {
                                toast("App info did not open");
                            }
                        } else if (choice.equals("Uninstall")) {
                            try {
                                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception e) {
                                toast("Uninstall did not open");
                            }
                        }
                    }
                })
                .show();
    }

    /** Moves a favourite one place among the ones on screen (uninstalled ones are skipped over). */
    private void moveFav(String pkg, int step) {
        int from = favs.indexOf(pkg);
        int to = from + step;
        while (to >= 0 && to < favs.size() && !installed(favs.get(to))) to += step;
        if (from < 0 || to < 0 || to >= favs.size()) return;
        Collections.swap(favs, from, to);
        focusPkg = pkg;
        focusInFavs = true;
        saveFavs();
        fill();
    }

    private boolean installed(String pkg) {
        for (AppItem a : apps) if (a.pkg.equals(pkg)) return true;
        return false;
    }

    private void toast(String s) {
        Toast.makeText(this, T.t(s), Toast.LENGTH_SHORT).show();
    }
}
