# 🚀 JustRTP

An ultra-performant and safe Random Teleport plugin, designed for modern Minecraft server networks with full Velocity, Paper, and Folia support.

<p align="center">
  <img src="https://img.shields.io/badge/Author-kotori-lightgrey?style=for-the-badge" alt="Author" />
  <img src="https://img.shields.io/badge/API-1.20+-brightgreen?style=for-the-badge" alt="API Version" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License" />
</p>

## ✨ Features

- 🌐 **Robust Cross-Server RTP** — Seamlessly teleport players across servers in your Velocity network using a stable MySQL backend.
- ⚡ **Full Folia & Paper Support** — Optimized from the ground up to use modern, region-aware schedulers on Folia for maximum performance.
- 🛡️ **Advanced Safety Checks** — Multi-layered checks ensure players never land in lava, water, claimed land, or other unsafe locations.
- 🌍 **Per-World Customization** — Define unique teleport radii, center points, cooldowns, and delays for every single world.
- ✨ **Nether-Specific Logic** — Intelligent algorithm for the Nether that finds safe, enclosed spaces, avoiding lava oceans and the roof.
- 🏆 **Permission-Based Overrides** — Create custom permission groups (e.g., for VIPs) to grant reduced cooldowns, shorter delays, or larger teleport radii.
- 🚀 **High-Performance Teleport Queue** — Prevents server lag by processing multiple RTP requests sequentially, perfect for servers with high player counts.
- 🎨 **Stunning Visual & Audio Effects** — Customizable titles, boss bars, action bars, sounds, and particles to make every teleport feel polished.
- 🧩 **Full PlaceholderAPI Support** — Display cooldowns, delays, and world-specific settings anywhere.
- ⚙️ **Plugin Hooks** — Integrated support for WorldGuard to respect region claims automatically.
- 💬 **MiniMessage Formatting** — Style all plugin messages with modern gradients, click events, and hover text.
- 🔧 **Simplified & Powerful Config** — Easy-to-understand configuration files for main settings and database connections.

---

## 📦 Installation

1. Download the latest `JustRTP.jar`.
2. Stop your Minecraft server(s).
3. Place the same `.jar` file in the `plugins/` folder of all your Spigot/Paper servers (e.g., lobby, survival).
4. *(For Cross-Server RTP)* Create a dedicated MySQL database accessible by all your servers.
5. *(Recommended)* Install PlaceholderAPI.
6. Start your servers once to generate the `config.yml` and `mysql.yml` files.
7. Configure `config.yml` on each server, making sure `this_server_name` is unique for each.
8. Configure `mysql.yml` on all servers to connect to your database.
9. Reload the configuration on all servers with `/rtp reload`.

---

## ⚙️ Configuration

### `config.yml`
```yaml
# ----------------------------------------------------------------
# JustRTP Main Configuration
# ----------------------------------------------------------------

settings:
  cooldown: 30
  delay: 3
  attempts: 25
  respect_regions: true
  debug: false
  credits_command_requires_permission: true

# --- Proxy & Cross-Server Settings ---
# Cross-server functionality is now managed via the mysql.yml file.
proxy:
  enabled: true
  this_server_name: 'lobby'
  cross_server_rtp_no_delay: false
  servers:
    - 'lobby2'
    - 'survival'

custom_worlds:
  world:
    max_radius: 5000
    min_radius: 100
    center_x: 0
    center_z: 0
  world_nether:
    max_radius: 2500
    min_radius: 50
```

### `mysql.yml` *(Required for Proxy features)*
```yaml
# MySQL Database Configuration for JustRTP
# This file MUST be configured on all Spigot/Paper servers where you want cross-server RTP to work.

enabled: true
host: "127.0.0.1"
port: 3306
database: "justrtp_db"
username: "justrtp_user"
password: "YourSecurePassword"

pool-settings:
  maximum-pool-size: 10
```

---

## ⌨️ Commands & Permissions

### 🔧 Commands
| Command | Aliases | Description |
|--------|---------|-------------|
| `/rtp` | `jrtp` | Teleports you to a random location. |
| `/rtp <player>` | - | Teleports another player. |
| `/rtp <world>` | - | Teleports you to a random location in a specific world. |
| `/rtp <server>` | - | Teleports you to a random location on another server. |
| `/rtp <player> <world/server> [minRadius] [maxRadius]` | - | The full command with all possible arguments. |
| `/rtp reload` | - | Reloads all configuration files (admin only). |
| `/rtp credits` | - | Shows plugin information and author credits. |
| `/rtp proxystatus` | - | Checks the status of the MySQL proxy connection. |

### 🔐 Permissions
| Permission | Description |
|------------|-------------|
| `justrtp.command.rtp` | Allows using the basic /rtp command. *(Default)* |
| `justrtp.command.rtp.others` | Allows teleporting other players. *(OP)* |
| `justrtp.command.rtp.world` | Allows specifying a world to teleport in. *(OP)* |
| `justrtp.command.rtp.server` | Allows specifying a server to teleport to. *(OP)* |
| `justrtp.command.rtp.radius` | Allows specifying a custom min/max radius. *(OP)* |
| `justrtp.command.reload` | Allows reloading the plugin. *(OP)* |
| `justrtp.command.credits` | Allows viewing the credits screen. |
| `justrtp.cooldown.bypass` | Bypasses the teleport cooldown. *(OP)* |
| `justrtp.delay.bypass` | Bypasses the teleport delay/warmup. *(OP)* |
| `justrtp.group.<group_name>` | Grants a player the benefits of a permission group. |
| `justrtp.admin` | Grants access to all admin commands like proxystatus. |

**Recommended Setup:**
- Players: `justrtp.command.rtp`
- Admins: `justrtp.admin` and all `justrtp.command.*` permissions

---

## 🧩 PlaceholderAPI

JustRTP supports PlaceholderAPI out of the box.

| Placeholder | Description |
|-------------|-------------|
| `%justrtp_cooldown_seconds%` | Remaining cooldown time in seconds for the player. |
| `%justrtp_cooldown_total%` | Total cooldown time for the player's group/world. |
| `%justrtp_delay_seconds%` | Teleport delay/warmup time for the player's group/world. |
| `%justrtp_world_min_radius%` | The minimum teleport radius in the player's current world. |
| `%justrtp_world_max_radius%` | The maximum teleport radius in the player's current world. |

<p align="center">Made with ❤️ by <strong>kotori</strong></p>
