package np.ghar.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The loading screen: two arcs turning against each other around a dot that breathes, over a soft glow.
 * No words, so there is nothing to translate and nothing to read while it is on screen for a second.
 */
final class LoadingView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final ValueAnimator turn = ValueAnimator.ofFloat(0f, 1f);
    private final int u;
    private float t;

    LoadingView(Context c, int unit) {
        super(c);
        u = unit;
        setBackgroundColor(0xFF0B1220);
        turn.setDuration(1600);
        turn.setRepeatCount(ValueAnimator.INFINITE);
        turn.setInterpolator(new LinearInterpolator());
        turn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                t = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        turn.start();
    }

    void stop() { turn.cancel(); }

    @Override protected void onDetachedFromWindow() {
        turn.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = 8f * u;
        double wave = Math.sin(t * 2 * Math.PI);

        // The glow behind everything.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, r * 3.2f, 0x332F6BFF, 0x002F6BFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 3.2f, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);

        // A faint full ring, then the bright arc that runs round it. The arc grows and shrinks as it goes.
        p.setStrokeWidth(0.9f * u);
        p.setColor(0x1FFFFFFF);
        c.drawCircle(cx, cy, r, p);
        p.setColor(0xFF3B82FF);
        box.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(box, t * 360f - 90f, 70f + 50f * (float) wave, false, p);

        // The inner arc turns the other way, a little faster.
        float r2 = r * 0.62f;
        p.setStrokeWidth(0.7f * u);
        p.setColor(0xFF19C3B1);
        box.set(cx - r2, cy - r2, cx + r2, cy + r2);
        c.drawArc(box, -t * 720f + 40f, 80f, false, p);

        // The dot in the middle breathes.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setAlpha((int) (200 + 55 * wave));
        c.drawCircle(cx, cy, u * (1.2f + 0.35f * (float) wave), p);
        p.setAlpha(255);
    }
}
