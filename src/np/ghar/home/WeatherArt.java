package np.ghar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * A soft picture of the weather behind the header: sun, moon, clouds, rain, snow, fog or a storm.
 * Drawn with fading circles, which look blurred and cost old TV chips almost nothing.
 */
final class WeatherArt extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String kind = "";
    /** Draws the picture in the middle of the view (a forecast card), not toward the corner of the screen. */
    boolean centered;

    WeatherArt(Context c) { super(c); }

    void setKind(String k) {
        if (k == null) k = "";
        if (k.equals(kind)) return;
        kind = k;
        invalidate();
    }

    /** A circle that fades to nothing at its edge. */
    private void glow(Canvas c, float x, float y, float r, int color) {
        paint.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, paint);
        paint.setShader(null);
    }

    private void sun(Canvas c, float x, float y, float h) {
        glow(c, x, y, h * 0.60f, 0x40FFB84D);
        glow(c, x, y, h * 0.34f, 0x70FFD36B);
        glow(c, x, y, h * 0.17f, 0xB0FFF0B0);
    }

    private void moon(Canvas c, float x, float y, float h) {
        glow(c, x, y, h * 0.50f, 0x30A9C4FF);
        glow(c, x, y, h * 0.15f, 0xA0EAF1FF);
    }

    private void cloud(Canvas c, float x, float y, float s, int color) {
        glow(c, x - s * 0.55f, y + s * 0.12f, s * 0.48f, color);
        glow(c, x, y - s * 0.12f, s * 0.62f, color);
        glow(c, x + s * 0.60f, y + s * 0.10f, s * 0.52f, color);
        glow(c, x + s * 0.10f, y + s * 0.25f, s * 0.60f, color);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (kind.length() == 0 || w == 0) return;
        float x = w * 0.42f, y = h * 0.30f;
        if (centered) {
            x = w * 0.5f;
            y = h * 0.42f;
            h *= 0.85f;
        }
        int white = 0x48FFFFFF, grey = 0x40AEBBD6;
        if (kind.equals("sun")) {
            sun(c, x, y, h);
        } else if (kind.equals("moon")) {
            moon(c, x, y, h);
        } else if (kind.equals("partly") || kind.equals("partly_night")) {
            if (kind.equals("partly")) sun(c, x + h * 0.18f, y - h * 0.08f, h * 0.8f);
            else moon(c, x + h * 0.18f, y - h * 0.08f, h * 0.8f);
            cloud(c, x - h * 0.12f, y + h * 0.14f, h * 0.30f, white);
        } else if (kind.equals("cloud")) {
            cloud(c, x - h * 0.20f, y + h * 0.02f, h * 0.30f, white);
            cloud(c, x + h * 0.22f, y + h * 0.16f, h * 0.24f, grey);
        } else if (kind.equals("fog")) {
            for (int i = 0; i < 4; i++) {
                glow(c, x - h * 0.3f + i * h * 0.2f, y + (i % 2) * h * 0.14f, h * 0.36f, 0x30DCE4F2);
            }
        } else {
            // rain, snow, storm: a cloud with something under it
            if (kind.equals("storm")) glow(c, x, y + h * 0.12f, h * 0.36f, 0x50FFE27A);
            cloud(c, x, y, h * 0.32f, kind.equals("storm") ? 0x508C99B8 : grey);
            paint.setStrokeWidth(Math.max(2f, h * 0.012f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 7; i++) {
                float dx = x - h * 0.36f + i * h * 0.12f, dy = y + h * 0.26f + (i % 3) * h * 0.07f;
                if (kind.equals("snow")) {
                    paint.setColor(0x90FFFFFF);
                    c.drawCircle(dx, dy, h * 0.016f, paint);
                } else {
                    paint.setColor(0x80A9CCFF);
                    c.drawLine(dx, dy, dx - h * 0.035f, dy + h * 0.10f, paint);
                }
            }
        }
    }
}
