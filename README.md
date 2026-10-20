# Colony Foundations — Minecraft 1.21.5 Fabric

An experimental colony-building mod with a UI-driven blueprint catalog and an incremental villager builder.

## Features in this build
- Right-click the Town Hall to open the in-game build catalog; no commands are required.
- Includes a starter cottage; additional blueprints can be loaded from legally obtained datapacks.
- Rotate the selected schematic by 0°, 90°, 180°, or 270° before ordering construction.
- Project selection, rotation, pause state, and progress are persisted in the Town Hall block entity.
- A Builder Contract recruits an ordinary villager as the construction worker.
- Loads both Colony Foundations JSON files and compressed Structurize v1 NBT palettes/block arrays.

The public release does not download or bundle upstream MineColonies NBT schematics while the license of the exact pinned asset revision is being verified. The loader still supports Colony Foundations JSON and Structurize v1 block palettes from compatible local/datapack resources; see THIRD_PARTY_NOTICES.md before redistributing any third-party assets. Import in Colony Foundations is intentionally restricted to block states: tile entities, entities, special Structurize substitution blocks, and executable metadata are not applied. Unknown modded blocks are replaced by air. Air cells in the source blueprint do not erase existing terrain.

## Important limitations
This is not yet a complete MineColonies or Structurize port. Full profession AI, economy/storage, material delivery, building upgrade workflow, all building styles and structure types, blueprint editor, and the full ghost preview renderer are not implemented. The current UI and scheme placement are a foundation, not a claim of feature parity. Test in a disposable world.

## Build
GitHub Actions builds with JDK 22 and Gradle 8.12. The public workflow does not fetch unverified third-party schematic assets. To use external schematics, place a compatible datapack whose licence permits your intended use in the relevant world's datapacks directory.

## Colony ownership commands (experimental)

The owner can manage the currently persisted Town Hall colony from within 8 blocks of the hall. Commands are server-authoritative and resolve the Town Hall in the player's current dimension:

- `/colony info <x> <y> <z>` — show colony name, owner, level and member count (owner/member).
- `/colony rename <x> <y> <z> <new name>` — rename the colony (owner only).
- `/colony invite <x> <y> <z> <online player>` — grant membership (owner only).
- `/colony revoke <x> <y> <z> <online player>` — revoke membership (owner only).

This command workflow is an incremental addition, not a claim of full MineColonies permission parity. Build/CI and in-game multiplayer testing for this feature are still pending.
