package np.ghar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;

/** The patro: one Bikram Sambat month as a grid, with the English dates, Saturdays and festivals. */
final class PatroView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final int u;
    private final long today;
    private int year, month;

    PatroView(Context c, int unit) {
        super(c);
        u = unit;
        today = NepaliDate.today(Calendar.getInstance());
        int[] d = NepaliDate.bs(today);
        year = d == null ? NepaliDate.FIRST_YEAR : d[0];
        month = d == null ? 0 : d[1];
    }

    /** One month back or forward, inside the years the calendar knows. */
    void move(int step) {
        int m = month + step, y = year;
        if (m < 0) { m = 11; y--; }
        if (m > 11) { m = 0; y++; }
        if (y < NepaliDate.FIRST_YEAR || y > NepaliDate.LAST_YEAR) return;
        year = y;
        month = m;
        invalidate();
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        p.setTextSize(size * u);
        p.setColor(color);
        p.setTextAlign(align);
        c.drawText(s, x, y, p);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean ne = T.ne;
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF0B1220, 0xFF0E2A3A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        float side = w * 0.06f, top = 5f * u;
        long start = NepaliDate.monthStart(year, month);
        int days = NepaliDate.daysIn(year, month);
        int[] a = NepaliDate.english(start), b = NepaliDate.english(start + days - 1);

        String title = ne ? NepaliDate.MONTHS_NE[month] + " " + T.num("" + year) : NepaliDate.MONTHS[month] + " " + year;
        text(c, title, side, top + 5f * u, 6f, 0xFFFFFFFF, Paint.Align.LEFT, false);
        String range = NepaliDate.AD_MONTHS[a[1] - 1] + (a[0] != b[0] ? " " + a[0] : "") + " – " + NepaliDate.AD_MONTHS[b[1] - 1] + " " + b[0];
        text(c, range, side, top + 9f * u, 2.6f, 0xB3FFFFFF, Paint.Align.LEFT, false);
        int[] now = NepaliDate.bs(today);
        if (now != null) {
            String day = ne ? NepaliDate.MONTHS_NE[now[1]] + " " + T.num("" + now[2]) : NepaliDate.MONTHS[now[1]] + " " + now[2];
            text(c, (ne ? "आज: " : "Today: ") + day + "   \u2022   " + Tithi.name(today, ne), w - side, top + 5f * u, 2.8f, 0xFFFFD27A, Paint.Align.RIGHT, false);
            text(c, ne ? "तिथि: काठमाडौंको सूर्योदयमा, गणना गरिएको" : "Tithi: at sunrise in Kathmandu, calculated", w - side, top + 9f * u, 2.0f, 0x80FFFFFF, Paint.Align.RIGHT, false);
        }

        // The grid takes the left two thirds; the month's festivals go on the right.
        float gridW = w * 0.60f, cellW = gridW / 7f, gridTop = top + 13f * u;
        float cellH = (h - gridTop - 9f * u) / 6.6f;
        String[] names = ne ? NepaliDate.DAYS_NE : NepaliDate.DAYS_EN;
        for (int i = 0; i < 7; i++) {
            text(c, names[i], side + cellW * (i + 0.5f), gridTop, 2.3f, i == 6 ? 0xFFFF8A80 : 0x99FFFFFF, Paint.Align.CENTER, true);
        }
        float listX = side + gridW + 5f * u, listY = gridTop + 0.5f * u;
        int first = NepaliDate.weekday(start);
        for (int d = 1; d <= days; d++) {
            long n = start + d - 1;
            int slot = first + d - 1, col = slot % 7, row = slot / 7;
            float x = side + cellW * col, y = gridTop + 1.5f * u + cellH * row;
            String festival = NepaliDate.festivalOn(n, ne);
            box.set(x + u * 0.4f, y + u * 0.4f, x + cellW - u * 0.4f, y + cellH - u * 0.4f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(n == today ? 0xFF2F6BFF : 0x12FFFFFF);
            c.drawRoundRect(box, u, u, p);
            int color = n == today ? 0xFFFFFFFF : festival != null ? 0xFFFFD27A : col == 6 ? 0xFFFF8A80 : 0xFFFFFFFF;
            text(c, T.num("" + d), x + cellW * 0.5f, y + cellH * 0.52f, 3.4f, color, Paint.Align.CENTER, false);
            int ti = Tithi.index(n);
            text(c, Tithi.brief(n, ne), x + u * 1.1f, y + cellH - u * 1.0f, 1.55f,
                    ti == 14 || ti == 29 ? 0xFFFFD27A : n == today ? 0xE6FFFFFF : 0x99FFFFFF, Paint.Align.LEFT, false);
            text(c, "" + NepaliDate.english(n)[2], x + cellW - u * 1.1f, y + cellH - u * 1.0f, 1.7f, 0x99FFFFFF, Paint.Align.RIGHT, false);
            if (festival != null) {
                text(c, T.num("" + d) + "   " + festival, listX, listY + 3.4f * u, 2.7f, 0xFFFFD27A, Paint.Align.LEFT, false);
                listY += 4.4f * u;
            }
        }
        text(c, T.t("Left / Right: change month     Back: close"), side, h - 4f * u, 2.2f, 0x80FFFFFF, Paint.Align.LEFT, false);
    }
}
