# Colony Foundations — Minecraft 1.21.5 Fabric

An experimental colony-building mod with a UI-driven blueprint catalog and an incremental villager builder.

## Features in this build
- Right-click the Town Hall to open the in-game build catalog; no commands are required.
- Includes a starter cottage and 20 citizen-house schematics from four MineColonies styles (birch, dark oak, default wooden, stone), tiers 1–5.
- Rotate the selected schematic by 0°, 90°, 180°, or 270° before ordering construction.
- Project selection, rotation, pause state, and progress are persisted in the Town Hall block entity.
- A Builder Contract recruits an ordinary villager as the construction worker.
- Loads both Colony Foundations JSON files and compressed Structurize v1 NBT palettes/block arrays.

Official schematic assets are fetched from ldtteam/minecolonies-schematics at a pinned commit and bundled into the release JAR. See THIRD_PARTY_NOTICES.md for attribution and licensing. Import is intentionally restricted to block states: tile entities, entities, special Structurize substitution blocks, and any executable metadata are not applied. Unknown modded blocks are replaced by air. Air cells in the source blueprint do not erase existing terrain.

## Important limitations
This is not yet a complete MineColonies or Structurize port. Full profession AI, economy/storage, material delivery, building upgrade workflow, all building styles and structure types, blueprint editor, and the full ghost preview renderer are not implemented. The current UI and scheme placement are a foundation, not a claim of feature parity. Test in a disposable world.

## Build
GitHub Actions fetches the pinned NBT assets, then builds with JDK 22 and Gradle 8.12. If building locally, download the same assets using the instructions in .github/workflows/build.yml before running gradle clean build.
