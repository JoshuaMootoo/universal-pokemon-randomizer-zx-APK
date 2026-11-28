/*
 * What the app shows about a loaded ROM. Part of "UPR ZX for Android (unofficial)", GPLv3.
 */
package uprzx.core;

public final class RomInfo {
    public final String name;
    public final String code;
    public final int generation;
    public final String supportLevel;
    /** False for unrecognised or modified ROMs (desktop shows a warning). */
    public final boolean valid;
    public final String defaultExtension;
    public final boolean is3DS;
    public final boolean isDS;
    /** Null unless a 3DS game update is loaded. */
    public final String gameUpdateVersion;

    RomInfo(String name, String code, int generation, String supportLevel, boolean valid, String defaultExtension,
            boolean is3DS, boolean isDS, String gameUpdateVersion) {
        this.name = name;
        this.code = code;
        this.generation = generation;
        this.supportLevel = supportLevel;
        this.valid = valid;
        this.defaultExtension = defaultExtension;
        this.is3DS = is3DS;
        this.isDS = isDS;
        this.gameUpdateVersion = gameUpdateVersion;
    }
}
