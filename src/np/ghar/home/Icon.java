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

    static final int SPEAKER = 0, USB = 1, BLUETOOTH = 2, WIRED = 3, FLAG_NEPAL = 4, FLAG_US = 5, WIFI = 6;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final int kind, size;
    /** For the speaker: 0 to 1, how many of its waves show. */
    float level = 1f;
    /** Draws a slash across the icon: switched off, or not connected. */
    boolean off;

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
        } else if (kind == WIFI) {
            // Wi-Fi: three arcs fanning up from a dot.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 0.09f * s));
            p.setStrokeCap(Paint.Cap.ROUND);
            float cx = x + 0.5f * s, cy = y + 0.82f * s;
            for (float rad : new float[] {0.24f, 0.46f, 0.68f}) {
                r.set(cx - rad * s, cy - rad * s, cx + rad * s, cy + rad * s);
                c.drawArc(r, -135, 90, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 0.07f * s, p);
        } else if (kind == BLUETOOTH) {
            // The Bluetooth rune: one stroke through six points.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 0.09f * s));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            path.moveTo(x + 0.28f * s, y + 0.30f * s);
            path.lineTo(x + 0.70f * s, y + 0.70f * s);
            path.lineTo(x + 0.50f * s, y + 0.92f * s);
            path.lineTo(x + 0.50f * s, y + 0.08f * s);
            path.lineTo(x + 0.70f * s, y + 0.30f * s);
            path.lineTo(x + 0.28f * s, y + 0.70f * s);
            c.drawPath(path, p);
        } else if (kind == WIRED) {
            // Headphones, for a speaker or headphones on the audio socket: the band and the two ear cups.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 0.09f * s));
            p.setStrokeCap(Paint.Cap.ROUND);
            r.set(x + 0.16f * s, y + 0.14f * s, x + 0.84f * s, y + 0.86f * s);
            c.drawArc(r, 180, 180, false, p);
            p.setStyle(Paint.Style.FILL);
            r.set(x + 0.10f * s, y + 0.50f * s, x + 0.30f * s, y + 0.88f * s);
            c.drawRoundRect(r, 0.06f * s, 0.06f * s, p);
            r.set(x + 0.70f * s, y + 0.50f * s, x + 0.90f * s, y + 0.88f * s);
            c.drawRoundRect(r, 0.06f * s, 0.06f * s, p);
        } else if (kind == FLAG_NEPAL) {
            // Nepal's flag: two stacked pennants, crimson with a blue border, the moon above and the sun below.
            int keep = p.getColor();
            path.reset();
            path.moveTo(x + 0.20f * s, y + 0.06f * s);
            path.lineTo(x + 0.80f * s, y + 0.46f * s);
            path.lineTo(x + 0.44f * s, y + 0.46f * s);
            path.lineTo(x + 0.82f * s, y + 0.94f * s);
            path.lineTo(x + 0.20f * s, y + 0.94f * s);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFDC143C);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, 0.07f * s));
            p.setStrokeJoin(Paint.Join.MITER);
            p.setColor(0xFF1F4FBF);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x + 0.36f * s, y + 0.34f * s, 0.07f * s, p);
            c.drawCircle(x + 0.38f * s, y + 0.74f * s, 0.09f * s, p);
            p.setColor(keep);
        } else if (kind == FLAG_US) {
            // The United States flag, simplified: seven stripes, and a blue canton with a few stars as dots.
            int keep = p.getColor();
            float l = x + 0.04f * s, t = y + 0.22f * s, rr = x + 0.96f * s, bb = y + 0.78f * s, stripe = (bb - t) / 7f;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 7; i++) {
                p.setColor(i % 2 == 0 ? 0xFFB22234 : 0xFFFFFFFF);
                c.drawRect(l, t + i * stripe, rr, t + (i + 1) * stripe, p);
            }
            float cw = (rr - l) * 0.44f, ch = stripe * 4f;
            p.setColor(0xFF3C3B6E);
            c.drawRect(l, t, l + cw, t + ch, p);
            p.setColor(0xFFFFFFFF);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    c.drawCircle(l + cw * (0.2f + 0.3f * col), t + ch * (0.2f + 0.3f * row), Math.max(0.6f, 0.022f * s), p);
                }
            }
            p.setColor(keep);
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
        if (off) {
            int keep = p.getColor();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(2f, 0.10f * s));
            p.setColor(0xFFFF6B6B);
            c.drawLine(x + 0.12f * s, y + 0.12f * s, x + 0.88f * s, y + 0.88f * s, p);
            p.setColor(keep);
        }
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
