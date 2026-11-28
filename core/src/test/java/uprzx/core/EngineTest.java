/*
 * Facade tests that need no ROM (BUILD_SPEC 7.2). Part of "UPR ZX for Android (unofficial)", GPLv3.
 */
package uprzx.core;

import com.dabomstew.pkrandom.MiscTweak;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.SysConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {

    static File root;

    @BeforeAll
    static void init() throws Exception {
        root = new File(System.getProperty(SysConstants.ROOT_PATH_PROPERTY));
        Engine.initialize(root);
    }

    @TempDir
    Path tmp;

    @Test
    void resourcesArePackagedAtTheirOriginalPaths() {
        // Utils.testForRequiredConfigs() already ran in initialize(); check bundle, patches and misc tweaks too.
        ResourceBundle b = Engine.bundle();
        assertFalse(b.getString("GUI.unsupportedRom").isEmpty());
        assertNotNull(Engine.class.getResource("/com/dabomstew/pkrandom/config/gen7_offsets.ini"));
        assertNotNull(Engine.class.getResource("/com/dabomstew/pkrandom/patches/bwexp/rb_en_bwxp.ips"));
        assertNotNull(Engine.class.getResource("/com/dabomstew/pkrandom/config/customnames.rncn"));
        assertFalse(MiscTweak.allTweaks.isEmpty());
        for (MiscTweak t : MiscTweak.allTweaks) assertFalse(t.getTweakName().isEmpty());
    }

    @Test
    void rootPathIsTheConfiguredFolder() throws Exception {
        assertEquals(root.getCanonicalPath() + File.separator, SysConstants.ROOT_PATH);
        assertThrows(IllegalStateException.class, () -> Engine.initialize(tmp.toFile()));
    }

    @Test
    void initializeSweepsLeftoverWorkFolders() throws Exception {
        File stale = new File(root, "tmp_OldRom");
        assertTrue(new File(stale, "sub").mkdirs());
        Files.write(new File(stale, "sub/file.bin").toPath(), new byte[100]);
        File keep = new File(root, "not_engine_work");
        assertTrue(keep.mkdirs() || keep.isDirectory());
        Engine.initialize(root);
        assertFalse(stale.exists(), "tmp_ folders are deleted");
        assertTrue(keep.exists(), "other folders are left alone");
    }

    EngineException.Kind loadKind(byte[] content) throws Exception {
        File f = tmp.resolve("candidate.nds").toFile();
        Files.write(f.toPath(), content);
        EngineException e = assertThrows(EngineException.class, () -> Engine.load(f));
        return e.kind;
    }

    @Test
    void loadErrorsMatchDesktopChecks() throws Exception {
        assertEquals(EngineException.Kind.TOO_SHORT, loadKind(new byte[5]));
        assertEquals(EngineException.Kind.ZIP_FILE, loadKind(new byte[]{0x50, 0x4b, 3, 4, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals(EngineException.Kind.RAR_FILE, loadKind(new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 7, 0, 0, 0, 0}));
        assertEquals(EngineException.Kind.IPS_FILE, loadKind("PATCH-and-some-more".getBytes(StandardCharsets.US_ASCII)));
        byte[] junk = new byte[65536];
        new java.util.Random(1).nextBytes(junk);
        assertEquals(EngineException.Kind.UNSUPPORTED, loadKind(junk));
        EngineException e = assertThrows(EngineException.class, () -> Engine.load(tmp.resolve("missing.gba").toFile()));
        assertEquals(EngineException.Kind.UNREADABLE, e.kind);
        // A failed load must not leave the engine "busy".
        assertEquals(EngineException.Kind.UNSUPPORTED, loadKind(junk));
    }

    /** Every golden-master profile: settings string and .rnqs file round trip unchanged. */
    @Test
    void settingsRoundTrip() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("../tools/golden/profiles.tsv"), StandardCharsets.UTF_8);
        int n = 0;
        for (String line : lines) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            String str = line.split("\t")[2];
            Settings s = Settings.fromString(str);
            assertEquals(str, s.toString());
            File rnqs = tmp.resolve("p" + n + ".rnqs").toFile();
            Engine.writeSettings(s, rnqs);
            Settings back = Engine.readSettings(rnqs);
            assertEquals(str, back.toString());
            assertFalse(back.isUpdatedFromOldVersion());
            n++;
        }
        assertEquals(12, n);
    }
}
