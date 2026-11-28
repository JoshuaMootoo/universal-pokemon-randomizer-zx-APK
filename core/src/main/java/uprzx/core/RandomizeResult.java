/*
 * Result of a successful randomization. Part of "UPR ZX for Android (unofficial)", GPLv3.
 */
package uprzx.core;

import java.io.IOException;
import java.io.OutputStream;

public final class RandomizeResult {
    /** The check value desktop shows (race mode). */
    public final int checkValue;
    private final byte[] logUtf8;

    RandomizeResult(int checkValue, byte[] logUtf8) {
        this.checkValue = checkValue;
        this.logUtf8 = logUtf8;
    }

    /** Writes the log exactly as desktop saves it: a UTF-8 byte order mark, then the UTF-8 text. */
    public void writeLog(OutputStream out) throws IOException {
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);
        out.write(logUtf8);
    }

    public byte[] logBytes() {
        return logUtf8.clone();
    }
}
