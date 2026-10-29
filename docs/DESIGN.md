# Design and implementation

## Tornado-first scope

Stormward focuses on a readable survival loop: see a developing front, obtain a forecast, prepare a room and siren, then survive the strongest wind. The alpha implements five sequential stages and a moving tornado. It deliberately leaves local rain, lightning, changing tracks and additional disasters for later milestones. A normal lifecycle is 6,000 loaded game ticks; the operator demo divides stage lengths by ten.

Natural fronts vary in radius (20–44 blocks), strength (0.7–2.0), heading and speed (0.015–0.040 blocks/tick). Their initial heading points toward the selected player's position at creation. Operator fronts have a fixed westward course with a small southward component. Tracks do not turn during a storm. Force intensity ramps within the tornado stage and decays during dissipation.

## Research and three chosen differentiators

The official [Weather, Storms & Tornadoes page](https://www.curseforge.com/minecraft/mc-mods/weather-storms-tornadoes) describes localized storms, instruments, forecasting, deflectors and particle debris. Its stated radar/forecast limitations concern older 1.18/1.20 releases and should not be generalized to its latest version. [Weather & Tornadoes Remastered](https://modrinth.com/mod/weather-and-tornadoes-remastered) provides a Fabric client/server weather project and recommends optimization mods. These show established player expectations; no code, names, textures, sounds or branding were copied.

The following opportunities are **our design inferences**, not claims that other mods lack these features:

| Choice | Implemented behavior | Evidence and current boundary |
| --- | --- | --- |
| Explainable shelter | Five roof probes plus eight two-height wall directions produce continuous exposure; receiver reports it | Geometry and force tests; no air-flow or structural simulation |
| Useful forecasting | Bearing, range, straight-course closest approach/ETA, radar path dots, and unloaded-front status in the command | Deterministic forecast and packet tests; remote client presentation still needs manual checks |
| Explicit performance budgets | Bounded entity collection, per-tick surface attempts, lifetime removals, client creation/live caps and no simulation chunk tickets | Reproducible suite and actual results in [Validation](VALIDATION.md); no comparative speed claim |

Popularity is an aspiration. Demonstrated fairness, presentation, accessibility and measured behavior are the acceptance criteria.

## Architecture

`sim/` contains Minecraft-independent stage, motion, force, shelter and forecast logic. `server/` adapts it to loaded terrain, entities, persistence, protection and metrics. `Stormward` registers content, commands, lifecycle hooks and the common payload codec. `src/client/` owns rendering, local audio, HUD and local preferences. Loom's split environment source sets prevent a common source from accidentally importing client classes at compile time.

Only the server advances fronts, applies entity velocity or removes blocks. There is no client-to-server storm-control packet. Commands use operator level 2 except the public forecast. Servers send explicit player-velocity updates after wind application. Creative and spectator players are excluded, and the player effect can be disabled in server configuration.

### Work and safety bounds

- At most eight persisted/synchronized fronts; three by default. Only the Overworld ticks them.
- Each front checks its current and next origin chunks with a non-loading chunk lookup. A missing origin pauses motion, age and effects. Surface sampling checks a chunk before querying its height map; no storm loads a distant region.
- Wind queries a local box extending twice the radius horizontally and -8/+96 blocks from the center's terrain height. A shared default budget accepts at most 48 living-entity visits per dimension tick. Storm iteration rotates. Overlaps can visit an entity more than once; final velocity is capped.
- Shelter uses at most 163 query calls, stops a ray at obstruction, caches by entity/block position for ten ticks and keeps at most 512 entries. Unknown neighboring chunks count as sheltered. Block changes can take up to half a second to affect a stationary entity's cached exposure.
- Destruction selects one front per tick and makes at most 12 surface attempts by default. Only tagged, sufficiently soft, fluid-free, non-container blocks inside the storm radius, height limits and border are eligible. Lifetime removals are persisted. Removal has no drops or physical debris entities.
- Protection reserves whole chunks on block-item interaction. Capacity overflow and unknown save schema fail closed by locking destruction. Existing builds and other placement mechanisms require explicit protection. External claims are not consulted.

Caps bound accepted work and growth, not wall-clock execution time. Minecraft's engine still searches spatial candidates, and many scheduled sirens add work. Crowded queries may repeatedly omit some entities; per-entity fairness is a remaining weakness. A complete server-time or multiplayer capacity claim requires sustained testing.

### Synchronization and client detail

A dimension-local snapshot contains at most eight frames: UUID, position, velocity, radius, intensity, stage and remaining stage ticks, plus exposure and the entity budget. It is sent once per 20 ticks and on join/explicit control changes. Each player's snapshot includes only loaded fronts within 512 blocks. No particles are transmitted. The codec rejects an excessive frame count.

Client presentation extrapolates for at most 40 ticks and expires a signal after 100 ticks. Disconnects and world changes clear snapshots and owned particles. Default rendering stops beyond 256 blocks, reduces detail with distance and shares a 48-particle creation budget across visible fronts. A deque owns at most 1,024 live particles, marks older particles dead on overflow and gives every new particle a maximum 32-tick lifetime. User limits can disable effects completely. Minecraft's particle preference reduces creation further.

The receiver forecast uses current relative position and constant storm velocity; the player's future motion and unloaded pause are not predicted. `/stormward forecast` exposes unloaded status. The radar is north-up and automatically scales to the selected front. Client-only wind audio uses the Weather category; server sirens use Blocks. No screen shake, camera roll or flashing lightning is implemented.

### Persistence

Schema 1 stores storm UUIDs, position, velocity, radius, strength, age, time divisor, lifetime block-removal counts, protected chunks, protection lock and next natural check in `stormward_storms` persistent state. Invalid individual fronts are skipped. The read path caps fronts and protection entries. Runtime shelter caches and timings are not persisted. Minecraft handles world-save writing; an NBT roundtrip does not prove crash recovery.

## Verified upstream references

Pinned versions are in `gradle.properties`, `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`. The source is compiled against those exact artifacts.

- [Fabric 1.21.1 setup / JDK 21](https://docs.fabricmc.net/1.21.1/develop/getting-started/setting-up-a-development-environment)
- [Fabric Loom documentation](https://docs.fabricmc.net/develop/loom/), including environment source separation; modern documentation also covers newer plugin IDs, so follow this repository's pinned build for this target
- [Gradle Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- [Fabric API 0.116.7 networking registry](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry.html): codecs register on both sides before receivers
- [Server networking API](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.html): client capability checks and payload transmission
- [Yarn 1.21.1 API](https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.3/): loaded-world access, persistent state and bounded entity collection
- [HUD callback for the pinned API](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.html)
- [Fabric 1.21.1 particles](https://docs.fabricmc.net/1.21.1/develop/rendering/particles/creating-particles) and [automated testing](https://docs.fabricmc.net/1.21.1/develop/automatic-testing)
