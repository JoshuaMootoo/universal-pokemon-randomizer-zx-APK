package com.dabomstew.pkrandom.ctr;

/*----------------------------------------------------------------------------*/
/*--  GARCArchive.java - class for packing/unpacking GARC archives          --*/
/*--                                                                        --*/
/*--  Code based on "pk3DS", copyright (C) Kaphotics                        --*/
/*--                                                                        --*/
/*--  Ported to Java by UPR-ZX Team under the terms of the GPL:             --*/
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
/*  Modified 2026-11-28 for "UPR ZX for Android (unofficial)": optional disk-backed mode and writeTo(File), so large archives are not held in memory (memory change M2).  */

import cuecompressors.BLZCoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class GARCArchive {

    private final int VER_4 = 0x0400;
    private final int VER_6 = 0x0600;
    private int version;
    private final int garcHeaderSize_4 = 0x1C;
    private final int garcHeaderSize_6 = 0x24;
    private final String garcMagic = "CRAG";
    private final String fatoMagic = "OTAF";
    private final String fatbMagic = "BTAF";
    private final String fimbMagic = "BMIF";
    private boolean skipDecompression = true;

    public List<Map<Integer,byte[]>> files = new ArrayList<>();
    private Map<Integer,Boolean> isCompressed = new TreeMap<>();
    private List<Boolean> compressThese = null;

    // Android port (memory, change M2): optional backing file for a disk-backed archive.
    private File backingFile = null;
    private RandomAccessFile backingRaf = null;
    private boolean backingInvalidated = false;

    private GARCFrame garc;
    private FATOFrame fato;
    private FATBFrame fatb;
    private FIMBFrame fimb;

    public GARCArchive() {

    }

    public GARCArchive(byte[] data, boolean skipDecompression) throws IOException {
        this.skipDecompression = skipDecompression;
        boolean success = readFrames(data);
        if (!success) {
            throw new IOException("Invalid GARC file");
        }
        files = fimb.files;
    }

    public GARCArchive(byte[] data, List<Boolean> compressedThese) throws IOException {
        this.compressThese = compressedThese;
        boolean success = readFrames(data);
        if (!success) {
            throw new IOException("Invalid GARC file");
        }
        files = fimb.files;
    }

    /**
     * Android port (memory, change M2): a disk-backed archive. Same result as
     * GARCArchive(contents of backing, compressedThese), but only the directories marked in compressedThese
     * (the ones the randomizer reads and changes) are loaded into memory; the others are read from the
     * backing file when needed. Write it back with writeTo(File), not getBytes(). The backing file must not
     * change while this archive is in use; after writeTo it may be overwritten, so lazy reads then fail.
     */
    public GARCArchive(File backing, List<Boolean> compressedThese) throws IOException {
        this.compressThese = compressedThese;
        this.backingFile = backing;
        try (RandomAccessFile raf = new RandomAccessFile(backing, "r")) {
            if (raf.length() < 0x14) {
                throw new IOException("Invalid GARC file");
            }
            raf.seek(0x10);
            long dataOffset = Integer.toUnsignedLong(Integer.reverseBytes(raf.readInt()));
            if (dataOffset > raf.length()) {
                throw new IOException("Invalid GARC file");
            }
            byte[] head = new byte[(int) dataOffset];
            raf.seek(0);
            raf.readFully(head);
            backingRaf = raf;
            boolean success;
            try {
                success = readFrames(head);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            } finally {
                backingRaf = null;
            }
            if (!success) {
                throw new IOException("Invalid GARC file");
            }
        }
        files = fimb.files;
    }

    public boolean isFileBacked() {
        return backingFile != null;
    }

    /** Reads bytes from the backing file (an open one during construction or writeTo, else opened briefly). */
    private byte[] readBacking(long offset, int length) {
        if (backingInvalidated) {
            throw new IllegalStateException("GARC backing file was overwritten; reload the archive");
        }
        try {
            if (backingRaf != null) {
                byte[] buf = new byte[length];
                backingRaf.seek(offset);
                backingRaf.readFully(buf);
                return buf;
            }
            try (RandomAccessFile raf = new RandomAccessFile(backingFile, "r")) {
                byte[] buf = new byte[length];
                raf.seek(offset);
                raf.readFully(buf);
                return buf;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A directory whose sub-files stay in the backing file until read (Android port, memory change M2). */
    private class LazyDirectory extends AbstractMap<Integer, byte[]> {
        private final TreeMap<Integer, long[]> locations = new TreeMap<>();
        private final TreeMap<Integer, byte[]> overrides = new TreeMap<>();
        private final TreeSet<Integer> keys = new TreeSet<>();

        void addLocation(int key, long offset, int length) {
            locations.put(key, new long[]{offset, length});
            keys.add(key);
        }

        @Override
        public byte[] get(Object key) {
            if (overrides.containsKey(key)) {
                return overrides.get(key);
            }
            long[] loc = locations.get(key);
            return loc == null ? null : readBacking(loc[0], (int) loc[1]);
        }

        @Override
        public byte[] put(Integer key, byte[] value) {
            overrides.put(key, value);
            keys.add(key);
            return null;
        }

        @Override
        public boolean containsKey(Object key) {
            return keys.contains(key);
        }

        @Override
        public int size() {
            return keys.size();
        }

        @Override
        public Set<Integer> keySet() {
            return Collections.unmodifiableSet(keys);
        }

        @Override
        public Set<Map.Entry<Integer, byte[]>> entrySet() {
            return new AbstractSet<Map.Entry<Integer, byte[]>>() {
                @Override
                public Iterator<Map.Entry<Integer, byte[]>> iterator() {
                    Iterator<Integer> it = keys.iterator();
                    return new Iterator<Map.Entry<Integer, byte[]>>() {
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        public Map.Entry<Integer, byte[]> next() {
                            Integer k = it.next();
                            return new AbstractMap.SimpleImmutableEntry<>(k, get(k));
                        }
                    };
                }

                @Override
                public int size() {
                    return keys.size();
                }
            };
        }
    }

    private boolean readFrames(byte[] data) {
        if (data.length <= 0) {
            System.out.println("Empty GARC");
            return false;
        }
        ByteBuffer bbuf = ByteBuffer.wrap(data);
        bbuf.order(ByteOrder.LITTLE_ENDIAN);
        // GARC
        byte[] magicBuf = new byte[4];
        bbuf.get(magicBuf);
        String magic = new String(magicBuf);
        if (!magic.equals(garcMagic)) {
            return false;
        }
        garc = new GARCFrame();
        garc.headerSize = bbuf.getInt();
        garc.endianness = bbuf.getShort();
        garc.version = bbuf.getShort();
        int frameCount = bbuf.getInt();
        if (frameCount != 4) {
            return false;
        }
        garc.dataOffset = bbuf.getInt();
        garc.fileSize = bbuf.getInt();
        if (garc.version == VER_4) {
            garc.contentLargestUnpadded = bbuf.getInt();
            garc.contentPadToNearest = 4;
            version = 4;
        } else if (garc.version == VER_6) {
            garc.contentLargestPadded = bbuf.getInt();
            garc.contentLargestUnpadded = bbuf.getInt();
            garc.contentPadToNearest = bbuf.getInt();
            version = 6;
        } else {
            return false;
        }

        // FATO
        fato = new FATOFrame();
        bbuf.get(magicBuf);
        magic = new String(magicBuf);
        if (!magic.equals(fatoMagic)) {
            return false;
        }
        fato.headerSize = bbuf.getInt();
        fato.entryCount = bbuf.getShort();
        fato.padding = bbuf.getShort();
        fato.entries = new int[fato.entryCount];
        for (int i = 0; i < fato.entryCount; i++) {
            fato.entries[i] = bbuf.getInt();
        }

        // FATB
        fatb = new FATBFrame();
        bbuf.get(magicBuf);
        magic = new String(magicBuf);
        if (!magic.equals(fatbMagic)) {
            return false;
        }
        fatb.headerSize = bbuf.getInt();
        fatb.fileCount = bbuf.getInt();
        fatb.entries = new FATBEntry[fatb.fileCount];
        for (int i = 0; i < fatb.fileCount; i++) {
            fatb.entries[i] = new FATBEntry();
            fatb.entries[i].vector = bbuf.getInt();
            fatb.entries[i].subEntries = new TreeMap<>();
            int bitVector = fatb.entries[i].vector;
            int counter = 0;
            for (int b = 0; b < 32; b++) {
                boolean exists = (bitVector & 1) == 1;
                bitVector >>>= 1;
                if (!exists) continue;
                FATBSubEntry subEntry = new FATBSubEntry();
                subEntry.start = bbuf.getInt();
                subEntry.end = bbuf.getInt();
                subEntry.length = bbuf.getInt();
                fatb.entries[i].subEntries.put(b,subEntry);
                counter++;
            }
            fatb.entries[i].isFolder = counter > 1;
        }

        // FIMB
        fimb = new FIMBFrame();
        bbuf.get(magicBuf);
        magic = new String(magicBuf);
        if (!magic.equals(fimbMagic)) {
            return false;
        }
        fimb.headerSize = bbuf.getInt();
        fimb.dataSize = bbuf.getInt();
        fimb.files = new ArrayList<>();
        for (int i = 0; i < fatb.fileCount; i++) {
            FATBEntry entry = fatb.entries[i];
            if (backingFile != null) {
                // Android port (memory, change M2): same contents as the loop below, read from the backing file.
                if (!readDirectoryFromBacking(i, entry)) {
                    return false;
                }
                continue;
            }
            Map<Integer,byte[]> files = new TreeMap<>();
            for (int k: entry.subEntries.keySet()) {
                FATBSubEntry subEntry = entry.subEntries.get(k);
                bbuf.position(garc.dataOffset + subEntry.start);
                byte[] file = new byte[subEntry.length];
                boolean compressed = compressThese == null ?
                        bbuf.get(bbuf.position()) == 0x11 && !skipDecompression :
                        bbuf.get(bbuf.position()) == 0x11 && compressThese.get(i);
                bbuf.get(file);
                if (compressed) {
                    try {
                        files.put(k,new BLZCoder(null).BLZ_DecodePub(file,"GARC"));
                        isCompressed.put(i,true);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                } else {
                    files.put(k,file);
                    isCompressed.put(i,false);
                }
            }
            fimb.files.add(files);
        }
        return true;
    }

    /**
     * Android port (memory, change M2): the disk-backed version of one iteration of the FIMB loop in
     * readFrames. Directories in compressThese are read (and decompressed) exactly as there; the others,
     * which that loop stores raw and uncompressed, become LazyDirectory entries with the same bytes.
     */
    private boolean readDirectoryFromBacking(int i, FATBEntry entry) {
        try {
            if (compressThese.get(i)) {
                Map<Integer,byte[]> files = new TreeMap<>();
                for (int k: entry.subEntries.keySet()) {
                    FATBSubEntry subEntry = entry.subEntries.get(k);
                    long position = (long) garc.dataOffset + subEntry.start;
                    backingRaf.seek(position);
                    int first = backingRaf.read();
                    if (first < 0) {
                        throw new IndexOutOfBoundsException("GARC sub-file starts past the end of the file");
                    }
                    byte[] file = readBacking(position, subEntry.length);
                    boolean compressed = first == 0x11;
                    if (compressed) {
                        try {
                            files.put(k,new BLZCoder(null).BLZ_DecodePub(file,"GARC"));
                            isCompressed.put(i,true);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return false;
                        }
                    } else {
                        files.put(k,file);
                        isCompressed.put(i,false);
                    }
                }
                fimb.files.add(files);
            } else {
                LazyDirectory files = new LazyDirectory();
                for (int k: entry.subEntries.keySet()) {
                    FATBSubEntry subEntry = entry.subEntries.get(k);
                    files.addLocation(k, (long) garc.dataOffset + subEntry.start, subEntry.length);
                    isCompressed.put(i,false);
                }
                fimb.files.add(files);
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void updateFiles(List<Map<Integer,byte[]>> files) {
        fimb.files = files;
    }

    public byte[] getBytes() throws IOException {
        int garcHeaderSize = garc.version == VER_4 ? garcHeaderSize_4 : garcHeaderSize_6;
        ByteBuffer garcBuf = ByteBuffer.allocate(garcHeaderSize);
        garcBuf.order(ByteOrder.LITTLE_ENDIAN);
        garcBuf.put(garcMagic.getBytes());
        garcBuf.putInt(garcHeaderSize);
        garcBuf.putShort((short)0xFEFF);
        garcBuf.putShort(version == 4 ? (short)VER_4 : (short)VER_6);
        garcBuf.putInt(4);

        ByteBuffer fatoBuf = ByteBuffer.allocate(fato.headerSize);
        fatoBuf.order(ByteOrder.LITTLE_ENDIAN);
        fatoBuf.put(fatoMagic.getBytes());
        fatoBuf.putInt(fato.headerSize);
        fatoBuf.putShort((short)fato.entryCount);
        fatoBuf.putShort((short)fato.padding);

        ByteBuffer fatbBuf = ByteBuffer.allocate(fatb.headerSize);
        fatbBuf.order(ByteOrder.LITTLE_ENDIAN);
        fatbBuf.put(fatbMagic.getBytes());
        fatbBuf.putInt(fatb.headerSize);
        fatbBuf.putInt(fatb.fileCount);

        ByteBuffer fimbHeaderBuf = ByteBuffer.allocate(fimb.headerSize);
        fimbHeaderBuf.order(ByteOrder.LITTLE_ENDIAN);
        fimbHeaderBuf.put(fimbMagic.getBytes());
        fimbHeaderBuf.putInt(fimb.headerSize);

        ByteArrayOutputStream fimbPayloadStream = new ByteArrayOutputStream(); // Unknown size, can't use ByteBuffer

        int fimbOffset = 0;
        int largestSize = 0;
        int largestPadded = 0;
        for (int i = 0; i < fimb.files.size(); i++) {
            Map<Integer,byte[]> directory = fimb.files.get(i);
            int bitVector = 0;
            int totalLength = 0;
            for (int k: directory.keySet()) {
                bitVector |= (1 << k);
                byte[] file = directory.get(k);
                if (isCompressed.get(i)) {
                    file = new BLZCoder(null).BLZ_EncodePub(file,false,false,"GARC");
                }
                fimbPayloadStream.write(file);
                totalLength += file.length;
            }

            int paddingRequired = totalLength % garc.contentPadToNearest;
            if (paddingRequired != 0) {
                paddingRequired = garc.contentPadToNearest - paddingRequired;
            }

            if (totalLength > largestSize) {
                largestSize = totalLength;
            }
            if (totalLength + paddingRequired > largestPadded) {
                largestPadded = totalLength + paddingRequired;
            }

            for (int j = 0; j < paddingRequired; j++) {
                fimbPayloadStream.write(fato.padding & 0xFF);
            }

            fatoBuf.putInt(fatbBuf.position() - 12);

            fatbBuf.putInt(bitVector);
            fatbBuf.putInt(fimbOffset);
            fimbOffset = fimbPayloadStream.size();
            fatbBuf.putInt(fimbOffset);
            fatbBuf.putInt(totalLength);
        }

        int dataOffset = garcHeaderSize + fatoBuf.position() + fatbBuf.position() + fimb.headerSize;
        garcBuf.putInt(dataOffset);
        garcBuf.putInt(dataOffset + fimbOffset);
        if (garc.version == VER_4) {
            garcBuf.putInt(largestSize);
        } else if (garc.version == VER_6) {
            garcBuf.putInt(largestPadded);
            garcBuf.putInt(largestSize);
            garcBuf.putInt(garc.contentPadToNearest);
        }
        fimbHeaderBuf.putInt(fimbPayloadStream.size());

        garcBuf.flip();
        fatoBuf.flip();
        fatbBuf.flip();
        fimbHeaderBuf.flip();

        byte[] fullArray = new byte[garcBuf.limit() + fatoBuf.limit() + fatbBuf.limit() + fimbHeaderBuf.limit() + fimbPayloadStream.size()];
        System.arraycopy(garcBuf.array(),
                0,
                fullArray,
                0,
                garcBuf.limit());
        System.arraycopy(fatoBuf.array(),
                0,
                fullArray,
                garcBuf.limit(),
                fatoBuf.limit());
        System.arraycopy(fatbBuf.array(),
                0,
                fullArray,
                garcBuf.limit()+fatoBuf.limit(),
                fatbBuf.limit());
        System.arraycopy(fimbHeaderBuf.array(),
                0,
                fullArray,
                garcBuf.limit()+fatoBuf.limit()+fatbBuf.limit(),
                fimbHeaderBuf.limit());
//        garcBuf.get(fullArray);
//        fatoBuf.get(fullArray,garcBuf.limit(),fatoBuf.limit());
//        fatbBuf.get(fullArray,garcBuf.limit()+fatoBuf.limit(),fatbBuf.limit());
//        fimbHeaderBuf.get(fullArray,garcBuf.limit()+fatoBuf.limit()+fatbBuf.limit(),fimbHeaderBuf.limit());
        System.arraycopy(fimbPayloadStream.toByteArray(),
                0,
                fullArray,
                garcBuf.limit()+fatoBuf.limit()+fatbBuf.limit()+fimbHeaderBuf.limit(),
                fimbPayloadStream.size());
        return fullArray;
    }



    /**
     * Android port (memory, change M2): writes exactly the bytes getBytes() would return to {@code out},
     * without building them in memory. Keep in step with getBytes(): same frames, same order, same values.
     * The FIMB payload goes to a temporary file first, because the headers in front of it need its sizes.
     * After this, a disk-backed archive refuses further lazy reads (its backing file may be overwritten).
     */
    public void writeTo(File out) throws IOException {
        int garcHeaderSize = garc.version == VER_4 ? garcHeaderSize_4 : garcHeaderSize_6;
        ByteBuffer garcBuf = ByteBuffer.allocate(garcHeaderSize);
        garcBuf.order(ByteOrder.LITTLE_ENDIAN);
        garcBuf.put(garcMagic.getBytes());
        garcBuf.putInt(garcHeaderSize);
        garcBuf.putShort((short)0xFEFF);
        garcBuf.putShort(version == 4 ? (short)VER_4 : (short)VER_6);
        garcBuf.putInt(4);

        ByteBuffer fatoBuf = ByteBuffer.allocate(fato.headerSize);
        fatoBuf.order(ByteOrder.LITTLE_ENDIAN);
        fatoBuf.put(fatoMagic.getBytes());
        fatoBuf.putInt(fato.headerSize);
        fatoBuf.putShort((short)fato.entryCount);
        fatoBuf.putShort((short)fato.padding);

        ByteBuffer fatbBuf = ByteBuffer.allocate(fatb.headerSize);
        fatbBuf.order(ByteOrder.LITTLE_ENDIAN);
        fatbBuf.put(fatbMagic.getBytes());
        fatbBuf.putInt(fatb.headerSize);
        fatbBuf.putInt(fatb.fileCount);

        ByteBuffer fimbHeaderBuf = ByteBuffer.allocate(fimb.headerSize);
        fimbHeaderBuf.order(ByteOrder.LITTLE_ENDIAN);
        fimbHeaderBuf.put(fimbMagic.getBytes());
        fimbHeaderBuf.putInt(fimb.headerSize);

        File payloadFile = new File(out.getPath() + ".payload");
        long payloadSize = 0; // replaces fimbPayloadStream.size()
        int fimbOffset = 0;
        int largestSize = 0;
        int largestPadded = 0;
        try {
            if (backingFile != null) {
                backingRaf = new RandomAccessFile(backingFile, "r");
            }
            try (OutputStream payload = new BufferedOutputStream(new FileOutputStream(payloadFile), 1 << 20)) {
                for (int i = 0; i < fimb.files.size(); i++) {
                    Map<Integer,byte[]> directory = fimb.files.get(i);
                    int bitVector = 0;
                    int totalLength = 0;
                    for (int k: directory.keySet()) {
                        bitVector |= (1 << k);
                        byte[] file = directory.get(k);
                        if (isCompressed.get(i)) {
                            file = new BLZCoder(null).BLZ_EncodePub(file,false,false,"GARC");
                        }
                        payload.write(file);
                        payloadSize += file.length;
                        totalLength += file.length;
                    }

                    int paddingRequired = totalLength % garc.contentPadToNearest;
                    if (paddingRequired != 0) {
                        paddingRequired = garc.contentPadToNearest - paddingRequired;
                    }

                    if (totalLength > largestSize) {
                        largestSize = totalLength;
                    }
                    if (totalLength + paddingRequired > largestPadded) {
                        largestPadded = totalLength + paddingRequired;
                    }

                    for (int j = 0; j < paddingRequired; j++) {
                        payload.write(fato.padding & 0xFF);
                        payloadSize++;
                    }

                    fatoBuf.putInt(fatbBuf.position() - 12);

                    fatbBuf.putInt(bitVector);
                    fatbBuf.putInt(fimbOffset);
                    fimbOffset = (int) payloadSize;
                    fatbBuf.putInt(fimbOffset);
                    fatbBuf.putInt(totalLength);
                }
            }
        } finally {
            if (backingRaf != null) {
                backingRaf.close();
                backingRaf = null;
            }
        }

        int dataOffset = garcHeaderSize + fatoBuf.position() + fatbBuf.position() + fimb.headerSize;
        garcBuf.putInt(dataOffset);
        garcBuf.putInt(dataOffset + fimbOffset);
        if (garc.version == VER_4) {
            garcBuf.putInt(largestSize);
        } else if (garc.version == VER_6) {
            garcBuf.putInt(largestPadded);
            garcBuf.putInt(largestSize);
            garcBuf.putInt(garc.contentPadToNearest);
        }
        fimbHeaderBuf.putInt((int) payloadSize);

        garcBuf.flip();
        fatoBuf.flip();
        fatbBuf.flip();
        fimbHeaderBuf.flip();

        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 20);
             InputStream payloadIn = new FileInputStream(payloadFile)) {
            os.write(garcBuf.array(), 0, garcBuf.limit());
            os.write(fatoBuf.array(), 0, fatoBuf.limit());
            os.write(fatbBuf.array(), 0, fatbBuf.limit());
            os.write(fimbHeaderBuf.array(), 0, fimbHeaderBuf.limit());
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = payloadIn.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } finally {
            payloadFile.delete();
        }
        if (backingFile != null) {
            backingInvalidated = true;
        }
    }

    public byte[] getFile(int index) {
        return fimb.files.get(index).get(0);
    }

    public byte[] getFile(int index, int subIndex) {
        return fimb.files.get(index).get(subIndex);
    }

    public void setFile(int index, byte[] data) {
        fimb.files.get(index).put(0,data);
    }

    public Map<Integer,byte[]> getDirectory(int index) {
        return fimb.files.get(index);
    }

    private class GARCFrame {
        int headerSize;
        int endianness;
        int version;
        int dataOffset;
        int fileSize;

        int contentLargestPadded;
        int contentLargestUnpadded;
        int contentPadToNearest;
    }

    private class FATOFrame {
        int headerSize;
        int entryCount;
        int padding;

        int[] entries;
    }

    private class FATBFrame {
        int headerSize;
        int fileCount;
        FATBEntry[] entries;
    }

    private class FATBEntry {
        int vector;
        boolean isFolder;
        Map<Integer,FATBSubEntry> subEntries;
    }

    private class FATBSubEntry {
        boolean exists;
        int start;
        int end;
        int length;
        int padding;
    }

    private class FIMBFrame {
        int headerSize;
        int dataSize;
        List<Map<Integer,byte[]>> files;
    }
}
