# Tom's Storage Autocrafting

**Unofficial AE2-style autocrafting addon for [Tom's Simple Storage](https://modrinth.com/mod/toms-storage).**
Encode recipes into patterns, request items right in Tom's terminals and let your crafters and machines do the work, including multi-step recipes.

> This is a community addon and is not affiliated with Tom5454 or Tom's Simple Storage.

---

## Features

### Pattern Terminal
A Tom's storage terminal (search, sorting, tall mode) with a pattern encoding area.
- **Crafting patterns**: put ingredients in the 3×3 grid, the result is computed from the recipe.
- **Processing patterns**: any machine (mod machines, …) with free input/output amounts, scalable with x2 / x10 buttons.
- **Smelting patterns**: furnace, blast furnace or smoker, the result is computed from the recipe.
- **Fluid patterns**: filled containers (buckets, bottles, mod tanks) are emptied into the machine's tank, the empty containers come back.
- **Brewing patterns**: the grid turns into a brewing stand – fuel, reagent and 1-3 bottles (may differ). Results come from the brewing recipes (mod and datapack recipes too); the fuel is only topped up when the stand runs out.
- **Substitutes** toggle: crafting patterns also accept other items the recipe allows (e.g. any planks). Set per pattern when encoding.
- Load an existing pattern back into the grid to edit or copy it. Rename patterns in an anvil, re-encoding keeps the name.

### Pattern Provider
Holds 9 patterns (up to 36 with Capacity Upgrades).
- **Full block**: supplies **every** crafter and machine around it, one after the other. **Connector** (and paintable *framed* connector): supplies the block in front of it.
- **Crafting patterns** fill a vanilla **Crafter**. How it is triggered is configurable: by your own redstone circuit (default), directly, or by a redstone pulse – which only reaches the crafter that was just filled.
- **Processing patterns** insert all ingredients into the machine at once (all or nothing) – with machine profiles even through different sides, e.g. fuel into a furnace's fuel slot.
- **Brewing patterns** fill the whole brewing stand: bottles, reagent and – when it is empty – the fuel slot.
- **Any inventory works**: a pattern next to a hopper, chest or other block that is not its machine simply inserts its ingredients there (e.g. a smelting pattern into a hopper that feeds your furnace array).
- **Blocking mode**: only push again once the machine is empty. When off, crafters get more sets of the same recipe stacked on top.
- Crafter slot settings are respected: the recipe is moved to enabled slots.
- **Lock mode**: wait until the result is back (or for a redstone signal) before the next delivery.
- **Priority**: preferred providers get jobs first when several have the same pattern.
- Shown with the name of the machine it supplies (e.g. "Crafter", "Furnace, Smoker") – or its anvil name.

### Pattern Access Terminal
All Pattern Providers of the network in one terminal: insert, take out and swap patterns from one place, with search. Works wireless.

### Request in any terminal
Craftable items show up in **Tom's Storage / Crafting Terminal** with a green **+** (also when none are in storage).
**Ctrl + click** opens the request dialog with an **AE2-style material list**: what comes from storage, what gets crafted (all steps) and what is missing. If something is missing, **"Craft N"** offers the largest amount you can craft right now.
The **recipe tree** shows the whole plan as a graph with item icons and amounts: drag to move, scroll to zoom, click a node to fold it. Red = missing, blue = crafted, grey = from storage – the paths to missing items are opened right away, and you can start the job from there.
In Tom's **Crafting Terminal**, **Ctrl + click** on JEI's/REI's "+" also crafts the missing ingredients of a recipe.

### Autocrafting Terminal
Runs the crafting jobs of the network and shows all of them, including jobs started by Requesters and Exporters.
- **Progress per step** and **why a job is stuck**: which item is missing and how many, which provider is busy (by machine or anvil name and position), no machine, storage full, …
- **Priorities** (high / normal / low): higher priority jobs get free Pattern Providers first.
- **Pause** (frees the reserved items) and **cancel** jobs; unprocessed ingredients of a cancelled job come back from the machines.
- **Estimated time left** per job and a **job history** – request one of your past jobs again with one click.
- Jobs **reserve** their ingredients, so other jobs and Exporters do not take them away.
- Opens from afar with Tom's **(Advanced) Wireless Terminal** (the Pattern Terminal too).

### Requester
Keeps up to 6 items at a minimum stock and starts crafting jobs automatically when the stock drops.

### Level Maintainer
12 entries with threshold and batch size: "craft 64 when there are less than 32". Pausable by redstone.

### Crafting Emitter
Redstone signal while the network holds less / at least a set amount of an item – or in **craft mode**: the item becomes craftable and the signal is on while a job needs it (e.g. to switch a farm on).
Available as a full block (signal on all sides) and as a directional connector (signal only out of the front, also framed).

### Importer & Exporter
- **Importer**: pulls items from the inventory in front of it into the network (e.g. crafter/machine output). Results that come back are credited to the running jobs, so a job only counts as done when its items have really arrived. With a filled container as filter (e.g. a lava bucket) it fills empty containers from a machine's tank.
- **Exporter**: pushes filtered items from the network into an inventory. With a **Crafting Upgrade** it crafts filter items that are not in storage.
- **Filters**: plain items as ghosts, Tom's item / tag / polymorphic filters as real items.
- **Modes**: round robin, priority, random, blacklist (Importer).
- Both are available as paintable framed versions.

### Memory Card
Set up one block, copy it to the rest: **shift + right click** copies the settings of a Pattern Provider, Exporter, Importer, Requester, Level Maintainer or Crafting Emitter, **right click** applies them to a block of the same type.
Ghost filters are copied directly; real filter items and missing upgrades are taken from your inventory (an empty filter is set up like the original) – the card never creates items and tells you what was missing.

### Built for big networks
Item counts, network scans and craftable lists are cached and shared by all blocks of a network, jobs that wait for items stay idle, and terminals only send updates when something changed – so large bases with many providers and jobs stay light on TPS.

### Upgrades
Several upgrades of the same kind fit into one slot.
- **Capacity**: more jobs per Autocrafting Terminal, more pattern slots per Pattern Provider.
- **Speed**: faster Importer/Exporter, more deliveries per tick for the Pattern Provider.
- **Stack**: more items per operation, up to 64 crafts per delivery.
- **Crafting**: the Exporter crafts what is missing.
- **Redstone**: Importer/Exporter work only with or without a signal, or toggle on every pulse; the Pattern Provider gives the machine a redstone pulse after every delivery; the Level Maintainer pauses new jobs.

### Integrations (optional)
- **JEI** (and **REI**):
  - **"+" in the Pattern Terminal**: copies any recipe into the encoding grid.
  - **"+" in the Pattern Provider**: encodes the recipe straight into a free slot (uses a Blank Pattern, exact items).
  - **"+" in the Requester / Level Maintainer**: adds the recipe's output as a new entry.
  - **Ctrl + "+" in Tom's Crafting Terminal**: crafts missing ingredients.
  - Drag items from JEI onto pattern, Requester and filter slots.
- **Jade**: status of Pattern Providers, Autocrafting Terminals, Requesters, Crafting Emitters, Importers and Exporters, including crafts / items per minute.
- **Mod Menu**: in-game config screen.

---

## Getting started

1. Build a Tom's Simple Storage network (Inventory Connector + Inventory Cables).
2. Place a **Pattern Terminal** and encode a pattern from a **Blank Pattern**.
3. Put the pattern into a **Pattern Provider** next to a **Crafter** (or a machine), connected to the network.
4. Bring the results back with an **Importer** facing the crafter's or machine's output.
5. Place an **Autocrafting Terminal** on the same network: it runs the jobs.
6. **Ctrl + click** a craftable item in any Tom's terminal → *Calculate* → *Start*.

Stuck? Craft the **Autocrafting Guide** (book + Blank Pattern): an in-game book (two pages side by side) that explains every block, with setup pictures – including how to automate water bottles with a dispenser. Resource packs can translate the texts and change the pictures.

---

## Configuration

`config/tsautocraft.json` (server side) or the **Mod Menu** screen, for example:
- `crafterTrigger`: `external` (your redstone circuit), `direct` or `redstone`
- `enableDisabledCrafterSlots`: allow the provider to use disabled crafter slots
- Job limits, timeouts, Requester and Importer/Exporter speed

Server admins: `/tsautocraft reload` reads the config again, `/tsautocraft jobs` lists every running job with its position, `/tsautocraft cancel <id>` cancels one.

### Datapacks
- **Machine profiles** (`data/<namespace>/tsautocraft/machines/*.json`): insert sides (also per input: `routes` by item, tag, fuel or pattern position), blocking mode, a redstone pulse and a `cooldown` between deliveries per machine. Profiles for furnaces (fuel from the side), the brewing stand (bottles and fuel from the side, reagent on top) and dispenser/dropper are built in.
- Item tags `#tsautocraft:not_autocraftable` and `#tsautocraft:no_substitute`.

### For mod developers
Works together with **Tom's Storage Network**: fluids for patterns straight from its tanks, autocrafting across its Dimensional Links and Create machine pattern modes (Fabric, Create Fly).

A public API: own pattern modes, Importer/Exporter modes, crafting providers, job owners, upgrades, machine profiles, events, stock extensions, network bridges and terminal reach. The API jar (`-api.jar`, with sources) is only available on [GitHub](https://github.com/SytxLabs/Toms-Storage-Autocrafting/releases).

---

## Requirements

| Minecraft | Loader | Loader/API (tested version) | Tom's Storage |
|---|---|---|---|
| **26.2** | Fabric | Fabric Loader 0.19+; Fabric API 0.161.0+26.2 | 2.11.4+, < 2.12.0 |
| **26.2** | NeoForge | NeoForge 26.2.0.93 | 2.11.3+, < 2.12.0 |
| **26.3** | Fabric | Fabric Loader 0.19+; Fabric API 0.160.5+26.3 | 2.12.2+ |
| **26.3** | NeoForge | NeoForge 26.3.0.102 | 2.12.2+ |

Use Fabric API and NeoForge releases compatible with the selected Minecraft version; the versions above are the ones tested.
Java **25+** is required. Install the mod on both client and server.
- Optional: **JEI** or **REI**, **Jade**, **Mod Menu** (Fabric), **Cloth Config** (config screen on NeoForge)

## Links

- Source & issues: [GitHub](https://github.com/SytxLabs/Toms-Storage-Autocrafting)
- Website: [sytxlabs.eu](https://sytxlabs.eu)
