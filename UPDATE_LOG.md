# Endless Marriage - Update Log

## 2026-06-24 — 3.1.0 (the V3 line, compared to 2.13.0)

The major version bump to **V3** collapses three commits (`3ed24c1` → `8923766` → `d26be3e`) into one release. It is the first build to require **EndlessLeveling Core ≥ 9.35.0** (was `≥ 9.34.1`) — the new couple-dungeon routing, banked-instance even-split, cross-world rider pull, and suite backup hooks all consume core seams added in 9.35.0. **Requires an EL Core ≥ 9.35.0 redeploy alongside this jar.**

### Highlights

- **Married couples share dungeon instances** like a party (without forming one) — new `marriage_shared_dungeons` toggle, bridged to EL core's couple share-key routing.
- **Even-split XP now works *inside* dungeons/rifts/waves**, not just the overworld — fixes the long-standing instance bypass via a new `CoupleBankSplitResolver` (see memory note "Marriage even-split bypassed in instances").
- **Piggyback survives cross-world teleports**: a carrier going through a portal/dungeon pulls the seated rider with them instead of dismounting.
- **New `PiggybackDeathDetachSystem`** tears the session down the instant either partner dies, so a dead, death-kicked rider can never be snapped back onto the carrier from across the map.
- **Piggyback free-look**: the rider can glance around within a configurable yaw cone instead of a hard camera lock (default 180°).
- **New `MarriageBackupParticipant`** registers marriage with EL core's suite backup/restore (`/el restore all`).
- **Home coords hidden by default** (anti-leak) with a `VIEW COORDS` reveal toggle and a `SET HOME` overwrite-confirmation bar.
- **`VIEW PROFILE`** button opens EndlessGuilds' read-only spouse profile card when Guilds is installed.
- **Ring prestige gating fixed** for offline spouses (`getHighestPrestigeLevel`), and a **tiered-ring stat rebalance** (Defense buffed hard; Strength/Sorcery/Flow S-tier trimmed).

### Couple shared dungeons (`new feature`)

- New config `marriage_shared_dungeons` (default `true`) in [`MarriageConfig`](src/main/java/com/airijko/endlessmarriage/config/MarriageConfig.java) → `isSharedDungeonsEnabled()`. When enabled, each spouse clicking the same dungeon lands in the **same instance** — the engine routes them together exactly like a party, without either having to form one.
- Pushed to core via `EndlessLevelingAPI.setCoupleSharedDungeonsEnabled(...)` in [`onEnable`](src/main/java/com/airijko/endlessmarriage/EndlessMarriage.java), and **re-pushed on `/marry reload`** so the toggle survives a live config reload.
- Kill-switch: `false` → spouses get independent instances.

### Banked-instance even-split (`bug fix` — the instance bypass)

- Inside a dungeon/rift/wave, kill XP is diverted into the killer's personal **claim-or-lose bank** *before* the normal XP-grant listener runs, so the overworld 50/50 even-split never fired there (couples earned nothing for each other in instances).
- Fixed at the bank level: `EndlessMarriage` now registers a `CoupleBankSplitResolver` via `EndlessLevelingAPI.setCoupleBankSplitResolver(...)` that answers *"who is this earner's spouse?"*. EL core's XP banks apply the same even-split and verify the spouse is a live participant in the same instance themselves. Cleared to `null` in `onDisable`.

### Piggyback: cross-world carry (`new behavior`)

- The pre-teleport listener in [`EndlessMarriage`](src/main/java/com/airijko/endlessmarriage/EndlessMarriage.java) was rewritten:
  - **Carrier** teleporting cross-world → **no longer dismounts**. The teleport sites pull the rider into the carrier's destination (`EndlessLevelingAPI.pullPiggybackRider`, fed by `setPiggybackRiderResolver(piggybackService::getRiderFor)`), so the pair stays attached across the world boundary and the follow/seat-stream systems re-seat the rider on arrival.
  - **Rider** teleporting on its own (admin TP, `/spawn`) → session ends (the carrier isn't moving, so there's nothing to follow).
- The rider resolver is cleared (`setPiggybackRiderResolver(null)`) in `onDisable` so core/Rifts stop resolving a rider from an unloading plugin.

### Piggyback: death detach (`new system`, crash/teleport safety)

- New [`PiggybackDeathDetachSystem`](src/main/java/com/airijko/endlessmarriage/systems/PiggybackDeathDetachSystem.java) (a `DeathSystems.OnDeathSystem`, **player-only query**) clears the in-memory piggyback session the instant **either** participant dies.
- **Why:** dungeons death-kick players out and (correctly) don't return them. Without this, the lingering session would let `PiggybackFollowSystem` snap the dead, now-distant rider back onto the carrier from thousands of blocks away once the carrier exited the instance. Detaching before the death-kick teleport runs prevents the re-converge. Pure map teardown — the rider holds no engine `MountedComponent` (the seat is a server-streamed `BlockMount`).

### Piggyback: follow-system grace window (`hardening`)

- [`PiggybackFollowSystem`](src/main/java/com/airijko/endlessmarriage/systems/PiggybackFollowSystem.java) gained a per-rider `carrierAbsentSeconds` timer. If the carrier stays absent from the rider's store past `CARRIER_ABSENT_DETACH_SECONDS` (3s) — a teleport path that wasn't wired to pull, or a mid-window carrier disconnect — the session is **force-detached** rather than left to snap the rider back when the carrier reappears. The tick body still never initiates a transfer itself (avoids the documented transfer-race / interaction-tick IOOBE); it's detach-only.
- The timer map is pruned each tick against the live session set (and cleared when no sessions exist) so it can't grow unbounded.

### Piggyback: free-look (`new feature`)

- New config `piggyback_seat_free_look_enabled` (default `true`) and `piggyback_seat_free_look_degrees` (default `180.0`, clamped `[0,360]`) in [`MarriageConfig`](src/main/java/com/airijko/endlessmarriage/config/MarriageConfig.java).
- [`PiggybackSeatStreamSystem.computeFreeLookOrientation`](src/main/java/com/airijko/endlessmarriage/systems/PiggybackSeatStreamSystem.java) streams the rider's **own** yaw clamped to a cone centered on the carrier's facing (rider's pitch passed through, carrier's roll). The cone travels with the carrier, so the rider keeps generally facing forward but can glance to either side; inside the cone it echoes the rider's own look (no camera fight), pinning only at the edge. `false` → hard-lock to the carrier's facing (the prior behavior); rider-look unavailable → falls back to hard-lock.

### Suite backup integration (`new`)

- New [`MarriageBackupParticipant`](src/main/java/com/airijko/endlessmarriage/backup/MarriageBackupParticipant.java) implements EL core's `SuiteBackupParticipant` (id `"marriage"`), registered in `onEnable`.
  - **Export:** flushes `MarriageDataManager` / `TieredRingDataManager` / `MarriageOverflowLog` to disk, then copies every `*.json` in the data folder into the snapshot.
  - **Restore-all:** copies the snapshot JSON back and reloads the three managers in place.
  - **Per-player restore is unsupported** — marriage state is couple-scoped (pairs, officiants, shared homes) and can't be safely sliced; use `/el restore all`.

### Home coords privacy + overwrite guard (`UI`)

- In [`MarriageMainPage`](src/main/java/com/airijko/endlessmarriage/ui/MarriageMainPage.java), the home card no longer prints raw coords by default — it shows `Home set · coords hidden`. A new **`VIEW COORDS`** button toggles the reveal **for the open session only** (`coordsRevealed` resets on re-open); guards against accidental leaks while streaming/screenshotting.
- Pressing **`SET HOME`** when a home already exists now opens a **`SetHomeConfirmBar`** ("Overwrite your existing home…") with `CONFIRM OVERWRITE` / `CANCEL`, instead of silently moving the shared home on a stray click. Setting the *first* home still saves immediately. New handlers: `handleSetHomeConfirm` / `handleSetHomeCancel` / `handleToggleCoords`, with `doSetHome` / `applyHomeInfo` helpers, plus the matching `.ui` markup in [`MarriageMainPage.ui`](src/main/resources/Common/UI/Custom/Pages/Marriage/MarriageMainPage.ui).

### View spouse profile (`UI`, soft Guilds integration)

- New **`VIEW PROFILE`** button on the main page opens **EndlessGuilds'** read-only `guild-profile` card for the spouse (DB-backed, so it works while they're offline). Routed through core's `MenuRegistry` param-page seam (the same path EndlessLink uses), so EndlessMarriage needs **no compile/reflection dependency** on Guilds — the button is hidden unless `MenuRegistry.hasParamPage("guild-profile")` is true, and `handleViewProfile` degrades gracefully if the page is gone.

### Ring prestige gating fix (`bug fix`)

- [`MarriageRingPage`](src/main/java/com/airijko/endlessmarriage/ui/MarriageRingPage.java) and [`MarriageRingVariationPage`](src/main/java/com/airijko/endlessmarriage/ui/MarriageRingVariationPage.java) now gate on each partner's **highest prestige across their profiles** via `getHighestPrestigeLevel(...)` (persistent storage) instead of `getPlayerPrestigeLevel(...)`. **Why:** the old call collapsed an **offline** spouse's prestige to `0`, which locked every ring tier/variation above E whenever a partner was away. Both partners' highest prestige is now read from persistent storage, so the lower-of-the-two gate stays correct regardless of who's online.

### Tiered-ring stat rebalance (`balance`)

- Default catalog ([`TieredRingCatalog`](src/main/java/com/airijko/endlessmarriage/data/tiered/TieredRingCatalog.java)) and bundled `config.json` re-tuned (tiers `e/d/c/b/a/s`):
  - **Defense** — buffed across the board: `3/5/8/12/20/36` → `12/20/30/48/76/120`.
  - **Strength** — A/S trimmed: `…/65/120` → `…/63/100`.
  - **Sorcery** — A/S trimmed: `…/65/120` → `…/63/100`.
  - **Flow** — E/D/B/S adjusted: `15/25/40/65/100/180` → `16/26/40/64/100/160`.

### Config & version

- `MarriageConfigMigrator.BUNDLED_CONFIG_VERSION` bumped `4` → `6`; `config.json` `config_version` synced to `6`. Existing user configs are merged forward on next boot to pick up `marriage_shared_dungeons`, `piggyback_seat_free_look_*`, and the rebalanced `tiered_rings` values.
- `manifest.json`: `2.13.0` → `3.1.0`; `EndlessLevelingCore` dependency `≥ 9.34.1` → `≥ 9.35.0`.

Build target jar: `EndlessMarriage-3.1.0.jar` (**requires EL Core ≥ 9.35.0**).

---

## 2026-06-23 — 2.13.0

### Carry your partner (piggyback, reversed) + note on the existing XP-overflow toggle

**Carry partner.** Added the mirror of piggyback: instead of hopping onto your spouse, you pick *them* up and carry them on your back. It reuses the entire piggyback session machinery — the spouse becomes the passive `BlockMount` rider and the sender is the carrier/driver — so the seat-stream camera, follow/anti-wander, damage-reduction and dismount logic all apply unchanged (they key off the rider→carrier maps, not on who initiated).

- **`PiggybackService.tryCarry(carrier, ref, store)`**: resolves the sender's spouse as the rider and registers the session with the sender as carrier — the exact inverse of `tryMount`. Same engine `MountedComponent` clone()-corruption guard, same proximity (`piggyback_max_range`) check. New `MountResult` codes `ALREADY_CARRYING` / `SELF_IS_RIDING` for the carry-side occupancy cases.
- **`/carry` command** (top-level, mirrors `/piggyback`): toggles — picks the spouse up, or sets them down if already carrying; hops you off if you're the one being carried. Registered in `MarriageCommandRegistrar`.
- **UI**: new `CARRY` button in the Companion → Piggyback card on the `/marry` main page (`marry:carry` → `handleCarry`). While in any session, `PIGGYBACK`/`CARRY` disable and only `DISMOUNT` is actionable.
- **Lang**: new `ui.marriage.carry.*` keys (`success_self`, `spouse_carried`, `put_down_self`, `too_far`, `already_carrying`, `self_riding`) added to all 8 shipped locales in EL core's `endlessleveling.lang` (en-US is the fallback). **Requires EL Core redeploy** for the new strings, same as prior marriage features.

**XP-overflow toggle (already shipped).** The requested "enable/disable XP Overflow for Marriage" config already exists as `xp_overflow_funnel_enabled` (default `true`) in `config.json` — added in 2.12.0, fully gating both funnel paths (`MarriageOverflowService.handleOverflow` and the capped-spouse branch in the even-split listener). Servers that don't want it set it to `false`. No code change needed; documented here for discoverability.

Build target jar: `EndlessMarriage-2.13.0.jar` (+ EL Core for the lang keys).

---

## 2026-06-23 — 2.11.5

### Chat feedback modernized to match EndlessLeveling core (split-color prefix + named palette)

Brought all player-facing chat in line with EL core's `PlayerChatNotifier`/`ChatMessageStrings` style: the prefix tag now renders in its **own** color, joined (`Message.join`) to a separately-colored body — instead of the whole line being flat-tinted with the body color.

- **`MarriageMessages`**: added a centralized `MarriageMessages.Color` palette (mirrors core's `ChatMessageStrings.Color`) — `SUCCESS`/`ERROR`/`INFO`/`WARN`/`ROMANCE`/`MUTED` plus brand prefix colors. The `[Endless Marriage]` / `[Marriage]` / `[Rings]` tags now render in romance-rose `PREFIX_BRAND` (`#ff7fb0`); `[Marriage Debug]` / `[Marriage Admin]` in slate `PREFIX_MUTED` (`#9fb6d3`). The 4 localized helpers (`chat`/`shortChat`/`debugChat`/`ringsChat`) were rewritten to join the brand prefix to the body, so **all ~200 helper call sites pick up the new look automatically**. Added raw-text builders `prefixedLine`/`shortLine`/`debugLine`/`adminLine` for inline call sites that compose their own strings.
- **`MarriageUtil`**: `COLOR_*` constants now delegate to the palette (single source of truth); added `msg(body, color)` so inline command feedback gets the same split-color prefix. Dropped the baked-in `PREFIX` literal.
- **Migrated inline sites**: `ProposeCommand`/`AcceptCommand`/`DivorceCommand`/`DenyCommand` (`Message.raw(PREFIX + …)` → `msg(…)`), the 8 debug/admin commands (`Message.raw("[Marriage Debug/Admin] …")` → `debugLine`/`adminLine`), `MarriageInteractListener` (→ `shortLine`), and `MarriageAnnouncer` (wedding broadcast now joins a rose prefix to the green body, per-locale preserved).

No lang changes required — prefix text still resolves from `endlessleveling.lang`; only the rendering split + colors changed. Build target jar: `EndlessMarriage-2.11.5.jar`.

---

## 2026-06-23 — 2.11.4

### Piggyback: rider camera follows via a server-authoritative BlockMount seat (carrier is the driver)

Replaces the 2.11.3 `Minecart` mount, which the source shows can't work for this: `MountController` has only `Minecart` and `BlockMount`, and **`Minecart` makes the rider the driver** (the rider's client locally simulates the mount and writes the mount target's transform — `MountSystems.HandleMountInput` / `GamePacketHandler.handleMountMovement`), so a self-walking carrier never moves on the rider's client and the camera freezes. `BlockMount` is the opposite: a **server-authoritative** seat the rider rides passively. So the design is now:

- **Carrier = driver:** unchanged — the carrier is an ordinary player moving under its own control.
- **Rider = passive BlockMount seat:** new `PiggybackSeatStreamSystem` streams a `MountedUpdate{controller = BlockMount, block.position = carrier position + seat height}` to the rider's viewers — **including the rider itself** (a player is a viewer of itself at distance 0, so it gets the seat and therefore the camera). Runs in `EntityTrackerSystems.QUEUE_UPDATE_GROUP` (mirroring the engine's own `MountSystems.TrackerUpdate`) so per-viewer visibility is populated and `EntityViewer.queueUpdate` is safe. No `MountedComponent` is attached, so the rider is never put in driver mode and can't steer the carrier.
- **`PiggybackFollowSystem` retained, role narrowed:** it slaves the rider's *server* position/velocity to the carrier — for onlooker body co-location, anti-wander, and to keep the rider inside the tracking range of players near the carrier so the seat stream reaches them. It does not (and cannot) drive the rider's own camera.
- Session end sends a `queueRemove(Mounted)` so the seat clears on clients.
- New config: `piggybackSeatStreamEnabled` (kill-switch, default true), `piggybackSeatHeight` (default 1.0), `piggybackSeatBlockId` (default `Chair`, resolved via `BlockType.getAssetMap()`, falls back to index 0).

> **Needs in-game testing.** `BlockMount` was designed for a *static* chair; whether the client smoothly interpolates a seat whose position changes every tick (vs. snapping, or latching the first value) is client-side behavior the server source can't confirm. If it snaps/jitters or doesn't follow, that's the signal to revisit (and `piggybackSeatStreamEnabled=false` is the kill-switch back to body-only carry). Build target jar: `EndlessMarriage-2.11.4.jar`.

---

## 2026-06-22 — 2.11.3

### Piggyback: rider camera now follows the carrier (mount re-added on top of the visibility fix)

Follow-up to 2.11.2. The 2.11.2 build fixed the rider invisibility but the rider's camera still didn't move when the carrier walked around. Root cause of that: 2.11.2 carried the rider purely by writing its server-side `TransformComponent` every tick — but **a player's client owns its own position**, so the server cannot move the rider's camera that way (the body moved for onlookers, but the rider's own view stayed put).

- Re-attached the engine `MountedComponent` (`MountController.Minecart`) to the rider — **with the 2.11.2 bounding-box fix kept in place** (this combination was never actually tested before; the original frozen-camera report was under the zero-volume-box/invisible condition). A player's camera follows an external anchor only when the client is in mount mode, and then the camera tracks the *mount entity* (the carrier), whose position the client already receives via normal entity tracking — so it follows.
- We never set the rider's `Player.mountEntityId`, so the rider's `MountMovement` packets are ignored server-side (`GamePacketHandler` keys on `mountEntityId`). The rider therefore **cannot steer/hijack the carrier** — the carrier keeps walking under their own control, matching the intended "carrier walks, rider is passive" model.
- `PiggybackFollowSystem` is retained but its role is now purely **spatial co-location**: it slaves the rider's server position (and velocity, for smooth onlooker interpolation) to the carrier so the rider stays in the tracking range of players near the carrier as it moves away from the mount point — otherwise the rider's `MountedUpdate` would stop reaching them and the rider would vanish for onlookers. `RIDE_OFFSET_Y` is now `0`; the visible seat offset comes from the MountedComponent attachment offset (`DEFAULT_OFFSET`, 1.5 blocks).
- `dismount` now uses `tryRemoveComponent` for the rider's MountedComponent (no warning if already gone).

> The bounding-box fix from 2.11.2 (below) is unchanged and still required: a zero-volume box trips `EntityTrackerSystems.LODCull` and makes the rider invisible.

---

## 2026-06-22 — 2.11.2

### Piggyback: rider no longer invisible + camera follows the carrier

Two long-standing piggyback bugs traced back to the same 2.1.1 fix attempt (the zero-volume `BoundingBox` + the `MountedComponent`); both are reversed here with the actual root cause addressed.

- **Rider invisibility (fixed).** Shrinking the rider's `BoundingBox` to a zero-volume box made `getMaximumThickness()` return `0`. The engine's `EntityTrackerSystems.LODCull` drops any entity where `maximumThickness < ENTITY_LOD_RATIO * distanceSq` — with thickness `0` that is true for every viewer not standing exactly on the rider, so the rider was removed from everyone's visible set and never rendered (no model/skin/mount packets sent). The box is no longer touched. (Players don't physically collide with each other in this engine — all entity-collision systems are NPC-only — so the box-shrink was guarding against a push that can't happen; the original "Jump in location" spam came from the follow-system teleport, and piggyback range is ≤5 blocks so no large jump occurs.)
- **Rider camera stationary (fixed).** The rider was given an engine `MountedComponent` with `MountController.Minecart` — the only entity-mount controller the engine has. That puts the rider's *own* client into "driver" mode: it simulates the mount's motion locally and anchors the camera to that local sim. Since a piggyback carrier is a real player who walks under their own power (the rider supplies no steering input), the rider's local sim never moved the "mount" and the camera froze. We no longer attach a `MountedComponent`. The rider is carried as an ordinary player whose position **and velocity** are slaved to the carrier every tick by `PiggybackFollowSystem`, which keeps the rider's normal player camera and makes it track the carrier smoothly.
- `PiggybackFollowSystem` now applies a small vertical seat offset (`RIDE_OFFSET_Y`, 1.0 blocks) so the rider sits on the carrier's back/shoulders instead of clipping inside them (the old client-side `MountedComponent` attachment offset is gone with the component).
- `PiggybackService` simplified: removed the `BoundingBox` snapshot/shrink/restore plumbing and the `MountedComponent` attach/remove; the engine-mount guards (refuse to start a piggyback while a participant is on a real minecart/seat) are retained.

> Note: the "rider is a passive passenger; the carrier walks normally" control model was chosen deliberately. The engine offers no native passenger-on-a-moving-entity mount mode, so the camera-follow is achieved via server-side position/velocity slaving rather than a client mount attachment.

---

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
