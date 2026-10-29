# Headless loaded-world adapter burst benchmark

Java: 21.0.11+10-LTS (OpenJDK 64-Bit Server VM)

OS: Windows 11 10.0 amd64

CPU environment: Intel64 Family 6 Model 167 Stepping 1, GenuineIntel; logical processors: 12

Heap max bytes: 8560574464; JVM args: [-Dfabric-api.gametest, -Dfabric-api.gametest.report-file=C:\Users\maxru\Documents\Codex\2026-10-28\can\mod\build\gametest-results.xml, -Dfabric.dli.config=C:\Users\maxru\Documents\Codex\2026-10-28\can\mod\.gradle\loom-cache\launch.cfg, -Dfabric.dli.env=server, -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant]

Minecraft 1.21.1; Fabric Loader 0.20.1; Fabric API 0.116.7+1.21.1; all storms stationary radius 32 strength 2.5. Destruction off, 48 entity processing budget, no connected clients.

100 warmup calls + 400 measured calls per scenario. Times cover StormManager.tick only; calls run back-to-back at fixed world time. This measures a loaded-world adapter burst, not whole-server MSPT/TPS, rendering FPS, allocation, network delivery, or a competitor comparison. Entity physics does not advance during a scenario, and caches stay hot.

| Storms | Cows | p50 ms | p95 ms | max ms |
|---:|---:|---:|---:|---:|
| 0 | 0 | 0.000700 | 0.001200 | 0.018100 |
| 1 | 0 | 0.008800 | 0.015200 | 0.163000 |
| 3 | 0 | 0.008100 | 0.013400 | 0.047900 |
| 8 | 0 | 0.006400 | 0.013600 | 0.043500 |
| 0 | 64 | 0.000200 | 0.000300 | 0.003600 |
| 1 | 64 | 0.020200 | 0.043000 | 0.104600 |
| 3 | 64 | 0.020200 | 0.030400 | 0.262300 |
| 8 | 64 | 0.026300 | 0.046900 | 0.084000 |
| 0 | 256 | 0.000200 | 0.000300 | 0.000800 |
| 1 | 256 | 0.012200 | 0.033500 | 0.052300 |
| 3 | 256 | 0.011300 | 0.012300 | 0.031500 |
| 8 | 256 | 0.012900 | 0.023700 | 0.100200 |

Final local validation, 2026-10-28 America/New_York (2026-10-29T01:55:27Z). Microsoft JDK21.0.11, Intel Core i5-11400F2.60GHz/12 logical CPUs. This rerun includes collection-before-filtering so excluded entities cannot expand accepted query enumeration. The development Minecraft client remained open on the same host; it was not connected to this test server. All limitations in the report above apply. The earlier matrix and its 12.7648ms outlier remain in BENCHMARK_2026-10-28.md; no comparative performance claim is made.
