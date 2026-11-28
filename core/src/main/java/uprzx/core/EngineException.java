/*
 * Typed engine errors for the app (BUILD_SPEC 6.5). Part of "UPR ZX for Android (unofficial)", GPLv3,
 * built on "Universal Pokemon Randomizer ZX" by Dabomstew, Ajarmar and contributors.
 */
package uprzx.core;

/** An engine failure with a kind the app can turn into a plain message. {@link #bundleKey} is the desktop text. */
public class EngineException extends Exception {

    public enum Kind {
        /** Desktop checks before loading (Utils.validateRomFile). */
        TOO_SHORT("GUI.tooShortToBeARom"), ZIP_FILE("GUI.openedZIPfile"), RAR_FILE("GUI.openedRARfile"),
        IPS_FILE("GUI.openedIPSfile"), UNREADABLE("GUI.unreadableRom"),
        UNSUPPORTED("GUI.unsupportedRom"), ENCRYPTED("GUI.encryptedRom"), LOAD_FAILED("GUI.loadFailedNoLog"),
        /** Another ROM is still loaded: only one engine job at a time (BUILD_SPEC 5.2). */
        BUSY(null),
        /** The output location is not allowed (overwriting the source DS/3DS ROM, non-empty LayeredFS folder). */
        BAD_OUTPUT("GUI.cantOverwriteDS"),
        GAME_UPDATE_FAILED(null),
        RANDOMIZATION_FAILED("GUI.saveFailedMessageNoLog"),
        /** Discard the handler and partial output, and recommend restarting the app (BUILD_SPEC 6.5). */
        OUT_OF_MEMORY(null),
        IO(null);

        public final String bundleKey;

        Kind(String bundleKey) {
            this.bundleKey = bundleKey;
        }
    }

    public final Kind kind;

    public EngineException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public EngineException(Kind kind, String message) {
        this(kind, message, null);
    }
}
