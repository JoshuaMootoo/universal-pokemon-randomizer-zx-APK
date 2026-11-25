/*
 * Compile-only shim for the golden-master harness (BUILD_SPEC 7.1).
 * Part of "UPR ZX for Android (unofficial)", GPLv3 like the rest of this repository.
 *
 * The engine's Utils.getExecutionLocation() asks where this class was loaded from,
 * and SysConstants.ROOT_PATH becomes the PARENT of that location. The harness
 * compiles this empty stand-in into <work>/root/shim, so ROOT_PATH = <work>/root/.
 * It replaces the Swing GUI class so the untouched engine compiles without the GUI.
 */
package com.dabomstew.pkrandom.newgui;

public class NewRandomizerGUI {
}
