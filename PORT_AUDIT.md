# Port audit — Fabric 1.21.5 / Java 22

## Scope
The uploaded Forge MineColonies, Structurize, BlockUI, Domum Ornamentum, MultiPiston, TownTalk, and ChristmasColonies sources are inputs for a future porting effort. This repository does **not** contain a complete port of those projects and intentionally does not redistribute their code/assets.

## Implemented in this clean-room prototype
- Fabric entrypoint and registrations.
- Town Hall block and block entity.
- Builder Contract callback that marks a vanilla villager as a builder.
- A hard-coded vanilla cottage blueprint.
- Persistent build progress with one placement every 10 server ticks, provided a builder can reach the work spot.
- Build uses vanilla blocks only and skips nonreplaceable cells.

## Still out of scope
- MineColonies professions, worker AI, economy, storage/warehouses, requests, research, raids, colony claims, network menus, and full blueprint/structure system.
- Porting upstream Forge APIs to Fabric in the dependency projects.
- Compatibility testing against a large modpack.

## CI
GitHub Actions is configured to compile against Minecraft 1.21.5 with Fabric Loom and JDK 22, emit Java 21 bytecode, upload a build artifact, and attach the distributable JAR to a release only after successful compilation.
