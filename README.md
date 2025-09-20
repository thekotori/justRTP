# JustRTP

<p align="center">
<img src="https://i.imgur.com/L7h5u0a.png" alt="JustRTP Logo" width="1000"/>
</p>

<p align="center">
<strong>Ultra-performant random teleport plugin for Paper, Folia, and Velocity/BungeeCord networks with advanced zone support.</strong>
</p>

<p align="center">
<img src="https://img.shields.io/badge/Version-3.2.2-brightgreen?style=for-the-badge" alt="Version" />
<img src="https://img.shields.io/badge/API-1.20+-blue?style=for-the-badge" alt="API Version" />
<img src="https://img.shields.io/badge/Java-17+-orange?style=for-the-badge" alt="Java" />
</p>

## 👋 Welcome to JustRTP!

Welcome to the official and complete documentation for **JustRTP**. This guide will walk you through every feature, from basic installation to the most advanced configurations, ensuring you can harness the full power of the plugin with confidence.

---

## ✨ Features

✅ **High Performance**: Asynchronous location cache and sequential teleport queue for instant, lag-free teleports  
🛡️ **Advanced Safety**: Intelligent scanning logic for NORMAL and NETHER worlds with comprehensive hazard detection  
🌐 **Proxy Support**: Cross-server teleports via Velocity/BungeeCord with MySQL backend  
⚔️ **RTP Zones**: Dynamic arena-style zones with global timers for PvP events  
🎨 **Rich Effects**: Configurable titles, sounds, particles, action bars, boss bars with MiniMessage support  
⚙️ **Deep Configuration**: Per-world settings, cooldowns, permission-based perks  
🚀 **Automation**: First-join and respawn teleports  
🧩 **Integrations**: Vault economy, PlaceholderAPI, WorldGuard region protection  

---

## 🚀 Installation

### Basic Setup
1. Download `JustRTP.jar`
2. Place in server `/plugins` directory  
3. Install **Vault** and **PlaceholderAPI** (recommended)
4. Restart server to generate configs

### Cross-Server Setup
For network-wide `/rtp <server>` functionality:

- Deploy `JustRTP.jar` on all network servers
- Configure shared MySQL database in `mysql.yml` with `enabled: true`
- Set unique `proxy.this_server_name` in `config.yml` matching proxy config
- Ensure all servers can access the same MySQL instance

---

## ⚙️ Configuration

JustRTP uses modular configuration files for clean organization:

**Core Files:**
- `config.yml` - Main settings, world configs, performance options
- `messages.yml` - All text output with MiniMessage support  
- `mysql.yml` - Database connection for cross-server features
- `rtp_zones.yml` - Arena-style zone definitions
- `animations.yml` - Particle effect configurations
- `holograms.yml` - Zone hologram settings
- `commands.yml` - Command alias toggles

**Key Configuration Options:**

| Setting | Description |
|---------|-------------|
| `cooldown/delay` | Default timers for RTP usage |
| `location_cache.enabled` | Pre-calculates safe locations for performance |
| `respect_regions` | WorldGuard region protection |
| `proxy.enabled` | Cross-server functionality |
| `economy.enabled` | Vault integration for paid teleports |
| `first_join_rtp/respawn_rtp` | Automatic teleportation triggers |
| `world_types` | Scanning algorithms per world type |
| `permission_groups` | VIP perks and custom settings |

---

## ⌨️ Commands

### Player Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/rtp` | Teleport in current world | `justrtp.command.rtp` |
| `/rtp <world>` | Teleport to specific world | `justrtp.command.rtp.world` |
| `/rtp <server>` | Cross-server teleport | `justrtp.command.rtp.server` |
| `/rtp confirm` | Confirm paid teleport | `justrtp.command.confirm` |
| `/rtpzone ignore` | Toggle zone participation | `justrtp.command.zone.ignore` |

### Admin Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/rtp <player>` | Teleport another player | `justrtp.command.rtp.others` |
| `/rtp reload` | Reload configurations | `justrtp.command.reload` |
| `/rtp proxystatus` | Check MySQL connection | `justrtp.admin` |
| `/rtpzone setup <id>` | Create new zone | `justrtp.admin.zone` |
| `/rtpzone delete <id>` | Remove zone | `justrtp.admin.zone` |
| `/rtpzone list` | List all zones | `justrtp.admin.zone` |

### Command Aliases
Available aliases: `jrtp`, `wild`, `randomtp` (configurable in `commands.yml`)

---

## ⚔️ RTP Zones

Dynamic arena-style zones for automated group teleports.

**Setup Process:**
1. Use `/rtpzone setup <id>` for interactive configuration
2. Select area with Blaze Rod wand tool
3. Configure target world/server and radius
4. Set teleport interval and hologram settings

**Player Experience:**
- Automatic detection when entering/leaving zones
- Real-time countdown displays via titles and action bars  
- Synchronized group teleports with configurable spread
- Optional hologram displays with custom messages

**Zone Features:**
- Cross-server destination support
- Customizable teleport intervals
- Individual player opt-out capability
- Rich visual and audio feedback

---

## 🔑 Permissions

### Core Permissions
| Permission | Description | Default |
|-----------|-------------|---------|
| `justrtp.admin` | Full plugin access | op |
| `justrtp.admin.zone` | Zone management | op |
| `justrtp.command.rtp` | Basic RTP usage | true |
| `justrtp.command.rtp.others` | Teleport other players | op |
| `justrtp.command.rtp.world` | World-specific RTP | op |
| `justrtp.command.rtp.server` | Cross-server RTP | op |
| `justrtp.command.reload` | Reload configs | op |

### Bypass Permissions
| Permission | Description |
|-----------|-------------|
| `justrtp.cooldown.bypass` | Skip cooldown timers |
| `justrtp.delay.bypass` | Skip warmup delays |
| `justrtp.cost.bypass` | Skip economy costs |

### Group Permissions
Use `justrtp.group.<name>` for custom permission groups defined in config.

---

## � PlaceholderAPI

Available placeholders for integration with other plugins:

| Placeholder | Description |
|------------|-------------|
| `%justrtp_cooldown%` | Remaining cooldown (formatted) |
| `%justrtp_cooldown_raw%` | Remaining cooldown (seconds) |
| `%justrtp_cost%` | RTP cost for current world |
| `%justrtp_delay%` | Teleport warmup time |
| `%justrtp_world_min_radius%` | Minimum teleport distance |
| `%justrtp_world_max_radius%` | Maximum teleport distance |

---

## 🔧 Technical Requirements

- **Server Software**: Paper 1.20+, Folia supported
- **Java**: Version 17 or higher  
- **Dependencies**: Vault (optional), PlaceholderAPI (optional), WorldGuard (optional)
- **Database**: MySQL 8.0+ for cross-server features
