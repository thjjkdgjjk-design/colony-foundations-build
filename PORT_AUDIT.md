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

## Upstream schematic inventory at pinned CI revision

The exact source tree `ldtteam/minecolonies-schematics@ae86ba500d3172220669657e0102861fa8b7f3be` was scanned via its Git tree API (tree not truncated). It contains 598 `.nbt` files, no `.blueprint` files, in nine style folders:

| Style folder | NBT files |
|---|---:|
| `alternativestone` | 5 |
| `alternativewooden` | 10 |
| `birch` | 79 |
| `darkoak` | 75 |
| `defaultwooden` | 156 |
| `mesa` | 60 |
| `sandstone` | 91 |
| `stone` | 44 |
| `taiga` | 78 |
| **Total** | **598** |

An earlier CI workflow bundled 20 files from four style folders (`birch`, `darkoak`, `defaultwooden`, `stone`; citizen-house tiers 1–5). Because the pinned revision had no explicit redistribution grant, current public builds no longer fetch or bundle any of these upstream NBT assets. This upstream Git-tree count is not a count of unique buildings or guaranteed-compatible schematics; it only counts filenames with the `.nbt` suffix.

## Current project layers

### Colony Foundations
- Fabric bootstrap, Town Hall block entity, builder-contract item, vanilla-villager work loop and a basic blueprint-building queue.
- Does not provide the original colony model, profession system, worker-specific AI, requests, warehouse logistics, colony permissions, research, raids, diseases, happiness, or original MineColonies blocks/items.

### Colony Schematics (separate JAR)
- Separate Fabric mod ID and catalog item.
- Current public JARs do not bundle upstream MineColonies NBT assets due unresolved redistribution permission for the pinned snapshot. The loader can read compatible schematics from installed datapacks; the previously supplied private/user pack is not included in this repository or public JARs.
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

The MineColonies Java source uses GPL-3.0, while its own resources directory has a separate assets/minecolonies/LICENSE stating “All Rights Reserved”. The current default branch of the public schematic repository exposes a GPL-3.0 license file, but the exact revision pinned by CI (`ae86ba500d3172220669657e0102861fa8b7f3be`) has no root `LICENSE` file in its Git tree, and its README alone does not establish a broad reuse grant. Confirm that the license applies to the pinned NBT files before relying on it for redistribution. Do not treat source-code GPL as a blanket license for textures, sounds, GUI art, models or the separate MineColonies resource tree, which contains an `All Rights Reserved` notice.

## Current incremental delivery (2026-10-20)

The separate colony-schematics-addon is the active first workstream: making the Structurize schematic format usable on Fabric and enabling the full blueprint tree through a server datapack. The datapack output stays separate from the public JAR because the original MineColonies resource tree carries a separate “All Rights Reserved” notice; only the user who supplied the archive is receiving this generated pack here. Source-code GPL terms are not assumed to override that separate resource notice.

## Corrective tranche — 2026-10-20 (CI compile verified; gameplay testing pending)

- Both mods declare `java >=21`; Gradle continues using a JDK 22 toolchain and `options.release = 21`. This keeps the developer JDK requested by the project without preventing Java 21 clients from satisfying Minecraft 1.21.5's runtime requirement.
- Town Hall construction no longer silently advances past a solid obstruction. The active work order pauses and a reason/target coordinate is persisted and displayed; the player can clear the obstruction and resume.
- Before placement, the builder checks dimension build height, world border, and chunk-loaded state without forcing a distant chunk to load. An existing matching target block counts as recovered progress. Material is deducted only after `setBlockState` reports success.
- The separate schematic catalog's user-facing strings were moved to English/Russian language keys and the built-in style labels were localized.
- CI runs #136, #137, #138 and #140 succeeded. Run #139 caught a missing call-site update during transform extraction; run #140 compiled both JARs and ran the new schematic geometry unit tests successfully. Run #138 compiled the worker-name localization update.
- The extracted coordinate-transform utility has automated tests over X/Z dimensions 1–7 by 1–5, all 4 rotations and 3 mirror modes. Tests cover bounds, unique cell mapping, normalized parameters, invalid dimensions and anchors outside the schematic bounds.
- The Java 1.21.5 API fix, worker UUID/path recovery and localized worker assignment strings have compile-level CI verification. The in-game client/server behaviors remain untested here.

## Verification statement

CI compilation proves source compatibility with the compile environment only; it does not prove behavior in Minecraft. The complete MineColonies feature set is not yet ported. Build success and gameplay tests must be tracked separately.


## Follow-up UI/state correction — 2026-10-20

- Fixed Town Hall status formatting so the server sends actual line breaks instead of the literal characters \\ and `n`; this lets the existing client status renderer split the text correctly.
- Fixed the pause/resume guard so a build stopped by a placement/path issue at progress 0 can be resumed after the player addresses the issue. Previously the UI's pause button rejected that retry as if no project had been selected.
- Validation target: both Gradle projects compile and the schematic transform unit tests run in CI. Town Hall behavior still needs client/server in-game validation.


## Локализация каталога схем — 2026-10-20

- Названия 23 стилей из набора схем, категорий и распространённых типов построек и уровней отображаются по-русски.
- Добавлены автоматические тесты для пользовательских datapack-схем, альтернативных вариантов и встроенных схем.
- Проверка шрифтов, переносов и ширины длинных строк в самой игре ещё не проводилась.


## Надёжность сохранения и размещения сущностей — 2026-10-20

- При сканировании схемы теперь переводятся в локальные координаты позиции корневой сущности и вложенных пассажиров.
- Очередь проверяет мировые границы и загруженность чанков всей цепочки пассажиров до создания сущности. Ожидание чанка больше не создаёт временные сущности заново каждый тик.
- Расчёт непрерывных координат сущностей вынесен в общий трансформатор; добавлены тесты дробных смещений, поворотов и отражений.
- Требуется игровая проверка схем с ездоком/пассажиром на границах чанков; CI не заменяет её.


## Единые правила назначения жителей — 2026-10-20

- Добавлен WorkerRoleRules как единый источник правил найма: житель не может одновременно быть строителем, работником хижины или иметь другую метку задания.
- Проверки применяются и при выборе кандидата, и повторно на серверном методе назначения, что закрывает обход фильтра.
- Строительная очередь проверяет метки назначенного UUID и не продолжает работу с жителем, которого заняли другой профессией; причина сохраняется и отображается.
- В корневой Gradle-проект добавлен JUnit 5; тесты проверяют свободного жителя, конфликтующие назначения и восстановление собственной привязки строителя.
- Проверка миграции старых миров остаётся необходимой: старые метки не переписываются автоматически.


## Первый рабочий цикл профессии фермера — 2026-10-20

- На рабочие хижины добавлен серверный тикер с ограниченной частотой; он активируется только для назначенного жителя в загруженном мире.
- Фермер ищет зрелые культуры в радиусе 8 блоков, подходит к ним обычной навигацией, собирает поддерживаемые культуры и хранит урожай в инвентаре хижины. Клетка сразу пересевается; при заполненном складе урожай не снимается.
- Добавлены тесты таблицы урожая для пшеницы, моркови, картофеля и свёклы. Остальные профессии всё ещё не имеют оригинального рабочего цикла; участок фермы и расширенные рецепты пока не реализованы.
- В игре необходимо проверить путь жителя, заполненный инвентарь, выгрузку чанков и повторное сохранение мира.


## Regression исправление тестов фермера — 2026-10-20

- CI №150 подтвердил компиляцию рабочего цикла, но 2 теста упали при статической инициализации реестра Minecraft (`Items`) в окружении обычных JUnit-тестов.
- Таблица урожая переведена на чистые данные (ID предмета и количество); серверный контроллер преобразует их в ItemStack только в игровом контексте и безопасно пропускает неизвестные ID.
- Добавлены проверки отклонения некорректных описаний выпадений. CI необходимо повторить; игровой цикл пока не подтверждён внутриигровым испытанием.

## Восстановление навигации рабочих — 2026-10-20

- Цикл фермера повторно запрашивает маршрут, если житель всё ещё далеко от цели, а навигация продолжает сообщать об активном пути. Повторная попытка ограничена одним запросом на 100 игровых тиков; слоты сдвигаются по ID сущности, чтобы жители не пересчитывали пути одновременно.
- Для расписания повторных запросов добавлены чистые unit-тесты: бездействующая навигация, отсутствие пересчёта каждый тик и распределение повторов между жителями.
- Тесты подтверждают только политику расписания. Реальное восстановление застрявшего жителя, производительность массовой навигации и поведение на сервере требуют игрового испытания.

## Полный повторный аудит ядра строительства и схем — 2026-10-20

Источники сверки: [официальный каталог схем](https://minecolonies.com/schematics/), [руководство по схемам](https://minecolonies.com/wiki/tutorials/schematics/), [наборы стилей](https://minecolonies.com/wiki/tutorials/schematics/style-packs/), [Structurize](https://minecolonies.com/wiki/dependencies/structurize/), [строительство](https://minecolonies.com/wiki/buildings/builder/) и [система запросов](https://minecolonies.com/wiki/systems/request/). Проверен исходный код ветки `main` на базе `2fbb731a25386f7ed41e7918a711e91f4b772e05`; этот аудит не является игровым тестом.

### Карта функционального соответствия

| Подсистема | Фактическая реализация | Статус и открытые проблемы |
|---|---|---|
| Формат Colony Foundations JSON | `BlueprintRegistry.readJson`: формат 1, блоки и координаты ограничены; только состояния блоков | Частично реализовано. Нет блока-сущностей, сущностей, теговых подстановок и полной схемной модели Structurize |
| Structurize NBT в основном моде | `BlueprintRegistry.readStructurizeNbt`: сжатый NBT, `size_x/y/z`, палитра и packed-массив, primary anchor; лимиты 128 на размерность, 131072 ячейки и 64 MiB NBT | Частично. Не переносит block entities/entities и required-mods; неподдерживаемые блоки заменяются воздухом. Некорректные индексы палитры теперь отвергаются целиком вместо тихого пропуска ячеек |
| NBT в Colony Schematics | `SchematicLoader`: сжатый NBT для встроенных схем и файлов `.blueprint`/`.nbt` из активных серверных datapack; палитра, блок-сущности, сущности, required-mods и primary anchor; лимиты по размеру данных, объёму, палитре и количеству записей | Частично. Полный DataFixer, все обработчики специальных блоков и семантика версий Structurize не реализованы. Ошибочные записи block entities/entities всё ещё требуют более строгой проверки; неизвестные блоки заменяются воздухом |
| Безопасность импорта | Используется `NbtSizeTracker`, пределы размеров, проверка областей сканирования и идентификаторов ресурсов | Частично. Требуется отдельная проверка опасных block-entity данных, идентификаторов сущностей и всех границ до записи в мир. Встроенные схемы не следует считать доверенными без проверки |
| Поворот и зеркалирование | `SchematicTransform` плюс `BlockState.mirror/rotate` в `SchematicPlacementQueue`; unit-тесты проверяют геометрию координат и дробные координаты сущностей | Геометрия проверена unit-тестами. Совпадение направленных свойств блоков, сущностей и фактической постройки не проверено в игре. Основной мод поддерживает поворот, но не предоставляет зеркалирование |
| Предпросмотр | `BlueprintPreviewRenderer` рисует проволочные кубы максимум для 1800 блоков и маркирует занятые позиции; экран каталога рисует 2D-проекцию фасада | Не является точным 3D предпросмотром моделей блоков. Для Colony Schematics нет эквивалента полноценного ghost preview; соответствие предпросмотра фактическому размещению не подтверждено |
| Очередь Colony Foundations | `TownHallBlockEntity`: один сохраняемый заказ, поворот/смещение, прогресс, запас материалов, назначенный ванильный житель; строительство выполняется по одному блоку за рабочий тик | Рабочий прототип. Нет очереди приоритетных заказов, улучшения/ремонта, резервирования материалов, замен материалов и логистики. Расчёт ресурсов основан на `Block.asItem()`, а не на полной модели рецептов/вариантов блоков |
| Очередь Colony Schematics | `SchematicPlacementQueue`: серверная очередь до 8 работ, бюджет операций на тик; творческий режим, размещение блоков, затем block entities и сущностей | Отдельный инструмент творческого размещения, а не заказ рабочего MineColonies. Может перезаписывать участок; позиции вне мира, неудачные записи и неподдерживаемые сущности учитываются как пропущенные. Нет отката частично поставленной схемы и продолжения после перезапуска |
| Сканирование/свиток | `SchematicScanner`: два угла, проверка чанков и размеров, запись палитры, состояний, block entities и сущностей в предмет | Частично. Нужны round-trip интеграционные тесты, строгая проверка содержимого NBT и игровая проверка пассажиров/данных блок-сущностей |
| Сохранение строительства | NBT ратуши хранит ID схемы, поворот, прогресс, материалы, владельца/участников и UUID строителя | Частично и ещё не проверено перезапуском игры. Нужны тесты миграции старых миров, смены/удаления схемы после сохранения и восстановления после сбоя между записью мира и увеличением прогресса |
| Территория и разрешения | Добавлен начальный квадратный claim ратуши радиусом 4 чанка в каждом горизонтальном направлении; серверные обработчики проверяют ломание и взаимодействие/размещение чужими игроками | Первый защитный слой, только владелец и приглашённые участники имеют доступ; расширение от рабочих хижин, гранулярные разрешения, защита взрывов и синхронизация claims независимо от загрузки чанков ещё не реализованы |
| Жители и профессии | `WorkerRoleRules`, назначение жителя на хижину, ограниченный цикл фермера для четырёх зрелых культур и проверка повторного запроса пути | Частично. Нет оригинальной модели граждан, жилья/потребностей, восстановления смерти и рабочего цикла остальных профессий |
| Фермер | `CitizenJobController` собирает зрелые пшеницу, морковь, картофель и свёклу в радиусе 8 блоков, подходит к ближайшей культуре, пересаживает на базовое состояние | Только минимальный сбор. Нет управляемых полей, вспашки, выбора культур, полноценной посадки, удобрений, зрелости с учётом запасов семян и складской логистики |
| Склад/запросы/экономика | Инвентарь хижины и локальный запас материалов в ратуше | Система запросов, курьерская сеть, склад, приоритеты, рецептурные цепочки и экономика не реализованы |
| UI и локализация | Два отдельных каталога, русские language-файлы и ряд русских статусных сообщений | Переводы основных экранов есть, но полная локализация не подтверждена. Остаются жёстко заданные строки, технические ID, неполные названия построек и английские записи fallback |
| Каталог схем | Встроена JSON-схема коттеджа; add-on сканирует включённые datapack-схемы. Upstream snapshot содержит 598 NBT-файлов в 9 стилевых папках, но эти файлы больше не пакуются в публичный JAR до подтверждения лицензии | Полный каталог не реализован. Отдельный пользовательский datapack должен иметь подтверждённые права на использование; в мире также могут отсутствовать зависимые блоки |

### Исправление текущего этапа

- В обоих парсерах заменено тихое игнорирование индексов палитры вне диапазона на отказ с конкретной диагностикой; этим предотвращается принятие повреждённой схемы как неполной.
- Добавлены чистые unit-тесты для верхнего и нижнего 16-битных индексов, невалидных ссылок и неверных аргументов. Эти тесты проверяют только декодер индекса, а не загрузку NBT целиком.
- Следующие критические проверки перед дальнейшим расширением: строгая проверка неизвестных свойств блоков, отображение предупреждения об отсутствующих модах/блоках игроку, фильтрация потенциально опасных NBT блок-сущностей, и корректное поведение очереди при неудачном `setBlockState`.
- Автоматические тесты и CI не заменяют запуск клиента и выделенного сервера на Minecraft 1.21.5; проверка ротаций/отражений в мире, постройки, сохранения/загрузки и ИИ остаётся открытой.


## Предупреждение о несовместимых блоках и свойствах — 2026-10-20

- Colony Schematics теперь собирает уникальные предупреждения при разборе палитры: отсутствующий блок, неизвестное свойство и недопустимое значение свойства.
- Перед размещением игрок получает сообщение на русском языке с первыми четырьмя предупреждениями и числом дополнительных предупреждений. Отсутствующий блок по-прежнему заменяется воздухом, а неизвестное свойство сохраняет значение блока по умолчанию; пользователь теперь предупреждён до начала записи в мир.
- Запись палитры без поля `Name` считается некорректной и отклоняется. Это проверяется сборкой, однако фактические совместимость состояний блоков, размеры сообщения и отображение в игре ещё не проверены внутриигровым тестом.


## Защита при импорте исполняющих блоков и сущностей — 2026-10-20

- Добавлена явная блокировка командных блоков, повторяющихся/цепных командных блоков, структурных блоков, jigsaw и тестовых служебных блоков в обоих загрузчиках схем. Такие блоки заменяются воздухом, а Colony Schematics показывает предупреждение на русском языке.
- Colony Schematics больше не переносит block-entity NBT для перечисленных служебных блоков; malformed block-entity entries, отсутствующий `id` и позиции block entity за пределами размеров схемы приводят к отказу разбора, а не к тихому пропуску.
- Дерево сущностей теперь проверяется рекурсивно: command-block minecart/player-записи запрещены даже внутри `Passengers`. Добавлены unit-тесты для списка запрещённых блоков, обычных блоков и дерева ездока/пассажиров.
- Это базовая защита импорта, а не полная песочница NBT. Остальные типы block entities/сущностей, содержащие нестандартное интерактивное или модифицированное поведение, ещё требуют threat-model проверки и игровой валидации.


## Ограничение глубины данных сущностей и координат блок-сущностей — 2026-10-20

- Строго ограничено дерево пассажиров: глубина не более 32, общее число сущностей вместе с пассажирами не более 2048. Лимит применяется до трансформации координат и создания сущностей, чтобы malformed NBT не мог вызвать чрезмерную рекурсию и нагрузку.
- Координаты `x/y/z` каждой block entity теперь обязательны и должны иметь числовой тип; отсутствие координаты отвергает схему вместо перенаправления данных на `(0,0,0)).
- Добавлены тесты на чрезмерную глубину и количество пассажиров. Проверка относится к валидации данных; реальные сущности и блок-сущности по-прежнему требуют игровой round-trip проверки.


## Предупреждения совместимости в основном моде — 2026-10-20

- `BlueprintDefinition` теперь хранит предупреждения загрузки схемы; прежний семипараметрический конструктор сохранён для совместимости с существующими компонентами.
- Root-парсер JSON и Structurize NBT собирает предупреждения по отсутствующим блокам, запрещённым служебным блокам, неизвестным свойствам и недопустимым значениям. Отсутствующий `Name` в записи палитры теперь делает схему ошибочной вместо молчаливого превращения в воздух.
- При выборе/запуске заказа игрок получает русское предупреждение с первыми тремя несовместимостями и числом оставшихся; уникальных предупреждений собирается не более 64.
- Добавлены unit-тесты форматирования краткого сообщения. Они не заменяют запуск Minecraft и не проверяют полную загрузку настоящего NBT через реестры игры.


### CI correction — compatibility-warning import

- CI run `39487076548` caught a missing import for `CompatibilityWarningSummary` in `TownHallBlockEntity`; no gameplay or test assertion failed because compilation stopped first.
- Added the missing import. The next run must confirm compilation and both test suites before the warning feature can be treated as CI-verified.


## Первый серверный слой защиты территории — 2026-10-20

- В официальной документации начальная территория колонии — квадрат чанков с радиусом четыре чанка вокруг ратуши; реализация использует эту геометрию, а не радиус в блоках. Источники: [граница колонии](https://minecolonies.com/wiki/systems/border/), [защита колонии](https://minecolonies.com/wiki/systems/protection/).
- `ColonyProtection` индексирует ратуши при загрузке чанков и во время серверного тика, а также удаляет записи при выгрузке чанка или разрушении ратуши. Проверки идут только на сервере; клиентские сетевые запросы не являются источником разрешения.
- Чужой игрок не может ломать блоки или взаимодействовать с блоками в claim. Проверка взаимодействия также проверяет смежный блок назначения, чтобы простая установка блока из-за границы не обходила ограничение. Действия владельца и явно приглашённых участников разрешены.
- Добавлены чистые unit-тесты для границы на отрицательных координатах, края claim и правил владельца/участника.
- Ограничения этой итерации: claim пока создаётся только ратушей и не расширяется от хижин; нет отдельных уровней разрешений, защиты взрывов, автоматизаций редстоун/модовых машин и постоянного индекса claims для разгруженных чанков. Настоящий dedicated-server тест ещё обязателен.


### Защита от спама сообщениями о запрете

- Ограничитель уведомлений теперь учитывает измерение и время мира, поэтому переход в другое измерение или откат времени мира не блокирует сообщение об отказе.


### CI correction — граница чанка защиты

- Запуск CI `39487605337` выявил неверное ожидание в unit-тесте: для ратуши в чанке 0 координата X=80 уже относится к чанку 5, а радиус 4 включает чанки от -4 до +4. Проверка исправлена на X=79; X=80 остаётся за пределами claim.


## Исправление побочного основания колонии сетевым пакетом — 2026-10-20

- Найдена проблема контроля доступа: `canManage`, `isOwner` и `sendStatus` могли автоматически вызывать `ensureFounded`, поэтому запрос статуса/заказа от клиента мог назначить владельца пустой ратуше.
- Теперь основание выполняется только при серверном обработчике реального ПКМ по Town Hall. Проверки владельца и разрешений стали чистыми запросами и не меняют состояние колонии; запрос статуса больше не создаёт владельца.
- Это закрывает очевидный побочный путь через C2S payload, но полный multiplayer тест и проверка повторной отправки/порядка пакетов в настоящем сервере всё ещё необходимы.


## Восстановление строительного заказа при исчезновении схемы — 2026-10-20

- Устранена опасная подмена: ранее `TownHallBlockEntity.currentDefinition()` возвращал стартовый коттедж, если ID сохранённого чертежа больше не загружался. Активный заказ мог начать строить совершенно другое здание после отключения datapack/удаления файла.
- Добавлен отдельный `BlueprintRegistry.getBuildDefinition`: он разрешает встроенный резерв только для `colony_foundations:cottage`; любой другой отсутствующий ID остаётся недоступным.
- Активный заказ с отсутствующей схемой теперь приостанавливается с причиной на русском и сохраняет исходный ID/прогресс/запасы. Заказ также останавливается, если его сохранённый прогресс больше числа блоков текущей версии схемы. Повреждённый прогресс ограничивается глобальным лимитом, но не превращается автоматически в завершённый проект.
- Добавлен unit-тест, что неизвестный ID схемы не подменяется коттеджем. CI подтвердит чистую lookup-логику; восстановление после реального перезапуска и смены datapack требует отдельного игрового сценария.


### CI correction — missing Optional import

- Run `39488387547` stopped during compilation because the new safe status rendering uses `Optional<BlueprintDefinition>` without importing `java.util.Optional`.
- Added the import; no tests ran in that failed build because compilation stopped first.


## Безопасное восстановление block-entity данных — 2026-10-20

- Перед применением NBT `SchematicPlacementQueue` сверяет сохранённый `id` с зарегистрированным типом block entity в целевой позиции. При несовпадении данные не применяются, запись считается пропущенной и выводится ограниченное по частоте русское предупреждение.
- Восстановление каждой отдельной block entity обёрнуто в обработку `RuntimeException`; повреждённые данные записываются в журнал и не прерывают весь серверный цикл размещения.
- Добавлены unit-тесты сравнения идентификаторов типов. Они не проверяют все реальные сериализаторы block entities в Minecraft; игровые контейнеры и modded BEs требуют отдельного теста.


## Владение ратушей при размещении — 2026-10-20

- Исправлен риск захвата новой ратуши: владелец теперь назначается серверным обработчиком `TownHallBlock.onPlaced` из фактического игрока-разместившего блок, до первого отдельного взаимодействия.
- `ensureFounded` остаётся резервным путём только для не принадлежащих никому ратуш, появившихся через автоматизацию/старые сохранения; проверка сетевых пакетов по-прежнему не основывает колонию.
- API-сигнатура `onPlaced(World, BlockPos, BlockState, LivingEntity, ItemStack)` сверена с Yarn 1.21.5 build 1. Поведение требует подтверждения серверным игровым тестом: CI компиляция не доказывает, что владелец корректно сохраняется после реального размещения/перезахода.


### CI correction — null-safe block-entity type comparison

- Run `39488777713` reported a NullPointerException in the negative unit test because `Identifier.tryParse(null)` throws. The type guard now rejects null strings before parsing. The placement queue already calls the guard for missing/invalid type IDs, so this also makes that malformed-data path non-throwing.


## Проверка территории для строительного ИИ — 2026-10-20

- Строитель ратуши размещает блоки через `ServerWorld.setBlockState`, а не от имени игрока; поэтому события защиты игроков сами по себе не защищают чужой claim от строительной очереди.
- Перед каждым блоком активного заказа проверяется, что координата внутри четырёхчанкового начального claim собственной ратуши и не находится внутри другого основанного claim. При нарушении очередь сохраняет прогресс и останавливается с русской причиной, не пытаясь менять чужую территорию.
- Проверка использует только индекс ратуш в текущем измерении и не загружает чужие чанки принудительно. Неизвестные/выгруженные чужие claims и границы от хижин остаются ограничениями текущей начальной модели.
- Компиляция и unit-тесты ещё должны подтвердить эту итерацию; серверный сценарий с двумя соседними колониями не проверен в игре.


## Восстановление заказа при нулевом прогрессе — 2026-10-20

- Исправлено условие `resumeSame`: теперь повторный запуск того же активного или приостановленного заказа сохраняет поворот и координаты площадки, даже если препятствие было обнаружено до размещения первого блока.
- Новая чистая политика `BuildResumeRules` покрыта пятью unit-сценариями: остановка на нулевом прогрессе, активный заказ, заказ с уже накопленным прогрессом, новая не начатая схема и действительно завершённый/сменённый заказ.
- Этот этап проверяет выбор пути состояния. Внутриигровой тест «препятствие → убрать → возобновить» по-прежнему требуется.


## Исключение непроверенных схем из публичных релизов — 2026-10-20

- Повторная проверка upstream показала: закреплённый snapshot `ae86ba500d3172220669657e0102861fa8b7f3be` содержит 598 файлов `.nbt`, но не содержит `LICENSE`; текущий `master` имеет GPL-3.0, однако его Git tree не содержит этих NBT-файлов. Этого недостаточно, чтобы надёжно подтвердить право на распространение именно упакованного snapshot.
- Удалены CI-шаги скачивания 20 домов и копирования их во второй JAR. Будущие публичные сборки не будут включать эти NBT-ресурсы до подтверждения разрешения. Код загрузки совместимых datapack и встроенный коттедж остаются.
- Из-за отсутствия инструмента удаления релизов в текущем GitHub-коннекторе исторические CI-релизы, опубликованные до этого коммита, могут оставаться доступными. Их удаление/замена требует отдельного доступа к удалению GitHub Releases; эта работа здесь не подтверждается как выполненная.


## Автоматическая проверка паритета локализации — 2026-10-20

- Добавлены unit-тесты, которые сравнивают наборы ключей `ru_ru.json` и `en_us.json` в обоих JAR и отклоняют пустые переводы каталога схем.
- Для основного мода проверяется, что зарегистрированные строительные/утилитарные названия не используют fallback, полученный простой заменой `_` на пробелы. Сами тесты ещё должны пройти CI; динамические сообщения и строки, жёстко заданные в Java, требуют отдельного аудита.


## Постоянное хранилище территориальных прав — 2026-10-20

- После повторного аудита найдено ограничение предыдущей защиты: индекс существовал только пока ратуша была загружена, поэтому чужой claim мог исчезнуть из проверки при выгрузке чанка.
- Добавлен `ColonyClaimState` в `PersistentStateManager` overworld: положение, измерение, UUID владельца и список участников сохраняются в данных мира; пространственный индекс перестраивается из сохранённых записей.
- Проверка игрока и строительного ИИ теперь опирается на постоянные записи, а не на наличие загруженной блок-сущности ратуши. Снятие владельца/участника синхронизирует запись, фактическое удаление блока ратуши удаляет claim, а выгрузка чанка только сбрасывает временный индекс.
- Добавлены unit-тесты для поиска claim вокруг выгруженного центра, прав владельца/участника, измерений и отсева некорректных записей. Сборка должна подтвердить API `PersistentStateType` для Yarn 1.21.5; игровой перезапуск мира и проверка двух соседних колоний остаются обязательными.
- Ограничение миграции: колонии, созданные до появления постоянного реестра, записываются в него при первой загрузке/регистрации своей ратуши. Сканирование всех старых region-файлов не выполняется автоматически.


## Серверный каталог реально доступных строительных схем — 2026-10-20

- Исправлено меню, которое продолжало показывать 20 домов из снятого asset bundle при отсутствии самих схем. Теперь список страниц формируется сервером из `BlueprintRegistry` после загрузки datapack; встроенный коттедж добавляется безопасным резервным вариантом.
- Добавлены C2S-запрос страницы `RequestBlueprintCatalogPayload` и S2C-ответ `BlueprintCatalogPayload`: пять записей на страницу, ID, название, размеры и число блоков. Сервер проверяет близость к существующей ратуше, а клиент принимает ответ только для открытого экрана, его позиции и текущей страницы.
- Точный геометрический предпросмотр оставлен только для встроенного коттеджа. Клиентский ресурс или модовый asset не считается авторитетным доказательством, что геометрия совпадает с серверной схемой. Для datapack-схем показываются габариты и количество блоков с честным сообщением об отсутствии точной клиентской геометрии. Синхронизация точной геометрии/состояний блоков и полноценный 3D preview остаются открытыми задачами.
- CI ещё должен подтвердить новый сетевой протокол. Настоящий datapack/multiplayer сценарий и соответствие размещению в игре не проверены.


### CI correction — удаление старого генератора каталога

- Запуск CI `39491071643` выявил два compile errors: `BlueprintCatalogScreen.Entry` получил новую серверную структуру (ID, название, габариты, количество блоков), но старые неиспользуемые `makeEntries/addStyle` продолжали создавать его старым двухаргументным конструктором.
- Удалены неиспользуемые хелперы со статическим списком upstream-схем. Это также предотвращает случайное возвращение удалённых из публичного JAR схем. Нужно повторно подтвердить CI обоих JAR.


## Цикл работы фермера по настраиваемым полям — 2026-10-20

- После регистрации отдельного поля подключён рабочий цикл фермера к данным поля, а не к любым зрелым культурам рядом с хижиной. Поиск ограничен загруженными чанками и радиусом 16 блоков вокруг хижины; число ближайших полей ограничивается уровнем 1–5.
- Земля поля находится под блоком-пугалом: культура в соседней клетке занимает тот же уровень Y, что и якорь, а почва находится на Y−1. Алгоритм пропускает центральную клетку, не пытается вырастить растение поверх блока поля и проверяет загруженность обоих блоков почвы/культуры.
- Добавлены stock helpers для проверки мотыги, топора, семян/продуктов и расхода инвентаря хижины. Строитель сельхозработ не использует случайные ресурсы из инвентаря игрока.
- Ежедневный маркер сохраняется в NBT поля. Поддерживаются отдельные действия: вспашка поддерживаемых типов почвы, посадка из запасов хижины, сбор зрелой выбранной культуры, ускорение роста костной мукой при включённом переключателе. Если выдача не помещается в хижине, урожай не срезается.
- Требуется CI для актуального коммита и игровое испытание на новом мире: настроить поле, положить мотыгу/топор/семена в Farmer's Hut, увидеть ежедневную последовательность почва → посадка → рост → сбор и проверить повторный перезапуск мира. Модовые культуры и производство ресурсов по цепочкам пока не заявляются.


### CI correction — единственная реализация методов запасов хижины

- CI run `39492886793` остановился на компиляции: методы `hasItem`, `hasItemPathSuffix` и `consumeItem` в `ColonyHutBlockEntity` были определены дважды.
- Дубликаты удалены; для каждого метода оставлена одна полная реализация. Это исправление чисто структурное; после него обязателен новый CI-прогон обоих JAR и тестов.


## Постоянная очередь запросов предметов — 2026-10-20

- Добавлена `ColonyRequestState` поверх `PersistentStateManager` мира: каждый запрос хранит ID, владельца колонии, измерение и позицию получателя, ID предмета, требуемое/доставленное количество, приоритет, текущий статус, назначенного курьера и временные метки.
- Жизненный цикл ограничен проверяемыми переходами `OPEN → RESERVED → IN_TRANSIT → DELIVERED`; отмена переводит активный запрос в `CANCELLED`, освобождение курьера возвращает запрос в очередь. Частичная доставка обновляет delivered count и открывает остаток повторно; дублированный вызов доставки не срабатывает, когда запрос уже не `IN_TRANSIT`.
- Запросы одной колонии/цели/предмета объединяются с верхним пределом 4096 единиц; очередь ограничена 4096 активными запросами, а приоритет — диапазоном 0–10. Применение состояния ограничено серверным persistent state и не даёт клиенту напрямую переключать статус.
- Добавлены чистые unit-тесты переходов: выдача только курьеру-резиденту запроса, частичная/полная доставка, отказ постороннему курьеру, отмена, освобождение и лимиты.
- Это первая часть логистики: физическое перемещение ItemStack между Warehouse, courier и целевой хижиной, GUI создания ручного запроса, автоматическое создание запросов при нехватке семян/материалов и привязка хутора к владельцу колонии ещё должны быть реализованы отдельными этапами. Настоящий restart-world тест отсутствует.


## Состояние маршрута курьера и разрешённый владелец колонии — 2026-10-20

- Добавлен `ColonyClaimState.ownerAt`: позиция привязывается только к одному однозначному сохранённому owner UUID; неоднозначные перекрытия claims отказывают в доставке, а не объединяют инвентари колоний.
- Очередь предоставляет `hasActiveRequest` для подавления повторной постановки одного запроса фермером в каждом рабочем тике.
- Блок-сущность рабочей хижины теперь сохраняет ID задачи курьера, этап «к складу/к получателю», две позиции маршрута и количество/ID груза в инвентаре. Метаданные маршрута читаются/пишутся вместе с NBT хижины; фактическая AI-логика будет добавлена отдельно после проверки этого слоя.


## Автоматическое снабжение фермера через курьера — 2026-10-20

- Цикл фермера создаёт объединяемый запрос на посадочный материал, если поле настроено, нужных семян/корнеплодов нет в инвентаре хижины и активного запроса для той же цели/предмета ещё нет.
- Профессия `hut_deliveryman` подключена к `CitizenJobController`: курьер выбирает открытый запрос по приоритету, ищет загруженный склад в радиусе 16 блоков, резервирует запрос за UUID работника и идёт к складу.
- После физического изъятия одного стека курьер сохраняет этап и количество груза, идёт к хижине-получателю, проверяет что получатель принадлежит той же колонии, что в нём есть место, затем увеличивает доставленное количество запроса и удаляет груз из своего инвентаря. Недоступные чанки не подгружаются насильно.
- Поддержка ограничена одним стеком на поездку и ближайшим складом; возвращение готовых продуктов на склад, маршруты из нескольких остановок, GUI заказов, ручные запросы и вместимость курьера по уровням ещё не реализованы. Проверка настоящего игрового сценария/перезапуска остаётся открытой.
