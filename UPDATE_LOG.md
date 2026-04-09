# Endless Marriage - Update Log

## 2026-04-09 — 2.1.1 (continued)

### Marriage XP Even-Split (`XP Rework`)

- The marriage XP system has been reworked from a **one-way share** (spouse receives `xpShareMultiplier` of the earner's XP) to a **50/50 even split** (earner's adjusted XP is pooled and divided equally between both partners).
- When near spouse, the earner's post-bonus XP is halved: the earner keeps half, and the other half is granted to the spouse via `EndlessLevelingAPI.adjustRawXp` (no double-dipping on discipline/luck bonuses, no listener re-fire).
- **Party exclusivity:** marriage XP split and party XP sharing are now mutually exclusive. If the earner is in a party (`EndlessLevelingAPI.isInParty`), only the discipline/kiss bonuses apply — the even split is skipped entirely so party distribution is not doubled.
- Over time, both partners converge on the same total XP regardless of who killed what.
- Updated Javadoc in [`EndlessMarriage`](src/main/java/com/airijko/endlessleveling/endlessmarriage/EndlessMarriage.java) to reflect the new system.

---

## 2026-04-08 — 2.1.1 (compared to 2.1.0)

A focused bug-fix pass on top of 2.1.0. The headlines are a piggyback collision fix, a brand-new `PiggybackFollowSystem` so the rider's camera actually moves with the carrier, multi-world correctness for the proximity tick, and outright cancellation of spouse-on-spouse damage.

### Highlights

- Piggyback rider's `BoundingBox` is now shrunk to a zero-volume box during a session — fixes the carrier velocity reset / "Jump in location" warning spam.
- New **`PiggybackFollowSystem`** copies the carrier's position to the rider every tick, so the rider's camera no longer stays glued to the spot where they pressed the use key.
- `MarriageProximitySystem` rewritten to be **per-store**, fixing both a multi-world tick-rate bug and a `Store.assertThread()` violation on cross-world spouse lookups.
- `SpouseProtectionSystem` now **outright cancels** all spouse-on-spouse damage (melee + projectile), in addition to the existing piggyback damage reduction.
- New `MarriageConfigMigrator` schema bump (v1 → v2) — every user config gets merged forward on the next boot.
- `/marry admin menu` renamed to `/marry admin testmenu` to free up the `menu` slot.
- **Marriage XP even-split** — XP sharing reworked from a one-way share to a 50/50 split (party-exclusive).

### Piggyback Collision Fix (`Bug Fixes`)

- [`PiggybackService`](src/main/java/com/airijko/endlessleveling/endlessmarriage/services/PiggybackService.java) now snapshots the rider's `BoundingBox` into `riderBoundingBoxSnapshots` on mount and replaces it with a zero-volume box (`Vector3d.ZERO` min and max). The original box is restored on dismount, force-clear, and disconnect (for both rider-disconnects and carrier-disconnects).
- **Why:** the engine's `MountedComponent` is purely client-visual — the rider's server position stays exactly on the carrier. With the rider's full-size box still in place, the carrier's collision sweep was getting pushed >10 blocks per tick, which tripped `PlayerProcessMovementSystem`'s "Jump in location in processMovementBlockCollisions" guard, spammed warnings, and reset the carrier's velocity each tick.
- New `shrinkRiderBoundingBox` / `restoreRiderBoundingBox` helpers handle the snapshot/restore lifecycle.
- `forceClearByPlayer` now also restores the rider's box on disconnect for both directions (rider disconnecting and carrier disconnecting), with safe fallbacks when the player ref is no longer valid.

### `PiggybackFollowSystem` (`More Bug Fixes`)

- New per-tick [`PiggybackFollowSystem`](src/main/java/com/airijko/endlessleveling/endlessmarriage/systems/PiggybackFollowSystem.java) (~111 LOC) copies the carrier's `TransformComponent` position into the rider's transform every tick.
- **Why:** without this, the rider's server-side position never updates — the `MountedComponent` only renders the visual offset on the client. The rider's camera anchors to the server position, so it stayed glued to wherever they originally pressed the use key.
- Direct-assigns the position (bypassing `Player.moveTo` / `addLocationChange`) so no `collisionPositionOffset` accumulates on the rider — safe because the rider's box is now zero-volume.
- Per-store: only syncs sessions where both spouses are present in the currently-ticking store. Cross-world cases are left to the dismount / pre-teleport hooks.
- New `PiggybackService` accessors to support this: `getCarrierFor(rider)`, `getRiderFor(carrier)`, and `getRiderToCarrierView()` (a live read-only view of the `riders` map for tick walks).

### `MarriageProximitySystem` Rewrite (`Bug Fixes`)

- [`MarriageProximitySystem`](src/main/java/com/airijko/endlessleveling/endlessmarriage/systems/MarriageProximitySystem.java) is now fully per-store:
  - The single shared `timeSinceLastCheck` accumulator is replaced with a per-`Store` `ConcurrentHashMap` — previously, on a multi-world server, the system was called once per store per tick, so a single shared accumulator advanced N× faster than intended.
  - Spouse entity refs are looked up via `entityStore.getRefFromUUID()` instead of `Universe.getPlayer().getReference()`. This guarantees subsequent `store.getComponent(...)` reads happen on the correct store thread (the previous code was tripping `Store.assertThread()` and silently leaving couples flagged as "not near").
  - When only one of the two spouses is present in the ticking store, the present one gets cleared from `playersNearSpouse` and the absent one is left for whichever store actually owns them.
- New `onStoreShutdown(store)` hook drops the per-store throttle entry so the map doesn't hold dead `Store` references.

### `SpouseProtectionSystem` — Hard Cancel (`Bug Fixes`)

- [`SpouseProtectionSystem`](src/main/java/com/airijko/endlessleveling/endlessmarriage/systems/SpouseProtectionSystem.java) now takes a `MarriageDataManager` dependency in its constructor.
- Before applying the existing piggyback damage reduction, it inspects `damage.getSource()` for a `Damage.EntitySource` (which also covers projectiles via `ProjectileSource`). If the attacker is a player, is married, and the defender is their spouse, `damage.setAmount(0f)` cancels the hit outright.
- Wired up in [`EndlessMarriage.onEnable`](src/main/java/com/airijko/endlessleveling/endlessmarriage/EndlessMarriage.java) with the new `marriageDataManager` argument.

### Config Migration

- [`MarriageConfigMigrator.BUNDLED_CONFIG_VERSION`](src/main/java/com/airijko/endlessleveling/endlessmarriage/config/MarriageConfigMigrator.java) bumped `1` → `2`. Every existing user config file with a lower version will be merged forward against the bundled `config.json` on next boot.

### Command Rename (`renamed admin debug menu`)

- `/marry admin menu` → `/marry admin testmenu` ([`DebugMenuCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DebugMenuCommand.java)). The previous name collided with the real `/marry menu` slot reserved for the player-facing main page.
- [`DebugCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DebugCommand.java) help text updated to list `testmenu` instead of `menu`.

### Version

- `gradle.properties`: `2.1.0` → `2.1.1`.
- `config.json` `config_version` and `manifest.json` synced to match.

## 2026-04-08 — 2.1.0 (compared to 1.0.0)

This is the first major content update since the 1.0.0 launch. Endless Marriage went from a minimal "marry / divorce / ring" system to a full marriage feature suite with ceremonies, rings, kissing, piggyback, debug tooling, an admin UI, cooldowns, and a permission overhaul.

### Highlights

- New **services layer**: kiss, piggyback, witness collection, debug NPC, kiss buff, spouse protection.
- New **tiered ring system** (catalog, definitions, tiers, data manager, browser UI page).
- New **marriage announcer** with wedding sound effects (`wedding-march.ogg`, `kiss.ogg`).
- New **admin command + UI**: `/marry adminlist` opens a paged list of every active marriage, with reload/grant-divorce actions.
- New **config migrator** with full reload support (`/marry reload`).
- Marriage **cooldowns and permissions** added across the propose/accept/divorce/officiate flow.
- Command aliases added: `/marriage`, `/em`, `/m`.
- Kiss SFX moved to a 3D positional sound (audible to nearby players, not just the participants).

### New Services

- [`KissService`](src/main/java/com/airijko/endlessleveling/endlessmarriage/services/KissService.java) — handles the `/kiss` flow, particles, and SFX.
- [`KissBuffService`](src/main/java/com/airijko/endlessleveling/endlessmarriage/services/KissBuffService.java) — applies a temporary buff after kissing your spouse.
- [`PiggybackService`](src/main/java/com/airijko/endlessleveling/endlessmarriage/services/PiggybackService.java) — full piggyback ride implementation between spouses.
- [`WitnessCollector`](src/main/java/com/airijko/endlessleveling/endlessmarriage/services/WitnessCollector.java) — collects nearby players as ceremony witnesses.
- [`DebugNpcService`](src/main/java/com/airijko/endlessleveling/endlessmarriage/services/DebugNpcService.java) — spawns debug NPCs for testing the marriage flow.

### New Systems & Listeners

- [`SpouseProtectionSystem`](src/main/java/com/airijko/endlessleveling/endlessmarriage/systems/SpouseProtectionSystem.java) — prevents spouses from damaging each other.
- [`MarriageInteractListener`](src/main/java/com/airijko/endlessleveling/endlessmarriage/listeners/MarriageInteractListener.java) — handles in-world interactions (rings, ceremonies).
- [`MarriageAnnouncer`](src/main/java/com/airijko/endlessleveling/endlessmarriage/MarriageAnnouncer.java) — broadcasts ceremony events with sound effects.

### Tiered Ring System

- [`TieredRingDataManager`](src/main/java/com/airijko/endlessleveling/endlessmarriage/data/TieredRingDataManager.java) (~270 LOC) — persistence and lookup.
- [`TieredRingCatalog`](src/main/java/com/airijko/endlessleveling/endlessmarriage/data/tiered/TieredRingCatalog.java), [`TieredRingDefinition`](src/main/java/com/airijko/endlessleveling/endlessmarriage/data/tiered/TieredRingDefinition.java), [`TieredRingTier`](src/main/java/com/airijko/endlessleveling/endlessmarriage/data/tiered/TieredRingTier.java) — ring catalog and tier metadata.
- [`TieredRingBrowserPage`](src/main/java/com/airijko/endlessleveling/endlessmarriage/ui/TieredRingBrowserPage.java) — UI for browsing rings, with `TieredRingBrowserPage.ui` and `TieredRingRow.ui` markup.

### New Commands

- `/kiss` — [`KissCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/KissCommand.java)
- `/piggyback`, `/dismount` — [`PiggybackCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/PiggybackCommand.java), [`DismountCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DismountCommand.java)
- `/marry reload` — [`ReloadCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/ReloadCommand.java) (added during the "Sync Config" pass).
- `/marry adminlist` — [`AdminListCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/AdminListCommand.java) opens the admin UI.
- Debug suite: [`DebugKissCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DebugKissCommand.java), [`DebugMenuCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DebugMenuCommand.java), [`DebugNpcCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DebugNpcCommand.java), [`DebugPiggybackCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DebugPiggybackCommand.java), [`DebugRingsCommand`](src/main/java/com/airijko/endlessleveling/endlessmarriage/commands/subcommands/DebugRingsCommand.java).
- New aliases on the root: `/marry` accepts `/marriage`, `/em`, and `/m`.

### Cooldowns, Permissions & Fixes (latest commit)

- `Marriage Cooldowns, Permissions, and Fixes` ([`38b4604`](src/main/java/com/airijko/endlessleveling/endlessmarriage/EndlessMarriage.java)) added cooldown tracking and permission checks across:
  - `ProposeCommand`, `AcceptCommand`, `DivorceCommand`, `OfficiateCommand`, `GrantDivorceCommand`, `DebugCommand`, `MarriageUtil`, `MarriageDataManager`.
- New UI: [`MarriageAdminListPage`](src/main/java/com/airijko/endlessleveling/endlessmarriage/ui/MarriageAdminListPage.java) (~120 LOC) plus `MarriageAdminListPage.ui` and `MarriageAdminRow.ui`.

### Config & Reload

- New [`MarriageConfigMigrator`](src/main/java/com/airijko/endlessleveling/endlessmarriage/config/MarriageConfigMigrator.java) (~220 LOC) — migrates old configs forward across schema bumps.
- `MarriageConfig` expanded from a thin wrapper into a full ~160 LOC config class.
- `MarriageDataManager` and `MarriagePair` extended to track richer marriage state.
- `config.json` and `manifest.json` synced; full reload supported via `/marry reload`.

### Audio / Assets

- `Common/Sounds/Marriage/wedding-march.ogg` — plays during ceremonies.
- `Common/Sounds/Marriage/kiss.ogg` — plays on `/kiss`.
- `SFX_EM_Ceremony_WeddingMarch.json`, `SFX_EM_Kiss.json` — sound event definitions.
- New ceremony UI: 212-line rewrite of `MarriageMainPage.ui`, plus `WitnessRow.ui`.

### UI Layer

- `MarriageMainPage` rewritten (~300 LOC of changes) with the new ceremony / spouse / ring flow.
- `MarriageOfficiatePage`, `MarriagePriestPage` significantly extended.
- Bug fix: `MarriageRingPage` minor cleanup.

### Pending Tweaks (uncommitted at time of writing)

- `KissService` — kiss SFX switched from per-player 2D playback to a 3D positional sound at the midpoint between partners (~25 block radius).
- `MarriageCommand` — `/marry` now also responds to `/em` and `/m`.

## 1.0.0 — 2026-04-06 (baseline)

Initial public release. Core marry/accept/divorce/officiate flow, basic ring page, basic config, basic UI. See commit `1c69022` (`1.0.0 Update`).
