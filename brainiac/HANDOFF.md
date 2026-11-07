# Brainiac handoff: read this first

You are picking up a project from a cloud Claude Code session. You are now
running on the user's Windows PC (folder `C:\Users\among\stuff`). Everything
below is what that session knew. Check facts against the code before you rely
on them.

## 0. First steps

1. Get the latest work: `git fetch origin claude/brainiac-math-solver-mdgiwt`
   and `git checkout claude/brainiac-math-solver-mdgiwt`, then
   `git pull origin claude/brainiac-math-solver-mdgiwt`.
   Develop, commit and push **only** on that branch. Don't open a pull request
   unless the user asks.
2. Python: the project was developed on Python 3.13. Packages the tests and
   harnesses use: `tivars==1.1.1`, `pillow`, `pytest`, `sympy`, `mpmath`,
   `numpy`.
   For example: `python -m pip install tivars==1.1.1 pillow pytest sympy mpmath numpy`.
   If Python isn't installed, `winget install Python.Python.3.13` works; tell
   the user to tick "Add to PATH" if an installer window appears.
3. Run the unit tests (expect **476 passed**, about 45 s):
   `python -m pytest tests/test_brainiac.py tests/test_geometry.py tests/test_units.py tests/test_simulator_fidelity.py -q -p no:cacheprovider`
   (`tests/` also has unrelated tests for other projects in this repo: ignore them.)
4. Ask before installing software, changing settings, or anything outside this
   folder. Only shut the PC down if the user asks you to; `shutdown /s /t 60`
   does it, and `shutdown /a` cancels.

## 1. The user and how they like to work

- A student with a **TI-84 Plus CE** (OS 5.2 or newer). They test on the real
  calculator and send photos of error screens.
- Their standing preference: "show images of designed textures always and debug
  always, preferably let me test". Always send screenshots of what you changed
  (`python -m brainiac.shots` renders them into `brainiac/screenshots/`) and the
  `.8xp` files, and give them something to try themselves.
- Explain things plainly. When they ask for commands, give exact copy-paste
  PowerShell lines.

## 2. What the project is

**Brainiac** is a set of TI-BASIC math programs for the TI-84 Plus CE:

- **BRAINIAC**: the main menu. It solves equations and inequalities (with a
  number line), handles polynomials (roots, quadratic steps, simplify, factor,
  divide, zeros), systems of up to 6 equations, and formulas (rearrange,
  inverse). It has a "help me choose" menu.
- **GEOMETRY**: triangles, angles, 2D areas, 3D solids, circles, scale factors.
  Every solver draws a labelled figure. It is reached from BRAINIAC menu 5 → 1.
- **UNITS**: unit conversion (dimensional analysis) with the working shown.
  It is reached from BRAINIAC menu 5 → 2.

### Two channels: never break the stable one

| Channel | Program names | Version | Built by |
|---|---|---|---|
| **Stable fallback** | BRAINIAC, GEOMETRY, UNITS | 0.4, frozen | `python -m brainiac.build --stable` (**don't run unless the user asks**) |
| **Test** | BRAINUNS, GEOMUNS, UNITSUNS | 0.5 ("BRAINIAC 0.5 TEST") | `python -m brainiac.build` (the default) |

The user asked for the test versions to be called "BrainiacUNS"/"GeometryUNS".
TI names are at most 8 characters, hence BRAINUNS etc. The test builds call each
other (`prgmGEOMUNS`, `prgmUNITSUNS`). The stable `.8xp` files in
`brainiac/dist/` must stay byte-for-byte as they are. The user keeps them as a
working fallback.

## 3. Layout

- `brainiac/src/BRAINIAC.txt`, `GEOMETRY.txt`, `UNITS.txt`: the sources. They are
  TI-BASIC plus build macros: `// notes`; `@labels` (become 2-character TI labels);
  `#sub name` … `#return`, and `#call name` (a return stack in a list, e.g. ʟBS);
  `\xNN` token escapes inside strings (tokens like `real(` = `\xBB\x26`,
  `sin(` = `\xC2`, π = `\xAC`, 𝑖 = `\x2C`). The build **refuses** a Goto, Menu(
  or #call inside an If-Then/For/While/Repeat block, because the calculator
  leaks memory on those. Use labels and Goto loops around #call.
- `brainiac/build.py`: expands macros, checks the source and writes
  `brainiac/dist/*.8xp`. `TEST_NAMES` and `test_edits()` make the test channel.
- `brainiac/tibasic.py`: a **strict TI-84 Plus CE simulator**. It uses
  14-digit decimal arithmetic, enforces the real errors and refuses things the
  calculator might not do. `load_program(name, test=False)` loads a program.
- `brainiac/screen.py`, `brainiac/shots.py`: screenshot rendering, with 65
  "tours" listed in `TOURS` in `shots.py`.
- `brainiac/play.py` / `brainiac-play.bat`: the user can play Brainiac on the
  simulator by double-clicking the .bat file.
- `tests/test_brainiac.py`, `test_geometry.py`, `test_units.py`,
  `test_simulator_fidelity.py`: the unit tests.
- `stress/*.py`: nine stress harnesses. Each checks answers against independent
  math (sympy/mpmath).
- `brainiac/README.md`: user-facing docs, including a "What it can't do yet"
  list. Keep it up to date.

## 4. TI-BASIC facts that bit us (all enforced by the simulator)

- `Text(row,col,…)`: rows 0–150, columns 0–264, about 8 px per character. Row 152
  gave ERR:DOMAIN on the real calculator; that is how the stable 0.4 inequality
  crash happened.
- The home screen is 26×10 characters. `Menu(` takes at most 7 options of at most
  24 characters.
- There are no empty strings (`""→Str1` is an error). Build strings from a "?"
  and cut it off.
- `and`/`or` evaluate both sides. `seq(` can't nest. `expr(` can't nest.
- An empty list can only be measured with `dim(` or stored into.
- **In Degree mode the calculator refuses `𝑒^(` of an imaginary number
  (ERR:DOMAIN)**, and the simulator also refuses complex logs and powers in
  Degree. So Brainiac always computes in Radian. In Degree, `tmpl` converts
  angles inside sin( cos( tan( to radians (and the inverses back), and Quit
  restores the user's mode.
- sin( and the other trig functions reject complex arguments (ERR:DATA TYPE);
  sin⁻¹( and cos⁻¹( past ±1 give ERR:DOMAIN. `tmpl` wraps each one with `real(`
  plus a `+𝑖` marker, so those X values count as "not allowed" instead of
  crashing.
- TI-BASIC has no error trapping, so anything that can error must be prevented
  beforehand. Input validation lives in `#sub typed` and `#sub valid`, and
  GEOMETRY guards every Input.
- **Lesson:** every real-calculator failure so far came from the simulator
  being too lenient. When the user reports a calculator error, first make the
  simulator reproduce it, then fix the program.

## 5. How the math engine works (BRAINIAC)

- `tmpl` turns the expression into Str9 with each letter as `(ʟBVn)`, so
  `expr(Str9)` evaluates whole lists of values at once.
- `extract` decides whether an expression is a polynomial: it samples on the
  unit circle (DFT), checks far points (X=5 and 2.3, plus X=1000 for
  fractions and roots) and snaps coefficients to fractions or π multiples.
- Exact roots come from factoring. Durand–Kerner (`dk`) gives the rest as
  decimals. Anything that isn't a polynomial goes to a numeric `sweep`:
  201 samples over ⁻99..99 (0..360 for trig of the letter in Degree), with
  bisection and rounding to the nicest value that still fits.
- Inequalities find boundaries (roots, holes, domain edges), test a point in
  each region and draw a number line.
- Rearrange/Inverse cross-multiply (`frac`), multiply out bottoms (`bots`),
  move a lone √( or ³√( to its own side and undo it (`sqiso`), then `isolate`.

## 6. Testing: what "done" means here

1. Unit tests: 476 must pass.
2. Stress harnesses, run from the repo root. These are the last results, and
   the failures are all documented limits:

| Command | Result last run |
|---|---|
| `python stress/solve_poly.py --jobs 3` | 2215 runs, about 60 failing: extreme magnitudes, decimal-root precision |
| `python stress/solve_nonpoly.py -j 3` | 1464 cases, 46 failing: search misses fast wiggles/touches |
| `python stress/inequalities.py --jobs 3` | 1980, 48 failing |
| `python stress/polynomials.py -j 3` | 1640, 26 failing: 22 are products of two cubics, which it can't factor |
| `python stress/systems_formulas.py` | 2971 checks, 24 flagged: mostly the harness wanting a re-ask after a bad equation count |
| `python stress/fuzz.py --quick` | 27 crash cases, all absurd input: 1ᴇ99, `0^0`, `ln(0)` |
| `python stress/units.py` | all pass |
| `python stress/geometry.py` | 0 failures |
| `python stress/simulator_fidelity.py` | 0 failures |

   **On Windows:** `fuzz.py`, `inequalities.py` and `polynomials.py` use
   Unix-only features (`signal.SIGALRM`, `fork`). Run them in WSL (`wsl
   --install` needs the user's OK and a restart), or port the timeouts. The
   others should run natively; check.
3. After any change, compare harness output with the previous run. A new
   failing case is a regression and must be fixed before shipping.
4. Rebuild (`python -m brainiac.build`), regenerate screenshots, look at
   them, commit, push, and send the user the `.8xp` files and screenshots.

## 7. What was done in the 0.5 test build

- Inequality crash fixed (Text( rows).
- Typos: the minus key gets read as (-), and broken input gets a message instead
  of ERR:SYNTAX.
- Powers of 100 or more and `1ᴇ100` are refused.
- Identities are handled ("true for every X with …", "except where a bottom
  is 0").
- Trig of a number in Degree (sin(30)X²=2) is searched over ⁻99..99.
- Trig of something imaginary (`sin(√(X))=.5`) and inverse trig past ±1 no
  longer crash. `sin⁻¹(X)≥0` gives [0,1]. In Degree, the letter in `sin⁻¹(X)`
  isn't treated as an angle.
- Degree trig mixed with logs or roots (`sin(X)+log(X)=1`) works.
- Inverse/Rearrange now handle `1/(X+2)+3`, `√(X)+1`, `³√(X-1)+2` and
  `R=1/(1/A+1/B)`.
- Simplify refuses false polynomials like `1/(X+60)` and `√(X+150)` (it used
  to show a series of tiny decimals).
- GEOMETRY refuses imaginary numbers or numbers of 10^12 or more and asks
  again. A squashed side no longer divides by 0 in the right-angle mark.
- UNITS: parent-factor trees, rates (MI/HR → FT/SEC), temperature with an
  absolute-zero warning.
- All three set Normal/Float/Full mode, and GEOMETRY saves and restores the
  graph settings (GDB9).

### Known limits (also in the README)

- Factoring products of two cubics.
- Degree 14, or 6 per letter with several letters.
- Numbers more than about a billion times apart.
- Inequality boundaries limited to ±99.
- Holes like X=0 in ln(X²)<1 aren't found.
- Constants that error by themselves (`0^0`).
- The search can miss fast wiggles or touch points. Example: Degree
  `sin(X)>ln(X)` gives (0,360], missing a crossing near X≈1.02.
- Degree 5+ polynomials with decimals and close roots are good to about 6
  digits.
- `e^(X/50)` in Simplify can show as a series.
- SEARCH A RANGE far from 0 with fast growers can overflow.

## 8. What the user wants next

1. **Get everything running on the PC** (section 0), then run the unit tests
   and as many harnesses as Windows allows. Report the results.
2. **Test on the real TI OS without the user doing anything**, using the
   **CEmu** emulator (open source TI-84 Plus CE emulator, github.com/CE-Programming/CEmu).
   CEmu has an autotester that runs JSON scripts of key presses and checks
   screen hashes. Plan: install CEmu (ask first), get a ROM, then write a test
   runner that sends the `.8xp` files, presses keys through the main flows and
   the cases in section 7, and captures screens to compare with the simulator.
   - **The ROM must come from the user's own calculator.** Never download one.
     CEmu's ROM wizard makes a dumper program for the calculator. The user
     sends it with TI Connect CE, runs it, and sends the dump back. On newer
     calculator OS versions (5.5+), assembly programs are blocked and the dumper
     may need the arTIfiCE workaround. Check CEmu's current instructions and the
     user's OS version (2nd, +, About) before telling them what to do.
     Give them exact, numbered steps.
3. Anything the real OS shows that the simulator didn't: make the simulator
   stricter, fix the program, add a test.
