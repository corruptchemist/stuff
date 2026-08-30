# PMWeather 0.17.14 — 1.21.1 NeoForge → 1.20.1 Forge port

**Status: incomplete. This does not compile yet, and nothing here has been run or tested.**

Read this file before you read anything else in the directory.

---

## 1. The headline

I could not build or test the port, and I could not have. The container this ran in blocks
outbound access to every host the job needs:

| Host | Needed for | Result |
|---|---|---|
| `piston-meta.mojang.com`, `libraries.minecraft.net` | the Minecraft 1.20.1 client/server jar and its libraries | **403 at the egress proxy** |
| `maven.minecraftforge.net` | Forge 47.x, ForgeGradle, MCPConfig, SRG mappings | **403** |
| `maven.neoforged.net` | NeoForge (for diffing against the original) | **403** |
| `maven.parchmentmc.org` | parameter mappings | **403** |
| `modrinth.com`, `curseforge.com`, `maven.blamejared.com` | Veil, Sodium/Embeddium, Iris/Oculus, Create | **403** |
| `repo1.maven.org` (Maven Central) | — | reachable, but hosts none of the above |

Without the Minecraft jar, not one Minecraft-referencing class can be compiled, so
`gradlew build` was never possible, `runClient` was never possible, and no claim in this
document about runtime behaviour has been verified by execution. Everything below is
static analysis of the decompiled source.

The build also needs **JDK 17** (ForgeGradle 6 requirement); only JDK 21 is installed here.

## 2. What is actually in this directory

Source of truth was the uploaded `pmweather-1.21.1-0.17.14-alpha.jar`, decompiled with
Vineflower into 222 files / ~26,600 lines. The pristine decompile is kept at
`../reference/pmweather-1.21.1-neoforge-decompiled-dev/` so you can diff against it.

### Done and believed correct

- **Gradle workspace** — `build.gradle`, `settings.gradle`, `gradle.properties` for
  MC 1.20.1 / Forge 47.3.0, official mappings, ForgeGradle 6, MixinGradle, Java 17 toolchain.
- **`META-INF/mods.toml`** — rewritten from `neoforge.mods.toml` into the Forge 1.20.1
  schema (`mandatory=` instead of `type="required"`, no `requiredMods` on mixin blocks,
  `loaderVersion` `[47,)`).
- **Access transformer** — carries over unchanged; both `BiomeColors` fields exist on 1.20.1.
- **`pack.mcmeta`** — pack_format 15 (resources) / 12 (data) for 1.20.1.
- **Datapack layout** — 1.21's singular directories renamed to 1.20.1's plural ones
  (`recipe`→`recipes`, `loot_table`→`loot_tables`, `tags/block`→`tags/blocks`,
  `tags/item`→`tags/items`).
- **Recipe JSON** — 45 `"result": {"id": …}` blocks converted back to `"result": {"item": …}`.
- **Tag namespaces** — `data/neoforge`→`data/forge`, `#c:`→`#forge:` (13 files),
  `neoforge:remove_features`→`forge:remove_features`.
- **NeoForge→Forge API migration** across 39 source files: 56 distinct package/type moves
  (`net.neoforged.bus.api.*`→`net.minecraftforge.eventbus.api.*`, `NeoForge.EVENT_BUS`→
  `MinecraftForge.EVENT_BUS`, `ModConfigSpec`→`ForgeConfigSpec`, `EventHooks`→
  `ForgeEventFactory`, `ItemAbilities`→`ToolActions`, the whole event package, …).
- **`ResourceLocation`** — 32 call sites of the 1.21 static factories
  (`fromNamespaceAndPath`, `parse`, `withDefaultNamespace`) rewritten to 1.20.1 constructors.
- **`NbtUtils.readBlockPos`** — 12 call sites moved from the 1.21 two-arg `Optional`-returning
  form back to 1.20.1's single-arg form.
- **Networking layer — fully ported by hand.** The four files in `networking/` no longer use
  any 1.21-only API. NeoForge's `CustomPacketPayload` + `StreamCodec` + `PayloadRegistrar`
  are replaced with a Forge `SimpleChannel`, explicit `FriendlyByteBuf` encoders/decoders,
  and `consumerMainThread` handlers; `PacketDistributor` calls rewritten to the 1.20.1
  `.with(...)` form. Wire format is unchanged.

### Not done — 47 of 222 files still need hand porting

**a) No Forge 1.20.1 equivalent exists (needs redesign, not translation)**

| Thing | Files | What it needs |
|---|---|---|
| NeoForge **data attachments** | `data/DataAttachments.java` | Forge capabilities or a custom `SavedData` |
| **Ticket controllers** (`RegisterTicketControllersEvent`, `TicketHelper`) | `level/ChunkLoading.java` | `ForgeChunkManager` — different model |
| **`DataComponents`** (1.20.5+ item components) | `item/component/ModComponents.java` | back to `ItemStack` NBT |
| **`RegisterNamedRenderTypesEvent`** | `event/ModBusClientEvents.java`, `mixin/RegisterNamedRenderTypesEventMixin.java` | no 1.20.1 counterpart at all |
| **`ConfigurationScreen` / `IConfigScreenFactory`** | `PMWeather.java` | `ConfigScreenHandler` |
| **NeoForge data maps** (furnace fuels JSON) | `data/forge/data_maps/` | `IForgeItem#getBurnTime` in code |
| `DeferredBlock`/`DeferredItem`/`DeferredHolder` | `ModBlocks`, `ModItems`, `MultiBlocks`, `ModParticleTypes` | `RegistryObject<T>` |

**b) Vanilla 1.21 render internals that moved**

`MeshData` (→ `BufferBuilder.RenderedBuffer`), the `VertexFormatElement` record/registry
redesign, `SectionCompiler` (does not exist on 1.20.1 — the 1.20.1 equivalent lives in
`ChunkRenderDispatcher`), `ChunkRenderTypeSet`, `HolderLookup.Provider` on BlockEntity
save/load. Affects `render/RadarRenderer`, `render/CustomLightningRenderer`,
`render/SoundingViewerRenderer`, `particle/ParticleManager`, `mixin/SectionCompilerMixin`,
`mixin/BufferBuilderMixin`, `mixin/VertexFormatElementMixin`, `block/entity/…`, `data/…`.

**c) Veil — NOT a blocker (corrected)**

My first pass called this fatal. It is not. FoundryMC/Veil has a **`1.20` branch**
(commit `3592ddd`, Dec 2024) targeting `minecraft_version=1.20.1` / `forge_version=47.3.1`,
LGPL-3.0, with a complete `forge/` module. Veil 1.0.0 provides 6 of the 7 hooks PMWeather
uses — `onVeilRendererAvailable`, `preVeilPostProcessing`, `postVeilPostProcessing`,
`onVeilRegisterFixedBuffers`, `onVeilRegisterBlockLayers`, `onVeilRenderTypeStageRender` —
plus `PostProcessingManager`, `AdvancedFbo`/`FramebufferManager`, `ShaderManager`/
`ShaderProgram`, `VeilRenderSystem`, `CameraMatrices`, and `FastNoiseLite` at the same FQN
`SeasonalPlantBlock` imports. The one missing hook, `onVeilShaderCompile`, forwards to
`PMWPostShader.onCompile`, which is an empty method nothing overrides — a no-op in 0.17.14.

Two real caveats: the `1.20` branch has **no release tags**, so you build and jar-in-jar it
yourself (LGPL-3.0 allows this; you must publish source) and maintain an branch whose last
commit is Dec 2024. And Veil 1.0.0's uniform API is flat — ~55 `getUniformSafe("x").setY(v)`
sites collapse to `setY("x", v)` — while `renderStage` is absent from its
`CompositePostPipeline` codec, so `sky.json`/`volumes.json` must be driven from
`onVeilRenderTypeStageRender` instead (~50 LOC).

**d) The one thing that genuinely cannot be ported: Sodium**

The 10 Sodium mixins (`compat/sodium/`, 662 LOC) target Sodium 0.6/0.8
(`net.caffeinemc.mods.sodium.*`). 1.20.1 Forge has no Sodium — only Embeddium/Rubidium on
the Sodium 0.5 lineage, with a different chunk vertex format, no FRAPI mesh path, and a
closed `ChunkMeshAttribute` enum. Retargeting is ~600 LOC that cannot be validated without
running the game. It is ~2.5% of the mod and cleanly gated, so deleting the directory is the
right call. Iris → Oculus is the same story for 54 LOC.

**e) Landmines that fail silently on Forge 1.20.1**

Found by the analysis, and the first three are **fixed in this tree**:

- `private static @SubscribeEvent` handlers are dropped without warning (Forge scans
  `getMethods()`). Three existed — `ClientConfig.onLoad`, `ServerConfig.onLoad`,
  `ChunkLoading.registerTicketControllers`. Both config loaders being dead would have left
  every config field at its Java default and stopped weather spawning. Now public.
- Forge 1.20.1 `mods.toml` has **no `[[mixins]]` block**; configs live in the jar manifest's
  `MixinConfigs` attribute. Moved.
- Forge 1.20.1 access transformers match **SRG** names, not Mojang names. The AT now carries
  SRG ids (still to be verified against your mappings — see the comment in the file).
- Still outstanding: `requiredMods` mixin gating must be reimplemented as an
  `IMixinConfigPlugin.shouldApplyMixin` consulting `ModList.isLoaded`; a refmap is mandatory
  under SRG runtime; `ItemProperties.register` clamps to [0,1] on 1.20.1, which would collapse
  the calendar item's 12 month models into one.

## 3. Honest effort estimate

A 7-subsystem analysis with adversarial verification of every claimed blocker landed on:

- **~74% mechanical** (~19,600 LOC). `weather/` alone is 5,984 LOC and ~91% untouched — the
  storm model is Simplex noise + JOML + `CompoundTag`, identical across versions. `command/`
  is ~99% portable (Brigadier is unchanged).
- **~20% needs rewriting** (~5,300 LOC touched → ~3,500-4,000 new): capabilities, networking,
  the chunk-layer mixins, the particle render loop, the Veil uniform/scheduling layer,
  `tickPrecipitation`, data components.
- **~6% cannot be ported** (~1,000-1,600 LOC): `compat/sodium`, `compat/iris`, `compat/sable`
  (no 1.20.1 build exists in any version), `compat/powergrid`, the config screen.

**Total: 66-92 developer-days — call it ~75 dev-days, 15 weeks for one expert**, or 10-12
weeks for two (the render track parallelises cleanly against the sim/data/network track).
Add 15-25 days if Embeddium support is required, with a real chance of never reaching parity.

The single riskiest item is **swaying grass**: 8-12 days of mixin work against
`ChunkRenderDispatcher$RenderChunk$RebuildTask#compile` and `BufferVertexConsumer`, for a
cosmetic effect, where stride/offset bugs only show up as garbled terrain in a running game.
Ship v1 without it — repoint the 142 model JSONs that hardcode
`"render_type": "pmweather:swaying_cutout"` to `minecraft:cutout` — and add it later.

**What a reduced-scope 1.20.1 build keeps:** the whole atmospheric simulation, storms, radar,
wildfire, seasons, volumetric clouds, sky replacement and sounds. **What it loses:**
Sodium/Embeddium support, shaderpack-compatible swaying, Sable and PowerGrid integration,
the config GUI, and (if deferred) swaying grass.

## 4. Worth knowing before you spend that time

**The author already ships a 1.20.1 build.** ProtoManly's Weather `0.16.4-1.20.1-alpha`
exists on Modrinth. If 0.16.4 covers what you need, using it is enormously cheaper than
porting 0.17.14, and it will be correct where a hand port would not be. I could not open
the Modrinth page from this container to confirm its loader and feature delta — check it
first.

## 5. To continue

1. On a machine with JDK 17 and normal network access: `./gradlew build` and start from the
   compiler errors. Expect them in the ~47 files listed above.
2. Work in this order: registries (`RegistryObject`) → data attachments → config screen →
   BlockEntity save/load → particles → then rendering last.
3. Re-enable each disabled mixin config only once its target library is settled.
