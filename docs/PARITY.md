# Desktop parity checklist

Status values: `done`, `not applicable (reason)`, `todo`.

This file will list **every** control in `src/com/dabomstew/pkrandom/newgui/NewRandomizerGUI.form`, every dynamically created control in `NewRandomizerGUI.java`, every misc tweak in `MiscTweak.java`, and every item in BUILD_SPEC 6.4. A test (added in Phase 5) fails if a form control is missing from this list.

Counted in the form on 2026-11-25 (matches BUILD_SPEC 6.1):

| Control type | Count |
|---|---|
| JCheckBox | 127 |
| JRadioButton | 67 |
| JSlider | 10 |
| JComboBox | 7 |
| JSpinner | 4 |
| JButton | 7 |
| JLabel | 12 |
| `MiscTweak` constants (`new MiscTweak(`) | 23 |

The full per-control table is generated in Phase 5.

## BUILD_SPEC 6.4 items

| Item | Status |
|---|---|
| Load and validate ROMs with specific errors | todo |
| Unrecognised/modified ROM warnings and diagnostics | todo |
| All randomization options and misc tweaks | todo |
| `.rnqs` load/save incl. old-version upgrades and `tweakForRom` warnings | todo |
| Premade seeds, settings strings, `.rndp` presets | todo |
| Limit Pokemon dialog | todo |
| Custom names editor, `customnames.rncn` import/export | todo |
| Race mode and check value | todo |
| Batch randomization | todo |
| Log export with UTF-8 BOM | todo |
| 3DS game updates remembered per ROM code, LayeredFS output | todo |
| "Unload game on success" preference | todo |
