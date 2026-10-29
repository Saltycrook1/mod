# Release checklist

Target: **0.1.0-alpha.1**, Minecraft 1.21.1 / Fabric / Java 21. Keep unverified items unchecked; source availability and compilation do not establish gameplay quality.

## Build and automated evidence

- [ ] From a clean checkout, run `./gradlew --no-daemon clean build` and `./gradlew --no-daemon runGametest` with JDK 21.
- [ ] Record commit, runtime/hardware, actual test counts and failures in [Validation](VALIDATION.md).
- [ ] Retain JUnit/GameTest reports and the generated adapter benchmark, including storm/entity counts and limitations.
- [ ] Verify the release JAR excludes the development test mod and contains content resources, license and correct metadata.
- [ ] Confirm normal dedicated-server startup and a full stop/restart with live fronts; compare resumed age, course, removal counts and protected chunks.

## Gameplay and presentation

- [ ] Play a normal five-minute lifecycle and a 30-second demo in Survival; confirm warnings give useful preparation time.
- [ ] Check funnel continuity, distance detail, motion, HUD clipping and radar legibility across common GUI scales.
- [ ] Craft all three items/blocks; verify reinforced-panel and siren drops, siren indicator, bell cadence and redstone 15/reset.
- [ ] Test complete/partial shelter, openings, underground rooms, terrain changes, crouching and fall danger.
- [ ] Compare two real clients through late join, disconnect, reconnect, dimension travel and server restart. Check shared storm stage/position and removal of stale effects.
- [ ] Verify reduced/minimal particles, both zero-particle options, wind mute/volume, Weather/Blocks/Master sliders and HUD disable. Check that muted audio still leaves readable warnings.

## World integrity and sustained load

- [ ] In a disposable world, test destruction off/on, height/border edges, containers, protected tags, old builds and explicit 3×3 protection.
- [ ] Exercise protection capacity overflow and malformed/unknown saved data; destruction must stay locked.
- [ ] Run a sustained ten-minute dedicated-server scenario with clients, terrain and realistic entity counts. Record server MSPT/TPS, loaded chunks, heap/GC, network bytes and client FPS/GPU details.
- [ ] State every compatibility combination actually tested. Do not imply external claim, shader or optimization-mod support from standalone tests.

## Packaging and publication

- [ ] Copy actual results into the release notes and list unresolved manual checks prominently.
- [ ] Confirm dependency versions, install/run instructions, asset provenance and licensing notices.
- [ ] Publish only the remapped mod JAR and intended sources/documentation; exclude caches, test worlds and local configuration.
- [ ] Retain the alpha label until multiplayer, presentation, ordinary restart and sustained-load checks pass. Do not claim comparative performance without a comparable competitor benchmark.

The roadmap records remaining engineering work; unchecked items are not completed features.
