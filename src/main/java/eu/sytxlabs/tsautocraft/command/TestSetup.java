package eu.sytxlabs.tsautocraft.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
//? if >=26.3 {
import net.minecraft.world.level.block.entity.SignTextSlot;
//?}
import net.minecraft.world.level.block.state.BlockState;

import com.tom.storagemod.block.AbstractStorageTerminalBlock;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.block.ConnectorNodeBlock;
import eu.sytxlabs.tsautocraft.blockentity.AbstractIOBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.CraftingEmitterBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.LevelMaintainerBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.PatternProviderBlockEntity;
import eu.sytxlabs.tsautocraft.pattern.PatternEncoder;

/**
 * {@code /tsautocraft testsetup}: builds test stations in a row south of the player (every 10 blocks, platform +
 * sign): a crafting network (chest, terminal, Pattern Provider with patterns, crafter, importer), a furnace line, a
 * Level Maintainer, Importer / Exporter and a chest with all items. Meant for a creative superflat test world.
 */
public final class TestSetup {
	private static final int SPACING = 10, SIZE = 8;

	private final ServerLevel level;
	private final BlockPos origin;
	private int station;
	private final List<Component> report = new ArrayList<>();

	private TestSetup(ServerPlayer player) {
		this.level = player.level();
		this.origin = player.blockPosition().offset(2, 0, 4);
	}

	public static List<Component> build(ServerPlayer player) {
		TestSetup t = new TestSetup(player);
		t.crafting();
		t.smelting();
		t.io();
		t.items();
		// setBlock puts Tom's cables in their default state: connect them like placing would
		for (int i = 0; i < t.station; i++) t.fixShapes(t.origin.offset(0, 0, i * SPACING));
		return t.report;
	}

	/** Recomputes every block's state from its neighbours in a station (like placing it by hand). */
	private void fixShapes(BlockPos from) {
		for (int pass = 0; pass < 2; pass++) {
			for (int x = 0; x < SIZE; x++) for (int y = -1; y < 5; y++) for (int z = 0; z < SIZE; z++) {
				BlockPos p = from.offset(x, y, z);
				BlockState st = level.getBlockState(p);
				if (st.isAir()) continue;
				BlockState n = Block.updateFromNeighbourShapes(st, level, p);
				if (n != st) level.setBlock(p, n, Block.UPDATE_ALL);
			}
		}
	}

	/** Chest + connector + Autocrafting Terminal (south of the connector) + Pattern Provider; returns the provider. */
	private PatternProviderBlockEntity network(BlockPos o, ItemStack... stock) {
		chest(o.offset(1, 0, 2), stock);
		set(o.offset(2, 0, 2), toms("inventory_connector"));
		set(o.offset(2, 0, 3), ModContent.AUTOCRAFTING_TERMINAL.defaultBlockState().setValue(AbstractStorageTerminalBlock.FACING, Direction.NORTH));
		set(o.offset(3, 0, 2), ModContent.PATTERN_PROVIDER);
		return level.getBlockEntity(o.offset(3, 0, 2)) instanceof PatternProviderBlockEntity p ? p : null;
	}

	private void crafting() {
		BlockPos o = next("crafting");
		PatternProviderBlockEntity p = network(o, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
				new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.REDSTONE, 32));
		// Crafter east of the provider (front north -> output chest), importer above the output chest, cables to the network
		set(o.offset(4, 0, 2), Blocks.CRAFTER);
		set(o.offset(4, 0, 1), Blocks.CHEST);
		set(o.offset(3, 1, 2), toms("inventory_cable"));
		set(o.offset(4, 1, 2), toms("inventory_cable"));
		set(o.offset(4, 1, 1), ModContent.IMPORTER.defaultBlockState().setValue(ConnectorNodeBlock.FACING, Direction.DOWN));
		if (p != null) {
			pattern(p, 0, crafting(Items.OAK_LOG, 4, null));                  // log -> 4 planks
			pattern(p, 1, crafting(Items.OAK_PLANKS, 1, Items.OAK_PLANKS));   // planks -> 4 sticks
			pattern(p, 2, crafting(Items.COBBLESTONE, 8, null));             // furnace (8 cobblestone in a ring)
			// Pulses the crafter after every delivery (default crafterTrigger "external" leaves that to the player)
			p.getUpgrades().setItem(0, new ItemStack(ModContent.REDSTONE_UPGRADE));
		}
		// Level Maintainer (keep 32 sticks, batch 16) and Crafting Emitter + lamp on the same network
		set(o.offset(1, 0, 3), ModContent.LEVEL_MAINTAINER);
		if (level.getBlockEntity(o.offset(1, 0, 3)) instanceof LevelMaintainerBlockEntity m) {
			m.getItems().setItem(0, new ItemStack(Items.STICK));
			m.setThreshold(0, 32);
			m.setBatch(0, 16);
		}
		// Chest and terminal next to it do not carry the network: cable over the terminal to the connector
		set(o.offset(1, 1, 3), toms("inventory_cable"));
		set(o.offset(2, 1, 3), toms("inventory_cable"));
		set(o.offset(2, 1, 2), toms("inventory_cable"));
		set(o.offset(1, 0, 4), toms("inventory_cable"));
		set(o.offset(1, 0, 5), ModContent.CRAFTING_EMITTER);
		// Lamp on while fewer than 32 sticks are stored (off once the Level Maintainer has crafted them)
		if (level.getBlockEntity(o.offset(1, 0, 5)) instanceof CraftingEmitterBlockEntity e) {
			e.getItem().setItem(0, new ItemStack(Items.STICK));
			e.setAmount(32);
		}
		set(o.offset(1, 1, 5), Blocks.REDSTONE_LAMP);
		set(o.offset(3, 0, 4), ModContent.REQUESTER);
		set(o.offset(3, 0, 3), toms("inventory_cable"));
	}

	private void smelting() {
		BlockPos o = next("smelting");
		PatternProviderBlockEntity p = network(o, new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.COAL, 64), new ItemStack(Items.SAND, 64));
		set(o.offset(4, 0, 2), Blocks.FURNACE);
		if (level.getBlockEntity(o.offset(4, 0, 2)) instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity f) f.setItem(1, new ItemStack(Items.COAL, 64));
		// Importer under the furnace takes the results (bottom = output slot)
		set(o.offset(4, -1, 2), ModContent.IMPORTER.defaultBlockState().setValue(ConnectorNodeBlock.FACING, Direction.UP));
		set(o.offset(3, -1, 2), toms("inventory_cable"));
		set(o.offset(2, -1, 2), toms("inventory_cable"));
		if (p != null) {
			pattern(p, 0, PatternEncoder.encodeSmelting(level, List.of(new ItemStack(Items.RAW_IRON))));
			pattern(p, 1, PatternEncoder.encodeSmelting(level, List.of(new ItemStack(Items.SAND))));
		}
	}

	private void io() {
		BlockPos o = next("io");
		chest(o.offset(1, 0, 2), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIRT, 64));
		set(o.offset(2, 0, 2), toms("inventory_connector"));
		set(o.offset(2, 0, 3), ModContent.AUTOCRAFTING_TERMINAL.defaultBlockState().setValue(AbstractStorageTerminalBlock.FACING, Direction.NORTH));
		// Exporter (cobblestone) into a barrel east, Importer from a barrel with gravel
		set(o.offset(3, 0, 2), ModContent.EXPORTER.defaultBlockState().setValue(ConnectorNodeBlock.FACING, Direction.EAST));
		set(o.offset(4, 0, 2), Blocks.BARREL);
		if (level.getBlockEntity(o.offset(3, 0, 2)) instanceof AbstractIOBlockEntity e) e.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
		set(o.offset(2, 0, 1), ModContent.IMPORTER.defaultBlockState().setValue(ConnectorNodeBlock.FACING, Direction.NORTH));
		set(o.offset(2, 0, 0), Blocks.BARREL);
		if (level.getBlockEntity(o.offset(2, 0, 0)) instanceof net.minecraft.world.Container c) c.setItem(0, new ItemStack(Items.GRAVEL, 64));
	}

	private void items() {
		BlockPos o = next("items");
		List<ItemStack> all = new ArrayList<>();
		for (Item i : ModContent.items()) all.add(new ItemStack(i));
		all.add(new ItemStack(ModContent.BLANK_PATTERN, 16));
		for (int c = 0; c * 27 < all.size(); c++) chest(o.offset(1 + c, 0, 2), all.subList(c * 27, Math.min(all.size(), c * 27 + 27)).toArray(ItemStack[]::new));
	}

	/** 3x3 crafting grid: {@code count} of {@code item} in the first slots (8 = ring), {@code second} below the first. */
	private PatternEncoder.Result crafting(Item item, int count, Item second) {
		List<ItemStack> grid = new ArrayList<>();
		for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
		if (count == 8) {
			for (int i = 0; i < 9; i++) if (i != 4) grid.set(i, new ItemStack(item));
		} else {
			grid.set(0, new ItemStack(item));
			if (second != null) grid.set(3, new ItemStack(second));
		}
		return PatternEncoder.encodeCrafting(level, grid, false);
	}

	private void pattern(PatternProviderBlockEntity p, int slot, PatternEncoder.Result r) {
		if (r.error() == null) p.getPatternInventory().setItem(slot, r.toItem());
		else report.add(Component.literal("  pattern " + slot + ": ").append(Component.translatable(r.error())));
	}

	private BlockPos next(String id) {
		BlockPos o = origin.offset(0, 0, station * SPACING);
		station++;
		for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
			level.setBlock(o.offset(x, -2, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			level.setBlock(o.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			for (int y = 0; y < 5; y++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		level.setBlock(o, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(o) instanceof SignBlockEntity sign) {
			Component[] lines = {Component.literal(station + ". ").append(Component.translatable("teststation.tsautocraft." + id)),
					Component.translatable("teststation.tsautocraft." + id + ".l2"), Component.translatable("teststation.tsautocraft." + id + ".l3"), Component.empty()};
			//? if >=26.3 {
			sign.setText(new SignText(List.of(lines), List.of(lines), DyeColor.BLACK, false), SignTextSlot.FRONT);
			//?}
			//? if <26.3 {
			/*sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), true);
			*///?}
		}
		report.add(Component.literal(station + ". ").append(Component.translatable("teststation.tsautocraft." + id)).append(" @ " + o.getX() + ", " + o.getY() + ", " + o.getZ()));
		return o;
	}

	private void set(BlockPos p, Block b) {
		set(p, b.defaultBlockState());
	}

	private void set(BlockPos p, BlockState s) {
		level.setBlock(p, s, Block.UPDATE_ALL);
	}

	private void chest(BlockPos p, ItemStack... items) {
		set(p, Blocks.CHEST);
		if (level.getBlockEntity(p) instanceof ChestBlockEntity c) for (int i = 0; i < items.length && i < 27; i++) c.setItem(i, items[i]);
	}

	private static Block toms(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("toms_storage", id));
	}
}
