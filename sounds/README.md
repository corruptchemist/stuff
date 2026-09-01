# PMWeather audio — what each sound is and when it plays

The 57 files in this folder are the mod's audio, regrouped and renamed for easy browsing.
`FILENAME_MAP.csv` maps every file back to its original path.

Everything below was read out of the mod's own code, so the thresholds and numbers are the
real ones. Distances are in blocks, wind speeds in the units the mod's wind field uses.

---

## `thunder near/` · `thunder next/` · `thunder far/` — 18 files

Which set you hear depends on how far the strike is, horizontally, scaled by how strong it
was (distance ÷ √strength):

| Distance | Set | Plays |
|---|---|---|
| under 40 | **thunder next** | the crack directly overhead, no distance falloff applied |
| 40 – 400 | **thunder near** | the main body of a nearby storm |
| over 400 | **thunder far** | distant rumble, carried across the map at high gain |

Strike **strength above 9.0** selects the positive-polarity set, otherwise the negative one.
Positive strikes are rarer and much sharper; in this folder they come first, so
`thunder far 1`, `thunder near 1-4` and `thunder next 1` are the positives, and the rest are
negatives. Pitch is randomised 0.8–1.0 (near/far) or 0.8–1.1 (next) so repeats don't sound
identical.

## `lightning/` — 3 files

`lightning hum 1-3`. When a strike lands within 40 of you, the mod sweeps the volume around
the impact and rings every conductive surface it finds — metal in particular. Volume scales
with √strength × 0.65, pitch 1.0–1.5, one roll per surface. It's the electrical ring-off
after a close hit, not the thunder itself.

## `storm/` — 5 files

Continuous loops pinned to a storm and following it as it moves. Their audible range is the
storm's own width, so they swell as it closes on you. Each restarts if it ever stops, and
only ends when the storm dies.

| File | Starts when |
|---|---|
| `storm supercell wind` | the storm reaches stage 2 — the broad roar of the rotating updraft |
| `storm tornado wind` | stage 3, a tornado on the ground. Fire whirls use this one too |
| `storm tornado damage` | stage 3, layered under the above — the debris and destruction bed |
| `storm eyewall wind` | tropical cyclones, tied to the eyewall |
| `storm underground wind` | every storm carries this: the muffled version you hear from below ground |

## `ambience/` — 5 files

**Wind.** Sampled at your head every 4–7 seconds and layered by speed, so they stack rather
than swap:

- above 5 → `ambience wind calm 1` (held quiet, capped at 0.1)
- above 35 → `ambience wind medium 1`
- above 55 → `ambience wind strong 1` / `2`

At 60 all three are playing at once. Volume tracks speed ÷ 200; pitch drifts 0.9–1.1.

**`ambience calm 1`** is the foliage bed. Twice a second the mod checks leaves and water
within 32 of you and plays it at those spots, with volume driven by the local wind speed and
your leaf-volume setting. Each spot then goes quiet for 12 seconds. Still air means silence;
a gusty tree line means constant rustle.

## `rain/` · `sleet/` — 6 files

`rain 1-3` play while rain, freezing rain, or a wintry mix is falling above you; `sleet 1-3`
for sleet or wintry mix. Both pick their volume from precipitation intensity and drop pitch
as it gets heavier. Each has two forms: **muffled**, when something is over your head, and
**open**, at roughly double the volume and a higher pitch, under clear sky.

> Hail has no file here — it borrows stone-hit sounds rather than shipping its own.

## `fire/` — 9 files

**Three stacked background layers**, all keyed to how much fire is burning near you. Each
fades in past intensity 0.15 and reaches full volume at a different point, so they pile up as
a fire grows:

| File | Full volume at | Weight |
|---|---|---|
| `fire background low` | 1.0 | 0.65 |
| `fire background medium` | 2.0 | 0.8 |
| `fire background high` | 3.5 | 1.0 |

A campfire gets you the low layer alone. A firestorm gets all three.

**Six one-shots** — `fire crackle 1-2`, `fire pop 1-2`, `fire snap 1-2` — fire from burning
cells as individual ticks and pops, drawn at random from all six. Bigger fires roll less often
per cell (1 in 1 + intensity÷5), so the crackle stays sparse instead of turning to mush.
Played quiet (~0.2) with pitch 0.8–1.2.

## `block sleet/` — 7 files

The surface sounds for accumulated sleet, played by the layer itself:

- `sleet step 1-3` — walking across it
- `sleet break 1-2` — clearing it (also reused when placing it, and for landing on it)
- `sleet hit 1-2` — striking it

## `block/` — 3 files

- **`block siren`** — the tornado siren. Once a second it looks for a tornado-warning storm
  within 1.15× the configured storm size; if it finds one it sounds, then holds off for two
  minutes. Audible to 120, volume from your siren setting. It streams rather than loading up
  front, since it's long.
- **`block radar complete`** — the radar tower finishing assembly.
- **`block radar dismantle`** — the same structure coming apart.

## `powerflash/` — 1 file

`powerflash 1`, the arc flash when a power line faults. Volume comes from the fault's
strength — a curve on voltage ÷ 10000 — times 4, with pitch 0.9–1.0. Fires once at the moment
of the flash, paired with the visual.
