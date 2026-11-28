/*
 * The only engine API the Android app calls (BUILD_SPEC 3). Part of "UPR ZX for Android (unofficial)",
 * GPLv3, built on "Universal Pokemon Randomizer ZX" by Dabomstew, Ajarmar and contributors.
 */
package uprzx.core;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.SysConstants;
import com.dabomstew.pkrandom.Utils;
import com.dabomstew.pkrandom.exceptions.EncryptedROMException;
import com.dabomstew.pkrandom.romhandlers.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Stream;

/**
 * Entry point. Call {@link #initialize} once at app start, then {@link #load} a ROM. Only one ROM can be
 * loaded at a time, because the engine keeps global state (RandomSource) and its working folder is shared.
 */
public final class Engine {

    private static final Object LOCK = new Object();
    private static File workRoot;
    private static LoadedRom current;

    private Engine() {
    }

    /** Same handler order as desktop NewRandomizerGUI and CliRandomizer. */
    static RomHandler.Factory[] factories() {
        return new RomHandler.Factory[]{
                new Gen1RomHandler.Factory(), new Gen2RomHandler.Factory(), new Gen3RomHandler.Factory(),
                new Gen4RomHandler.Factory(), new Gen5RomHandler.Factory(), new Gen6RomHandler.Factory(),
                new Gen7RomHandler.Factory()
        };
    }

    /** The desktop UI text bundle; the engine also uses it (Randomizer, MiscTweak). */
    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle");
    }

    /**
     * Sets the engine's working folder (SysConstants.ROOT_PATH) to {@code root}, which must be app-private and
     * writable, checks the packaged config files, and deletes work left over from an earlier process.
     * Must run before anything else touches the engine. Calling it again with the same folder is allowed.
     */
    public static void initialize(File root) throws IOException {
        synchronized (LOCK) {
            Files.createDirectories(root.toPath());
            String wanted = root.getCanonicalPath();
            String already = System.getProperty(SysConstants.ROOT_PATH_PROPERTY);
            if (already != null && !new File(already).getCanonicalPath().equals(wanted)) {
                throw new IllegalStateException("Engine already initialized with another folder: " + already);
            }
            System.setProperty(SysConstants.ROOT_PATH_PROPERTY, wanted);
            if (!new File(SysConstants.ROOT_PATH).getCanonicalPath().equals(wanted)) {
                throw new IllegalStateException("SysConstants.ROOT_PATH was read before initialize(): "
                        + SysConstants.ROOT_PATH);
            }
            Utils.testForRequiredConfigs();
            workRoot = root.getCanonicalFile();
            if (current == null) {
                sweepWorkFiles();
            }
        }
    }

    static File workRoot() {
        if (workRoot == null) {
            throw new IllegalStateException("Engine.initialize() has not been called");
        }
        return workRoot;
    }

    /**
     * Deletes the engine's temporary extraction folders (tmp_* in the working folder). The engine relies on
     * deleteOnExit, which Android does not run reliably (BUILD_SPEC 4.5). Only call with no ROM loaded.
     */
    static void sweepWorkFiles() throws IOException {
        File[] children = workRoot().listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory() && f.getName().startsWith("tmp_")) {
                deleteTree(f.toPath());
            }
        }
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        List<Path> all = new ArrayList<>();
        try (Stream<Path> s = Files.walk(p)) {
            s.forEach(all::add);
        }
        all.sort(Comparator.reverseOrder());
        for (Path q : all) {
            Files.deleteIfExists(q);
        }
    }

    /**
     * Loads a ROM, with the same checks and errors as desktop NewRandomizerGUI.loadROM. The returned handle
     * must be closed (try-with-resources), which also deletes the extracted files.
     */
    public static LoadedRom load(File rom) throws EngineException {
        synchronized (LOCK) {
            workRoot();
            if (current != null) {
                throw new EngineException(EngineException.Kind.BUSY, "Another ROM is still loaded");
            }
            try {
                Utils.validateRomFile(rom);
            } catch (Utils.InvalidROMException e) {
                EngineException.Kind kind;
                switch (e.getType()) {
                    case LENGTH: kind = EngineException.Kind.TOO_SHORT; break;
                    case ZIP_FILE: kind = EngineException.Kind.ZIP_FILE; break;
                    case RAR_FILE: kind = EngineException.Kind.RAR_FILE; break;
                    case IPS_FILE: kind = EngineException.Kind.IPS_FILE; break;
                    default: kind = EngineException.Kind.UNREADABLE; break;
                }
                throw new EngineException(kind, e.getMessage(), e);
            }
            String path = rom.getAbsolutePath();
            for (RomHandler.Factory f : factories()) {
                if (f.isLoadable(path)) {
                    RomHandler handler = f.create(RandomSource.instance());
                    current = new LoadedRom(handler, rom);
                    try {
                        handler.loadRom(path);
                        return current;
                    } catch (EncryptedROMException e) {
                        closeQuietly();
                        throw new EngineException(EngineException.Kind.ENCRYPTED, e.getMessage(), e);
                    } catch (OutOfMemoryError e) {
                        closeQuietly();
                        throw new EngineException(EngineException.Kind.OUT_OF_MEMORY, "Out of memory while loading", e);
                    } catch (Exception e) {
                        closeQuietly();
                        throw new EngineException(EngineException.Kind.LOAD_FAILED, String.valueOf(e.getMessage()), e);
                    }
                }
            }
            throw new EngineException(EngineException.Kind.UNSUPPORTED, rom.getName() + " is not a supported ROM");
        }
    }

    private static void closeQuietly() {
        try {
            current.close();
        } catch (Exception ignored) {
            current = null;
        }
    }

    /** Called by LoadedRom.close(). */
    static void released(LoadedRom rom) throws IOException {
        synchronized (LOCK) {
            if (current == rom) {
                current = null;
                sweepWorkFiles();
            }
        }
    }

    /** Reads a .rnqs settings file (desktop format). Old versions are upgraded, as on desktop. */
    public static Settings readSettings(File rnqs) throws IOException {
        try (FileInputStream in = new FileInputStream(rnqs)) {
            return Settings.read(in);
        }
    }

    /** Writes a .rnqs settings file (desktop format). */
    public static void writeSettings(Settings settings, File rnqs) throws IOException {
        try (FileOutputStream out = new FileOutputStream(rnqs)) {
            settings.write(out);
        }
    }

    /** The custom names the engine uses by default (an override file in the working folder, else bundled). */
    public static com.dabomstew.pkrandom.CustomNamesSet defaultCustomNames() throws IOException {
        workRoot();
        return FileFunctions.getCustomNames();
    }
}
