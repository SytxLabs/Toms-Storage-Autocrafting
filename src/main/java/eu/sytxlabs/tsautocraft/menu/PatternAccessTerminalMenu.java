package eu.sytxlabs.tsautocraft.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import com.tom.storagemod.network.NetworkHandler;
import com.tom.storagemod.util.IDataReceiver;

import eu.sytxlabs.tsautocraft.util.SyncHash;
import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.blockentity.PatternAccessTerminalBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.PatternProviderBlockEntity;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.pattern.PatternItem;

/**
 * Pattern Access Terminal: only a window of {@value #LINES} lines (slots 0-53, player inventory 54-89) are real slots,
 * mapped server side to the provider slots in view.
 * <p>Client -> server: {@code scroll} (first line) with {@code all} or {@code view} (providers passing the client-side
 * filter, so names match the player's language). Server -> client (every 10 ticks if changed, at once after a request):
 * {@code dir} (providers with name, slot count, outputs), {@code lines} (the window), {@code scroll} (clamped).
 */
public class PatternAccessTerminalMenu extends AbstractContainerMenu implements IDataReceiver {
	public static final int WIDTH = 194, HEIGHT = 228, GRID_X = 8, GRID_Y = 18, LINES = 6, COLS = 9, PLAYER_Y = 146;
	public static final int WINDOW = LINES * COLS, PLAYER = WINDOW;

	/** A provider of the network: position, name, usable slots, filled slots and the primary output of each pattern. */
	public record Entry(long pos, Component name, int slots, int used, List<ItemStack> outputs) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("p").forGetter(Entry::pos),
				ComponentSerialization.CODEC.fieldOf("n").forGetter(Entry::name),
				Codec.INT.fieldOf("s").forGetter(Entry::slots),
				Codec.INT.fieldOf("u").forGetter(Entry::used),
				ItemStack.OPTIONAL_CODEC.listOf().fieldOf("o").forGetter(Entry::outputs))
				.apply(i, Entry::new));

		public int rows() {
			return slots / COLS;
		}

		public boolean full() {
			return used >= slots;
		}
	}

	/** A line of the window: header of the provider at {@code pos} ({@code row == -1}) or its pattern row {@code row}. */
	public record Line(long pos, int row) {
		public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("p").forGetter(Line::pos),
				Codec.INT.fieldOf("r").forGetter(Line::row))
				.apply(i, Line::new));

		public boolean header() {
			return row < 0;
		}
	}

	private final PatternAccessTerminalBlockEntity be;
	private final Inventory inv;

	// Server state
	private final PatternProviderBlockEntity[] mapProvider = new PatternProviderBlockEntity[WINDOW];
	private final int[] mapSlot = new int[WINDOW];
	private List<PatternProviderBlockEntity> directory = List.of();
	/** Positions of the providers to show, null = all. */
	private Set<Long> view;
	private int syncTimer;
	private boolean sent;
	private long lastHash;

	// Client state (the server keeps its own lines too)
	public final List<Entry> entries = new ArrayList<>();
	/** Client: bumped on every directory update (screens cache their filtered view by it). */
	public int revision;
	public List<Line> lines = List.of();
	public int scroll;
	public Runnable onUpdate;

	public PatternAccessTerminalMenu(int id, Inventory inv) {
		this(id, inv, null);
	}

	public PatternAccessTerminalMenu(int id, Inventory inv, PatternAccessTerminalBlockEntity be) {
		super(ModContent.PATTERN_ACCESS_TERMINAL_MENU, id);
		this.be = be;
		this.inv = inv;
		Container window = be != null ? new Window() : new SimpleContainer(WINDOW);
		for (int i = 0; i < WINDOW; i++) addSlot(new PatternSlot(window, i, GRID_X + i % COLS * 18, GRID_Y + i / COLS * 18));
		MenuUtil.addPlayerSlots(this::addSlot, inv, GRID_X, PLAYER_Y);
		if (be != null) {
			directory = be.providers();
			remap();
		}
	}

	//region Server: mapping of the window

	/** Provider behind window slot {@code i}, or null if the line is empty/a header or the provider is gone or no longer has that slot. */
	private PatternProviderBlockEntity provider(int i) {
		if (i < 0 || i >= WINDOW) return null;
		PatternProviderBlockEntity p = mapProvider[i];
		if (p == null || p.isRemoved() || p.getLevel() != be.getLevel() || p.getLevel().getBlockEntity(p.getBlockPos()) != p) return null;
		return mapSlot[i] < PatternProviderBlockEntity.activeSlots(p.getUpgrades()) ? p : null;
	}

	private List<PatternProviderBlockEntity> visibleProviders() {
		if (view == null) return directory;
		return directory.stream().filter(p -> view.contains(p.getBlockPos().asLong())).toList();
	}

	/** Rebuilds the window lines from the directory, the view and the scroll offset. */
	private void remap() {
		Arrays.fill(mapProvider, null);
		List<PatternProviderBlockEntity> vis = visibleProviders();
		int total = 0;
		for (PatternProviderBlockEntity p : vis) total += 1 + PatternProviderBlockEntity.activeSlots(p.getUpgrades()) / COLS;
		scroll = Mth.clamp(scroll, 0, Math.max(0, total - LINES));
		List<Line> out = new ArrayList<>(LINES);
		int line = 0;
		for (PatternProviderBlockEntity p : vis) {
			int rows = PatternProviderBlockEntity.activeSlots(p.getUpgrades()) / COLS;
			for (int r = -1; r < rows && out.size() < LINES; r++) {
				if (line++ < scroll) continue;
				if (r >= 0) {
					for (int c = 0; c < COLS; c++) {
						mapProvider[out.size() * COLS + c] = p;
						mapSlot[out.size() * COLS + c] = r * COLS + c;
					}
				}
				out.add(new Line(p.getBlockPos().asLong(), r));
			}
			if (out.size() >= LINES) break;
		}
		lines = out;
	}

	private static Entry entry(PatternProviderBlockEntity p) {
		int slots = PatternProviderBlockEntity.activeSlots(p.getUpgrades()), used = 0;
		List<ItemStack> outputs = new ArrayList<>();
		for (int i = 0; i < slots; i++) {
			ItemStack st = p.getPatternInventory().getItem(i);
			if (st.isEmpty()) continue;
			used++;
			PatternData d = PatternItem.get(st);
			if (d != null && !d.primaryOutput().isEmpty()) outputs.add(d.primaryOutput().copyWithCount(1));
		}
		return new Entry(p.getBlockPos().asLong(), PatternAccessTerminalBlockEntity.providerName(p), slots, used, outputs);
	}

	@Override
	public void broadcastChanges() {
		if (be != null && syncTimer-- <= 0) {
			syncTimer = 10;
			directory = be.providers();
			remap();
			super.broadcastChanges();
			sendState();
			return;
		}
		super.broadcastChanges();
	}

	/** Directory, window and scroll to the client, only if something changed. */
	private void sendState() {
		if (!(inv.player instanceof ServerPlayer sp)) return;
		List<Entry> entries = new ArrayList<>(directory.size());
		for (PatternProviderBlockEntity p : directory) entries.add(entry(p));
		// Compare a content hash first: encoding everything to NBT every 10 ticks just to find "no change" is the expensive part
		long hash = SyncHash.of(entries, lines, scroll);
		if (sent && hash == lastHash) return;
		sent = true;
		lastHash = hash;
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, sp.registryAccess());
		var dir = out.list("dir", Entry.CODEC);
		entries.forEach(dir::add);
		var l = out.list("lines", Line.CODEC);
		lines.forEach(l::add);
		out.putInt("scroll", scroll);
		NetworkHandler.sendTo(sp, out.buildResult());
	}

	@Override
	public void receive(ValueInput in) {
		if (be == null) {
			entries.clear();
			entries.addAll(in.listOrEmpty("dir", Entry.CODEC).stream().toList());
			revision++;
			lines = in.listOrEmpty("lines", Line.CODEC).stream().toList();
			scroll = in.getIntOr("scroll", 0);
			if (onUpdate != null) onUpdate.run();
			return;
		}
		var s = in.getInt("scroll");
		if (s.isEmpty()) return;
		scroll = Math.max(0, s.get());
		if (in.getBooleanOr("all", true)) {
			view = null;
		} else {
			view = new HashSet<>();
			in.listOrEmpty("view", Codec.LONG).stream().limit(4096).forEach(view::add);
		}
		syncTimer = 0;
	}
	//endregion

	/** Client: positions of the providers in the order of the directory (for the "view" request). */
	public static void writeRequest(net.minecraft.world.level.storage.ValueOutput out, List<Entry> shown, boolean all, int scroll) {
		out.putInt("scroll", scroll);
		out.putBoolean("all", all);
		if (!all) {
			var l = out.list("view", Codec.LONG);
			for (Entry e : shown) l.add(e.pos());
		}
	}

	/** Is window slot {@code i} a usable pattern slot? Server: mapped to a provider; client: on a pattern row line. */
	public boolean slotActive(int i) {
		if (be != null) return provider(i) != null;
		int line = i / COLS;
		return line < lines.size() && !lines.get(line).header();
	}

	@Override
	public boolean stillValid(Player player) {
		return be == null || be.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < PLAYER) {
			if (!moveItemStackTo(stack, PLAYER, PLAYER + 36, true)) return ItemStack.EMPTY;
		} else if (stack.getItem() instanceof PatternItem) {
			// First free visible slot, else the first free slot of any provider on the network
			if (!moveItemStackTo(stack, 0, PLAYER, false) && !insertAnywhere(stack)) return ItemStack.EMPTY;
		} else if (index < PLAYER + 27) {
			if (!moveItemStackTo(stack, PLAYER + 27, PLAYER + 36, false)) return ItemStack.EMPTY;
		} else if (!moveItemStackTo(stack, PLAYER, PLAYER + 27, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}

	/** Server: puts one pattern into the first free slot of any provider of the directory. */
	private boolean insertAnywhere(ItemStack stack) {
		if (be == null) return false;
		for (PatternProviderBlockEntity p : visibleProvidersFirst()) {
			if (p.isRemoved() || p.getLevel() != be.getLevel()) continue;
			int active = PatternProviderBlockEntity.activeSlots(p.getUpgrades());
			for (int i = 0; i < active; i++) {
				if (p.getPatternInventory().getItem(i).isEmpty()) {
					p.getPatternInventory().setItem(i, stack.split(1));
					syncTimer = 0;
					return true;
				}
			}
		}
		return false;
	}

	private List<PatternProviderBlockEntity> visibleProvidersFirst() {
		List<PatternProviderBlockEntity> l = new ArrayList<>(visibleProviders());
		for (PatternProviderBlockEntity p : directory) if (!l.contains(p)) l.add(p);
		return l;
	}

	/** Window slot: patterns only, one per slot, usable only on a pattern row. */
	private class PatternSlot extends Slot {
		PatternSlot(Container c, int index, int x, int y) {
			super(c, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof PatternItem && slotActive(getContainerSlot());
		}

		@Override
		public boolean mayPickup(Player player) {
			return slotActive(getContainerSlot());
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean isActive() {
			return slotActive(getContainerSlot());
		}
	}

	/** Server container of the window: every access goes straight to the mapped provider slot (checked each time). */
	private class Window implements Container {
		@Override
		public int getContainerSize() {
			return WINDOW;
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < WINDOW; i++) if (!getItem(i).isEmpty()) return false;
			return true;
		}

		@Override
		public ItemStack getItem(int i) {
			PatternProviderBlockEntity p = provider(i);
			return p != null ? p.getPatternInventory().getItem(mapSlot[i]) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItem(int i, int count) {
			PatternProviderBlockEntity p = provider(i);
			return p != null ? p.getPatternInventory().removeItem(mapSlot[i], count) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItemNoUpdate(int i) {
			PatternProviderBlockEntity p = provider(i);
			if (p == null) return ItemStack.EMPTY;
			ItemStack st = p.getPatternInventory().removeItemNoUpdate(mapSlot[i]);
			p.getPatternInventory().setChanged(); // still a change of the provider
			return st;
		}

		@Override
		public void setItem(int i, ItemStack stack) {
			PatternProviderBlockEntity p = provider(i);
			if (p != null) p.getPatternInventory().setItem(mapSlot[i], stack);
		}

		@Override
		public boolean canPlaceItem(int i, ItemStack stack) {
			return stack.getItem() instanceof PatternItem && provider(i) != null;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
			// The providers' containers mark their block entities themselves
		}

		@Override
		public boolean stillValid(Player player) {
			return be.stillValid(player);
		}

		@Override
		public void clearContent() {
			// Never clears the providers
		}
	}
}
