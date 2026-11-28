package com.dabomstew.pkrandom;

/*----------------------------------------------------------------------------*/
/*--  PixelImage.java - a platform-neutral ARGB image, replacing            --*/
/*--                    java.awt.image.BufferedImage (TYPE_INT_ARGB) in the --*/
/*--                    engine, because Android has no java.awt.            --*/
/*--                                                                        --*/
/*--  New file, 2026-11-28, for "UPR ZX for Android (unofficial)", based on --*/
/*--  "Universal Pokemon Randomizer ZX" by the UPR-ZX team and              --*/
/*--  "Universal Pokemon Randomizer" by Dabomstew.                          --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

/**
 * Width x height ARGB pixels (0xAARRGGBB, not premultiplied), initially all 0 (transparent black),
 * like a new BufferedImage of TYPE_INT_ARGB. getRGB/setRGB store and return the raw value.
 */
public class PixelImage {

    private final int width;
    private final int height;
    private final int[] argb;

    public PixelImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width (" + width + ") and height (" + height + ") must be > 0");
        }
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRGB(int x, int y) {
        checkBounds(x, y);
        return argb[y * width + x];
    }

    public void setRGB(int x, int y, int rgb) {
        checkBounds(x, y);
        argb[y * width + x] = rgb;
    }

    /** A copy of all pixels, row by row from the top-left. */
    public int[] getPixels() {
        return argb.clone();
    }

    private void checkBounds(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds: (" + x + ", " + y + ") in "
                    + width + "x" + height);
        }
    }

    /**
     * Same as java.awt.Graphics.drawImage(src, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null) on a fresh
     * Graphics of a TYPE_INT_ARGB BufferedImage (default SrcOver composite), for the unscaled case the
     * engine uses: the source and destination rectangles must be the same size. Parts of the source
     * rectangle outside the source image, or of the destination rectangle outside this image, are skipped.
     */
    public void drawImage(PixelImage src, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
        int w = dx2 - dx1;
        int h = dy2 - dy1;
        if (w != sx2 - sx1 || h != sy2 - sy1 || w < 0 || h < 0) {
            throw new UnsupportedOperationException("Only unscaled drawing is supported");
        }
        for (int j = 0; j < h; j++) {
            int sy = sy1 + j;
            int dy = dy1 + j;
            if (sy < 0 || sy >= src.height || dy < 0 || dy >= height) {
                continue;
            }
            for (int i = 0; i < w; i++) {
                int sx = sx1 + i;
                int dx = dx1 + i;
                if (sx < 0 || sx >= src.width || dx < 0 || dx >= width) {
                    continue;
                }
                int index = dy * width + dx;
                argb[index] = srcOver(src.argb[sy * src.width + sx], argb[index]);
            }
        }
    }

    /**
     * Porter-Duff "source over" for non-premultiplied ARGB. The engine only draws pixels with alpha 0
     * (left out) or 255 (copied); partly transparent pixels use the standard formula, rounded to nearest.
     */
    static int srcOver(int s, int d) {
        int sa = s >>> 24;
        if (sa == 0xFF) {
            return s;
        }
        if (sa == 0) {
            return d;
        }
        int da = d >>> 24;
        if (da == 0) {
            return s;
        }
        // Result alpha and colours in 0..255 x 255 fixed point.
        int ra255 = sa * 255 + da * (255 - sa);
        int ra = (ra255 + 127) / 255;
        int r = blend((s >> 16) & 0xFF, (d >> 16) & 0xFF, sa, da, ra255);
        int g = blend((s >> 8) & 0xFF, (d >> 8) & 0xFF, sa, da, ra255);
        int b = blend(s & 0xFF, d & 0xFF, sa, da, ra255);
        return (ra << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int cs, int cd, int sa, int da, int ra255) {
        long num = (long) cs * sa * 255 + (long) cd * da * (255 - sa);
        return (int) ((num + ra255 / 2) / ra255);
    }
}
