package eu.sytxlabs.tsautocraft.api;

import java.util.List;
import java.util.function.ToLongFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.tom.storagemod.inventory.IInventoryAccess;

import eu.sytxlabs.tsautocraft.api.CraftingProvider.DispatchResult;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.pattern.PatternEncoder;
import eu.sytxlabs.tsautocraft.util.ItemKey;

/**
 * A kind of pattern: how the Pattern Terminal encodes it and how a Pattern Provider delivers it to the machine in front of it.
 * Built-in: crafting, processing, smelting, fluid, brewing. Other mods register their own in their {@code ModInitializer} with {@link PatternModes#register(PatternMode)} (on both sides, in the same order).
 * <p>
 * The mode is stored in the pattern by {@link #id()}; patterns of an unknown mode behave like processing patterns.
 */
public interface PatternMode {
	Identifier id();

	/** Item shown on the Pattern Terminal's mode button. */
	ItemStack icon();

	/** Display name, translation key {@code pattern_mode.<namespace>.<path>} by default. */
	default Component name() {
		return Component.translatable("pattern_mode." + id().getNamespace() + "." + id().getPath());
	}

	/**
	 * true: one item per grid slot and the outputs are computed by {@link #computeOutputs}; false: free amounts and
	 * outputs set by hand (like processing patterns).
	 */
	default boolean fixedInputs() {
		return false;
	}

	/** true: the 9 grid positions matter (crafting); false: inputs are moved to the front of the grid. */
	default boolean keepsGridPositions() {
		return false;
	}

	/** Can grid position {@code index} (0-8) of the Pattern Terminal be used in this mode? Disabled positions are hidden and stay empty. */
	default boolean gridSlotEnabled(int index) {
		return true;
	}

	/**
	 * Is input {@code index} of the pattern only delivered when the machine needs it (e.g. fuel)? Such inputs are not
	 * planned or reserved per craft ({@link PatternData#inputTotals()} leaves them out); the mode pulls them when needed.
	 */
	default boolean optionalInput(PatternData d, int index) {
		return false;
	}

	/** Outputs for the current grid ({@link #fixedInputs()} modes only), empty list if the grid is no valid recipe. */
	default List<ItemStack> computeOutputs(Level level, List<ItemStack> grid) {
		return List.of();
	}

	/**
	 * Most inputs a pattern of this mode may have (API 4). JEI/REI transfers with more inputs are refused; more than 9
	 * can only be encoded by a transfer (the Pattern Terminal grid has 9 slots), e.g. a 9x9 mechanical crafting recipe.
	 */
	default int maxInputs() {
		return 9;
	}

	/** Most outputs a pattern of this mode may have (API 4). */
	default int maxOutputs() {
		return 3;
	}

	/** Builds the pattern from the Pattern Terminal grid (or a JEI/REI transfer). */
	PatternEncoder.Result encode(Level level, List<ItemStack> grid, List<ItemStack> outputs, boolean substitutes);

	/** Can a pattern with this data be used at all? */
	default boolean isValid(PatternData d) {
		return d.inputs().stream().anyMatch(e -> !e.isEmpty()) && d.outputs().stream().anyMatch(e -> !e.isEmpty());
	}

	/** Server: adjusts the inputs of a JEI/REI transfer before encoding (e.g. REI's shaped recipe lists to 3x3). */
	default List<ItemStack> transferInputs(Level level, List<ItemStack> inputs) {
		return inputs;
	}

	/** Is this JEI recipe (usually a {@code RecipeHolder}) of this mode? The first matching mode wins, else processing. */
	default boolean matchesRecipe(Object recipe) {
		return false;
	}

	/** Is this REI display category of this mode? */
	default boolean matchesReiCategory(Identifier category) {
		return false;
	}

	/**
	 * Delivers the inputs for up to {@link DispatchContext#maxCrafts()} crafts to the machine in front of the provider.
	 * Take items only through {@link DispatchContext#pull(List)}.
	 */
	Outcome dispatch(DispatchContext ctx, PatternData pattern);

	/** {@code crafts} = crafts started (must be > 0 for {@link DispatchResult#OK}). */
	record Outcome(DispatchResult result, int crafts) {
		public static Outcome ok(int crafts) {
			return new Outcome(DispatchResult.OK, crafts);
		}

		public static Outcome fail(DispatchResult result) {
			return new Outcome(result, 0);
		}
	}

	/** What a Pattern Provider offers to a mode during one delivery. */
	interface DispatchContext {
		Level level();

		BlockPos providerPos();

		/** Direction from the provider to the target. */
		Direction facing();

		default BlockPos targetPos() {
			return providerPos().relative(facing());
		}

		/** Network storage (Tom's merged inventory). */
		IInventoryAccess network();

		/** Usable stock per item (stock minus what other jobs reserved). */
		ToLongFunction<ItemKey> available();

		/** Crafts this delivery may start at most (1, more with Stack Upgrades). */
		int maxCrafts();

		/** Blocking mode of the provider. */
		boolean blocking();

		/** Speed Upgrades installed in the provider. */
		int speed();

		/** Pulls all stacks from the network or nothing (null if something is missing); counts as used by the job. */
		List<ItemStack> pull(List<ItemStack> wanted);

		/** Puts items back into the network; whatever does not fit is buffered by the provider. */
		void giveBack(ItemStack stack);

		/** Puts the stacks of a {@link #pull} back that could not be delivered after all; they no longer count as used. */
		default void undoPull(List<ItemStack> got) {
			got.forEach(this::giveBack);
		}

		/**
		 * A result the delivery made right away (e.g. an empty container filled from the machine's tank). It goes into
		 * the network on the next tick and counts as an arrived result for the jobs, like an Importer arrival.
		 */
		default void result(ItemStack stack) {
			giveBack(stack);
		}

		/** The provider block entity (the built-in modes use its internals). */
		Object provider();

		/** Gives the target a redstone signal for {@code ticks} ticks (e.g. to start a machine after a delivery). */
		default void redstonePulse(int ticks) {}
	}
}
