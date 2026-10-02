# ULTRAS_HOMES v1.1.0 — by UC_Hussein

Professional GUI-driven Homes plugin for **Paper** (Minecraft 1.21+), Java **25**, Gradle Kotlin DSL.
Per-player YAML storage with **lazy loading**, full teleport safety (countdown, cancel-on-move, cooldown,
arrival protection), an admin system, and English/Arabic language files.

> ⚠️ **Build status — read this first / اقرأ هذا أولًا:** this project was authored in a sandbox with
> **no internet access and no Gradle installed**, so `gradle build` could **not** actually be executed
> there and `BUILD SUCCESSFUL` was not produced in that environment. What *was* done: every `.java` file
> was syntax/type-checked with the JDK's own compiler (`javac`); the only errors it reports are
> "package org.bukkit... does not exist" / "package net.kyori... does not exist", which is exactly what
> you'd expect without the Paper API jar available — there are no other errors. Run the build once on
> your machine (below, needs internet the first time to fetch Paper API + Gradle) and send me the exact
> message if anything fails; I will fix it.
> Also: no `gradlew`/`gradle-wrapper.jar` is committed (generating one needs network access to Gradle's
> servers, which this sandbox doesn't have). Use your own local Gradle to build, or run `gradle wrapper`
> once to generate one for next time. The included CI workflow installs Gradle itself, so it doesn't need
> a committed wrapper either.

## 1) Build / البناء
```bash
# requirements: JDK 25 (or JDK 21, see build.gradle.kts) + Gradle 8.10+, internet on first run
gradle build
# output:
build/libs/ULTRAS_HOMES-1.1.0.jar
```
Only dependency: `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` (the latest Paper API that actually
exists; `api-version: '1.21'` in `plugin.yml` keeps the plugin loading on every current and future 1.21.x
Paper build without changes — "26.2" is not a real Paper/Minecraft version, so it was not invented here).

## 2) How the Homes system works
Every player has their own file: `plugins/ULTRAS_HOMES/data/homes/<uuid>.yml`. **Nothing is read from
disk at server startup.** A file is only loaded the first time it's actually needed (opening the GUI,
running `/home`, an admin looking the player up) — read on an async thread, then handed to the main
thread. After a player leaves, their data stays cached for `storage.unload-delay-seconds` (default 30s,
so a quick re-join or admin command doesn't force a re-read) and is then dropped from memory. Dirty
players are auto-saved every `storage.save-interval-seconds` (default 60s), and immediately on quit and
plugin disable. Writes are atomic (temp file + rename) with a `.bak` safety copy; a corrupted file is
recovered from `.bak` or quarantined (`.corrupt-<time>`) and the rest of that player's homes load fine.

## 3) How the GUI works
Every screen is driven entirely by its YAML file under `gui/` — materials, names, lore, slots and sounds
are all data, nothing is hardcoded in Java:
- **`gui/homes.yml`** — the player's own home list (`/home`, `/homes`, `/sethome`). `bed-slots` /
  `dye-slots` say exactly which inventory slots hold which home number; `homes-per-page` and pagination
  are computed from the player's current limit (and any "kept but locked" homes above it).
- **`gui/home_manage.yml`** — opened by clicking a **green** dye: Teleport / Delete (two-click confirm) / Back.
- **`gui/player_homes.yml`** — the admin's view of a target player's homes (`/home_admin tp <player>`),
  with an optional player-head info panel (`player-info` in `config.yml`). Admins can never save a home
  for someone else from here (spec requirement) — clicking an empty gray dye just shows a message.
- **`gui/admin_homes.yml`** — the admin's manage-one-home screen (Teleport / Delete / Back) for a target
  player's home, opened from `player_homes.yml`.

A home slot is **gray** (available, unsaved), **green** (saved) or **black** (locked — its number is
above the player's current limit). Clicking a gray dye saves the player's exact current location;
clicking a green bed teleports; clicking a green dye opens the manage screen; black slots do nothing
except tell the player which limit unlocks them. Rapid double-clicks are debounced (250ms) so nothing
can be double-saved/deleted/teleported.

## 4) How the limit system works
Effective limit = an explicit admin override if one was set (`/home_admin set`), otherwise the highest of
`homes.default-limit` and any granted `ultras.homes.limit.<N>` permission (works with LuckPerms out of the
box, no dependency on it). Everything is capped at `homes.maximum` (default 50). `/home_admin add` adds to
whatever the player currently has (never resets it); `/home_admin set` assigns an exact value. If a `set`
lowers the limit below the player's saved home count, `admin.set-limit-behavior` in `config.yml` decides
whether the extra homes are only **locked** (`KEEP`, default) or **deleted** (`DELETE_EXCESS`) — kept homes
are always still visible in the GUI as locked so nothing silently disappears.

## 5) How the admin system works
`/home_admin add|set|rest <player|all> [amount]` and `/home_admin tp <player> [home]`. The **`all`**
variants never load every player into memory at once: they process one player's file at a time (from
cache if already loaded, otherwise a direct disk read‑modify‑write), exactly as required. `/home_admin
reload` re-reads `config.yml`, both language files and all four GUI files, flushes any pending saves, and
restarts only the periodic-save timer — it never re-registers commands/listeners, so reloading repeatedly
cannot leak tasks or duplicate anything.

## 6) Teleport system
`TeleportManager` runs a countdown (`teleport.delay`, default 3s) with an action-bar tick, cancels itself
on movement past `teleport.move-tolerance` blocks (if `teleport.cancel-on-move: true`), on damage, on
death, on any other teleport, on quit and on plugin disable. After a successful teleport the player gets
`teleport.cooldown` seconds (default 10) before using another home (bypassable with
`ultras.homes.bypass.cooldown` when `admin.bypass-cooldown: true`) and `teleport.arrival-protection`
seconds of damage immunity (default 2s). The destination is re-validated (world loaded, coordinates
finite) right before the actual teleport in case something changed during the countdown.

## 7) Where things are
| What | Where |
|---|---|
| Player home files | `plugins/ULTRAS_HOMES/data/homes/<uuid>.yml` |
| Language | `plugins/ULTRAS_HOMES/messages/en.yml`, `messages/ar.yml` — pick with `language.default` in `config.yml` |
| GUI layout | `plugins/ULTRAS_HOMES/gui/homes.yml`, `home_manage.yml`, `player_homes.yml`, `admin_homes.yml` |
| Settings | `plugins/ULTRAS_HOMES/config.yml` (every section has an Arabic comment explaining it) |

## 8) Commands
| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/home` | `/uhome` | `ultras.homes.home` | No args: open GUI. `<name>`: teleport. `remove <name>`: delete. `rest [confirm]`: delete all (configurable confirmation). |
| `/homes` | `/uhomes` | `ultras.homes.home` | Open the Homes GUI |
| `/sethome` | `/usethome` | `ultras.homes.sethome` | Open the Homes GUI (saving happens on dye click, never on command) |
| `/home_admin add <player\|all> <amount>` | `/uhomeadmin` | `ultras.homes.admin.add` | Increase a limit |
| `/home_admin set <player\|all> <amount>` | | `ultras.homes.admin.set` | Set a limit exactly |
| `/home_admin rest <player\|all>` | | `ultras.homes.admin.rest` | Reset a limit to default (+homes if `admin.reset-homes-too: true`) |
| `/home_admin tp <player> [home]` | | `ultras.homes.admin.tp` | Teleport to / browse a player's homes |
| `/home_admin reload` | | `ultras.homes.admin.reload` | Reload config, messages and GUI files |

Full Tab Completion is implemented for every command and subcommand (home names, online player names, `all`).

## 9) Permissions
`ultras.homes.use` (default true) · `ultras.homes.home` · `ultras.homes.sethome` ·
`ultras.homes.admin` (op, grants every `admin.*` below) · `ultras.homes.admin.add/.set/.rest/.tp/.reload` ·
`ultras.homes.bypass.cooldown` (op) · `ultras.homes.bypass.limit` · `ultras.homes.bypass.world` (op) ·
`ultras.homes.limit.<N>` (e.g. `ultras.homes.limit.10`) — highest granted number wins, works with LuckPerms.

## 10) World blacklist
`world-blacklist.enabled: true` + `world-blacklist.worlds: [...]` blocks `/home`, `/homes`, `/sethome` and
teleporting *into* a home in that world entirely (with a message), independent of the GUI.

## 11) Building it yourself, step by step
1. Install JDK 25 (or 21) and Gradle 8.10+.
2. `git clone`/copy this project, `cd ULTRAS_HOMES`.
3. `gradle build` (first run needs internet to fetch Paper API + the toolchain if not already installed).
4. Copy `build/libs/ULTRAS_HOMES-1.1.0.jar` into your Paper server's `plugins/` folder and start it once.
5. Edit `plugins/ULTRAS_HOMES/config.yml`, then `/home_admin reload`.

## Project tree
```
ULTRAS_HOMES/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ README.md
├─ .github/workflows/build.yml
└─ src/main/
   ├─ resources/
   │  ├─ plugin.yml
   │  ├─ config.yml
   │  ├─ messages/ en.yml  ar.yml
   │  └─ gui/ homes.yml  home_manage.yml  player_homes.yml  admin_homes.yml
   └─ java/me/uc/hussein/ultrashomes/
      ├─ UltrasHomesPlugin.java
      ├─ command/   HomeCommand, SetHomeCommand, HomeAdminCommand
      ├─ config/    ConfigManager, MessageManager, GuiConfigManager
      ├─ gui/       Menu, GuiManager, HomesGui, HomeManageGui, PlayerHomesGui, AdminHomeManageGui
      ├─ home/      HomeManager, HomeStorage, HomeLimitManager
      ├─ teleport/  TeleportManager, PendingTeleport, ProtectionManager
      ├─ listener/  PlayerListener, TeleportListener, GuiListener
      ├─ model/     Home, PlayerHomeData, HomeLookup
      └─ util/      Text, TimeUtil, LocationUtil, SoundUtil, ItemBuilder
```
(`data/homes/` is created under the plugin's data folder at runtime — it is never bundled inside the jar.)


---
## v1.1.0 changes
**New commands**
| Command | Permission | What it does |
|---|---|---|
| `/home setting` | `ultras.homes.setting` (default true) | Personal settings GUI: Chat Messages ON/OFF, Plugin Sounds ON/OFF, Language English/العربية. Saved per player in `data/homes/<uuid>.yml` (`settings:` section). |
| `/home admin home <player>` | `ultras.homes.admin.home` | Admin GUI of a player's homes: view, teleport, manage/delete (confirmation), save a home in an empty slot (at the admin's location), +/- slot limit, settings button. |
| `/home admin setting <player>` | `ultras.homes.admin.setting` | Edit that player's settings only. |
| `/home admin add|set|rest|tp|reload` | same as `/home_admin` | Same code as `/home_admin`, which still works exactly as before. |

**Messages**: every message starts with `{prefix}` (the only place with the server name is `prefix:` in `config.yml`), Small Caps text, calm colours. `messages/en.yml` and `messages/ar.yml` have identical keys. Old files from v1.0.0 are backed up as `*.old-v1` and replaced automatically (same for `gui/*.yml`).
**Command conflicts / Tab**: `commands.*` in `config.yml`. If another plugin owns `/home`, `/homes`, `/sethome` or `/home_admin`, ULTRAS_HOMES' command is the one that runs; the other plugin is not unregistered (still reachable as `/pluginname:home`). Prefixed duplicates and long aliases are hidden from Tab; `/home_admin` is only shown to admins.
**Bug fixes**: cancel-on-move now measures from where the countdown started (before, walking slowly never cancelled it); placeholders `{home}` were passed as `home-mm` and never matched the messages; `teleport.countdown` action bar is configurable (`teleport.actionbar.*`).
