# MineColonies → Fabric 1.21.5 port audit

Updated: 2026-10-20. This file is the working parity checklist, not a claim of completion.

## User-supplied source inputs inspected

| Project/source | Source target noted in archive | Java files | Files mentioning Forge/NeoForge APIs (approx.) | Current status |
|---|---|---:|---:|---|
| MineColonies | 1.20.1 / Forge | 2,102 | 499 | Not ported; main colony runtime is still a small prototype |
| Structurize | 1.20.1 / Forge | 223 | 86 | Partial NBT parser/placement prototype in Colony Schematics |
| BlockUI | 1.21.1 / NeoForge | 109 | 33 | Not ported |
| Domum Ornamentum | 1.20.1 / Forge | 226 | 94 | Not ported |
| MultiPiston / Piston Unlimited | 1.20 / Forge | 12 | 8 | Not ported |
| TownTalk | 1.19.2 / Forge | 2 | 1 | Not ported |
| ChristmasColonies | JAR supplied; source archive not present in this upload set | — | — | Not ported |
| MineFortress | JAR supplied separately; source archive not present in this upload set | — | — | Not ported |

These counts are source scans, not an estimate of the complete number of required changes. The practical dependency order is BlockUI → Structurize and Domum Ornamentum → MultiPiston/TownTalk integrations → MineColonies core → optional add-ons.

## Current project layers

### Colony Foundations
- Fabric bootstrap, Town Hall block entity, builder-contract item, vanilla-villager work loop and a basic blueprint-building queue.
- Does not provide the original colony model, profession system, worker-specific AI, requests, warehouse logistics, colony permissions, research, raids, diseases, happiness, or original MineColonies blocks/items.

### Colony Schematics (separate JAR)
- Separate Fabric mod ID and catalog item.
- 20 citizen-house NBT assets built into the JAR (4 styles × 5 tiers); a separate user-provided datapack contains 9,515 .blueprint files from 23 architecture styles.
- Structurize v1 palette/packed-block decoding, rotation, primary anchor, block-entity and entity NBT paths.
- 2-corner region scan/save into a portable Blueprint Scroll; stored blueprint can be placed from Creative mode.
- Server-synchronized, paginated/searchable catalog supports bundled examples and enabled world datapacks under data/colony_schematics/blueprints/.
- Rotation and two mirror modes are now implemented in catalog placement and entity transforms; CI compiled the mirror code on commit `fc7a6733bbf7e95484b13b8d46921c3905908f2f`. In-game transform correctness remains unverified. Not yet a complete Structurize implementation: data fixing, special substitution/tag handlers, undo/redo, true block-model ghost preview, survival requirement/material accounting, and staged build-work-order integration remain unimplemented.

## Missing MineColonies feature groups

- Colony lifecycle and data: create/found colony, colony IDs, chunk/area ownership, distance limits, permissions, allies, colony rename/delete/reactivate, player-to-colony mapping, persistence and migration.
- Citizens: custom citizen entity, names/skills/traits/age/family/needs, hiring, housing and beds, schedules, pathfinding, death/respawn, happiness, disease and education.
- Jobs and worker AI: builder, courier, miner, farmer, fisherman, lumberjack, forester, cook, baker, smelter, crafter, guard and remaining job types, each with job-specific goals and hut integration.
- Construction: current Town Hall has a persisted single-project queue, blueprint ID, rotation, site offsets, progress, a basic tagged vanilla-villager builder and material stock; this is a prototype rather than MineColonies' builder AI. Prior issue: obstructed positions were silently skipped while advancing progress, and resources could be consumed without checking placement success. Corrective tranche now pauses on obstructions, world-border/build-height/unloaded-chunk violations, preserves progress and records a localized reason, treats already-matching target blocks as recovered work, and consumes materials only after successful `setBlockState`. Build/test pending for this tranche. Full build/upgrade/repair orders, builder-hut-driven construction, material requests, staged placement, callbacks/events and full sync remain incomplete.
- Logistics/economy: warehouse, courier networks, racks, inventories, requests, request priorities, crafting dependencies, delivery, minimum stock, tools/equipment, fuel/food and trade.
- Buildings/content: town hall, builder hut, citizen housing, all job huts, warehouse, university, guard towers, tavern, bakery, farms, decoration blocks, block-entity logic, recipes, loot tables, tags, advancements and translations.
- Gameplay systems: research tree, raids, guards/combat, barbarians/monsters, colony stats and ranking, quests/achievements, expeditions where present in the source version.
- Client/UI: BlockUI replacement, town hall/hut screens, lists, worker assignment, inventories, task trees, resource details, schematic selection/editor, minimap/colony map and sync packets.
- Dependency APIs: Domum Ornamentum block/material palette, MultiPiston, TownTalk, integrations/optional mods and 1.20.1→1.21.5 API/data-schema migration.

## Test gates

1. CI clean build on JDK 22 (source bytecode release 21); `fabric.mod.json` minimum Java is 21, matching the runtime bytecode target.
2. Client launch with Fabric API only; verify entrypoints/resources and language fallback.
3. Dedicated server launch and multiplayer join.
4. Schematic round-trip: palette, block states, block entities, entities, rotation, anchor, missing-mod handling and reload.
5. Save/reload colony data and active work orders.
6. Citizen hiring, housing, work assignment, navigation, death/recovery and job completion.
7. Regression suite for world border, dimension build height, chunk boundaries/unloads, occupied cells, missing blocks/mods and malformed NBT.
8. Profile server tick cost as colony/citizen/build-order count grows.

## Resource licensing note

The MineColonies Java source uses GPL-3.0, while its own resources directory has a separate assets/minecolonies/LICENSE stating “All Rights Reserved”. The public schematic repository's README documents its intended use for MineColonies but does not itself establish a broad redistribution grant in the revision reviewed. Do not treat source-code GPL as a blanket license for textures, sounds, GUI art, models or all schematic assets. Replacement art or an explicit asset license/permission is required before claiming that the entire original visual content has been ported and redistributed.

## Current incremental delivery (2026-10-20)

The separate colony-schematics-addon is the active first workstream: making the Structurize schematic format usable on Fabric and enabling the full blueprint tree through a server datapack. The datapack output stays separate from the public JAR because the original MineColonies resource tree carries a separate “All Rights Reserved” notice; only the user who supplied the archive is receiving this generated pack here. Source-code GPL terms are not assumed to override that separate resource notice.

## Corrective tranche — 2026-10-20 (pending CI)

- Both mods declare `java >=21`; Gradle continues using a JDK 22 toolchain and `options.release = 21`. This keeps the developer JDK requested by the project without preventing Java 21 clients from satisfying Minecraft 1.21.5's runtime requirement.
- Town Hall construction no longer silently advances past a solid obstruction. The active work order pauses and a reason/target coordinate is persisted and displayed; the player can clear the obstruction and resume.
- Before placement, the builder checks dimension build height, world border, and chunk-loaded state without forcing a distant chunk to load. An existing matching target block counts as recovered progress. Material is deducted only after `setBlockState` reports success.
- The separate schematic catalog's user-facing strings were moved to English/Russian language keys and the built-in style labels were localized.
- These source changes are not considered verified until CI completes. No in-game client/server test has been run.

## Verification statement

CI compilation proves source compatibility with the compile environment only; it does not prove behavior in Minecraft. The complete MineColonies feature set is not yet ported. Build success and gameplay tests must be tracked separately.
