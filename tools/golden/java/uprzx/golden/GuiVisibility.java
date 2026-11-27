/*
 * Desktop GUI visibility mask for the golden-master "max" profiles.
 * Part of "UPR ZX for Android (unofficial)". Licensed under GPLv3, like the
 * Universal Pokemon Randomizer ZX by Dabomstew, Ajarmar and contributors.
 *
 * NewRandomizerGUI.createSettingsFromState stores 38 options as "isSelected() && isVisible()",
 * and romLoaded() decides visibility from the loaded ROM. A hidden option never reaches the
 * engine from the desktop GUI, and the engine can fail if it does (found 2026-11-27 on Gen 1-3:
 * "out of space" saving movesets, Gen 2 double battle index errors, Gen 3 static music NPE).
 * This class applies the same rules, copied from romLoaded() at tag baseline-engine
 * (NewRandomizerGUI.java lines ~2658-3049). Options the GUI does not mask are left alone.
 */
package uprzx.golden;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

public final class GuiVisibility {

    private GuiVisibility() {
    }

    public static void apply(Settings s, RomHandler rh) {
        int gen = rh.generationOfPokemon();
        boolean megas = rh.hasMegaEvolutions();

        // Pokemon traits
        if (!(gen >= 4)) s.setBanIrregularAltFormes(false);                       // noIrregularAltFormesCheckBox
        if (!megas) s.setBaseStatsFollowMegaEvolutions(false);                     // pbsFollowMegaEvosCheckBox
        if (!megas) s.setTypesFollowMegaEvolutions(false);                         // ptFollowMegaEvosCheckBox
        if (!(gen >= 3 && megas)) s.setAbilitiesFollowMegaEvolutions(false);       // paFollowMegaEvosCheckBox (abilities panel is gen >= 3)
        if (!(gen >= 7)) s.setEvosAllowAltFormes(false);                           // peAllowAltFormesCheckBox

        // Starters
        if (!rh.hasStarterAltFormes()) s.setAllowStarterAltFormes(false);          // spAllowAltFormesCheckBox
        if (!rh.supportsStarterHeldItems()) {                                      // spRandomizeStarterHeldItems / spBanBadItems
            s.setRandomizeStartersHeldItems(false);
            s.setBanBadRandomStarterHeldItems(false);
        }

        // Static Pokemon (all inside "if (romHandler.canChangeStaticPokemon())")
        boolean statics = rh.canChangeStaticPokemon();
        if (!(statics && rh.hasMainGameLegendaries())) s.setLimitMainGameLegendaries(false);
        if (!(statics && rh.hasStaticAltFormes())) s.setAllowStaticAltFormes(false);
        if (!(statics && gen == 6 && !rh.forceSwapStaticMegaEvos())) s.setSwapStaticMegaEvos(false);
        if (!(statics && rh.hasStaticMusicFix())) s.setCorrectStaticMusic(false);

        // Moves and movesets
        if (!(gen < 8)) s.setUpdateMoves(false);                                   // mdUpdateMovesCheckBox
        if (!rh.supportsFourStartingMoves()) s.setStartWithGuaranteedMoves(false); // pmsGuaranteedLevel1MovesCheckBox
        if (!(gen >= 7)) s.setEvolutionMovesForAll(false);                         // pmsEvolutionMovesCheckBox

        // Trainers
        if (!rh.hasFunctionalFormes()) s.setAllowTrainerAlternateFormes(false);    // tpAllowAlternateFormesCheckBox
        if (!megas) s.setSwapTrainerMegaEvos(false);                               // tpSwapMegaEvosCheckBox
        if (!(gen >= 3)) s.setDoubleBattleMode(false);                             // tpDoubleBattleModeCheckBox
        if (!(gen >= 3)) {                                                         // additionalPokemonAvailable
            s.setAdditionalBossTrainerPokemon(0);
            s.setAdditionalImportantTrainerPokemon(0);
            s.setAdditionalRegularTrainerPokemon(0);
        }
        if (!(gen >= 3)) {                                                         // trainersHeldItemSupport
            s.setRandomizeHeldItemsForBossTrainerPokemon(false);
            s.setRandomizeHeldItemsForImportantTrainerPokemon(false);
            s.setRandomizeHeldItemsForRegularTrainerPokemon(false);
            s.setConsumableItemsOnlyForTrainers(false);
            s.setSensibleItemsOnlyForTrainers(false);
            s.setHighestLevelGetsItemsForTrainers(false);
        }
        if (!(gen >= 3)) s.setEliteFourUniquePokemonNumber(0);                     // tpEliteFourUniquePokemonCheckBox
        if (!(gen >= 7)) s.setShinyChance(false);                                  // tpRandomShinyTrainerPokemonCheckBox
        if (!(gen >= 3)) s.setBetterTrainerMovesets(false);                        // tpBetterMovesetsCheckBox

        // Wild Pokemon
        if (!(gen != 1)) {                                                         // wpRandomizeHeldItems / wpBanBadItems
            s.setRandomizeWildPokemonHeldItems(false);
            s.setBanBadRandomWildPokemonHeldItems(false);
        }
        if (!(gen == 5)) s.setBalanceShakingGrass(false);                          // wpBalanceShakingGrassPokemonCheckBox
        if (!rh.hasWildAltFormes()) s.setAllowWildAltFormes(false);                // wpAllowAltFormesCheckBox

        // TMs
        if (!(gen < 7)) s.setFullHMCompat(false);                                  // tmFullHMCompatibilityCheckBox
    }
}
