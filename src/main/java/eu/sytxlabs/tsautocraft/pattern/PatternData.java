package eu.sytxlabs.tsautocraft.pattern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import eu.sytxlabs.tsautocraft.TsAutocraft;
import eu.sytxlabs.tsautocraft.api.PatternMode;
import eu.sytxlabs.tsautocraft.api.PatternModes;
import eu.sytxlabs.tsautocraft.util.ItemKey;

/**
 * Contents of an encoded pattern; {@code mode} is the id of its {@link PatternMode} ({@link PatternModes#register}).
 * Crafting patterns have exactly 9 inputs in crafter slot order, empty entries allowed. Counts live in
 * {@link Entry#count()} because {@code ItemStack.CODEC} caps them at 99.
 *
 * @param derived lazily computed values (totals, hash), internal: not part of equality, always fresh per instance
 */
public record PatternData(Identifier mode, List<Entry> inputs, List<Entry> outputs, Derived derived) implements TooltipProvider {
	public static final Identifier CRAFTING = TsAutocraft.id("crafting"), PROCESSING = TsAutocraft.id("processing"), SMELTING = TsAutocraft.id("smelting"), FLUID = TsAutocraft.id("fluid"), BREWING = TsAutocraft.id("brewing");
	public static final Codec<PatternData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("mode").forGetter(d -> Optional.of(d.mode())),
			Codec.BOOL.optionalFieldOf("crafting", false).forGetter(PatternData::crafting),
			Codec.BOOL.optionalFieldOf("smelting", false).forGetter(PatternData::smelting),
			Entry.CODEC.listOf().fieldOf("inputs").forGetter(PatternData::inputs),
			Entry.CODEC.listOf().fieldOf("outputs").forGetter(PatternData::outputs))
			.apply(i, (mode, crafting, smelting, in, out) -> new PatternData(mode.orElse(crafting ? CRAFTING : smelting ? SMELTING : PROCESSING), in, out)));

	public boolean crafting() {
		return CRAFTING.equals(mode);
	}

	public boolean smelting() {
		return SMELTING.equals(mode);
	}

	public PatternMode modeType() {
		return PatternModes.get(mode);
	}

	/** @param alternatives substitutes that may be used instead of {@code item} (crafting patterns encoded with "substitutes" on, e.g. birch planks for oak planks); empty = only {@code item} */
	public record Entry(ItemStack item, int count, List<ItemStack> alternatives) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(ItemStack.OPTIONAL_CODEC.fieldOf("item").forGetter(Entry::item), Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count), ItemStack.CODEC.listOf().optionalFieldOf("alt", List.of()).forGetter(Entry::alternatives)).apply(i, Entry::new));
		public static final Entry EMPTY = new Entry(ItemStack.EMPTY, 0);

		public Entry {
			item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
			if (item.isEmpty()) count = 0;
			alternatives = item.isEmpty() ? List.of() : alternatives.stream().filter(s -> !s.isEmpty()).map(s -> s.copyWithCount(1)).toList();
		}

		public Entry(ItemStack item, int count) {
			this(item, count, List.of());
		}

		/** The item itself followed by its substitutes. */
		public List<ItemStack> options() {
			if (alternatives.isEmpty()) return List.of(item);
			List<ItemStack> l = new ArrayList<>(alternatives.size() + 1);
			l.add(item);
			l.addAll(alternatives);
			return l;
		}

		/** Is {@code stack} this entry's item or one of its substitutes? */
		public boolean accepts(ItemStack stack) {
			for (ItemStack o : options()) if (ItemStack.isSameItemSameComponents(o, stack)) return true;
			return false;
		}

		public static Entry of(ItemStack stack) {
			return stack.isEmpty() ? EMPTY : new Entry(stack, stack.getCount());
		}

		public boolean isEmpty() {
			return item.isEmpty() || count <= 0;
		}

		public ItemStack stack() {
			return isEmpty() ? ItemStack.EMPTY : item.copyWithCount(count);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Entry(ItemStack item1, int count1, List<ItemStack> alternatives1)) || count != count1 || !ItemStack.isSameItemSameComponents(item, item1)) return false;
			if (alternatives.size() != alternatives1.size()) return false;
			for (int i = 0; i < alternatives.size(); i++) {
				if (!ItemStack.isSameItemSameComponents(alternatives.get(i), alternatives1.get(i))) return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return (ItemStack.hashItemAndComponents(item) * 31 + count) * 31 + alternatives.size();
		}
	}

	/** At least one input may be replaced by a substitute. */
	public boolean hasSubstitutes() {
		Boolean b = derived.substitutes;
		if (b == null) derived.substitutes = b = inputs.stream().anyMatch(e -> !e.alternatives().isEmpty());
		return b;
	}

	public PatternData {
		inputs = List.copyOf(inputs);
		outputs = List.copyOf(outputs);
		derived = new Derived();
	}

	public PatternData(Identifier mode, List<Entry> inputs, List<Entry> outputs) {
		this(mode, inputs, outputs, null);
	}

	/**
	 * Values computed once per pattern instance: patterns are compared, hashed and totalled in every job tick and planner
	 * step. Patterns are immutable, so the cache never goes stale.
	 */
	public static final class Derived {
		private volatile int hash;
		private volatile Boolean substitutes;
		private volatile Map<ItemKey, Long> inputTotals, outputTotals;
		private volatile ItemKey primaryKey;

		private Derived() {}

		@Override
		public String toString() {
			return "";
		}
	}

	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof PatternData d && hashCode() == d.hashCode() && mode.equals(d.mode) && inputs.equals(d.inputs) && outputs.equals(d.outputs);
	}

	@Override
	public int hashCode() {
		int h = derived.hash;
		if (h == 0) {
			h = (mode.hashCode() * 31 + inputs.hashCode()) * 31 + outputs.hashCode();
			if (h == 0) h = 1;
			derived.hash = h;
		}
		return h;
	}

	public boolean isValid() {
		return modeType().isValid(this);
	}

	/** Inputs used up per craft, without the mode's {@linkplain PatternMode#optionalInput optional inputs} (e.g. fuel). */
	public Map<ItemKey, Long> inputTotals() {
		Map<ItemKey, Long> c = derived.inputTotals;
		if (c == null) derived.inputTotals = c = java.util.Collections.unmodifiableMap(computeInputTotals());
		return c;
	}

	private Map<ItemKey, Long> computeInputTotals() {
		PatternMode m = modeType();
		Map<ItemKey, Long> t = new LinkedHashMap<>();
		for (int i = 0; i < inputs.size(); i++) {
			Entry e = inputs.get(i);
			if (!e.isEmpty() && !m.optionalInput(this, i)) t.merge(new ItemKey(e.item()), (long) e.count(), Long::sum);
		}
		return t;
	}

	/** Inputs only delivered when the machine needs them (e.g. fuel), see {@link PatternMode#optionalInput}. */
	public List<Entry> optionalInputs() {
		PatternMode m = modeType();
		List<Entry> l = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) if (!inputs.get(i).isEmpty() && m.optionalInput(this, i)) l.add(inputs.get(i));
		return l;
	}

	public Map<ItemKey, Long> outputTotals() {
		Map<ItemKey, Long> c = derived.outputTotals;
		if (c == null) derived.outputTotals = c = java.util.Collections.unmodifiableMap(totals(outputs));
		return c;
	}

	private static Map<ItemKey, Long> totals(List<Entry> list) {
		Map<ItemKey, Long> m = new LinkedHashMap<>();
		for (Entry e : list) {
			if (!e.isEmpty()) m.merge(new ItemKey(e.item()), (long) e.count(), Long::sum);
		}
		return m;
	}

	public List<ItemStack> inputStacks() {
		List<ItemStack> l = new ArrayList<>();
		for (Entry e : inputs) {
			if (e.isEmpty()) continue;
			int left = e.count();
			int max = Math.max(1, e.item().getMaxStackSize());
			while (left > 0) {
				int n = Math.min(left, max);
				l.add(e.item().copyWithCount(n));
				left -= n;
			}
		}
		return l;
	}

	// Tooltip (Fabric ItemComponentTooltipProviderRegistry)
	private static final int MAX_NAMES = 6;

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
		tooltip.accept(Component.translatable("tooltip.tsautocraft.pattern.kind", modeType().name()).withStyle(ChatFormatting.AQUA));
		tooltip.accept(Component.translatable("tooltip.tsautocraft.pattern.outputs").withStyle(ChatFormatting.GOLD));
		for (Entry e : outputs) {
			if (!e.isEmpty()) tooltip.accept(entryLine(e.count(), e.item()));
		}
		tooltip.accept(Component.translatable("tooltip.tsautocraft.pattern.inputs").withStyle(ChatFormatting.GOLD));
		inputTotals().forEach((k, c) -> tooltip.accept(entryLine(c, k.template())));
		for (Entry e : optionalInputs()) tooltip.accept(Component.translatable("tooltip.tsautocraft.pattern.when_needed", e.item().getHoverName()).withStyle(ChatFormatting.GRAY));
		if (hasSubstitutes()) substitutesTooltip(tooltip);
	}

	private void substitutesTooltip(Consumer<Component> tooltip) {
		if (!PatternItem.shiftDown.getAsBoolean()) {
			tooltip.accept(Component.translatable("tooltip.tsautocraft.pattern.substitutes_hint").withStyle(ChatFormatting.GREEN));
			return;
		}
		tooltip.accept(Component.translatable("tooltip.tsautocraft.pattern.substitutes").withStyle(ChatFormatting.GREEN));
		java.util.Set<String> shown = new java.util.HashSet<>();
		for (Entry e : inputs) {
			if (e.isEmpty() || e.alternatives().isEmpty()) continue;
			if (!shown.add(e.item().getItem().toString())) continue;
			MutableComponent names = Component.empty();
			List<ItemStack> alt = e.alternatives();
			for (int i = 0; i < alt.size() && i < MAX_NAMES; i++) {
				if (i > 0) names.append(", ");
				names.append(alt.get(i).getHoverName());
			}
			if (alt.size() > MAX_NAMES) names.append(" ").append(Component.translatable("tooltip.tsautocraft.pattern.more", alt.size() - MAX_NAMES));
			tooltip.accept(Component.translatable("tooltip.tsautocraft.pattern.substitute_line", e.item().getHoverName(), names).withStyle(ChatFormatting.GRAY));
		}
	}

	private static Component entryLine(long count, ItemStack item) {
		return Component.translatable("tooltip.tsautocraft.pattern.entry", count, item.getHoverName()).withStyle(ChatFormatting.GRAY);
	}

	/** Key of {@link #primaryOutput()}, null without outputs. */
	public ItemKey primaryKey() {
		ItemKey k = derived.primaryKey;
		if (k == null) {
			ItemStack p = primaryOutput();
			if (p.isEmpty()) return null;
			derived.primaryKey = k = new ItemKey(p);
		}
		return k;
	}

	public ItemStack primaryOutput() {
		for (Entry e : outputs) {
			if (!e.isEmpty()) return e.stack();
		}
		return ItemStack.EMPTY;
	}
}
