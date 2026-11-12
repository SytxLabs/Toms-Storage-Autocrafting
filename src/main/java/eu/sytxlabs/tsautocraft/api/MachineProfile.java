package eu.sytxlabs.tsautocraft.api;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
//? if >=26.3 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
//? if <26.3 {
/*import net.minecraft.tags.ItemTags;
*///?}
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * How a Pattern Provider feeds one kind of machine with processing patterns (mod machines, brewing stands, ...).
 * Comes from a datapack ({@code data/<ns>/tsautocraft/machines/*.json}) or code ({@link MachineProfiles#register}).
 * <pre>
 * {
 *   "blocks": ["minecraft:brewing_stand", "#c:machines"],
 *   "sides": ["up", "north"],
 *   "routes": [
 *     { "inputs": [0], "sides": ["up"] },
 *     { "fuel": "cooking", "sides": ["east"] },
 *     { "items": ["minecraft:bucket", "#minecraft:logs"], "sides": ["west"] }
 *   ],
 *   "redstone_pulse": 2,
 *   "cooldown": 10,
 *   "blocking": true
 * }
 * </pre>
 *
 * @param sides         faces of the machine to insert through, tried in this order per stack (e.g. ingredient on top, bottles from the side); empty = the face that points at the provider
 * @param redstonePulse after a delivery the provider gives a redstone pulse of this many ticks to the machine (0 = none)
 * @param blocking      forces blocking mode on or off for this machine; empty = the provider's setting
 * @param routes        different faces for some inputs; the first route that matches an input wins, inputs without a route use {@code sides}
 * @param cooldown      ticks between two deliveries to this machine (e.g. a dispenser filling buckets: the water source must flow back first)
 */
public record MachineProfile(List<Direction> sides, int redstonePulse, Optional<Boolean> blocking, List<Route> routes, int cooldown) {
	public static final MachineProfile DEFAULT = new MachineProfile(List.of(), 0, Optional.empty());

	/**
	 * Faces for some of the inputs.
	 * @param items  item ids or {@code #tags} this route is for
	 * @param inputs positions in the pattern's input list (0 = first input) this route is for
	 * @param fuel   {@code cooking} (furnace fuel), {@code brewing} (brewing stand fuel) or {@code any}: this route is for such fuels; empty = none
	 * @param sides  faces of the machine to insert these inputs through, tried in this order
	 */
	public record Route(List<String> items, List<Integer> inputs, Optional<String> fuel, List<Direction> sides) {
		public static final Codec<Route> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Route::items),
				Codec.intRange(0, 8).listOf().optionalFieldOf("inputs", List.of()).forGetter(Route::inputs),
				Codec.STRING.validate(f -> f.equals("cooking") || f.equals("brewing") || f.equals("any") ? DataResult.success(f) : DataResult.error(() -> "fuel must be cooking, brewing or any: " + f)).optionalFieldOf("fuel").forGetter(Route::fuel),
				Direction.CODEC.listOf().fieldOf("sides").forGetter(Route::sides)
				).apply(i, Route::new));

		public Route {
			items = List.copyOf(items);
			inputs = List.copyOf(inputs);
			sides = List.copyOf(sides);
		}

		/** {@link #matches(ItemStack, int, Level)} without a level (before 26.3 no item then counts as cooking fuel). */
		public boolean matches(ItemStack stack, int index) {
			return matches(stack, index, null);
		}

		/**
		 * Is this route for {@code stack}, the input at {@code index} of the pattern?
		 * @param level only used for the cooking fuel check before 26.3 (fuel values belong to the server); null = no item counts as cooking fuel there
		 */
		public boolean matches(ItemStack stack, int index, @Nullable Level level) {
			if (inputs.contains(index)) return true;
			if (fuel.isPresent()) {
				String f = fuel.get();
				//? if >=26.3 {
				if ((!f.equals("brewing") && stack.has(DataComponents.COOKING_FUEL)) || (!f.equals("cooking") && stack.has(DataComponents.BREWING_FUEL))) return true;
				//?}
				//? if <26.3 {
				/*boolean cookingFuel = level != null && level.getServer() != null && level.getServer().fuelValues().isFuel(stack);
				if ((!f.equals("brewing") && cookingFuel) || (!f.equals("cooking") && stack.is(ItemTags.BREWING_FUEL))) return true;
				*///?}
			}
			for (String s : items) {
				if (s.startsWith("#")) {
					Identifier tag = Identifier.tryParse(s.substring(1));
					if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return true;
				} else {
					Identifier id = Identifier.tryParse(s);
					if (id != null && stack.typeHolder().is(id)) return true;
				}
			}
			return false;
		}
	}

	public static final MapCodec<MachineProfile> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Direction.CODEC.listOf().optionalFieldOf("sides", List.of()).forGetter(MachineProfile::sides),
			Codec.intRange(0, 100).optionalFieldOf("redstone_pulse", 0).forGetter(MachineProfile::redstonePulse),
			Codec.BOOL.optionalFieldOf("blocking").forGetter(MachineProfile::blocking),
			Route.CODEC.listOf().optionalFieldOf("routes", List.of()).forGetter(MachineProfile::routes),
			Codec.intRange(0, 1200).optionalFieldOf("cooldown", 0).forGetter(MachineProfile::cooldown)
			).apply(i, MachineProfile::new));
	public static final Codec<MachineProfile> CODEC = MAP_CODEC.codec();

	public MachineProfile {
		sides = List.copyOf(sides);
		routes = List.copyOf(routes);
	}

	public MachineProfile(List<Direction> sides, int redstonePulse, Optional<Boolean> blocking) {
		this(sides, redstonePulse, blocking, List.of(), 0);
	}

	/** {@link #sidesFor(ItemStack, int, Level)} without a level (before 26.3 no item then counts as cooking fuel). */
	public List<Direction> sidesFor(ItemStack stack, int index) {
		return sidesFor(stack, index, null);
	}

	/**
	 * Faces for the input {@code stack} at {@code index} of the pattern: its route's, else {@link #sides()} (empty = the face towards the provider).
	 * @param level see {@link Route#matches(ItemStack, int, Level)}
	 */
	public List<Direction> sidesFor(ItemStack stack, int index, @Nullable Level level) {
		for (Route r : routes) if (r.matches(stack, index, level)) return r.sides();
		return sides;
	}

	/** true if every input goes through the face that points at the provider (no sides, no routes). */
	public boolean facingOnly() {
		return sides.isEmpty() && routes.isEmpty();
	}
}
