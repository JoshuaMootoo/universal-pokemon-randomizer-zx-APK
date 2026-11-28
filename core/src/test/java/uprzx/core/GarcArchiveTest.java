/*
 * Memory change M2: a disk-backed GARCArchive must give the same files and write exactly the same bytes
 * as the in-memory one. Part of "UPR ZX for Android (unofficial)", GPLv3.
 */
package uprzx.core;

import com.dabomstew.pkrandom.ctr.GARCArchive;
import cuecompressors.BLZCoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GarcArchiveTest {

    @TempDir
    File tmp;

    /**
     * Compressible test data that never starts with 0x11. (The engine's LZ11 encoder, BLZCoder.LZSS_Encode,
     * overflows its output buffer on data that does not compress; real game files compress well.)
     */
    static byte[] bytes(int length, int seed) {
        byte[] b = new byte[length];
        for (int i = 0; i < length; i++) b[i] = (byte) (((i / 5) + seed) % 6 + 0x20);
        return b;
    }

    static byte[] lz11(byte[] plain) {
        byte[] c = new BLZCoder(null).BLZ_EncodePub(plain, false, false, "GARC");
        assertEquals(0x11, c[0] & 0xFF, "test data must be LZ11-compressed");
        return c;
    }

    /** Builds a version 6 GARC: each directory maps sub-index (0..31) to its stored bytes. */
    static byte[] buildGarc(List<TreeMap<Integer, byte[]>> dirs, int padTo, int padByte) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteBuffer fatb = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
        int largest = 0;
        for (TreeMap<Integer, byte[]> d : dirs) {
            int vector = 0;
            for (int k : d.keySet()) vector |= 1 << k;
            fatb.putInt(vector);
            int total = 0;
            for (Map.Entry<Integer, byte[]> e : d.entrySet()) {
                int start = data.size();
                data.write(e.getValue());
                fatb.putInt(start).putInt(start + e.getValue().length).putInt(e.getValue().length);
                total += e.getValue().length;
            }
            largest = Math.max(largest, total);
            while (data.size() % padTo != 0) data.write(padByte);
        }
        int fatoSize = 12 + 4 * dirs.size();
        int fatbSize = 12 + fatb.position();
        int dataOffset = 0x24 + fatoSize + fatbSize + 12;
        ByteBuffer out = ByteBuffer.allocate(dataOffset + data.size()).order(ByteOrder.LITTLE_ENDIAN);
        out.put("CRAG".getBytes()).putInt(0x24).putShort((short) 0xFEFF).putShort((short) 0x0600).putInt(4)
                .putInt(dataOffset).putInt(dataOffset + data.size()).putInt(largest).putInt(largest).putInt(padTo);
        out.put("OTAF".getBytes()).putInt(fatoSize).putShort((short) dirs.size()).putShort((short) padByte);
        for (int i = 0; i < dirs.size(); i++) out.putInt(0);
        out.put("BTAF".getBytes()).putInt(fatbSize).putInt(dirs.size());
        out.put(fatb.array(), 0, fatb.position());
        out.put("BMIF".getBytes()).putInt(12).putInt(data.size());
        out.put(data.toByteArray());
        return out.array();
    }

    List<TreeMap<Integer, byte[]>> sampleDirs() {
        List<TreeMap<Integer, byte[]>> dirs = new ArrayList<>();
        dirs.add(new TreeMap<>(Map.of(0, bytes(301, 1))));                       // 0 relevant, plain
        dirs.add(new TreeMap<>(Map.of(0, lz11(bytes(900, 2)))));                 // 1 relevant, compressed
        dirs.add(new TreeMap<>(Map.of(0, bytes(17, 3), 2, bytes(64, 4))));       // 2 lazy folder
        dirs.add(new TreeMap<>(Map.of(0, lz11(bytes(500, 5)))));                 // 3 lazy, compressed (kept raw)
        dirs.add(new TreeMap<>());                                               // 4 empty
        dirs.add(new TreeMap<>(Map.of(0, bytes(3, 6))));                         // 5 lazy, odd length
        dirs.add(new TreeMap<>(Map.of(1, bytes(40, 7), 3, lz11(bytes(200, 8))))); // 6 relevant folder, mixed
        return dirs;
    }

    static final List<Boolean> RELEVANT = Arrays.asList(true, true, false, false, false, false, true);

    void applyEdits(GARCArchive g) {
        g.setFile(0, bytes(123, 10));     // relevant, new size
        g.setFile(1, bytes(1500, 11));    // relevant compressed: re-encoded on write
        g.setFile(5, bytes(9, 12));       // edit in a disk-backed directory
    }

    @Test
    void diskBackedMatchesInMemory() throws Exception {
        for (int padTo : new int[]{4, 0x80}) {
            byte[] original = buildGarc(sampleDirs(), padTo, 0xFF);
            File backing = new File(tmp, "garc_" + padTo + ".bin");
            Files.write(backing.toPath(), original);

            GARCArchive mem = new GARCArchive(original, RELEVANT);
            GARCArchive disk = new GARCArchive(backing, RELEVANT);
            assertTrue(disk.isFileBacked());
            assertEquals(mem.files.size(), disk.files.size());
            for (int i = 0; i < mem.files.size(); i++) {
                assertEquals(mem.getDirectory(i).keySet(), disk.getDirectory(i).keySet(), "keys of " + i);
                for (int k : mem.getDirectory(i).keySet()) {
                    assertArrayEquals(mem.getFile(i, k), disk.getFile(i, k), "file " + i + "/" + k);
                }
            }

            // Unchanged archive: identical bytes.
            File out1 = new File(tmp, "out1_" + padTo + ".bin");
            new GARCArchive(backing, RELEVANT).writeTo(out1);
            assertArrayEquals(mem.getBytes(), Files.readAllBytes(out1.toPath()), "unchanged, pad " + padTo);

            // Edited archive: identical bytes.
            applyEdits(mem);
            applyEdits(disk);
            File out2 = new File(tmp, "out2_" + padTo + ".bin");
            disk.writeTo(out2);
            assertArrayEquals(mem.getBytes(), Files.readAllBytes(out2.toPath()), "edited, pad " + padTo);
            assertFalse(new File(out2.getPath() + ".payload").exists(), "payload temp file deleted");

            // After writeTo the backing may be overwritten: lazy reads must fail loudly, not return stale data.
            assertThrows(IllegalStateException.class, () -> disk.getFile(2, 0));
        }
    }

    @Test
    void inMemoryWriteToMatchesGetBytes() throws Exception {
        byte[] original = buildGarc(sampleDirs(), 4, 0x00);
        GARCArchive mem = new GARCArchive(original, false);
        File out = new File(tmp, "mem.bin");
        mem.writeTo(out);
        assertArrayEquals(mem.getBytes(), Files.readAllBytes(out.toPath()));
    }
}
