# MineColonies upstream registry inventory — pinned source sample

Updated: 2026-10-21. Target: Minecraft Java 1.21.5, Fabric Loader, JDK 22.  
**This is an upstream ID inventory, not a declaration of functional parity.**

## Source snapshot and compatibility

Source repository: [ldtteam/minecolonies](https://github.com/ldtteam/minecolonies), ref `version/main`, recursive Git tree **`4eb1b870b7cb2339b79f231033df5bbef27b2646`**. Tree enumeration returned 22,492 entries, including 2,102 Java source files. Reproduce this inventory by re-reading the source files and comparing the blob hashes:

| Upstream file | Blob SHA |
|---|---|
| `gradle.properties` | `20a1f903bcac46a1ff762d1e9df6d673b0838c9e` |
| `src/main/java/com/minecolonies/apiimp/initializer/ModItemsInitializer.java` | `0b7665b370560ead27db12fdfd6aa0ded30c006c` |
| `src/main/java/com/minecolonies/apiimp/initializer/ModBlocksInitializer.java` | `2ef5a9e485cc62a994592dcbb40a6c159858cd79` |
| `src/main/java/com/minecolonies/apiimp/initializer/EntityInitializer.java` | `ff57035de02f39598c7a04ce401b3d35f7c5128a` |
| `src/main/java/com/minecolonies/api/colony/buildings/ModBuildings.java` | `3af56d3978264ea9c7e63622cfc474569cf0ea3d` |
| `src/main/java/com/minecolonies/apiimp/initializer/ModBuildingsInitializer.java` | `bb413fab9b1774d1e3fbcff5804a324d0c80ffbb` |
| `src/main/java/com/minecolonies/api/colony/jobs/ModJobs.java` | `45b1a34c3f54bcec59edb10feec9303d18a2368a` |
| `src/main/java/com/minecolonies/apiimp/initializer/ModJobsInitializer.java` | `b30d150d2b4055d3650b099ae3caede32b2efe5a` |
| `src/main/java/com/minecolonies/api/entity/ModEntities.java` | `570e20398b2487f1e891f7aa5c31b6a288f8110f` |

The upstream `gradle.properties` declares Minecraft 1.20.1, Java 17, Forge 47.1.3, Structurize 1.20.1-1.0.818, BlockUI 1.20.1-1.0.190-snapshot, Domum Ornamentum 1.20.1-1.0.288-snapshot and MultiPiston 1.20-1.2.30-ALPHA; it lists Structurize, Domum Ornamentum, BlockUI, MultiPiston and TownTalk as required dependencies. These are source-context pins, **not Fabric 1.21.5 compatible packages**. BlockUI's separately inspected default branch targets NeoForge/Minecraft 1.21.1, so compatibility cannot be inferred from the name or from Java 22 alone.

## Registry counts and local parity

Counts are derived from the exact named files above (they count declarations/identifiers, not working behavior).

| Area | Official upstream snapshot | Local branch at inventory time | Status |
|---|---:|---|---|
| Explicit item registration identifiers in `ModItemsInitializer.java` | 146 | 2 plain registered contracts (`builder_contract`, `worker_contract`) | **Missing/partial** — no matching specialized item classes/behaviors/recipes |
| Building IDs in `ModBuildings.java` | 55 constants | 61 local hut/utility IDs plus Town Hall | **Partial only** — local huts use a shared generic block class; generated oak-plank placeholder models are not upstream buildings |
| Job IDs in `ModJobs.java` | 51 constants | generic role tags plus partial builder/farmer/courier loops | **Missing/partial** — no complete job data registry or per-profession work AI |
| Entity type symbols in `ModEntities.java` | 47 fields | no custom MineColonies entity types/classes found in local Java tree | **Missing** — vanilla villagers are not Citizen entities, visitors, raiders or projectile types |
| Public block/crop symbols in `api/blocks/ModBlocks.java` | 86 fields listed below | one Town Hall block plus 61 generic local hut/utility registrations | **Missing/partial** — a related ID does not implement block entity, level, requirement, inventory or crop semantics |
| JSON block/item models | 386 | small subset plus generated placeholders | **Missing** |
| Texture paths | 4,170 | upstream artwork is not bundled | **Rights review required** |
| Sound / sound JSON paths | 68 | upstream sounds not bundled | **Rights review required** |
| Blueprint resources in `src/main/resources/blueprints/minecolonies/` | 9,515 `.blueprint` / `.nbt` paths in this source tree | no such original snapshot bundled into public JAR | **Partial** — parser support and redistribution permission are separate checks |
| Files under `src/main/resources/data/` | 554, including 227 JSON / 23 JSON tag files | limited local datapack/recipe resources | **Missing/partial** |

## Explicit item IDs

These 146 identifiers are extracted from explicit `new ResourceLocation(Constants.MOD_ID, "...")` registrations in `ModItemsInitializer.java`. They cover different types of content, including food, tools, scrolls, armor, deployers, projectiles and hostile-camp spawn eggs.

`adventure_token`, `amazonchiefegg`, `amazonegg`, `amazonspearmanegg`, `ancienttome`, `apple_pie`, `assistanthammer_diamond`, `assistanthammer_gold`

`assistanthammer_iron`, `baked_salmon`, `banner_rally_guards`, `barbarcheregg`, `barbarianegg`, `barbchiefegg`, `borscht`, `bread_dough`

`build_goggles`, `butter`, `cabochis`, `cake_batter`, `cheddar_cheese`, `cheese_pizza`, `cheese_ravioli`, `chicken_broth`

`chiefsword`, `chorus_bread`, `clipboard`, `colony_banner`, `colonymap`, `compost`, `congee`, `cooked_rice`

`cookie_dough`, `corn_chowder`, `cornmeal`, `creamcheese`, `drownedpiratearcheregg`, `drownedpiratecaptainegg`, `drownedpirateegg`, `eggdrop_soup`

`eggplant_dolma`, `feta_cheese`, `firearrow`, `fish_dinner`, `fish_n_chips`, `flatbread`, `fried_rice`, `golden_bread`

`hand_pie`, `iron_scimitar`, `kebab`, `kimchi`, `lamb_stew`, `large_empty_bottle`, `large_milk_bottle`, `large_soy_milk_bottle`

`large_water_bottle`, `lembas_scone`, `magicpotion`, `manchet_bread`, `manchet_dough`, `meat_ravioli`, `milky_bread`, `mint_jelly`

`mint_tea`, `mintchoco_cheesecake`, `mistletoe`, `muffin`, `muffin_dough`, `mummyarcheregg`, `mummyegg`, `mushroom_pizza`

`mutton_dinner`, `norsemenarcheregg`, `norsemenchiefegg`, `pasta_plain`, `pasta_tomato`, `pea_soup`, `pepper_hummus`, `pharaoegg`

`pharaoscepter`, `pierogi`, `pirate_boots`, `pirate_cap`, `pirate_chest`, `pirate_hat`, `pirate_leggins`, `pirate_legs`

`pirate_shoes`, `pirate_top`, `piratearcheregg`, `piratecaptainegg`, `pirateegg`, `pita_hummus`, `plain_cheesecake`, `plate_armor_boots`

`plate_armor_chest`, `plate_armor_helmet`, `plate_armor_legs`, `polenta`, `potato_soup`, `pottage`, `questlog`, `ramen`

`raw_noodle`, `raw_pumpkin_pie`, `resourcescroll`, `rice_ball`, `santa_hat`, `scan_analyzer`, `scepterbeekeeper`, `scepterguard`

`scepterlumberjack`, `scepterpermission`, `schnitzel`, `scroll_area_tp`, `scroll_buff`, `scroll_guard_help`, `scroll_highlight`, `scroll_tp`

`shieldmaidenegg`, `sifter_mesh_diamond`, `sifter_mesh_flint`, `sifter_mesh_iron`, `sifter_mesh_string`, `soysauce`, `spear`, `spicy_eggplant`

`spicy_grilled_chicken`, `squash_soup`, `steak_dinner`, `stew_trencher`, `stuffed_pepper`, `stuffed_pita`, `sugary_bread`, `supplycampdeployer`

`supplychestdeployer`, `sushi_roll`, `tacos`, `tofu`, `tortillas`, `veggie_quiche`, `veggie_ravioli`, `veggie_soup`

`yogurt`, `yogurt_with_berries`

Local `ModItems` registers only `builder_contract` and `worker_contract`; this is not an implementation of the list above.

## Building definition IDs

All 55 constants in `ModBuildings.java`:

`archery`, `baker`, `barracks`, `barrackstower`, `blacksmith`, `builder`, `chickenherder`, `combatacademy`

`composter`, `cook`, `cowboy`, `crusher`, `deliveryman`, `farmer`, `fisherman`, `guardtower`

`residence`, `library`, `lumberjack`, `miner`, `sawmill`, `shepherd`, `sifter`, `smeltery`

`stable`, `stonemason`, `stonesmeltery`, `swineherder`, `townhall`, `warehouse`, `postbox`, `florist`

`enchanter`, `university`, `hospital`, `stash`, `school`, `glassblower`, `dyer`, `fletcher`

`mechanic`, `plantation`, `tavern`, `concretemixer`, `rabbithutch`, `beekeeper`, `mysticalsite`, `graveyard`

`netherworker`, `simplequarry`, `mediumquarry`, `largequarry`, `alchemist`, `kitchen`, `gatehouse`

In the pinned `ModBuildingsInitializer.java`, the `largequarry` entry is commented out; it remains a constant but is not actively registered in this source snapshot. Re-check this detail if updating the upstream reference.

## Job / profession IDs

All 51 identifiers in `ModJobs.java`:

`placeholder`, `builder`, `deliveryman`, `miner`, `lumberjack`, `farmer`, `undertaker`, `fisherman`

`baker`, `cook`, `shepherd`, `cowboy`, `swineherder`, `chickenherder`, `smelter`, `ranger`

`knight`, `marksman`, `huscarl`, `cavalry`, `composter`, `student`, `archertraining`, `combattraining`

`sawmill`, `blacksmith`, `stonemason`, `stonesmeltery`, `crusher`, `sifter`, `florist`, `enchanter`

`researcher`, `healer`, `pupil`, `teacher`, `glassblower`, `dyer`, `fletcher`, `mechanic`

`planter`, `rabbitherder`, `concretemixer`, `beekeeper`, `netherworker`, `quarrier`, `druid`, `alchemist`

`chef`, `stablemaster`, `cookassistant`

A job isn't ported just because its ID exists. Functional parity requires citizen job data, target building modules, work AI, tools, production chains, work-order lifecycle, resource requests, retry/failure behavior, save/load, client sync and multiplayer checks.

## Entity types

The 47 public type fields in `ModEntities.java`:

`CITIZEN`, `VISITOR`, `FISHHOOK`, `MERCENARY`, `BARBARIAN`, `ARCHERBARBARIAN`, `CHIEFBARBARIAN`, `PIRATE`

`CHIEFPIRATE`, `ARCHERPIRATE`, `SITTINGENTITY`, `MUMMY`, `PHARAO`, `ARCHERMUMMY`, `NORSEMEN_ARCHER`, `SHIELDMAIDEN`

`NORSEMEN_CHIEF`, `AMAZON`, `AMAZONSPEARMAN`, `AMAZONCHIEF`, `MINECART`, `CAVALRY_HORSE`, `FIREARROW`, `MC_NORMAL_ARROW`

`DRUID_POTION`, `SPEAR`, `DROWNED_PIRATE`, `DROWNED_CHIEFPIRATE`, `DROWNED_ARCHERPIRATE`, `CAMP_BARBARIAN`, `CAMP_ARCHERBARBARIAN`, `CAMP_CHIEFBARBARIAN`

`CAMP_PIRATE`, `CAMP_CHIEFPIRATE`, `CAMP_ARCHERPIRATE`, `CAMP_AMAZON`, `CAMP_AMAZONSPEARMAN`, `CAMP_AMAZONCHIEF`, `CAMP_MUMMY`, `CAMP_PHARAO`

`CAMP_ARCHERMUMMY`, `CAMP_NORSEMEN_ARCHER`, `CAMP_SHIELDMAIDEN`, `CAMP_NORSEMEN_CHIEF`, `CAMP_DROWNED_PIRATE`, `CAMP_DROWNED_CHIEFPIRATE`, `CAMP_DROWNED_ARCHERPIRATE`

The upstream `EntityInitializer.java` registers runtime entity types using Forge builders; these cannot be added as Fabric registrations unchanged. Each type still needs an implementation, renderer/model/texture, attributes, goals, save/load and client/server sync. No custom MineColonies entity class/type equivalent was found in the project at this inventory stage.

## Block / crop symbols

Public symbols from upstream `api/blocks/ModBlocks.java` (86 fields):

`blockHutTownHall`, `blockHutHome`, `blockHutMiner`, `blockHutLumberjack`, `blockHutBaker`, `blockHutBuilder`, `blockHutDeliveryman`

`blockHutBlacksmith`, `blockHutStonemason`, `blockHutFarmer`, `blockHutFisherman`, `blockHutGuardTower`, `blockHutWareHouse`, `blockHutShepherd`

`blockHutCowboy`, `blockHutSwineHerder`, `blockHutChickenHerder`, `blockHutBarracks`, `blockHutBarracksTower`, `blockHutCook`, `blockHutSmeltery`

`blockHutComposter`, `blockHutLibrary`, `blockHutArchery`, `blockHutCombatAcademy`, `blockHutSawmill`, `blockHutStoneSmeltery`, `blockHutCrusher`

`blockHutSifter`, `blockHutFlorist`, `blockHutEnchanter`, `blockHutUniversity`, `blockHutHospital`, `blockHutSchool`, `blockHutGlassblower`

`blockHutDyer`, `blockHutFletcher`, `blockHutMechanic`, `blockHutPlantation`, `blockHutTavern`, `blockHutRabbitHutch`, `blockHutConcreteMixer`

`blockHutBeekeeper`, `blockHutMysticalSite`, `blockHutGraveyard`, `blockHutNetherWorker`, `blockSimpleQuarry`, `blockMediumQuarry`, `blockHutAlchemist`

`blockHutKitchen`, `blockHutGateHouse`, `blockHutStable`, `blockStash`, `blockPostBox`, `blockConstructionTape`, `blockRack`

`blockGrave`, `blockNamedGrave`, `blockWayPoint`, `blockBarrel`, `blockDecorationPlaceholder`, `blockScarecrow`, `blockPlantationField`

`blockCompostedDirt`, `blockColonyBanner`, `blockColonyWallBanner`, `blockIronGate`, `blockWoodenGate`, `farmland`, `floodedFarmland`

`blockColonySign`, `blockBellPepper`, `blockCabbage`, `blockChickpea`, `blockDurum`, `blockEggplant`, `blockGarlic`

`blockOnion`, `blockSoyBean`, `blockTomato`, `blockRice`, `blockButternutSquash`, `blockCorn`, `blockMint`

`blockNetherPepper`, `blockPeas`

Upstream hut blocks are not plain decorative anchors: base classes interact with Structurize placement metadata, named/leveled building anchors, requirements, block entities and inventories. Local `ModHuts.IDS` (61 identifiers) is:

`hut_alchemist`, `hut_archery`, `hut_baker`, `hut_barracks`, `hut_barracks_tower`, `hut_beekeeper`, `hut_blacksmith`, `hut_builder`

`hut_chicken_herder`, `hut_citizen`, `hut_combat_academy`, `hut_composter`, `hut_concrete_mixer`, `hut_cook`, `hut_cowboy`, `hut_crusher`

`hut_deliveryman`, `hut_dyer`, `hut_enchanter`, `hut_farmer`, `hut_fisherman`, `hut_fletcher`, `hut_florist`, `hut_gate_house`

`hut_glassblower`, `hut_graveyard`, `hut_guard_tower`, `hut_hospital`, `hut_kitchen`, `hut_library`, `hut_lumberjack`, `hut_mechanic`

`hut_miner`, `hut_mystical_site`, `hut_nether_worker`, `hut_plantation`, `hut_rabbit_hutch`, `hut_sawmill`, `hut_school`, `hut_shepherd`

`hut_sifter`, `hut_smeltery`, `hut_stable`, `hut_stone_smeltery`, `hut_stonemason`, `hut_swine_herder`, `hut_tavern`, `hut_university`

`hut_warehouse`, `simple_quarry`, `medium_quarry`, `large_quarry`, `stash`, `post_box`, `colony_rack`, `plantation_field`

`colony_flag_banner`, `colony_flag_wall_banner`, `construction_tape`, `colony_gate`, `colony_barrel`

Those local IDs are generic local blocks/utility anchors. They do not implement the matching MineColonies building entries, all module producers, original crop systems or original artwork. A separate per-block registry-ID / block-entity / state-variant pass remains required.

## Architectural inventory still open

The upstream code uses separate `Colony`, `ColonyManager`, `CitizenData`, `CitizenManager`, building managers, request/work managers, animal/reproduction/event managers, and raid/combat systems. The new durable colony UUID directory is only an incremental foundation, not a port of those managers.

Still needing a source-backed, row-level pass: exact block registration IDs and state variants, block entities, item components/crafting remainders, all recipe JSON and custom serializers, tags/loot/advancements, building levels/styles/requirements/modules, research/unlock data, full textures/models/sounds license manifest, and per-blueprint style/type/level/hash metadata.

## Dependency and redistribution boundary

- The current MineColonies source declares Structurize, BlockUI, Domum Ornamentum, MultiPiston and TownTalk as required dependencies. The pinned dependency versions/platforms above are not established as compatible with Fabric 1.21.5.
- Structurize, BlockUI and Domum Ornamentum repositories are source references, not proof that their releases can be plugged in without a port. Check platform, Minecraft/API version, code/resource license and transitive dependencies independently.
- MineColonies Java source is GPL-3.0, while `src/main/resources/assets/minecolonies/LICENSE` says **All Rights Reserved**. The root source license is not assumed to permit republishing the original art, textures, models, GUI or audio.
- The separate schematic snapshot `ldtteam/minecolonies-schematics@ae86ba500d3172220669657e0102861fa8b7f3be` previously audited in `PORT_AUDIT.md` contains 598 `.nbt` files and has no root `LICENSE` file in that pinned tree. That is distinct from the 9515 entries in this upstream source tree. Do not combine counts or assume their licensing is interchangeable.
- Do not bundle resources whose exact revision's redistribution terms are unconfirmed. Keep the external datapack path for user-provided assets unless rights are established.

## Verification

Commit `bb94b75c3650d66e5880fcec41457a5107d6f204` passed [GitHub Actions run #39509235016](https://github.com/thjjkdgjjk-design/colony-foundations-build/actions/runs/39509235016): JDK 22, Gradle build/tests, schematic add-on build, distributable checks, artifact upload and test release succeeded. This verifies that code commit, not this inventory file until its own commit CI passes. **No Minecraft client, dedicated server, multiplayer world or gameplay scenario has been launched.**
