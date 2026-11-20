package np.ghar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * The moving part of an animated wallpaper: a few dozen dots over the still background.
 * Kept deliberately light for old TV chips: about fifteen frames a second, plain circles, and nothing
 * at all while another app is in front.
 */
final class FxView extends View {

    static final int NONE = 0, STARS = 1, SNOW = 2, FIREFLIES = 3;

    private static final int COUNT = 36;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] x = new float[COUNT], y = new float[COUNT], size = new float[COUNT], phase = new float[COUNT];
    private final int u;
    private int mode;
    private boolean running = true;

    FxView(Context c, int unit) {
        super(c);
        u = unit;
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < COUNT; i++) {
            x[i] = r.nextFloat();
            y[i] = r.nextFloat();
            size[i] = 0.4f + r.nextFloat() * 0.6f;
            phase[i] = r.nextFloat() * 6.283f;
        }
    }

    void setMode(int m) {
        mode = m;
        invalidate();
    }

    void setRunning(boolean on) {
        running = on;
        if (on) invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (mode == NONE) return;
        float w = getWidth(), h = getHeight(), t = SystemClock.uptimeMillis() / 1000f;
        for (int i = 0; i < COUNT; i++) {
            float px, py, r;
            int alpha;
            if (mode == STARS) {            // still points that twinkle; mostly in the upper part of the sky
                px = x[i] * w;
                py = y[i] * h * 0.75f;
                r = size[i] * 0.28f * u;
                alpha = (int) (70 + 150 * (0.5f + 0.5f * Math.sin(t * (0.5f + size[i]) + phase[i])));
                p.setColor(0xFFFFFFFF);
            } else if (mode == SNOW) {      // flakes falling slowly, swaying a little
                py = ((y[i] + t * (0.02f + 0.03f * size[i])) % 1f) * h;
                px = x[i] * w + (float) Math.sin(t * 0.6f + phase[i]) * 2f * u;
                r = size[i] * 0.45f * u;
                alpha = (int) (90 + 110 * size[i]);
                p.setColor(0xFFFFFFFF);
            } else {                        // fireflies: warm dots drifting in slow loops, glowing and fading
                px = x[i] * w + (float) Math.sin(t * 0.25f * (0.5f + size[i]) + phase[i]) * 6f * u;
                py = (0.35f + y[i] * 0.65f) * h + (float) Math.cos(t * 0.2f + phase[i] * 2f) * 4f * u;
                r = size[i] * 0.5f * u;
                alpha = (int) (40 + 200 * Math.max(0, Math.sin(t * 0.8f + phase[i] * 3f)));
                p.setColor(0xFFE8F27A);
            }
            p.setAlpha(Math.min(255, alpha));
            c.drawCircle(px, py, r, p);
        }
        if (running) postInvalidateDelayed(66);
    }
}
