"""Generates Lithic's models, blockstates, loot tables, recipes, tags, research and lang."""
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

# ------------------------------------------------------------------- items ---
# name -> (display, model parent)
ITEMS = {
    "plant_fibre":   ("Plant Fibre",   "generated"),
    "flint_shard":   ("Flint Shard",   "generated"),
    "flint_blade":   ("Flint Blade",   "generated"),
    "cordage":       ("Cordage",       "generated"),
    "bark":          ("Bark",          "generated"),
    "rough_log":     ("Rough Log",     "generated"),
    "split_wood":    ("Split Wood",    "generated"),
    "tinder":        ("Tinder",        "generated"),
    "hand_axe":      ("Hand Axe",      "handheld"),
    "crude_knife":   ("Crude Knife",   "handheld"),
    "digging_stick": ("Digging Stick", "handheld"),
    "fire_drill":    ("Fire Drill",    "handheld"),
}
BLOCKS = {
    "knapping_site":  "Knapping Site",
    "chopping_block": "Chopping Block",
    "fire_pit":       "Fire Pit",
    "drying_rack":    "Drying Rack",
}

for name, (_disp, parent) in ITEMS.items():
    write(f"assets/{NS}/models/item/{name}.json", {
        "parent": f"minecraft:item/{parent}",
        "textures": {"layer0": f"{NS}:item/{name}"},
    })
for name in BLOCKS:
    write(f"assets/{NS}/models/item/{name}.json", {"parent": f"{NS}:block/{name}"})

# ------------------------------------------------------------ block models ---
def slab_like(texture, height):
    face = {"texture": "#all"}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/{texture}", "all": f"{NS}:block/{texture}"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, height, 16],
            "faces": {
                "down": {"texture": "#all", "cullface": "down"}, "up": dict(face),
                "north": dict(face), "south": dict(face), "east": dict(face), "west": dict(face),
            },
        }],
    }

def stump(top_texture):
    """A 12-high stump, so the model matches ChoppingBlockBlock's collision shape."""
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/chopping_block_side",
            "top": f"{NS}:block/{top_texture}",
            "side": f"{NS}:block/chopping_block_side",
        },
        "elements": [{
            "from": [1, 0, 1], "to": [15, 12, 15],
            "faces": {
                "down":  {"texture": "#top"},
                "up":    {"texture": "#top"},
                "north": {"texture": "#side"}, "south": {"texture": "#side"},
                "east":  {"texture": "#side"}, "west":  {"texture": "#side"},
            },
        }],
    }

write(f"assets/{NS}/models/block/knapping_site.json",
      {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/knapping_site"}})
write(f"assets/{NS}/models/block/chopping_block.json", stump("chopping_block_top"))
write(f"assets/{NS}/models/block/chopping_block_loaded.json", stump("chopping_block_top_loaded"))
write(f"assets/{NS}/models/block/fire_pit.json", slab_like("fire_pit", 4))
write(f"assets/{NS}/models/block/fire_pit_lit.json", slab_like("fire_pit_lit", 4))
for variant in ("drying_rack", "drying_rack_occupied", "drying_rack_finished"):
    write(f"assets/{NS}/models/block/{variant}.json", slab_like(variant, 4))

write(f"assets/{NS}/blockstates/knapping_site.json",
      {"variants": {"": {"model": f"{NS}:block/knapping_site"}}})
write(f"assets/{NS}/blockstates/chopping_block.json", {"variants": {
    "loaded=false": {"model": f"{NS}:block/chopping_block"},
    "loaded=true":  {"model": f"{NS}:block/chopping_block_loaded"},
}})
write(f"assets/{NS}/blockstates/fire_pit.json", {"variants": {
    "lit=false": {"model": f"{NS}:block/fire_pit"},
    "lit=true":  {"model": f"{NS}:block/fire_pit_lit"},
}})
# Property names in a variant key MUST be alphabetical; Minecraft builds the
# lookup key from the sorted state definition, so "occupied=..,finished=.." would
# never match and the block would render as the missing-model cube.
write(f"assets/{NS}/blockstates/drying_rack.json", {"variants": {
    "finished=false,occupied=false": {"model": f"{NS}:block/drying_rack"},
    "finished=false,occupied=true":  {"model": f"{NS}:block/drying_rack_occupied"},
    "finished=true,occupied=false":  {"model": f"{NS}:block/drying_rack"},
    "finished=true,occupied=true":   {"model": f"{NS}:block/drying_rack_finished"},
}})

for name in BLOCKS:
    write(f"data/{NS}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
print("models, blockstates and loot tables written")
