/*
 * Golden-master driver (BUILD_SPEC 7.1). Finds ROMs, runs every case in fresh JVMs twice,
 * then once more as consecutive jobs in a single JVM, compares, and writes a manifest.
 * Part of "UPR ZX for Android (unofficial)". Licensed under GPLv3, like the
 * Universal Pokemon Randomizer ZX by Dabomstew, Ajarmar and contributors.
 *
 * Usage: GoldenRunner --roms DIR --work DIR --profiles FILE --manifest FILE --label NAME
 *                     [--engine-rev REV] [--gens 4,5] [--only PROFILE[,PROFILE]] [--xmx 4608M]
 *                     [--timeout-min 20] [--keep-outputs] [--exclude-file FILE]
 * Exit codes: 0 all deterministic, 1 mismatch or fatal error, 2 no ROMs found (SKIPPED).
 */
package uprzx.golden;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GoldenRunner {

    /** Fields that must be identical between runs. Everything else is diagnostic. */
    static final List<String> COMPARED = Arrays.asList("status", "romSha256", "gen", "romName", "romCode",
            "romValid", "tweakChangedStarter", "tweakRemovedCodeTweaks", "customNamesSha256", "settings",
            "checkValue", "outputSha256", "outputBytes", "logSha256", "error");

    static final List<String> COLUMNS = Arrays.asList("rom", "profile", "seed", "status", "gen", "romCode",
            "romName", "romValid", "romSha256", "customNamesSha256", "settings", "tweakChangedStarter",
            "tweakRemovedCodeTweaks", "checkValue", "outputSha256", "outputBytes", "logSha256", "error",
            "deterministic", "sameProcessMatches", "elapsedMs", "peakHeapBytes", "tempFilesOnDisk", "tempBytesPeak");

    static final Set<String> ROM_EXT = new HashSet<>(Arrays.asList(
            "gb", "sgb", "gbc", "gba", "nds", "3ds", "cci", "cxi", "cia", "app"));

    public static void main(String[] a) throws Exception {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals("--keep-outputs")) opt.put("keep-outputs", "true");
            else opt.put(a[i].substring(2), a[++i]);
        }
        Path roms = Paths.get(req(opt, "roms"));
        Path work = Paths.get(req(opt, "work")).toAbsolutePath();
        Path profilesFile = Paths.get(req(opt, "profiles"));
        Path manifest = Paths.get(req(opt, "manifest"));
        String label = req(opt, "label");
        String xmx = opt.getOrDefault("xmx", "4608M"); // same as the desktop launcher scripts
        long timeoutMin = Long.parseLong(opt.getOrDefault("timeout-min", "20"));
        Set<Integer> gens = new HashSet<>();
        if (opt.containsKey("gens")) for (String g : opt.get("gens").split(",")) gens.add(Integer.parseInt(g.trim()));
        Set<String> only = new HashSet<>();
        if (opt.containsKey("only")) only.addAll(Arrays.asList(opt.get("only").split(",")));

        List<String[]> profiles = readProfiles(profilesFile, only);
        List<Path> romFiles = findRoms(roms);
        List<String> notVerified = new ArrayList<>();
        if (opt.containsKey("exclude-file")) {
            for (String line : Files.readAllLines(Paths.get(opt.get("exclude-file")), StandardCharsets.UTF_8)) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] ex = line.split("\t", 2);
                for (Iterator<Path> it = romFiles.iterator(); it.hasNext(); ) {
                    Path p = it.next();
                    if (p.getFileName().toString().contains(ex[0])) {
                        it.remove();
                        String why = p.getFileName() + ": " + (ex.length > 1 ? ex[1] : "excluded");
                        notVerified.add(why);
                        System.out.println("EXCLUDED, NOT VERIFIED: " + why);
                    }
                }
            }
        }
        System.out.println("Profiles: " + profiles.size() + ", ROM files found: " + romFiles.size());
        if (romFiles.isEmpty()) {
            System.out.println("SKIPPED: no ROM files under " + roms.toAbsolutePath()
                    + ". Nothing about randomization has been verified.");
            System.exit(2);
        }

        String root = work.resolve("root").toString() + File.separator;
        Path jobWork = work.resolve("jobs");
        Path logs = work.resolve("logs");
        List<Map<String, String>> rows = new ArrayList<>();
        boolean allOk = true;

        for (Path rom : romFiles) {
            List<String> specs = new ArrayList<>();
            for (String[] p : profiles) {
                for (String seed : p[1].split(",")) {
                    specs.add(rom.toAbsolutePath() + "|" + p[0] + "|" + seed.trim() + "|" + p[2]);
                }
            }
            System.out.println();
            System.out.println("=== " + rom.getFileName() + ": " + specs.size() + " cases ===");

            // Quick probe with the first case to learn the generation (for --gens filtering).
            List<Map<String, String>> runA = new ArrayList<>(), runB = new ArrayList<>();
            boolean skipRom = false;
            for (int i = 0; i < specs.size() && !skipRom; i++) {
                Map<String, String> ra = runJvm(root, jobWork, logs.resolve("runA"), xmx, timeoutMin, opt, specs.get(i)).get(0);
                if (i == 0 && !gens.isEmpty() && ra.containsKey("gen") && !gens.contains(Integer.parseInt(ra.get("gen")))) {
                    System.out.println("  skipped: generation " + ra.get("gen") + " not in --gens");
                    skipRom = true;
                    break;
                }
                if (i == 0 && "ERROR".equals(ra.get("status")) && !ra.containsKey("gen")) {
                    System.out.println("  could not load: " + ra.get("error"));
                    rows.add(ra);
                    skipRom = true;
                    break;
                }
                Map<String, String> rb = runJvm(root, jobWork, logs.resolve("runB"), xmx, timeoutMin, opt, specs.get(i)).get(0);
                runA.add(ra);
                runB.add(rb);
                boolean same = sameCompared(ra, rb);
                ra.put("deterministic", Boolean.toString(same));
                if (!same) allOk = false;
                System.out.printf("  %-14s seed %-18s %-5s %s  %6.1fs  heap %4d MB%s%n", ra.get("profile"), ra.get("seed"),
                        ra.get("status"), same ? "same twice" : "DIFFERENT BETWEEN RUNS: " + diff(ra, rb),
                        Long.parseLong(ra.getOrDefault("elapsedMs", "0")) / 1000.0,
                        Long.parseLong(ra.getOrDefault("peakHeapBytes", "0")) >> 20,
                        "ERROR".equals(ra.get("status")) ? "  (" + ra.get("error") + ")" : "");
            }
            if (skipRom) continue;

            // Consecutive jobs in one JVM: catches state left over between runs (BUILD_SPEC 7.1).
            List<Map<String, String>> seq = runJvm(root, jobWork, logs.resolve("sameProcess"), xmx,
                    timeoutMin * specs.size(), opt, specs.toArray(new String[0]));
            for (int i = 0; i < runA.size(); i++) {
                Map<String, String> s = i < seq.size() ? seq.get(i) : null;
                boolean same = s != null && sameCompared(runA.get(i), s);
                runA.get(i).put("sameProcessMatches", Boolean.toString(same));
                if (!same) {
                    System.out.println("  NOTE: job " + i + " (" + runA.get(i).get("profile") + ") differs when run after "
                            + "other jobs in the same JVM: " + (s == null ? "missing result" : diff(runA.get(i), s)));
                }
            }
            rows.addAll(runA);
        }

        writeManifest(manifest, label, opt.getOrDefault("engine-rev", "unknown"), rows, notVerified);
        long det = rows.stream().filter(r -> "true".equals(r.get("deterministic"))).count();
        long err = rows.stream().filter(r -> !"OK".equals(r.get("status"))).count();
        long sp = rows.stream().filter(r -> "false".equals(r.get("sameProcessMatches"))).count();
        System.out.println();
        System.out.println("Cases: " + rows.size() + ", deterministic: " + det + ", errors: " + err
                + ", differ in same process: " + sp);
        System.out.println("Manifest: " + manifest.toAbsolutePath());
        for (String nv : notVerified) System.out.println("NOT VERIFIED (excluded): " + nv);
        long loaded = rows.stream().filter(r -> r.containsKey("deterministic")).count();
        if (!allOk) {
            System.out.println("RESULT: FAIL (see DIFFERENT lines)");
            System.exit(1);
        } else if (loaded == 0) {
            System.out.println("RESULT: NOTHING VERIFIED (no file loaded as a supported ROM)");
            System.exit(2);
        }
        System.out.println("RESULT: PASS (every case that ran gave identical results twice"
                + (err > 0 ? "; " + err + " case(s) ended in an error, check they are expected" : "") + ")");
        System.exit(0);
    }

    static List<Map<String, String>> runJvm(String root, Path jobWork, Path logDir, String xmx, long timeoutMin,
                                            Map<String, String> opt, String... specs) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        cmd.add("-Xmx" + xmx);
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Dfile.encoding=UTF-8");
        if (opt.containsKey("keep-outputs")) cmd.add("-Dgolden.keepOutputs=true");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("uprzx.golden.GoldenCase");
        cmd.add(root);
        cmd.add(jobWork.toString());
        cmd.add(logDir.toString());
        cmd.addAll(Arrays.asList(specs));
        Files.createDirectories(jobWork);
        // One stderr file per process, next to the logs, so engine stack traces are kept for every case.
        Files.createDirectories(logDir);
        String[] first = specs[0].split("\\|", 4);
        String tag = specs.length == 1
                ? GoldenCase.safe(Paths.get(first[0]).getFileName().toString()) + "__" + first[1] + "__" + first[2]
                : GoldenCase.safe(Paths.get(first[0]).getFileName().toString()) + "__all";
        Path errFile = logDir.resolve("stderr__" + tag + ".txt");
        Path outFile = jobWork.resolve("last-stdout.txt");
        Process p = new ProcessBuilder(cmd).redirectError(errFile.toFile()).redirectOutput(outFile.toFile()).start();
        List<Map<String, String>> results = new ArrayList<>();
        String fatal = null;
        boolean timedOut = false;
        if (!p.waitFor(timeoutMin, TimeUnit.MINUTES)) {
            p.destroyForcibly().waitFor();
            timedOut = true;
        }
        try (BufferedReader br = Files.newBufferedReader(outFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("RESULT\t")) {
                    Map<String, String> m = new LinkedHashMap<>();
                    for (String kv : line.substring(7).split("\t")) {
                        int eq = kv.indexOf('=');
                        if (eq > 0) m.put(kv.substring(0, eq), kv.substring(eq + 1));
                    }
                    results.add(m);
                } else if (line.startsWith("FATAL\t")) {
                    fatal = line.substring(6);
                }
            }
        }
        if (fatal != null) {
            System.out.println("FATAL: " + fatal);
            System.exit(1);
        }
        while (results.size() < specs.length) {
            String[] parts = specs[results.size()].split("\\|", 4);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("rom", Paths.get(parts[0]).getFileName().toString());
            m.put("profile", parts[1]);
            m.put("seed", parts[2]);
            m.put("status", timedOut ? "TIMEOUT" : "CRASH");
            m.put("error", timedOut ? "no result within " + timeoutMin + " min; process killed"
                    : "JVM exited with code " + p.exitValue() + " before reporting; see " + errFile);
            results.add(m);
        }
        return results;
    }

    static boolean sameCompared(Map<String, String> a, Map<String, String> b) {
        for (String k : COMPARED) if (!Objects.equals(a.get(k), b.get(k))) return false;
        return true;
    }

    static String diff(Map<String, String> a, Map<String, String> b) {
        return COMPARED.stream().filter(k -> !Objects.equals(a.get(k), b.get(k))).collect(Collectors.joining(","));
    }

    static List<String[]> readProfiles(Path f, Set<String> only) throws IOException {
        List<String[]> l = new ArrayList<>();
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] p = line.split("\t");
            if (only.isEmpty() || only.contains(p[0])) l.add(p);
        }
        return l;
    }

    static List<Path> findRoms(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("golden-work"))
                    // 3DS game updates are .cia files too; they live in testroms/updates/ and are not ROMs.
                    .filter(p -> { for (Path part : dir.relativize(p)) if (part.toString().equalsIgnoreCase("updates")) return false; return true; })
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        int dot = n.lastIndexOf('.');
                        return dot > 0 && ROM_EXT.contains(n.substring(dot + 1).toLowerCase(Locale.ROOT));
                    })
                    .sorted().collect(Collectors.toList());
        }
    }

    static void writeManifest(Path f, String label, String rev, List<Map<String, String>> rows,
                              List<String> notVerified) throws IOException {
        if (f.getParent() != null) Files.createDirectories(f.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# Golden-master manifest: ").append(label).append('\n');
        sb.append("# engine-rev=").append(rev).append(" created=").append(Instant.now())
                .append(" java=").append(System.getProperty("java.version"))
                .append(" os=").append(System.getProperty("os.name")).append('\n');
        sb.append("# Compared fields: ").append(String.join(",", COMPARED)).append('\n');
        sb.append("# Diagnostic only: elapsedMs, peakHeapBytes, tempFilesOnDisk, tempBytesPeak. Contains no ROM data.\n");
        for (String nv : notVerified) sb.append("# NOT VERIFIED (excluded): ").append(nv).append('\n');
        sb.append(String.join("\t", COLUMNS)).append('\n');
        for (Map<String, String> r : rows) {
            List<String> cells = new ArrayList<>();
            for (String c : COLUMNS) cells.add(r.getOrDefault(c, ""));
            sb.append(String.join("\t", cells)).append('\n');
        }
        Files.write(f, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String req(Map<String, String> o, String k) {
        String v = o.get(k);
        if (v == null) throw new IllegalArgumentException("Missing --" + k);
        return v;
    }
}
