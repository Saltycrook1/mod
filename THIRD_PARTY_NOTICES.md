# Asset and dependency notices

Stormward's source, original 16×16 receiver/shelter/siren textures, models and data resources are provided under the repository's [MIT license](LICENSE), copyright 2026 Stormward contributors. The textures were produced procedurally for this project; no external image pack or weather-mod assets are included.

Cloud/smoke particles, the elytra sound, bell sound, fonts and model parents are referenced by Minecraft resource identifiers and supplied by the user's installed game. Minecraft game assets and binaries are not included in the mod JAR and remain the property of their respective owners. This is an independent Minecraft mod, not an official Mojang or Microsoft product.

| Component | Use | Upstream license / notice |
| --- | --- | --- |
| Fabric Loader | Separately installed runtime | [Apache-2.0](https://github.com/FabricMC/fabric-loader/blob/master/LICENSE) |
| Fabric API | Separately installed runtime API | [Apache-2.0](https://github.com/FabricMC/fabric-api/blob/1.21.1/LICENSE) |
| Fabric Loom | Build plugin | [MIT, FabricMC](https://github.com/FabricMC/fabric-loom/blob/dev/1.11/LICENSE) |
| Yarn mappings | Development names | [CC0-1.0](https://github.com/FabricMC/yarn/blob/1.21.1/LICENSE) |
| Gradle / Gradle Wrapper | Build tooling; wrapper files included in source | [Apache-2.0 and distribution notices](https://github.com/gradle/gradle/blob/v8.14.3/LICENSE) |
| JUnit 5 | Test dependency; not shipped in release JAR | [EPL-2.0](https://github.com/junit-team/junit5/blob/r5.11.4/LICENSE.md) |

The build downloads Minecraft and its runtime libraries, including Gson, through the development toolchain. These are not shaded into Stormward. Their respective distributions retain their own licenses and notices. Development test fixtures are excluded from the release JAR.

Public descriptions of [Weather, Storms & Tornadoes](https://www.curseforge.com/minecraft/mc-mods/weather-storms-tornadoes) and [Weather & Tornadoes Remastered](https://modrinth.com/mod/weather-and-tornadoes-remastered) informed the design research. This repository does not incorporate their source, names, branding, textures or sounds; no endorsement or compatibility is implied.

The Gradle distribution license and notice are retained as [GRADLE_LICENSE](gradle/GRADLE_LICENSE) and [GRADLE_NOTICE](gradle/GRADLE_NOTICE). Original sprite sources are in scripts/generate_client_assets.py (Python standard library only).
