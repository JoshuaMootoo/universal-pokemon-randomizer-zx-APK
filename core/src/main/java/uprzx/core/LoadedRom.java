/*
 * A loaded ROM. Part of "UPR ZX for Android (unofficial)", GPLv3, built on
 * "Universal Pokemon Randomizer ZX" by Dabomstew, Ajarmar and contributors.
 */
package uprzx.core;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.PixelImage;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.romhandlers.Abstract3DSRomHandler;
import com.dabomstew.pkrandom.romhandlers.AbstractDSRomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Created by {@link Engine#load}. A ROM can be randomized once: the engine changes its in-memory data while
 * randomizing, so desktop reloads the ROM before the next run (reinitializeRomHandler) and so must the app.
 */
public final class LoadedRom implements Closeable {

    private final RomHandler handler;
    private final File romFile;
    private boolean used;
    private boolean closed;

    LoadedRom(RomHandler handler, File romFile) {
        this.handler = handler;
        this.romFile = romFile;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("ROM is closed");
        }
    }

    /** The engine handler, for the option model (which options exist for this ROM). Do not randomize with it. */
    public RomHandler handler() {
        checkOpen();
        return handler;
    }

    public RomInfo info() {
        checkOpen();
        String update = handler.hasGameUpdateLoaded() ? handler.getGameUpdateVersion() : null;
        return new RomInfo(handler.getROMName(), handler.getROMCode(), handler.generationOfPokemon(),
                handler.getSupportLevel(), handler.isRomValid(), handler.getDefaultExtension(),
                handler instanceof Abstract3DSRomHandler, handler instanceof AbstractDSRomHandler, update);
    }

    /** As desktop: a random Pokemon picture from the ROM (uses the engine's random source). */
    public PixelImage mascot() {
        checkOpen();
        return handler.getMascotImage();
    }

    /** ROM diagnostics text, as desktop writes into its error logs. */
    public String diagnostics() {
        checkOpen();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PrintStream ps = new PrintStream(baos, true, "UTF-8"); // String charset: Java 10 overloads are not on older Android
            handler.printRomDiagnostics(ps);
            return baos.toString("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 3DS only. Loads a decrypted game update (.cia). */
    public void applyGameUpdate(File update) throws EngineException {
        checkOpen();
        if (used) {
            throw new IllegalStateException("Reload the ROM before changing the game update");
        }
        boolean ok;
        try {
            ok = handler.loadGameUpdate(update.getAbsolutePath());
        } catch (Exception e) {
            throw new EngineException(EngineException.Kind.GAME_UPDATE_FAILED, String.valueOf(e.getMessage()), e);
        }
        if (!ok) {
            throw new EngineException(EngineException.Kind.GAME_UPDATE_FAILED, "Not a valid update for this game");
        }
    }

    public void removeGameUpdate() {
        checkOpen();
        handler.removeGameUpdate();
    }

    /**
     * Adjusts settings to this ROM, as desktop does when settings are loaded (Settings.tweakForRom). The
     * feedback says whether a custom starter or misc tweaks had to change, so the app can warn.
     */
    public Settings.TweakForROMFeedback tweakForRom(Settings settings) {
        checkOpen();
        return settings.tweakForRom(handler);
    }

    /**
     * Randomizes and saves, following desktop NewRandomizerGUI.performRandomization. The settings should come
     * from the option model (or a .rnqs after {@link #tweakForRom}); their ROM name is set to this ROM, and if
     * they have no custom names the default ones are used. {@code asDirectory} is LayeredFS output (3DS only)
     * into {@code output}, which must not exist or be empty. On failure, partial output is deleted.
     */
    public RandomizeResult randomize(Settings settings, long seed, File output, boolean asDirectory,
                                     ProgressListener progress) throws EngineException {
        checkOpen();
        if (used) {
            throw new IllegalStateException("This ROM was already randomized; load it again first");
        }
        boolean is3DS = handler instanceof Abstract3DSRomHandler;
        if (asDirectory && !is3DS) {
            throw new EngineException(EngineException.Kind.BAD_OUTPUT, "Folder output is only for 3DS games");
        }
        try {
            if (output.getCanonicalPath().equals(romFile.getCanonicalPath())) {
                throw new EngineException(EngineException.Kind.BAD_OUTPUT, "The output would overwrite the source ROM");
            }
        } catch (IOException e) {
            throw new EngineException(EngineException.Kind.IO, e.getMessage(), e);
        }
        if (asDirectory) {
            // NCCH.saveAsLayeredFS deletes the contents of an existing title folder: only ever a fresh folder.
            String[] existing = output.list();
            if (output.exists() && (!output.isDirectory() || (existing != null && existing.length > 0))) {
                throw new EngineException(EngineException.Kind.BAD_OUTPUT, "LayeredFS output needs a new, empty folder");
            }
        }

        used = true;
        settings.setRomName(handler.getROMName());
        try {
            if (settings.getCustomNames() == null) {
                settings.setCustomNames(FileFunctions.getCustomNames());
            }
        } catch (IOException e) {
            throw new EngineException(EngineException.Kind.IO, "Could not read custom names", e);
        }
        RandomSource.seed(seed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream log;
        try {
            log = new PrintStream(baos, false, "UTF-8"); // as desktop
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        handler.setLog(log);
        progress.onStage(ProgressListener.Stage.RANDOMIZING_AND_SAVING);
        try {
            int checkValue = new Randomizer(settings, handler, Engine.bundle(), asDirectory)
                    .randomize(output.getAbsolutePath(), log, seed);
            log.close();
            progress.onStage(ProgressListener.Stage.FINISHED);
            return new RandomizeResult(checkValue, baos.toByteArray());
        } catch (OutOfMemoryError e) {
            deletePartialOutput(output, asDirectory);
            throw new EngineException(EngineException.Kind.OUT_OF_MEMORY, "Out of memory while randomizing", e);
        } catch (RandomizationException e) {
            deletePartialOutput(output, asDirectory);
            throw new EngineException(EngineException.Kind.RANDOMIZATION_FAILED, e.getMessage(), e);
        } catch (Exception e) {
            deletePartialOutput(output, asDirectory);
            throw new EngineException(EngineException.Kind.RANDOMIZATION_FAILED, String.valueOf(e.getMessage()), e);
        }
    }

    private static void deletePartialOutput(File output, boolean asDirectory) {
        try {
            if (asDirectory) {
                Engine.deleteTree(output.toPath());
            } else {
                java.nio.file.Files.deleteIfExists(output.toPath());
            }
        } catch (IOException ignored) {
            // Reported to the user as a failed run either way.
        }
    }

    /** Closes the ROM and deletes the engine's extracted files, after success, failure or cancellation. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (handler instanceof AbstractDSRomHandler) {
                ((AbstractDSRomHandler) handler).closeInnerRom();
            } else if (handler instanceof Abstract3DSRomHandler) {
                ((Abstract3DSRomHandler) handler).closeInnerRom();
            }
        } finally {
            Engine.released(this);
        }
    }
}
