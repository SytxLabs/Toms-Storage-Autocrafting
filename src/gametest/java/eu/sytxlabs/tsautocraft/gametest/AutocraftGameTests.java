package eu.sytxlabs.tsautocraft.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.tom.storagemod.block.AbstractStorageTerminalBlock;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.TsAutocraftConfig;
import eu.sytxlabs.tsautocraft.block.ConnectorNodeBlock;
import eu.sytxlabs.tsautocraft.blockentity.AutocraftingTerminalBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.PatternProviderBlockEntity;
import eu.sytxlabs.tsautocraft.job.CraftingJob;
import eu.sytxlabs.tsautocraft.network.NetworkCache;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.pattern.PatternEncoder;

/**
 * Game tests of the whole chain (gradlew :<mc>-fabric:runGametest). Every test builds its setup in code:
 * network chest - Tom's Inventory Connector - Pattern Provider - machine, an Autocrafting Terminal behind the connector
 * and, where results come back, an Importer reached through Tom's cables.
 */
public class AutocraftGameTests {
	// Storage chest and connector; the terminal sits behind the connector, the provider east of it
	private static final BlockPos STORAGE = new BlockPos(1, 1, 2), CONNECTOR = new BlockPos(2, 1, 2), TERMINAL = new BlockPos(2, 1, 3);
	private static final BlockPos PROVIDER = new BlockPos(3, 1, 2), MACHINE = new BlockPos(4, 1, 2);

	private static Block toms(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("toms_storage", id));
	}

	/** Storage, connector, terminal and provider; returns the terminal. */
	private static AutocraftingTerminalBlockEntity network(GameTestHelper h, ItemStack... stock) {
		TsAutocraftConfig.get().crafterTrigger = TsAutocraftConfig.CrafterTrigger.DIRECT;
		TsAutocraftConfig.get().returnInputsOnCancel = true;
		h.setBlock(STORAGE, Blocks.CHEST);
		Container chest = h.getBlockEntity(STORAGE, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
		for (int i = 0; i < stock.length; i++) chest.setItem(i, stock[i].copy());
		h.setBlock(CONNECTOR, toms("inventory_connector"));
		h.setBlock(TERMINAL, ModContent.AUTOCRAFTING_TERMINAL.defaultBlockState().setValue(AbstractStorageTerminalBlock.FACING, Direction.NORTH));
		h.setBlock(PROVIDER, ModContent.PATTERN_PROVIDER);
		NetworkCache.invalidate();
		return h.getBlockEntity(TERMINAL, AutocraftingTerminalBlockEntity.class);
	}

	private static void addPattern(GameTestHelper h, PatternEncoder.Result r) {
		h.assertTrue(r.error() == null, "pattern not encoded: " + r.error());
		h.getBlockEntity(PROVIDER, PatternProviderBlockEntity.class).getPatternInventory().setItem(0, r.toItem());
		NetworkCache.invalidate();
	}

	private static PatternEncoder.Result sticks(GameTestHelper h) {
		List<ItemStack> grid = new ArrayList<>();
		for (int i = 0; i < 9; i++) grid.add(i == 1 || i == 4 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
		return PatternEncoder.encodeCrafting(h.getLevel(), grid, false);
	}

	private static PatternEncoder.Result stoneProcessing() {
		return PatternEncoder.encodeFree(PatternData.PROCESSING, List.of(new ItemStack(Items.COBBLESTONE)), List.of(new ItemStack(Items.STONE)));
	}

	/** Importer at {@code pos} pulling from {@code facing}, linked to the provider through cables at {@code cables}. */
	private static void importer(GameTestHelper h, BlockPos pos, Direction facing, BlockPos... cables) {
		for (BlockPos c : cables) h.setBlock(c, toms("inventory_cable"));
		h.setBlock(pos, ModContent.IMPORTER.defaultBlockState().setValue(ConnectorNodeBlock.FACING, facing));
		NetworkCache.invalidate();
	}

	private static long count(Container c, net.minecraft.world.item.Item item) {
		long n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	private static Container chest(GameTestHelper h, BlockPos pos) {
		return (Container) h.getBlockEntity(pos, BlockEntity.class);
	}

	private static CraftingJob onlyJob(GameTestHelper h, AutocraftingTerminalBlockEntity t) {
		h.assertValueEqual(t.activeJobs().size(), 1, "running jobs");
		return t.activeJobs().getFirst();
	}

	/** Waits until the network sees the stock (Tom's connector scans on its own ticks), then requests. */
	private static GameTestSequence request(GameTestHelper h, AutocraftingTerminalBlockEntity t, ItemStack item, long amount) {
		return h.startSequence()
				.thenWaitUntil(() -> h.assertTrue(t.preview(item, amount).ok(), "network does not see the stock yet"))
				.thenExecute(() -> {
					var r = t.request(item, amount);
					h.assertTrue(r.ok(), "request failed: " + r.error() + " " + r.missing());
				});
	}

	/** Crafting pattern into a crafter (trigger "direct"), result through an Importer: the job ends DONE. */
	@GameTest(maxTicks = 400)
	public void craftSticks(GameTestHelper h) {
		var t = network(h, new ItemStack(Items.OAK_PLANKS, 2));
		h.setBlock(MACHINE, Blocks.CRAFTER); // default orientation: front = north -> output chest
		h.setBlock(new BlockPos(4, 1, 1), Blocks.CHEST);
		importer(h, new BlockPos(4, 2, 1), Direction.DOWN, new BlockPos(3, 2, 2), new BlockPos(4, 2, 2));
		addPattern(h, sticks(h));
		request(h, t, new ItemStack(Items.STICK), 4)
				.thenWaitUntil(() -> {
					h.assertTrue(t.activeJobs().isEmpty(), "job still running");
					h.assertValueEqual(count(chest(h, STORAGE), Items.STICK), 4L, "sticks in storage");
				})
				.thenSucceed();
	}

	/** Cancelling a job gives back the inputs still waiting in the machine (processing pattern into a barrel). */
	@GameTest(maxTicks = 200)
	public void cancelReturnsInputs(GameTestHelper h) {
		var t = network(h, new ItemStack(Items.COBBLESTONE, 4));
		h.setBlock(MACHINE, Blocks.BARREL);
		addPattern(h, stoneProcessing());
		request(h, t, new ItemStack(Items.STONE), 2)
				.thenWaitUntil(() -> h.assertValueEqual(count(chest(h, MACHINE), Items.COBBLESTONE), 1L, "delivered to the barrel (blocking mode: one set)"))
				.thenExecute(() -> t.cancelJob(onlyJob(h, t).uid))
				.thenWaitUntil(() -> {
					h.assertValueEqual(count(chest(h, MACHINE), Items.COBBLESTONE), 0L, "left in the barrel");
					h.assertValueEqual(count(chest(h, STORAGE), Items.COBBLESTONE), 4L, "back in storage");
				})
				.thenSucceed();
	}

	/** A running job reserves its raw materials: a second plan for the same items fails. */
	@GameTest(maxTicks = 100)
	public void reservations(GameTestHelper h) {
		var t = network(h, new ItemStack(Items.OAK_PLANKS, 2));
		addPattern(h, sticks(h)); // no crafter: the job keeps waiting with its reservation
		request(h, t, new ItemStack(Items.STICK), 4)
				.thenExecute(() -> {
					var second = t.request(new ItemStack(Items.STICK), 4);
					h.assertFalse(second.ok(), "second request should miss planks");
					h.assertFalse(second.missing().isEmpty(), "second request should report missing planks");
				})
				.thenSucceed();
	}

	/** Smelting pattern into a furnace, result through an Importer below it: the job ends DONE. */
	@GameTest(maxTicks = 600)
	public void smeltInFurnace(GameTestHelper h) {
		var t = network(h, new ItemStack(Items.RAW_IRON, 1));
		h.setBlock(MACHINE, Blocks.FURNACE);
		chest(h, MACHINE).setItem(1, new ItemStack(Items.COAL));
		importer(h, new BlockPos(4, 0, 2), Direction.UP, new BlockPos(3, 0, 2));
		addPattern(h, PatternEncoder.encodeSmelting(h.getLevel(), List.of(new ItemStack(Items.RAW_IRON))));
		request(h, t, new ItemStack(Items.IRON_INGOT), 1)
				.thenWaitUntil(() -> {
					h.assertTrue(t.activeJobs().isEmpty(), "job still running");
					h.assertValueEqual(count(chest(h, STORAGE), Items.IRON_INGOT), 1L, "ingot in storage");
				})
				.thenSucceed();
	}

	/** The provider remembers which job filled the barrel across a save and load of provider and terminal. */
	@GameTest(maxTicks = 200)
	public void cancelAfterReload(GameTestHelper h) {
		var t = network(h, new ItemStack(Items.COBBLESTONE, 4));
		h.setBlock(MACHINE, Blocks.BARREL);
		addPattern(h, stoneProcessing());
		request(h, t, new ItemStack(Items.STONE), 2)
				.thenWaitUntil(() -> h.assertValueEqual(count(chest(h, MACHINE), Items.COBBLESTONE), 1L, "delivered to the barrel (blocking mode: one set)"))
				.thenExecute(() -> {
					reload(h, PROVIDER);
					reload(h, TERMINAL); // jobs come back as new objects with new runtime ids
				})
				.thenIdle(5) // the scheduler binds the reloaded job (persistent id known again)
				.thenExecute(() -> t.cancelJob(onlyJob(h, t).uid))
				.thenWaitUntil(() -> {
					h.assertValueEqual(count(chest(h, MACHINE), Items.COBBLESTONE), 0L, "left in the barrel");
					h.assertValueEqual(count(chest(h, STORAGE), Items.COBBLESTONE), 4L, "back in storage");
				})
				.thenSucceed();
	}

	/** Saves the block entity at {@code rel} and loads it again in place (like a chunk reload). */
	private static void reload(GameTestHelper h, BlockPos rel) {
		BlockEntity be = h.getBlockEntity(rel, BlockEntity.class);
		var reg = h.getLevel().registryAccess();
		CompoundTag tag = be.saveWithoutMetadata(reg);
		be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, reg, tag));
	}

	/** {@code /tsautocraft testsetup} builds its stations with valid patterns (far away, not to disturb other tests). */
	@GameTest(maxTicks = 100)
	public void testSetup(GameTestHelper h) {
		var player = h.makeMockServerPlayerInLevel();
		player.setPos(h.absolutePos(BlockPos.ZERO).getX() + 30_000.5, 220, h.absolutePos(BlockPos.ZERO).getZ() + 0.5);
		var lines = eu.sytxlabs.tsautocraft.command.TestSetup.build(player);
		h.assertValueEqual(lines.size(), 4, "4 stations and no pattern errors: " + lines.stream().map(c -> c.getString()).toList());
		BlockPos o = player.blockPosition().offset(2, 0, 4);
		var level = h.getLevel();
		h.assertTrue(level.getBlockEntity(o.offset(3, 0, 2)) instanceof PatternProviderBlockEntity p && p.getPatterns().size() == 3
				&& p.getUpgrades().getItem(0).is(eu.sytxlabs.tsautocraft.ModContent.REDSTONE_UPGRADE), "3 crafting patterns and a Redstone Upgrade in the provider");
		h.assertTrue(level.getBlockEntity(o.offset(1, 0, 5)) instanceof eu.sytxlabs.tsautocraft.blockentity.CraftingEmitterBlockEntity e
				&& e.getItem().getItem(0).is(net.minecraft.world.item.Items.STICK), "Crafting Emitter watches sticks");
		// Every node of the crafting station reaches the connector (it scans its inventories on its own ticks)
		BlockPos[] nodes = {o.offset(3, 0, 2), o.offset(1, 0, 3), o.offset(1, 0, 5), o.offset(3, 0, 4), o.offset(4, 1, 1)};
		String[] names = {"provider", "level maintainer", "crafting emitter", "requester", "importer"};
		h.succeedWhen(() -> {
			for (int i = 0; i < nodes.length; i++) {
				h.assertTrue(eu.sytxlabs.tsautocraft.network.NetworkCache.of(level, nodes[i]).connector() != null, names[i] + " is on the network");
			}
		});
	}
}
