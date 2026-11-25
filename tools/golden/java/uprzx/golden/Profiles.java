/*
 * Golden-master settings profiles (BUILD_SPEC 7.1).
 * Part of "UPR ZX for Android (unofficial)". Licensed under GPLv3, like the
 * Universal Pokemon Randomizer ZX by Dabomstew, Ajarmar and contributors.
 *
 * Profiles are built in code against the UNMODIFIED engine Settings class and
 * written to profiles.tsv as settings strings. The golden master only needs
 * deterministic, varied inputs: these do not have to be combinations the
 * desktop GUI would allow (that is checked separately in Phase 5).
 */
package uprzx.golden;

import com.dabomstew.pkrandom.MiscTweak;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.ExpCurve;
import com.dabomstew.pkrandom.pokemon.GenRestrictions;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public final class Profiles {

    public static final class Profile {
        public final String id;
        public final long[] seeds;
        public final String description;
        public final Settings settings;

        Profile(String id, String description, Settings settings, long... seeds) {
            this.id = id;
            this.description = description;
            this.settings = settings;
            this.seeds = seeds;
        }
    }

    /** Settings booleans left false in the "max" profile, with the reason. */
    private static final Map<String, String> MAX_EXCLUDED = new LinkedHashMap<>();
    static {
        MAX_EXCLUDED.put("setUpdateMovesLegacy", "mutually exclusive with updateMoves; covered by the legacy-update profile");
        MAX_EXCLUDED.put("setLimitPokemon", "needs GenRestrictions; covered by the limit-gen1 profile");
    }

    public static List<Profile> all() throws Exception {
        List<Profile> list = new ArrayList<>();

        list.add(new Profile("none", "Nothing changed (engine defaults)", base(), 1L));

        list.add(new Profile("typical", "Common choices: random starters, wilds, trainers, TMs, items",
                typical(), 1234567890L, -987654321012345L));

        // modes-k: every enum option set to values()[k % size]. k = 1..5 plus "none" (k = 0)
        // covers every value of every mutually exclusive mode at least once (largest enum has 6).
        for (int k = 1; k <= 5; k++) {
            Settings s = base();
            setAllEnums(s, k);
            s.setCustomStarters(new int[]{1, 4, 7});
            s.setUpdateMovesToGeneration(9);
            s.setUpdateBaseStatsToGeneration(8);
            list.add(new Profile("modes-" + k, "Every mode option set to choice " + k + " (wrapping)", s, 1000L + k));
        }

        Settings max = base();
        setAllEnums(max, 2);
        setAllBooleans(max, true);
        max.setCustomStarters(new int[]{1, 4, 7});
        max.setUpdateMovesToGeneration(9);
        max.setUpdateBaseStatsToGeneration(8);
        max.setGuaranteedMoveCount(4);
        max.setMovesetsGoodDamagingPercent(50);
        max.setTrainersForceFullyEvolvedLevel(40);
        max.setTrainersLevelModifier(20);
        max.setEliteFourUniquePokemonNumber(2);
        max.setAdditionalBossTrainerPokemon(2);
        max.setAdditionalImportantTrainerPokemon(1);
        max.setAdditionalRegularTrainerPokemon(1);
        max.setMinimumCatchRateLevel(3);
        max.setWildLevelModifier(10);
        max.setStaticLevelModifier(10);
        max.setTotemLevelModifier(10);
        max.setTmsGoodDamagingPercent(40);
        max.setTutorsGoodDamagingPercent(40);
        max.setCurrentMiscTweaks(allMiscTweaks());
        max.setSelectedEXPCurve(ExpCurve.SLOW);
        list.add(new Profile("max", "As many options on as possible (all booleans, choice 2 modes, all misc tweaks)", max, 424242L));

        Settings misc = typical();
        misc.setCurrentMiscTweaks(allMiscTweaks());
        list.add(new Profile("misc-all", "Typical plus every misc tweak", misc, 777L));

        Settings limit = typical();
        limit.setLimitPokemon(true);
        limit.setCurrentRestrictions(new GenRestrictions(1)); // Gen 1 species only: valid for every game
        list.add(new Profile("limit-gen1", "Typical with Limit Pokemon = Gen 1 only", limit, 31337L));

        Settings legacy = base();
        legacy.setUpdateMovesLegacy(true);
        legacy.setUpdateBaseStats(true);
        legacy.setUpdateBaseStatsToGeneration(6);
        legacy.setRandomizeMovePowers(true);
        legacy.setRandomizeMoveTypes(true);
        list.add(new Profile("legacy-update", "Legacy move update, base stat update, move data randomization", legacy, 55555L));

        return list;
    }

    /**
     * The GUI always sets these two (createSettingsFromState); Settings.toString() fails without them.
     * romName is replaced with the loaded ROM's name by GoldenCase, as the GUI does.
     */
    static Settings base() {
        Settings s = new Settings();
        s.setRomName("GOLDEN");
        s.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST); // GUI default (first combo entry)
        return s;
    }

    static Settings typical() throws Exception {
        Settings s = base();
        setEnum(s, "StartersMod", "COMPLETELY_RANDOM");
        setEnum(s, "AbilitiesMod", "RANDOMIZE");
        setEnum(s, "MovesetsMod", "RANDOM_PREFER_SAME_TYPE");
        setEnum(s, "TrainersMod", "RANDOM");
        setEnum(s, "WildPokemonMod", "AREA_MAPPING");
        setEnum(s, "WildPokemonRestrictionMod", "SIMILAR_STRENGTH");
        setEnum(s, "StaticPokemonMod", "RANDOM_MATCHING");
        setEnum(s, "TMsMod", "RANDOM");
        setEnum(s, "TMsHMsCompatibilityMod", "RANDOM_PREFER_TYPE");
        setEnum(s, "MoveTutorMovesMod", "RANDOM");
        setEnum(s, "MoveTutorsCompatibilityMod", "RANDOM_PREFER_TYPE");
        setEnum(s, "InGameTradesMod", "RANDOMIZE_GIVEN");
        setEnum(s, "FieldItemsMod", "RANDOM");
        setEnum(s, "ShopItemsMod", "RANDOM");
        setEnum(s, "PickupItemsMod", "RANDOM");
        s.setBanTrappingAbilities(true);
        s.setBanNegativeAbilities(true);
        s.setTrainersUsePokemonOfSimilarStrength(true);
        s.setUseMinimumCatchRate(true);
        s.setRandomizeTrainerNames(true);
        s.setChangeImpossibleEvolutions(true);
        s.setMakeEvolutionsEasier(true);
        s.setBanBadRandomFieldItems(true);
        return s;
    }

    static int allMiscTweaks() {
        int all = 0;
        for (MiscTweak t : MiscTweak.allTweaks) {
            all |= t.getValue();
        }
        return all;
    }

    /** Enum options use private setX(EnumType) methods; this finds them by parameter type. */
    private static List<Method> enumSetters() {
        List<Method> out = new ArrayList<>();
        for (Method m : Settings.class.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().startsWith("set") && p.length == 1 && p[0].isEnum()
                    && p[0].getEnclosingClass() == Settings.class) {
                m.setAccessible(true);
                out.add(m);
            }
        }
        out.sort(Comparator.comparing(Method::getName));
        return out;
    }

    static void setAllEnums(Settings s, int k) throws Exception {
        for (Method m : enumSetters()) {
            Object[] values = m.getParameterTypes()[0].getEnumConstants();
            m.invoke(s, values[k % values.length]);
        }
    }

    static void setEnum(Settings s, String enumSimpleName, String value) throws Exception {
        for (Method m : enumSetters()) {
            Class<?> e = m.getParameterTypes()[0];
            if (e.getSimpleName().equals(enumSimpleName)) {
                for (Object v : e.getEnumConstants()) {
                    if (((Enum<?>) v).name().equals(value)) {
                        m.invoke(s, v);
                        return;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No such enum option: " + enumSimpleName + "." + value);
    }

    static void setAllBooleans(Settings s, boolean value) throws Exception {
        for (Method m : Settings.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().startsWith("set") && p.length == 1 && p[0] == boolean.class
                    && !MAX_EXCLUDED.containsKey(m.getName())
                    && !m.getName().equals("setUpdatedFromOldVersion")) {
                m.invoke(s, value);
            }
        }
    }

    /** Writes profiles.tsv: id, seeds, settings string, description. */
    public static void main(String[] args) throws Exception {
        String outPath = args.length > 0 ? args[0] : "tools/golden/profiles.tsv";
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by uprzx.golden.Profiles from the unmodified engine. Do not edit by hand.\n");
        sb.append("# Settings strings are engine Settings.toString() (no version prefix). Engine version ")
                .append(com.dabomstew.pkrandom.Version.VERSION).append('\n');
        sb.append("# id\tseeds\tsettings\tdescription\n");
        for (Profile p : all()) {
            String str = p.settings.toString();
            // Round trip check: the string must load back to the same string.
            String again = Settings.fromString(str).toString();
            if (!again.equals(str)) {
                throw new IllegalStateException("Settings string does not round trip for profile " + p.id);
            }
            StringJoiner seeds = new StringJoiner(",");
            for (long seed : p.seeds) seeds.add(Long.toString(seed));
            sb.append(p.id).append('\t').append(seeds).append('\t').append(str).append('\t')
                    .append(p.description).append('\n');
        }
        Files.write(Paths.get(outPath), sb.toString().getBytes(StandardCharsets.UTF_8));
        PrintStream o = System.out;
        o.println("Wrote " + outPath);
    }
}
