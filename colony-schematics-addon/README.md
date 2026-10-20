# Colony Schematics (separate mod)

A separate Fabric 1.21.5 JAR for browsing and test-placing bundled MineColonies citizen-house schematics.

## Build

Use JDK 22 and run:

```sh
gradle --no-daemon build
```

Output: `build/libs/colony-schematics-fabric-1.21.5-0.1.0-alpha.1.jar`.

## In-game

1. Put this JAR in `.minecraft/mods`.
2. In Creative mode, take **Schematic Tool** from the Functional item group.
3. Right-click with it to open the catalog of 20 citizen-house variants (4 styles × 5 tiers).
4. Select a house, choose rotation, and use **Place (creative)**. The structure is placed a few blocks in front of the player.

This is a prototype schematic tool, not an official Structurize port. It imports block palette/grid data only; block entities, inventories, entities, custom substitution logic, terrain adaptation, and survival material costs are not copied/implemented. Use in a test world first: placement overwrites blocks in the target volume.

The project contains original loader/GUI code plus schematic assets from `ldtteam/minecolonies-schematics`, pinned at commit `ae86ba500d3172220669657e0102861fa8b7f3be`. See `THIRD_PARTY_NOTICES.md` and `LICENSE`.
