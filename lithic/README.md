# Lithic

A progression overhaul for **Minecraft 1.21.1 / NeoForge**, built on one rule:

> You cannot craft what you do not understand.

Difficulty here comes from mechanics, recipes and knowledge — not from mob stats.
Nothing in Lithic makes a zombie hit harder.

**Tough As Nails is a hard dependency.** Lithic's pacing is balanced around its
thirst and temperature systems and registers modifiers directly into them.

---

## The opening ten minutes

There is no punching trees. Bare hands take nothing from a log, and unarmed
swings at gated blocks hurt you.

1. Break **gravel** by hand until you have **flint**.
2. Right-click flint against **stone** to knap it. It shatters more often than
   not (55% success by default). Each attempt costs thirst.
3. Two **Flint Shards** → one **Sharp Flint**.
4. Break **leaves** or **grass** by hand for saplings/seeds → **Plant Fibre** →
   **Cordage**.
5. Sharp Flint + Cordage + Stick → **Crude Hatchet**. Only now can you cut wood.
6. Cordage + Sharp Flint + Sticks → **Crude Workbench**, in the 2×2 inventory grid.

Everything above is deliberately craftable without a crafting table, because the
player has to be able to reach the workbench from a bare spawn.

## Knowledge

Two states per research node:

- **Discovered** — a trigger fired (you picked up a thing, broke a thing, killed
  a thing). This makes the node visible and grants *insight*.
- **Learned** — you spent insight at a **Contemplation Stone** and had every
  prerequisite. Only learned nodes unlock recipes.

Read your progress with a **Tally Bone** (right-click) or `/lithic research list`.
Knowledge survives death: dying is not meant to erase a tech tree.

The tree shipped here is eight nodes:

```
knapping (free)
├── cordwork ──┬── firecraft ── kilncraft ──┐
│              └── hidework                 ├── bloomery
└── stoneworking ── oredressing ────────────┘
```

The iron chain alone is: raw iron → crushed → washed (costs a water bucket) →
bloom (8 charcoal dust) → smelt. Vanilla's raw-iron smelting recipe is deleted.

## Hostile world rules

| Rule | Effect |
|---|---|
| Cutting gate | `#minecraft:logs` need an item in `lithic:cutting_tools` |
| Striking gate | Stone and ores need an item in `lithic:striking_tools` |
| Bare hands | Punching a gated block deals damage |
| No natural regen | `naturalRegeneration` is switched off on world load |
| Labour | Knapping and gated mining add Tough As Nails thirst exhaustion |
| Night chill | Until `lithic:firecraft` is learned, nights are one TAN temperature step colder |

Every one of these is individually switchable in the config.

---

## Building

```bash
cd lithic
./gradlew build      # jar lands in build/libs/
./gradlew runClient  # dev client
```

Requires JDK 21. Pinned versions come from the official NeoForge 1.21.1 MDK:
NeoForge `21.1.249`, Parchment `2024.11.17`, Gradle wrapper `9.2.1`.

### The one thing you may need to fix first

`toughasnails_version` in `gradle.properties` is **not confirmed**. This project
was generated in an environment whose egress policy blocks every Minecraft
modding Maven (`maven.neoforged.net`, `maven.minecraftforge.net`,
`libraries.minecraft.net`, `cursemaven.com`), so the published artifact list
could not be read. `glitchcore_version` *is* confirmed — it is what Tough As
Nails' own `1.21.1` branch pins.

If dependency resolution fails, either:

- browse `https://maven.minecraftforge.net/releases/com/github/glitchfiend/ToughAsNails-neoforge/`
  and set `toughasnails_version` to the `1.21.1-*` artifact you want; or
- set `use_maven_tan=false` and drop the ToughAsNails and GlitchCore jars you
  actually run into `libs/`. That path needs no version at all.

### What has and has not been verified

Because no Minecraft toolchain could be fetched, **this code has never been
compiled or run.** What *was* done instead:

- Every NeoForge and Tough As Nails API used was checked against upstream source
  cloned from GitHub (NeoForge `1.21.1` branch, ToughAsNails `1.21.1` branch) —
  event shapes, `AttachmentType`, `PayloadRegistrar`, `SimpleTier`,
  `IMenuTypeExtension`, the `Recipe`/`RecipeSerializer` contract, and the TAN
  thirst/temperature interfaces.
- The custom crafting menu is modelled on NeoForge's own `RecipeBookTestMenu`.
- Datapack folder names were taken from NeoForge's *generated* test resources,
  which is what settled `recipe/`, `loot_table/` and `tags/block/` as singular in
  1.21.1. The hand-written plural directories elsewhere in that repo are dead.
- `javac` was run over the sources; the only diagnostics remaining are missing
  external packages. It found one real fragility — an overload pair separated
  only by its last parameter type — which was removed.
- All 105 JSON files parse, all 30 PNGs are well-formed 16×16, and every
  `lithic:` identifier, research parent, translation key, blockstate variant and
  model reference was cross-checked against the Java registries.

Expect the first real compile to still surface something. Textures are
procedurally generated placeholders and are meant to be replaced.

---

## Extending it

The interesting content is data, not Java:

- `data/lithic/lithic/research/*.json` — the research tree
- `data/lithic/recipe/*.json` — recipes, including the gated
  `lithic:crude_crafting` and `lithic:drying` types
- `data/minecraft/recipe/*.json` — vanilla recipes deleted via
  `neoforge:false`, no mixin involved
- `data/lithic/tags/{block,item}/*.json` — what needs which tool class

A gated recipe is just a recipe with one extra field:

```json
{
  "type": "lithic:crude_crafting",
  "required_research": "lithic:firecraft",
  "pattern": ["TST", "CCC"],
  "key": {
    "T": { "item": "minecraft:stick" },
    "S": { "item": "lithic:tinder" },
    "C": { "item": "minecraft:cobblestone" }
  },
  "result": { "id": "lithic:fire_pit", "count": 1 }
}
```

### Known gaps

- No research **GUI** — it is chat and command driven. A book screen is the
  obvious next addition.
- The **Fire Pit** can be lit only with flint and steel; a primitive fire drill
  would fit the theme better.
- Tough As Nails' canteens and water purification are not yet research-gated.
- No advancements, and no JEI/EMI integration for the custom recipe types.

## Licence

MIT.
