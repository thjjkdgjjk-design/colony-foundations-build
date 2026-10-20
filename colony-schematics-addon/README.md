# Colony Schematics (separate mod)

A standalone Fabric 1.21.5 mod for cataloging, scanning, saving, and placing Structurize-style blueprints. It is separate from Colony Foundations (`colony_schematics` mod ID).

## Downloads

The build workflow publishes this add-on as its own JAR:
`colony-schematics-fabric-1.21.5-0.1.0-alpha.1.jar`.

## In-game controls

1. Put the JAR in `.minecraft/mods`; install Fabric API for Minecraft 1.21.5. JDK 22 is used to build the mod; the mod itself supports Java 21+ as required by Minecraft 1.21.5.
2. In Creative mode, take **Инструмент чертежей** from the Functional item group.
3. Right-click in the air to open the catalog. The list is requested from the server and is paginated; type to search and press Enter to refresh. Backspace removes a search character; Home clears the filter.
4. Select a blueprint, choose a 90-degree rotation or mirror mode, then press **Разместить (творческий режим)**.
5. To scan a structure, use the tool to right-click a block for the first corner and then the opposite corner. The resulting **Свиток чертежа** stores palette, block states, block-entity NBT and entities and can be used to place a copy.

## Loading the full MineColonies blueprint set

The JAR contains 20 built-in citizen-house examples. The separately delivered `colony-schematics-minecolonies-blueprints-1.21.5.zip` datapack contains 9,515 blueprint files from the MineColonies source archive uploaded for this port. Place the ZIP, without extracting it, in:

`<minecraft>/saves/<world>/datapacks/`

Run `/reload` or re-enter the world. The mod scans enabled server datapacks under `data/colony_schematics/blueprints/` and sends the index to the client catalog. In multiplayer, install the mod on the server and the same datapack on the server, not just on the client.

The pack includes multiple architectural styles and buildings, but this does **not** mean all of the blocks those blueprints reference have been ported. Unsupported blocks are replaced with air. In particular, Domum Ornamentum and the remaining MineColonies dependency chain are not yet complete Fabric ports.

## Known limitations

- This is an ongoing porting prototype, not a complete Structurize replacement.
- Structure placement is Creative-only and can overwrite terrain.
- Structurize metadata such as palette, packed block grid, required-mod metadata, primary anchor, block-entity NBT and entity NBT is read. The full old-to-new DataFixer pipeline, tag substitutions, survival materials and staged worker construction are not implemented.
- Rotation and mirroring are implemented in the current catalog, but their results still need in-game regression testing. Undo/redo, a true per-block-model ghost renderer, survival material planning, staged worker construction, and full client/server test coverage remain unfinished.
- Blueprints are from the user's uploaded source archive. Review and follow the licenses applicable to each original asset; the JAR's public release does not bundle the 9,515-file datapack.

## Source and licensing

This code port is distributed under GPL-3.0-only; a copy of the license is included. See `THIRD_PARTY_NOTICES.md`. MineColonies's own resource tree contains a separate `All Rights Reserved` notice for some assets, so the GPL on Java code must not be treated as an automatic license for every texture or other non-code resource.
