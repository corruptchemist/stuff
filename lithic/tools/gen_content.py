"""Recipes, vanilla overrides, tags, research and lang for Ages 0-4."""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
NS = "lithic"

def write(rel, obj):
    p = os.path.normpath(os.path.join(ROOT, rel))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")

def item(i):        return {"item": i}
def tag(t):         return {"tag": t}
def result(i, n=1): return {"id": i, "count": n}

V_SHAPED, V_SHAPELESS = "minecraft:crafting_shaped", "minecraft:crafting_shapeless"
STATION = f"{NS}:crude_crafting"
R = f"data/{NS}/recipe"

def shaped(pattern, key, res, research=None, rtype=STATION):
    out = {"type": rtype}
    if research:
        out["required_research"] = research
    out.update({"pattern": pattern, "key": key, "result": res})
    return out

def shapeless(ings, res, research=None, rtype=STATION):
    out = {"type": rtype}
    if research:
        out["required_research"] = research
    out.update({"ingredients": ings, "result": res})
    return out

# --------------------------------------------------- bootstrap, 2x2 grid -----
# Exactly two recipes stay craftable without a station, because the player must
# be able to reach the first station from a bare spawn. Everything else moves
# to the Knapping Site.
write(f"{R}/cordage.json",
      shapeless([item(f"{NS}:plant_fibre")] * 3, result(f"{NS}:cordage"), rtype=V_SHAPELESS))
write(f"{R}/knapping_site.json", shaped(
    ["BC", "SS"],
    {"B": item(f"{NS}:flint_blade"), "C": item(f"{NS}:cordage"), "S": item("minecraft:stick")},
    result(f"{NS}:knapping_site"), rtype=V_SHAPED))

# ------------------------------------------------ Age 2: tools, at station ---
write(f"{R}/hand_axe.json", shaped(
    ["BC", ".S", ".S"],
    {"B": item(f"{NS}:flint_blade"), "C": item(f"{NS}:cordage"), "S": item("minecraft:stick")},
    result(f"{NS}:hand_axe"), research=f"{NS}:cordwork"))
write(f"{R}/crude_knife.json", shapeless(
    [item(f"{NS}:flint_blade"), item(f"{NS}:cordage")],
    result(f"{NS}:crude_knife"), research=f"{NS}:cordwork"))
write(f"{R}/digging_stick.json", shapeless(
    [item(f"{NS}:flint_shard"), item(f"{NS}:flint_shard"),
     item(f"{NS}:cordage"), item("minecraft:stick")],
    result(f"{NS}:digging_stick"), research=f"{NS}:cordwork"))
write(f"{R}/drying_rack.json", shaped(
    ["SSS", "C.C"],
    {"S": item("minecraft:stick"), "C": item(f"{NS}:cordage")},
    result(f"{NS}:drying_rack"), research=f"{NS}:cordwork"))

# ----------------------------------------------------------- Age 3: wood -----
write(f"{R}/chopping_block.json", shapeless(
    [item(f"{NS}:rough_log"), item(f"{NS}:cordage")],
    result(f"{NS}:chopping_block"), research=f"{NS}:woodcraft"))
write(f"{R}/oak_planks_from_split_wood.json", shapeless(
    [item(f"{NS}:split_wood")], result("minecraft:oak_planks", 2), research=f"{NS}:woodcraft"))
# A3.7: the vanilla table lands after the whole wood pipeline, not before it.
write(f"{R}/crafting_table.json", shaped(
    ["WW", "WW"], {"W": item(f"{NS}:split_wood")},
    result("minecraft:crafting_table"), research=f"{NS}:woodcraft"))
write(f"{R}/stick_from_split_wood.json", shapeless(
    [item(f"{NS}:split_wood")], result("minecraft:stick", 2), research=f"{NS}:woodcraft"))
# Bark is not a dead end: it twists into cordage too, just less efficiently.
write(f"{R}/cordage_from_bark.json", shapeless(
    [item(f"{NS}:bark")] * 4, result(f"{NS}:cordage"), research=f"{NS}:cordwork"))

# ----------------------------------------------------------- Age 4: fire -----
write(f"{R}/fire_drill.json", shaped(
    ["S", "C", "W"],
    {"S": item("minecraft:stick"), "C": item(f"{NS}:cordage"), "W": item(f"{NS}:split_wood")},
    result(f"{NS}:fire_drill"), research=f"{NS}:firecraft"))
write(f"{R}/fire_pit.json", shaped(
    ["CCC", "WTW"],
    {"C": item("minecraft:cobblestone"), "W": item(f"{NS}:split_wood"), "T": item(f"{NS}:tinder")},
    result(f"{NS}:fire_pit"), research=f"{NS}:firecraft"))

def drying(inp, res, time, research=None):
    out = {"type": f"{NS}:drying", "input": inp, "result": res, "time": time}
    if research:
        out["required_research"] = research
    return out

write(f"{R}/drying_tinder.json",
      drying(item(f"{NS}:plant_fibre"), result(f"{NS}:tinder"), 1800, f"{NS}:cordwork"))
write(f"{R}/drying_tinder_from_bark.json",
      drying(item(f"{NS}:bark"), result(f"{NS}:tinder"), 2400, f"{NS}:cordwork"))

# ------------------------------------------------- vanilla shortcut removals -
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
         "crimson", "warped", "bamboo"]
REMOVED = [f"{w}_planks" for w in WOODS] + [
    "stick", "crafting_table",
    "wooden_pickaxe", "wooden_axe", "wooden_shovel", "wooden_sword", "wooden_hoe",
    "stone_pickaxe", "stone_axe", "stone_shovel", "stone_sword", "stone_hoe",
]
for name in REMOVED:
    write(f"data/minecraft/recipe/{name}.json", {
        "neoforge:conditions": [{"type": "neoforge:false"}],
        "type": V_SHAPELESS,
        "ingredients": [item("minecraft:dirt")],
        "result": result("minecraft:dirt", 1),
    })

# ---------------------------------------------------- vanilla loot overrides -
LOGS = []
for w in ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"]:
    LOGS += [f"{w}_log", f"stripped_{w}_log", f"{w}_wood", f"stripped_{w}_wood"]
for w in ["crimson", "warped"]:
    LOGS += [f"{w}_stem", f"stripped_{w}_stem", f"{w}_hyphae", f"stripped_{w}_hyphae"]

for log in LOGS:
    write(f"data/minecraft/loot_table/blocks/{log}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:rough_log"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })

for grass in ["short_grass", "tall_grass", "fern", "large_fern"]:
    write(f"data/minecraft/loot_table/blocks/{grass}.json", {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "bonus_rolls": 0,
             "entries": [{"type": "minecraft:item", "name": f"{NS}:plant_fibre"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.6}]},
            {"rolls": 1, "bonus_rolls": 0,
             "entries": [{"type": "minecraft:item", "name": "minecraft:wheat_seeds"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.125}]},
        ],
    })

# ------------------------------------------------------------------- tags ----
write(f"data/{NS}/tags/block/requires_cutting_tool.json", {"values": ["#minecraft:logs"]})
write(f"data/{NS}/tags/block/requires_striking_tool.json", {"values": [
    "#minecraft:base_stone_overworld", "minecraft:cobblestone", "minecraft:cobbled_deepslate",
    "#minecraft:iron_ores", "#minecraft:copper_ores", "#minecraft:coal_ores",
    "#minecraft:gold_ores", "#minecraft:redstone_ores", "#minecraft:diamond_ores",
    "#minecraft:lapis_ores", "#minecraft:emerald_ores",
]})
write(f"data/{NS}/tags/block/knapping_surface.json", {"values": [
    "#minecraft:base_stone_overworld", "minecraft:cobblestone",
    "minecraft:cobbled_deepslate", "#minecraft:stone_bricks",
]})
write(f"data/{NS}/tags/item/cutting_tools.json",
      {"values": ["#minecraft:axes", f"{NS}:hand_axe", f"{NS}:crude_knife"]})
write(f"data/{NS}/tags/item/striking_tools.json",
      {"values": ["#minecraft:pickaxes", f"{NS}:digging_stick"]})

# --------------------------------------------------------------- research ----
# Every Age 0-4 node costs 0, so it learns itself the moment it is discovered.
# They exist to teach the player the system and to sequence the fire recipes,
# not yet to cost anything. Paid research arrives with Age 5.
RES = f"data/{NS}/{NS}/research"
def node(triggers, parents=None):
    out = {}
    if parents:
        out["parents"] = parents
    out["insight_cost"] = 0
    out["triggers"] = triggers
    return out

write(f"{RES}/knapping.json", node([{"type": "use_station", "value": f"{NS}:knapping", "insight": 1}]))
write(f"{RES}/cordwork.json", node([{"type": "obtain_item", "value": f"{NS}:cordage", "insight": 1}],
                                   parents=[f"{NS}:knapping"]))
write(f"{RES}/woodcraft.json", node([{"type": "obtain_item", "value": f"{NS}:rough_log", "insight": 1}],
                                    parents=[f"{NS}:cordwork"]))
write(f"{RES}/firecraft.json", node([{"type": "obtain_item", "value": f"{NS}:tinder", "insight": 1}],
                                    parents=[f"{NS}:woodcraft"]))

# ------------------------------------------------------------------- lang ----
ITEMS = {
    "plant_fibre": "Plant Fibre", "flint_shard": "Flint Shard", "flint_blade": "Flint Blade",
    "cordage": "Cordage", "bark": "Bark", "rough_log": "Rough Log", "split_wood": "Split Wood",
    "tinder": "Tinder", "hand_axe": "Hand Axe", "crude_knife": "Crude Knife",
    "digging_stick": "Digging Stick", "fire_drill": "Fire Drill",
}
BLOCKS = {
    "knapping_site": "Knapping Site", "chopping_block": "Chopping Block",
    "fire_pit": "Fire Pit", "drying_rack": "Drying Rack",
}
RESEARCH = {
    "knapping":  ("Knapping",  "Stone breaks stone. Strike flint against a hard face and take what shatters off."),
    "cordwork":  ("Cordwork",  "Twisted fibre holds where bare hands cannot. Everything after this is tied together."),
    "woodcraft": ("Woodcraft", "A tree does not give up its wood to hands. Bark first, then the trunk, then the split."),
    "firecraft": ("Firecraft", "Dry tinder, a spindle, and patience. Fire is the difference between a night survived and a night endured."),
}

lang = {"itemGroup.lithic": "Lithic", "container.lithic.knapping_site": "Knapping Site"}
for k, v in ITEMS.items():
    lang[f"item.{NS}.{k}"] = v
for k, v in BLOCKS.items():
    lang[f"block.{NS}.{k}"] = v
for k, (nm, desc) in RESEARCH.items():
    lang[f"research.{NS}.{k}"] = nm
    lang[f"research.{NS}.{k}.desc"] = desc
lang.update({
    "lithic.gate.cutting":  "Your hands are not a blade. You need something that cuts.",
    "lithic.gate.striking": "Bare hands will not shift stone. You need something that strikes.",
    "lithic.knapping.shattered": "The flint shatters uselessly.",
    "lithic.knapping.blade":     "The shards come together into an edge.",
    "lithic.workbench.locked":   "You do not understand this yet: %s",
    "lithic.rack.locked":        "You do not know how to dry this.",
    "lithic.firepit.no_tinder":  "Nothing here will catch. You need tinder.",
    "lithic.firepit.failed":     "The spindle smokes, and goes cold.",
    "lithic.tally.insight": "Insight: %s",
    "lithic.tally.learned": "Learned so far: %s",
    "lithic.research.discovered": "It occurs to you: %s",
    "lithic.research.learned":    "You have worked it out: %s",
})
p = os.path.normpath(os.path.join(ROOT, f"assets/{NS}/lang/en_us.json"))
os.makedirs(os.path.dirname(p), exist_ok=True)
with open(p, "w") as fh:
    json.dump(lang, fh, indent=2, ensure_ascii=False)
    fh.write("\n")

print(f"recipes, {len(REMOVED)} removals, {len(LOGS)} log overrides, tags, research, {len(lang)} lang keys")
