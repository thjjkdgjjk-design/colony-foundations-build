# Colony Schematics — independent Fabric 1.21.5 add-on

A separate Fabric mod JAR for opening a catalog and test-placing Structurize v1 NBT blueprints.

## Current functionality

- Catalog of 20 citizen-house schematics (4 styles × 5 tiers).
- Quarter-turn rotation and Structurize primary_offset anchor handling.
- Palette and packed block-grid decoding.
- Attempts to restore block-entity NBT and serialized entities after placing block states.
- Reports required-mod metadata; unknown blocks are replaced with air.
- Safe limits for NBT size and structure dimensions.

## Run

1. Put the JAR in .minecraft/mods with Fabric Loader 0.16.14+ and Fabric API 0.119.5+ for Minecraft 1.21.5.
2. Launch a test world and use Schematic Tool from the Functional item group in Creative mode.
3. Select a house, choose rotation, and use Place (creative).
4. Test in a backup world first. Placement overwrites blocks in the target structure volume.

## Known gaps versus Structurize

This is not yet a complete Structurize port. It does not implement scan/save, mirroring/undo, visual block-model preview, palette replacement, tag/fluid/solid substitutions, requirement accounting, surface flattening, structure update handling, full NBT data fixing, or a survival build planner. Restoring modded block entities can fail if their mod is absent or their 1.20.1 NBT is incompatible with 1.21.5. This build has passed CI compilation only; actual in-game placement still needs testing.

## Build

Use JDK 22:

    gradle --no-daemon build

The resulting file is build/libs/colony-schematics-fabric-1.21.5-0.1.0-alpha.1.jar.

## Upstream source and resource licensing

The Structurize source is GPL-3.0. The MineColonies Java source is GPL-3.0, but its source/resources/assets/minecolonies/LICENSE explicitly says All Rights Reserved. The public schematics repository README describes the files as being for use in MineColonies, but does not establish an explicit blanket license in the pinned revision. Therefore this prototype includes only the limited 20-house set already used by its current CI; no broad claim is made that all original MineColonies art, assets, or blueprints can be redistributed freely. See root PORT_AUDIT.md and THIRD_PARTY_NOTICES.md.
