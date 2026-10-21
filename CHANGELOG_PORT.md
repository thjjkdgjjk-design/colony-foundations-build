# Port changelog

## 2026-10-21 — Persistent colony rank profiles

- Added saved `OWNER/OFFICER/FRIEND/NEUTRAL/HOSTILE` rank IDs and per-colony action profiles for the six action types currently enforced by this port.
- Added `/colony rank` and `/colony rank-permission`; the older `/colony permission` command now edits a shared profile.
- Added Town Hall NBT versioning and migration: pre-permission saves preserve access by migrating existing members to officer; the intermediate per-member grant format is retained as a migration override until its rank profile is explicitly edited.
- Added unit coverage for rank parsing/default profiles. This entry is not an in-game verification claim; the exact commit's CI is pending.

## 2026-10-21 — Atomic work-order conflict admission

- Changed queued-order admission to validate all same-site conflicts before cancelling replaceable queued/blocked orders. If an active/progressed conflict rejects a request, prior queued orders remain unchanged.
- Added a regression test with a seeded conflicting work-order history to verify the rejected request is side-effect-free.
- Status: implementation saved in a feature-branch commit; CI result pending. No Minecraft gameplay test was run.

## 2026-10-21 — Explicit work-order lifecycle transitions

- Added allowed status edges to the persistent work-order store and rejected invalid transitions before mutation.
- Added regression coverage for queue skipping directly to completion, moving active orders backward to queued, and completing blocked/paused work without resuming it.
- Implementation is on the feature branch; CI for the exact commit is pending. No Minecraft gameplay test was run.

- CI follow-up: adjusted an existing completion test to use the now-enforced lifecycle sequence `queued -> active -> completed`; direct queued-to-terminal completion remains rejected. The fix awaits a fresh CI run.

## 2026-10-21 — Work-order NBT persistence tests

- Added an encode/parse round trip for a queued work order and a legacy-save compatibility scenario where `citizens`, `buildings`, `work_orders` and new colony identity fields are absent.
- Exposed the existing internal `PersistentState` codec package-privately for tests; no serialization format change was introduced.
- Status: CI pending. This test does not substitute for an actual Minecraft world restart test.

## 2026-10-21 — Persist completed blueprint structures

- Extended the optional building-record codec with blueprint ID, rotation and world-space footprint bounds; older hut records continue to decode as point-sized records.
- On BUILD completion, register the structure in the persistent colony directory and retry registration after reload if a completed Town Hall record survived without the structure record.
- Queue and GUI start paths now reject placements outside the colony footprint, overlaps with existing structures/hut anchors, and overlaps with other non-terminal work orders.
- Added footprint geometry, site conflict, blueprint metadata and codec round-trip coverage. UPGRADE/REPAIR/REMOVE execution still remains unfinished; no in-game test was run.

## 2026-10-21 — Conservative REPAIR work orders

- Added `/colony repair <hall> <building> <priority>` to queue a REPAIR only against a completed registered blueprint whose style, rotation, level and footprint match the available datapack.
- Persisted active work-order type/current level/target level in Town Hall NBT and updated scheduler/sync to preserve REPAIR rather than rewriting it as BUILD.
- The shared builder executor repairs missing/replaceable cells and skips correct cells; a solid mismatch blocks rather than overwrites. Builder capability uses colony level as a temporary proxy until original Builder Hut/job-skill semantics exist.
- Added state regression coverage for target-footprint exclusion and repair level semantics. No gameplay test has been run.


## 2026-10-21 — Conservative lumberjack work cycle

- Integrated hut_lumberjack into the server-side citizen job controller.
- The worker requires an axe in hut storage, searches only loaded chunks inside the colony claim for vertical trunks on supported soil with a nearby canopy, and moves to the trunk's highest log.
- The action replaces one log with air, commits one matching item to hut inventory, and restores the original block if insertion unexpectedly fails. The current tree base is persisted in hut NBT to resume after reload.
- Added unit tests for search-radius bounds, eligible soil/trunk sizes, canopy thresholds and harvest prerequisites.
- No in-game test has been performed. Connected tree traversal, leaf/sapling collection, replanting, modded trees and axe durability remain unimplemented.


## 2026-10-21 — Persisted sapling replanting and axe wear

- Added a durable replant-pending state to hut NBT; after the last trunk block is harvested, the worker queues the corresponding supported vanilla sapling/propagule and retries planting after it is already stored or delivered through the existing request path.
- The replant task survives hut/world reload and is cleared after planting, a conflicting block/soil, or a stale target makes the operation invalid.
- Each harvested log consumes one durability point from the first axe in hut storage; a broken tool is removed and further work waits for a replacement.
- Added unit coverage for supported vanilla log-to-sapling mappings and rejection of unsupported/modded IDs. CI passed; no in-game verification was performed.
