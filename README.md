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
- `/colony invite <x> <y> <z> <online player>` — grant membership (owner or a member with `manage_members`). New members start with no action grants.
- `/colony revoke <x> <y> <z> <online player>` — revoke membership (owner or a member with `manage_members`).
- `/colony permission <x> <y> <z> <online player> <action> <true|false>` — owner changes one of `break_blocks`, `place_blocks`, `use_blocks`, `manage_members`, `build_projects`, `assign_workers`.

This is an incremental per-member action model. MineColonies shared rank presets, permission GUI, full event semantics and gameplay parity are not implemented yet; run CI and test in a disposable multiplayer world.


### Colony permission ranks (incremental port)

- `/colony rank <x> <y> <z> <online player> <officer|friend|neutral|hostile>` assigns a member rank.
- `/colony rank-permission <x> <y> <z> <rank> <action> <true|false>` edits an action for all current and future members of that rank.
- `/colony permission <x> <y> <z> <online player> <action> <true|false>` is retained as a compatibility command and edits the target member's rank profile, so all peers at that rank are affected.
- Supported local actions: `break_blocks`, `place_blocks`, `use_blocks`, `manage_members`, `build_projects`, `assign_workers`.

The model persists `OWNER/OFFICER/FRIEND/NEUTRAL/HOSTILE` rank IDs and profile actions. This is not full MineColonies permission parity: upstream actions beyond the six enforced here, alliance/hostile interaction, and the Town Hall permissions GUI remain open. Old saves without a permission-version marker migrate existing members to `officer` to preserve the prior broad access; intermediate saves keep explicit grants until the affected rank profile is edited.


### Durable work-order history (experimental)

The current Town Hall BUILD project now mirrors its order ID, blueprint, site, rotation, requester, lifecycle status, progress and obstruction reason into the world-persistent colony directory. Use `/colony work-orders <x> <y> <z>` near the Town Hall to inspect recorded orders (requires `build_projects`). This is a history/state foundation, not a full queue: BUILD still uses the single existing Town Hall executor; actual UPGRADE, REPAIR and REMOVE execution, parallel orders, priorities in scheduling, and building-module workflows remain unimplemented.


### Queued BUILD orders (experimental)

- `/colony queue <x> <y> <z> <blueprint_id> <rotation 0-3> <offset_x> <offset_z> <priority -10..10>` adds another BUILD order without replacing the order currently being executed.
- When the Town Hall executor is idle or its current BUILD finishes, the highest-priority queued BUILD for that Town Hall is promoted automatically. Ties use creation sequence. Construction still uses one executor and a shared Town Hall inventory; materials needed across active and queued BUILD orders are counted together.
- `/colony repair <hall x y z> <building x y z> <priority -10..10>` queues a repair for a previously completed, registered schematic building. Its saved blueprint, rotation, level and footprint must still match the available datapack. The existing builder executor skips correct blocks and fills missing/replaceable cells; a conflicting solid block pauses the order for manual resolution rather than overwriting it.
- Queueing now executes BUILD and the first conservative REPAIR path. UPGRADE and REMOVE still have validated models but no execution pipeline. Builder capability temporarily uses colony level as a proxy because the original Builder Hut/citizen skill model is not ported. Back up the world before testing; CI is not a gameplay test.

- `/colony order-priority <x> <y> <z> <sequence> <priority -10..10>` changes the priority of an unstarted queued order.
- `/colony order-cancel <x> <y> <z> <sequence>` cancels only a queued/blocked order with zero placed blocks; active/progressed orders are protected.


### Lumberjack (experimental partial worker)

A villager can be assigned to hut_lumberjack using worker_contract. Put an axe in the hut inventory; the worker searches for vertical tree trunks with leaves, within the colony claim and in loaded chunks, then stores harvested logs in hut storage. The current tree base is saved in hut NBT. Branch traversal, leaves/saplings, replanting, modded trees and axe durability are not implemented. See PLAYTEST.md.
