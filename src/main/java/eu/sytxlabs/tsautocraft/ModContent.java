package eu.sytxlabs.tsautocraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType.MenuSupplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.tom.storagemod.block.IPaintable;

import eu.sytxlabs.tsautocraft.api.Upgrades;
import eu.sytxlabs.tsautocraft.block.AutocraftingTerminalBlock;
import eu.sytxlabs.tsautocraft.block.PatternAccessTerminalBlock;
import eu.sytxlabs.tsautocraft.block.ConnectorNodeBlock;
import eu.sytxlabs.tsautocraft.block.CraftingEmitterBlock;
import eu.sytxlabs.tsautocraft.block.FramedConnectorNodeBlock;
import eu.sytxlabs.tsautocraft.block.NodeBlock;
import eu.sytxlabs.tsautocraft.block.PatternProviderBlock;
import eu.sytxlabs.tsautocraft.block.PatternTerminalBlock;
import eu.sytxlabs.tsautocraft.blockentity.AutocraftingTerminalBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.PatternAccessTerminalBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.CraftingEmitterBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.ExporterBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.ImporterBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.PatternProviderBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.PatternTerminalBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.RequesterBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.LevelMaintainerBlockEntity;
import eu.sytxlabs.tsautocraft.menu.AutocraftingTerminalMenu;
import eu.sytxlabs.tsautocraft.menu.PatternAccessTerminalMenu;
import eu.sytxlabs.tsautocraft.menu.CraftingEmitterMenu;
import eu.sytxlabs.tsautocraft.menu.IOMenu;
import eu.sytxlabs.tsautocraft.menu.PatternProviderMenu;
import eu.sytxlabs.tsautocraft.menu.PatternTerminalMenu;
import eu.sytxlabs.tsautocraft.menu.RequesterMenu;
import eu.sytxlabs.tsautocraft.menu.LevelMaintainerMenu;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.pattern.PatternItem;
import eu.sytxlabs.tsautocraft.platform.Platform;

public final class ModContent {
	private static final List<Item> TAB_ITEMS = new ArrayList<>();

	/** All items of the creative tab (e.g. for {@code /tsautocraft testsetup}). */
	public static List<Item> items() {
		return java.util.Collections.unmodifiableList(TAB_ITEMS);
	}

	// --- Data component ---
	public static final DataComponentType<PatternData> PATTERN_COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, TsAutocraft.id("pattern"), DataComponentType.<PatternData>builder().persistent(PatternData.CODEC).build());
	/** Description line of our blocks and upgrades (default component, see {@link ItemDescription}). */
	public static final DataComponentType<ItemDescription> DESCRIPTION_COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, TsAutocraft.id("description"), DataComponentType.<ItemDescription>builder().persistent(ItemDescription.CODEC).networkSynchronized(ItemDescription.STREAM_CODEC).build());

	/** Fluid of a {@code fluid_unit} stack (API 4, {@code api/FluidUnits}). */
	public static final DataComponentType<net.minecraft.resources.Identifier> FLUID_UNIT_COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, TsAutocraft.id("fluid_unit"), DataComponentType.<net.minecraft.resources.Identifier>builder().persistent(net.minecraft.resources.Identifier.CODEC).networkSynchronized(net.minecraft.resources.Identifier.STREAM_CODEC).build());
	/** Settings stored on a Memory Card. */
	public static final DataComponentType<MemoryCardData> MEMORY_CARD_COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, TsAutocraft.id("memory_card"), DataComponentType.<MemoryCardData>builder().persistent(MemoryCardData.CODEC).build());

	// --- Blocks ---
	public static final ConnectorNodeBlock EXPORTER = block("exporter", p -> new ConnectorNodeBlock(p.noOcclusion(), ExporterBlockEntity::new, false));
	public static final FramedConnectorNodeBlock EXPORTER_FRAMED = block("exporter_framed", p -> new FramedConnectorNodeBlock(p.noOcclusion(), ExporterBlockEntity::new, false));
	public static final ConnectorNodeBlock IMPORTER = block("importer", p -> new ConnectorNodeBlock(p.noOcclusion(), ImporterBlockEntity::new, false));
	public static final FramedConnectorNodeBlock IMPORTER_FRAMED = block("importer_framed", p -> new FramedConnectorNodeBlock(p.noOcclusion(), ImporterBlockEntity::new, false));
	public static final PatternProviderBlock PATTERN_PROVIDER = block("pattern_provider", p -> new PatternProviderBlock(p, PatternProviderBlockEntity::new));
	public static final ConnectorNodeBlock PATTERN_PROVIDER_CONNECTOR = block("pattern_provider_connector", p -> new ConnectorNodeBlock(p.noOcclusion(), PatternProviderBlockEntity::new, true));
	public static final FramedConnectorNodeBlock PATTERN_PROVIDER_CONNECTOR_FRAMED = block("pattern_provider_connector_framed", p -> new FramedConnectorNodeBlock(p.noOcclusion(), PatternProviderBlockEntity::new, true));
	public static final PatternTerminalBlock PATTERN_TERMINAL = block("pattern_terminal", p -> new PatternTerminalBlock(p.noOcclusion().lightLevel(s -> 6)));
	public static final AutocraftingTerminalBlock AUTOCRAFTING_TERMINAL = block("autocrafting_terminal", p -> new AutocraftingTerminalBlock(p.noOcclusion().lightLevel(s -> 6)));
	public static final PatternAccessTerminalBlock PATTERN_ACCESS_TERMINAL = block("pattern_access_terminal", p -> new PatternAccessTerminalBlock(p.noOcclusion().lightLevel(s -> 6)));
	public static final NodeBlock REQUESTER = block("requester", p -> new NodeBlock(p, RequesterBlockEntity::new));
	public static final NodeBlock LEVEL_MAINTAINER = block("level_maintainer", p -> new NodeBlock(p, LevelMaintainerBlockEntity::new));
	public static final CraftingEmitterBlock CRAFTING_EMITTER = block("crafting_emitter", p -> new CraftingEmitterBlock(p, CraftingEmitterBlockEntity::new));
	/** Crafting Emitter in connector shape: signal only out of its front. */
	public static final ConnectorNodeBlock CRAFTING_EMITTER_DIRECTIONAL = block("crafting_emitter_directional", p -> new ConnectorNodeBlock(p.noOcclusion(), CraftingEmitterBlockEntity::new, true));
	public static final FramedConnectorNodeBlock CRAFTING_EMITTER_DIRECTIONAL_FRAMED = block("crafting_emitter_directional_framed", p -> new FramedConnectorNodeBlock(p.noOcclusion(), CraftingEmitterBlockEntity::new, true));
	/** Blocks that use Tom's painted model (client registers {@code UnbakedPaintedModel} and tint for these). */
	public static final List<Block> FRAMED_BLOCKS = List.of(EXPORTER_FRAMED, IMPORTER_FRAMED, PATTERN_PROVIDER_CONNECTOR_FRAMED, CRAFTING_EMITTER_DIRECTIONAL_FRAMED);

	// --- Items ---
	public static final Item BLANK_PATTERN = item("blank_pattern", Item::new, p -> p.stacksTo(64));
	public static final PatternItem PATTERN = item("pattern", PatternItem::new, p -> p.stacksTo(1));
	public static final Item CAPACITY_UPGRADE = upgrade("capacity_upgrade");
	public static final Item SPEED_UPGRADE = upgrade("speed_upgrade");
	public static final Item STACK_UPGRADE = upgrade("stack_upgrade");
	public static final Item CRAFTING_UPGRADE = upgrade("crafting_upgrade");
	/** Importer/Exporter: work only with / without a redstone signal or toggle on pulses; Pattern Provider: redstone pulse after every delivery. */
	public static final Item REDSTONE_UPGRADE = upgrade("redstone_upgrade");
	/** In-game guide, right click opens {@code client/GuideScreen}. */
	/** Copies settings between blocks of the same type. */
	public static final MemoryCardItem MEMORY_CARD = item("memory_card", MemoryCardItem::new, p -> p.stacksTo(1).component(DESCRIPTION_COMPONENT, new ItemDescription("tooltip.tsautocraft.memory_card", false)));
	/** Virtual item for exact fluid amounts in patterns (API 4); not in the creative tab. */
	public static final FluidUnitItem FLUID_UNIT = hiddenItem("fluid_unit", FluidUnitItem::new);
	public static final GuideBookItem GUIDE_BOOK = item("guide_book", GuideBookItem::new, p -> p.stacksTo(1).component(DESCRIPTION_COMPONENT, new ItemDescription("tooltip.tsautocraft.guide_book", false)));

	static {
		Upgrades.register(Upgrades.PATTERN_PROVIDER, CAPACITY_UPGRADE, PatternProviderBlockEntity.MAX_CAPACITY);
		Upgrades.register(Upgrades.PATTERN_PROVIDER, SPEED_UPGRADE, 4);
		Upgrades.register(Upgrades.PATTERN_PROVIDER, STACK_UPGRADE, 3); // 4^3 = 64 crafts per delivery
		Upgrades.register(Upgrades.AUTOCRAFTING_TERMINAL, CAPACITY_UPGRADE, 8);
		for (var host : List.of(Upgrades.EXPORTER, Upgrades.IMPORTER)) {
			Upgrades.register(host, SPEED_UPGRADE, 4);
			Upgrades.register(host, STACK_UPGRADE, 3);
		}
		Upgrades.register(Upgrades.EXPORTER, CRAFTING_UPGRADE);
		Upgrades.register(Upgrades.EXPORTER, REDSTONE_UPGRADE);
		Upgrades.register(Upgrades.IMPORTER, REDSTONE_UPGRADE);
		Upgrades.register(Upgrades.PATTERN_PROVIDER, REDSTONE_UPGRADE);
		Upgrades.register(Upgrades.LEVEL_MAINTAINER, SPEED_UPGRADE, 4);
		Upgrades.register(Upgrades.LEVEL_MAINTAINER, REDSTONE_UPGRADE);
	}

	// --- Block Entities ---
	public static final BlockEntityType<ExporterBlockEntity> EXPORTER_BE = blockEntity("exporter", ExporterBlockEntity::new, EXPORTER, EXPORTER_FRAMED);
	public static final BlockEntityType<ImporterBlockEntity> IMPORTER_BE = blockEntity("importer", ImporterBlockEntity::new, IMPORTER, IMPORTER_FRAMED);
	public static final BlockEntityType<PatternProviderBlockEntity> PATTERN_PROVIDER_BE = blockEntity("pattern_provider", PatternProviderBlockEntity::new, PATTERN_PROVIDER, PATTERN_PROVIDER_CONNECTOR, PATTERN_PROVIDER_CONNECTOR_FRAMED);
	public static final BlockEntityType<PatternTerminalBlockEntity> PATTERN_TERMINAL_BE = blockEntity("pattern_terminal", PatternTerminalBlockEntity::new, PATTERN_TERMINAL);
	public static final BlockEntityType<AutocraftingTerminalBlockEntity> AUTOCRAFTING_TERMINAL_BE = blockEntity("autocrafting_terminal", AutocraftingTerminalBlockEntity::new, AUTOCRAFTING_TERMINAL);
	public static final BlockEntityType<PatternAccessTerminalBlockEntity> PATTERN_ACCESS_TERMINAL_BE = blockEntity("pattern_access_terminal", PatternAccessTerminalBlockEntity::new, PATTERN_ACCESS_TERMINAL);
	public static final BlockEntityType<RequesterBlockEntity> REQUESTER_BE = blockEntity("requester", RequesterBlockEntity::new, REQUESTER);
	public static final BlockEntityType<LevelMaintainerBlockEntity> LEVEL_MAINTAINER_BE = blockEntity("level_maintainer", LevelMaintainerBlockEntity::new, LEVEL_MAINTAINER);
	public static final BlockEntityType<CraftingEmitterBlockEntity> CRAFTING_EMITTER_BE = blockEntity("crafting_emitter", CraftingEmitterBlockEntity::new, CRAFTING_EMITTER, CRAFTING_EMITTER_DIRECTIONAL, CRAFTING_EMITTER_DIRECTIONAL_FRAMED);

	// --- Menus (client constructor without block entity) ---
	public static final MenuType<IOMenu> EXPORTER_MENU = menu("exporter", (id, inv) -> new IOMenu(ModContent.EXPORTER_MENU, id, inv, null));
	public static final MenuType<IOMenu> IMPORTER_MENU = menu("importer", (id, inv) -> new IOMenu(ModContent.IMPORTER_MENU, id, inv, null));
	public static final MenuType<PatternProviderMenu> PATTERN_PROVIDER_MENU = menu("pattern_provider", PatternProviderMenu::new);
	public static final MenuType<PatternTerminalMenu> PATTERN_TERMINAL_MENU = menu("pattern_terminal", PatternTerminalMenu::new);
	public static final MenuType<AutocraftingTerminalMenu> AUTOCRAFTING_TERMINAL_MENU = menu("autocrafting_terminal", AutocraftingTerminalMenu::new);
	public static final MenuType<PatternAccessTerminalMenu> PATTERN_ACCESS_TERMINAL_MENU = menu("pattern_access_terminal", PatternAccessTerminalMenu::new);
	public static final MenuType<RequesterMenu> REQUESTER_MENU = menu("requester", RequesterMenu::new);
	public static final MenuType<LevelMaintainerMenu> LEVEL_MAINTAINER_MENU = menu("level_maintainer", LevelMaintainerMenu::new);
	public static final MenuType<CraftingEmitterMenu> CRAFTING_EMITTER_MENU = menu("crafting_emitter", CraftingEmitterMenu::new);

	// --- Creative Tab ---
	@SuppressWarnings("unused")
    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, TsAutocraft.id("tab")),
			Platform.creativeTab().title(Component.translatable("itemGroup.tsautocraft.tab")).icon(() -> new ItemStack(ModContent.PATTERN_PROVIDER))
			.displayItems((params, out) -> TAB_ITEMS.forEach(out::accept)).build());

	private ModContent() {}

	/** Loads the class and thereby all static registrations. */
	public static void init() {}

	/** Same as Tom's wooden storage blocks: wood sound, strength 3, mined with an axe (tag mineable/axe). */
	private static BlockBehaviour.Properties defaults(BlockBehaviour.Properties p) {
		return p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(3);
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TsAutocraft.id(name));
		B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(defaults(BlockBehaviour.Properties.of().setId(key))));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TsAutocraft.id(name));
		ItemDescription desc = new ItemDescription("tooltip.tsautocraft." + name.replace("_framed", ""), block instanceof IPaintable);
		BlockItem item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().component(DESCRIPTION_COMPONENT, desc)));
		TAB_ITEMS.add(item);
		return block;
	}

	/** Item that is not in the creative tab (virtual items like {@link #FLUID_UNIT}). */
	private static <I extends Item> I hiddenItem(String name, Function<Item.Properties, I> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TsAutocraft.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	/** Upgrade item with the description {@code tooltip.tsautocraft.<id>}. */
	private static Item upgrade(String name) {
		return item(name, Item::new, p -> p.stacksTo(16).component(DESCRIPTION_COMPONENT, new ItemDescription("tooltip.tsautocraft." + name, false)));
	}

	private static <I extends Item> I item(String name, Function<Item.Properties, I> factory, java.util.function.UnaryOperator<Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TsAutocraft.id(name));
		I item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
		TAB_ITEMS.add(item);
		return item;
	}

	private static <T extends BlockEntity> BlockEntityType<T> blockEntity(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TsAutocraft.id(name), new BlockEntityType<>(factory, java.util.Set.of(blocks)));
	}

	private static <M extends AbstractContainerMenu> MenuType<M> menu(String name, MenuSupplier<M> supplier) {
		return Registry.register(BuiltInRegistries.MENU, TsAutocraft.id(name), new MenuType<>(supplier, FeatureFlags.VANILLA_SET));
	}
}
