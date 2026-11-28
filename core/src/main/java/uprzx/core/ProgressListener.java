/*
 * Stage reporting. The engine gives no percentages, so neither does this (BUILD_SPEC 5.2).
 * Part of "UPR ZX for Android (unofficial)", GPLv3.
 */
package uprzx.core;

public interface ProgressListener {
    enum Stage { RANDOMIZING_AND_SAVING, FINISHED }

    void onStage(Stage stage);

    ProgressListener NONE = stage -> { };
}
