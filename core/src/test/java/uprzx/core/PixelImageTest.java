/*
 * PixelImage must behave exactly like java.awt drawing on a TYPE_INT_ARGB BufferedImage (BUILD_SPEC 4.3).
 * Part of "UPR ZX for Android (unofficial)", GPLv3. These tests run on desktop Java, where java.awt exists.
 */
package uprzx.core;

import com.dabomstew.pkrandom.PixelImage;
import org.junit.jupiter.api.Test;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PixelImageTest {

    /** The drawImage calls Gen5RomHandler/Gen6RomHandler.getMascotImage make, in order. */
    static final int[][] MASCOT_CALLS = {
            {0, 0, 64, 64, 0, 0, 64, 64}, {64, 0, 96, 8, 0, 64, 32, 72}, {64, 8, 96, 16, 32, 64, 64, 72},
            {64, 16, 96, 24, 0, 72, 32, 80}, {64, 24, 96, 32, 32, 72, 64, 80}, {64, 32, 96, 40, 0, 80, 32, 88},
            {64, 40, 96, 48, 32, 80, 64, 88}, {64, 48, 96, 56, 0, 88, 32, 96}, {64, 56, 96, 64, 32, 88, 64, 96},
            {0, 64, 64, 96, 0, 96, 64, 128}, {64, 64, 96, 72, 0, 128, 32, 136}, {64, 72, 96, 80, 32, 128, 64, 136},
            {64, 80, 96, 88, 0, 136, 32, 144}, {64, 88, 96, 96, 32, 136, 64, 144}};

    /** Random pixels with alpha 0 or 255 (what the engine's palettes produce), colours random in both cases. */
    static int randomOpaqueOrClear(Random r) {
        int rgb = r.nextInt(0x1000000);
        return r.nextBoolean() ? 0xFF000000 | rgb : rgb;
    }

    static BufferedImage awt(int w, int h, int[] px) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, px[y * w + x]);
        return b;
    }

    static PixelImage mine(int w, int h, int[] px) {
        PixelImage p = new PixelImage(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) p.setRGB(x, y, px[y * w + x]);
        return p;
    }

    static void assertSame(BufferedImage expected, PixelImage actual, String what) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int e = expected.getRGB(x, y), a = actual.getRGB(x, y);
                if (e != a) fail(String.format("%s: pixel (%d,%d) expected %08X but was %08X", what, x, y, e, a));
            }
        }
    }

    void runMascotSequence(int srcW, int srcH, int dstW, int dstH, long seed) {
        Random r = new Random(seed);
        int[] px = new int[srcW * srcH];
        for (int i = 0; i < px.length; i++) px[i] = randomOpaqueOrClear(r);
        BufferedImage aSrc = awt(srcW, srcH, px);
        BufferedImage aDst = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB);
        Graphics g = aDst.getGraphics();
        PixelImage mSrc = mine(srcW, srcH, px);
        PixelImage mDst = new PixelImage(dstW, dstH);
        for (int[] c : MASCOT_CALLS) {
            g.drawImage(aSrc, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], null);
            mDst.drawImage(mSrc, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]);
        }
        assertSame(aDst, mDst, "mascot sequence " + srcW + "x" + srcH + " -> " + dstW + "x" + dstH);
    }

    @Test
    void gen5MascotSequenceMatchesAwt() {
        for (long seed = 0; seed < 20; seed++) runMascotSequence(64, 144, 96, 96, seed);
    }

    @Test
    void gen6MascotSequenceMatchesAwtIncludingOutOfBounds() {
        // Gen 6 draws a 64x32 icon with the Gen 5 coordinates onto a 40x30 canvas: most rectangles are partly
        // or wholly outside the source and/or the canvas.
        for (long seed = 0; seed < 20; seed++) runMascotSequence(64, 32, 40, 30, seed);
    }

    @Test
    void randomRectanglesMatchAwt() {
        Random r = new Random(42);
        for (int t = 0; t < 2000; t++) {
            int sw = 1 + r.nextInt(40), sh = 1 + r.nextInt(40), dw = 1 + r.nextInt(40), dh = 1 + r.nextInt(40);
            int[] spx = new int[sw * sh], dpx = new int[dw * dh];
            for (int i = 0; i < spx.length; i++) spx[i] = randomOpaqueOrClear(r);
            for (int i = 0; i < dpx.length; i++) dpx[i] = r.nextInt(4) == 0 ? randomOpaqueOrClear(r) : 0;
            BufferedImage aSrc = awt(sw, sh, spx), aDst = awt(dw, dh, dpx);
            PixelImage mSrc = mine(sw, sh, spx), mDst = mine(dw, dh, dpx);
            int w = r.nextInt(50), h = r.nextInt(50);
            int sx = r.nextInt(60) - 10, sy = r.nextInt(60) - 10, dx = r.nextInt(60) - 10, dy = r.nextInt(60) - 10;
            Graphics g = aDst.getGraphics();
            g.drawImage(aSrc, dx, dy, dx + w, dy + h, sx, sy, sx + w, sy + h, null);
            mDst.drawImage(mSrc, dx, dy, dx + w, dy + h, sx, sy, sx + w, sy + h);
            assertSame(aDst, mDst, "trial " + t);
        }
    }

    @Test
    void newImageIsTransparentAndSetGetRoundTrips() {
        PixelImage p = new PixelImage(3, 2);
        assertArrayEquals(new int[6], p.getPixels());
        p.setRGB(2, 1, 0x12345678);
        assertEquals(0x12345678, p.getRGB(2, 1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> p.getRGB(3, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> p.setRGB(0, -1, 0));
        assertThrows(UnsupportedOperationException.class, () -> p.drawImage(p, 0, 0, 2, 2, 0, 0, 1, 1));
    }
}
