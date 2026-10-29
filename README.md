# Stormward

**Read the sky. Find cover. Survive the front.**

Stormward `0.1.0-alpha.2` is a tornado-first survival mod for **Minecraft Java 1.21.1 / Fabric / Java 21**. Local storms progress through warnings into a moving funnel. A handheld receiver, redstone sirens and reinforced shelter give players practical counterplay. Destruction is **off by default**.

This is an alpha. Client presentation, real two-client synchronization and sustained gameplay need the manual checks in [Validation](docs/VALIDATION.md). A compiled effect is not a verified aesthetic result. There are no popularity guarantees or claims of outperforming another mod.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **1.21.1**, using Java 21.
2. Put `stormward-0.1.0-alpha.2.jar` and **Fabric API for 1.21.1** in the instance's `mods/` directory. Use the same mod versions on clients and the dedicated server.
3. The pinned baseline is Loader **0.20.1**, Fabric API **0.116.7+1.21.1**. Newer dependency versions are not automatically validated. Forge, NeoForge, Bedrock and other Minecraft versions are unsupported.

## Play

At 20 TPS, an operator-spawned normal loaded front lasts five minutes:

| Stage | Duration | What to expect |
| --- | ---: | --- |
| Gathering | 60 seconds | Local cloud deck and light wind |
| Watch | 60 seconds | Strengthening wind and a stage notification |
| Warning | 30 seconds | Funnel formation; nearby sirens activate |
| Tornado | 120 seconds | Rotating funnel, strongest pull and lift |
| Dissipating | 30 seconds | Wind fades before the front disappears |

Natural fronts vary in radius, strength, speed and duration: either the timing above or half those durations. By default, the server checks every **24,000 game ticks** (20 minutes at 20 TPS), with a **35% chance per eligible check**, spawning 128 blocks from a player if that position is already loaded and inside the world border. There can be three tracked fronts. Unloaded fronts pause and consume a storm slot; they do not load terrain to continue. Movement is straight and constant in this alpha. Fronts reaching the world border are removed.

Hold a **Storm Receiver** in either hand to see nearby loaded fronts: stage, distance, bearing, a north-up radar, estimated closest approach, and local wind exposure. The estimate assumes the present straight course and ignores the observer's future movement; it is not a promise of impact. `/stormward forecast` also reports paused fronts in your dimension.

Build a complete roof and two-block-high full-cube walls within eight blocks of your position. Roof-only cover gives partial protection; an enclosed room gives stronger protection. A reinforced panel is permanently excluded from tornado destruction. Shelter sampling is a local geometric approximation, not a structural engineering model. Crouching reduces applied wind acceleration by 55%. Stormward adds no direct damage source; wind lift, collisions and normal fall damage can still be dangerous.

A **Storm Siren** checks once per second for loaded Warning/Tornado fronts within 256 blocks, changes its indicator and emits redstone level 15. Its bell signal repeats about every five seconds. It only runs while its chunk is ticking.

### Crafting

Spaces in each pattern are empty slots. All recipes use a 3×3 crafting grid.

| Output | Pattern, top → bottom | Key |
| --- | --- | --- |
| 1 Storm Receiver | ` I ` / `ICI` / ` R ` | I = iron ingot, C = compass, R = redstone |
| 1 Storm Siren | ` I ` / `INI` / ` R ` | I = iron ingot, N = note block, R = redstone |
| 8 Reinforced Shelter Panels | `SIS` / `ISI` / `SIS` | S = stone bricks, I = iron ingot |

## Server controls

| Command | Permission | Effect |
| --- | --- | --- |
| `/stormward forecast` | Everyone | Distance, bearing, stage, advisory closest approach and unloaded status |
| `/stormward spawn [strength]` | Operator level 2 | Five-minute front 64 blocks east; strength 0.1–5, default 1 |
| `/stormward demo` | Operator level 2 | Accelerated 30-second lifecycle 32 blocks east |
| `/stormward stop` | Operator level 2 | Remove tracked fronts in the current dimension |
| `/stormward protect` | Operator level 2 | Persistently protect the surrounding 3×3 chunks from destruction |
| `/stormward destruction true\|false` | Operator level 2 | Change and save the destruction option |
| `/stormward reload` | Operator level 2 | Reload server settings; clear runtime caches and timings |
| `/stormward status` | Operator level 2 | Simulation timings, work counters, protection count and safety lock |

Spawning is restricted to the Overworld and loaded terrain inside the border. To try the loop quickly in a test world, use `/give @s stormward:storm_receiver`, place a siren and shelter, then `/stormward demo`.

### Configuration and protection

Server controls are in `config/stormward.json`; reload with `/stormward reload`. Client effects/HUD/audio are in `config/stormward-client.json`, read on startup. See the [complete settings and destruction rules](docs/CONFIGURATION.md).

Default destruction limits are 12 surface attempts per tick and 256 removals per front, limited to tagged fragile vegetation/snow. Containers are skipped and no drops or debris entities are created. Player block-item interactions conservatively protect entire chunks, even on failed placement. **Protect pre-existing builds, command/mod placements and claimed areas with `/stormward protect` before enabling destruction.** External claims are not integrated.

Client defaults cap creation at 48 particles/tick and 1,024 live particles. Both can be set to zero. Minecraft's Decreased/Minimal settings reduce creation further. Wind uses the Weather slider; sirens use Blocks. There are no camera shakes or lightning flashes, and text/redstone warnings remain available with audio muted.

## Build and run

Install a **JDK 21**, set `JAVA_HOME` and ensure `java -version` reports 21. No separate Gradle installation is needed. First builds require network access.

```powershell
git clone https://github.com/Saltycrook1/mod.git
cd mod
.\gradlew.bat --no-daemon clean build
.\gradlew.bat --no-daemon runGametest
.\gradlew.bat runClient
```

On Linux/macOS, use `./gradlew` in place of `.\gradlew.bat`. The distributable is `build/libs/stormward-0.1.0-alpha.2.jar`; the `-sources.jar` is for development. To run a development dedicated server, use `runServer`, review the generated Minecraft EULA in `run/eula.txt`, accept it if you agree, then run again. For ordinary hosting, use a Fabric 1.21.1 server and put the mod/Fabric API in its `mods/` directory.

The build pins Gradle **8.14.3**, Loom **1.11.8**, Yarn **1.21.1+build.3**, Loader **0.20.1** and Fabric API **0.116.7+1.21.1**. Fabric's [1.21.1 setup guide](https://docs.fabricmc.net/1.21.1/develop/getting-started/setting-up-a-development-environment) requires JDK 21. This target uses an established Fabric API and Java 21 toolchain; it is not the newest Minecraft release. API choices and research are linked in [Design](docs/DESIGN.md).

## Current limits

The alpha does not replace vanilla weather, create local rain/lightning, simulate changing storm tracks, damage structural buildings, or add other disaster types. Shelter favors nearby full-cube roof/wall geometry; doors, slabs, openings and large halls need manual assessment. Under heavy crowding, some entities can be omitted by the work budget. Large siren installations and engine spatial-query costs still scale with world activity. Low-end FPS, real multiplayer presentation and crash recovery are not established by compilation or codec tests.

Storm state, removal counters, natural-spawn timing and protected chunks use Minecraft persistent state in the world save. Unknown state schemas preserve raw data, suspend simulation and lock destruction. Back up worlds before upgrading an alpha.

See [Validation and measurements](docs/VALIDATION.md), [Release checklist](docs/RELEASE_CHECKLIST.md), [Roadmap](docs/ROADMAP.md), [MIT license](LICENSE), and [asset/dependency notices](THIRD_PARTY_NOTICES.md).
