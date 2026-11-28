package com.dabomstew.pkrandom.ctr;

/*----------------------------------------------------------------------------*/
/*--  RomfsFile.java - an entry in the romfs filesystem                     --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
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
/*  Modified 2026-11-28 for "UPR ZX for Android (unofficial)": added copyOverrideContentsTo() to stream changed files (memory change M1).  */

import com.dabomstew.pkrandom.FileFunctions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RomfsFile {

    private NCCH parent;
    public long offset;
    public int size;
    public String fullPath;
    private Extracted status = Extracted.NOT;
    private String extFilename;
    public byte[] data;
    public boolean fileChanged = false;
    public long originalCRC;

    public RomfsFile(NCCH parent) {
        this.parent = parent;
    }

    public byte[] getContents() throws IOException {
        if (this.status == Extracted.NOT) {
            // extract file
            parent.reopenROM();
            RandomAccessFile rom = parent.getBaseRom();
            byte[] buf = new byte[this.size];
            rom.seek(this.offset);
            rom.readFully(buf);
            originalCRC = FileFunctions.getCRC32(buf);
            if (parent.isWritingEnabled()) {
                // make a file
                String tmpDir = parent.getTmpFolder();
                this.extFilename = fullPath.replaceAll("[^A-Za-z0-9_\\.]+", "");
                File tmpFile = new File(tmpDir + extFilename);
                FileOutputStream fos = new FileOutputStream(tmpFile);
                fos.write(buf);
                fos.close();
                tmpFile.deleteOnExit();
                this.status = Extracted.TO_FILE;
                this.data = null;
                return buf;
            } else {
                this.status = Extracted.TO_RAM;
                this.data = buf;
                byte[] newcopy = new byte[buf.length];
                System.arraycopy(buf, 0, newcopy, 0, buf.length);
                return newcopy;
            }
        } else if (this.status == Extracted.TO_RAM) {
            byte[] newcopy = new byte[this.data.length];
            System.arraycopy(this.data, 0, newcopy, 0, this.data.length);
            return newcopy;
        } else {
            String tmpDir = parent.getTmpFolder();
            return FileFunctions.readFileFullyIntoBuffer(tmpDir + this.extFilename);
        }
    }

    public void writeOverride(byte[] data) throws IOException {
        if (status == Extracted.NOT) {
            // temp extract
            getContents();
        }
        fileChanged = true;
        size = data.length;
        if (status == Extracted.TO_FILE) {
            String tmpDir = parent.getTmpFolder();
            FileOutputStream fos = new FileOutputStream(new File(tmpDir + this.extFilename));
            fos.write(data);
            fos.close();
        } else {
            if (this.data.length == data.length) {
                // copy new in
                System.arraycopy(data, 0, this.data, 0, data.length);
            } else {
                // make new array
                this.data = null;
                this.data = new byte[data.length];
                System.arraycopy(data, 0, this.data, 0, data.length);
            }
        }
    }

    /** Receives file data in chunks (RandomAccessFile::write and OutputStream::write both fit). */
    public interface ChunkSink {
        void write(byte[] buf, int off, int len) throws IOException;
    }

    /**
     * Android port (memory, change M1): writes exactly the bytes getOverrideContents() would return, in
     * chunks, without holding the whole file in memory. Returns the number of bytes written.
     * Only for files that have been extracted (status TO_FILE or TO_RAM).
     */
    public long copyOverrideContentsTo(ChunkSink out) throws IOException {
        if (status == Extracted.TO_RAM) {
            out.write(this.data, 0, this.data.length);
            return this.data.length;
        }
        if (status != Extracted.TO_FILE) {
            throw new IllegalStateException("File was never extracted: " + fullPath);
        }
        long written = 0;
        byte[] buf = new byte[1 << 20];
        try (java.io.InputStream in = new java.io.FileInputStream(parent.getTmpFolder() + this.extFilename)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                written += n;
            }
        }
        return written;
    }

    // returns null if no override
    public byte[] getOverrideContents() throws IOException {
        if (status == Extracted.NOT) {
            return null;
        }
        return getContents();
    }

    private enum Extracted {
        NOT, TO_FILE, TO_RAM
    }
}
