# Configuration and protection

Server settings are created in `config/stormward.json`. Edit them and run `/stormward reload` or restart. Invalid JSON refuses loading; numeric values are clamped to supported ranges. A failed reload retains the previous settings. `/stormward destruction true` and `false` update and save that option immediately.

## Server settings

| Setting | Default | Supported range / meaning |
| --- | --- | --- |
| `naturalStorms` | `true` | Allow natural spawning |
| `naturalIntervalTicks` | `24000` | 1,200–1,728,000 ticks between checks |
| `naturalChance` | `0.35` | 0–1 probability per eligible check |
| `maxStorms` | `3` | 1–8; persisted fronts count toward the spawning cap |
| `entityBudgetPerTick` | `48` | 1–128 accepted entity visits per dimension tick |
| `blockAttemptsPerTick` | `12` | 0–64 attempts shared across fronts |
| `maxBlocksPerStorm` | `256` | 0–4,096 lifetime removals, persisted |
| `destruction` | `false` | Allow bounded fragile-block removal |
| `protectPlayerBuilds` | `true` | Track placement interactions and honor protected chunks |
| `maxBlockHardness` | `0.8` | 0–3; tagged blocks must also meet this limit |
| `destructionMinY` / `destructionMaxY` | `-64` / `320` | Inclusive bounds, clamped to -64–320 |
| `windMultiplier` | `1.0` | 0–2; 0 disables wind acceleration |
| `affectPlayers` | `true` | Apply wind to non-creative, non-spectator players |
| `warningMessages` | `true` | Stage messages within 384 blocks |
| `sirenSounds` | `true` | Enable siren bells; indicator/redstone still work when muted |
| `maxProtectedChunks` | `65536` | 256–65,536; overflow persistently locks destruction |

Reducing the spawning cap does not delete already persisted fronts. `/stormward stop` clears fronts in the command source's dimension. `windMultiplier` is the difficulty control; zero prevents additional acceleration but retains the adapter's final velocity limits. Shelter and crouching multiply acceleration before those limits.

## Destruction policy

Opt-in destruction only removes exposed surface blocks in `#stormward:fragile`: leaves, short/tall grass, fern/large fern, dead bush, snow layers and vines by default. The hardness ceiling, inclusive height limits, world border, protected tag, protected chunks, radius and per-storm budget all apply. Containers/block entities and fluid-bearing blocks are always skipped. Removal produces no item drops, falling blocks or persistent debris entities.

Data packs can extend `data/stormward/tags/block/fragile.json` and `protected.json` for this Minecraft version. A block must be fragile and satisfy every other rule; the protected tag wins. Reinforced shelter panels and sirens are protected by the default tag. Back up worlds and test custom tags in a disposable world.

With `protectPlayerBuilds=true`, a player's block-item interaction conservatively protects both clicked and target chunks, including failed placement attempts. This tracks activity after installing the mod. **Protect pre-existing builds, command/mod placements and claimed areas with `/stormward protect` before enabling destruction.** That operator command reserves the surrounding 3×3 chunks, aligned to chunk boundaries. Third-party claims are not integrated.

There is no unprotect command. Disabling `protectPlayerBuilds` also disables honoring these protected chunks, while tag/container/height/border rules remain. Reaching the protection-table capacity persistently locks destruction instead of discarding protection. Unknown saved-data schemas also lock destruction. `/stormward status` reports the lock; changing `destruction` does not bypass it.

## Client settings and accessibility

`config/stormward-client.json` is read on client startup. Invalid local settings use defaults. These preferences never change server physics.

| Setting | Default | Supported range / meaning |
| --- | --- | --- |
| `particleBudget` | `48` | 0–128 particles created per tick across all fronts |
| `liveParticleCap` | `1024` | 0–2,048 live Stormward particles; maximum lifetime 32 ticks |
| `effectRange` | `256` | 32–512 blocks; distance reduces detail |
| `windSound` / `soundVolume` | `true` / `0.35` | Local wind sound toggle and 0–1 volume |
| `hud` | `true` | Receiver HUD toggle |

Minecraft's Decreased/Minimal particles settings halve/quarter the local creation budget. Zero budget or zero live cap disables storm particles. Wind audio respects Weather and Master; sirens respect Blocks and Master, and server `sirenSounds` can mute them globally. There are no camera shakes or lightning flashes. Stage text, receiver HUD, siren indicator and redstone remain available when sounds are muted. Client settings currently have no in-game configuration screen.
