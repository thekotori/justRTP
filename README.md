# JustRTP

<p align="center">
<img src="https://i.imgur.com/L7h5u0a.png" alt="JustRTP Logo" width="1000"/>
</p>

<p align="center">
<strong>An ultra-performant, modern, and highly configurable Random Teleport plugin for Paper, Folia, and Velocity/BungeeCord networks.</strong>
</p>

<p align="center">
<img src="https://img.shields.io/badge/Author-kotori-blueviolet?style=for-the-badge" alt="Author" />
<img src="https://img.shields.io/badge/API-1.20+-brightgreen?style=for-the-badge" alt="API Version" />
<img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License" />
</p>

## 👋 Welcome to JustRTP!

Welcome to the official and complete documentation for **JustRTP**. This guide will walk you through every feature, from basic installation to the most advanced configurations, ensuring you can harness the full power of the plugin with confidence.

---

## ✨ Core Features

✅ **High Performance:** Utilizes an asynchronous location cache and a sequential teleport queue for instant, lag-free teleports.  
🛡️ **Advanced Safety Checks:** Guarantees players land safely, avoiding lava, water, cacti, and other hazards. Features intelligent NORMAL and NETHER world scanning logic.  
🌐 **Full Proxy Support:** Seamlessly teleport players across your Velocity/BungeeCord network with a stable MySQL backend.  
⚔️ **Arena-Style RTP Zones:** Create dynamic zones that teleport all players inside on a global timer to a random location—perfect for PvP events.  
🎨 **Stunning & Configurable Effects:** Customize every aspect of the player experience with titles, sounds, particles, action bars, boss bars, and full MiniMessage formatting.  
⚙️ **Deep Customization:** Control everything from per-world radii and cooldowns to permission-based perks for VIPs.  
🚀 **Automatic Actions:** Configure automatic random teleports for a player's first join or upon respawning after death.  
🧩 **Plugin Integrations:** Out-of-the-box support for Vault (economy), PlaceholderAPI, and WorldGuard (region protection).  

---

## 🚀 Getting Started

### 1. Installation
1. Download the latest `JustRTP.jar`.
2. Place the `JustRTP.jar` file into your server's `/plugins` directory.
3. *(Optional but Recommended)* Install **Vault** and **PlaceholderAPI**.
4. Restart your server to generate all necessary configuration files.

### 2. Cross-Server Setup (Proxy)
To enable `/rtp <server>`, you must perform these steps on **ALL** your Spigot/Paper servers:

- Place the same `JustRTP.jar` in every server's `/plugins` folder.
- Create a single **MySQL** database that all your servers can connect to.
- Configure `mysql.yml` on every server with the same database credentials and set `enabled: true`.
- Configure `config.yml` on every server, ensuring that `proxy.this_server_name` is unique and matches the name in your **Velocity/BungeeCord** config exactly.

---

## ⚙️ Configuration Files In-Depth

JustRTP uses a modular file system to keep configuration clean and simple.

<details>
<summary><strong>► Click to see a detailed breakdown of all 8 configuration files</strong></summary>

### `config.yml` (Main Configuration)

| Section | Key | Description |
|--------|-----|-------------|
| `settings` | `cooldown / delay` | Default cooldown and delay for `/rtp`. |
| | `attempts` | Max attempts to find a safe spot. |
| `respect_regions` |  | If true, respects WorldGuard claims. |
| `rtp_settings` | `worlds & biomes` | Blacklist/whitelist worlds or biomes. |
| `location_cache` | `enabled` | Pre-calculates safe locations. |
| | `generate_chunks_for_cache` | Recommended on new maps. |
| `animations` | `delay_animation / success_animation` | Animation from `animations.yml`. |
| `delay_settings` | `cancel_on_move / combat` | Cancel teleport on move or combat. |
| `first_join_rtp` | `enabled` | Auto-RTP on first join. |
| `respawn_rtp` | `enabled` | Auto-RTP on respawn. |
| `economy` | `enabled` | Use Vault to charge for `/rtp`. |
| `performance` | `use_teleport_queue` | Queue RTPs to reduce lag. |
| `proxy` | `enabled / this_server_name` | Enable cross-server. Server name must match proxy config. |
| `aliases` |  | User-friendly names for servers. |
| `effects` |  | Titles, particles, sounds, etc. |
| `disabled_worlds` |  | Disable `/rtp` in listed worlds. |
| `blacklist_blocks` |  | Unsafe block types. |
| `world_types` |  | Scanning logic per world. |
| `custom_worlds` |  | Per-world overrides. |
| `permission_groups` |  | VIP groups with custom perks. |

### `rtp_zones.yml` (Arena-Style Zones)
- Stores teleport zones.
- Recommended: Use `/rtpzone setup` to create them.

### `animations.yml` (Particle Effects)
- Define particle animations for delay and success phases.

### `holograms.yml` (Zone Holograms)
- Configure global hologram appearance.
- Supports MiniMessage and placeholders.

### `messages.yml` (All Plugin Text)
- Full message customization with MiniMessage.

### `mysql.yml` (Database Connection)
- Required for proxy support.
- Must match on all servers.

### `commands.yml` (Command Aliases)
- Enable/disable aliases like `/wild`, `/randomtp`.

### `plugin.yml` (Plugin Metadata)
- Not typically edited manually.

</details>

---

## ⌨️ Commands & Permissions

### Player Commands

| Command | Aliases | Description | Permission | Default |
|--------|---------|-------------|------------|---------|
| `/rtp` | jrtp, wild, randomtp | RTP in current world | `justrtp.command.rtp` | ✅ Yes |
| `/rtp <world>` |  | RTP to specific world | `justrtp.command.rtp.world` | 👮 Op |
| `/rtp <server>` |  | RTP to another server | `justrtp.command.rtp.server` | 👮 Op |
| `/rtp confirm` |  | Confirms paid teleport | `justrtp.command.confirm` | ✅ Yes |
| `/rtpzone ignore` |  | Toggle zone effects | `justrtp.command.zone.ignore` | ✅ Yes |
| `/rtp credits` |  | Plugin info | `justrtp.command.credits` | ✅ Yes |

### Admin Commands

| Command | Description | Permission |
|--------|-------------|------------|
| `/rtp <player>` | Teleport another player | `justrtp.command.rtp.others` |
| `/rtp reload` | Reload configs | `justrtp.command.reload` |
| `/rtpzone setup <id>` | Setup new zone | `justrtp.admin.zone` |
| `/rtpzone delete <id>` | Delete zone | `justrtp.admin.zone` |
| `/rtpzone list` | List all zones | `justrtp.admin.zone` |
| `/rtpzone sethologram <id> [y]` | Create hologram | `justrtp.admin.zone` |
| `/rtpzone delhologram <id>` | Delete hologram | `justrtp.admin.zone` |
| `/rtp proxystatus` | Check MySQL status | `justrtp.admin` |

---

## ⚔️ RTP Zones

**RTP Zones** are powerful tools for creating automated group teleports.

### How It Works

1. **Define Area**: Use `/rtpzone setup` to define a 3D zone.
2. **Set Timer**: Set an interval (e.g., 300s).
3. **Set Destination**: World or cross-server.
4. **Teleport**: All players inside are teleported to one safe location with spread.

### Player Experience

- **Enter**: Receive title and sound.
- **Wait**: Countdown title/action bar.
- **Leave**: Effects cleared instantly.
- **Teleport**: Dramatic visuals and sound.

### Interactive Setup Guide: `/rtpzone setup`

- **Step 1:** Select zone with wand (Blaze Rod).
- **Step 2:** Target world/server.
- **Step 3:** Min/Max radius.
- **Step 4:** Teleport interval.
- **Step 5:** Hologram view distance.

---

## 🔑 All Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `justrtp.admin` | Grants all plugin access | op |
| `justrtp.admin.zone` | Access to all `/rtpzone` commands | op |
| `justrtp.command.rtp` | Use `/rtp` | true |
| `justrtp.command.rtp.others` | Teleport other players | op |
| `justrtp.command.rtp.world` | RTP to specific world | op |
| `justrtp.command.rtp.server` | RTP to another server | op |
| `justrtp.command.rtp.radius` | Set custom radius | op |
| `justrtp.command.reload` | Reload config | op |
| `justrtp.command.credits` | View plugin info | true |
| `justrtp.command.confirm` | Confirm paid teleport | true |
| `justrtp.command.zone.ignore` | Toggle zone ignore | true |
| `justrtp.cooldown.bypass` | Bypass cooldown | op |
| `justrtp.delay.bypass` | Bypass teleport delay | op |
| `justrtp.cost.bypass` | Bypass cost | op |
| `justrtp.group.<group_name>` | Use custom group settings | op |

### Recommended Setup

- **Default Players:** `justrtp.command.rtp`, `justrtp.command.confirm`, `justrtp.command.credits`, `justrtp.command.zone.ignore`
- **Moderators:** Above + `justrtp.command.rtp.others`, `justrtp.cooldown.bypass`
- **Admins:** `justrtp.admin`

---

## 📌 All PlaceholderAPI Placeholders

| Placeholder | Description |
|------------|-------------|
| `%justrtp_cooldown%` | Remaining cooldown (formatted) |
| `%justrtp_cooldown_raw%` | Remaining cooldown (seconds) |
| `%justrtp_cost%` | RTP cost in current world |
| `%justrtp_delay%` | RTP delay time |
| `%justrtp_cooldown_total%` | Total cooldown time |
| `%justrtp_world_min_radius%` | Minimum teleport radius |
| `%justrtp_world_max_radius%` | Maximum teleport radius |

---

<p align="center">
<em>Made with ❤️ by <strong>kotori</strong></em>
</p>
