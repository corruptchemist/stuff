"""Static checks that don't need a Minecraft toolchain."""
import json, os, re, struct, sys

RES = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources"))
JAVA = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "src", "main", "java", "com", "corruptchemist", "lithic"))
problems, notes = [], []

json_files = []
for root, _d, files in os.walk(RES):
    for f in files:
        if f.endswith(".json"):
            p = os.path.join(root, f)
            json_files.append(p)
            try:
                json.load(open(p))
            except Exception as e:
                problems.append(f"BAD JSON {p}: {e}")

pngs = []
for root, _d, files in os.walk(RES):
    for f in files:
        if f.endswith(".png"):
            p = os.path.join(root, f)
            pngs.append(p)
            data = open(p, "rb").read()
            if not data.startswith(b"\x89PNG\r\n\x1a\n"):
                problems.append(f"BAD PNG {p}")
                continue
            w, h = struct.unpack(">II", data[16:24])
            if (w, h) != (16, 16):
                problems.append(f"PNG {p} is {w}x{h}")

items_src = open(f"{JAVA}/registry/LithicItems.java").read()
blocks_src = open(f"{JAVA}/registry/LithicBlocks.java").read()
reg_items = set(re.findall(r'register(?:Simple)?Item\w*\(\s*"([a-z0-9_]+)"', items_src))
reg_block_items = set(re.findall(r'registerSimpleBlockItem\(\s*"([a-z0-9_]+)"', items_src))
reg_blocks = set(re.findall(r'registerBlock\(\s*\n?\s*"([a-z0-9_]+)"', blocks_src))
all_items = reg_items | reg_block_items

rdir = f"{RES}/data/lithic/lithic/research"
research_ids = {f"lithic:{f[:-5]}" for f in os.listdir(rdir) if f.endswith(".json")}

RECIPE_TYPES = {"crude_crafting", "drying"}
known = all_items | reg_blocks | RECIPE_TYPES | {r.split(":")[1] for r in research_ids}
for p in json_files:
    text = json.dumps(json.load(open(p)))
    for ident in set(re.findall(r'"lithic:([a-z0-9_]+)"', text)):
        if ident not in known:
            problems.append(f"{os.path.relpath(p, RES)}: unknown lithic:{ident}")

for f in os.listdir(rdir):
    node = json.load(open(os.path.join(rdir, f)))
    for parent in node.get("parents", []):
        if parent not in research_ids:
            problems.append(f"research/{f}: unknown parent {parent}")
    for t in node.get("triggers", []):
        if t["type"] not in {"obtain_item", "craft_item", "break_block", "kill_entity", "use_station"}:
            problems.append(f"research/{f}: bad trigger {t['type']}")

for p in json_files:
    req = json.load(open(p)).get("required_research")
    if req and req not in research_ids:
        problems.append(f"{os.path.relpath(p, RES)}: required_research {req} missing")

lang = json.load(open(f"{RES}/assets/lithic/lang/en_us.json"))
for i in all_items:
    if not os.path.exists(f"{RES}/assets/lithic/models/item/{i}.json"):
        problems.append(f"missing item model: {i}")
    if i in reg_block_items:
        if f"block.lithic.{i}" not in lang:
            problems.append(f"missing lang block.lithic.{i}")
    else:
        if not os.path.exists(f"{RES}/assets/lithic/textures/item/{i}.png"):
            problems.append(f"missing item texture: {i}")
        if f"item.lithic.{i}" not in lang:
            problems.append(f"missing lang item.lithic.{i}")

for b in reg_blocks:
    if not os.path.exists(f"{RES}/assets/lithic/blockstates/{b}.json"):
        problems.append(f"missing blockstate: {b}")
    if not os.path.exists(f"{RES}/data/lithic/loot_table/blocks/{b}.json"):
        problems.append(f"missing loot table: {b}")

# every model a blockstate points at must exist, and every texture a model uses
for f in os.listdir(f"{RES}/assets/lithic/blockstates"):
    for v in json.load(open(f"{RES}/assets/lithic/blockstates/{f}"))["variants"].values():
        m = v["model"].split(":", 1)[1]
        if not os.path.exists(f"{RES}/assets/lithic/models/{m}.json"):
            problems.append(f"blockstate {f} -> missing model {m}")

for root, _d, files in os.walk(f"{RES}/assets/lithic/models"):
    for f in files:
        model = json.load(open(os.path.join(root, f)))
        for key, tex in model.get("textures", {}).items():
            if tex.startswith("#") or not tex.startswith("lithic:"):
                continue
            if not os.path.exists(f"{RES}/assets/lithic/textures/{tex.split(':', 1)[1]}.png"):
                problems.append(f"model {f} -> missing texture {tex}")

# blockstate variants must cover every combination the Java declares
def props_of(cls):
    src = open(f"{JAVA}/block/{cls}.java").read()
    return set(re.findall(r'BooleanProperty\.create\("([a-z_]+)"\)', src)) | \
           ({"lit"} if "BlockStateProperties.LIT" in src else set())

for cls, block in [("ChoppingBlockBlock", "chopping_block"), ("FirePitBlock", "fire_pit"),
                   ("DryingRackBlock", "drying_rack")]:
    props = sorted(props_of(cls))
    variants = json.load(open(f"{RES}/assets/lithic/blockstates/{block}.json"))["variants"]
    import itertools
    for combo in itertools.product(["true", "false"], repeat=len(props)):
        key = ",".join(f"{p}={v}" for p, v in zip(props, combo))
        if key not in variants:
            problems.append(f"{block} blockstate missing variant '{key}'")

java_keys = set()
for root, _d, files in os.walk(JAVA):
    for f in files:
        if f.endswith(".java"):
            src = open(os.path.join(root, f)).read()
            java_keys |= set(re.findall(
                r'translatable\(\s*"(lithic\.[a-z_.]+|container\.lithic\.[a-z_.]+|itemGroup\.lithic)"', src))
for k in sorted(java_keys):
    if k not in lang:
        problems.append(f"Java uses missing lang key: {k}")

# every registered item should be obtainable somewhere
obtainable = set()
for p in json_files:
    if "/recipe/" not in p.replace(os.sep, "/"):
        continue
    blob = json.load(open(p))
    if blob.get("neoforge:conditions"):
        continue
    rid = blob.get("result", {}).get("id", "")
    if rid.startswith("lithic:"):
        obtainable.add(rid.split(":", 1)[1])
java_all = "\n".join(open(os.path.join(r, f)).read()
                     for r, _d, fs in os.walk(JAVA) for f in fs if f.endswith(".java"))
for i in all_items:
    if i in obtainable:
        continue
    if re.search(r'LithicItems\.' + re.escape(i.upper()) + r'\.get\(\)', java_all):
        continue  # produced by code (knapping, bark stripping, chopping)
    notes.append(f"no recipe and no code path produces: {i}")

print(f"JSON {len(json_files)} | PNG {len(pngs)} | items {len(all_items)} | blocks {len(reg_blocks)} | research {len(research_ids)}")
for n in notes:
    print("  note:", n)
if problems:
    print(f"\n!! {len(problems)} PROBLEM(S)")
    for p in problems:
        print("  -", p)
    sys.exit(1)
print("\nAll static checks passed.")
