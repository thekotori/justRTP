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
<a href="https://discord.gg/HRjcmEYXNy">
<img src="https://img.shields.io/discord/1389677354753720352?color=5865F2&label=Discord&logo=discord&logoColor=white&style=for-the-badge" alt="Discord" />
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

---

## Documentation

For full documentation, including advanced configuration, permissions, and API usage, please visit our [Wiki](https://kotori.ink/wiki/justrtp).

---

## License & Credits

**JustRTP** is developed and maintained with care by **kotori**.

This plugin is open-source software, licensed under the CC BY-NC-SA 4.0 License
