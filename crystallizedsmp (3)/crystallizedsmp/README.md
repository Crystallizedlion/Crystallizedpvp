# crystallizedsmp - Crystal PvP

## 1. Build the plugin (one jar, everything included)
Needs **JDK 25** and Maven (Paper 26.x requires Java 25).

    ./build.sh          # Windows: build.bat

This builds `CrystallizedPvP.jar` and copies it to `server/plugins/`. It contains the
Crystal PvP core and the whole staff system (ranks, punish GUI, bans/mutes, freeze, vanish...).

The plugin targets **Paper 26.3** (`api-version: '26.3'`). Paper only publishes beta builds for 26.3
so far; the pinned build is in `plugin/pom.xml` (`paper.version`).

## 2. Set up the server
1. Download Paper **26.3** from https://papermc.io/downloads and save it as `server/paper.jar`.
2. Make sure `java -version` shows 25 or newer.
3. Run `server/start.sh` (or `start.bat`). It stops the first time: open `eula.txt`, set `eula=true`, run again.
4. `op` yourself in the console, join, stand where you want spawn and run `/setspawn`.

## 3. Recommended extras (optional)
- LuckPerms - ranks/permissions
- ViaVersion + ViaBackwards (+ ViaRewind for 1.8) - more client versions
- Geyser + Floodgate - Bedrock players
- A build/protection plugin (WorldGuard) to protect spawn, plus an anti-cheat

## 4. What the plugin does
- Kit on join, respawn and `/kit` (edit it in `config.yml`)
- Instant crystal breaking, crystal power and block damage configurable
- No attack cooldown, no fall damage, no hunger
- Instant respawn, no item drops on death
- Killstreaks, `/stats`, `/spawn`, `/setspawn`
- Edit `plugins/CrystallizedPvP/config.yml`, then restart.

## 5. Staff system (v1.1)

Ranks: **Member < Helper < Mod < Admin < Owner**. Server operators count as Owner, so
op yourself (`op <you>` in the console) and then promote people:

    /staff set <player> <helper|mod|admin|owner>
    /staff remove <player>
    /staff list

You can only punish players with a strictly lower rank than yours.
Minimum rank per action is in `config.yml` under `staff.required`.

| Command | What it does | Default rank |
|---|---|---|
| `/punish <player>` | Punishment GUI: pick a reason, then warn / kick / mute / ban | Helper |
| `/warn`, `/kick` | Warn or kick with a reason | Helper |
| `/tempmute <p> <time> [reason]`, `/unmute` | Timed mute (30m, 12h, 7d, 2w) | Helper |
| `/mute <p> [reason]` | Permanent mute | Mod |
| `/tempban <p> <time> [reason]`, `/unban` | Timed ban | Mod |
| `/ban <p> [reason]` | Permanent ban | Admin |
| `/history <player>` | Last 10 punishments | Helper |
| `/freeze <player>` | Freeze toggle (no move, commands, damage, placing) | Helper |
| `/vanish` | Hide from non-staff | Mod |
| `/sc [message]` | Staff chat (no message = toggle) | Helper |
| `/invsee <player>` | View inventory (edit needs Admin) | Mod |
| `/clearchat`, `/mutechat` | Clear / lock chat | Helper / Mod |
| `/report <player> <reason>` | Anyone can report, staff get an alert | Everyone |

Bans, mutes and the rank list are saved in `plugins/CrystallizedPvP/` (`punishments.yml`, `staff.yml`).
Set `staff.appeal` in `config.yml` to your Discord link.

## Get the jar without installing anything (GitHub)
1. Make a free account at github.com and create a new repository (any name, private is fine).
2. Click "uploading an existing file" and drag in everything from this folder, **including the hidden `.github` folder**, then commit.
3. Open the **Actions** tab, click "Build CrystallizedPvP" and wait for the green tick (about 1-2 minutes).
4. Open the finished run, scroll to **Artifacts**, and download `CrystallizedPvP`. Unzip it: inside is `CrystallizedPvP.jar`.
5. Upload that jar to your host's `plugins` folder.
