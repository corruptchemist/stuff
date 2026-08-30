<!-- Produced by an 18-agent analysis of the decompiled 1.21.1 sources, with every
     claimed blocker adversarially re-verified against the real source. Nothing in it
     was compiled or run; see PORTING_STATUS.md section 1. -->

# PMWeather 0.17.14 — 1.21.1/NeoForge → 1.20.1/Forge 47.x: Consolidated Port Assessment

## 1. Is a faithful port feasible?

**Yes — with one named exclusion. There is no true fatal blocker.** Every "fatal" verdict in the input surveys was refuted against real source. What remains is a large, tedious, high-touch job on a codebase of **26,626 Java LOC / 222 files** (verified), plus 2,263 lines of GLSL across 18 shader files and ~390 JSON assets.

The decisive correction, because three separate surveys got it wrong in the same direction: **Veil is not a blocker.** I verified the FoundryMC/Veil `1.20` branch on disk at `/home/user/veil120` — commit `3592ddd` (2024-12-27), `gradle.properties`: `minecraft_version=1.20.1`, `forge_version=47.3.1`, `forge_version_range=[47.2,)`, licensed **LGPL-3.0** (`LICENSE`; the `license=All Rights Reserved` line in gradle.properties is an unfilled mods.toml metadata field), with a complete `forge/` module including `ForgeVeilEventPlatform.java`. Veil 1.0.0 provides:

- `foundry.veil.platform.VeilEventPlatform` declaring `onVeilRendererAvailable`, `preVeilPostProcessing`, `postVeilPostProcessing`, `onVeilRegisterFixedBuffers`, `onVeilRegisterBlockLayers`, **and `onVeilRenderTypeStageRender`** — 6 of the 7 hooks PMWeather uses, four of them byte-compatible with `PMWeather.java:70-74`
- `PostProcessingManager.getPipeline(ResourceLocation)` / `runPipeline(PostPipeline, boolean)` / `getContext()`, all public
- `AdvancedFbo` + `FramebufferManager.setFramebuffer`, `ShaderManager`/`ShaderProgram`, `VeilRenderSystem`, `CameraMatrices`
- `api/client/render/deferred/light/{Light,PointLight,AreaLight,LightRenderer}` with `addLight`/`removeLight`
- `foundry.veil.api.util.FastNoiseLite` at the identical FQN used by `block/SeasonalPlantBlock.java`

Only `onVeilShaderCompile` is genuinely absent, and its handler (`ModShadersVeil.VeilCompileShaders`) forwards to `PMWPostShader.onCompile`, which **is an empty method neither SkyShader nor VolumeShader overrides** — it is a no-op in 0.17.14. Similarly, `weather/effects/ClientLightning.lightHandle` and `PowerFlash.lightHandle` are **never assigned anywhere in the mod** (grep confirms writes only to `null`); the "Veil deferred-light blocker" is dead code. Delete both fields.

The one thing that genuinely cannot be ported is the **Sodium integration** (`compat/sodium/`, 662 LOC): it targets Sodium 0.6/0.8 (`net.caffeinemc.mods.sodium.*`), and 1.20.1 Forge has no Sodium at all — only Embeddium/Rubidium on the Sodium 0.5 lineage with a different chunk vertex format, no FRAPI mesh path, and a closed `ChunkMeshAttribute` enum. That is ~2.5% of the mod and it is cleanly gated.

## 2. True blockers, ranked (post-verification severities)

**None are fatal.** Ranked by actual project risk:

| # | Item | Real severity | Why |
|---|---|---|---|
| 1 | **Veil 1.0.0 is source-only for 1.20.1** | Project risk, not a code blocker | The clone carries the `1.20` branch with **zero release tags**; there is no evidence of a published 1.20.1 Forge binary. You must build it, jar-in-jar it, and maintain it. LGPL-3.0 permits redistribution as a separate jar; the burden is ongoing maintenance of an abandoned branch (last commit Dec 2024), not legality. |
| 2 | **Sodium/Embeddium terrain integration** | Cannot be ported faithfully — drop | 10 mixins + `PMWChunkVertex` bound to Sodium 0.6/0.8 internals. Retargeting to Embeddium 0.3.x means re-deriving a 20-byte compact vertex format, replacing the FRAPI `MutableQuadViewImpl` path with `ModelQuadView`/`writeGeometry`, and mixin-extending a closed enum — ~600 LOC that **cannot be validated without running the game**. Gated behind `requiredMods=["sodium"]` and `ClientConfig.swayingGrass`, so deletion is a directory removal with no core edits (verified: the only external reference is `compat/iris/mixin/SodiumProgramsMixin.java`, which also goes). |
| 3 | **Custom chunk render layer on 1.20.1's BufferBuilder** | Severe, non-optional, undesk-verifiable | `SectionCompiler`, `ByteBufferBuilder`, `beginElement(…)→long`, `endLastVertex`, `addVertex(FFF…)` are all 1.21-only. Must be re-authored against `ChunkRenderDispatcher$RenderChunk$RebuildTask#compile` + `BufferVertexConsumer.nextElement/putShort/putByte`, plus a **new** mixin on `RenderChunk#beginLayer` that has no 1.21 counterpart. ~350-400 LOC. Attribute stride/offset bugs here manifest as garbled terrain, only findable with a debugger. **142 model JSONs** (55 `minecraft`, 36 `expandeddelight`, 32 `farmersdelight`, 13 `pmweather`, 6 `fruitsdelight`) hardcode `"render_type": "pmweather:swaying_cutout"` and must be repointed to `minecraft:cutout` if this is dropped. |
| 4 | **Data attachments → Forge capabilities** | Severe but mechanical | **59 call sites across 18 files** (verified). One `ICapabilitySerializable<CompoundTag>` on `AttachCapabilitiesEvent<LevelChunk>` + a static facade. Two traps: (a) `hasData()` means "explicitly set" while an attached cap is always present — needs per-field `initialized` flags for the first-load paths in `LevelUpdater:28`, `GameBusEvents:341/406/753`, `MoistureHandler:34`, `FoliageColors:151`, `WeatherStationBlock:112`; (b) `ChunkStatus.EMPTY` reads need an `ImposterProtoChunk.getWrapped()` unwrap (~15 LOC), **not** a SavedData redesign. Client sync needs nothing new — the mod already hand-rolls it via `GameBusEvents.onChunkSent` → `ModNetworking`. |
| 5 | **Networking layer** | Total rewrite, fully known | `CustomPacketPayload`/`StreamCodec`/`PayloadRegistrar`/`RegistryFriendlyByteBuf` are 1.20.5+. Rebuild as one `SimpleChannel` + `registerMessage` + `PacketDistributor.*.with(...)`. ~220 LOC; the ~120 LOC of handler bodies port unchanged. |
| 6 | **Mid-frame post-pipeline scheduling + flat uniform API** | Moderate | `renderStage` is not in Veil 1.0.0's `CompositePostPipeline.CODEC`, so `sky.json`/`volumes.json` would silently run at end-of-frame and the sky pass would paint over the world. Fix: drop `post.add(id)`, and drive both from `onVeilRenderTypeStageRender` at `AFTER_SKY`/`AFTER_WEATHER` via `getPipeline` + `runPipeline(p, true)`, calling `PreVeilPostProcessing` by hand. ~50 LOC. Separately, **55 `getUniformSafe("x").setY(v)` sites** must flatten to `setY("x", v)` — pure sed. |
| 7 | **NeoForge auto-generated config screen** | Feature loss, zero engineering | `ConfigurationScreen` has no Forge 1.20.1 counterpart. ~40 client options become TOML-only unless users install Configured. Accept it. |
| 8 | **Silent-failure landmines** | Moderate, cheap, catastrophic if missed | (a) `private static @SubscribeEvent` handlers are **silently dropped** by Forge 1.20.1's `getMethods()` scan — `ClientConfig:95`, `ServerConfig:351`, `ChunkLoading:34` must become public or every config field stays at its Java default and weather never spawns; (b) `accesstransformer.cfg` must use **SRG names** (`f_108790_`), not `GRASS_COLOR_RESOLVER`; (c) Forge 1.20.1 `mods.toml` has **no `[[mixins]]` block at all** — configs go in the `MixinConfigs` manifest attribute, and `requiredMods` gating must be reimplemented as `IMixinConfigPlugin.shouldApplyMixin` + `ModList.isLoaded`; (d) SRG runtime means a refmap is mandatory. |

Also real but routine: `ServerLevel#tickPrecipitation` doesn't exist (fold ~180 LOC into the existing `tickChunk` hook and cancel vanilla's `iceandsnow` block); `SoundEngine#calculateVolume(float, SoundSource)` doesn't exist (retarget to `calculateVolume(SoundInstance)`); `EntityTickEvent` doesn't exist (split into `LivingTickEvent` + a mixin on `Entity#tick`); `ParticleEngine#iterateParticles` doesn't exist (`@Accessor("particles")`, ~25 LOC); `ItemProperties.register` clamps to [0,1] on 1.20.1, which would silently collapse the calendar item's 12 month models to one.

## 3. Effort for an expert with a working toolchain

The surveys sum to 65 dev-days, but that number is both inflated (compat-mixins' 22 days assumed a fatal Veil blocker and a Sable rewrite that is a 10-line deletion) and incomplete (nobody budgeted toolchain setup, asset migration, integration debugging, or playtesting).

| Phase | Days |
|---|---|
| Toolchain, build.gradle, mods.toml/AT/refmap/mixin-plugin scaffolding, Veil 1.0.0 build+JiJ | 4-6 |
| Java 21→17 + 1.20.5-churn mechanical sweep (~150 `Math.clamp`, `getFirst/reversed`, `ofFullCopy`, `ResourceLocation` ctors, `NbtUtils.readBlockPos`, `protected`→`public`, codec deletion, `useItemOn` merge, `BlockSetType`, `DoorBlock` args) | 6-8 |
| Registries: `DeferredRegister`→`RegistryObject`, ~98 `.get()` insertions, creative tabs, data components→NBT | 4-5 |
| Capabilities layer + 59 call sites + `hasData` semantics | 5-7 |
| Networking (SimpleChannel) + `PacketDistributor` facade | 3-4 |
| Events/config/commands/chunk tickets (`ForgeChunkManager`) | 4-5 |
| Vanilla mixins (12 easy + `tickPrecipitation` re-host + `SoundEngine` + `EntityTick` + `ParticleEngine`) | 5-7 |
| Particle engine clone: 1.20.1 buffer API, render types, vertex chain | 4-5 |
| Veil/shaders: flat uniforms, stage-driven pipelines, `VertexFormatCodec`, GLSL include fixes, filter workaround | 6-8 |
| **Swaying-grass chunk layer (vanilla path only)** | **8-12** |
| Create + DistantHorizons compat retarget; delete Sodium/Iris/Sable/PowerGrid | 3-4 |
| Datapack/asset migration (`loot_table`→`loot_tables`, `recipe`→`recipes`, `tags/block`→`tags/blocks`, recipe `result` format, biome_modifier `c:`→`forge`, `stripped_logs`/`stripped_woods` tags, 142 model JSONs) | 2-3 |
| Integration debugging + playtest to parity | 12-18 |

**Total: 66-92 developer-days.** Call it **~75 dev-days / 15 weeks for one expert**, or **10-12 weeks for two** (the render/Veil track parallelizes cleanly against the sim/data/network track). Add **+15-25 days** if Embeddium terrain support is required, and that work carries a real chance of never reaching parity.

## 4. Port fractions

Weighted by verified per-package LOC:

- **~74% mechanical** (≈19,600 LOC). Dominated by `weather/` (5,984 LOC, ~91% untouched — the Simplex/JOML/CompoundTag storm model is byte-identical across versions, so storm behaviour reproduces exactly), `command/` (1,107 LOC, ~99% — Brigadier is unchanged), `util/`, `sound/`, `seasons/`, `multiblock/`.
- **~20% needs rewriting** (≈5,300 LOC touched, producing ~3,500-4,000 LOC of new code): capabilities, networking, the chunk-layer mixin cluster, the particle render loop, the Veil uniform/scheduling layer, `tickPrecipitation`, data components.
- **~6% cannot be ported** (≈1,000-1,600 LOC): `compat/sodium` (662), `compat/iris` (54), `compat/sable` (190 — pure deletion, author already shipped vanilla fallbacks at `SableMod:39,43`), `compat/powergrid` (~110), Veil-4.x-only glue, and the config screen.

## 5. Realistic reduced-scope 1.20.1 build

Ship **"PMWeather 0.17.14-forge, no-optimization-mod edition."** Cut lines, in order of what you lose:

**Dropped outright (declare incompatible, don't half-support):**
- Sodium/Embeddium/Rubidium — declare `embeddium`/`rubidium` incompatible in mods.toml (the mod already uses exactly this pattern for `sodium (,0.8.12-beta.1)` and `immersive_portals`). *Verify first whether Embeddium's `DefaultMaterials.forRenderLayer` tolerates a modded chunk layer; if not, incompatibility is mandatory, not optional.*
- Iris/Oculus — keep only `WorldRenderingPhaseMixin` retargeted to `net.coderbot.iris.pipeline.WorldRenderingPhase`; drop `SodiumProgramsMixin`. Swaying never survived a loaded shaderpack anyway.
- Sable (no 1.20.1/Forge build exists in any version) and Create: Power Grid (no verified 1.20.1 build; `PowerGridHandler.lightning` is already an empty no-op). Losses: wind forces on physics sublevels, wind-driven power-line snapping.
- The in-game config screen.

**Kept, possibly staged:**
- Everything in `weather/`, `seasons/`, `block/`, `particle/`, `sound/`, `command/`, `render/RadarRenderer`, the volumetric cloud/tornado raymarcher and sky replacement, DistantHorizons (~140 of 180 LOC port cleanly; DH 2.x ships for 1.20.1 Forge), Create windmill-speed-from-wind (Create 0.5.1f keeps `AllBlockTags.WINDMILL_SAILS` and the `WindmillBearingBlockEntity` methods).
- **Swaying grass is the one feature to consider deferring to a 1.1 release.** It is 8-12 days of undesk-verifiable mixin work for a cosmetic effect. Ship v1 without it — repoint the 142 model JSONs to `minecraft:cutout` — and add it once the rest is stable. This alone converts the riskiest item in the project into a follow-up.
- Two cosmetic degradations to accept silently: the `CloudBlurSampler` per-sampler blur (set `"linear": true` on the clouds framebuffer attachment and `texelFetch` the sharp tap), and per-strike dynamic lightning illumination (already dead code).

Net user-visible loss vs. 1.21.1: no Sodium/Embeddium support, no shaderpack-compatible swaying, no Sable/PowerGrid integration, no config GUI, and (if deferred) no swaying grass in v1. **The atmospheric simulation, storms, radar, wildfire, seasons, volumetrics and sounds all survive intact.**

## 6. Ordered work plan

1. **Prove the dependency first.** Build Veil 1.0.0's `forge` module from `/home/user/veil120` against Forge 47.3.1, confirm it loads in a dev client, and decide the shipping model (jar-in-jar under LGPL-3.0 + published source). *If this fails, stop — everything downstream depends on it.* Also pin Embeddium/Oculus/Create/DH versions now and record the exact package layouts.
2. **Scaffold the build.** ForgeGradle 6, Java 17 toolchain, `mods.toml` (`loaderVersion="[47,)"`, per-dependency `mandatory`), `MixinConfigs` manifest attribute, `compatibilityLevel: JAVA_17`, refmap generation, `minecraft { accessTransformer = … }` with **SRG** names, MixinExtras 0.3.x (Forge 47.2+ or JiJ). Write the shared `IMixinConfigPlugin` for per-mod gating.
3. **Amputate first, port second.** Delete `compat/sodium`, `compat/iris/mixin/SodiumProgramsMixin`, `compat/sable`, `compat/powergrid`, `mixin/PostChainMixin` + `interfaces/PostChainData` (unreferenced dead code), `mixin/VertexFormatCodecMixin`, `ParticleManager.reload/makeParticle/spriteSets` (~140 LOC of dead code), and both `lightHandle` fields. Fix the ~8 call sites. This removes ~1,600 LOC before you touch anything hard.
4. **Mechanical sweep, compiler-guided.** Java 21→17, `net.neoforged.*`→`net.minecraftforge.*`, `DeferredRegister`/`RegistryObject` + `.get()`, `ResourceLocation` ctors, 1.20.5 signature churn, `Blocks.SHORT_GRASS`→`GRASS`, `Tags.Blocks.NETHERRACKS`→`NETHERRACK`/`GLASS_BLOCKS`→`GLASS`. **Manually audit every `private static @SubscribeEvent`.**
5. **Capabilities.** Land `IPMWChunkData` + provider + static facade with `initialized` flags and the `ImposterProtoChunk` unwrap, then swap all 59 sites. Ship the `pmweather:stripped_logs`/`stripped_woods` tag JSONs at the same time (12 call sites, several negated — re-verify each).
6. **Networking + data components → NBT.** SimpleChannel, the five-helper facade in `ModNetworking`, `WEATHER_BALLOON_PLATFORM` as a stack tag.
7. **Server-side gate.** Events, config, commands, `ForgeChunkManager`, `LevelSavedData`, `tickPrecipitation` re-host, `EntityTickEvent` split. **Milestone: dedicated server boots, storms spawn, `/pmweather` works, saves round-trip.** This is the real halfway point and it is worth reaching before any rendering work.
8. **Client, no shaders.** Particle engine buffer rewrite, `ParticleEngine` accessor, sound instances + `SoundEngine`/`SoundLibrary` mixins, block entity renderers, `RadarRenderer`, `ItemProperties` clamping fix. Milestone: joinable client, radar and sirens work.
9. **Veil rendering.** Flat uniform sweep (55 sites), `VeilVertexFormat` package/ctor changes, `#veil:buffer`→`#include veil:camera`, drop `#include veil:space_helper`, push `VeilRenderTime` manually, `FramebufferManager`/`AdvancedFbo` wiring, then the stage-driven pipeline dispatch. Milestone: sky replacement and volumetric clouds render at the correct point in the frame.
10. **Assets.** Datapack directory renames, recipe `result` format, biome modifier to `forge:remove_features` with `#minecraft:is_overworld`, `.png.mcmeta` blur files, 142 model JSONs.
11. **Swaying grass (optional / v1.1).** `RenderTypeMixin` + Veil's `onVeilRegisterBlockLayers`/`onVeilRegisterFixedBuffers` for layer registration, then `ModVertexFormats` via `new VertexFormat(ImmutableMap…)`, then the `RebuildTask#compile` and `BufferVertexConsumer` rewrites, then the new `RenchChunk#beginLayer` mixin. Timebox it; ship without it if it slips.
12. **Parity pass.** Same seed, same commands, 1.21.1 vs 1.20.1 side by side: storm tracks, CAPE/CINH values, radar imagery, fire spread, snow/sleet accumulation, chunk-force counts. The Simplex noise and `LegacyRandomSource` are identical across versions, so **any divergence in storm behaviour is a port bug, not version drift** — that makes this the single most valuable test you have.