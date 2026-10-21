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
