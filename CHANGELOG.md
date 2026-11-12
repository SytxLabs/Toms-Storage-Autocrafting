# 0.7.0

## New

- Minecraft 26.2 on Fabric (Tom's Storage 2.11.4) and NeoForge (Tom's Storage 2.11.3), thanks to Cornit.
- Recipe tree: legend (missing / crafted / stored), "Expand all", keyboard control (arrow keys pan, +/- zoom, Home
  centers, Enter starts the request). Node states also show as badges, not only as frame colors.
- Cancelling a job and clearing the history need a second click on the same button.
- Paused jobs say "(Paused)" in their row instead of showing it only through the play/pause glyph.
- The request screen shows "Calculating…" while the preview is on its way and explains an invalid amount
  (e.g. "64*3") instead of doing nothing; Enter takes the offered "Craft N" after a failed preview.
- Craftable items in Tom's terminals: the green "+" sits on a dark backdrop, and the tooltip says how to request.
- Guide book: the table of contents works with the keyboard (Up/Down, Enter).

## Fixed

- Fluid units that no stock extension takes are kept by the block that pushed them instead of disappearing.
- A stock extension that delivers more than asked no longer hands the surplus to the caller: it goes back to storage.
- Fluid units of mod fluids without a fluid block are named after the fluid id instead of "Air".
- The virtual fluid unit item no longer shows up in JEI/REI (`c:hidden_from_recipe_viewers`).
- Clearer wording in messages, tooltips and guide pages (all four languages).

## Development

- Game test reports of every loader and Minecraft version are uploaded as one artifact (`gametest-reports`).

## API 4 (unchanged version, backward compatible)

- `MachineProfile.sidesFor` / `Route.matches` take an optional `Level` (needed for furnace fuel on 26.2); the old
  signatures stay.

# 0.6.0

## New

- Works together with Tom's Storage Network (optional addon): patterns take fluids straight from its tanks, Pattern
  Providers and jobs reach across its Dimensional Links, its Wireless Antennas extend the reach of our terminals, and on
  Fabric with Create Fly it adds pattern modes for Create machines.
- JEI/REI "+": fluid ingredients of a recipe (e.g. Create mixing or filling) are encoded as filled buckets (amount rounded
  up to whole buckets) instead of being left out.
- Recipes with more than 9 inputs can be encoded with the "+" of a Pattern Provider for pattern modes that allow it
  (e.g. 9x9 mechanical crafting); the Pattern Terminal shows a hint instead of cutting the recipe.

## Fixed

- Pulls from Tom's storage (Exporter, Importer's empty containers, stock extensions) first bring Tom's change tracker
  up to date: before, items that arrived since the last count could stay invisible until a terminal looked.

## Development

- Game tests also run on NeoForge (`gradlew :26.3-neoforge:runGameTestServer`, CI).

- `/tsautocraft testsetup`: builds test stations (crafting with patterns, smelting, importer / exporter, all items).

## Faster

- The shared network view is found by block position: before, Tom's cable walk returned a new node set on every lookup
  from one of our blocks, so the view was rebuilt every time instead of once per second.

## API 4 (backward compatible)

- `StockExtensions`: extra network stock used by every count, plan and pull (e.g. fluids in tanks as filled containers).
- `NetworkBridges`: Pattern Providers, job owners and jobs of networks in other levels belong to the network.
- `TerminalReach`: extra reach for the menus of our terminal blocks.
- `PatternMode.maxInputs()` / `maxOutputs()`, `PatternEncoder.encodeFree(mode, grid, outputs, maxInputs, maxOutputs)`.
- `CraftingJob.target()` / `amount()`, `TsAutocraftApi.activeAmount(level, pos, item)` (what running jobs craft).
- `FluidUnits`: exact fluid amounts in patterns. With an extension that stores them (Tom's Storage Network's
  tanks), JEI/REI fluid ingredients are encoded in mB (item "Fluid (mB)") instead of whole buckets; deliveries put
  the exact amount into the machine's tank.
- `TerminalReach.registerDeny(hook)`: other mods can keep players out of our terminals (e.g. a network lock).
- `TsAutocraftApi.itemsArrived(level, pos, stack, count)`: credits results that reached the network without an
  Importer (e.g. fluid moved into tanks that count as filled containers).

# 0.5.0

## New

- NeoForge support (26.3), built from the same sources as the Fabric version (Stonecutter).
- Hold Ctrl in any inventory screen to see encoded patterns as the item they craft (with amount).
- Request dialog: smaller while entering the amount (item centered, full-width amount field, buttons +1/+16/+64/x2 and
  -1/-16/-64/:2). The amount field accepts math (`+ - * / x ( )`, e.g. `3*64+12`); invalid input turns red.
- French and Spanish translations (including the guide book).
- Pattern Terminal: an encoded pattern put into the top slot is loaded into the grid right away for editing (encode
  overwrites it); the clear and load buttons show icons (barrier, pattern) instead of "X" and "L".
- Guide book pages are plain text files per language (`assets/tsautocraft/guide/<lang>/<page>.txt`, first line = title),
  so resource packs can translate the guide; missing languages fall back to English.
- Memory Card (redstone, gold ingots and a Blank Pattern): shift + right click copies the settings of a Pattern
  Provider, Exporter, Importer, Requester, Level Maintainer or Crafting Emitter, right click applies them to a block of
  the same type, shift + right click into the air clears the card. Ghost filters are copied; real filter items and
  missing upgrades are taken from your inventory (unconfigured filters are set up like the original), nothing is
  created and anything missing is listed. Anvil names are not copied.
- Recipe tree as a graph: the Tree button in the request preview opens a full screen tree with item icons and amounts
  (drag to move, mouse wheel to zoom, click a node to collapse it; red frame = missing, blue = crafted, grey = from
  storage). It starts collapsed below the second level except for the paths to missing items, and can start the job.
- Autocrafting Terminal history: ↻ next to your own finished jobs opens the request dialog with the same item and amount.
- Jobs show "storage full" as the reason when an Importer (or a Pattern Provider returning results) cannot put the
  results into the network.
- Jade shows crafts per minute for Pattern Providers and items per minute for Importers and Exporters.
- The guide book opens like a real book: two pages side by side, leather cover, page arrows in the corners; wide
  diagrams are scaled to the page. Diagrams are files now (`assets/tsautocraft/guide/diagrams/<page>.txt`, resource packs
  can change them) with cable junctions (`+`); the setup diagram shows a clearer network.
- Admin command `/tsautocraft` (permission level 2): `reload` reads `config/tsautocraft.json` again, `jobs` lists all
  running jobs with their position (click to teleport / to cancel), `cancel <id>` cancels one.

## For mod developers

- API version 3: `CraftingEvents` use `eu.sytxlabs.tsautocraft.api.Event` instead of Fabric's `Event` (same `register` /
  `invoker`, recompile needed). The API jar no longer needs Fabric API.

## Fixed

- Fluid patterns that fill empty containers (e.g. empty bucket -> lava bucket) now work on a cauldron or any other block
  with only a tank: the Pattern Provider fills the containers from it itself (no Importer needed) and waits while there is
  not enough fluid yet. Before, the cauldron was reported as "no machine".
- A full-block Pattern Provider no longer treats plain blocks next to it (planks, stone, ...) as machines: they are no
  longer part of its name ("Spruce Planks, Crafter") and get no deliveries.
- Cancelling a job after a server restart returns its inputs again: the Pattern Provider now saves which job filled a
  machine (jobs have a persistent id), before this was lost on restart.

## Development

- Fabric game tests (`gradlew :26.3-fabric:runGametest`, also in CI): crafting through a crafter, smelting, cancel
  with returned inputs (also after a reload), reservations of two jobs.

# 0.4.1

## Performance

- Idle Importers and Exporters (nothing to move) check their inventory less often: they skip up to 3 operations, one more
  per idle round, and are back at full speed as soon as something moves or a filter, mode or redstone signal changes.
  New config option `ioIdleMaxSkip` (default 3, 0 = off).

# 0.4.0

## Please note when updating

- For mod developers: `PatternData` has a new internal record component (`derived`, cached values). The constructor with
  mode, inputs and outputs still works; code that deconstructs the record needs an update. `TsAutocraftApi.VERSION` is now 2.

## New

- Every pattern works on any inventory: a crafting, smelting, brewing or fluid pattern next to a hopper, chest or other
  block simply inserts its inputs there.
- Without an anvil name, a Pattern Provider is shown with the name of the machine(s) it supplies (Pattern Access Terminal,
  job status, which also shows its position).

## Changed

- The Pattern Provider has no substitutes button anymore: substitutes are set only when encoding a pattern in the Pattern
  Terminal. Patterns encoded with JEI's "+" directly in a provider are exact.

## Performance

- Much less server load on big networks: item counts are no longer recounted slot by slot for every check, Pattern
  Providers cache their machine sides and no longer cause neighbor updates just for lighting up. All blocks of a network
  share one network scan, patterns cache their totals, and planning resolves substitutes only where needed. Jobs waiting
  for items no longer ask every provider, failed automatic plans retry less often, terminals only send updates when something
  changed, and encoding a pattern with substitutes no longer tests every item of the game.

## Fixed

- The Pattern Terminal message no longer disappears faster at high frame rates.
- Patterns with substitutes (e.g. a chest from "any planks") reported the pattern's own item as missing when none was in
  stock, even though another allowed item could be crafted (e.g. oak planks from oak logs). The planner now picks a
  craftable option, and tries again preferring craftable options when stock alone is not enough.

# 0.3.0

## Please note when updating

- The **Pattern Provider GUI** is a bit taller (new row for lock mode and priority).
- A full-block Pattern Provider no longer supplies inventories that belong to the storage network itself.
- **Dispensers and droppers** now get a built-in machine profile: blocking on, a redstone pulse after every delivery and
  10 ticks between deliveries. Override it with your own profile in a datapack if needed.

## New

### Blocks & items
- **Pattern Access Terminal**: all Pattern Providers of the network in one place. Insert, take out and swap patterns
  without walking to each provider; search by provider name or pattern output, hide full providers. Works wireless.
- **Level Maintainer**: 12 entries with item, threshold and batch size ("craft 64 when below 32"). Speed Upgrade for
  faster checks, Redstone Upgrade to pause new jobs.
- **Autocrafting Guide**: in-game handbook (book + Blank Pattern) with a page for every block, pattern mode and feature,
  with setup pictures.

### Patterns
- **Brewing patterns**: the Pattern Terminal grid turns into a brewing stand (fuel, reagent, 1-3 bottles that may differ),
  using the (data-driven) brewing recipes, so mod and datapack recipes work too. The fuel is only delivered when the stand
  runs out (not counted per brew). JEI/REI "+" encodes a full stand. The Importer waits until a brewing stand is done.
- **Amount multiplier** in the Pattern Terminal for processing and fluid patterns: x2, /2, x10, /10.

### Pattern Provider
- **Lock mode**: off, *until the result is back* (per side on the full block, with a safety timeout), or only with /
  without a redstone signal.
- **Priority**: providers with a higher priority get jobs first when several have the same pattern.
- Stack Upgrades now work with the `direct` and `redstone` crafter triggers: every stacked set gets its own trigger.

### Jobs
- **Craft missing** from JEI/REI: in Tom's Crafting Terminal the "+" tooltip shows which missing ingredients can be
  crafted; Ctrl + click starts jobs for them.
- **Job history** in the Autocrafting Terminal (tabs Running / History, size configurable).
- **Estimated time left** in the job tooltip.
- **Recipe tree** in the request preview (toggle between list and tree, lines expand and collapse).
- **Return inputs on cancel**: ingredients of a cancelled job that still sit unprocessed in a crafter, furnace or
  machine go back into storage (config `returnInputsOnCancel`).

### Datapacks
- **Machine profiles**: `routes` send different inputs through different sides (by item id, `#tag`, `"fuel": "cooking" |
  "brewing" | "any"` or position in the pattern), `cooldown` pauses between two deliveries (e.g. a dispenser filling buckets: the water source must flow back).
- Built-in profiles for **furnace / blast furnace / smoker** (first input on top, fuel from the side) and the **brewing
  stand** (bottles and fuel from the side, reagent on top), so processing patterns may include the fuel.
- Built-in profile for **dispenser / dropper**: redstone pulse after every delivery, so water bottles/buckets can be automated.

### Config
- `providerLockTimeoutSeconds`, `returnInputsOnCancel`, `jobHistorySize`, `maintainerCheckTicks`, `maintainerReplanSeconds`.

### For mod developers
- `PatternMode.gridSlotEnabled`, `PatternMode.optionalInput` (inputs delivered when needed, not planned per craft),
  `PatternMode.DispatchContext.undoPull`, new default methods on `CraftingProvider` (priority, lock/result callbacks,
  `returnInputs`). Existing implementations keep working.

## Fixed

- Items could get lost when pulled ingredients did not fit back into a full network.
- With Stack Upgrades, a partial delivery could be delivered twice (overproduction).
- A delivery that failed after pulling still counted as a craft; fluid patterns could lose the fluid in that case.
- Jobs waiting for results could finish too early after a chunk reload or server restart.
- Arriving results are credited to the job that has waited longest instead of the first matching one.
- Requesters no longer cancel and replan jobs whose crafts are still inside machines (overproduction).
- The planner ignored the real stock of an item when a byproduct of the same item was produced first.
- Craft previews are rate limited per player (server lag from spammed requests).
- Importer could lose an empty container when filling several fluid filters; Pattern Providers could stay lit after a reload.

# 0.2.0

## Please note when updating

- **Pattern Provider (full block) has no front anymore.** It now supplies **every** crafter and machine around it. Check
  existing setups: a chest or machine next to it is now a target too (cables and other network blocks never are).
  The Pattern Provider Connector keeps its front.
- **Crafting Emitter** is a full block again (signal on all sides). The new **Directional Crafting Emitter** (connector shape,
  signal only out of the front) can be made from it in a crafting grid.
- **Exporter / Importer filters:** plain items are ghosts (nothing is used up), Tom's filter items are real items.

## New

### Jobs
- **Priority** per job (high / normal / low): higher priority jobs get free Pattern Providers first, on the whole level.
- **Pause / resume** jobs; a paused job frees its reserved items.
- **Detailed status:** hover a job in the Autocrafting Terminal to see every step and why it is stuck (missing item with
  have/need per craft, provider busy at x/y/z or by its name, no machine, ...).

### Blocks & items
- **Directional Crafting Emitter** (+ Framed): connector shape, signal only out of its front.
- **Redstone Upgrade:**
  - Importer/Exporter: work only with a signal, only without a signal, or toggle on every pulse (button in the GUI).
  - Pattern Provider: a redstone pulse to the machine after every delivery (also makes a crafter craft without your own circuit).
- **Fluid processing patterns:** filled containers (buckets, bottles, mod tanks) are emptied into the machine's tank, the empty
  containers go back into the network. An Importer with a filled container as filter fills empty containers from a tank.
- **Exporter / Importer modes:** round robin, priority, random, blacklist (Importer).
- **Upgrades stack:** several upgrades of the same kind fit into one slot (up to a maximum per block).
- **Anvil names** for all blocks: GUI title, Jade, job status; the name stays on the dropped block. Renamed patterns keep their
  name when re-encoded.
- Tom's (Advanced) **Wireless Terminal** opens the Autocrafting Terminal and the Pattern Terminal from afar.

### Compatibility
- **Jade:** status of Pattern Providers (patterns, machines, blocking mode), Autocrafting Terminals (jobs), Requesters,
  Crafting Emitters and Exporters.
- **Mod Menu:** in-game config screen (Cloth Config ships with Tom's Storage).

### Datapacks
- **Machine profiles** (`data/<namespace>/tsautocraft/machines/*.json`): insert sides, forced blocking mode and a redstone pulse
  per machine. Built in: brewing stand (bottles from the side, ingredient on top).
- Item tags `#tsautocraft:not_autocraftable` and `#tsautocraft:no_substitute`.

### For mod developers
- Public API in `eu.sytxlabs.tsautocraft.api` (API jar with sources only on GitHub releases): pattern modes, IO modes, crafting providers, job owners,
  events (plan created, job started, step dispatched, job finished), upgrades, machine profiles, planner hooks.

## Changed
- Tooltips wrap long descriptions; centered item and block textures.
- The Pattern Provider's redstone signal (redstone trigger mode, upgrade, machine profile) only reaches the machine that was
  just filled; every crafter around a provider has its own cooldown.
