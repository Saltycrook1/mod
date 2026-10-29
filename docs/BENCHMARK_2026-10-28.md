# Headless loaded-world adapter burst benchmark

Java: 21.0.11+10-LTS (OpenJDK 64-Bit Server VM)

OS: Windows 11 10.0 amd64

CPU environment: Intel64 Family 6 Model 167 Stepping 1, GenuineIntel; logical processors: 12

Heap max bytes: 8560574464; JVM args: [-Dfabric-api.gametest, -Dfabric-api.gametest.report-file=C:\Users\maxru\Documents\Codex\2026-10-28\can\mod\build\gametest-results.xml, -Dfabric.dli.config=C:\Users\maxru\Documents\Codex\2026-10-28\can\mod\.gradle\loom-cache\launch.cfg, -Dfabric.dli.env=server, -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant]

Minecraft 1.21.1; Fabric Loader 0.20.1; Fabric API 0.116.7+1.21.1; all storms stationary radius 32 strength 2.5. Destruction off, 48 entity processing budget, no connected clients.

100 warmup calls + 400 measured calls per scenario. Times cover StormManager.tick only; calls run back-to-back at fixed world time. This measures a loaded-world adapter burst, not whole-server MSPT/TPS, rendering FPS, allocation, network delivery, or a competitor comparison. Entity physics does not advance during a scenario, and caches stay hot.

| Storms | Cows | p50 ms | p95 ms | max ms |
|---:|---:|---:|---:|---:|
| 0 | 0 | 0.000800 | 0.001300 | 0.074700 |
| 1 | 0 | 0.009500 | 0.015500 | 0.161200 |
| 3 | 0 | 0.008900 | 0.018300 | 0.112600 |
| 8 | 0 | 0.007100 | 0.009000 | 0.027600 |
| 0 | 64 | 0.000200 | 0.000300 | 0.001000 |
| 1 | 64 | 0.027700 | 0.036700 | 0.091200 |
| 3 | 64 | 0.028400 | 0.046400 | 0.119500 |
| 8 | 64 | 0.033500 | 0.046200 | 0.122000 |
| 0 | 256 | 0.000200 | 0.000300 | 0.001100 |
| 1 | 256 | 0.013000 | 0.016000 | 0.043400 |
| 3 | 256 | 0.010800 | 0.012300 | 0.025700 |
| 8 | 256 | 0.024300 | 0.036900 | 12.764800 |

## Recorded run context

Date: 2026-10-28 (America/New_York); the XML report timestamp is 2026-10-29T01:47:57Z. Hardware identified by the host: Intel Core i5-11400F at 2.60 GHz, 12 logical processors. Runtime vendor: Microsoft OpenJDK 21.0.11+10-LTS. The matrix and JVM details above are copied unchanged from the generated report.

A Minecraft client was being launched to its title menu on the same machine during this run. Shared CPU, memory, disk and graphics-related activity may have affected these measurements. No clients were connected to the headless server. This single local run is not an isolated-host benchmark.

The fixture contains the stated total number of actual cows. The default 48-entity processing cap can repeatedly select a nearby subset, especially for overlapping storms; these counts do not imply all cows are simulated each call. Fixed world time also keeps shelter caches hot. The 8-storm/256-cow maximum of 12.7648 ms is retained as measured; it has not been attributed to a particular cause.
