# JustRTP

<p align="center">
<img src="https://i.imgur.com/L7h5u0a.png" alt="JustRTP Logo" width="1000"/>
</p>

<p align="center">
<strong>High-performance random teleport plugin for Paper, Folia, and proxy networks with advanced zone system</strong>
</p>

<p align="center">
<img src="https://img.shields.io/badge/Version-3.2.6-brightgreen?style=for-the-badge" alt="Version" />
<img src="https://img.shields.io/badge/API-1.20+-blue?style=for-the-badge" alt="API Version" />
<img src="https://img.shields.io/badge/Java-17+-orange?style=for-the-badge" alt="Java" />
<img src="https://img.shields.io/badge/Folia-Supported-purple?style=for-the-badge" alt="Folia" />
</p>

<p align="center">
<a href="https://discord.gg/your-invite-code">
<img src="https://img.shields.io/discord/YOUR_SERVER_ID?color=5865F2&label=Discord&logo=discord&logoColor=white&style=for-the-badge" alt="Discord" />
</a>
</p>

---

## Overview

JustRTP is a feature-rich random teleport plugin designed for modern Minecraft servers. Built with performance and reliability in mind, it supports single servers, Velocity/BungeeCord networks, and Folia's multi-threaded regions.

### Key Features

- **High Performance** - Asynchronous location cache and optimized search algorithms
- **Safety First** - Intelligent terrain analysis with dimension-specific scanning
- **Cross-Server Support** - Network-wide teleportation via MySQL backend
- **RTP Zones** - Arena-style zones with countdown holograms and group teleports
- **Rich Visuals** - MiniMessage formatting with titles, particles, and effects
- **Economy Integration** - Vault support with cost per teleport
- **Flexible Configuration** - Per-world settings, permission groups, and custom radii
- **Hook Support** - WorldGuard regions, PlaceholderAPI, and PacketEvents
- **Folia Ready** - Full support for multi-threaded region servers  

---

## Installation

### Quick Start

1. Download `JustRTP.jar` from releases
2. Place in your server's `plugins` folder
3. Restart the server to generate configuration files
4. Configure `config.yml` to your needs
5. Reload with `/rtp reload`

### Dependencies

**Required:**
- Java 17 or higher
- Paper 1.20+ (or Folia, Spigot compatible)

**Optional:**
- **Vault** - Economy and permission group support
- **PlaceholderAPI** - Placeholder expansion for other plugins
- **WorldGuard** - Region protection integration
- **PacketEvents** - High-performance packet-based holograms
- **MySQL** - Cross-server teleportation

### Cross-Server Setup

For network-wide `/rtp <server>:<world>` functionality:

1. Install JustRTP on all network servers
2. Set up shared MySQL database
3. Configure `mysql.yml` with connection details:
   ```yaml
   enabled: true
   host: "your-mysql-host"
   database: "justrtp"
   username: "your-username"
   password: "your-password"
   ```
4. Set unique server name in `config.yml`:
   ```yaml
   proxy:
     this_server_name: "survival"  # Unique per server
   ```
5. Restart all servers

---

## Commands

### Player Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/rtp` | Teleport in current world | `justrtp.command.rtp` | Everyone |
| `/rtp <world>` | Teleport to specific world | `justrtp.command.rtp.world` | OP |
| `/rtp <server>` | Teleport to another server | `justrtp.command.rtp.server` | OP |
| `/rtp <server>:<world>` | Cross-server world teleport | `justrtp.command.rtp.server` | OP |
| `/rtp <player>` | Teleport another player | `justrtp.command.rtp.others` | OP |
| `/rtp <min> <max>` | Custom radius teleport | `justrtp.command.rtp.radius` | OP |
| `/rtp confirm` | Confirm paid teleport | `justrtp.command.confirm` | Everyone |
| `/rtp help` | Display help message | `justrtp.command.rtp` | Everyone |
| `/rtp credits` | Show plugin credits | `justrtp.command.credits` | Everyone |
| `/rtpzone ignore` | Toggle zone participation | `justrtp.command.zone.ignore` | Everyone |

### Admin Commands

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/rtp reload` | Reload all configurations | `justrtp.command.reload` | OP |
| `/rtp proxystatus` | Check MySQL connection | `justrtp.admin` | OP |
| `/rtpzone setup <id>` | Create new RTP zone | `justrtp.admin.zone` | OP |
| `/rtpzone delete <id>` | Remove existing zone | `justrtp.admin.zone` | OP |
| `/rtpzone list` | List all configured zones | `justrtp.admin.zone` | OP |
| `/rtpzone cancel` | Cancel ongoing setup | `justrtp.admin.zone` | OP |
| `/rtpzone sethologram <id>` | Add hologram to zone | `justrtp.admin.zone` | OP |
| `/rtpzone delhologram <id>` | Remove zone hologram | `justrtp.admin.zone` | OP |
| `/rtpzone sync` | Show zone sync status | `justrtp.admin.zone` | OP |
| `/rtpzone push` | Push zones to database | `justrtp.admin.zone` | OP |
| `/rtpzone pull` | Pull zones from database | `justrtp.admin.zone` | OP |
| `/rtpzone status` | Display sync information | `justrtp.admin.zone` | OP |

### Command Aliases

Available aliases for `/rtp`: `jrtp`, `wild`, `randomtp`  
Available aliases for `/rtpzone`: `rtpzones`

Aliases can be disabled in `commands.yml`

---

## Configuration

JustRTP uses modular configuration files for clean organization and easy management:

### Core Configuration Files

| File | Purpose |
|------|---------|
| `config.yml` | Main settings, world configs, performance options |
| `messages.yml` | All text output with MiniMessage formatting |
| `mysql.yml` | Database connection for cross-server features |
| `redis.yml` | Redis configuration for advanced caching |
| `rtp_zones.yml` | Arena-style zone definitions |
| `animations.yml` | Particle effect configurations |
| `holograms.yml` | Zone hologram display settings |
| `commands.yml` | Command alias toggles |
| `cache.yml` | Location cache settings |
| `display_entities.yml` | Display entity configurations |

### Key Configuration Options

**Basic Settings (`config.yml`):**
```yaml
cooldown: 300              # Default cooldown in seconds
delay: 3                   # Warmup time before teleport
max_attempts: 30           # Location search attempts
min_radius: 100            # Minimum distance from spawn
max_radius: 10000          # Maximum distance from spawn

economy:
  enabled: true            # Require payment for RTP
  cost: 100.0              # Default cost per teleport
```

**World-Specific Settings:**
```yaml
worlds:
  world:
    enabled: true
    type: OVERWORLD         # or NETHER, THE_END, CUSTOM
    min_radius: 500
    max_radius: 15000
    cooldown: 300
    cost: 50.0
    
  world_nether:
    enabled: true
    type: NETHER
    min_y: 32               # Safe nether Y-level
    max_y: 120
```

**Automation Features:**
```yaml
first_join_rtp:
  enabled: true             # Auto-RTP on first join
  world: "world"            # Target world
  delay: 5                  # Delay after join

respawn_rtp:
  enabled: false            # Auto-RTP on respawn
  only_first_death: true    # Only first death per session
```

**Safety & Performance:**
```yaml
respect_regions: true       # WorldGuard integration
avoid_lava: true            # Skip lava locations
avoid_water: true           # Skip water locations
unsafe_blocks: true         # Check for dangerous blocks

location_cache:
  enabled: true             # Pre-calculate safe locations
  size: 100                 # Locations per world
  cleanup_interval: 3600    # Cleanup every hour
```

**Biome Control:**
```yaml
biome_blacklist:
  - DEEP_OCEAN
  - FROZEN_OCEAN
  - DEEP_COLD_OCEAN
```

---

## RTP Zones

Dynamic arena-style zones for automated group teleports with synchronized countdowns.

### Setup Process

1. **Create Zone:**
   ```
   /rtpzone setup <zone_id>
   ```

2. **Select Zone Area:**
   - Receive Blaze Rod selection wand
   - **Left-click** block to set first position
   - **Right-click** block to set second position

3. **Configure Zone (Interactive Prompts):**
   - Teleport interval (seconds between teleports)
   - Target world for teleportation
   - Minimum/maximum radius from target world spawn
   - Optional: Cross-server destination
   - Optional: Custom spread radius for grouped players

4. **Add Hologram (Optional):**
   ```
   /rtpzone sethologram <zone_id>
   ```
   Stand at desired location and run command

5. **Verify Configuration:**
   ```
   /rtpzone list
   /rtpzone status
   ```

### Zone Features

**Player Experience:**
- Automatic detection when entering/leaving zones
- Real-time countdown displays:
  - **Title messages**: Large countdown display (5s, 4s, 3s...)
  - **Action bar**: Persistent countdown timer
  - **Holograms**: Optional floating countdown display (with PacketEvents for best performance)
- **NEW 3.2.6**: Smart multi-player teleportation with automatic spreading
  - Each player gets a unique, safe location
  - Configurable spread distance prevents clustering
  - Optimized for Folia's multi-threaded regions
  - All players teleport simultaneously without lag
- Sound effects at each countdown tick
- Particle effects on teleportation

**Zone Management:**
- Cross-server destination support via MySQL sync
- Customizable teleport intervals per zone
- Individual player opt-out: `/rtpzone ignore`
- **NEW 3.2.6**: Advanced multi-player teleportation system
  - Each player gets a unique location (no more same-spot spawns!)
  - Configurable min/max spread distance between players
  - Per-zone spread settings override global defaults
  - Full dimension safety (nether Y < 127, end islands, etc.)
- Automatic zone sync across network servers

**Hologram System:**
- Automatic PacketEvents detection for optimal performance
- Fallback to entity-based holograms if PacketEvents not installed
- Dynamic countdown updates (5s → 4s → 3s → 2s → 1s → NOW!)
- Customizable hologram messages via `holograms.yml`

### Zone Configuration Example

**`rtp_zones.yml`:**
```yaml
zones:
  pvp-arena:
    world: "world"
    pos1:
      x: -50.0
      y: 64.0
      z: -50.0
    pos2:
      x: 50.0
      y: 80.0
      z: 50.0
    
    interval: 60              # Teleport every 60 seconds
    target: "arena_world"     # Destination world or server:world
    
    min-radius: 1000          # Min distance from world spawn
    max-radius: 10000         # Max distance from world spawn
    
    # NEW 3.2.6: Player spread configuration
    min-spread-distance: 10   # Minimum blocks between players
    max-spread-distance: 50   # Maximum blocks between players
    
    hologram:
      location:
        x: 0.5
        y: 75.0
        z: 0.5
      view-distance: 64
```

**Global Spread Settings in `config.yml`:**
```yaml
zone_teleport_settings:
  # These are defaults if zone doesn't specify its own values
  min_spread_distance: 5    # Min blocks apart (prevents clustering)
  max_spread_distance: 15   # Max blocks apart (keeps group together)
```

**How Multi-Player Spread Works:**
1. Zone countdown reaches 0
2. All players in zone are collected
3. Each player gets a unique safe location using RTPService
4. Locations are checked to ensure min_spread_distance apart
5. All players teleport simultaneously (Folia-safe)
6. Result: No two players spawn at exactly the same spot!

**`holograms.yml`:**
```yaml
countdown_format: "<gold>⏱ Teleporting in <time>"
teleport_now_format: "<green>✔ Teleporting NOW!"
```

### MySQL Zone Sync

For cross-server zones, zones automatically sync via MySQL:

**Push zones to database:**
```
/rtpzone push
```

**Pull zones from database:**
```
/rtpzone pull
```

**Check sync status:**
```
/rtpzone sync
/rtpzone status
```

---

## Permissions

### Core Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `justrtp.command.rtp` | Use `/rtp` in current world | Everyone |
| `justrtp.command.rtp.world` | Teleport to specific worlds | OP |
| `justrtp.command.rtp.server` | Cross-server teleportation | OP |
| `justrtp.command.rtp.others` | Teleport other players | OP |
| `justrtp.command.rtp.radius` | Use custom radius values | OP |
| `justrtp.command.confirm` | Confirm paid teleports | Everyone |
| `justrtp.command.reload` | Reload plugin configurations | OP |
| `justrtp.command.credits` | View plugin credits | Everyone |
| `justrtp.command.zone.ignore` | Toggle zone participation | Everyone |
| `justrtp.admin` | Full admin access (proxy status, etc.) | OP |
| `justrtp.admin.zone` | Manage RTP zones | OP |

### Bypass Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `justrtp.bypass.cooldown` | Skip cooldown timers | OP |
| `justrtp.bypass.delay` | Skip teleport delays | OP |
| `justrtp.bypass.cost` | Skip economy costs | OP |

### Wildcard Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `justrtp.*` | All plugin permissions | OP |
| `justrtp.command.*` | All command permissions | OP |
| `justrtp.bypass.*` | All bypass permissions | OP |
| `justrtp.group.*` | All permission groups | OP |

### Permission Groups

Configure custom settings per permission group in `config.yml`:

```yaml
permission_groups:
  vip:
    cooldown: 60          # 1 minute cooldown
    delay: 1              # 1 second delay
    cost: 0               # Free teleports
    min_radius: 100       # Custom radius
    max_radius: 10000
    permission: "justrtp.group.vip"
    
  mvp:
    cooldown: 30          # 30 second cooldown
    delay: 0              # Instant teleport
    cost: 0
    max_radius: 20000     # Larger radius
    permission: "justrtp.group.mvp"
    
  premium:
    cooldown: 0           # No cooldown
    delay: 0
    cost: 0
    auto_rtp_respawn: true  # Auto-RTP on death
    permission: "justrtp.group.premium"
```

Players receive the settings from the highest priority group they have access to. Priority is determined by the order defined in `config.yml`.

---

## PlaceholderAPI

**Requirement**: Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) to use these placeholders.

All placeholders support per-player context and world-specific settings:

### Cooldown Placeholders

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_cooldown%` | Formatted cooldown with units | `5 minutes, 30 seconds` |
| `%justrtp_cooldown_raw%` | Raw seconds remaining | `330` |
| `%justrtp_cooldown_total%` | Total cooldown duration | `600` |
| `%justrtp_cooldown_formatted%` | Compact format | `5m 30s` |
| `%justrtp_has_cooldown%` | Boolean cooldown check | `true` or `false` |

### Cost & Delay Placeholders

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_cost%` | Economy cost for RTP | `500.0` |
| `%justrtp_delay%` | Teleport warmup seconds | `3` |
| `%justrtp_is_delayed%` | Active delay check | `true` or `false` |

### World & Configuration Placeholders

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_world_name%` | Current world name | `world_nether` |
| `%justrtp_world_min_radius%` | Minimum teleport radius | `100` |
| `%justrtp_world_max_radius%` | Maximum teleport radius | `10000` |
| `%justrtp_permission_group%` | Active permission group | `vip` |

### Status Placeholder

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_can_rtp%` | Composite check (cooldown + cost + delay) | `true` or `false` |

### RTP Zone Placeholders

**NEW in 3.2.6**: Zone system placeholders for FancyHolograms, scoreboards, and other plugins.

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_zone_time_<zoneid>%` | Countdown timer for specific zone | `15` (seconds) |
| `%justrtp_in_zone%` | Whether player is in any zone | `true` or `false` |
| `%justrtp_current_zone%` | Zone ID player is currently in | `pvp-arena` or `None` |
| `%justrtp_zone_name%` | Alias for current_zone | `pvp-arena` or `None` |

**Zone Time Examples:**
- `%justrtp_zone_time_pvp-arena%` - Shows countdown for "pvp-arena" zone
- `%justrtp_zone_time_battle-royale%` - Shows countdown for "battle-royale" zone

**FancyHolograms Integration:**
```yaml
# Example hologram showing zone countdown
lines:
  - "<gradient:red:gold>PvP Arena</gradient>"
  - "<yellow>Teleporting in %justrtp_zone_time_pvp-arena%s"
  - "<gray>%justrtp_in_zone% players waiting"
```

**Scoreboard Integration:**
```yaml
- "Zone: %justrtp_current_zone%"
- "Teleport: %justrtp_zone_time_pvp-arena%s"
- "In Zone: %justrtp_in_zone%"
```

### Usage Examples

**Scoreboard Integration (with DeluxeMenus, FeatherBoard, etc.):**
```yaml
- "Cooldown: %justrtp_cooldown%"
- "Cost: $%justrtp_cost%"
- "World: %justrtp_world_name%"
- "Status: %justrtp_can_rtp%"
```

**Chat Integration:**
```yaml
message: "<green>You can RTP in %justrtp_cooldown%"
cost_message: "<yellow>Cost: $%justrtp_cost%"
```

---

## Message Customization

All messages support **MiniMessage** formatting for rich text, colors, gradients, and hover/click events.

### MiniMessage Format Examples

**`messages.yml`:**
```yaml
# Simple colors
teleport_success: "<green>Successfully teleported!"
teleport_failed: "<red>Could not find safe location."

# Gradients
cooldown_active: "<gradient:red:yellow>You must wait <cooldown> before using RTP again.</gradient>"

# Hover & Click Events
help_command: "<hover:show_text:'Click to teleport'><click:run_command:/rtp><gold>[Click to RTP]</click></hover>"

# Multiple formats
prefix: "<dark_gray>[<gradient:aqua:blue>RTP</gradient>]</dark_gray>"
zone_countdown: "<prefix> <yellow>Teleporting in <time>..."
zone_teleport: "<prefix> <green>✔ Teleporting now!"

# Action bar messages
action_bar_cooldown: "<red>⏱ Cooldown: <cooldown>"
action_bar_delay: "<yellow>⏳ Preparing teleport..."
```

### Available Message Placeholders

Messages support these internal placeholders:

- `<player>` - Player name
- `<world>` - World name
- `<server>` - Server name
- `<cooldown>` - Formatted cooldown time
- `<cost>` - Teleport cost
- `<delay>` - Warmup delay
- `<time>` - Zone countdown time
- `<min_radius>` / `<max_radius>` - Radius values

### Customizable Message Categories

- Teleport feedback (success, failure, cancelled)
- Cooldown and delay notifications
- Economy messages (cost, insufficient funds)
- Permission denied messages
- Zone enter/leave/countdown/teleport messages
- Admin command responses
- Error messages

For complete MiniMessage syntax, see: https://docs.advntr.dev/minimessage/format.html

---

## Performance Optimization

### Location Caching

Pre-calculate safe locations for instant teleports:

**`cache.yml`:**
```yaml
enabled: true
size: 100                  # Locations per world
min_size: 20               # Minimum before refill
refill_interval: 600       # Refill every 10 minutes
cleanup_interval: 3600     # Cleanup every hour
async: true                # Async processing
```

Benefits:
- Instant teleports (no location search delay)
- Reduced server load during peak usage
- Better player experience

### Redis Caching

For large networks, enable Redis for distributed caching:

**`redis.yml`:**
```yaml
enabled: true
host: "localhost"
port: 6379
password: ""
database: 0
cache_ttl: 3600           # Cache lifetime in seconds
```

### Folia Support

JustRTP is fully compatible with Folia's regionized threading:
- All operations are region-aware
- No global state modifications
- Safe concurrent zone teleportation
- Optimal performance on Folia servers

### Performance Tips

- Increase location caching for high-traffic servers
- Use Redis for networks with 3+ servers
- Configure reasonable `max_attempts` (20-30)
- Limit biome blacklist to essential biomes
- Use PacketEvents for hologram performance

---

## Troubleshooting

### Common Issues

**"Could not find safe location"**
- Increase `max_attempts` in config.yml
- Expand `max_radius` for the world
- Review `biome_blacklist` and `unsafe_blocks` settings
- Check WorldGuard region restrictions

**Cooldowns not working**
- Verify player has correct permissions
- Check for `justrtp.bypass.cooldown` permission
- Review permission group priorities in config

**Cross-server RTP not working**
- Confirm MySQL connection: `/rtp proxystatus`
- Verify `this_server_name` is unique per server
- Check MySQL credentials in `mysql.yml`
- Ensure plugin is installed on all network servers
- Verify target server is online

**Holograms not displaying countdown**
- Install PacketEvents for best performance and proper countdown display
- Check `holograms.yml` configuration
- Verify hologram location is within render distance
- Confirm zone has hologram enabled in `rtp_zones.yml`

**Zone not teleporting multiple players**
- Update to version 3.2.6+ (fixed race condition issues)
- Check zone is properly configured: `/rtpzone list`
- Verify players don't have zone ignored: `/rtpzone ignore`

**Economy costs not charging**
- Install Vault plugin
- Verify economy plugin is loaded (EssentialsX, CMI, etc.)
- Check `economy.enabled: true` in config.yml
- Confirm player has sufficient funds

### Debug Mode

Enable detailed logging in `config.yml`:
```yaml
debug: true
verbose_logging: true
```

This will output:
- Location search attempts and results
- Zone teleportation events
- MySQL/Redis connection details
- Permission checks
- Cache operations

### Getting Help

If you encounter issues not covered here:

1. Check console for error messages
2. Enable debug mode and review logs
3. Verify all dependencies are up-to-date
4. Review configuration files for syntax errors

---

## Technical Specifications

| Component | Details |
|-----------|---------|
| **Plugin Version** | 3.2.6 |
| **Minecraft Version** | 1.20+ |
| **Server Software** | Paper, Folia |
| **Java Version** | 17+ required |
| **API Version** | 1.20 |
| **Database Support** | MySQL 8.0+, Redis (optional) |
| **Dependencies** | Vault, PlaceholderAPI, WorldGuard, PacketEvents (all optional) |
| **License** | MIT |
| **Author** | kotori |

### Feature Matrix

| Feature | Status | Requirements |
|---------|--------|--------------|
| Basic RTP | ✅ Core | None |
| Cross-Server RTP | ✅ Available | MySQL |
| RTP Zones | ✅ Available | None |
| Economy Integration | ✅ Available | Vault |
| PlaceholderAPI | ✅ Available | PlaceholderAPI |
| WorldGuard Regions | ✅ Available | WorldGuard |
| Redis Caching | ✅ Available | Redis |
| Folia Support | ✅ Full Support | Folia |
| PacketEvents Holograms | ✅ Available | PacketEvents |
| Location Caching | ✅ Available | None |
| Permission Groups | ✅ Available | None |
| Auto-RTP (Join/Respawn) | ✅ Available | None |
| Particle Effects | ✅ Available | None |
| Sound Effects | ✅ Available | None |
| MiniMessage Formatting | ✅ Available | None |

---

## License & Credits

**JustRTP** is developed and maintained with care by **kotori**.

This plugin is open-source software, licensed under the MIT License.

---

**Need help?** Review the troubleshooting section above or check your console logs with debug mode enabled.

**Enjoying JustRTP?** Consider leaving feedback or supporting continued development!
