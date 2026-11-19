package np.ghar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * A keyboard for the TV with two ways in: the TV remote moves over its keys, and the phone remote
 * sends whole text straight into the text box that is open, in any app.
 */
public class GharKeyboard extends InputMethodService {

    static volatile GharKeyboard active;
    /** True while some app has a text box open for typing. */
    volatile boolean inputActive;

    private static final String[][] KEYS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "@"},
            {"z", "x", "c", "v", "b", "n", "m", ".", "-", "_"},
            {"ABC", "Space", "Del", "Done"},
    };

    private final Handler handler = new Handler();
    private Board board;
    private int row = 1, col;
    private boolean caps;

    @Override public void onCreate() {
        super.onCreate();
        active = this;
    }

    @Override public void onDestroy() {
        if (active == this) active = null;
        super.onDestroy();
    }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        inputActive = info != null && info.inputType != InputType.TYPE_NULL;
    }

    @Override public void onFinishInput() {
        inputActive = false;
        super.onFinishInput();
    }

    @Override public View onCreateInputView() {
        board = new Board(this);
        return board;
    }

    /** A TV remote counts as a hardware keyboard, which would keep the keys hidden. */
    @Override public boolean onEvaluateInputViewShown() { return true; }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    // ---------------------------------------------------------------- from the phone

    /** Text, or one of "backspace" and "enter", sent by the phone remote. */
    void remote(final String text, final String key) {
        handler.post(new Runnable() {
            @Override public void run() {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                if (text != null && text.length() > 0) ic.commitText(text, 1);
                if ("backspace".equals(key)) ic.deleteSurroundingText(1, 0);
                if ("enter".equals(key)) done(ic);
            }
        });
    }

    /** An arrow or OK from the phone, passed to the app that owns the text box. */
    void remoteKey(final int code) {
        handler.post(new Runnable() {
            @Override public void run() { sendDownUpKeyEvents(code); }
        });
    }

    private void done(InputConnection ic) {
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noAction = info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED;
        if (noAction) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        else ic.performEditorAction(action);
    }

    // ---------------------------------------------------------------- from the TV remote

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (!isInputViewShown() || board == null) return super.onKeyDown(code, event);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: move(-1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: move(1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: move(0, -1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(0, 1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: press(); return true;
            default: return super.onKeyDown(code, event);
        }
    }

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if (isInputViewShown() && board != null && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) return true;
        return super.onKeyUp(code, event);
    }

    private void move(int dr, int dc) {
        if (dr != 0) {
            int next = row + dr;
            if (next < 0 || next >= KEYS.length) return;
            // Between the letter rows and the wide bottom row, keep roughly the same place across.
            col = Math.round(col * (KEYS[next].length - 1) / (float) Math.max(1, KEYS[row].length - 1));
            row = next;
        } else {
            col = (col + dc + KEYS[row].length) % KEYS[row].length;
        }
        board.invalidate();
    }

    private void press() {
        InputConnection ic = getCurrentInputConnection();
        String k = KEYS[row][col];
        if (k.equals("ABC")) {
            caps = !caps;
            board.invalidate();
            return;
        }
        if (ic == null) return;
        if (k.equals("Space")) ic.commitText(" ", 1);
        else if (k.equals("Del")) ic.deleteSurroundingText(1, 0);
        else if (k.equals("Done")) {
            done(ic);
            requestHideSelf(0);
        } else ic.commitText(caps ? k.toUpperCase(java.util.Locale.US) : k, 1);
    }

    /** The keys, drawn by hand: the IME's window can't take the focus, so there are no real buttons. */
    private final class Board extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final int u;

        Board(Context c) {
            super(c);
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            u = Math.max(4, Math.min(dm.widthPixels, dm.heightPixels) / 100);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), KEYS.length * 8 * u + 3 * u);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xF20B1220);
            float w = getWidth(), keyH = 8 * u, top = 1.5f * u, boardW = Math.min(w * 0.72f, 130 * u), left = (w - boardW) / 2;
            p.setTextAlign(Paint.Align.CENTER);
            for (int y = 0; y < KEYS.length; y++) {
                float keyW = boardW / KEYS[y].length;
                for (int x = 0; x < KEYS[y].length; x++) {
                    boolean on = y == row && x == col;
                    r.set(left + x * keyW + u * 0.4f, top + y * keyH + u * 0.4f, left + (x + 1) * keyW - u * 0.4f, top + (y + 1) * keyH - u * 0.4f);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(on ? 0xFF2F6BFF : 0x1FFFFFFF);
                    c.drawRoundRect(r, 1.4f * u, 1.4f * u, p);
                    String k = KEYS[y][x];
                    String label = k.length() == 1 && caps ? k.toUpperCase(java.util.Locale.US) : k.equals("ABC") ? (caps ? "abc" : "ABC") : k;
                    p.setColor(0xFFFFFFFF);
                    p.setTextSize((k.length() == 1 ? 3.6f : 2.8f) * u);
                    c.drawText(label, r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2, p);
                }
            }
        }
    }
}
