package np.ghar.home;

/**
 * A small QR code encoder: byte mode, error correction L, versions 1 to 5 (up to 106 bytes), mask 0.
 * Enough for a web address. Follows Project Nayuki's QR Code generator (MIT).
 */
final class Qr {

    private static final int[] DATA = {19, 34, 55, 80, 108};   // data codewords per version, level L
    private static final int[] ECC = {7, 10, 15, 20, 26};      // error correction codewords (one block)

    private Qr() { }

    /** The code as [row][column], true = dark; null when the text is too long. */
    static boolean[][] encode(String text) {
        byte[] bytes;
        try { bytes = text.getBytes("UTF-8"); } catch (Exception e) { return null; }
        int ver = 0;
        for (int v = 1; v <= 5 && ver == 0; v++) if (bytes.length <= DATA[v - 1] - 2) ver = v;
        if (ver == 0) return null;
        int dataLen = DATA[ver - 1], eccLen = ECC[ver - 1];

        // Mode (byte) + length + data + terminator, then padding.
        byte[] cw = new byte[dataLen];
        int[] pos = {0};
        put(cw, pos, 4, 4);
        put(cw, pos, bytes.length, 8);
        for (byte b : bytes) put(cw, pos, b & 0xFF, 8);
        put(cw, pos, 0, Math.min(4, dataLen * 8 - pos[0]));
        pos[0] = (pos[0] + 7) / 8 * 8;
        for (int pad = 0xEC; pos[0] < dataLen * 8; pad ^= 0xEC ^ 0x11) put(cw, pos, pad, 8);

        byte[] all = new byte[dataLen + eccLen];
        System.arraycopy(cw, 0, all, 0, dataLen);
        System.arraycopy(remainder(cw, divisor(eccLen)), 0, all, dataLen, eccLen);

        int size = 17 + 4 * ver;
        boolean[][] m = new boolean[size][size], fn = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            set(m, fn, 6, i, i % 2 == 0);
            set(m, fn, i, 6, i % 2 == 0);
        }
        finder(m, fn, 3, 3);
        finder(m, fn, size - 4, 3);
        finder(m, fn, 3, size - 4);
        if (ver >= 2) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    set(m, fn, size - 7 + dx, size - 7 + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                }
            }
        }
        // Format bits: level L (01) and mask 0.
        int data = 1 << 3, rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(m, fn, 8, i, bit(bits, i));
        set(m, fn, 8, 7, bit(bits, 6));
        set(m, fn, 8, 8, bit(bits, 7));
        set(m, fn, 7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(m, fn, 14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(m, fn, size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(m, fn, 8, size - 15 + i, bit(bits, i));
        set(m, fn, 8, size - 8, true);

        // The codewords, in the zigzag from the bottom right.
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean up = ((right + 1) & 2) == 0;
                    int y = up ? size - 1 - vert : vert;
                    if (!fn[y][x] && i < all.length * 8) {
                        m[y][x] = bit(all[i >>> 3] & 0xFF, 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!fn[y][x] && (x + y) % 2 == 0) m[y][x] = !m[y][x];
            }
        }
        return m;
    }

    private static void put(byte[] cw, int[] pos, int value, int count) {
        for (int i = count - 1; i >= 0; i--, pos[0]++) {
            if (((value >>> i) & 1) != 0) cw[pos[0] >>> 3] |= 0x80 >>> (pos[0] & 7);
        }
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private static void set(boolean[][] m, boolean[][] fn, int x, int y, boolean dark) {
        m[y][x] = dark;
        fn[y][x] = true;
    }

    private static void finder(boolean[][] m, boolean[][] fn, int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy)), xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < m.length && yy >= 0 && yy < m.length) set(m, fn, xx, yy, dist != 2 && dist != 4);
            }
        }
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static byte[] divisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                r[j] = (byte) mul(r[j] & 0xFF, root);
                if (j + 1 < degree) r[j] ^= r[j + 1];
            }
            root = mul(root, 2);
        }
        return r;
    }

    private static byte[] remainder(byte[] data, byte[] div) {
        byte[] r = new byte[div.length];
        for (byte b : data) {
            int factor = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= mul(div[i] & 0xFF, factor);
        }
        return r;
    }
}
