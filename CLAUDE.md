# Tom's Storage Autocrafting (tsautocraft)

Fabric and NeoForge **26.2/26.3** addon for Tom's Simple Storage (2.11.4 on Fabric 26.2, 2.11.3 on NeoForge 26.2, 2.12.2 on 26.3)
adding AE2-style autocrafting.
Package `eu.sytxlabs.tsautocraft`, mod id `tsautocraft`. Code, comments and docs in English; every visible string is a translation key
(`src/main/resources/assets/tsautocraft/lang/en_us.json` + `de_de.json`, `fr_fr.json`, `es_es.json`, keys must stay in sync;
guide pages in `assets/tsautocraft/guide/<lang>/`).
The user (Shaun) talks German; reply in German.

## Build / run

- JDK 25, Gradle 9.9.0 wrapper, Stonecutter 0.10 (one source tree, nodes `versions/<mc>-<loader>`: `26.2-fabric`,
  `26.2-neoforge`, `26.3-fabric`, `26.3-neoforge`), Loom 1.16 via `dev.kikugie.loom-back-compat` (`build.fabric.gradle.kts`), ModDevGradle
  (`build.neoforge.gradle.kts`). Versions per MC version/loader in `stonecutter.properties.toml` (`deps.*`).
- `gradlew build` (all nodes), `gradlew :26.2-fabric:runClient` / `:26.2-neoforge:runClient` / `:26.3-fabric:runClient` / `:26.3-neoforge:runClient`,
  `gradlew :26.2-fabric:plannerTest` / `:26.3-fabric:plannerTest`
  (Minecraft-free planner self test, must print "All planner tests passed"), `gradlew :26.2-fabric:runGametest` /
  `gradlew :26.3-fabric:runGametest` (Fabric game
  tests on a headless server, must print "All N required tests passed"; source set `gametest` = `src/gametest/{java,resources}`,
  own test mod `tsautocraft-gametest`, not in the mod jar; setups built in code in `gametest/AutocraftGameTests`, report
  `versions/<mc>-fabric/build/gametest/report.xml`; Tom's terminal `FACING` = side it connects from, the connector scans its
  inventories on its own ticks, so tests wait for `preview(...).ok()` before requesting). Jars: `versions/<node>/build/libs/tsautocraft-<loader>-<ver>+<mc>.jar`; `gradlew buildAndCollect` copies all of them to `build/libs/<mod.version>/`.
- GitHub Actions: `.github/workflows/build.yml` (push/PR: buildAndCollect + plannerTest + runGametest, jars and game test report as artifacts), `release.yml` (tag `<mod.version>` or `v<mod.version>`, e.g. `0.6.0`: GitHub release with the jars, notes = that version's CHANGELOG section).
- Runtime log: `run/logs/latest.log` (Fabric), `run-neoforge/logs/latest.log` (NeoForge).
- Loader split: shared code in `src/main`; `src/fabric/{java,resources}` and `src/neoforge/{java,resources}` hold classes with the
  SAME fully qualified names per loader (only one folder is compiled per node): `platform/Platform` (config dir, mod loaded,
  creative tab builder), `transfer/{Transaction,ItemVariant,FluidVariant,ItemStorage,FluidStorage}` (+ `FabricStorage`/`NeoStorage`),
  `util/FluidUtil`, entry points `fabric/TsAutocraftFabric(Client)` / `neoforge/TsAutocraftNeoForge(Client)`, `fabric.mod.json` /
  `META-INF/neoforge.mods.toml`, Fabric-only `tsautocraft.fabric.mixins.json` (REI mixin: Tom's NeoForge jar has no REI handler).
  Shared code never imports Fabric/NeoForge APIs: storage access goes through `transfer/Storage`/`StorageView` (Fabric method
  names; NeoForge amounts are int and fluids in mB, so only pass on amounts the loader gave you), events via `api/Event`.
- NeoForge registration: `ModContent` registers in its static initializer, run from the first `RegisterEvent`. Tooltips via
  `RegisterTooltipAppendersEvent`, framed models via `ModelEvent.ModifyBakingResult` + Tom's `BakedPaintedModel`, config screen
  via `IConfigScreenFactory`, JEI/Jade/REI plugins by annotation (`@JeiPlugin`, `@WailaPlugin`, `TsAutocraftReiPluginNeoForge`).
- Version-specific code in `src/main`: Stonecutter comments (`//? if <26.3 {` / `//? if >=26.3 {`), switch the active node with the
  Gradle task "Set active project to ..." and "Reset active project" (= `26.3-fabric`) before committing. A new MC version:
  `match("<mc>", "fabric", "neoforge")` in `settings.gradle.kts` + its sections in `stonecutter.properties.toml`. For 26.2,
  Tom's Storage 2.11.3 is the available NeoForge build (Fabric uses 2.11.4). Shared 26.2 API branches cover Minecraft differences.
- Forge: not possible, Tom's Storage has no Forge build after 1.20.1.

## Status (2026-11-12)

- 0.7.0 in preparation (not released, not tested in game): MC 26.2 support (PR #2), review fixes (fluid units are
  never voided, extension pull surplus goes back, fluid unit hidden in JEI/REI), antislop GUI/copy pass (recipe tree
  legend/keyboard, confirm clicks for cancel/clear, guide keyboard TOC). antislop audit: `anti-slop/audit-001-*.md`.
- Fabric 26.2 (`26.2-fabric`) passes both planner and game tests (7 required tests); `buildAndCollect` builds all four nodes.
- NeoForge 26.2 (`26.2-neoforge`) compiles and packages against Tom's Storage 2.11.3; runtime has not been tested in game.
- Fabric 26.3 passes 7 required game tests; NeoForge 26.3 passes 6; both 26.2 and 26.3 planner tests and `buildAndCollect` pass.
- 0.2.0 - 0.5.0 tested in game by the user (2026-11-09). Feature scope agreed as complete after 0.5.0 (no notification on
  job end, no fuzzy matching, no own assembler block, no job size limits, no history search, no crafting monitor, no
  per-request pattern choice, no pattern text export, no JEI hotkey request); next: new MC versions (26.4 when Tom's has it).
- 0.5.0 (2026-11-09, last additions not tested in game yet): Memory Card, recipe tree graph, history "request again",
  "storage full" reason, Jade throughput, `/tsautocraft` command, persistent job ids, game tests. Earlier in 0.5.0: provider
  targets need item/fluid storage or a machine profile (`isTarget`); Ctrl shows pattern outputs in every container slot
  (`mixin/client/AbstractContainerScreenMixin`); `CraftRequestScreen` compact amount view (`H_INPUT`, rebuilt on preview
  switch), math via `util/MathExpr` (26.3 `EditBox` has no `setFilter`).
- 26.3 API notes: GUI uses `GuiGraphicsExtractor`, `extractBackground/extractLabels/extractRenderState`, `MouseButtonEvent`,
  `KeyEvent`; `Inventory.placeItemBackInInventory(stack, Prediction)`; `Item.appendHoverText` and
  `BlockEntity.removeComponentsFromTag` are deprecated. When unsure, check Tom's Storage source (github.com/tom5454/Toms-Storage,
  branch `snapshot` = 26.3) or the merged MC jar in `.gradle/loom-cache/minecraftMaven/.../minecraft-merged-*-26.3.jar`
  (`javap`; on Windows use `cygpath -w` and `;` in the class path).
- 26.2 compatibility branches keep the brewing pattern using `MinecraftServer.potionBrewing()` and legacy fuel APIs;
  `BaseEntityBlock` subclasses provide a unit codec only on 26.2, and inventory returns use the one-argument method.

## Architecture

- `planner/CraftingPlanner` – pure Java recursive planner (virtual stock, surplus reuse, cycle/depth guard, fallback patterns). Tested by `PlannerSelfTest`.
- `job/CraftingJob` – planned job, persisted (Codec) in its owner BE; states RUNNING -> WAITING -> DONE/CANCELLED (+ priority, paused).
  Dispatched crafts are `inFlight` per step until the Importer reports the results (`TerminalCrafting.itemsArrived`, by primary output).
  Once a job got such a report it is DONE exactly when nothing is in flight; otherwise stock/idle fallback (results via plain chests).
- `network/NetworkLink` – finds Tom's `IInventoryConnector` via `InventoryCableNetwork` (like Tom's Inventory Interface) and collects Pattern Providers on the network.
- Blocks: `NodeBlock` (full block node), `DirectionalNodeBlock` (full block with FACING), `PatternProviderBlock` (NodeBlock + POWERED),
  `ConnectorNodeBlock` (shape/model like Tom's Inventory Cable Connector, FACING + 6 connection props + POWERED),
  `FramedConnectorNodeBlock` (full hitbox, `IPaintable`, BE must extend Tom's `PaintedBlockEntity`; client wraps models with Tom's `UnbakedPaintedModel`).
- Blocks never expose item storage, so Tom's Inventory Connector does not pick them up as storage.
- Pattern Terminal = Tom's terminal: `PatternTerminalBlock extends AbstractStorageTerminalBlock`, BE extends `StorageTerminalBlockEntity`,
  menu extends `StorageTerminalMenu`, screen extends `AbstractStorageTerminalScreen` (GUI = Tom's crafting_terminal.png with encoding area).
  Model parents `toms_storage:block/crafting_terminal`. Every BE on a block using Tom's `TickerUtil` must implement `TickableServer`.
- Requests from Tom's terminals via mixins (`tsautocraft.mixins.json`): `StorageTerminalMenuMixin` syncs craftables (`tsac_craft`) and handles
  `tsac_req`; `AbstractStorageTerminalScreenMixin` marks craftables (green +) and opens `CraftRequestScreen` (click on amount 0 / Ctrl+click).
  Jobs run in an Autocrafting Terminal on the same cable network (`TerminalCrafting`).
- 26.3: `Minecraft.gui.setScreen(...)` (not `Minecraft.setScreen`).
- Multipart blockstates: the first part must have a `particle` texture (Tom's `cable_closed/open` have none) -> `*_particle` models first.
- Blocks with a GUI that are `IPaintable` must return `PASS` from `useItemOn` for Tom's `PaintKitItem`, else the GUI eats the click.
- Dev client has JEI + MezzConfig (`localRuntime`, Modrinth version ids in `gradle.properties`); JEI API is `compileOnly`,
  plugin `compat/jei/TsAutocraftJeiPlugin` via entrypoint `jei_mod_plugin`: "+" for Pattern Terminal (fills grid), Pattern Provider
  (encodes into a free slot, uses a Blank Pattern from the inventory), Requester (output -> entry); ghost drag for Pattern Terminal + Requester.
  Encoding logic shared in `pattern/PatternEncoder`.
- Autocrafting Terminal is a Tom's terminal block too (`AutocraftingTerminalBlock`, model parent `toms_storage:block/storage_terminal`).
  It lists the jobs of all job owners on the network (`CraftingJob.uid` = runtime id for cancel/pause/priority).
- Requests go through `CraftRequestProtocol` (`tsac_plan` -> material list preview, `tsac_req` -> start); UI = `CraftRequestScreen`.
- Block/item textures are generated from Tom's textures (wood panels, `inventory_interface` frame, `item_filter` for patterns).
- The dev client often dies natively (0xC0000005, no hs_err even with `-XX:ErrorFile`, no Windows event) during the
  resource reload, also without this mod/JEI, with any graphics backend, muted audio and fewer worker threads.
  Cause unknown (machine/driver/overlay); just retry. `gradlew runClient -PnoJei` starts without JEI, `-PnoJade` without Jade, `-PwithModMenu` adds Mod Menu (off by default, the native crash seemed more frequent with it).
- Config `config/tsautocraft.json` (`TsAutocraftConfig`, Gson, read at startup, written by the Mod Menu screen
  `compat/modmenu` built by reflection over the public fields, labels `config.tsautocraft.<field>[.desc]`; Cloth Config ships
  inside Tom's jar): `crafterTrigger` = `external` (default, player's
  redstone circuit crafts; no stuck cleanup) | `direct` (scheduled block tick on the crafter) | `redstone` (2-tick pulse; the
  provider emits only in this mode, with a Redstone Upgrade or for a machine profile pulse, per side:
  `PatternProviderBlockEntity.emitsSignal(Direction)`); `enableDisabledCrafterSlots` (default false = recipe is shifted to enabled crafter slots);
  plus timings/limits (`crafter*Ticks`, `maxJobsPerTerminal`, `maxRequestAmount`, `job*Seconds`, `requester*`, `io*`,
  `craftablesSyncTicks`) - no more hard-coded constants for these, read them via `TsAutocraftConfig.get()`.
- Blocking off: crafting patterns stack more sets of the same recipe/layout into a crafter (`fitOnTop`).
- Substitutes (toggle only in the Pattern Terminal; the Pattern Provider's JEI "+" encodes exact patterns): `PatternData.Entry.alternatives`, computed at encode
  time by testing every registry item at that grid position against the recipe (same result -> substitute, max 32).
  Planner uses the best-stocked option per position (`CraftingJob.resolvedInputs`; none in stock -> first craftable option;
  failed plan with substitutes -> second `PlanIndex(..., preferCraftable)` try), the provider picks per craft (`chooseInputs`).
  Crafting patterns only; processing patterns stay exact.
- Pattern Provider full block (`PatternProviderBlock extends NodeBlock`, no front): supplies every adjacent crafter/machine
  round robin (`targetSides`, never cables/`IInventoryCable`/connectors); connector variants keep their `FACING`. Cooldown and
  redstone signal are per side (`lastCrafter`, `signalTicks`): a pulse only reaches the machine that was just filled.
- Any pattern also works on a foreign inventory (hopper, chest, ...): crafting/smelting/brewing/fluid modes fall back to
  `BuiltinPatternModes.dispatchInsert` (all-or-nothing sets, optional inputs like brewing fuel left out) when the target is not
  their machine / has no tank.
- Provider name in lists/job status: `PatternProviderBlockEntity.listName()` = anvil name, else the supplied machine(s), else own block name (cached 20 ticks).
- Pattern Provider: crafting patterns fill `minecraft:crafter` slots 0-8 in pattern order (shifted if needed), trigger per config;
  processing patterns insert all-or-nothing, optional blocking mode. Results return via Importer.
- Provider lock modes (`PatternProviderBlockEntity.LockMode`, GUI 2nd button row, Jade, saved): OFF | UNTIL_RESULT (per side
  `awaitKey/Count/Since` = primary output of the last delivery; `TerminalCrafting.itemsArrived` -> `providerResults` ->
  `CraftingProvider.resultsArrived`, FIFO by `awaitingSince`, independent of jobs; any Importer arrival of that item counts;
  timeout `providerLockTimeoutSeconds`) | REDSTONE_HIGH/LOW (`hasNeighborSignal`). Provider priority (-999..999, GUI -/+,
  shift = 10): `NetworkLink.providers` sorts by `providerPriority()` desc (stable) -> dispatch order and planner pattern order.
- Cancel cleanup (`returnInputsOnCancel`): `CraftingJob.cancel` -> `TerminalCrafting.returnInputs(uid)` ->
  `CraftingProvider.returnInputs`. Jobs dispatch with `dispatch(..., uid)`; the provider remembers per side the job whose
  deliveries filled it since it last held no pattern inputs (`sideJob` = `CraftingJob.persistentId` UUID, `MIXED_JOBS` =
  several; saved with `sidePattern`/`sideCrafts` as `SideJobs`). Runtime `uid` -> persistent id via
  `CraftingJob.persistentIdOf` (registered on `bind`, so preview plans never are; removed on DONE and after a cancel's return).
  Returns: crafter contents if they match the pattern and no craft tick is scheduled (+ `pendingTriggers` cleared), furnace
  input slot, processing: full input sets extractable via the insert face (not with machine profiles, fluid, brewing,
  third-party modes). Partly consumed sets stay; results of running crafts are no longer credited to the cancelled job.
- Pattern modes are an API: `api/PatternMode` + `api/PatternModes.register(...)` (built-ins in `pattern/BuiltinPatternModes`:
  crafting, processing, smelting, fluid, brewing; only modes for blocks that can be automated, no stonecutter/smithing). `PatternData.mode` is the mode id (old patterns: `crafting`/`smelting` flags in the codec).
  A mode encodes (`encode`, `computeOutputs`), delivers (`dispatch(DispatchContext, pattern)`, items only via `ctx.pull`)
  and claims JEI recipes / REI categories. Menu sync by registry index; the Pattern Terminal button shows `mode.icon()`.
- Job execution: `api/CraftingProvider` (Pattern Provider, Crafting Emitter) and `api/JobOwner` (Autocrafting Terminal,
  Requester, Exporter with Crafting Upgrade). `job/Reservations`: jobs reserve their raw materials (planner `fromStock`) and
  intermediates their own crafts brought back; plans, other jobs and Exporters only see stock minus reservations.
- `job/JobScheduler`: owners only `submit` themselves every `JobRunner.INTERVAL` ticks; at `END_LEVEL_TICK` all jobs of the
  level tick sorted by priority (high/normal/low, then uid) via `owner.tickJob` -> `JobRunner` (net/providers/reservations
  cached per tick). New jobs: `JobScheduler.started`. Jobs can be paused (no reservations, reason PAUSED) and prioritized
  from the Autocrafting Terminal (`pause`/`prio` messages). Every step keeps a transient reason (missing item have/need,
  provider pos/name) shown in the job tooltip (`StepView`).
- 0.6.0 (released, tag `0.6.0`): API 4 + JEI/REI fluid ingredients as
  buckets (`compat/FluidIngredients`, reflection on the viewers' fluid objects) + `api/TerminalReach` (hooked into
  `util/TerminalAccess`).
- API 4 (for Tom's Storage Network `D:\IdeaProjects\TomsStorageNetwork`):
  `api/StockExtensions` (hooked into `StorageUtil.count/pull/push`; `baseCount/basePull/basePush` = Tom's storage only,
  extensions add stock and fill pulls; push may be taken first), `api/NetworkBridges` (`NetworkCache.Snapshot` follows
  them into other levels, BFS by block entity identity), `PatternMode.maxInputs/maxOutputs` (JEI/REI limits; > 9 only
  via the Pattern Provider "+", message `msg.tsautocraft.jei.provider_only`), `PatternEncoder.encodeFree(..., max, max)`.
  Also API 4 (all 0.6.0 API additions are one version, no separate 5/6/7):
  `api/FluidUnits` + hidden item `tsautocraft:fluid_unit` (`FluidUnitItem`, component `tsautocraft:fluid_unit` =
  fluid id, count = mB): `FluidUtil.content/emptyInto` (both loaders) treat it as 1 mB per item, `deliverFluidSet`
  empties a unit stack at once, `FluidIngredients` encodes units when `FluidUnits.enabled()` (set by a stock extension),
  `StorageUtil.basePush` never puts units into Tom's storage.
  `TerminalReach.registerDeny/denied` (checked first in `util/TerminalAccess.stillValid`), used by Tom's Storage
  Network's Network Lock.
  `TsAutocraftApi.itemsArrived` (= `TerminalCrafting.itemsArrived`, what Importers call) for Tom's Storage
  Network's Fluid Importer (fluid from a machine into tanks credits filled buckets).
- Public API package `api/` (+ `TsAutocraftApi.VERSION`, `gradlew build` also makes `-api.jar` with the data types it uses):
  `CraftingEvents` (PLAN_CREATED, JOB_STARTED, STEP_DISPATCHED, JOB_FINISHED), `Upgrades` (host -> allowed items + max count;
  `UpgradeHost`), `MachineProfiles` (datapack `data/<ns>/tsautocraft/machines/*.json`: `blocks`, `sides`, `redstone_pulse`,
  `blocking`, `routes` [per input by item/#tag/`fuel` cooking|brewing|any (item components `COOKING_FUEL`/`BREWING_FUEL`)/pattern position -> sides, first match wins], `cooldown` [ticks between deliveries, checked per side in the provider]; built-in furnace family, brewing stand, dispenser/dropper; smelting/brewing pattern modes set slots directly and ignore profiles), `PlannerHooks` (pattern order, craftable/substitute filters; item tags
  `tsautocraft:not_autocraftable`, `tsautocraft:no_substitute`), `IOMode`/`IOModes` (Exporter/Importer: round robin,
  priority, random, blacklist [Importer]).
- Brewing mode (26.3 brewing is data-driven: `RecipeType.BREWING`, `BrewingRecipe`, `BrewingInput(input, reagent)`): positional 9
  inputs like the brewing stand GUI (`PatternEncoder.BREW_FUEL`=0, `BREW_REAGENT`=1, bottles 6-8; other positions disabled via
  `PatternMode.gridSlotEnabled`, drawn as a brewing stand in `PatternTerminalScreen.extractGridLayout`). Fuel is an
  `optionalInput` (left out of `inputTotals`/planner/reservations; delivered from unreserved stock only into an empty fuel slot).
  JEI/REI "+" encodes 3 bottles; the Importer skips a brewing stand while slot 3 holds a reagent.
- Fluids: Tom's has none, so they travel as filled containers. Fluid pattern mode empties containers into the target's
  `FluidStorage`, empties go back; Importer with a filled container as filter fills empty containers from the network
  (`util/FluidUtil`). Target with only a tank (cauldron) + only empty-container inputs: `dispatchFill` fills them from the
  tank itself (not enough fluid = BUSY); results via `DispatchContext.result` -> provider `results`, pushed + `itemsArrived`
  next tick (same tick would be before the job counts the craft in flight).
- Item tooltips: `Item.appendHoverText` and `BlockEntity.removeComponentsFromTag` are deprecated in 26.3. Tooltips come from
  `TooltipProvider` components registered in Fabric's `ItemComponentTooltipProviderRegistry` (`PatternData`, default
  component `ItemDescription` on block items/upgrades, key `tooltip.tsautocraft.<id>`).
- Exporter/Importer filters (`IOMenu.clicked`): plain items are ghosts, Tom's filter items (`IItemFilter`) are real items
  (taken out again, dropped on break); JEI/REI `setPhantom` only places ghosts.
- Anvil names: our BEs keep `CUSTOM_NAME` (`util/CustomName`, loot tables `copy_components`), used as GUI title, Jade and
  provider name in job status; re-encoding a named pattern keeps its name.
- Wireless: our terminals are in `toms_storage:remote_activate`; `util/TerminalAccess` uses the Wireless Terminal range.
- Jade: `compat/jade` (entrypoint `jade`), BEs implement `blockentity/StatusInfo`, lines sent as components. WTHIT/EMI have
  no 26.3 build (2026-11-02).
- Upgrades (`util/UpgradeContainer`, `menu/UpgradeSlot`, panel right of the GUI via `client/GuiUtil`; same kind stacks in a
  slot up to the max from `ModContent`'s `Upgrades.register`): Capacity (terminal
  +`jobsPerCapacityUpgrade` jobs, provider +9 pattern slots up to 36), Speed (IO interval >> n, provider +1 delivery/tick),
  Stack (IO items << 2n, provider 4^n crafts per delivery up to 64), Crafting (Exporter crafts missing filter items).
  Redstone (Importer/Exporter: `RS_HIGH`/`RS_LOW`/`RS_TOGGLE`, checked every tick in `AbstractIOBlockEntity.redstoneAllows`, button in
  `IOScreen` only with the upgrade; Pattern Provider: `ctx.redstonePulse(crafterPulseTicks)` after every delivery).
- Crafting Emitter (full block, signal on all sides; directional variant = `ConnectorNodeBlock`/framed with `emitsPulse`, signal only
  out of the front; BE extends `PaintedBlockEntity`): redstone below/above an amount, or "craft" mode = pattern without inputs, signal while a job needs it.
- Pattern Terminal amount scaling (free-amount modes only): buttons x2, /2, x10, /10 right of the encoding area (outside the GUI,
  JEI/REI exclusion area), menu button ids `BTN_SCALE`+i -> `PatternTerminalBlockEntity.scaleAmounts(mul, div)`; disabled when
  `PatternTerminalMenu.canScale` fails (amount not whole, < 1 or > `MAX_PROCESSING_COUNT`), no clamping (keeps ratios).
- Guide book `guide_book` (`GuideBookItem`, book + blank pattern): right click opens client-only `client/GuideScreen` via
  `GuideBookItem.opener`. Pages = `PAGES` list (id + icons), text in `assets/tsautocraft/guide/<lang>/<id>.txt` (line 1 =
  title, rest = text; selected language, fallback en_us; read in `init`); long texts
  are split into sheets automatically, the first sheets = table of contents (one column per sheet). Drawn as an open book:
  two sheets (pages of `PAGE_W`) side by side, `current` is always even, turning moves a spread; wide diagrams are scaled
  down to the page width via `g.pose()`. New page: add to `PAGES` + a file in every language folder.
  Pictures = `guide/diagrams/<id>.txt` (language independent, resource packs can replace them; one row per line, `#` comment):
  item icons (`mc:`/`ts:`/`ac:` ids), arrows `> < ^ v`, cables `-` `|` and `+` (junction/corner, joins neighbouring cables and items), `.` empty; drawn live
  above the text of the page's first sheet (no PNG screenshots).
- `CraftRequestScreen`: failed preview gets `tsac_max` (binary search `CraftingJob.maxPossible`) -> "Craft N" button.
  Recipe tree: `CraftingPlanner.plan(..., recordTree)` builds `Node`s (need -> stock/crafts/missing, children = pattern inputs;
  alternatives roll back their children); `CraftingJob.TreeLine` = flattened pre-order (max 200 lines / depth 16, empty item =
  "n more"), sent as `tsac_tree`; the "Tree" button opens `client/RecipeTreeScreen` (full screen graph: nodes = slot with
  icon + amount, frame red/blue/grey = missing/crafted/stored, subtree-width layout top-down, right-angled edges; drag /
  arrow keys pan, wheel zooms 50-400 % via `g.pose()` scale, "Center"/Home fits the visible tree into the view, click collapses (+N badge); starts collapsed from depth 2 except
  paths to missing items; "Start" returns to the request screen and calls its `start()`, data packets are forwarded to it).
- Job history: `AutocraftingTerminalBlockEntity` records its own finished jobs and watches the running jobs of the other owners
  on the network (scan every 20 ticks, keeps the job reference until it is finished) -> `JobHistoryEntry` list ("History",
  config `jobHistorySize`, newest first), synced as `hist`, tabs "Running/History" in the bar above the job list, `hclear` clears.
  Several terminals on one network each keep the network history. `CraftingJob.startTime` (codec `start`) set on first `bind`.
  "↻" (column `CANCEL_X`) on non-automatic history rows opens `CraftRequestScreen` with item and amount.
- Storage full: Importer (and provider `flushResults`) call `TerminalCrafting.storageFull` when a push leaves a rest;
  steps in flight for that primary output get `fullAt`, shown as `Reason.STORAGE_FULL` for 40 ticks (step and WAITING job).
- Throughput: `util/Throughput` (transient, 100-tick windows, EMA 0.3, idle windows decay): provider crafts/min (per
  dispatch), Importer/Exporter items/min, Jade line only when > 0.
- Admin command `command/TsAutocraftCommand` (`/tsautocraft reload|jobs|cancel <uid>`, `Commands.LEVEL_GAMEMASTERS`), registered
  by Fabric `CommandRegistrationCallback` / NeoForge `RegisterCommandsEvent`. Jobs come from `JobScheduler.knownOwners(level)`
  (weak set of every owner that submitted); `TsAutocraftConfig.reload()` keeps the old values if the file is broken.
- Memory Card (`MemoryCardItem`, component `MEMORY_CARD_COMPONENT` = `MemoryCardData(type, settings, filters, upgrades)`,
  recipe RGR/GCG/RGR with C = Blank Pattern): BEs implement `blockentity/MemoryCardHost` (`saveCard`/`loadCard` = settings
  without real items, `cardFilters()`, `cardUpgrades()`): Pattern Provider, Exporter/Importer (`AbstractIOBlockEntity`),
  Requester, Level Maintainer, Crafting Emitter (mode + amount only). Type = block entity type id (variants match).
  Real filters (`IItemFilter`) need an unconfigured one (no component patch) from the inventory, replaced ones go back
  (`placeItemBackInInventory`); upgrades are topped up from the inventory; creative creates them. `NodeBlock`/
  `ConnectorNodeBlock`/framed `useItemOn` return PASS for the card so `useOn` runs instead of the GUI.
- ETA: `job/JobEta` (transient) measures `doneCrafts` per 100-tick window, EMA 0.3; `JobView.eta` seconds (-1 unknown), shown
  in the job tooltip (`JobText.eta`).
- Craft missing (JEI/REI "+" in Tom's Crafting Terminal): client mixins on Tom's `jei/CraftingTerminalTransferHandler` and `rei/ReiTransferHandler`
  (not own handlers: JEI keeps one handler per menu+recipe type, last registered wins), applied only if the viewer is loaded
  (`compat/TsAutocraftMixinPlugin`). `client/CraftMissing` counts storage + inventory per input slot (one recipe set) and uses the
  synced craftables: tooltip lists craftable (blue) / missing (red) items, Ctrl+click sends `tsac_reqs` (one job per item,
  one rate limit token) -> `CraftRequestProtocol.receiveBatch`, result as actionbar. Tom's handler still fills the grid.
- REI: API compile only (`me.shedaniel:RoughlyEnoughItems-api-fabric` + `cloth basic-math`), plugin `compat/rei` via
  entrypoint `rei_client`, not in the dev client (untested in game). Ghost targets shared in `client/GhostTargets`.
  EMI has no 26.3 build (2026-11-01).
- Published on Modrinth (https://modrinth.com/mod/toms-storage-autocrafting) and CurseForge
  (https://www.curseforge.com/minecraft/mc-mods/toms-storage-autocrafting).
- Stop the dev client before `gradlew build`: rebuilding under a running game swaps the classes (crash with
  `NoClassDefFoundError`, e.g. `JobScheduler$Entry`). Start it again only when the user asks.
- IntelliJ may save stale buffers over files changed on disk (happened to `ModContent.java`): if edits vanish, check the file.
- Pixel art: inner areas of the 16px textures are 8px (even), so a 3x3 grid of 1px dots cannot be centered; use 2x2 dots with
  1px gaps (crafting upgrade, provider front).
- Pattern Access Terminal (`PatternAccessTerminalBlock extends AbstractStorageTerminalBlock`, wireless): lists all Pattern Providers of
  the network; 6x9 real slots mapped server side onto the visible provider slots (scroll + client-side filter sent via data packet).
- Level Maintainer (`LevelMaintainerBlockEntity`, `JobOwner`, separate from the Requester): 12 entries item/threshold/batch,
  config `maintainer*`, Speed + Redstone Upgrade (blocks only new jobs).
- Crafter triggers: a crafter crafts once per trigger, so extra sets of one delivery are owed in
  `PatternProviderBlockEntity.pendingTriggers` (saved) and retriggered one per tick. `DispatchContext.undoPull` = delivery failed after
  pull (not consumed). Results are credited FIFO over all jobs (`CraftingJob.waitingSince`); `tracked` is saved. Requester/Maintainer
  never replan a job with crafts in flight. `tsac_plan`/`tsac_req` are rate limited per player (`CraftRequestProtocol.takePlan`).
- Performance: Tom's `countItems` walks every slot, so `StorageUtil.count` uses one count map per network storage (rebuilt when
  Tom's `getChangeTracker` changes). Always pull/push network items through `StorageUtil.pull/push/pullAll` (they adjust the map),
  never `pullMatchingStack`/`pushStack` directly. Provider target sides are cached 20 ticks (`targetMask`, dropped in `NodeBlock.neighborChanged`);
  the POWERED look uses `UPDATE_CLIENTS` only (signal changes call `updateNeighborsAt` themselves).
  `network/NetworkCache`: one snapshot per network (found by node position: Tom's walk from a node returns a new set
  each call, so identity keys never hit; max 20 ticks; `NetworkCache.invalidate()`
  on placement, provider priority change, job start) with nodes, connector, providers sorted by priority, owners, jobs (per tick);
  `NetworkLink` and `TerminalCrafting` delegate to it. `Reservations.live(jobs)` sums the jobs' reservations on lookup.
  `PatternData` carries a `Derived` component (cached hash, totals, `primaryKey`; excluded from equals). Planning uses
  `CraftingJob.PlanIndex` (byOutput, stock memo, substitutes resolved lazily), shared by the `maxPossible` binary search.
  Epochs in `NetworkCache`: `invalidate()` (snapshot), `jobsChanged()` (job list, from `JobScheduler.started`), `patternsChanged()`
  (cached `Snapshot.craftables()`, called wherever a provider's `patternCache`/emitter `pattern` resets). `Reservations.live` caches
  its totals by `CraftingJob.reservationVersion()` (bump it on every change of `reserved`/`paused`). Jobs skip all providers when
  `Step.canCraftOnce` fails (patterns without substitutes). Failed plans back off (`failPlan`, up to 8x) in Exporter, Requester,
  Maintainer. Menus send only on change via `util/SyncHash` (record/list/ItemStack content hash). `MachineProfiles.find` is memoized
  per BlockState. Substitute encoding only tests items an ingredient accepts. Client screens cache filtered lists/tooltips/row texts.
- Menus/screens sync through Tom's existing `toms_storage:data` packet (`NetworkHandler.sendTo/sendDataToServer`, menus implement `IDataReceiver`).
- Assets were generated by a Python script (pixel textures, GUI backgrounds, connector models derived from Tom's `cable_connector/*` models).
- NeoForge game tests (2026-11-11): `gradlew :26.3-neoforge:runGameTestServer`. Shared tests in `src/gametest/java`
  (Stonecutter adds it by source set name), `src/gametest-fabric/resources` (fabric.mod.json),
  `src/gametest-neoforge` (stand-in annotation `net.fabricmc.fabric.api.gametest.v1.GameTest`, test mod
  `tsautocraft_gametest` registering each annotated method as test function + instance, empty 8x8x8 structure), same as
  Tom's Storage Network.
- `command/TestSetup` (`/tsautocraft testsetup`, op like the whole command): stations every 10 blocks south of the
  player: crafting (Pattern Provider with log -> planks, planks -> sticks, cobblestone -> furnace; crafter, importer,
  Level Maintainer, Crafting Emitter, Requester), smelting (furnace + importer below), importer / exporter, all items.
  Tom's terminals sit south of their connector with FACING north. Game test `testSetup`.
