package np.ghar.home;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
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
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private boolean quiet;
    private AudioTrack tickSound;
    private long weatherTried;

    private ScrollView scroll;
    private LinearLayout favRow;
    private LinearLayout grid;
    private TextView clock, date, net, bs, weatherBig, weatherSmall;
    private String focusPkg;
    private boolean focusInFavs = true;
    private boolean loading;
    private int lastMinute = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable settled = new Runnable() {
        @Override public void run() {
            prefs.edit().putInt(KEY_CRASHES, 0).apply();
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
            loadApps();
        } catch (Throwable t) {
            showSafeScreen();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        showing = this;
        if (usesChanged && favRow != null && !apps.isEmpty()) {    // back from an app: the order may have changed
            usesChanged = false;
            fill();
        }
        lastMinute = -1;
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
        try { if (tickSound != null) tickSound.release(); } catch (Exception ignored) { }
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

        FrameLayout root = new FrameLayout(this);
        GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF0B1220, 0xFF111B33, 0xFF0E2A3A});
        GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x552F6BFF, 0x002F6BFF});
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(screenW * 0.45f);
        glow.setGradientCenter(0.88f, 0.05f);
        root.setBackgroundDrawable(new LayerDrawable(new Drawable[] {sky, glow}));

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
        bs = text("", 2.4f, 0xFFFFD27A);
        bs.setPadding(0, u / 2, 0, 0);
        left.addView(clock);
        left.addView(date);
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

        page.addView(sectionTitle("Favourites", side));
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

        page.addView(sectionTitle("All apps", side));
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
        footer.addView(pill("Phone remote", new Runnable() { @Override public void run() { showRemote(); } }));
        footer.addView(pill("Settings", new Runnable() { @Override public void run() { openSettings(); } }));
        footer.addView(pill("Original home", new Runnable() {
            @Override public void run() {
                if (!openOriginalHome()) toast("No other home screen found");
            }
        }));
        footer.addView(pill("Hidden apps", new Runnable() { @Override public void run() { showHidden(); } }));
        final TextView[] sound = new TextView[1];
        sound[0] = (TextView) pill(prefs.getBoolean(KEY_SOUND, true) ? "Sound: on" : "Sound: off", new Runnable() {
            @Override public void run() {
                boolean on = !prefs.getBoolean(KEY_SOUND, true);
                prefs.edit().putBoolean(KEY_SOUND, on).apply();
                sound[0].setText(on ? "Sound: on" : "Sound: off");
                tick();
            }
        });
        footer.addView(sound[0]);
        net = text("", 2.2f, 0x99FFFFFF);
        net.setGravity(Gravity.RIGHT);
        footer.addView(net, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(footer);

        setContentView(root);
        updateClock();
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
                        .setDuration(has ? 220 : 140).start();
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
        TextView t = text(label, 2.3f, Color.WHITE);
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
            TextView hint = text("Hold OK on an app below to add it here.", 2.4f, 0x99FFFFFF);
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
        String miti = NepaliDate.format(c), festival = NepaliDate.festival(c);
        CharSequence english = android.text.format.DateFormat.format("EEEE, d MMMM", now);
        date.setText(miti == null ? english : english + "   \u2022   " + miti);
        bs.setText(festival == null ? "" : festival);
        bs.setVisibility(festival == null ? View.GONE : View.VISIBLE);
        boolean online = false;
        try {
            NetworkInfo n = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            online = n != null && n.isConnected();
        } catch (Exception ignored) { }
        net.setText(online ? "Ghar Home 0.4" : "No internet   \u2022   Ghar Home 0.4");
        net.setTextColor(online ? 0x66FFFFFF : 0xFFFFB199);
        showWeather();
        if (online) refreshWeather(false);
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
        weatherSmall.setText(words.length() > 0 ? words + "   \u2022   " + Weather.city(prefs) : Weather.city(prefs));
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
            int quiet = 4, cells = code.length + 2 * quiet, cell = Math.max(2, 44 * u / cells);
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
        TextView how = text("Scan with your phone's camera.\n\nThe phone must be on the same Wi-Fi as the TV.\n\n"
                + "Or open  " + address + "\nand enter PIN  " + pin, 2.6f, Color.WHITE);
        how.setLineSpacing(0, 1.25f);
        how.setPadding(4 * u, 0, 0, 0);
        box.addView(how);
        new AlertDialog.Builder(this).setTitle("Phone remote").setView(box).setPositiveButton("Close", null).show();
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
                .setTitle("Show again")
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
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(names.toArray(new String[names.size()]), new DialogInterface.OnClickListener() {
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
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
