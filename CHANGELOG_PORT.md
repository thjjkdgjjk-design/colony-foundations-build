# Port changelog

## 2026-10-21 — Persistent colony rank profiles

- Added saved `OWNER/OFFICER/FRIEND/NEUTRAL/HOSTILE` rank IDs and per-colony action profiles for the six action types currently enforced by this port.
- Added `/colony rank` and `/colony rank-permission`; the older `/colony permission` command now edits a shared profile.
- Added Town Hall NBT versioning and migration: pre-permission saves preserve access by migrating existing members to officer; the intermediate per-member grant format is retained as a migration override until its rank profile is explicitly edited.
- Added unit coverage for rank parsing/default profiles. This entry is not an in-game verification claim; the exact commit's CI is pending.
