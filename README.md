# ChatCop

**Smart, automatic chat moderation for Spigot, Paper & Folia (1.20+).**

ChatCop watches chat in real time and handles spam, advertising, toxicity, threats and profanity for you — with obfuscation-aware detection, a points-based punishment system, mutes, warnings, Discord alerts and in-game staff notifications.

- **Spigot:** https://www.spigotmc.org/resources/chatcop-chat-plugin-smart-auto-chat-moderation-system.135758/
- **Modrinth:** https://modrinth.com/plugin/chatcop

---

## Features

- **5 built-in filters**
  - **Spam** — flood detection, duplicate/similar messages (Levenshtein similarity), excessive caps, repeated characters, and **cross-player raid detection** (the same line from many accounts at once).
  - **Advertising** — IPs, URLs, domains, Minecraft server addresses and Discord invites, including spelled-out forms like `example (dot) com`. IP and URL blocking toggle independently, with a domain whitelist.
  - **Toxicity** — slurs and hate speech, sexual content (reported separately), plus your own custom phrases.
  - **Threats** — death threats, "kys", doxxing, DDoS/swat threats, and custom phrases.
  - **Profanity** — censor (`****`) or block mode, with a configurable per-message limit.
- **Obfuscation-aware detection** — sees through leet speak (`n1gg3r`), symbol substitution (`sh!t`, `f*ck`), separator tricks (`f.u.c.k`), letter spacing (`f u c k`), repeated characters and zero-width/invisible characters.
- **Filters everywhere players can type** — chat, **private messages and other commands** (`/msg`, `/me`, `/mail`, …), **signs**, **books** and **anvil renames**.
- **Four actions per match** — `ALLOW`, `CENSOR`, `BLOCK`, and `SHADOW` (ghost mode: only the sender sees their own message, so they don't realize they were filtered).
- **Points & punishments** — every violation awards points that decay over time; crossing a threshold runs your configured commands. Per-filter commands and a per-player cooldown are supported. Points, warnings and history **persist across restarts**.
- **Mutes & warnings** — temporary or permanent mutes that work on **offline players too**, persisted to disk, with a mute-exempt permission for staff.
- **Chat control** — slowmode, a global chat lock, and a first-join chat delay (very effective against bot raids).
- **Discord webhooks** — rich embed alerts, per-filter colors and toggles, manual staff actions, rate-limit aware, and `@everyone` in a flagged message can never ping your server.
- **In-game staff alerts** — configurable message and sound, optional console mirroring.
- **PlaceholderAPI** support and optional **bStats** metrics.
- **Per-world disable**, global and **per-filter bypass** permissions, rotating file logging, persistent statistics.
- **Built-in update checker** (Spigot/Modrinth) — logs to console and notifies admins on join.
- **Folia-ready** — one jar runs on Spigot, Paper and Folia.

---

## Requirements

- **Server:** Spigot, Paper, Purpur or Folia **1.20+** (works on 1.21.x).
- **Java:** 17+.

---

## Installation

1. Download the latest `ChatCop-x.y.z.jar`.
2. Drop it into your server's `plugins/` folder.
3. Restart the server.
4. Edit `plugins/ChatCop/config.yml` to taste and run `/chatcop reload`.

Upgrading from an earlier version? Your existing `config.yml` keeps working — every new option has a built-in default. Add only the keys you want to change.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/chatcop` (aliases: `/cc`, `/cop`) | Main command — see subcommands below | `chatcop.admin` |
| `/chatcop reload` | Reload the configuration | `chatcop.admin` |
| `/chatcop stats` | View moderation statistics | `chatcop.stats` |
| `/chatcop history <player>` | View a player's violation history (works offline) | `chatcop.admin` |
| `/chatcop check <player>` | Points, warnings and mute status at a glance | `chatcop.admin` |
| `/chatcop test <message>` | Test what a message would trigger (also works from console) | `chatcop.admin` |
| `/chatcop clear <player>` | Reset a player's points and history | `chatcop.admin` |
| `/chatcop mutelist` | List active mutes | `chatcop.mute` |
| `/chatcop slowmode <seconds>` | Set chat slowmode (0 to disable) | `chatcop.admin` |
| `/chatcop lock` / `/chatcop unlock` | Lock or unlock chat | `chatcop.admin` |
| `/ccmute <player> [duration] [reason]` | Mute a player (online or offline) | `chatcop.mute` |
| `/ccunmute <player>` | Unmute a player (online or offline) | `chatcop.mute` |
| `/ccwarn <player> [reason]` | Warn a player | `chatcop.warn` |

All commands have tab completion.

**Durations:** `s`, `m`, `h`, `d`, `w` (combinable, e.g. `1h30m`), or `perm` for permanent. Example: `/ccmute Steve 2d Advertising`. A duration must include a unit — a bare number is rejected rather than silently treated as a permanent mute.

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `chatcop.admin` | Full access to ChatCop | op |
| `chatcop.mute` | Mute / unmute players | op |
| `chatcop.mute.exempt` | Cannot be muted by other staff | false |
| `chatcop.mute.override` | Can mute players who are normally exempt | false |
| `chatcop.warn` | Warn players | op |
| `chatcop.notify` | Receive in-game moderation alerts | op |
| `chatcop.stats` | View statistics | op |
| `chatcop.chatlock.bypass` | Talk while chat is locked | op |
| `chatcop.bypass` | Bypass **all** chat filters | false |
| `chatcop.bypass.<filter>` | Bypass one filter (`spam`, `advertising`, `toxicity`, `threats`, `profanity`) | false |
| `chatcop.bypass.slowmode` | Bypass chat slowmode | op |
| `chatcop.bypass.joindelay` | Bypass the first-join chat delay | op |

> **Note:** `chatcop.bypass` skips the *filters*. It does not let a muted player talk — mutes are always enforced.

---

## Configuration

Everything lives in `config.yml`, which is heavily commented. Highlights:

- **`filters.*`** — enable/disable each filter, set its points, custom blocked/whitelisted phrases, and per-filter punishment commands. Toxicity and Threats support **shadow mode**.
- **`filters.spam.cross-player`** — raid detection for the same message arriving from many accounts.
- **`command-filter`** — which commands get filtered, and which are blocked outright for muted players.
- **`chat-control`** — slowmode, first-join delay, chat lock.
- **`extras`** — sign, book and anvil filtering.
- **`punishments.thresholds`** — run commands when a player's total points cross a value (e.g. auto-warn at 15, tempmute at 30).
- **`discord`** — webhook URL, per-filter colors and toggles, staff actions, queue and rate limiting.
- **`notifications`** — staff alert format, sound, console mirroring.
- **`messages`** — every player-facing message (supports `&` color codes and `&#RRGGBB` hex).
- **`update-checker`**, **`metrics`**, **`mutes`**, **`player-data`** — persistence and integrations.

### Custom phrases

In any `blocked-phrases` list, a plain word is matched as a **whole word** — `nega` will not flag `negative`. If the phrase contains regex characters it is treated as a regex instead, so partial matching is still available when you want it. Blank entries are ignored rather than matching every message.

### Placeholders

In **punishment commands** (`%name%` form):

| Placeholder | Meaning |
|---|---|
| `%player%` | Offending player |
| `%uuid%` | Offending player's UUID |
| `%punisher%` | Who triggered it (`CONSOLE` if automatic) |
| `%reason%` | Why the message was flagged |
| `%message%` | The offending message |
| `%duration%` | Punishment duration |
| `%filter%` | Filter name (Spam, Toxicity, …) |
| `%world%` | Player's world |

In the **`messages`** section both `{name}` and `%name%` forms work, e.g. `{reason}` or `%reason%`.

### PlaceholderAPI

`%chatcop_points%`, `%chatcop_warns%`, `%chatcop_muted%`, `%chatcop_mute_time%`, `%chatcop_mute_reason%`, `%chatcop_bypass%`, `%chatcop_total_messages%`, `%chatcop_total_blocked%`, `%chatcop_total_censored%`, `%chatcop_total_mutes%`, `%chatcop_total_warns%`, `%chatcop_block_rate%`, `%chatcop_active_mutes%`, `%chatcop_slowmode%`, `%chatcop_chat_locked%`.

### Colour codes

`&a`, `&l`, `&#RRGGBB` and so on all work. Only an ampersand that is actually followed by a colour code is converted, so `Fish & Chips` stays intact. Write `&&` for a literal ampersand before a code letter (`R&&D`).

---

## Building from source

Requires JDK 17+ and Maven.

```bash
mvn clean package
```

The compiled plugin will be at `target/ChatCop-<version>.jar`. Unit tests run as part of the build.

### Obfuscated release build

```bash
mvn clean package -Pobfuscate
```

Works on any JDK, with no extra flags. Internal packages are renamed; the plugin
entry points (main class, listeners, commands, integrations) and their
annotations are kept, so the jar loads and behaves identically.

Obfuscation is rename-only — shrinking and optimization are deliberately off,
because both need a complete view of the class hierarchy to be safe and
reflectively-loaded code is exactly what they tend to break.

It is still opt-in on purpose: a missed keep rule surfaces as a runtime failure
on a live server rather than a build error, so smoke-test an obfuscated jar
before release.

---

## Support

Found a bug or have a suggestion? Open it on the Spigot resource discussion or the Modrinth page linked above.

---

*Author: Abood_8001*
