# Endless Marriage - Update Log

## 2026-07-16 — 3.4.0 (compared to 3.3.0)

Security/consistency/cleanup pass. Officiate-UI actions now re-check the priest/magistrate role and the pending-marriage pair match **server-side** (client-supplied action strings could no longer bypass the gate the button visibility implied). Divorce now unequips both partners' **tiered rings**, clearing the EL attribute bonus instead of leaving it to re-apply on every join/augment reconcile forever. The marriage discipline/kiss XP bonus is credited via `adjustRawXp` instead of `grantXp`, so it is no longer double-bonused against the even-split strip's own math. The backup snapshot now flushes pending `AsyncFileWriter` writes before copying, so a snapshot can't capture stale bytes. The Rings-unlock hint on the main page now reads account-scoped `getHighestPrestigeLevel` for both partners (an offline spouse no longer reads as Prestige 0). `MarriageDataManager` saves are now async + per-section instead of rewriting every JSON file on every mutation. The legacy cosmetic wedding-ring system (`WeddingRingTier`) is fully removed — tiered rings are the only ring system. Two piggyback systems' silent/log-once failure handling is now a 60s rate-limited warning so recurring bugs surface instead of going dark forever.

### Officiate-UI server-side role/pair gates (`security fix`)

- `MarriageOfficiatePage.handleOfficiate` now re-checks `config.isRequirePriestForMarriage()` + `OperatorHelper.isOperator` + `MarriageUtil.isPriest` before officiating, and verifies the two UUIDs are pending **with each other** (mirrors `OfficiateCommand`'s pair-match check) — not just each independently pending with someone.
- `MarriageOfficiatePage.handleGrantDivorce` now re-checks `config.isRequireMagistrateForDivorce()` + `OperatorHelper.isOperator` + `MarriageUtil.isMagistrate` before granting. UI action strings are client-supplied, so the role must be re-verified server-side, not only at page-build time.
- New shared `MarriageUtil.isPriest(UUID)` / `isMagistrate(UUID)` helpers, deduplicating the inline primary/secondary-class checks previously copy-pasted across `MarriageOfficiatePage`, `MarriageMainPage`, `OfficiateCommand`, and `GrantDivorceCommand`.

### Divorce clears tiered-ring EL bonuses (`bug fix`)

- New `TieredRingDataManager.unequipOnDivorce(UUID)`: clears the persisted equip entry, zeroes the runtime attribute bonus, and — if the player is online — hops the entity stat-map refresh to their own world thread (divorce can be triggered from another world's thread; ECS access is world-thread-bound).
- `MarriageDataManager.divorce()` now calls it for both partners instead of only clearing the (now-removed) legacy `weddingRings` map.

### XP math correction (`bug fix`)

- `EndlessMarriage.createXpGrantListener`'s step-1 discipline/kiss bonus grant now uses `EndlessLevelingAPI.adjustRawXp` instead of `grantXp`. `grantXp` re-applies the player's own discipline/luck/passive bonuses and clips at the per-kill gain cap, so the credited amount no longer matched the exact `adjustedXp * pct` the even-split strip's `killerDiscMult` math assumes.

### Backup / persistence hardening (`bug fix`)

- `MarriageBackupParticipant.exportTo` now calls `AsyncFileWriter.INSTANCE.flushAllNow()` after the manager saves and before copying the data folder, so the snapshot can't race a still-pending debounced write.
- `MarriageDataManager` saves are now per-section (`saveMarriages`/`saveRecords`/`saveHomes`/`savePriestInbox`/`saveDivorceCooldowns`) via a shared `writeJson` helper backed by `AsyncFileWriter`, instead of every mutation rewriting all five JSON files synchronously. `marry()`/`divorce()`/etc. now only save the sections they actually touched.

### UI correctness (`bug fix`)

- `MarriageMainPage`'s ring-unlock hint now reads `EndlessLevelingAPI.getHighestPrestigeLevel` (account-scoped) for both the player and their spouse, instead of the active-profile getter which reads an offline spouse as Prestige 0 and could falsely lock an already-unlocked tier.

### Legacy ring system removed (`cleanup`)

- Deleted `data/WeddingRingTier.java`, `MarriageDataManager.getRing/setRing/loadRings/saveRings`, and `MarriageMainPage.handleRingUpgrade` + its `marry:ring_upgrade` action. Tiered rings (the attribute-typed ring system) are the only ring system now; `rings.json` is orphaned on disk with no migration needed.
- Deleted 17 confirmed-dead `MarriageMessages` constants left over from the legacy ring/proposal/divorce flows.

### Piggyback diagnosability (`bug fix`)

- `PiggybackSeatStreamSystem` and `PiggybackFollowSystem` both replace their log-once-forever / silent-catch failure handling with a 60-second rate-limited warning (with cause), so a recurring bug in either tick loop surfaces in the logs instead of going dark after the first occurrence.

### Shared utilities (`cleanup`)

- New `util/PositionUtil` (`resolvePosition` + `distanceSq`) replacing identical private duplicates in `MarriageOfficiatePage`, `OfficiateCommand`, `MarriageProximitySystem`, `PiggybackService`, and `KissService`.
- New `util/PlayerNameResolver` replacing the identical `resolvePlayerName` duplicates in `MarriageOfficiatePage` and `MarriagePriestPage`. `MarriageMainPage`, `MarriageOverflowLogPage`, and `MarriageAdminListPage` keep their own copies — they carry an extra offline-DAO-name fallback the shared version doesn't have.

### Config & version

- `manifest.json`: `3.3.0` → `3.4.0`. No new EL Core APIs consumed (`adjustRawXp`, `getHighestPrestigeLevel`, `OperatorHelper.isOperator` all pre-date this release); dependency floor unchanged.

Build target jar: `EndlessMarriage-3.4.0.jar`.

---

## 2026-07-13 — 3.2.4 (compared to 3.2.2)

Collapses the whole unlogged 3.2.3 → 3.2.4 window (`8dde4d7` → `57a55d2` → `19baeb6` → `7d62a1b` → `c4d70f3` → `89474be` → `5f01862`) into one release. Two headlines: a **new admin command — `/marry admin reset-cooldown <player>`** to clear a player's post-divorce remarriage lock, and a correction to the **+25% "near spouse" proximity Discipline XP bonus** so it now rides *each partner's own half* of the even split (both spouses near each other each get their own +25%, instead of only the killer keeping it on the whole kill). Alongside those: the even split now pays a spouse who is in a **party with outsiders** (50/50 on the killer's full kill, without double-paying via the party loop), two new **overflow-effectiveness knobs** (raid `0.35`, combat `0.25`), and a **crash-durability fix** for raid overflow. It raises the core floor to **EndlessLeveling Core ≥ 10.0.4** (was `≥ 9.36.0`) — the spouse-in-outsider-party split consumes new core seams (`getCurrentGrantSourceName`, `isInPartyWith`, `markCoupleEvenSplitSpouse`). **Requires an EL Core ≥ 10.0.4 redeploy alongside this jar.**

### Highlights

- **New admin command `/marry admin reset-cooldown <player>`** (alias `clear-cooldown`) — clears a player's post-divorce remarriage cooldown so they can propose/accept again immediately. Ops-only; accepts an online name **or** a raw UUID (so offline players can be cleared).
- **+25% proximity Discipline XP bonus is now per-half in the even split** — both partners near each other each apply their **own** `discipline_bonus_percent` (default 25%) to **their** half of a shared kill (`killerDiscMult` / `spouseDiscMult`), exactly like xp-boost/luck. Previously the killer kept the +25% on the *full* kill while the spouse's half went unbuffed; the couple total is unchanged but now splits evenly instead of leaving the killer ahead. Kiss buffs stay per-partner. When the split collapses 100/0 (spouse capped), the killer's full-kill proximity bonus stands.
- **Even split now works when the spouse is in a party with outsiders** — the killer-spouse's own full-kill grant (`MOB_KILL` / `PARTY_KILL`) splits 50/50 with the spouse, who is precisely excluded from the party-share loop via a spouse marker; outsiders still draw their normal member share. Already-divided `PARTY_SHARE` cuts never split (would inflate a member's share).
- **Two overflow-effectiveness knobs** — `xp_overflow_raid_effectiveness` (default `0.35`) throttles raid/boss-reward overflow only; `xp_overflow_combat_effectiveness` (default `0.25`) throttles combat overflow (mob kills / party kills-shares / matchmaking shares) only. Independent knobs; `1.0` = no nerf.
- **Raid overflow is now crash-durable** — the spouse's raid-overflow credit is force-flushed (`flushPlayerNow`) instead of riding the ~5s coalesced save, so a crash in that window no longer loses XP the ledger/chat already reported as delivered. Combat overflow stays on the coalesced path (per-kill hot path).

### New admin command — reset remarriage cooldown (`new feature`)

- New [`ResetCooldownCommand`](src/main/java/com/airijko/endlessmarriage/commands/subcommands/ResetCooldownCommand.java) registered under `/marry admin` in [`DebugCommand`](src/main/java/com/airijko/endlessmarriage/commands/subcommands/DebugCommand.java). Command name `reset-cooldown`, alias `clear-cooldown`, generates its own permission and additionally gates on `OperatorHelper.isOperator`.
- Resolves the target by **online player name first, then falls back to parsing the argument as a UUID** — so an offline player can still be cleared by UUID. Reports "no active divorce cooldown" when `getDivorceTimestamp` is null, otherwise calls `MarriageDataManager.clearDivorceCooldown(uuid)`, confirms to the sender, and (if online) DMs the target that they may marry again.
- The `/marry admin` help line now reads `… | list | reset-cooldown <player>`.

### Proximity + even-split XP correction (`bug fix` / `rework`)

- In [`EndlessMarriage`](src/main/java/com/airijko/endlessmarriage/EndlessMarriage.java)'s XP grant listener, the discipline/kiss bonus is granted on the **full** earned amount in step 1 (`marriageDisciplineBonusPercent` = proximity `discipline_bonus_percent` + active kiss buff, additive — single source of truth shared with the profile-UI Discipline-row provider). When the even split runs, folding **`killerDiscMult = 1 + disciplineBonusPct/100`** into the earner's strip reduces that bonus to the earner's **half**, and **`spouseDiscMult = 1 + marriageDisciplineBonusPercent(spouseUuid)/100`** applies the spouse's *own* proximity/kiss bonus to their credited half. Applied across all three split paths (mob-context, non-mob base/2, legacy raw half).
- **Spouse-in-outsider-party gate:** the split now also runs when `spouseInOutsiderParty && fullKillGrant` — i.e. the spouse is a party member but the party has outsiders, and this grant is the killer's own `MOB_KILL`/`PARTY_KILL` (never an already-divided `PARTY_SHARE`). `api.markCoupleEvenSplitSpouse(spouse)` tells core to exclude *just the spouse* from its share loop (new cores); the legacy `markCoupleEvenSplitApplied()` boolean still fires only for couple-only parties so an old core can't starve outsiders.

### Overflow balancing & durability (`balance` / `bug fix`)

- New config `xp_overflow_raid_effectiveness` (default `0.35`) in [`MarriageConfig`](src/main/java/com/airijko/endlessmarriage/config/MarriageConfig.java) → `getXpOverflowRaidEffectiveness()`, applied to the **RAID** overflow channel only in [`MarriageOverflowService`](src/main/java/com/airijko/endlessmarriage/services/MarriageOverflowService.java) before funneling to the spouse.
- New config `xp_overflow_combat_effectiveness` (default `0.25`) → `getXpOverflowCombatEffectiveness()`, applied to the **COMBAT** channel only. Combat overflow is a high-frequency per-kill path; it stays on the coalesced save.
- Raid overflow credit forced durable via `api.flushPlayerNow(spouse)` (raid overflow is rare, bounded, and large; the ledger/chat notification are already synchronous/visible on the same call).

### Config & version

- `config.json` `config_version` bumped `7` → `8` (raid effectiveness) → `9` (combat effectiveness); `MarriageConfigMigrator.BUNDLED_CONFIG_VERSION` = `9`. Existing user configs merge forward on next boot to pick up both `xp_overflow_*_effectiveness` keys.
- `manifest.json`: `3.2.2` → `3.2.4`; `EndlessLevelingCore` dependency `≥ 9.36.0` → `≥ 10.0.4`.
- Build tooling: `copyToEndlessJars` no longer fingerprints the shared jar folder (faster incremental builds; `89474be`).

Build target jar: `EndlessMarriage-3.2.4.jar` (**requires EL Core ≥ 10.0.4, deploy both together**).

---

## 2026-06-25 — 3.2.2 (compared to 3.1.0)

A post-V3 hardening pass: six commits (`a5ab9f3` → `8acad48` → `4002a47` → `51676be` → `b2a9db9` → `51fac0a`) collapsed into one release. The headlines are a **piggyback master kill-switch**, the **reversal of the 3.1.0 free-look mechanism** (replaced with a level "face-the-carrier" seat plus a body back-offset so the rider stops covering the camera and staring at the ground), an **XP even-split rework** (best-multiplier carry, boost-free base), an **offline-spouse ring-equip fix**, and an optimized **Refixes targeting bridge**. It bumps the core floor to **EndlessLeveling Core ≥ 9.36.0** (was `≥ 9.35.0`) — the reworked even-split consumes `computeMobKillGrantBaseFor` / `getXpBoostMultiplier` seams. **Requires an EL Core ≥ 9.36.0 redeploy alongside this jar.**

### Highlights

- **Piggyback/carry master kill-switch** — new `piggyback_enabled` config; `false` refuses `/piggyback`, `/carry` and the right-click-spouse interaction, and `/marry reload` tears down any in-flight sessions immediately.
- **Free-look removed** (reverted): a `BlockMount` seat can't both follow a moving carrier *and* grant independent look. The seat now streams the carrier's **live yaw with a leveled pitch/roll**, so the rider faces where the carrier walks instead of inheriting its downward walking-aim.
- **New `piggyback_back_offset`** pushes the rider's body behind the carrier so the model no longer overlaps/covers the camera.
- **XP even-split rework** — the kill is valued at the **best level-range multiplier** of the two spouses (XP-conserving), and the split base is **boost-free** so each partner re-applies their **own** xp-boost (like luck/discipline).
- **Offline-spouse ring-equip fix** — the ring *equip/upgrade* action now gates on account-scoped `getHighestPrestigeLevel`, so an offline spouse no longer collapses to prestige 0 and re-locks a ring the page just showed as equippable.
- **Refixes targeting bridge** prefers a 3-arg `install` that resolves a shooter's partner once per projectile tick.
- **UI**: the in-page divorce button was removed (command-only now) and the spouse prestige/level chips moved into their own progression card.

### Piggyback master kill-switch (`new feature`)

- New config `piggyback_enabled` (default `true`) in [`MarriageConfig`](src/main/java/com/airijko/endlessmarriage/config/MarriageConfig.java) → `isPiggybackEnabled()`. When `false`, `tryMount` / `tryCarry` short-circuit with a new `MountResult.DISABLED`, and the per-tick follow/seat/detach systems stay idle because nobody is ever registered as a rider.
- New `ui.marriage.piggyback.disabled` lang key surfaced from [`PiggybackCommand`](src/main/java/com/airijko/endlessmarriage/commands/subcommands/PiggybackCommand.java) and [`CarryCommand`](src/main/java/com/airijko/endlessmarriage/commands/subcommands/CarryCommand.java).
- New `PiggybackService.dismountAllSessions()` (snapshots rider UUIDs, dismounts each properly, then clears both maps defensively) is invoked from `/marry reload` in [`EndlessMarriage`](src/main/java/com/airijko/endlessmarriage/EndlessMarriage.java) — so flipping the switch off mid-session is **immediate**, not deferred to manual dismount.

### Piggyback free-look removed → level face-carrier + back-offset (`behaviour change`)

- The 3.1.0 free-look mechanism is **reverted**: removed configs `piggyback_seat_free_look_enabled` / `piggyback_seat_free_look_degrees` and the `computeFreeLookOrientation()` cone code in [`PiggybackSeatStreamSystem`](src/main/java/com/airijko/endlessmarriage/systems/PiggybackSeatStreamSystem.java). Documented conclusion: a `BlockMount` seat must re-send its position every tick (the carrier moves) and orientation rides in that same packet, so the client re-snaps the view each tick — a follow-seat and independent look are mutually exclusive.
- **Seat orientation** is now `new Vector3f(0f, cRot.y, 0f)` — the carrier's live **yaw** every tick, **pitch/roll zeroed**. Copying the carrier's pitch made the rider inherit its slight downward walking-aim (looking at the ground); leveling it locks the view to the horizon.
- **New `piggyback_back_offset`** config (default `0.45`): the rider is pushed **behind** the carrier along its facing (`(sin(yaw), cos(yaw)) * offset`) so the rider's model doesn't overlap the carrier and cover the camera. Applied in **both** the seat stream (authoritative visual, queued to every viewer) and [`PiggybackFollowSystem`](src/main/java/com/airijko/endlessmarriage/systems/PiggybackFollowSystem.java) (server body/tracking position) — the follow system now takes `MarriageConfig` and uses `TrigMathUtil`. `0` = co-located (old behaviour).

### XP even-split rework (`rework` — best-multiplier carry, boost-free base)

- The in-instance / overworld even-split in [`EndlessMarriage`](src/main/java/com/airijko/endlessmarriage/EndlessMarriage.java) now values the kill at the **best level-range multiplier among the two spouses** — `poolBase = max(earnerBase, spouseBase)` via `computeMobKillGrantBaseFor` — split once and XP-conserving (total never exceeds the best value). An underleveled killer's level-range penalty no longer drags the in-range spouse's half down; the in-range partner carries the pool. Each partner keeps `poolBase/2`.
- The split base is **boost-free**: each partner re-applies their **own** xp-boost (`getXpBoostMultiplier`), keeping xp-boost per-spouse exactly like luck/discipline. The earner's already-credited `adjustedXp` is scaled by `((half/earnerBase) − 1)` — a strip when the earner carries (`poolBase == earnerBase`), a top-**up** when the spouse's value carries (each keeps their own boost, which cancels in the ratio).
- Non-mob grants (no level-range/boost context) fall back to a plain `base/2` even split; the legacy no-base path (raw bonused half across) is unchanged. Funnel-awareness (spouse-at-cap collapses the split toward 100/0) is retained.

### Offline-spouse ring equip fix (`bug fix`)

- 3.1.0 fixed the ring *display* gate but the **equip/upgrade action** still read `getPlayerPrestigeLevel`. [`MarriageMainPage`](src/main/java/com/airijko/endlessmarriage/ui/MarriageMainPage.java) (ring upgrade) and [`MarriageRingVariationPage`](src/main/java/com/airijko/endlessmarriage/ui/MarriageRingVariationPage.java) now gate the action on account-scoped `getHighestPrestigeLevel` for **both** partners — matching the display gate. **Why:** the active-profile getter resolved an **offline** spouse (or one on a non-prestiged profile) to `0`, falsely re-locking a ring the page had just shown as equippable. ("Fix Equipping while Offline".)

### Refixes targeting bridge — single-shooter resolver (`integration`)

- [`PiggybackTargetingBridge`](src/main/java/com/airijko/endlessmarriage/bridge/PiggybackTargetingBridge.java) now prefers a **3-arg `install(BooleanSupplier, BiPredicate, UnaryOperator)`** on Refixes-Endless' `PiggybackPairs`, passing a new order-independent `PiggybackService.getPartnerFor(uuid)` (two cheap map lookups). The optimized projectile path resolves a shooter's partner **once per tick** instead of scanning every candidate. Falls back to the 2-arg `install` on older Refixes-Endless builds (`NoSuchMethodException`).

### UI cleanup (`UI`)

- The in-page **"REQUEST DIVORCE" button was removed** from [`MarriageMainPage`](src/main/java/com/airijko/endlessmarriage/ui/MarriageMainPage.java) — the event binding, the `marry:divorce` dispatch case, and the `handleDivorce()` method (72h-minimum + magistrate-pending logic) are all gone; divorce is command-only now.
- In [`MarriageMainPage.ui`](src/main/resources/Common/UI/Custom/Pages/Marriage/MarriageMainPage.ui), the spouse prestige/level chips were relocated out of the spouse header into their own **Spouse Progression Card** (chips flex to share the narrow side), and the HOME card height was retuned to fit its tallest state (label + info + the VIEW COORDS button row).

### Config & version

- `MarriageConfigMigrator.BUNDLED_CONFIG_VERSION` bumped `6` → `7`; `config.json` `config_version` synced to `7`. Existing user configs merge forward on next boot to pick up `piggyback_enabled` and `piggyback_back_offset`, and to drop the obsolete `piggyback_seat_free_look_*` keys.
- `manifest.json`: `3.1.0` → `3.2.2`; `EndlessLevelingCore` dependency `≥ 9.35.0` → `≥ 9.36.0`.

Build target jar: `EndlessMarriage-3.2.2.jar` (**requires EL Core ≥ 9.36.0**).

---

## 2026-06-24 — 3.1.0 (the V3 line, compared to 2.13.0)

The major version bump to **V3** collapses three commits (`3ed24c1` → `8923766` → `d26be3e`) into one release. It is the first build to require **EndlessLeveling Core ≥ 9.35.0** (was `≥ 9.34.1`) — the new couple-dungeon routing, banked-instance even-split, cross-world rider pull, and suite backup hooks all consume core seams added in 9.35.0. **Requires an EL Core ≥ 9.35.0 redeploy alongside this jar.**

### Highlights

- **Married couples share dungeon instances** like a party (without forming one) — new `marriage_shared_dungeons` toggle, bridged to EL core's couple share-key routing.
- **Even-split XP now works *inside* dungeons/rifts/waves**, not just the overworld — fixes the long-standing instance bypass via a new `CoupleBankSplitResolver` (see memory note "Marriage even-split bypassed in instances").
- **Piggyback survives cross-world teleports**: a carrier going through a portal/dungeon pulls the seated rider with them instead of dismounting.
- **New `PiggybackDeathDetachSystem`** tears the session down the instant either partner dies, so a dead, death-kicked rider can never be snapped back onto the carrier from across the map.
- **Piggyback free-look**: the rider can freely look around instead of having their camera dictated by the carrier, by holding the streamed seat orientation constant so it stops re-snapping the view each tick.
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

- Config `piggyback_seat_free_look_enabled` (default `true`) in [`MarriageConfig`](src/main/java/com/airijko/endlessmarriage/config/MarriageConfig.java) selects free-look vs. the legacy hard-lock. (`piggyback_seat_free_look_degrees` is retained for back-compat but no longer used — the new mechanism gives unrestricted look rather than a cone.)
- **Mechanism.** [`PiggybackSeatStreamSystem`](src/main/java/com/airijko/endlessmarriage/systems/PiggybackSeatStreamSystem.java) streams a server-authoritative `BlockMount` "seat" to the rider every tick so the camera *anchor* follows the moving carrier. The engine itself (`MountSystems.TrackerUpdate`) sends a static seat's orientation **once** (on a mount-state change or to newly-visible viewers) — after which the seated player's own `SetBody`/`SetHead` look input drives the camera freely (`MountSystems.HandleMountInput`; only *movement* dismounts a `BlockMount`). The earlier cone code recomputed a fresh orientation **every tick**, so the client re-snapped the view continuously — and because the echoed angle was a network round-trip stale, it fought the rider's live look and read as "camera locked to the carrier."
- The shipped `BlockMount` protocol serializes orientation **unconditionally** (`PacketIO.writeVector3f` dereferences it with no null guard — verified via `javap`, the build jar differs from `hytale-shared-source`), so the per-tick seat can't simply omit it. The fix instead **captures the carrier's facing once at session start and resends that same constant orientation every tick**: with nothing new to snap to, the rider's own look owns the camera. Per-rider captured value is cleared on session end so a re-mount re-captures. `false` → legacy hard-lock (re-assert the carrier's live facing each tick).

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
