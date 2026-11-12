package eu.sytxlabs.tsautocraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;

import com.tom.storagemod.screen.TSContainerScreen;
import com.tom.storagemod.util.IDataReceiver;

import eu.sytxlabs.tsautocraft.blockentity.AbstractIOBlockEntity;
import eu.sytxlabs.tsautocraft.blockentity.LevelMaintainerBlockEntity;
import eu.sytxlabs.tsautocraft.menu.LevelMaintainerMenu;
import eu.sytxlabs.tsautocraft.menu.MenuUtil;

/**
 * Two columns of 6 entries: ghost item, threshold and batch (text fields), status bar (hover: status and stock;
 * shape per state, problems also get a "!" on the slot).
 * Upgrade panel on the right, redstone mode button top right (only with a Redstone Upgrade).
 */
public class LevelMaintainerScreen extends TSContainerScreen<LevelMaintainerMenu> implements IDataReceiver {
	private static final int ENTRIES = LevelMaintainerMenu.ENTRIES, THR_X = 20, BAT_X = 64, BOX_W = 42, BAR_X = 108, BAR_W = 4;
	private static final String[] REDSTONE_KEYS = {"high", "low", "toggle"};
	private final EditBox[] thresholdBoxes = new EditBox[ENTRIES];
	private final EditBox[] batchBoxes = new EditBox[ENTRIES];
	private Button redstoneBtn;
	private int shownRedstone = -1;
	private boolean updating;

	public LevelMaintainerScreen(LevelMaintainerMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, LevelMaintainerMenu.WIDTH, LevelMaintainerMenu.HEIGHT);
		this.inventoryLabelX = LevelMaintainerMenu.PLAYER_X;
		this.inventoryLabelY = LevelMaintainerMenu.PLAYER_Y - 11;
		menu.onUpdate = this::onUpdate;
	}

	@Override
	protected void init() {
		super.init();
		for (int i = 0; i < ENTRIES; i++) {
			thresholdBoxes[i] = addRenderableWidget(box(i, THR_X, "thr", menu.thresholds[i], "tooltip.tsautocraft.maintainer.threshold"));
			batchBoxes[i] = addRenderableWidget(box(i, BAT_X, "bat", menu.batches[i], "tooltip.tsautocraft.maintainer.batch"));
		}
		//noinspection DataFlowIssue
		redstoneBtn = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)).bounds(leftPos + imageWidth - 22, topPos + 2, 16, 16).build());
		shownRedstone = -1;
	}

	/** Numeric text field of entry {@code i}; every change is sent as {@code set} + {@code key}. */
	private EditBox box(int i, int dx, String key, int value, String tooltip) {
		EditBox b = new EditBox(font, leftPos + LevelMaintainerMenu.slotX(i) + dx, topPos + LevelMaintainerMenu.slotY(i) + 1, BOX_W, 14, Component.translatable(tooltip));
		b.setMaxLength(7);
		b.setValue(Integer.toString(value));
		b.setTooltip(Tooltip.create(Component.translatable(tooltip)));
		b.setResponder(s -> {
			String digits = s.replaceAll("[^0-9]", "");
			if (!digits.equals(s)) {
				b.setValue(digits); // EditBox has no input filter in 26.x; this re-enters with clean text
				return;
			}
			if (updating || minecraft.player == null) return;
			int v;
			try {
				v = s.isEmpty() ? 0 : Integer.parseInt(s);
			} catch (NumberFormatException e) {
				return;
			}
			MenuUtil.sendToServer(minecraft.player, out -> {
				out.putInt("set", i);
				out.putInt(key, v);
			});
		});
		return b;
	}

	private void onUpdate() {
		updating = true;
		try {
			for (int i = 0; i < ENTRIES; i++) {
				if (thresholdBoxes[i] != null && !thresholdBoxes[i].isFocused()) thresholdBoxes[i].setValue(Integer.toString(menu.thresholds[i]));
				if (batchBoxes[i] != null && !batchBoxes[i].isFocused()) batchBoxes[i].setValue(Integer.toString(menu.batches[i]));
			}
		} finally {
			updating = false;
		}
	}

	@Override
	public void receive(ValueInput in) {
		menu.receive(in);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) return super.keyPressed(event);
		for (int i = 0; i < ENTRIES; i++) {
			for (EditBox b : new EditBox[] {thresholdBoxes[i], batchBoxes[i]}) {
				if (b != null && b.isFocused() && (b.keyPressed(event) || b.canConsumeInput())) return true;
			}
		}
		return super.keyPressed(event);
	}

	/** Icon of a redstone mode: with signal, without signal, toggle (same as the Importer/Exporter). */
	private static ItemStack redstoneIcon(int mode) {
		return new ItemStack(mode == AbstractIOBlockEntity.RS_LOW ? Items.LEVER : mode == AbstractIOBlockEntity.RS_TOGGLE ? Items.STONE_BUTTON : Items.REDSTONE_TORCH);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (redstoneBtn != null) {
			redstoneBtn.visible = menu.hasRedstoneUpgrade();
			if (shownRedstone != menu.redstoneMode) {
				shownRedstone = menu.redstoneMode;
				redstoneBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.tsautocraft.redstone." + REDSTONE_KEYS[Math.clamp(menu.redstoneMode, 0, REDSTONE_KEYS.length - 1)])));
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (redstoneBtn != null && redstoneBtn.visible) graphics.item(redstoneIcon(menu.redstoneMode), redstoneBtn.getX(), redstoneBtn.getY());
		int hovered = barAt(mouseX - leftPos, mouseY - topPos);
		if (hovered >= 0) graphics.setComponentTooltipForNextFrame(font, statusTooltip(hovered), mouseX, mouseY);
	}

	/** Entry whose status bar is at (mx, my) relative to the GUI, or -1. */
	private int barAt(int mx, int my) {
		for (int i = 0; i < ENTRIES; i++) {
			int x = LevelMaintainerMenu.slotX(i) + BAR_X - 1, y = LevelMaintainerMenu.slotY(i);
			if (mx >= x && mx < x + BAR_W + 2 && my >= y && my < y + 16) return i;
		}
		return -1;
	}

	private List<Component> statusTooltip(int i) {
		List<Component> l = new ArrayList<>();
		l.add(statusText(menu.status[i], menu.statusItems[i]));
		if (menu.status[i] != LevelMaintainerBlockEntity.ST_OFF && menu.status[i] != LevelMaintainerBlockEntity.ST_NO_NETWORK) {
			l.add(Component.translatable("status.tsautocraft.maintainer.stock", menu.stock[i], menu.thresholds[i], menu.batches[i]));
		}
		return l;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gr, int mouseX, int mouseY, float a) {
		super.extractBackground(gr, mouseX, mouseY, a);
		GuiUtil.panel(gr, leftPos, topPos, imageWidth, imageHeight);
		for (int i = 0; i < ENTRIES; i++) GuiUtil.slot(gr, leftPos + LevelMaintainerMenu.slotX(i) - 1, topPos + LevelMaintainerMenu.slotY(i) - 1);
		for (int i = 0; i < 36; i++) {
			int row = i / 9;
			GuiUtil.slot(gr, leftPos + LevelMaintainerMenu.PLAYER_X - 1 + i % 9 * 18, topPos + LevelMaintainerMenu.PLAYER_Y - 1 + (row < 3 ? row * 18 : 58));
		}
		GuiUtil.upgradePanel(gr, leftPos, topPos, imageWidth, LevelMaintainerBlockEntity.UPGRADE_SLOTS);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		super.extractLabels(g, xm, ym);
		Component thr = Component.translatable("label.tsautocraft.maintainer.threshold"), bat = Component.translatable("label.tsautocraft.maintainer.batch");
		for (int c = 0; c < 2; c++) {
			int x = LevelMaintainerMenu.ROW_X + c * LevelMaintainerMenu.COL_W;
			g.text(font, font.plainSubstrByWidth(thr.getString(), BOX_W), x + THR_X, LevelMaintainerMenu.ROW_Y - 10, 0xFF404040, false);
			g.text(font, font.plainSubstrByWidth(bat.getString(), BOX_W), x + BAT_X, LevelMaintainerMenu.ROW_Y - 10, 0xFF404040, false);
		}
		for (int i = 0; i < ENTRIES; i++) {
			int x = LevelMaintainerMenu.slotX(i) + BAR_X, y = LevelMaintainerMenu.slotY(i);
			g.fill(x - 1, y, x + BAR_W + 1, y + 16, 0xFF373737);
			statusBar(g, x, y + 1, menu.status[i]);
			if (isProblem(menu.status[i])) {
				// Red "!" badge on the entry slot's top left corner
				int sx = LevelMaintainerMenu.slotX(i) - 1, sy = LevelMaintainerMenu.slotY(i) - 1;
				g.fill(sx, sy, sx + 5, sy + 9, 0xFFB02020);
				g.text(font, "!", sx + 3 - font.width("!") / 2, sy + 1, 0xFFFFFFFF, false);
			}
		}
	}

	/**
	 * Status bar of 14 px height: the shape tells the state apart without relying on the color alone.
	 * Stocked and problems = solid, crafting = dashed, off/redstone = hollow outline.
	 */
	private static void statusBar(GuiGraphicsExtractor g, int x, int y, int st) {
		int color = statusColor(st), h = 14;
		switch (st) {
		case LevelMaintainerBlockEntity.ST_CRAFTING -> {
			for (int dy = 0; dy < h; dy += 4) g.fill(x, y + dy, x + BAR_W, y + Math.min(dy + 2, h), color);
		}
		case LevelMaintainerBlockEntity.ST_OFF, LevelMaintainerBlockEntity.ST_REDSTONE -> {
			g.fill(x, y, x + BAR_W, y + 1, color);
			g.fill(x, y + h - 1, x + BAR_W, y + h, color);
			g.fill(x, y + 1, x + 1, y + h - 1, color);
			g.fill(x + BAR_W - 1, y + 1, x + BAR_W, y + h - 1, color);
		}
		default -> g.fill(x, y, x + BAR_W, y + h, color);
		}
	}

	private static boolean isProblem(int st) {
		return st == LevelMaintainerBlockEntity.ST_MISSING || st == LevelMaintainerBlockEntity.ST_NO_PATTERN || st == LevelMaintainerBlockEntity.ST_NO_NETWORK;
	}

	private static Component statusText(int st, ItemStack item) {
		return switch (st) {
		case LevelMaintainerBlockEntity.ST_STOCKED -> Component.translatable("status.tsautocraft.maintainer.stocked");
		case LevelMaintainerBlockEntity.ST_CRAFTING -> item.isEmpty() ? Component.translatable("status.tsautocraft.maintainer.crafting") : Component.translatable("status.tsautocraft.maintainer.crafting_waiting", item.getHoverName());
		case LevelMaintainerBlockEntity.ST_MISSING -> item.isEmpty() ? Component.translatable("status.tsautocraft.maintainer.missing_any") : Component.translatable("status.tsautocraft.maintainer.missing", item.getHoverName());
		case LevelMaintainerBlockEntity.ST_NO_PATTERN -> Component.translatable("status.tsautocraft.maintainer.no_pattern");
		case LevelMaintainerBlockEntity.ST_NO_NETWORK -> Component.translatable("status.tsautocraft.maintainer.no_network");
		case LevelMaintainerBlockEntity.ST_REDSTONE -> Component.translatable("status.tsautocraft.maintainer.redstone");
		default -> Component.translatable("status.tsautocraft.maintainer.off");
		};
	}

	private static int statusColor(int st) {
		return switch (st) {
		case LevelMaintainerBlockEntity.ST_STOCKED -> 0xFF2E9E3E;
		case LevelMaintainerBlockEntity.ST_CRAFTING -> 0xFF3B6FD8;
		case LevelMaintainerBlockEntity.ST_REDSTONE -> 0xFFE08A1E;
		case LevelMaintainerBlockEntity.ST_OFF -> 0xFF8B8B8B;
		default -> 0xFFC0302A;
		};
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int left, int top) {
		return super.hasClickedOutside(mx, my, left, top) && !GuiUtil.inUpgradePanel(mx, my, left, top, imageWidth, LevelMaintainerBlockEntity.UPGRADE_SLOTS);
	}

	@Override
	public void getExclusionAreas(Consumer<Box> consumer) {
		consumer.accept(GuiUtil.upgradeBox(leftPos, topPos, imageWidth, LevelMaintainerBlockEntity.UPGRADE_SLOTS));
	}
}
