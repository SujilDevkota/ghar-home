package np.ghar.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Lets the phone remote work inside other apps. Android does not let one app press keys in another,
 * but an accessibility service the user turns on may move the focus, click and go back.
 * It acts only when LinkService passes on a command that carried the PIN, and it reads nothing.
 */
public class GharAccess extends AccessibilityService {

    static volatile GharAccess active;

    @Override protected void onServiceConnected() { active = this; }

    @Override public boolean onUnbind(Intent intent) {
        active = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        if (active == this) active = null;
        super.onDestroy();
    }

    /** Set when the user asks for the TV's own home screen, so it is left alone until TV Ghar shows again. */
    static volatile boolean letOriginal;
    private java.util.HashSet<String> homes;

    /**
     * Some TVs never ask which home screen to use, and the Home button keeps opening the maker's.
     * When that one comes to the front, TV Ghar takes its place: the Home button then lands here.
     */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
            String pkg = event.getPackageName().toString();
            if (pkg.equals(getPackageName()) || letOriginal) return;
            if (!getSharedPreferences("ghar", MODE_PRIVATE).getBoolean("take_home", true)) return;
            if (homes == null) {
                homes = new java.util.HashSet<String>();
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(home, 0)) {
                    String p = r.activityInfo == null ? null : r.activityInfo.packageName;
                    if (p != null && !p.equals(getPackageName()) && !p.equals("com.android.settings")) homes.add(p);
                }
            }
            if (!homes.contains(pkg)) return;
            // After a crash the original home screen is the way out: leave it be until TV Ghar has run well again.
            if (getSharedPreferences("ghar", MODE_PRIVATE).getInt("crashes", 0) > 0) return;
            startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
    }

    @Override public void onInterrupt() { }

    /** A remote key, done by hand in the app in front. False when nothing could be done with it. */
    boolean key(int code) {
        try {
            if (code == KeyEvent.KEYCODE_BACK) return performGlobalAction(GLOBAL_ACTION_BACK);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focus == null) {
                AccessibilityNodeInfo first = firstFocusable(root, 0);
                return first != null && first.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_MENU) {
                int action = code == KeyEvent.KEYCODE_MENU ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK;
                AccessibilityNodeInfo n = focus;
                for (int up = 0; n != null && up < 6; up++, n = n.getParent()) {
                    if ((action == AccessibilityNodeInfo.ACTION_CLICK ? n.isClickable() : n.isLongClickable()) && n.performAction(action)) return true;
                }
                return focus.performAction(action);
            }
            int dir = code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                    : code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : code == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
            if (dir == 0) return false;
            AccessibilityNodeInfo next = focus.focusSearch(dir);
            if (next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return true;
            // At the edge of a list: scroll it, so more rows come into reach.
            boolean forward = dir == View.FOCUS_DOWN || dir == View.FOCUS_RIGHT;
            AccessibilityNodeInfo n = focus.getParent();
            for (int up = 0; n != null && up < 8; up++, n = n.getParent()) {
                if (n.isScrollable()) {
                    return n.performAction(forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static AccessibilityNodeInfo firstFocusable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 25) return null;
        if (n.isFocusable() && n.isVisibleToUser() && n.isEnabled()) return n;
        for (int i = 0; i < n.getChildCount() && i < 60; i++) {
            AccessibilityNodeInfo found = firstFocusable(n.getChild(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    /** Adds text to the focused text box. Android 5 and newer only; older TVs type through the keyboard. */
    boolean type(String text) {
        if (Build.VERSION.SDK_INT < 21) return false;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            AccessibilityNodeInfo box = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (box == null || !box.isEditable()) return false;
            CharSequence old = box.getText();
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, (old == null ? "" : old.toString()) + text);
            return box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        } catch (Throwable t) {
            return false;
        }
    }
}
