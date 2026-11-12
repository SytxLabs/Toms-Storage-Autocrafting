package eu.sytxlabs.tsautocraft.pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.sytxlabs.tsautocraft.transfer.FluidStorage;
import eu.sytxlabs.tsautocraft.transfer.FluidVariant;
import eu.sytxlabs.tsautocraft.transfer.ItemStorage;
import eu.sytxlabs.tsautocraft.transfer.ItemVariant;
import eu.sytxlabs.tsautocraft.transfer.Storage;
import eu.sytxlabs.tsautocraft.transfer.StorageView;
import eu.sytxlabs.tsautocraft.transfer.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
//? if >=26.3 {
import net.minecraft.world.item.crafting.BrewingRecipe;
//?}
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import eu.sytxlabs.tsautocraft.api.CraftingProvider.DispatchResult;
import eu.sytxlabs.tsautocraft.api.MachineProfile;
import eu.sytxlabs.tsautocraft.api.MachineProfiles;
import eu.sytxlabs.tsautocraft.api.PatternMode;
import eu.sytxlabs.tsautocraft.blockentity.PatternProviderBlockEntity;
import eu.sytxlabs.tsautocraft.util.FluidUtil;
import eu.sytxlabs.tsautocraft.util.ItemKey;
import eu.sytxlabs.tsautocraft.util.StorageUtil;

/** The pattern modes of this mod. Registered by {@link eu.sytxlabs.tsautocraft.api.PatternModes}. */
public final class BuiltinPatternModes {
	private BuiltinPatternModes() {}

	/** Vanilla crafting recipe, delivered into a {@code minecraft:crafter}. */
	public static final PatternMode CRAFTING = new PatternMode() {
		@Override
		public Identifier id() {
			return PatternData.CRAFTING;
		}

		@Override
		public ItemStack icon() {
			return new ItemStack(Items.CRAFTER);
		}

		@Override
		public boolean fixedInputs() {
			return true;
		}

		@Override
		public boolean keepsGridPositions() {
			return true;
		}

		@Override
		public List<ItemStack> computeOutputs(Level level, List<ItemStack> grid) {
			return PatternEncoder.findRecipe(level, grid).map(r -> List.of(r.value().assemble(PatternEncoder.craftInput(grid)))).orElse(List.of());
		}

		@Override
		public PatternEncoder.Result encode(Level level, List<ItemStack> grid, List<ItemStack> outputs, boolean substitutes) {
			return PatternEncoder.encodeCrafting(level, grid, substitutes);
		}

		@Override
		public boolean isValid(PatternData d) {
			return PatternMode.super.isValid(d) && d.inputs().size() == 9;
		}

		@Override
		public List<ItemStack> transferInputs(Level level, List<ItemStack> inputs) {
			return PatternEncoder.layoutGrid(level, inputs);
		}

		@Override
		public boolean matchesRecipe(Object recipe) {
			return recipe instanceof RecipeHolder<?> h && h.value() instanceof CraftingRecipe;
		}

		@Override
		public boolean matchesReiCategory(Identifier category) {
			return category.getNamespace().equals("minecraft") && category.getPath().equals("plugins/crafting");
		}

		@Override
		public Outcome dispatch(DispatchContext ctx, PatternData pattern) {
			if (!(ctx.level().getBlockEntity(ctx.targetPos()) instanceof CrafterBlockEntity)) return dispatchInsert(ctx, pattern);
			return ((PatternProviderBlockEntity) ctx.provider()).dispatchCrafter(ctx, pattern);
		}
	};

	/** Any machine: all inputs are inserted at once, if everything fits. */
	public static final PatternMode PROCESSING = new PatternMode() {
		@Override
		public Identifier id() {
			return PatternData.PROCESSING;
		}

		@Override
		public ItemStack icon() {
			return new ItemStack(Items.HOPPER);
		}

		@Override
		public PatternEncoder.Result encode(Level level, List<ItemStack> grid, List<ItemStack> outputs, boolean substitutes) {
			return PatternEncoder.encodeFree(id(), grid, outputs);
		}

		@Override
		public Outcome dispatch(DispatchContext ctx, PatternData pattern) {
			return dispatchInsert(ctx, pattern);
		}
	};

	/**
	 * Like processing, but inputs that are filled fluid containers (water bucket, potion, mod tanks...) are emptied into
	 * the machine's fluid storage; the empty containers go back into the network. Fluid results come back through an
	 * Importer with a filled container as filter (it fills empty containers from the network).
	 */
	public static final PatternMode FLUID = new PatternMode() {
		@Override
		public Identifier id() {
			return PatternData.FLUID;
		}

		@Override
		public ItemStack icon() {
			return new ItemStack(Items.WATER_BUCKET);
		}

		@Override
		public PatternEncoder.Result encode(Level level, List<ItemStack> grid, List<ItemStack> outputs, boolean substitutes) {
			return PatternEncoder.encodeFree(id(), grid, outputs);
		}

		@Override
		public Outcome dispatch(DispatchContext ctx, PatternData pattern) {
			return dispatchFluid(ctx, pattern);
		}
	};

	/** Furnace, blast furnace or smoker: the input goes straight into the input slot. */
	public static final PatternMode SMELTING = new PatternMode() {
		@Override
		public Identifier id() {
			return PatternData.SMELTING;
		}

		@Override
		public ItemStack icon() {
			return new ItemStack(Items.FURNACE);
		}

		@Override
		public boolean fixedInputs() {
			return true;
		}

		@Override
		public List<ItemStack> computeOutputs(Level level, List<ItemStack> grid) {
			ItemStack r = PatternEncoder.smeltingResult(level, PatternEncoder.firstItem(grid));
			return r.isEmpty() ? List.of() : List.of(r);
		}

		@Override
		public PatternEncoder.Result encode(Level level, List<ItemStack> grid, List<ItemStack> outputs, boolean substitutes) {
			return PatternEncoder.encodeSmelting(level, grid);
		}

		@Override
		public boolean isValid(PatternData d) {
			return PatternMode.super.isValid(d) && d.inputs().stream().filter(e -> !e.isEmpty()).count() == 1;
		}

		@Override
		public boolean matchesRecipe(Object recipe) {
			return recipe instanceof RecipeHolder<?> h && h.value() instanceof AbstractCookingRecipe;
		}

		@Override
		public boolean matchesReiCategory(Identifier category) {
			return category.getNamespace().equals("minecraft") && (category.getPath().equals("plugins/smelting") || category.getPath().equals("plugins/blasting") || category.getPath().equals("plugins/smoking"));
		}

		@Override
		public Outcome dispatch(DispatchContext ctx, PatternData pattern) {
			return dispatchFurnace(ctx, pattern);
		}
	};

	/**
	 * Brewing stand, laid out like its GUI in the Pattern Terminal: fuel top left (optional, only delivered when the stand's
	 * fuel slot is empty), reagent top middle, 1-3 bottles in the bottom row; the other positions are not used.
	 */
	public static final PatternMode BREWING = new PatternMode() {
		@Override
		public Identifier id() {
			return PatternData.BREWING;
		}

		@Override
		public ItemStack icon() {
			return new ItemStack(Items.BREWING_STAND);
		}

		@Override
		public boolean fixedInputs() {
			return true;
		}

		@Override
		public boolean keepsGridPositions() {
			return true;
		}

		@Override
		public boolean gridSlotEnabled(int index) {
			return index == PatternEncoder.BREW_FUEL || index == PatternEncoder.BREW_REAGENT || index >= PatternEncoder.BREW_FIRST_BOTTLE;
		}

		@Override
		public boolean optionalInput(PatternData d, int index) {
			return index == PatternEncoder.BREW_FUEL && d.inputs().size() == 9;
		}

		@Override
		public List<ItemStack> computeOutputs(Level level, List<ItemStack> grid) {
			return PatternEncoder.brewingOutputs(level, grid);
		}

		@Override
		public PatternEncoder.Result encode(Level level, List<ItemStack> grid, List<ItemStack> outputs, boolean substitutes) {
			return PatternEncoder.encodeBrewing(level, grid);
		}

		@Override
		public boolean isValid(PatternData d) {
			if (!PatternMode.super.isValid(d) || d.inputs().size() != 9 || d.inputs().get(PatternEncoder.BREW_REAGENT).isEmpty()) return false;
			for (int i = PatternEncoder.BREW_FIRST_BOTTLE; i < 9; i++) if (!d.inputs().get(i).isEmpty()) return true;
			return false;
		}

		/** JEI/REI show one bottle: a pattern always brews a full stand. */
		@Override
		public List<ItemStack> transferInputs(Level level, List<ItemStack> inputs) {
			PatternEncoder.Brew b = PatternEncoder.findBrew(level, inputs);
			if (b == null) return inputs;
			List<ItemStack> grid = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
			grid.set(PatternEncoder.BREW_REAGENT, b.reagent());
			for (int i = PatternEncoder.BREW_FIRST_BOTTLE; i < 9; i++) grid.set(i, b.input());
			return grid;
		}

		@Override
		public boolean matchesRecipe(Object recipe) {
			//? if >=26.3 {
			if (recipe instanceof RecipeHolder<?> h && h.value() instanceof BrewingRecipe) return true;
			//?}
			return isJeiBrewing(recipe.getClass());
		}

		@Override
		public boolean matchesReiCategory(Identifier category) {
			return category.getNamespace().equals("minecraft") && category.getPath().equals("plugins/brewing");
		}

		@Override
		public Outcome dispatch(DispatchContext ctx, PatternData pattern) {
			return dispatchBrewing(ctx, pattern);
		}
	};

	/** JEI's own brewing recipe type (by name: JEI is optional at runtime). */
	private static boolean isJeiBrewing(Class<?> c) {
		for (; c != null; c = c.getSuperclass()) {
			for (Class<?> i : c.getInterfaces()) if (i.getName().equals("mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe")) return true;
		}
		return false;
	}

	/**
	 * Brewing: bottles into slots 0-2, the reagent into slot 3, only into an empty stand (one brew at a time, Stack Upgrades
	 * do not apply). The pattern's fuel goes into slot 4 only while it is empty, from unreserved stock and outside the job
	 * (not planned per craft). The Importer waits until the reagent is used up, so it never takes unfinished potions.
	 */
	public static PatternMode.Outcome dispatchBrewing(PatternMode.DispatchContext ctx, PatternData p) {
		//noinspection resource
		if (!(ctx.level().getBlockEntity(ctx.targetPos()) instanceof BrewingStandBlockEntity stand)) return dispatchInsert(ctx, p);
		if (p.inputs().size() != 9) return PatternMode.Outcome.fail(DispatchResult.NO_TARGET);
		for (int i = 0; i < 4; i++) if (!stand.getItem(i).isEmpty()) return PatternMode.Outcome.fail(DispatchResult.BUSY);
		List<ItemStack> want = new ArrayList<>(4);
		List<Integer> slots = new ArrayList<>(4);
		for (int i = PatternEncoder.BREW_FIRST_BOTTLE; i < 9; i++) {
			PatternData.Entry e = p.inputs().get(i);
			if (e.isEmpty()) continue;
			want.add(e.stack());
			slots.add(i - PatternEncoder.BREW_FIRST_BOTTLE);
		}
		want.add(p.inputs().get(PatternEncoder.BREW_REAGENT).stack());
		slots.add(3);
		List<ItemStack> got = ctx.pull(want);
		if (got == null) return PatternMode.Outcome.fail(DispatchResult.MISSING);
		for (int i = 0; i < got.size(); i++) stand.setItem(slots.get(i), got.get(i));
		PatternData.Entry fuel = p.inputs().get(PatternEncoder.BREW_FUEL);
		if (!fuel.isEmpty() && stand.getItem(4).isEmpty() && ctx.available().applyAsLong(new ItemKey(fuel.item())) >= 1) {
			try {
				List<ItemStack> f = StorageUtil.pullAll(ctx.network(), ctx.level(), List.of(fuel.item().copyWithCount(1)));
				if (f != null) stand.setItem(4, f.getFirst());
			} catch (StorageUtil.LeftoverException e) {
				e.leftovers().forEach(ctx::giveBack);
			}
		}
		stand.setChanged();
		return PatternMode.Outcome.ok(1);
	}

	/**
	 * Processing: one set after the other into the target's item storage, each set must fit completely.
	 * Blocking mode: only while none of the pattern's inputs are left in the target. A {@link MachineProfile} of the target may choose the faces to insert through (per input with routes), force blocking and ask for a redstone pulse after the delivery.
	 */
	public static PatternMode.Outcome dispatchInsert(PatternMode.DispatchContext ctx, PatternData p) {
        //noinspection resource
        MachineProfile profile = MachineProfiles.find(ctx.level().getBlockState(ctx.targetPos()));
		// One stack per input (split at the stack size), each with the faces it goes through
		List<ItemStack> in = new ArrayList<>();
		List<List<Storage<ItemVariant>>> where = new ArrayList<>();
		java.util.Map<List<Direction>, List<Storage<ItemVariant>>> bySides = new java.util.HashMap<>();
		Set<Storage<ItemVariant>> all = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		int index = 0;
		PatternMode mode = p.modeType();
		for (int i = 0; i < p.inputs().size(); i++) {
			PatternData.Entry e = p.inputs().get(i);
			if (e.isEmpty() || mode.optionalInput(p, i)) continue; // e.g. brewing fuel: not planned, so not delivered here
			List<Storage<ItemVariant>> targets = bySides.computeIfAbsent(profile.sidesFor(e.item(), index++, ctx.level()), sides -> storagesFor(ctx, sides));
			if (targets.isEmpty()) return PatternMode.Outcome.fail(DispatchResult.NO_TARGET);
			all.addAll(targets);
			for (int left = e.count(), max = Math.max(1, e.item().getMaxStackSize()); left > 0; left -= max) {
				in.add(e.item().copyWithCount(Math.min(left, max)));
				where.add(targets);
			}
		}
		if (in.isEmpty()) return PatternMode.Outcome.fail(DispatchResult.NO_TARGET);
		if (profile.blocking().orElse(ctx.blocking()) && all.stream().anyMatch(t -> containsAnyInput(t, p))) {
			return PatternMode.Outcome.fail(DispatchResult.BUSY);
		}

		DispatchResult fail = DispatchResult.OK;
		int crafts = 0;
		for (int set = 0; set < ctx.maxCrafts(); set++) {
			if (!insertRouted(in, where, false)) {
				fail = DispatchResult.NO_SPACE;
				break;
			}
			List<ItemStack> got = ctx.pull(in);
			if (got == null) {
				fail = DispatchResult.MISSING;
				break;
			}
			if (!insertRouted(got, where, true)) {
				ctx.undoPull(got); // changed in between: back to storage, not a craft
				fail = DispatchResult.NO_SPACE;
				break;
			}
			crafts++;
		}
		if (crafts > 0) ctx.redstonePulse(profile.redstonePulse());
		return crafts > 0 ? PatternMode.Outcome.ok(crafts) : PatternMode.Outcome.fail(fail);
	}

	/** Item storages of the target through {@code sides} (empty = the face that points at the provider). */
	private static List<Storage<ItemVariant>> storagesFor(PatternMode.DispatchContext ctx, List<Direction> sides) {
		List<Storage<ItemVariant>> l = new ArrayList<>();
		if (sides.isEmpty()) {
			Storage<ItemVariant> s = StorageUtil.facingStorage(ctx.level(), ctx.providerPos(), ctx.facing());
			if (s != null) l.add(s);
		} else {
			for (Direction side : sides) {
				Storage<ItemVariant> s = ItemStorage.SIDED.find(ctx.level(), ctx.targetPos(), side);
				if (s != null) l.add(s);
			}
		}
		return l;
	}

	/** Inserts {@code stacks.get(i)} through {@code where.get(i)}, all or nothing. */
	private static boolean insertRouted(List<ItemStack> stacks, List<List<Storage<ItemVariant>>> where, boolean commit) {
		try (Transaction t = Transaction.openOuter()) {
			for (int i = 0; i < stacks.size(); i++) {
				if (!StorageUtil.insertAll(where.get(i), List.of(stacks.get(i)), t)) return false;
			}
			if (commit) t.commit();
			return true;
		}
	}

	/** Fluid processing: fluid containers are emptied into the target's fluid storage, other inputs are inserted. */
	public static PatternMode.Outcome dispatchFluid(PatternMode.DispatchContext ctx, PatternData p) {
		List<ItemStack> in = p.inputStacks();
		boolean[] fluid = new boolean[in.size()];
		boolean anyFluid = false, anyItem = false;
		for (int i = 0; i < in.size(); i++) {
			fluid[i] = FluidUtil.content(in.get(i)) != null;
			anyFluid |= fluid[i];
			anyItem |= !fluid[i];
		}
		Storage<FluidVariant> tank = FluidStorage.SIDED.find(ctx.level(), ctx.targetPos(), ctx.facing().getOpposite());
		Storage<ItemVariant> items = StorageUtil.facingStorage(ctx.level(), ctx.providerPos(), ctx.facing());
		// No tank (e.g. a hopper): the filled containers go in as items
		if (anyFluid && tank == null) return dispatchInsert(ctx, p);
		// Only a tank (e.g. a cauldron under dripstone): empty containers are filled from it right here
		if (!anyFluid && tank != null && items == null) return dispatchFill(ctx, p, in, tank);
		if (anyItem && items == null) return PatternMode.Outcome.fail(DispatchResult.NO_TARGET);
		if (ctx.blocking() && (anyItem && containsAnyInput(items, p) || anyFluid && FluidUtil.containsAny(tank, FluidUtil.fluidsOf(in)))) {
			return PatternMode.Outcome.fail(DispatchResult.BUSY);
		}

		DispatchResult fail = DispatchResult.OK;
		int crafts = 0;
		for (int set = 0; set < ctx.maxCrafts(); set++) {
			if (deliverFluidSet(in, fluid, tank, items, false) == null) {
				fail = DispatchResult.NO_SPACE;
				break;
			}
			List<ItemStack> got = ctx.pull(in);
			if (got == null) {
				fail = DispatchResult.MISSING;
				break;
			}
			List<ItemStack> empties = deliverFluidSet(got, fluid, tank, items, true);
			if (empties == null) {
				ctx.undoPull(got); // changed in between: everything back to storage, not a craft
				fail = DispatchResult.NO_SPACE;
				break;
			}
			empties.forEach(ctx::giveBack);
			crafts++;
		}
		return crafts > 0 ? PatternMode.Outcome.ok(crafts) : PatternMode.Outcome.fail(fail);
	}

	/**
	 * Fluid pattern on a target with a tank and no item storage, e.g. empty bucket -> lava bucket at a cauldron: every input
	 * must be an empty container that one of the outputs holds filled. The provider fills them from the tank (all or
	 * nothing, not enough fluid yet = busy) and hands them back as results.
	 */
	private static PatternMode.Outcome dispatchFill(PatternMode.DispatchContext ctx, PatternData p, List<ItemStack> in, Storage<FluidVariant> tank) {
		List<ItemStack> filledAs = new ArrayList<>(); // per input: the output it becomes
		List<FluidUtil.Content> contents = new ArrayList<>();
		for (ItemStack st : in) {
			ItemStack as = null;
			FluidUtil.Content content = null;
			for (PatternData.Entry e : p.outputs()) {
				if (e.isEmpty()) continue;
				FluidUtil.Content c = FluidUtil.content(e.item().copyWithCount(1));
				if (c != null && ItemStack.isSameItemSameComponents(c.empty(), st)) {
					as = e.item().copyWithCount(1);
					content = c;
					break;
				}
			}
			if (as == null) return PatternMode.Outcome.fail(DispatchResult.NO_TARGET);
			filledAs.add(as);
			contents.add(content);
		}
		if (in.isEmpty()) return PatternMode.Outcome.fail(DispatchResult.NO_TARGET);

		DispatchResult fail = DispatchResult.OK;
		int crafts = 0;
		for (int set = 0; set < ctx.maxCrafts(); set++) {
			if (fillSet(in, filledAs, contents, tank, false) == null) {
				fail = DispatchResult.BUSY; // not enough fluid yet
				break;
			}
			List<ItemStack> got = ctx.pull(in);
			if (got == null) {
				fail = DispatchResult.MISSING;
				break;
			}
			List<ItemStack> filled = fillSet(got, filledAs, contents, tank, true);
			if (filled == null) {
				ctx.undoPull(got);
				fail = DispatchResult.BUSY;
				break;
			}
			filled.forEach(ctx::result);
			crafts++;
		}
		return crafts > 0 ? PatternMode.Outcome.ok(crafts) : PatternMode.Outcome.fail(fail);
	}

	/** Fills every container of {@code stacks} from {@code tank} in one transaction; the filled ones, or null (nothing changed). */
	private static List<ItemStack> fillSet(List<ItemStack> stacks, List<ItemStack> filledAs, List<FluidUtil.Content> contents, Storage<FluidVariant> tank, boolean commit) {
		List<ItemStack> filled = new ArrayList<>();
		try (Transaction t = Transaction.openOuter()) {
			for (int i = 0; i < stacks.size(); i++) {
				ItemStack st = stacks.get(i);
				for (int n = 0; n < st.getCount(); n++) {
					ItemStack f = FluidUtil.fill(st.copyWithCount(1), contents.get(i).fluid(), filledAs.get(i), tank, t);
					if (f == null) return null;
					filled.add(f);
				}
			}
			if (commit) t.commit();
		}
		return filled;
	}

	/**
	 * Empties the containers marked in {@code fluid} into {@code tank} and inserts the other stacks into {@code items},
	 * in one transaction: all or nothing. Returns the empty containers, or null (nothing changed) if something did not fit.
	 * @param commit false = only test
	 */
	private static List<ItemStack> deliverFluidSet(List<ItemStack> stacks, boolean[] fluid, Storage<FluidVariant> tank, Storage<ItemVariant> items, boolean commit) {
		List<ItemStack> empties = new ArrayList<>();
		List<ItemStack> other = new ArrayList<>();
		try (Transaction t = Transaction.openOuter()) {
			for (int i = 0; i < stacks.size(); i++) {
				ItemStack st = stacks.get(i);
				if (!fluid[i]) {
					other.add(st);
					continue;
				}
				if (eu.sytxlabs.tsautocraft.api.FluidUnits.is(st)) {
					// Fluid unit (API 4): count mB in one go
					if (FluidUtil.emptyInto(st, tank, t) == null) return null;
					continue;
				}
				for (int n = 0; n < st.getCount(); n++) {
					ItemStack empty = FluidUtil.emptyInto(st.copyWithCount(1), tank, t);
					if (empty == null) return null;
					if (!empty.isEmpty()) empties.add(empty);
				}
			}
			if (other.stream().anyMatch(s -> !s.isEmpty()) && (items == null || !StorageUtil.insertAll(List.of(items), other, t))) return null;
			if (commit) t.commit();
		}
		return empties;
	}

	/** Smelting: the input goes into the furnace's input slot, several at once with Stack Upgrades. */
	public static PatternMode.Outcome dispatchFurnace(PatternMode.DispatchContext ctx, PatternData p) {
        //noinspection resource
        if (!(ctx.level().getBlockEntity(ctx.targetPos()) instanceof AbstractFurnaceBlockEntity furnace)) return dispatchInsert(ctx, p);
		List<ItemStack> in = p.inputStacks();
		if (in.size() != 1) return PatternMode.Outcome.fail(DispatchResult.NO_TARGET);
		ItemStack want = in.getFirst();
		ItemStack cur = furnace.getItem(0);
		int room = want.getMaxStackSize();
		if (!cur.isEmpty()) {
			if (ctx.blocking() || !ItemStack.isSameItemSameComponents(cur, want)) return PatternMode.Outcome.fail(DispatchResult.BUSY);
			room -= cur.getCount();
		}
		int per = Math.max(1, want.getCount());
		int n = Math.min(ctx.maxCrafts(), room / per);
		if (n <= 0) return PatternMode.Outcome.fail(DispatchResult.BUSY);
		long avail = ctx.available().applyAsLong(new ItemKey(want));
		n = (int) Math.min(n, avail / per);
		if (n <= 0) return PatternMode.Outcome.fail(DispatchResult.MISSING);
		List<ItemStack> got = ctx.pull(List.of(want.copyWithCount(n * per)));
		if (got == null) return PatternMode.Outcome.fail(DispatchResult.MISSING);
		ItemStack add = got.getFirst();
		furnace.setItem(0, cur.isEmpty() ? add : cur.copyWithCount(cur.getCount() + add.getCount()));
		furnace.setChanged();
		return PatternMode.Outcome.ok(n);
	}

	private static boolean containsAnyInput(Storage<ItemVariant> target, PatternData p) {
		Set<ItemKey> keys = new HashSet<>(p.inputTotals().keySet());
		for (StorageView<ItemVariant> view : target) {
			if (view.isResourceBlank() || view.getAmount() <= 0) continue;
			if (keys.contains(new ItemKey(view.getResource().toStack(1)))) return true;
		}
		return false;
	}

	/** All non-empty stacks of a grid, in order (inputs of modes without grid positions). */
	public static List<ItemStack> compact(List<ItemStack> grid) {
		List<ItemStack> l = new ArrayList<>();
		for (ItemStack st : grid) if (!st.isEmpty()) l.add(st);
		return l;
	}
}
