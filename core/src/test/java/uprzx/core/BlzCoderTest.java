/*
 * BUILD_SPEC 4.2a: a malformed BLZ-compressed DS file must raise an exception, not end the process.
 * Part of "UPR ZX for Android (unofficial)", GPLv3.
 */
package uprzx.core;

import cuecompressors.BLZCoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlzCoderTest {

    @Test
    void badHeaderThrowsInsteadOfExiting() {
        // 8 bytes whose footer claims an impossible header length; desktop called System.exit(0) here.
        byte[] junk = {0, 0, 0, 0, 0, 0, 0, 0x7F};
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new BLZCoder(null).BLZ_DecodePub(junk, "arm9.bin"));
        assertTrue(e.getMessage().startsWith("BLZ: "), e.getMessage());
    }

    @Test
    void roundTripStillWorks() {
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 7 % 13);
        byte[] packed = new BLZCoder(null).BLZ_EncodePub(data, false, false, "test");
        assertArrayEquals(data, new BLZCoder(null).BLZ_DecodePub(packed, "test"));
    }
}
