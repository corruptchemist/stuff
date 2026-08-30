# Design Plan — Early Game

**Status: draft for review. Nothing here is built.**
Every line item has an ID (`A0.1`, `D3`, …). Edit directly, or tell me
"cut A2.4", "change D3 to option B", "A1 should be harsher".

---

## 0. The core pitch

A caveman survival mod where the early game is hard because of **access**, not
combat. You start with nothing and the world does not hand you wood, stone, or
fire. Each of those is a project.

The headline inversion: **wood is not step one. It is roughly step twelve.**

### Design principles

- **P1. Front-load difficulty into capability, not repetition.** Your first
  plank should take ~45 minutes. Your 200th plank should not take 200 ordeals.
  Difficulty lives in *reaching* a capability; once reached, it should scale.
  *(If you want a full grind instead, say so — it changes almost every number.)*
- **P2. Every gate teaches something.** A wall the player cannot read is a bug.
  When you are blocked, it should be obvious *what kind* of thing you lack.
- **P3. Far from vanilla means the verbs change, not just the numbers.**
  Stripping bark, splitting logs and knapping are new *actions*, not slower
  versions of "hold left click".
- **P4. No progress loss on death.** Constraint comes from the world. *(Your call,
  already locked in.)*

---

## 1. Age 0 — Bare Hands

You spawn. You can do almost nothing. This stage should last 5–10 minutes and
feel genuinely alarming.

| ID | Rule |
|---|---|
| A0.1 | **Logs cannot be broken at all** without a cutting tool. Not slow — impossible. Punching one damages you. |
| A0.2 | **Stone and ores cannot be broken at all** without a striking tool. |
| A0.3 | The 2×2 inventory crafting grid has **no useful recipes at all**. Vanilla's are deleted. All crafting happens at stations or through world interactions. |
| A0.4 | Natural regeneration is off. Food stops you starving; it does not heal you. |
| A0.5 | Tough As Nails thirst is active from second one, and manual labour drains it. |

**What you actually can do bare-handed:**

| ID | Action | Yield |
|---|---|---|
| A0.6 | Break grass / ferns | Plant Fibre |
| A0.7 | Break leaves (slow) | Sticks, saplings |
| A0.8 | Break gravel | Flint (painful drop rate) |
| A0.9 | Break clay, sand, dirt, snow | as vanilla |
| A0.10 | Harvest sugar cane, bamboo, cactus, kelp, vines | as vanilla |

> **DECISION D1 — how scarce is flint?**
> **(a)** Vanilla 10% from gravel. Brutal, lots of digging.
> **(b)** Raise to ~25% so the opening is less swingy.
> **(c)** Add **loose surface stones** scattered on the ground you pick up
> (Vintage Story / TFC style). Best feel by far, but needs new worldgen Java.
> *Leaning (c) if you'll accept the Java cost, else (b).*

---

## 2. Age 1 — Flint

The first real mechanic. No crafting involved, because you have no crafting.

| ID | Step |
|---|---|
| A1.1 | **Knapping.** Right-click flint against exposed stone. Chance-based. |
| A1.2 | Success → **Flint Shard**. Failure → the flint shatters and is gone. |
| A1.3 | Base success ~55%, configurable. Each attempt costs thirst. |
| A1.4 | 2 Flint Shards → **Flint Blade**, your first cutting edge. |

> **DECISION D2 — where does A1.4 happen, given A0.3 killed the 2×2 grid?**
> **(a)** Allow this one recipe in the 2×2 as a bootstrap exception. Simple, slightly inelegant.
> **(b)** Knapping itself produces the Blade at a higher cost (e.g. 4 flint), no crafting needed. Clean, fewer moving parts.
> **(c)** Right-click a stone block *holding 2 shards* to work them together. Most flavourful, needs a little Java.
> *Leaning (c).*

---

## 3. Age 2 — Cordage and Hafting

Nothing gets attached to anything until you can tie it.

| ID | Step |
|---|---|
| A2.1 | 3 Plant Fibre → **Cordage** (twisting). |
| A2.2 | **Knapping Site** — your first placed station. Made from Flint Blade + Cordage + Sticks. Deliberately requires **no wood**, since you cannot get any yet. |
| A2.3 | Flint Blade + Cordage + Stick → **Hand Axe**. Your first tool. |
| A2.4 | Flint Blade + Cordage → **Crude Knife** (cuts hide, reed, fibre; returned damaged rather than consumed by recipes). |
| A2.5 | 2 Flint Shards + Cordage + Stick → **Digging Stick** (a striking tool: lets you break stone at last). |

This is the moment the world opens up. It should feel like it.

---

## 4. Age 3 — Wood, the hard way

The centrepiece. Vanilla gives you planks in ten seconds; here it is a
three-tool, four-step pipeline.

| ID | Step | Tool |
|---|---|---|
| A3.1 | **Strip bark.** Right-click a log with a Hand Axe → **Bark**. Log becomes stripped. Bark → cordage and tinder. | Hand Axe |
| A3.2 | **Fell.** Breaking a log with a Hand Axe is *slow* and drops a **Rough Log**, never a vanilla log. | Hand Axe |
| A3.3 | **Split.** Rough Log worked at a **Chopping Block** → 2 **Split Wood**. | Hand Axe + station |
| A3.4 | **Plank.** Split Wood → 2 Planks. | station |

Net: 1 vanilla log ⇒ 4 planks, same as vanilla — but through 3 extra steps and
2 tools. **The yield is deliberately not nerfed**; the *access* is. (Per P1 —
punishing the ratio as well as the process is where this design tips into grind.)

| ID | Supporting change |
|---|---|
| A3.5 | Vanilla `log → 4 planks` recipe deleted. |
| A3.6 | Vanilla `planks → sticks` deleted; sticks come from leaves and branches. |
| A3.7 | Vanilla **crafting table** requires Split Wood, so it lands *after* all of the above. |

> **DECISION D3 — how much does this scale later?**
> **(a)** Always 4 steps per log, forever. Maximum hostility, real grind risk.
> **(b)** A stone axe (Age 5) fells logs directly into Split Wood, skipping A3.3. *(Leaning this.)*
> **(c)** A later **Sawpit** station batch-processes 8 rough logs at once.

> **DECISION D4 — tree felling.**
> Should chopping the base of a tree bring the **whole tree** down at once?
> Great feel, meaningful Java cost. **(a)** yes **(b)** no **(c)** only with a stone axe or better.

---

## 5. Age 4 — Fire

| ID | Step |
|---|---|
| A4.1 | **Fire drill**: Stick + Cordage + Split Wood. Right-click held on a Fire Pit. Takes several seconds and can fail. |
| A4.2 | Needs **Tinder** (dried grass / bark) to catch. |
| A4.3 | Fire Pit is a TAN heat source — the first reliable warmth in the game. |
| A4.4 | Until fire is learned, nights are **one TAN temperature step colder**. Exposure is a real threat, not a status bar. |
| A4.5 | Flint and steel is *not* an early option; it needs iron, which is Age 7+. |

---

## 6. Ages 5–8 — sketch only

Deliberately thin. Nail the opening first.

| ID | Age | Gist |
|---|---|---|
| A5 | **Stone** | Real stone tools: knapped heads, cordage-bound hafts. Vanilla stone tool recipes deleted and re-added behind knowledge. |
| A6 | **Hide & Cord** | Tanning, drying racks, waterskins. Ties into TAN thirst — carrying water becomes infrastructure. |
| A7 | **Clay & Fire** | Kiln, crucibles, pottery. Water storage and the prerequisite for metal. |
| A8 | **Bloomery** | Crushed → washed → bloom → iron. Vanilla raw-iron smelting deleted. |

---

## 7. Knowledge layer

The engine already does this; the question is how heavily to lean on it.

> **DECISION D5 — how much does research gate the early game?**
> **(a) Light.** Ages 0–3 are pure mechanical/tool gating, no research at all.
> Research starts at Age 4. Cleanest opening; the player is never confused about
> *why* they cannot do something.
> **(b) Medium.** A few free auto-unlocking nodes in Ages 0–3 as signposting, so
> the player learns the research system exists while it costs them nothing. *(Leaning this.)*
> **(c) Heavy.** Everything gated from minute one, including knapping.
> Risks a confusing first ten minutes.

> **DECISION D6 — how is research presented?**
> Currently chat messages + a `/lithic research` command, which is functional and
> ugly. A proper **research book GUI** is a chunk of Java. Worth it, or later?

---

## 8. What this costs to build

| Cost | Items |
|---|---|
| **Free** (datapack only, engine handles it) | A0.1–A0.5, A0.6 (loot override), A2.1–A2.5, A3.2 (loot override), A3.4–A3.7, A5–A8 recipe chains, all research |
| **Small Java** | A1.1–A1.4 knapping (mostly exists), A3.1 bark stripping, A3.3 chopping block, A4.1 fire drill |
| **Real Java** | D1(c) surface stones (worldgen), D4(a) tree felling, D6 research GUI |

The engine as it stands already covers research, gated crafting, gated timed
conversion, tool gating, vanilla recipe deletion and the TAN hooks.

---

## 9. Open questions for you

1. **D1** — flint scarcity, and are you paying for surface stones?
2. **D2** — where the first blade gets made.
3. **D3** — does wood processing scale later, or grind forever?
4. **D4** — whole-tree felling: worth the Java?
5. **D5** — how early does research start biting?
6. **D6** — research book GUI now or later?
7. **Naming.** "Lithic" is my placeholder. Yours to replace.
8. **Pack citizen or total conversion?** Deleting vanilla plank and tool recipes
   will fight other mods. Fine if this is the centrepiece of a pack; a problem if
   it has to coexist.
9. **How long should Age 0 → first plank actually take?** I have assumed ~45
   minutes. That number drives everything above.
