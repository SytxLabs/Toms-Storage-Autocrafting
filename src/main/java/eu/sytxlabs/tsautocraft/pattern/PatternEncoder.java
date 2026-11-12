package eu.sytxlabs.tsautocraft.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if <26.3 {
/*import net.minecraft.tags.ItemTags;
*///?}
//? if >=26.3 {
import net.minecraft.world.item.crafting.BrewingInput;
//?}
//? if <26.3 {

/*import net.minecraft.world.item.alchemy.PotionBrewing;
*///?}
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.api.PatternMode;
import eu.sytxlabs.tsautocraft.api.PlannerHooks;

public final class PatternEncoder {
	private PatternEncoder() {}

	public record Result(PatternData data, String error) {
		public static Result error(String key) {
			return new Result(null, key);
		}

		public static Result ok(PatternData data) {
			return new Result(data, null);
		}

		public ItemStack toItem() {
			ItemStack pattern = new ItemStack(ModContent.PATTERN);
			pattern.set(ModContent.PATTERN_COMPONENT, data);
			return pattern;
		}
	}

	public static CraftingInput craftInput(List<ItemStack> grid) {
		List<ItemStack> l = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			ItemStack st = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
			l.add(st.isEmpty() ? ItemStack.EMPTY : st.copyWithCount(1));
		}
		return CraftingInput.of(3, 3, l);
	}

	public static Optional<RecipeHolder<CraftingRecipe>> findRecipe(Level level, List<ItemStack> grid) {
		if (level == null || level.getServer() == null) return Optional.empty();
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftInput(grid), level);
	}

	public static final int MAX_SUBSTITUTES = 32;

	/** Furnace result for one {@code input} (smelting, then blasting, then smoking recipes), empty if none. */
	public static ItemStack smeltingResult(Level level, ItemStack input) {
		if (level == null || level.getServer() == null || input.isEmpty()) return ItemStack.EMPTY;
		SingleRecipeInput in = new SingleRecipeInput(input.copyWithCount(1));
		var rm = level.getServer().getRecipeManager();
		var r = rm.getRecipeFor(RecipeType.SMELTING, in, level).map(h -> h.value().assemble(in));
		if (r.isEmpty()) r = rm.getRecipeFor(RecipeType.BLASTING, in, level).map(h -> h.value().assemble(in));
		if (r.isEmpty()) r = rm.getRecipeFor(RecipeType.SMOKING, in, level).map(h -> h.value().assemble(in));
		return r.orElse(ItemStack.EMPTY);
	}

	/** Result of brewing {@code reagent} into one bottle of {@code input}, empty if there is no brewing recipe. */
	public static ItemStack brewingResult(Level level, ItemStack input, ItemStack reagent) {
		if (level == null || level.getServer() == null || input.isEmpty() || reagent.isEmpty()) return ItemStack.EMPTY;
		//? if >=26.3 {
		BrewingInput in = new BrewingInput(input.copyWithCount(1), reagent.copyWithCount(1));
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.BREWING, in, level).map(h -> h.value().assemble(in)).orElse(ItemStack.EMPTY);
		//?}
		//? if <26.3 {
		/*PotionBrewing brewing = level.getServer().potionBrewing();
		return brewing.hasPotionMix(input, reagent) ? brewing.mix(reagent, input) : ItemStack.EMPTY;
		*///?}
	}

	/** A brew found in a grid: {@code bottles} (1-3) bottles of {@code input} plus one {@code reagent} give {@code bottles} times {@code result}. */
	public record Brew(ItemStack input, ItemStack reagent, ItemStack result, int bottles) {}

	/** Tries every stack of the grid as reagent against every other one as bottle; null if no brewing recipe matches. */
	public static Brew findBrew(Level level, List<ItemStack> grid) {
		for (ItemStack reagent : grid) {
			if (reagent.isEmpty()) continue;
			for (ItemStack input : grid) {
				if (input.isEmpty() || ItemStack.isSameItemSameComponents(input, reagent)) continue;
				ItemStack result = brewingResult(level, input, reagent);
				if (result.isEmpty()) continue;
				int bottles = 0;
				for (ItemStack st : grid) if (ItemStack.isSameItemSameComponents(st, input)) bottles += st.getCount();
				return new Brew(input.copyWithCount(1), reagent.copyWithCount(1), result.copyWithCount(1), Math.clamp(bottles, 1, 3));
			}
		}
		return null;
	}

	/** Grid positions of a brewing pattern (like the brewing stand GUI): fuel top left, reagent top middle, bottles in the bottom row. */
	public static final int BREW_FUEL = 0, BREW_REAGENT = 1, BREW_FIRST_BOTTLE = 6;

	/** Brewed potion per bottle position of a brewing grid (one stack per bottle), empty if a bottle does not brew with the reagent. */
	public static List<ItemStack> brewingOutputs(Level level, List<ItemStack> grid) {
		if (grid.size() < 9) return List.of();
		ItemStack reagent = grid.get(BREW_REAGENT);
		List<ItemStack> out = new ArrayList<>(3);
		for (int i = BREW_FIRST_BOTTLE; i < 9; i++) {
			if (grid.get(i).isEmpty()) continue;
			ItemStack r = brewingResult(level, grid.get(i), reagent);
			if (r.isEmpty()) return List.of();
			out.add(r.copyWithCount(1)); // potions do not stack: one output slot per bottle
		}
		return out;
	}

	/** Brewing pattern from the brewing grid: reagent, 1-3 bottles (may differ), optional fuel; one brewed potion per bottle. */
	public static Result encodeBrewing(Level level, List<ItemStack> grid) {
		if (grid.size() < 9 || grid.get(BREW_REAGENT).isEmpty()) return Result.error("msg.tsautocraft.encode.no_brewing");
		List<ItemStack> results = brewingOutputs(level, grid);
		if (results.isEmpty()) return Result.error("msg.tsautocraft.encode.no_brewing");
		ItemStack fuel = grid.get(BREW_FUEL);
		//? if >=26.3 {
		if (!fuel.isEmpty() && !fuel.has(net.minecraft.core.component.DataComponents.BREWING_FUEL)) return Result.error("msg.tsautocraft.encode.no_brewing_fuel");
		//?}
		//? if <26.3 {
		/*if (!fuel.isEmpty() && !fuel.is(ItemTags.BREWING_FUEL)) return Result.error("msg.tsautocraft.encode.no_brewing_fuel");
		*///?}
		List<PatternData.Entry> in = new ArrayList<>(java.util.Collections.nCopies(9, PatternData.Entry.EMPTY));
		in.set(BREW_FUEL, fuel.isEmpty() ? PatternData.Entry.EMPTY : new PatternData.Entry(fuel, 1));
		in.set(BREW_REAGENT, new PatternData.Entry(grid.get(BREW_REAGENT), 1));
		for (int i = BREW_FIRST_BOTTLE; i < 9; i++) if (!grid.get(i).isEmpty()) in.set(i, new PatternData.Entry(grid.get(i), 1));
		List<PatternData.Entry> out = new ArrayList<>(3);
		for (ItemStack r : results) {
			int same = -1;
			for (int j = 0; j < out.size(); j++) if (ItemStack.isSameItemSameComponents(out.get(j).item(), r)) same = j;
			if (same >= 0) out.set(same, new PatternData.Entry(r, out.get(same).count() + 1));
			else out.add(new PatternData.Entry(r, 1));
		}
		return Result.ok(new PatternData(PatternData.BREWING, in, out));
	}

	/** First non-empty stack of {@code grid}. */
	public static ItemStack firstItem(List<ItemStack> grid) {
		for (ItemStack st : grid) if (!st.isEmpty()) return st;
		return ItemStack.EMPTY;
	}

	/**
	 * Crafting recipes from REI come as a list of {@code width * height} inputs: try every grid width until the
	 * recipe matches, so the pattern gets real 3x3 positions. Lists that already match are returned as they are.
	 */
	public static List<ItemStack> layoutGrid(Level level, List<ItemStack> in) {
		if (in.size() <= 9 && findRecipe(level, in).isPresent()) return in;
		for (int w = 1; w <= 3; w++) {
			if (in.size() > w * 3) continue;
			List<ItemStack> grid = new ArrayList<>(9);
			for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
			for (int i = 0; i < in.size(); i++) grid.set(i / w * 3 + i % w, in.get(i));
			if (findRecipe(level, grid).isPresent()) return grid;
		}
		return in;
	}

	/**
	 * Encodes with the given mode (see {@link PatternMode#encode}).
	 * @param grid        crafting: the 9 grid positions; processing: up to 9 inputs with amounts; smelting: first item
	 * @param outputs     processing only: up to 3 outputs with amounts (crafting/smelting outputs come from the recipe)
	 * @param substitutes crafting only: store every item the recipe also accepts at a position (e.g. all planks)
	 */
	public static Result encode(Level level, PatternMode mode, List<ItemStack> grid, List<ItemStack> outputs, boolean substitutes) {
		Result r = mode.encode(level, grid, outputs, substitutes);
		if (r.error() == null && (r.data() == null || !r.data().isValid())) return Result.error("msg.tsautocraft.encode.invalid");
		return r;
	}

	/** Smelting pattern: first item of the grid, result from the furnace recipe. */
	public static Result encodeSmelting(Level level, List<ItemStack> grid) {
		ItemStack input = firstItem(grid);
		if (input.isEmpty()) return Result.error("msg.tsautocraft.encode.invalid");
		ItemStack result = smeltingResult(level, input);
		if (result.isEmpty()) return Result.error("msg.tsautocraft.encode.no_smelting");
		return Result.ok(new PatternData(PatternData.SMELTING, List.of(new PatternData.Entry(input, 1)), List.of(PatternData.Entry.of(result))));
	}

	/** Crafting pattern: the 9 grid positions, result (and remainders) from the crafting recipe. */
	public static Result encodeCrafting(Level level, List<ItemStack> grid, boolean substitutes) {
		var recipe = findRecipe(level, grid);
		if (recipe.isEmpty()) return Result.error("msg.tsautocraft.encode.no_recipe");
		CraftingInput input = craftInput(grid);
		ItemStack result = recipe.get().value().assemble(input);
		List<PatternData.Entry> in = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			ItemStack st = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
			if (st.isEmpty()) {
				in.add(PatternData.Entry.EMPTY);
				continue;
			}
			List<ItemStack> alt = substitutes ? substitutesAt(level, recipe.get().value(), grid, i, result) : List.of();
			in.add(new PatternData.Entry(st, 1, alt));
		}
		List<PatternData.Entry> out = new ArrayList<>();
		out.add(PatternData.Entry.of(result));
		for (ItemStack r : recipe.get().value().getRemainingItems(input)) if (!r.isEmpty()) out.add(PatternData.Entry.of(r));
		return Result.ok(new PatternData(PatternData.CRAFTING, in, out));
	}

	/** Pattern with free inputs (up to 9) and outputs (up to 3) of the given mode (processing and similar modes). */
	public static Result encodeFree(net.minecraft.resources.Identifier mode, List<ItemStack> grid, List<ItemStack> outputs) {
		return encodeFree(mode, grid, outputs, 9, 3);
	}

	/** Like {@link #encodeFree(net.minecraft.resources.Identifier, List, List)} with other limits (API 4, see {@link PatternMode#maxInputs()}). */
	public static Result encodeFree(net.minecraft.resources.Identifier mode, List<ItemStack> grid, List<ItemStack> outputs, int maxInputs, int maxOutputs) {
		List<PatternData.Entry> in = new ArrayList<>();
		for (int i = 0; i < maxInputs && i < grid.size(); i++) if (!grid.get(i).isEmpty()) in.add(PatternData.Entry.of(grid.get(i)));
		List<PatternData.Entry> out = new ArrayList<>();
		for (int i = 0; i < maxOutputs && i < outputs.size(); i++) if (!outputs.get(i).isEmpty()) out.add(PatternData.Entry.of(outputs.get(i)));
		return Result.ok(new PatternData(mode, in, out));
	}

	/**
	 * Items worth testing at a position holding {@code original}: accepted by an ingredient that also accepts the original.
	 * A full recipe match per registry item costs ~9 x 20k matches in a modpack; the ingredient test is cheap. Recipes
	 * without placement info (special recipes) still test every item.
	 */
	private static Iterable<Item> candidates(CraftingRecipe recipe, ItemStack original) {
		var info = recipe.placementInfo();
		if (info.isImpossibleToPlace()) return BuiltInRegistries.ITEM;
		List<net.minecraft.world.item.crafting.Ingredient> relevant = new ArrayList<>();
		for (var ing : info.ingredients()) if (ing.test(original)) relevant.add(ing);
		List<Item> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (relevant.isEmpty()) break;
			ItemStack probe = item.getDefaultInstance(); // test(ItemStack) also covers Fabric custom ingredients
			for (var ing : relevant) {
				if (ing.test(probe)) {
					items.add(item);
					break;
				}
			}
		}
		return items;
	}

	/**
	 * Every item that can replace the one at {@code pos} so that the same recipe still matches and gives the same result (e.g. any planks for a crafting table).
	 * Tested against the recipe itself, so tags, shapeless recipes and mod recipes all work.
	 */
	private static List<ItemStack> substitutesAt(Level level, CraftingRecipe recipe, List<ItemStack> grid, int pos, ItemStack result) {
		ItemStack original = grid.get(pos);
		List<ItemStack> test = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) test.add(i < grid.size() ? grid.get(i) : ItemStack.EMPTY);
		List<ItemStack> alt = new ArrayList<>();
		for (Item item : candidates(recipe, original)) {
			if (item == Items.AIR || item == original.getItem()) continue;
			ItemStack candidate = item.getDefaultInstance();
			if (!PlannerHooks.isSubstitute(original, candidate)) continue;
			test.set(pos, candidate);
			CraftingInput input = craftInput(test);
			if (!recipe.matches(input, level)) continue;
			ItemStack r = recipe.assemble(input);
			if (r.getCount() != result.getCount() || !ItemStack.isSameItemSameComponents(r, result)) continue;
			alt.add(candidate.copyWithCount(1));
			if (alt.size() >= MAX_SUBSTITUTES) break;
		}
		return alt;
	}
}
