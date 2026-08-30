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

**c) The real blocker: Veil**

22 files import `foundry.veil.*`. The mod requires **Veil 4.2.1**, which is 1.21.1-only —
it ships jar-in-jar as `veil-neoforge-1.21.1-4.2.1.jar`. The 1.20.1 line of Veil is
**1.0.0.x**, a different major version whose API is not source-compatible. The mod's entire
raymarched-cloud/volumetric renderer is built on Veil 4.x framebuffers, shader programs,
post pipelines, `VeilRenderSystem` and `CameraMatrices`. There is no mechanical translation
for this; it is a rewrite against a different library, or a rewrite of the renderer against
raw `PostChain`/GL.

**d) Sodium and Iris compat**

The 10 Sodium mixins target Sodium **0.8.12+ for 1.21.1** (`net.caffeinemc.mods.sodium.*`),
patching its chunk-vertex encoder and chunk renderer. On 1.20.1 Forge there is no Sodium —
the analogue is **Embeddium**, with the older `me.jellysquid.mods.sodium.*` package and a
different chunk pipeline. Same story for Iris → **Oculus**. These mixins have to be
re-authored against different internals. I have disabled these three mixin configs
(`.disabled-port` suffix) rather than leave configs that would crash at load:
`pmweather.sodium.mixins.json`, `pmweather.iris.mixins.json`, `pmweather.veil.mixins.json`.

## 3. Honest effort estimate

For someone with a working 1.20.1 Forge toolchain, starting from this tree:

- Getting it to **compile** with the renderer stubbed out: ~3–5 days.
- Restoring the **weather simulation, blocks, items, commands, networking, sound**
  (the majority of the mod's logic, which is largely plain Java): ~1–2 weeks.
- Restoring the **Veil-based volumetric renderer** on 1.20.1: this is the bulk of it —
  weeks, and it may not be reproducible at all without a Veil 4.x-equivalent on 1.20.1.
- Sodium/Iris compat via Embeddium/Oculus: additional, and optional.

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
