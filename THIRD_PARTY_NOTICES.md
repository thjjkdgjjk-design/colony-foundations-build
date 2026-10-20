# Third-party notices

## MineColonies schematic assets — not bundled in current public builds

- Upstream project: https://github.com/ldtteam/minecolonies-schematics
- Previously used snapshot: https://github.com/ldtteam/minecolonies-schematics/commit/ae86ba500d3172220669657e0102861fa8b7f3be
- That snapshot contains 598 `.nbt` files across nine style directories but has no root `LICENSE` file in its Git tree. Its README describes the repository as schematics for use in MineColonies, but does not itself provide an explicit broad redistribution grant.
- The current upstream `master` has a GPL-3.0 license file, but its tree is a metadata/branch index and does not contain those 598 NBT files. We therefore cannot use the presence of that license file alone as proof that it covers the older snapshot's schematic content.
- To avoid implying permission that has not been confirmed, GitHub Actions no longer downloads or packages those NBT assets in public JARs. The code continues to support compatible datapacks supplied separately by users who have permission to use them. Do not re-enable public bundling until the authors or an applicable asset license confirm redistribution rights.

## Original MineColonies resources

The supplied MineColonies source archive contains source/resources/assets/minecolonies/LICENSE with the text All Rights Reserved. Its original GUI artwork, textures, models, sounds and other resources are not licensed by the root GPL source license. This repository does not claim a right to republish those original resources.

## Code

The new Colony Schematics and Colony Foundations implementation code is an independent Fabric port/prototype. The upstream Java projects MineColonies and Structurize are GPL-3.0 projects; consult their full source licenses for any derivative source modules. No endorsement by LDTTeam is implied.
