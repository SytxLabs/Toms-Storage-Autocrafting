package eu.sytxlabs.tsautocraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import com.tom.storagemod.item.IItemFilter;

import eu.sytxlabs.tsautocraft.blockentity.MemoryCardHost;
import eu.sytxlabs.tsautocraft.util.UpgradeContainer;

/**
 * Memory Card: shift + right click on a block copies its settings, right click applies them to a block of the same type,
 * shift + right click into the air clears the card. Real items are never created (except in creative): filter items
 * come from the player's inventory (an unconfigured one of the same item, configured like the source) and so do
 * missing upgrades; replaced filter items go to the inventory. Anything missing is skipped and reported.
 */
public class MemoryCardItem extends Item {
	public MemoryCardItem(Properties p) {
		super(p);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
		if (player == null || !(be instanceof MemoryCardHost host)) return InteractionResult.PASS;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		ItemStack card = ctx.getItemInHand();
		Identifier type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
		if (type == null) return InteractionResult.PASS;
		if (player.isSecondaryUseActive()) {
			card.set(ModContent.MEMORY_CARD_COMPONENT, copy(be, host, type));
			player.sendOverlayMessage(Component.translatable("msg.tsautocraft.memory_card.copied", be.getBlockState().getBlock().getName()));
			return InteractionResult.SUCCESS;
		}
		MemoryCardData data = card.get(ModContent.MEMORY_CARD_COMPONENT);
		if (data == null) {
			player.sendOverlayMessage(Component.translatable("msg.tsautocraft.memory_card.empty"));
			return InteractionResult.SUCCESS;
		}
		if (!data.type().equals(type)) {
			player.sendOverlayMessage(Component.translatable("msg.tsautocraft.memory_card.wrong_type", data.typeName()));
			return InteractionResult.SUCCESS;
		}
		Map<Item, Integer> missing = apply(be, host, data, player);
		be.setChanged();
		level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
		if (missing.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("msg.tsautocraft.memory_card.applied"));
		} else {
			MutableComponent list = Component.empty();
			boolean first = true;
			for (var e : missing.entrySet()) {
				if (!first) list.append(", ");
				first = false;
				list.append(Component.translatable("msg.tsautocraft.craft_missing.entry", e.getValue(), new ItemStack(e.getKey()).getHoverName()));
			}
			player.sendOverlayMessage(Component.translatable("msg.tsautocraft.memory_card.applied_missing", list));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack card = player.getItemInHand(hand);
		if (!player.isSecondaryUseActive() || !card.has(ModContent.MEMORY_CARD_COMPONENT)) return InteractionResult.PASS;
		if (!level.isClientSide()) {
			card.remove(ModContent.MEMORY_CARD_COMPONENT);
			player.sendOverlayMessage(Component.translatable("msg.tsautocraft.memory_card.cleared"));
		}
		return InteractionResult.SUCCESS;
	}

	private static MemoryCardData copy(BlockEntity be, MemoryCardHost host, Identifier type) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, be.getLevel().registryAccess());
		host.saveCard(out);
		List<ItemStack> filters = new ArrayList<>();
		Container f = host.cardFilters();
		if (f != null) for (int i = 0; i < f.getContainerSize(); i++) filters.add(f.getItem(i).copy());
		List<ItemStack> upgrades = new ArrayList<>();
		UpgradeContainer up = host.cardUpgrades();
		if (up != null) {
			Map<Item, Integer> counts = new LinkedHashMap<>();
			for (int i = 0; i < up.getContainerSize(); i++) {
				ItemStack st = up.getItem(i);
				if (!st.isEmpty()) counts.merge(st.getItem(), st.getCount(), Integer::sum);
			}
			counts.forEach((item, n) -> upgrades.add(new ItemStack(item, n)));
		}
		return new MemoryCardData(type, out.buildResult(), filters, upgrades);
	}

	/** @return items that were missing in the player's inventory (filters, upgrades) */
	private static Map<Item, Integer> apply(BlockEntity be, MemoryCardHost host, MemoryCardData data, Player player) {
		Map<Item, Integer> missing = new LinkedHashMap<>();
		host.loadCard(TagValueInput.create(ProblemReporter.DISCARDING, be.getLevel().registryAccess(), data.settings()));
		Container f = host.cardFilters();
		if (f != null) {
			for (int i = 0; i < f.getContainerSize() && i < data.filters().size(); i++) applyFilter(f, i, data.filters().get(i), player, missing);
			f.setChanged();
		}
		UpgradeContainer up = host.cardUpgrades();
		if (up != null) for (ItemStack u : data.upgrades()) addUpgrades(up, u.getItem(), u.getCount(), player, missing);
		return missing;
	}

	private static boolean isReal(ItemStack st) {
		return !st.isEmpty() && st.getItem() instanceof IItemFilter;
	}

	private static void applyFilter(Container f, int slot, ItemStack want, Player player, Map<Item, Integer> missing) {
		ItemStack cur = f.getItem(slot);
		if (isReal(want)) {
			if (ItemStack.isSameItemSameComponents(cur, want)) return; // already the same filter
			if (!takeUnconfigured(player, want.getItem())) {
				missing.merge(want.getItem(), 1, Integer::sum);
				return;
			}
			ItemStack configured = want.copyWithCount(1);
			configured.remove(DataComponents.CUSTOM_NAME);
			if (isReal(cur)) give(player, cur.copy());
			f.setItem(slot, configured);
			return;
		}
		// Ghost or empty: real filters in the target go back to the player
		if (isReal(cur)) give(player, cur.copy());
		f.setItem(slot, want.isEmpty() ? ItemStack.EMPTY : want.copyWithCount(1));
	}

	/** Takes an unconfigured {@code item} (no components set) from the inventory; creative players always have one. */
	private static boolean takeUnconfigured(Player player, Item item) {
		if (player.hasInfiniteMaterials()) return true;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item) && st.getComponentsPatch().isEmpty()) {
				st.shrink(1);
				inv.setChanged();
				return true;
			}
		}
		return false;
	}

	private static void addUpgrades(UpgradeContainer up, Item item, int want, Player player, Map<Item, Integer> missing) {
		int need = want - up.count(item);
		ItemStack probe = new ItemStack(item);
		for (int slot = 0; slot < up.getContainerSize() && need > 0; slot++) {
			ItemStack cur = up.getItem(slot);
			if (!cur.isEmpty() && !cur.is(item)) continue;
			int room = up.limit(slot, probe) - cur.getCount();
			if (room <= 0) continue;
			int ask = Math.min(room, need);
			int got = takeUpgrades(player, item, ask);
			if (got > 0) up.setItem(slot, new ItemStack(item, cur.getCount() + got));
			need -= got;
			if (got < ask) {
				missing.merge(item, need, Integer::sum); // inventory ran out (no room in the block is not reported)
				return;
			}
		}
	}

	private static int takeUpgrades(Player player, Item item, int n) {
		if (player.hasInfiniteMaterials()) return n;
		Inventory inv = player.getInventory();
		int got = 0;
		for (int i = 0; i < inv.getContainerSize() && got < n; i++) {
			ItemStack st = inv.getItem(i);
			if (!st.is(item)) continue;
			int t = Math.min(st.getCount(), n - got);
			st.shrink(t);
			got += t;
		}
		if (got > 0) inv.setChanged();
		return got;
	}

	private static void give(Player player, ItemStack st) {
		//? if >=26.3 {
		player.getInventory().placeItemBackInInventory(st, net.minecraft.util.Prediction.SERVER_ONLY); // drops what does not fit
		//?}
		//? if <26.3 {
		/*player.getInventory().placeItemBackInInventory(st); // drops what does not fit
		*///?}
	}
}
