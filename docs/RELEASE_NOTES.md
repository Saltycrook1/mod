# Stormward 0.1.0-alpha.1

First tornado-focused alpha for Minecraft Java 1.21.1, Fabric Loader 0.20.1, Fabric API 0.116.7+1.21.1 and Java 21.

- Five readable storm stages, moving tornadoes with varying natural size/strength/speed/duration, bounded server wind and locally rendered funnels.
- Craftable receiver with north-up radar, bearing, closest-approach estimate and shelter exposure.
- Scheduled sirens with visual warning, optional bell and redstone level 15; reinforced shelter panels.
- Opt-in, tag-based surface destruction with lifetime limits, permanent container exclusions, conservative player-build chunk protection and no loot/debris spawning.
- Persistent fronts, explicit work/particle caps, server controls and separate accessibility preferences.

Validation: final local build and 18 JUnit tests passed; the dedicated headless integration fixture passed NBT persistence, packet bounds, real shelter/destruction/siren/entity queries and unloaded pause. [Exact measurements](BENCHMARK_2026-10-28.md) include all counts, runtime information and limitations. These hot-cache adapter timings do not establish sustained TPS/FPS or superiority over other mods.

The development client started and entered a disposable singleplayer world. Computer Use was stopped by the user's Escape key before the receiver/funnel could be inspected; no later UI input was sent. HUD/funnel visual quality, real two-client synchronization, ordinary dedicated-server restart/crash recovery and sustained-load gameplay remain unverified. This is a playable alpha candidate, not a stable-release certification. Other disasters, evolving storm tracks, local rain/lightning and third-party claims integration are deferred; see the [roadmap](ROADMAP.md).

## 0.1.0-alpha.2 lift correction

Raised mature tornado core updraft from 0.07 to0.16 before strength/exposure/falloff and the existing0.28 total acceleration cap. Strength-one updraft can now overcome ordinary living-entity gravity. Added a unit regression and a real Minecraft gravity-enabled cow fixture: Y -34.0 to -6.6477 over40 back-to-back entity/server adapter ticks. AI must remain enabled for this fixture to exercise travel. All19 unit tests and the updated headless integration passed locally. This is automated server physics evidence, not a manual client gameplay check. Native Minecraft access was refused with 'Computer Use was not approved to use javaw'; hands-on visual verification is still pending approval.

