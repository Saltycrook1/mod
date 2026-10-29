# Validation and measurement

Run with Java 21 from the repository root:

```powershell
.\scripts\validate.ps1
```

Or run on any supported Gradle host:

```sh
./gradlew --no-daemon clean build
./gradlew --no-daemon runGametest
```

The wrapper pins Gradle, and the build pins Minecraft, Yarn, Loader, Fabric API, Loom and JUnit. Network access is needed to download dependencies on the first run. `runGametest` launches Fabric's dedicated headless GameTest server, loads common production classes and the development-only test mod, and exits with a failure status if an assertion fails. It does not package the test fixture in the release JAR. A normal user server still requires its owner to review and accept Minecraft's EULA.

## Automated coverage

- Plain JUnit exercises deterministic stage transitions, restored state, unloaded pause, force falloff/bounds, roof and wall geometry, forecasting and work budgets independently of Minecraft.
- One ordered loaded-world integration fixture avoids shared configuration races. It checks NBT state and deterministic resume, persistent damage counts/protected chunks, protection overflow lock, maximum snapshot roundtrip and invalid count rejection, a real stone shelter with a removed wall, actual block removal after destruction opt-in, lifetime damage cap, container/unbreakable/reinforced/player-build protection, height boundaries and absence of item drops.
- The same fixture checks a siren's scheduled warning detection and redstone level 15, then resets it after storms stop. It collects actual entities with Minecraft's limited spatial query, preserves existing entity velocity when wind is disabled or the sampled radial force is zero, and verifies remote storms remain frozen without loading their chunks. An unknown save schema must retain its full raw NBT while simulation is suspended.
- Successful startup of the headless test server checks that common initialization can run without client classes.

Artifacts: `build/reports/tests/test/index.html`, `build/test-results/test/`, `build/gametest-results.xml`, `build/gametest/logs/latest.log` and `build/adapter-benchmark.md`.

## Reproducible performance scenario

The headless fixture builds a loaded-world matrix of 0, 1, 3 and 8 stationary tornadoes against 0, 64 and 256 actual cows. Storms have radius 32, strength 2.5, destruction off, and the default 48-entity processing budget. Each case warms up for 100 adapter calls and measures 400 calls with `System.nanoTime`, then reports p50, p95 and maximum milliseconds. Zero-storm rows provide the adapter baseline with the same entities.

The generated report records the Java runtime, JVM arguments, operating system, CPU environment, available logical processors, maximum heap, Minecraft/Loader/API versions and actual fixture counts. Keep it with a tested commit when publishing measurements.

This is a hot-cache adapter burst measurement. Calls run at fixed world time; entity physics, block ticks and client rendering do not advance between calls. It is **not** whole-server MSPT/TPS, a multiplayer capacity estimate, a memory allocation profile, a graphics benchmark, or evidence of superiority over another mod. Destruction-on terrain sampling and network delivery need separate sustained measurements. Shared host load and JIT/GC pauses can affect the results. Re-run multiple times for release comparisons.

The separate synthetic kernel benchmark is reproducible with:

```sh
./gradlew --no-daemon simulationBenchmark
```

It runs with a fixed 256 MiB heap, 5,000 warmup iterations and 20,000 measured iterations for 1 storm/200 synthetic positions and 4 storms/800 positions, with eight synthetic shelter queries per iteration. Those positions are not Minecraft entities. It omits chunk access, entity queries, networking, persistence, destruction and rendering; use the headless matrix for adapter measurements and a sustained server scenario for gameplay performance. Its output is independent of the recorded headless results below.

## Manual checks required before calling this a stable release

The automated packet codec test verifies the wire representation; it does not establish that two remote clients receive/render identical state. Complete and record these checks separately:

- Two clients join, leave, change dimensions and reconnect during each storm stage; check removal of old visuals and late-join snapshots.
- View a full normal and accelerated lifecycle, then check funnel continuity, storm motion and HUD legibility at common GUI scales.
- Confirm particle reductions/minimal settings and the mod's zero-budget setting, sound volume/mute, HUD toggle and no flashes/camera shake.
- Build shelter with full cubes, doors, slabs, glass, stairs and damaged walls; document geometry limitations rather than implying fluid dynamics.
- Save and fully restart an ordinary dedicated server with active storms and player-build protection, then compare the resumed state. NBT roundtrip alone does not test operating-system crash recovery.
- Enable destruction in a disposable world; inspect world-border edges, configured height limits, player-built chunks and neighboring containers. Check third-party claim integrations separately; none is assumed.
- Run a sustained 10-minute dedicated-server scenario with clients and realistic terrain, recording whole-server MSPT/TPS, chunk count, entity count, heap/GC, network bytes and client FPS/GPU details.

Until these checks have actual recorded results, multiplayer visual synchronization, aesthetic quality, sustained low-end performance and ordinary server restart/crash recovery remain unverified.

## API references

- [Fabric GameTest entrypoint and empty structure](https://maven.fabricmc.net/docs/fabric-api-0.116.7%2B1.21.1/net/fabricmc/fabric/api/gametest/v1/FabricGameTest.html)
- [Minecraft 1.21.1 bounded spatial collection](https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.3/net/minecraft/world/World.html#collectEntitiesByType(net.minecraft.util.TypeFilter,net.minecraft.util.math.Box,java.util.function.Predicate,java.util.List,int))
- [Fabric automated-testing overview](https://docs.fabricmc.net/1.21.1/develop/automatic-testing)

## Recorded results

On **2026-10-28, America/New_York**, the local `build` completed with **18 JUnit tests passing**, zero failures and zero skipped tests across five test classes. The dedicated headless integration fixture also passed; `gametest-results.xml` records the run at `2026-10-29T01:47:57Z` (October 28 locally), and the server log confirms all required tests passed.

Hardware/runtime: Intel Core i5-11400F at 2.60 GHz, 12 logical processors, Windows 11 amd64, Microsoft OpenJDK 21.0.11+10-LTS. The test JVM reported maximum heap 8,560,574,464 bytes. Minecraft 1.21.1, Fabric Loader 0.20.1, Fabric API 0.116.7+1.21.1, Loom 1.11.8 and Gradle 8.14.3 were used.

- Maximum eight-storm snapshot: **542 bytes**, including exposure and entity-budget fields; roundtrip and invalid-count rejection passed.
- Real loaded-world shelter: enclosed roof/walls exposure **0.0**; after removing one entire wall, exposure **0.20625**. The production server exposure adapter matched the geometry result in both cases.
- NBT roundtrip/resume, unknown-schema raw data preservation, damage caps and protected blocks/chunks, protection metadata overflow lock, siren warning/redstone/reset, bounded entity collection, zero-force velocity preservation and no remote chunk loading passed.
- The exact 12-row timing matrix and JVM arguments are in [BENCHMARK_2026-10-28.md](BENCHMARK_2026-10-28.md). At the default three-storm cap, p95 adapter time was **0.0464 ms with 64 cows** and **0.0123 ms with 256 cows**. The eight-storm/256-cow case had p95 **0.0369 ms** and a **12.7648 ms** maximum outlier. These are hot-cache burst measurements; their non-monotonic scaling is not evidence that additional entities improve performance.

A Minecraft client was being launched to its title menu on the same machine during the headless run, so CPU, memory, disk and GPU-related host activity may have interfered. No clients were connected to the test server. The entity query cap means a crowded scene may repeatedly select a subset of nearby entities; the fixture counts are total actual cows, not a promise that every cow receives wind each tick.

GitHub Actions now defines Java 21 build, unit-test and headless integration jobs and uploads the JARs and test reports. The first clean remote run passed; see the revision record below. Manual checks listed above remain unverified unless separately recorded.

### Client launch smoke check

The development client loaded the mod and resources, reached the title screen, and entered a new creative superflat singleplayer world. The user stopped Computer Use with Escape before receiver or funnel inspection, and no subsequent UI inputs were sent. This confirms client startup and world entry only. HUD/funnel appearance, live snapshot display, sound and survival behavior remain unverified.


### Final revision and clean CI

A final local build plus all 18 JUnit tests and the headless fixture passed again after collection-before-filtering bounded the spatial candidate query. The rerun is recorded in [BENCHMARK_FINAL_2026-10-28.md](BENCHMARK_FINAL_2026-10-28.md): three storms/64 actual cows p95 0.0304ms, three/256 p95 0.0123ms; these remain hot-cache adapter-only timings. The original matrix/outlier is retained above.

The initial repository revision 0084c83 also passed a clean Ubuntu/Java21 [GitHub Actions run](https://github.com/Saltycrook1/mod/actions/runs/40803112841), including wrapper verification, compilation, all 18 unit tests, headless server integration, and artifact/report upload. Client presentation and real two-client checks remain pending.

