package np.ghar.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small status icons, drawn by hand so they are sharp at any size and need no image files. */
final class Icon extends Drawable {

    static final int SPEAKER = 0, USB = 1;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final int kind, size;
    /** For the speaker: 0 to 1, how many of its waves show. */
    float level = 1f;

    Icon(int kind, int size, int color) {
        this.kind = kind;
        this.size = size;
        p.setColor(color);
    }

    @Override public int getIntrinsicWidth() { return size; }

    @Override public int getIntrinsicHeight() { return size; }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = b.height(), x = b.left, y = b.top;
        if (kind == SPEAKER) {
            p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(x + 0.06f * s, y + 0.36f * s);
            path.lineTo(x + 0.24f * s, y + 0.36f * s);
            path.lineTo(x + 0.48f * s, y + 0.14f * s);
            path.lineTo(x + 0.48f * s, y + 0.86f * s);
            path.lineTo(x + 0.24f * s, y + 0.64f * s);
            path.lineTo(x + 0.06f * s, y + 0.64f * s);
            path.close();
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 0.08f * s));
            p.setStrokeCap(Paint.Cap.ROUND);
            if (level <= 0f) {          // muted: a cross
                c.drawLine(x + 0.62f * s, y + 0.36f * s, x + 0.90f * s, y + 0.64f * s, p);
                c.drawLine(x + 0.90f * s, y + 0.36f * s, x + 0.62f * s, y + 0.64f * s, p);
            } else {
                float cx = x + 0.48f * s, cy = y + 0.5f * s;
                r.set(cx - 0.20f * s, cy - 0.20f * s, cx + 0.20f * s, cy + 0.20f * s);
                c.drawArc(r, -50, 100, false, p);
                if (level > 0.4f) {
                    r.set(cx - 0.38f * s, cy - 0.38f * s, cx + 0.38f * s, cy + 0.38f * s);
                    c.drawArc(r, -50, 100, false, p);
                }
            }
        } else {
            // A USB stick: the body, the metal plug and its two contacts.
            p.setStyle(Paint.Style.FILL);
            r.set(x + 0.26f * s, y + 0.34f * s, x + 0.74f * s, y + 0.96f * s);
            c.drawRoundRect(r, 0.08f * s, 0.08f * s, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 0.07f * s));
            c.drawRect(x + 0.35f * s, y + 0.06f * s, x + 0.65f * s, y + 0.34f * s, p);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(x + 0.42f * s, y + 0.15f * s, x + 0.47f * s, y + 0.22f * s, p);
            c.drawRect(x + 0.53f * s, y + 0.15f * s, x + 0.58f * s, y + 0.22f * s, p);
        }
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
