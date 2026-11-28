/*
 * Runs golden-master cases against the engine in THIS JVM and prints one RESULT line per case.
 * Part of "UPR ZX for Android (unofficial)". Licensed under GPLv3, like the
 * Universal Pokemon Randomizer ZX by Dabomstew, Ajarmar and contributors.
 *
 * Call sequence follows desktop NewRandomizerGUI (BUILD_SPEC 7.1, corrected):
 *   load ROM with a fresh handler (as loadROM / reinitializeRomHandler do)
 *   -> settings.tweakForRom(handler) -> settings.setRomName(loaded ROM name) -> RandomSource.seed(seed)
 *   -> settings.setCustomNames(FileFunctions.getCustomNames()) -> handler.setLog(log)
 *   -> new Randomizer(settings, handler, bundle, saveAsDirectory).randomize(out, log, seed)
 *
 * Usage: GoldenCase <rootPath> <workDir> <logDir> <caseSpec>...
 *   caseSpec = romPath|profileId|seed|settingsString   (several = consecutive jobs in one JVM)
 */
package uprzx.golden;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.SysConstants;
import com.dabomstew.pkrandom.romhandlers.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class GoldenCase {

    private static final RomHandler.Factory[] FACTORIES = new RomHandler.Factory[]{
            new Gen1RomHandler.Factory(), new Gen2RomHandler.Factory(), new Gen3RomHandler.Factory(),
            new Gen4RomHandler.Factory(), new Gen5RomHandler.Factory(), new Gen6RomHandler.Factory(),
            new Gen7RomHandler.Factory()
    };

    /** Profiles that switch on (nearly) everything get the desktop GUI's per-ROM visibility mask. */
    static final Set<String> GUI_MASKED_PROFILES = new HashSet<>(Arrays.asList("max", "max-everylevel"));

    public static void main(String[] args) throws Exception {
        String expectedRoot = args[0];
        Path workDir = Paths.get(args[1]);
        Path logDir = Paths.get(args[2]);
        Files.createDirectories(workDir);
        Files.createDirectories(logDir);

        // ROOT_PATH must be the harness work root, otherwise temp files and custom names go elsewhere.
        String root = SysConstants.ROOT_PATH;
        if (!new File(root).getCanonicalPath().equals(new File(expectedRoot).getCanonicalPath())) {
            fail("ROOT_PATH is " + root + " but the harness expected " + expectedRoot);
        }
        if (new File(root, SysConstants.customNamesFile).exists()) {
            fail("A customnames.rncn exists in ROOT_PATH; the golden master uses the bundled custom names only");
        }

        ResourceBundle bundle = ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle");
        for (int i = 3; i < args.length; i++) {
            Map<String, String> r = runCase(args[i], root, workDir, logDir, bundle, i - 3);
            StringBuilder sb = new StringBuilder("RESULT");
            for (Map.Entry<String, String> e : r.entrySet()) {
                sb.append('\t').append(e.getKey()).append('=').append(clean(e.getValue()));
            }
            System.out.println(sb);
            System.out.flush();
        }
    }

    private static Map<String, String> runCase(String spec, String root, Path workDir, Path logDir,
                                               ResourceBundle bundle, int jobIndex) {
        String[] parts = spec.split("\\|", 4);
        Path rom = Paths.get(parts[0]);
        String profileId = parts[1];
        long seed = Long.parseLong(parts[2]);
        String settingsIn = parts[3];

        Map<String, String> r = new LinkedHashMap<>();
        r.put("rom", rom.getFileName().toString());
        r.put("profile", profileId);
        r.put("seed", Long.toString(seed));
        r.put("job", Integer.toString(jobIndex));
        r.put("status", "ERROR");
        long start = System.currentTimeMillis();
        RomHandler handler = null;
        Path out = null;
        try {
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) pool.resetPeakUsage();
            HeapSampler.start();
            deleteTempFolders(root);
            r.put("romSha256", sha256(rom));

            for (RomHandler.Factory f : FACTORIES) {
                if (f.isLoadable(rom.toAbsolutePath().toString())) {
                    handler = f.create(RandomSource.instance());
                    break;
                }
            }
            if (handler == null) {
                r.put("error", "unsupported ROM (no handler)");
                return r;
            }
            handler.loadRom(rom.toAbsolutePath().toString());
            r.put("gen", Integer.toString(handler.generationOfPokemon()));
            r.put("romName", handler.getROMName());
            r.put("romCode", handler.getROMCode());
            r.put("romValid", Boolean.toString(handler.isRomValid()));
            r.put("tempFilesOnDisk", Long.toString(countTempFiles(root)));

            // The desktop GUI only offers "Random Every Level" evolutions for Gen 3+ (romLoaded:
            // peRandomEveryLevelRadioButton.setVisible(pokemonGeneration >= 3)). On Crystal the engine
            // overflows its evolution/moveset table with it (Gen2RomHandler.writeEvosAndMovesLearnt has
            // no space check), found 2026-11-27. Record such cases as not applicable instead of running them.
            if (profileId.equals("max-everylevel") && handler.generationOfPokemon() < 3) {
                r.put("status", "N/A");
                r.put("error", "not applicable: the desktop GUI does not offer Random Every Level evolutions for Gen 1-2");
                return r;
            }

            Settings settings = Settings.fromString(settingsIn);
            Settings.TweakForROMFeedback fb = settings.tweakForRom(handler);
            r.put("tweakChangedStarter", Boolean.toString(fb.isChangedStarter()));
            r.put("tweakRemovedCodeTweaks", Boolean.toString(fb.isRemovedCodeTweaks()));
            settings.setRomName(handler.getROMName()); // GUI createSettingsFromState does this
            if (GUI_MASKED_PROFILES.contains(profileId)) {
                GuiVisibility.apply(settings, handler); // options the GUI hides for this ROM never reach the engine
            }

            RandomSource.seed(seed);
            settings.setCustomNames(FileFunctions.getCustomNames());
            r.put("customNamesSha256", sha256(settings.getCustomNames().getBytes()));
            r.put("settings", settings.toString());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream log = new PrintStream(baos, false, "UTF-8");
            handler.setLog(log);

            out = workDir.resolve("out_" + jobIndex + "." + handler.getDefaultExtension());
            Files.deleteIfExists(out);
            int checkValue = new Randomizer(settings, handler, bundle, false)
                    .randomize(out.toAbsolutePath().toString(), log, seed);
            log.close();
            r.put("checkValue", Integer.toString(checkValue));
            r.put("outputSha256", sha256(out));
            r.put("outputBytes", Long.toString(Files.size(out)));

            byte[] logBytes = baos.toByteArray();
            r.put("logSha256", sha256(normaliseLog(logBytes)));
            writeLogWithBom(logDir.resolve(safe(rom.getFileName().toString()) + "__" + profileId + "__" + seed
                    + "__job" + jobIndex + ".log"), logBytes);
            r.put("tempBytesPeak", Long.toString(dirBytes(root)));
            r.put("status", "OK");
        } catch (Throwable t) {
            r.put("error", t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            System.err.println(sw);
        } finally {
            try {
                if (handler instanceof AbstractDSRomHandler) ((AbstractDSRomHandler) handler).closeInnerRom();
                if (handler instanceof Abstract3DSRomHandler) ((Abstract3DSRomHandler) handler).closeInnerRom();
            } catch (Throwable ignored) {
            }
            r.put("elapsedMs", Long.toString(System.currentTimeMillis() - start));
            r.put("peakHeapBytes", Long.toString(HeapSampler.stop()));
            try {
                deleteTempFolders(root);
                if (out != null && !Boolean.getBoolean("golden.keepOutputs")) Files.deleteIfExists(out);
            } catch (IOException ignored) {
            }
        }
        return r;
    }

    /** Log comparison ignores the elapsed-time line and line-ending differences (BUILD_SPEC 7.1). */
    static byte[] normaliseLog(byte[] raw) {
        String s = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n");
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            if (line.startsWith("Time elapsed: ")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeLogWithBom(Path p, byte[] log) throws IOException {
        try (OutputStream os = Files.newOutputStream(p)) {
            os.write(0xEF);
            os.write(0xBB);
            os.write(0xBF); // desktop writes a UTF-8 BOM before the log
            os.write(log);
        }
    }

    /**
     * Highest total heap in use, sampled every 20 ms on a background thread. (Adding up the
     * per-pool peaks, as before 2026-11-28, overstated it: the pools peak at different times.)
     * Includes garbage not yet collected, so it is an upper bound; the minimum heap a game needs
     * is found by running with a smaller --xmx.
     */
    static final class HeapSampler {
        private static volatile boolean running;
        private static volatile long max;
        private static Thread thread;

        static void start() {
            max = 0;
            running = true;
            thread = new Thread(() -> {
                while (running) {
                    long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                    if (used > max) max = used;
                    try { Thread.sleep(20); } catch (InterruptedException e) { return; }
                }
            }, "heap-sampler");
            thread.setDaemon(true);
            thread.start();
        }

        static long stop() {
            running = false;
            long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            if (used > max) max = used;
            try { if (thread != null) thread.join(1000); } catch (InterruptedException ignored) { }
            return max;
        }
    }

    private static List<File> tempFolders(String root) {
        List<File> l = new ArrayList<>();
        File[] fs = new File(root).listFiles();
        if (fs != null) for (File f : fs) if (f.isDirectory() && f.getName().startsWith("tmp_")) l.add(f);
        return l;
    }

    private static long countTempFiles(String root) throws IOException {
        long n = 0;
        for (File f : tempFolders(root)) {
            try (java.util.stream.Stream<Path> s = Files.walk(f.toPath())) {
                n += s.filter(Files::isRegularFile).count();
            }
        }
        return n;
    }

    private static long dirBytes(String root) throws IOException {
        long n = 0;
        for (File f : tempFolders(root)) {
            try (java.util.stream.Stream<Path> s = Files.walk(f.toPath())) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) n += Files.size(p);
            }
        }
        return n;
    }

    private static void deleteTempFolders(String root) throws IOException {
        for (File f : tempFolders(root)) deleteTree(f.toPath());
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (java.util.stream.Stream<Path> s = Files.walk(p)) {
            List<Path> all = new ArrayList<>();
            s.forEach(all::add);
            Collections.reverse(all);
            for (Path q : all) Files.deleteIfExists(q);
        }
    }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p), 1 << 20)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    static String sha256(byte[] b) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void fail(String msg) {
        System.out.println("FATAL\t" + msg);
        System.exit(3);
    }
}
