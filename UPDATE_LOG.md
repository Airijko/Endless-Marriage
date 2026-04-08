# Endless Marriage - Update Log

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
