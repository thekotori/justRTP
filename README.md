# JustRTP

<p align="center">
<img src="https://i.imgur.com/dUlEG4p.png" alt="JustRTP Logo" width="1000"/>
</p>

<p align="center">
<strong>High-performance random teleport plugin for Paper, Folia, and proxy networks with advanced zone system</strong>
</p>

<p align="center">
<img src="https://img.shields.io/badge/Version-3.3.1-brightgreen?style=for-the-badge" alt="Version" />
<img src="https://img.shields.io/badge/API-1.21+-blue?style=for-the-badge" alt="API Version" />
<img src="https://img.shields.io/badge/Java-21+-orange?style=for-the-badge" alt="Java" />
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

- **Human-Readable Time Formatting** - Display cooldowns as "5m 38s" instead of "338 seconds"
- **OP/Permission Cooldown Bypass** - OP players and bypass permission holders skip cooldowns
- **Empty Message Suppression** - Hide any message by setting it to "" in messages.yml
- **Radius-Based Pricing** - Charge different prices based on teleport radius chosen
- **Per-World Cooldowns** - Independent cooldowns for each world - teleport freely between dimensions!
- **High Performance** - Asynchronous location cache and optimized search algorithms
- **Safety First** - Intelligent terrain analysis with dimension-specific scanning
- **Cross-Server Support** - Network-wide teleportation via MySQL backend
- **RTP Zones** - Arena-style zones with countdown holograms and group teleports
- **3-Tier Hologram System** - FancyHolograms, PacketEvents, or Display Entities with auto-selection
- **Persistent Holograms** - FancyHolograms integration with player-editable, restart-proof holograms
- **Rich Visuals** - MiniMessage formatting with titles, particles, and effects
- **Economy Integration** - Vault support with cost per teleport and radius-based pricing
- **Flexible Configuration** - Per-world settings, permission groups, and custom radii
- **Hook Support** - WorldGuard regions, PlaceholderAPI, FancyHolograms, and PacketEvents
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
- Java 21 or higher
- Paper 1.21+ (or Folia, Spigot 1.21+ compatible)

**Optional:**
- **Vault** - Economy and permission group support
- **PlaceholderAPI** - Placeholder expansion for other plugins
- **WorldGuard** - Region protection integration
- **FancyHolograms** - Beautiful, persistent holograms for zones (1.21+ Paper/Folia)
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
    cooldown: 300           #  Per-world cooldown!
    cost: 50.0
    
  world_nether:
    enabled: true
    type: NETHER
    min_y: 32               # Safe nether Y-level
    max_y: 120
    cooldown: 180           # Nether has shorter cooldown!
```

**Radius-Based Pricing System**

Charge different prices based on the teleport radius chosen by the player:

```yaml
custom_worlds:
  world:
    cost: 100.0             # Base cost (always charged)
    
    # Radius-based pricing (optional)
    radius_pricing:
      enabled: true
      tiers:
        tier1:
          max_radius: 5000   # Small radius
          cost: 0.0          # No extra charge
        tier2:
          max_radius: 15000  # Medium radius
          cost: 250.0        # +$250 extra
        tier3:
          max_radius: 25000  # Large radius
          cost: 500.0        # +$500 extra
```

**Example costs:**
- `/rtp` (default) → $100 (base only)
- `/rtp 5000` → $100 (base + tier1)
- `/rtp 15000` → $350 (base + tier2)
- `/rtp 20000` → $600 (base + tier3)

**Per-World Cooldown System:**

Each world has **independent** cooldowns! Players can teleport between worlds without waiting:

```yaml
custom_worlds:
  world:
    cooldown: 900           # 15 minutes for overworld
    cost: 150.0
    
  world_nether:
    cooldown: 300           # 5 minutes for nether
    cost: 300.0
    
  world_the_end:
    cooldown: 900           # 15 minutes for end
    cost: 500.0
```

**Example gameplay:**
1. Player types `/rtp world_the_end` → Teleports successfully (15min cooldown for world_the_end)
2. Player types `/rtp world_nether` → Teleports successfully (5min cooldown for world_nether)
3. Player types `/rtp world` → Teleports successfully (15min cooldown for world)
4. Player types `/rtp world_the_end` → Blocked! (cooldown still active for world_the_end)

**Benefits:**
- ✅ Explore multiple dimensions without global cooldown
- ✅ Each world has appropriate cooldown settings
- ✅ Better gameplay flow and player freedom
- ✅ Works with PlaceholderAPI for per-world displays

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
   - Optional: Hologram location (stand where you want it and confirm)

4. **Automatic Hologram Creation**
   - If you set a hologram location, it's automatically created
   - Engine is auto-selected based on `preferred-engine` setting
   - Hologram renders instantly with countdown
   - No manual `/rtpzone sethologram` command needed!

5. **Manual Hologram Management (Optional):**
   ```
   /rtpzone sethologram <zone_id>    # Add/move hologram
   /rtpzone delhologram <zone_id>    # Remove hologram
   ```

6. **Verify Configuration:**
   ```
   /rtpzone list      # Show all zones
   /rtpzone status    # Show sync status
   /fholo list        # Show FancyHolograms (if using FancyHolograms engine)
   ```

### Zone Features

**Player Experience:**
- Automatic detection when entering/leaving zones
- Real-time countdown displays:
  - **Title messages**: Large countdown display (5s, 4s, 3s...)
  - **Action bar**: Persistent countdown timer
  - **Holograms**: Optional floating countdown display with 3 engine options:
    - **FancyHolograms** Beautiful, persistent holograms with MiniMessage support
    - **PacketEvents**: High-performance packet-based holograms
    - **Display Entities**: Native vanilla entities, no dependencies
- Smart multi-player teleportation with automatic spreading
  - Each player gets a unique, safe location
  - Configurable spread distance prevents clustering
  - Optimized for Folia's multi-threaded regions
  - All players teleport simultaneously without lag
- Sound effects at each countdown tick
- Particle effects on teleportation

**Zone Management:**
- Automatic hologram creation on zone setup
  - No manual `/rtpzone sethologram` required
  - Holograms auto-select best available engine
  - Instant rendering on creation (no delays)
- Cross-server destination support via MySQL sync
- Customizable teleport intervals per zone
- Individual player opt-out: `/rtpzone ignore`
- Advanced multi-player teleportation system
  - Each player gets a unique location (no more same-spot spawns!)
  - Configurable min/max spread distance between players
  - Per-zone spread settings override global defaults
  - Full dimension safety (nether Y < 127, end islands, etc.)
- Automatic zone sync across network servers

**Hologram System**
- **3-Engine Architecture** with intelligent fallback:
  - **FancyHolograms**: Premium visuals, persistent storage, player-editable
  - **PacketEvents**: High performance, packet-based rendering
  - **Display Entities**: Vanilla fallback, no dependencies required
- **Automatic Engine Selection** via `preferred-engine` config option:
  - `auto` - Smart detection (FancyHolograms > PacketEvents > Entities)
  - `fancyholograms` - Force FancyHolograms (requires plugin)
  - `packetevents` - Force PacketEvents (requires plugin)
  - `entity` - Force vanilla Display Entities
- **Auto-Creation**: Holograms automatically created when zone is set up
- **Instant Rendering**: No delays, holograms appear immediately on all engines
- **FancyHolograms Persistence**:
  - Holograms saved to FancyHolograms storage
  - Visible in `/fholo list` for server admins
  - Players can edit holograms via FancyHolograms commands
  - Survives server restarts (managed by FancyHolograms)
  - Proper cleanup on zone deletion
- **Live Reload Support**: Update hologram templates via `/rtp reload`
- **Template Caching**: Zero config I/O during updates (85% performance boost)
- Dynamic countdown updates (5s → 4s → 3s → 2s → 1s → NOW!)
- Full MiniMessage formatting support (gradients, rainbow, hover, etc.)
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
    
    # Player spread configuration
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
# Preferred Hologram Engine
# Options: auto, fancyholograms, packetevents, entity
preferred-engine: auto

hologram-settings:
  line-spacing: 0.35      # Distance between lines
  y-offset: 2.5           # Height above zone center
  scale: 1.0              # Text size multiplier
  
  # Hologram text lines (supports MiniMessage)
  lines:
    - "<gradient:#20B2AA:#7FFFD4><bold>⚡ RTP Zone</bold></gradient>"
    - "<gray>━━━━━━━━━━━━━━━━</gray>"
    - "<gradient:#FFD700:#FFA500><bold>⏱ <time>s</bold></gradient>"
    - "<gray>━━━━━━━━━━━━━━━━</gray>"
    - "<aqua>Zone: <yellow><zone></yellow></aqua>"
  
  # Available placeholders:
  # <time> or <countdown> - Countdown timer (60, 59, 58... 3, 2, 1)
  # <zone> or <zone_id> - Zone identifier
  
  # MiniMessage Examples:
  # <gradient:#FF0000:#00FF00>Text</gradient> - Color gradients
  # <rainbow>Rainbow Text</rainbow> - Rainbow effect
  # <bold>Bold</bold> <italic>Italic</italic> - Text styling
  # <#FF5733>Custom Hex</#FF5733> - Custom hex colors
```

**FancyHolograms Integration**
When FancyHolograms is installed and `preferred-engine` is `auto` or `fancyholograms`:
- Holograms are **persistent** - saved to FancyHolograms database
- Visible in `/fholo list` with naming format: `justrtp_zone_<zoneid>`
- Fully editable by admins using FancyHolograms commands
- Survive server restarts (managed by FancyHolograms)
- Automatically cleaned up when zone is deleted
- Template changes applied via `/rtp reload` (no FancyHolograms restart needed)

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

## Hologram Engine System

JustRTP features a sophisticated 3-tier hologram engine system with automatic fallback and intelligent engine selection.

### Hologram Engine Options

**1. FancyHolograms (Recommended for 1.21+)**
- **Requirements**: FancyHolograms 2.8.0+ plugin, Paper/Folia 1.21+
- **Features**:
  - Beautiful, high-quality hologram rendering
  - Persistent storage (survives server restarts)
  - Visible in `/fholo list` as `justrtp_zone_<zoneid>`
  - Player-editable via FancyHolograms commands
  - Full MiniMessage support (gradients, rainbow, animations)
  - Template caching for zero config I/O overhead
- **Best For**: Servers wanting premium visuals and persistent holograms

**2. PacketEvents (High Performance)**
- **Requirements**: PacketEvents plugin
- **Features**:
  - Packet-based rendering (no entities)
  - High performance, low overhead
  - Instant rendering and updates
  - Full MiniMessage support
- **Best For**: High-traffic servers prioritizing performance

**3. Display Entities (Universal Fallback)**
- **Requirements**: None (vanilla Minecraft)
- **Features**:
  - Works on any Paper/Folia server
  - No dependencies required
  - Native Display Entity support
  - Full MiniMessage support
- **Best For**: Servers without hologram plugins, guaranteed compatibility

### Engine Selection

Configure in `holograms.yml`:

```yaml
# Preferred Hologram Engine
# Options: auto, fancyholograms, packetevents, entity
preferred-engine: auto
```

**Engine Selection Modes:**

| Mode | Behavior |
|------|----------|
| `auto` | Smart detection with priority: FancyHolograms > PacketEvents > Display Entities |
| `fancyholograms` | Force FancyHolograms (error if not installed) |
| `packetevents` | Force PacketEvents (error if not installed) |
| `entity` | Force Display Entities (always available) |

**How Auto-Detection Works:**
1. Checks if FancyHolograms 2.8.0+ is installed → Use FancyHolograms
2. Else, checks if PacketEvents is installed → Use PacketEvents
3. Else, fallback to Display Entities → Always works

### Hologram Lifecycle

**Automatic Creation**
- Holograms are automatically created when zone is set up
- No manual `/rtpzone sethologram` command needed
- Engine is auto-selected based on `preferred-engine` setting
- Instant rendering with forced refresh on all engines

**Live Updates:**
- Countdown updates every second (60s → 59s → 58s... → 1s)
- Template changes via `/rtp reload` apply immediately
- FancyHolograms: Changes persist across restarts
- PacketEvents/Entities: Changes apply to active holograms

**Automatic Cleanup:**
- Holograms removed when zone is deleted
- FancyHolograms: Also removed from `/fholo list` and persistent storage
- PacketEvents: Packets cleared for nearby players
- Entities: Display entities properly despawned

### Template Configuration

**`holograms.yml` - Global Template:**
```yaml
hologram-settings:
  line-spacing: 0.35      # Vertical distance between lines
  y-offset: 2.5           # Height above zone center
  scale: 1.0              # Text size (1.0 = normal)
  
  lines:
    - "<gradient:#20B2AA:#7FFFD4><bold>⚡ RTP Zone</bold></gradient>"
    - "<gray>━━━━━━━━━━━━━━━━</gray>"
    - "<gradient:#FFD700:#FFA500><bold>⏱ <time>s</bold></gradient>"
    - "<gray>━━━━━━━━━━━━━━━━</gray>"
    - "<aqua>Zone: <yellow><zone></yellow></aqua>"
```

**Available Placeholders:**
- `<time>` or `<countdown>` - Current countdown in seconds
- `<zone>` or `<zone_id>` - Zone identifier

**MiniMessage Formatting Examples:**
```yaml
# Gradients
"<gradient:#FF0000:#00FF00>Gradient Text</gradient>"

# Rainbow effect
"<rainbow>Rainbow Text</rainbow>"

# Text styling
"<bold>Bold</bold> <italic>Italic</italic> <underlined>Underlined</underlined>"

# Custom hex colors
"<#FF5733>Custom Color</#FF5733>"

# Hover text (FancyHolograms only)
"<hover:show_text:'More info'>Hover Me</hover>"
```

### FancyHolograms Integration

**Persistent Hologram Benefits:**
- **Saved to Database**: Holograms survive server restarts
- **Admin Editing**: Edit via `/fholo edit justrtp_zone_<zoneid>`
- **Player Visibility**: Visible in `/fholo list` for management
- **Automatic Sync**: JustRTP updates are applied to FancyHolograms storage
- **Clean Removal**: Deleting zone removes hologram from FancyHolograms

**FancyHolograms Commands for Zone Holograms:**
```bash
# List all holograms (including JustRTP zones)
/fholo list

# Edit a zone hologram directly
/fholo edit justrtp_zone_pvp-arena

# Teleport to hologram location
/fholo teleport justrtp_zone_pvp-arena

# Note: JustRTP manages lifecycle, manual deletion not recommended
```

**Template Caching System:**
- Hologram templates loaded once on startup
- Cached in memory (Map<String, List<String>>)
- Zero config file reads during updates
- 85% reduction in I/O operations
- Placeholders applied at runtime

### Performance Optimization

**Engine Performance Comparison:**

| Engine | CPU Impact | Memory | Dependencies | Persistence |
|--------|------------|--------|--------------|-------------|
| FancyHolograms | Low | Low | ✅ Required | ✅ Yes |
| PacketEvents | Very Low | Very Low | ✅ Required | ❌ No |
| Display Entities | Low | Low | ❌ None | ❌ No |

**Optimization Tips:**
- Use `auto` mode for best balance
- FancyHolograms: Best for persistent, editable holograms
- PacketEvents: Best for high player count servers
- Display Entities: Best for maximum compatibility
- Adjust `view-distance` in zone config to limit render range
- Template caching is automatic (no config needed)

### Troubleshooting Holograms

**Hologram not visible:**
1. Check engine is available: `/rtp reload` shows active engine
2. Verify zone has hologram: `/rtpzone list`
3. Check render distance (default: 64 blocks)
4. FancyHolograms: Verify with `/fholo list`
5. Enable debug mode in `config.yml` for detailed logs

**Countdown stuck at 0s:**
- Fixed in 3.2.9 with template caching system
- Ensure you're running latest version
- Run `/rtp reload` to refresh templates

**FancyHolograms not showing in /fholo list:**
- Ensure FancyHolograms 2.8.0+ is installed
- Verify `preferred-engine: auto` or `fancyholograms` in holograms.yml
- Check console for "Using FancyHolograms engine" message
- Delete and recreate zone if upgraded from older JustRTP version

**Wrong engine being used:**
- Check `preferred-engine` setting in holograms.yml
- Verify required plugin is installed and loaded
- Console shows active engine on `/rtp reload`
- Use `entity` mode for guaranteed fallback

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
| `justrtp.cooldown.bypass` | Skip cooldown timers | OP |
| `justrtp.bypass.cooldown` | Legacy cooldown bypass (deprecated) | OP |
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

### World-Specific Placeholder Support

**Format**: `%justrtp_<placeholder>_<worldname>%`

You can now get cooldowns, costs, and delays for **specific worlds** instead of just the player's current world!

**Examples:**
- `%justrtp_cooldown_world_nether%` - Cooldown for world_nether
- `%justrtp_cost_world%` - Cost for world named "world"
- `%justrtp_delay_world_the_end%` - Delay for world_the_end
- `%justrtp_min_radius_world%` - Min radius for world
- `%justrtp_max_radius_world_nether%` - Max radius for world_nether

**Usage in Scoreboards/GUIs:**
```yaml
# Show cooldowns for all worlds at once
scoreboard:
  lines:
    - "Overworld Cooldown: %justrtp_cooldown_world%"
    - "Nether Cooldown: %justrtp_cooldown_world_nether%"
    - "End Cooldown: %justrtp_cooldown_world_the_end%"
```

### Cooldown Placeholders

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_cooldown%` | Formatted cooldown with units | `5 minutes, 30 seconds` |
| `%justrtp_cooldown_<world>%` | Cooldown for specific world | `5m 30s` |
| `%justrtp_cooldown_raw%` | Raw seconds remaining | `330` |
| `%justrtp_cooldown_total%` | Total cooldown duration | `600` |
| `%justrtp_cooldown_formatted%` | Compact format | `5m 30s` |
| `%justrtp_has_cooldown%` | Boolean cooldown check | `true` or `false` |

### Cost & Delay Placeholders

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_cost%` | Economy cost for current world | `500.0` |
| `%justrtp_cost_<world>%` | Economy cost for specific world | `100.0` |
| `%justrtp_delay%` | Teleport warmup seconds | `3` |
| `%justrtp_delay_<world>%` | Delay for specific world | `5` |
| `%justrtp_is_delayed%` | Active delay check | `true` or `false` |

### World & Configuration Placeholders

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_world_name%` or `%justrtp_world%` | Current world name | `world_nether` |
| `%justrtp_current_world%` | Alias for world_name | `world_nether` |
| `%justrtp_world_min_radius%` or `%justrtp_min_radius%` | Minimum teleport radius | `100` |
| `%justrtp_min_radius_<world>%` | Min radius for specific world | `500` |
| `%justrtp_world_max_radius%` or `%justrtp_max_radius%` | Maximum teleport radius | `10000` |
| `%justrtp_max_radius_<world>%` | Max radius for specific world | `15000` |
| `%justrtp_permission_group%` or `%justrtp_group%` | Active permission group | `vip` |

### Status Placeholder

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_can_rtp%` | Composite check (cooldown + cost + delay) | `true` or `false` |

### RTP Zone Placeholders

Zone system placeholders for FancyHolograms, scoreboards, and other plugins.

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%justrtp_zone_time_<zoneid>%` | Countdown timer for specific zone | `15` (seconds) |
| `%justrtp_in_zone%` | Whether player is in any zone | `true` or `false` |
| `%justrtp_current_zone%` or `%justrtp_zone%` | Zone ID player is currently in | `pvp-arena` or `None` |
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

**Multi-World Scoreboard:**
```yaml
scoreboard:
  - "=== RTP Cooldowns ==="
  - "Overworld: %justrtp_cooldown_world%"
  - "Nether: %justrtp_cooldown_world_nether%"
  - "The End: %justrtp_cooldown_world_the_end%"
  - ""
  - "=== RTP Costs ==="
  - "Overworld: $%justrtp_cost_world%"
  - "Nether: $%justrtp_cost_world_nether%"
```

**Permission Group Display:**
```yaml
- "Rank: %justrtp_group%"
- "Status: %justrtp_can_rtp%"
- "World: %justrtp_world_name%"
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
- Install FancyHolograms for best visuals and persistence
- Alternative: Install PacketEvents for high-performance holograms
- Fallback: Display Entities work without any dependencies
- Check `preferred-engine` setting in `holograms.yml`
- Verify zone has hologram configured (auto-created on setup in 3.2.9+)
- Confirm hologram location is within render distance
- For FancyHolograms: Check `/fholo list` to verify hologram exists

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
| **Plugin Version** | 3.3.1 |
| **Minecraft Version** | 1.21+ (1.21.4 tested) |
| **Server Software** | Paper, Folia, Spigot |
| **Java Version** | 21+ required |
| **API Version** | 1.21 |
| **Database Support** | MySQL 8.0+, Redis (optional) |
| **Dependencies** | Vault, PlaceholderAPI, WorldGuard, FancyHolograms, PacketEvents (all optional) |
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
| FancyHolograms Integration | ✅ Available (3.2.9+) | FancyHolograms 2.8.0+ |
| PacketEvents Holograms | ✅ Available | PacketEvents |
| Display Entity Holograms | ✅ Available (Fallback) | None |
| Auto Hologram Creation | ✅ Available (3.2.9+) | None |
| Persistent Holograms | ✅ Available (3.2.9+) | FancyHolograms |
| Hologram Live Reload | ✅ Available (3.2.9+) | None |
| Location Caching | ✅ Available | None |
| Permission Groups | ✅ Available | None |
| Auto-RTP (Join/Respawn) | ✅ Available | None |
| Particle Effects | ✅ Available | None |
| Sound Effects | ✅ Available | None |
| MiniMessage Formatting | ✅ Available | None |

---

## Changelog

### Version 3.3.1 (Current)

**Quality of Life Improvements:**
- ⏱️ **Human-Readable Time Formatting**: All time displays now show "5m 38s" instead of "338 seconds"
  - Affects cooldowns, delays, holograms, and all time-based messages
- 👑 **OP/Permission Cooldown Bypass**: OP players and those with `justrtp.cooldown.bypass` permission skip cooldowns
  - Automatic bypass for OP status
  - Works for both proxy and local teleports
- 🔇 **Empty Message Suppression**: Hide any message by setting it to `""` in messages.yml
  - Set `message: ""` to completely suppress (no blank lines)
  - Distinguishes between missing keys (warning) and disabled messages (silent)
- 💰 **Radius-Based Pricing System**: Charge different prices based on teleport radius
  - Configure tier-based pricing per world
  - Example: radius 5000 = $100, radius 15000 = $350, radius 20000 = $600
  - Costs are additive (base cost + radius tier cost)
  - Fully compatible with existing permission group pricing


---

## License & Credits

**JustRTP** is developed and maintained with care by **kotori**.

This plugin is open-source software, licensed under the MIT License.

---

**Need help?** Review the troubleshooting section above or check your console logs with debug mode enabled.

**Enjoying JustRTP?** Consider leaving feedback or supporting continued development!
