# Colony Foundations — Fabric 1.21.5

This repository contains an experimental, clean-room colony building prototype for Minecraft 1.21.5, targeting Fabric and Java 22.

## Scope
- A craftable Town Hall block.
- A Builder Contract that recruits a vanilla villager as a builder.
- A simple built-in cottage blueprint.
- A persistent build order that places blocks incrementally near the Town Hall.

This is **not** a complete MineColonies port. It does not yet include the full MineColonies professions, economy, warehouse system, GUI, Structurize blueprints, or complete worker AI.

## Build
GitHub Actions builds the project with JDK 22 and Gradle. The resulting JAR is attached to a GitHub Release for each successful build.

Target: Minecraft 1.21.5 + Fabric Loader + Fabric API + Java 22. The mod emits Java 21 bytecode for Minecraft compatibility.

Please test on a disposable world first and report the relevant `latest.log` if a crash occurs.
