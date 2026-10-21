# MineColonies → Colony Foundations parity register

Updated: 2026-10-21. This is a traceable implementation register, not a claim of feature parity. Statuses apply to feat/courier-task-reassignment at the time of this revision.

## Official references and license boundary

- Official site: https://www.minecolonies.com/
- Official wiki: https://wiki.minecolonies.com/
- MineColonies source (project GPL-3.0): https://github.com/ldtteam/minecolonies
- Official wiki source: https://github.com/ldtteam/MinecoloniesWiki
- Structurize: https://github.com/ldtteam/Structurize
- BlockUI: https://github.com/ldtteam/BlockUI
- Domum Ornamentum: https://github.com/ldtteam/Domum-Ornamentum
- Official schematic repository: https://github.com/ldtteam/minecolonies-schematics
- Local license notes: THIRD_PARTY_NOTICES.md
- Existing source/dependency audit: PORT_AUDIT.md
- Pinned upstream ID inventory (source tree/blob hashes, registry IDs, asset/dependency counts): `MINECOLONIES_UPSTREAM_INVENTORY.md`

The audited source baseline is primarily MineColonies/Structurize for Minecraft 1.20.1 Forge, while this project targets Minecraft 1.21.5 Fabric. Feature details not yet traced to a pinned upstream source revision or official wiki page remain marked for verification. Registration or compilation alone never means functional parity.

## Status meanings

- Partial: some implementation exists, end-to-end parity is not established.
- Missing: no equivalent complete implementation found.
- Rights/source blocked: content is not redistributed until the exact asset revision's rights are established.
- Verify upstream: semantics must be confirmed against the chosen official source version.

## Functional inventory

| ID | Feature group to port | Official source | Current state | Dependencies | Completion criteria |
|---|---|---|---|---|---|
| CORE-01 | Founding, colony identity, owner, name, level, lifecycle | Official source and wiki above | Partial: Town Hall persists owner/name/level; a durable UUID and summary metadata now live in world PersistentState; full lifecycle is absent | Persistent colony model, Town Hall, permissions, migration | Stable identity across chunk unload/reload; found/delete/reactivate; migration and save/reload tests |
| CORE-02 | Membership, invitations, revocation, permission ranks | Official MineColonies Rank/Permissions source and Town Hall UI | Partial: member ranks and shared action profiles are persisted; rank assignment and rank-profile commands now exist. Six locally enforced actions are mapped; complete upstream action list, alliance behavior and UI remain unported. | CORE-01, commands, claim directory, ColonyRank, ColonyPermission | CI + save/reload migration tests; verify each action/rank in multiplayer; add remaining upstream actions and GUI |
| CORE-03 | Claims, boundaries, overlap, build/interaction protection | Official wiki; exact upstream settings to verify | Partial: fixed initial radius, persistent claim index and server hooks | CORE-01, PersistentState, server events | Break/place/use tests, overlapping claims, dimensions, unload/reload and removal |
| CORE-04 | Player-to-colony mapping, deletion, migration and corruption recovery | MineColonies source | Partial: persistent claim records now include colony UUID/name/owner display name/level/members and can be queried by UUID without loading the Town Hall; old records lazily gain deterministic IDs on re-registration | CORE-01, CORE-03 | Save/reload and legacy migration tests; identity conflict/corruption policy; lifecycle deletion and owner-to-colony indexes |
| CIT-01 | Citizen entity, names, skills, traits, age/family, needs, happiness, disease, education | Official wiki/source; enumerate exact fields from pinned version | Missing: vanilla villagers are used for basic worker behavior | Colony model, custom entity/data, AI, UI, persistence | Each upstream field has behavior, sync, save/load and gameplay tests |
| JOB-01 | Builder, assignment, navigation, staged construction | Official Builder's Hut documentation: https://minecolonies.com/wiki/buildings/builder/ | Partial: one server-side executor promotes persisted BUILD orders sequentially by priority; build progress and work-order records persist, and pause/blockage/resume is represented. Upgrade/repair/remove execution, builder-hut level enforcement, material transaction journaling, survival pathing parity and manual builder assignment remain incomplete. | Blueprints, materials, navigation, claims, orders | NBT codec round-trip and legacy save test; in-game unload/reload/restart and exactly-once material handling; real upgrade/repair/remove orders |
| JOB-02 | Farmer, fields, crop cycles, seeds and fertilizer | Official wiki/source | Partial: server worker loop for supported crops, including melon/pumpkin stem handling | Assignment, field, inventory, requests | Crop-by-crop parity matrix; full inventory/path/chunk reload tests |
| JOB-03 | Courier, warehouse routes, deliveries and receipt journal | Official wiki/source | Partial: request state, transfer identity/idempotency and receipt safeguards; replacement-worker changes lack confirmed CI | Requests, inventories, warehouses, persistent trip identity | Retry/restart produces no lost or duplicated items; replacement courier and chunk reload tests |
| JOB-04 | Miner, lumberjack, forester, fisherman, animal herders, guards, cook/baker, smelter and all other upstream jobs | Official project feature overview and profession pages | Missing: most job-specific work loops absent | Citizen AI, hut/job registry, tools, recipes, storage, requests | Diff against upstream job registry; each job must actually work and pass save/load tests |
| JOB-05 | Durable citizen job assignment directory and conflict prevention | `ColonyClaimState.CitizenAssignment`; source inventory in `MINECOLONIES_UPSTREAM_INVENTORY.md` | Implemented: world-persistent, server-authoritative record per villager UUID; idempotent refresh, cross-building conflicts fail closed, explicit release tombstones and legacy-save migration on assignment ticks | CORE-01, CORE-04, JOB-01, hut/Town Hall assignment | Save/reload, chunk unload, reassignment, duplicate-role and orphan recovery tests; gameplay verification still needed |
| BUILD-01 | Town Hall, houses, warehouse, university, barracks, school, tavern and every official hut | Official wiki/source block registry | Partial: Town Hall and hut/utility IDs exist; some models are generated placeholders | Block/item registry, block entities, assets, recipes, permissions | Per-block checklist: behavior, state, model, texture, loot, recipe, localization |
| BUILD-03 | Persistent typed building directory, level and citizen/job compatibility | ColonyClaimState.BuildingRecord; upstream IBuilding and BuildingEntry | Verified by CI on commit 7b239def4137cee45406e34369976c6f3267c858, Actions run #39525088496: records persist independently of loaded chunks; assignments require registered same-colony building and matching job/hut ID; demolition releases the assignment. No Minecraft gameplay run. | CORE-01, CORE-04, BUILD-01, JOB-05 | In-game placement/replacement/unload/reload/destruction tests; building modules/upgrades remain missing |
| BUILD-02 | Building levels, upgrades, requirements, repair and placement validation | Official wiki/source | Partial: one-project construction queue; original upgrade/repair flow absent | BUILD-01, blueprints, requests, builder, progression | Validated requirements per level and real upgrade/repair orders |
| BP-01 | Official styles, buildings, levels, schematic metadata and style selection | Schematic repo and Structurize | Partial: separate add-on reads compatible datapack palettes and supports catalog/rotation/mirror; not full parity | Structurize formats/datafixes, block registry, licensing | Versioned manifest per permitted style/file; real schemes pass anchor, transform and metadata tests |
| BP-02 | Blueprint tool/editor, ghost preview, material planning, staged placement, undo/redo | Structurize source | Partial: scan/save and Creative placement; full ghost preview, survival materials, undo/redo and worker integration missing | BP-01, renderer, inventory/requests, construction queue | Round-trip tests with block entities/entities; in-game preview and placement verification |
| ITEM-01 | Every original block, item, tool, contract, goggles and special item | MineColonies source registries | Missing/partial: limited local items/contracts and hut registry | Registries, assets, recipes, localization, jobs | Generated upstream-vs-port ID diff; every entry implemented or documented as blocked |
| REC-01 | Recipes, loot, tags, advancements, production chains, tools/equipment/fuel/food | MineColonies source data | Missing/partial: no exhaustive data/recipe parity | ITEM-01, jobs, buildings, storage | Resource ID diff and in-game crafting/production integration tests |
| LOG-01 | Requests, priorities, minimum stock, crafting dependencies, warehouses, racks and transport | Official wiki/source | Partial: request/transfer safeguards exist; complete warehouse/network lifecycle absent | Colony identity, jobs, inventories, recipes, journals | Request lifecycle/concurrency matrix; no loss/duplication after restart and chunk reload |
| COM-01 | Guards, combat, raids, hostile events and colony defense | Official wiki/source | Missing | Citizen AI, buildings, equipment, world events, permissions | Source-traced events/combat, deterministic tests and dedicated-server gameplay |
| PROG-01 | Research tree, university, requirements, unlocks, colony stats, quests/achievements | Official wiki/source | Missing/partial: Town Hall level field exists; original research/progression absent | Buildings, citizens, inventory, UI, persistence | Migrated research graph and server-side unlock/requirement tests |
| UI-01 | Town Hall/hut menus, worker assignment, inventories, task trees, resource detail, map, notifications and settings | MineColonies and BlockUI source | Partial: blueprint catalog/project status; BlockUI not ported | Networking, colony model, jobs, inventories | Every action server-validated; localization, multiplayer sync and reconnect tests |
| NET-01 | Client/server consistency, action validation, protocol compatibility | Fabric networking docs and upstream source | Partial: project/catalog payloads and checks exist | All gameplay systems | Invalid/stale/malicious packets rejected; dedicated server and reconnect tests |
| SAVE-01 | Save/load, chunk unload/reload, restart recovery and migrations | Upstream source | Partial: Town Hall, claims and request state persist; broad lifecycle unverified | All systems | Restart matrix for colony, jobs, requests, construction, claims, inventory and blueprints |
| ASSET-01 | Textures, models, sounds, animations, translations, manuals and GUI art | Upstream resources and per-asset licenses | Missing/blocked: placeholders for some content; MineColonies resource license says All Rights Reserved | Rights clearance and resource pipeline | Provenance/license manifest for each asset; no ARR redistribution without permission |
| DEP-01 | Fabric equivalents for Forge APIs and Structurize, BlockUI, Domum Ornamentum, MultiPiston, TownTalk integrations | Official dependency repositories | Missing/partial: Fabric core exists; dependency systems not fully ported | API mapping, registries, rendering, networking, licensing | Port and test dependencies before dependent MineColonies systems |
| DIST-01 | Packaging, config, compatibility, localization, documentation and releases | Repository build workflow | Partial: CI builds JARs/tests; gameplay and full compatibility matrix unverified | All modules | Exact-commit CI, client/server launch, and documented tested combinations |

## Blueprint and asset inventory policy

Do not confuse blueprint file count with implemented building parity. PORT_AUDIT.md records a pinned schematic snapshot with 598 NBT files in nine style folders; a separately supplied archive was scanned as 9,515 files across 23 styles. These are different source sets and must not be combined into one official count without a reproducible revision-level manifest. Public builds do not bundle the pinned 598-file snapshot because redistribution permission for that exact revision has not been established. Each candidate source revision needs a manifest containing hash, style, building/type/level metadata and license status.

MineColonies source code has a GPL-3.0 project license, but its resource directory contains a separate All Rights Reserved notice. The source-code license is not treated as a blanket license for textures, models, sounds, UI art or blueprints.

## Verified milestones

- Commit `bb94b75c3650d66e5880fcec41457a5107d6f204` passed GitHub Actions [run #39509235016](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39509235016) on JDK 22. `gradle clean build` (including test compilation and JUnit execution), the schematic add-on build, distribution preparation, artifact upload and test release all succeeded. Persistent colony identity and the four-argument `ClaimRecord` compatibility overload are **Verified by CI**, not **Verified in game**.

## Verified milestones

- Commit `bb94b75c3650d66e5880fcec41457a5107d6f204` passed Actions run [#39509235016](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39509235016): JDK 22, `gradle clean build` / JUnit, schematics build, distribution, artifact upload and test-release stages all succeeded. The next source-inventory change is separately checked by run [#39510122833](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39510122833); its result is recorded after completion.

## Dependency order

1. Verify courier-reassignment CI and review it before any merge.
2. Finish usable colony membership/permission workflows and persistence/security tests.
3. Extend the new persistent colony directory with migration/identity conflict tests, lifecycle handling, and reverse player-to-colony indexes.
4. Complete the blueprint format manifest and a rights-cleared content pipeline.
5. Build a data-driven block/item/recipe registry and connect hut levels to actual work orders.
6. Port required dependency APIs in dependency order.
7. Port every profession against the upstream job registry.
8. Port research/progression, combat/raids, remaining UI/resources/integrations.
9. Run client and dedicated-server gameplay tests separately from CI.

## Verification log — 2026-10-21

- GitHub heads at initial audit: main = 6aa8ae92a2ce4f6badfa446621911a7b6ed46b80; feature branch has since advanced with the parity register and owner-management command commits.
- Main Actions run #39503346438 succeeded for main HEAD 6aa8ae92a2ce4f6badfa446621911a7b6ed46b80.
- Feature Actions run #39508341303 succeeded for exact feature HEAD baa1eb4243cc065c034214bb21f97362f4a3a444: JDK 22 setup, main mod build/tests, schematics add-on build, distributable preparation and artifact upload all completed. This verifies that commit only; it does not verify the next change below.
- Code inventory confirms claim state/protection, Town Hall ownership/membership, blueprint catalog/queue, farmer logic, request state and a separate schematic add-on. This is not an in-game test.
- The owner command workflow includes /colony info, /colony rename, /colony invite and /colony revoke. Local git clone/build remains unavailable in this environment because it cannot resolve github.com. No Minecraft client launch, dedicated-server launch, multiplayer scenario or actual gameplay test has been executed in this audit.
- New implementation in commit 513b60467851f43e0f246a2a9b445bab5d91288b: durable colony UUID plus name/owner display name/level/member metadata in the existing world-persistent claim directory; UUID lookup works from persistent records without resolving a Town Hall block entity. Legacy records without identity receive a deterministic dimension/position/owner UUID when next registered.
- CI run #39509085912 for 513b60467851f43e0f246a2a9b445bab5d91288b failed at `:compileTestJava`: six existing `ColonyTerritoryRulesTest` cases still construct `ClaimRecord` with its original four-argument signature. Main sources compiled; tests were not executed. The follow-up commit restores that constructor as a compatibility overload; this fix remains **Implemented**, pending CI.

- New code stage: persisted `CitizenAssignment` records now share the Overworld `PersistentState` file with claims, preserving old saves via an optional `citizens` field. Assigning a worker requires a unique colony claim, a valid UUID/role/job and no other active assignment. This code is **Implemented** and awaiting its own CI run; no in-game testing has been performed.

- Lifecycle follow-up: on villager entity load, an active durable assignment reconstructs its role/building/job tags; a released assignment clears stale assignment tags and retires the tombstone. This follow-up is **Implemented**, pending its own CI run and gameplay test.

- Permission stage (this commit pending CI): per-member action grants persist on the Town Hall and claim record. Protection distinguishes breaks, block interaction and placement targets; project start and citizen assignment use their respective grants. This is **Implemented**, not yet **Verified**; shared rank presets/UI remain unported.

- CI correction log: permission implementation commit `7ecd4e75a6b20fbd7c46266e44cfc6dfa07d1987` failed run [#39511362157](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39511362157) at Java parsing in the nested Brigadier command declaration (`ColonyManagementCommands.java:61`). The follow-up rewrites the command tree using named argument builders; the full permission change remains unverified until that follow-up CI succeeds.

- Project authorization follow-up: server networking, pause, blueprint changes, and material deposits now all require `build_projects`; a member's mere colony membership is no longer enough. This change is **Implemented** and awaits a current-head CI result.

- CI correction log: `5755eea90612fbd761cdbfb67e792eebfd4fe9b7` failed [run #39511577909](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39511577909) because `ColonyProtection.java` referenced `BlockItem` without importing `net.minecraft.item.BlockItem`. The next commit adds that import explicitly; the permission implementation is still unverified until its own CI passes.

- CI correction log: commit `692eb9d29f25c95f2d72e45d0c8a7275301fc38d` failed [run #39511809445](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39511809445) because Java forbids capturing compact-record constructor parameter `members` after it was reassigned during normalization. Follow-up captures `Set.copyOf(members)` as `validMembers`; current permission feature stays unverified until the follow-up run passes.

- CI correction log: commit `2fec28f6fc588710c6594f2ce303ee0544f1997b` compiled successfully but failed one JUnit assertion in `persistedClaimsRemainIndexedAfterTheTownHallChunkUnloads` (line 41): that test expected membership alone to satisfy the legacy `canModify` convenience method. The assertion now expects denial by default, consistent with the new action-grant model; dedicated tests assert an explicit action grant succeeds. This fix awaits CI.

- Citizen lifecycle follow-up (pending CI): finishing a building project now removes the builder role as well as its assignment tag and retires the release tombstone for the loaded villager; an unloaded builder keeps a release marker for load-time cleanup. Replacing/removing a work-hut block releases the worker assignment, clears a loaded villager's role tags, or leaves a tombstone for unloaded entity recovery. This prevents orphaned citizens from remaining permanently unavailable for hiring.

- CI correction log: commit `641833131d8c318613ebeaff444ebb6483bc7995` failed run [#39512311789](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39512311789): Minecraft 1.21.5 requires `onStateReplaced(BlockState, ServerWorld, BlockPos, boolean)`, not the older signature with `World`, `newState`, and five arguments. The follow-up adopts the actual target-version signature; behavior remains unverified until CI passes.

- Rank implementation static review: added the explicit `ColonyRank` command import before CI to avoid a cross-package symbol resolution error. The rank model is awaiting CI and its JUnit tests; no gameplay verification has been performed.

- Current rank-profile implementation is pending CI for the exact commit: stored ranks are `officer/friend/neutral/hostile`, the owner is reserved, invite defaults to neutral, shared profile edits clear pre-rank grant overrides and update the persistent claim index. The six action IDs are a local subset, not the entire upstream Action enum. The Minecraft gameplay GUI/scenarios have not been run.

- Building directory milestone (current commit pending CI): an optional `buildings` field extends the existing PersistentState without invalidating prior saves; Town Halls and typed huts are registered on chunk load/placement and removed on destruction. Citizen assignments now reject missing buildings and mismatched job/building IDs. This is **Implemented**, not yet **Verified**, pending CI and in-game tests.


| BUILD-04 | Durable work-order lifecycle, status, progress, priority and query | Official WorkOrderBuilding, WorkManager, IWorkOrder and WorkOrderType sources | Partial: multi-BUILD persistent queue, ordering/priority, cancellation and reprioritization are integrated; BUILD is the only executed type. UPGRADE/REPAIR/REMOVE currently have level/status models only, no command/executor path. Explicit lifecycle transition guard and atomic same-site admission are implemented. | CORE-01, BUILD-03, JOB-01, BP-01, NET-01 | NBT codec round-trip and legacy-save tests; CI; gameplay order lifecycle; wire UPGRADE/REPAIR/REMOVE to validated registered BuildingRecord and real schematic operations |

- Work-order lifecycle integration is Verified by CI on commit eaa9706429ebc1dd4fcaf5fd7ed9b74a8494dea6, Actions run #39526391040: current BUILD state/progress is persisted on start, tick recovery, pause, blockage, block placement and completion; /colony work-orders lists persisted history. No in-game verification. The following queue change is pending its own CI.

- Work-order codec correction: the metadata portion (priority/sequence/status/progress/issue/requester) is stored as one nested codec record to stay within DataFixerUpper's generated record-codec arity. The original work_orders field remains optional for prior world saves. This correction is pending its own CI run.

- CI correction log: work-order model commit 327b8b718a95abe948b153584f897fe2b682a37f failed run #39525819588 at compile time because DataFixerUpper RecordCodecBuilder.group supports at most 16 fields and the first WorkOrderRecord codec supplied 20. Commit cb303c0ccf701583cc82aeb8af02d1895cd1fc61 nests lifecycle metadata; its CI was still running at the next update. This follow-up additionally rejects work-order sites outside the colony claim, matching the construction protection rules.

- CI correction log: commit cb303c0ccf701583cc82aeb8af02d1895cd1fc61 compiled and ran tests, but one work-order test failed because the model accepted a site far outside the Town Hall claim; the follow-up requires the site position to be inside the colony claim and adjusts no tests to weaken the check. Full integration code must pass its own current-head workflow before being marked verified.

- Integration review correction: the new work-order listing command calls ColonyClaimState directly, so the follow-up adds its explicit import before the current-head CI result. This is a compile-level correction, not a change to permissions or test expectations.

- CI correction log: integration commit 5daf3dcde21636ffff72f880b6fb417166df8734 failed [Actions run #39526226355](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39526226355) during compileJava because the newly added work-order command lacked ServerWorld and ColonyClaimState imports. This follow-up supplies ServerWorld; ColonyClaimState was added in b5074ff4104e929302124c2b8c2cfafcf281a98e. No tests were disabled, and the feature remains unverified until the current-head workflow succeeds.

- Multi-BUILD queue implementation was introduced in `87bd404a23a44b518c983b402cb8dde15d91f776` and confirmed again on HEAD `fa53d3771e0e1e232c3077baebd2a152ac07da21` by Actions run [#39527517667](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39527517667): Gradle compile/tests, schematics add-on build, distributable validation and artifact upload all succeeded. Supports durable queued BUILD orders, priority, sequential promotion, shared material planning, cancel and reprioritize. No in-game verification.

- Queue compile-review correction: both work-order blueprint parsers now treat Identifier.tryParse as nullable (Fabric/Yarn 1.21.5 mapping), instead of calling Optional.orElse. This is a defensive pre-CI correction to the queued multi-BUILD scheduler commit; current-head CI remains authoritative.

- Work-order management addendum (verified on HEAD `fa53d3771e0e1e232c3077baebd2a152ac07da21`, Actions run #39527517667): `/colony order-priority` edits only a zero-progress queued order; `/colony order-cancel` cancels only queued/blocked zero-progress orders and routes through the matching Town Hall. Active/progressed orders are protected. UPGRADE/REPAIR/REMOVE still have data-model validation only and lack execution pipelines.

- Queue cancellation safety correction: `/colony order-cancel` now routes through the Town Hall and refuses orders belonging to another Town Hall, active orders, completed orders or any order with progress. Cancelling the selected but not-started order clears the Town Hall's selected UUID so the scheduler can advance instead of leaving a terminal UUID attached. This correction is pending CI.

- Queue admission audit at HEAD `fa53d3771e0e1e232c3077baebd2a152ac07da21`: found and corrected a transactional defect in `upsertWorkOrder`. It previously cancelled same-site queued/blocked orders while scanning conflicts, then could reject the new order on a later active/progressed conflict, leaving a partial mutation. Conflict validation now completes before any cancellation; a regression test seeds a legacy/conflicting state and asserts rejection preserves both original statuses. This fix is **Implemented**, pending CI for its commit; no in-game test.

- Work-order transition audit: the persistent store previously prevented reopening terminal states but accepted other impossible transitions. `ColonyWorkOrderStatus.canTransitionTo` now enforces `QUEUED -> ACTIVE/BLOCKED/CANCELLED`, `ACTIVE -> PAUSED/BLOCKED/COMPLETED`, `PAUSED -> ACTIVE/BLOCKED`, `BLOCKED -> ACTIVE/CANCELLED`, terminal states closed, and idempotent same-state updates. Regression coverage checks that rejected transitions do not mutate the stored record. Pending CI and gameplay validation.

- CI correction log: `c3d1e03962eef1a02a474c07d829e670968ed733` failed [run #39528356658](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39528356658) in one test because its old happy-path setup attempted `QUEUED -> COMPLETED` directly. The regression fix takes the valid `QUEUED -> ACTIVE -> COMPLETED` path while keeping a separate assertion that terminal orders cannot be reactivated. No production checks or tests are being relaxed.

- Persistence test addendum (current commit pending CI): added an NBT codec round-trip test proving a queued BUILD record retains sequence, priority, status, site and blueprint ID, plus a legacy-save test that removes the optional newer maps and claim metadata fields and verifies old claim records remain readable. This is an automated codec test, not a world restart or gameplay test.

- Completed-structure directory addendum (current commit pending CI): finishing a BUILD registers its blueprint/style ID, level, rotation, anchor and world-space footprint in the durable building directory. Both queued and direct GUI starts reject footprints outside the colony and conflicts with registered buildings or non-terminal orders. Completed halls retry registration on tick to recover from interrupted saves. Legacy BuildingRecord data decodes as a point-sized non-blueprint record. This is metadata/overlap groundwork, not a claim that UPGRADE/REPAIR/REMOVE execute yet.

- CI correction log: building-directory commit `63355df0fb3d510c228ee679856284931db2a6a7` compiled, but one codec round-trip test failed when it seeded a building-map entry under the noncanonical key `structure`; production lookup keys are `dimension#packedPosition`. Follow-up fixes the fixture to use that canonical key and keeps the building lookup, blueprint metadata, bounds and legacy-save assertions intact.

- Test distribution documentation: `PLAYTEST.md` was added in the feature branch for the current two-JAR release. It explicitly distinguishes automated CI/JUnit evidence from in-game verification; the commit's own CI is required before distributing it as the current validated build.

- REPAIR executor implementation is in commit `d79ee48f0f40026ba515983d8a0ce0bb789f88d3` and is **Implemented**, pending CI: new `/colony repair` admission validates the registered building's colony/style/rotation/level/footprint and checks priority/order conflicts; scheduler activates BUILD and REPAIR and persists the operation type/level in Town Hall NBT. The common builder loop skips correct blocks and places only missing/replaceable cells; solid mismatches block without overwrite. No in-game verification. See the following CI run for verification status.
