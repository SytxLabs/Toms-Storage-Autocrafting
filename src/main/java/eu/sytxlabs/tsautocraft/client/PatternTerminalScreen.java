package eu.sytxlabs.tsautocraft.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import com.tom.storagemod.screen.AbstractStorageTerminalScreen;

import eu.sytxlabs.tsautocraft.TsAutocraft;
import eu.sytxlabs.tsautocraft.menu.PatternTerminalMenu;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.pattern.PatternEncoder;

public class PatternTerminalScreen extends AbstractStorageTerminalScreen<PatternTerminalMenu> {
	private static final Identifier GUI = TsAutocraft.id("textures/gui/pattern_terminal.png");
	private Button modeBtn, substBtn, clearBtn, loadBtn;
	/** Icons of the clear and load buttons (barrier = empty the grid, pattern = load it for editing). */
	private static final ItemStack CLEAR_ICON = new ItemStack(net.minecraft.world.item.Items.BARRIER);
	private static ItemStack loadIcon;
	private int shownMode;
	private boolean shownSubst;
	private Component message = Component.empty();
	private int messageTicks;

	@Override
	protected void containerTick() {
		super.containerTick();
		if (messageTicks > 0) messageTicks--; // game ticks, not frames
	}
	/** x2, /2, x10, /10 right of the encoding area (free-amount modes only). */
	private final Button[] scaleBtns = new Button[PatternTerminalMenu.SCALES.length];
	/** Tooltip key last set per scale button, to update tooltips only when the reason changes. */
	private final String[] scaleReasons = new String[PatternTerminalMenu.SCALES.length];
	private static final int SCALE_X = 177, SCALE_W = 22, SCALE_H = 13;

	public PatternTerminalScreen(PatternTerminalMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 5, 194, 256, 7, 17);
	}

	@Override
	public Identifier getGui() {
		return GUI;
	}

	private int areaY() {
		return PatternTerminalMenu.AREA_Y + (rowCount - textureSlotCount) * 18;
	}

	@Override
	protected void init() {
		super.init();
		int y = topPos + areaY() + 16;
		modeBtn = addRenderableWidget(Button.builder(modeLabel(), b -> click(0)).bounds(leftPos + 5, y, 18, 18).build());
		shownMode = menu.mode;
		modeBtn.setTooltip(modeTooltip());
		clearBtn = addRenderableWidget(Button.builder(Component.empty(), b -> click(3)).bounds(leftPos + 5, y + 20, 18, 16).build());
		clearBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.tsautocraft.clear")));
		loadBtn = addRenderableWidget(Button.builder(Component.empty(), b -> click(2)).bounds(leftPos + 5, y + 38, 18, 16).build());
		loadBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.tsautocraft.load")));
		addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.encode"), b -> click(1)).bounds(leftPos + 122, y + 19, 48, 16).build());
		substBtn = addRenderableWidget(Button.builder(substLabel(), b -> click(4)).bounds(leftPos + 150, y + 1, 20, 16).build());
		shownSubst = menu.substitutes;
		substBtn.setTooltip(substTooltip());
		for (int i = 0; i < scaleBtns.length; i++) {
			int[] f = PatternTerminalMenu.SCALES[i];
			int id = PatternTerminalMenu.BTN_SCALE + i;
			Component label = f[1] == 1 ? Component.translatable("button.tsautocraft.scale.mul", f[0]) : Component.translatable("button.tsautocraft.scale.div", f[1]);
			scaleBtns[i] = addRenderableWidget(Button.builder(label, b -> click(id)).bounds(leftPos + SCALE_X, topPos + areaY() + 17 + i * (SCALE_H + 1), SCALE_W, SCALE_H).build());
			scaleReasons[i] = null;
		}
		updateScaleButtons();
		onPacket();
	}

	/** Shows the scale buttons only for free-amount modes; greys them out (with the reason as tooltip) when the result would be invalid. */
	private void updateScaleButtons() {
		boolean visible = !menu.outputsLocked();
		List<ItemStack> stacks = visible ? menu.encodingStacks() : List.of();
		boolean empty = stacks.stream().allMatch(ItemStack::isEmpty);
		for (int i = 0; i < scaleBtns.length; i++) {
			Button b = scaleBtns[i];
			if (b == null) continue;
			b.visible = visible;
			if (!visible) continue;
			int[] f = PatternTerminalMenu.SCALES[i];
			boolean ok = PatternTerminalMenu.canScale(stacks, f[0], f[1]);
			b.active = ok;
			String reason = ok ? (f[1] == 1 ? "tooltip.tsautocraft.scale.mul" : "tooltip.tsautocraft.scale.div")
					: empty ? "tooltip.tsautocraft.scale.empty"
					: f[1] == 1 ? "tooltip.tsautocraft.scale.too_big" : "tooltip.tsautocraft.scale.not_divisible";
			if (!reason.equals(scaleReasons[i])) {
				scaleReasons[i] = reason;
				Object arg = reason.endsWith("too_big") ? PatternTerminalMenu.MAX_PROCESSING_COUNT : Math.max(f[0], f[1]);
				b.setTooltip(Tooltip.create(Component.translatable(reason, arg)));
			}
		}
	}

	@Override
	public void getExclusionAreas(Consumer<Box> consumer) {
		super.getExclusionAreas(consumer);
		if (!menu.outputsLocked()) consumer.accept(new Box(leftPos + SCALE_X, topPos + areaY() + 17, SCALE_W, scaleBtns.length * (SCALE_H + 1)));
	}

	private Component substLabel() {
		// mark as well as color, so the state does not depend on telling green from grey (both glyphs are in the default bitmap font)
		return Component.translatable("button.tsautocraft.substitutes_short").append(menu.substitutes ? "✔" : "✘")
				.withStyle(menu.substitutes ? ChatFormatting.GREEN : ChatFormatting.GRAY);
	}

	private Tooltip substTooltip() {
		return Tooltip.create(Component.translatable(menu.substitutes ? "tooltip.tsautocraft.substitutes_on" : "tooltip.tsautocraft.substitutes_off"));
	}

	/** The mode button shows the mode's icon item instead of text. */
	private Component modeLabel() {
		return Component.empty();
	}

	private void click(int id) {
        //noinspection DataFlowIssue
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
	}

	@Override
	public void receive(ValueInput in) {
		var msg = in.getString("msg");
		if (msg.isPresent()) {
			message = Component.translatable(msg.get());
			messageTicks = 80;
			return;
		}
		super.receive(in);
	}

	private Tooltip modeTooltip() {
		return Tooltip.create(Component.empty().append(Component.translatable("label.tsautocraft.mode", menu.modeType().name())).append("\n").append(Component.translatable("tooltip.tsautocraft.mode")));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (modeBtn != null && shownMode != menu.mode) {
			shownMode = menu.mode;
			modeBtn.setMessage(modeLabel());
			modeBtn.setTooltip(modeTooltip());
		}
		if (substBtn != null && shownSubst != menu.substitutes) {
			shownSubst = menu.substitutes;
			substBtn.setMessage(substLabel());
			substBtn.setTooltip(substTooltip());
		}
		updateScaleButtons();
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (modeBtn != null) graphics.item(menu.modeType().icon(), modeBtn.getX() + 1, modeBtn.getY() + 1);
		if (clearBtn != null) graphics.item(CLEAR_ICON, clearBtn.getX() + 1, clearBtn.getY());
		if (loadBtn != null) {
			if (loadIcon == null) loadIcon = new ItemStack(eu.sytxlabs.tsautocraft.ModContent.PATTERN);
			graphics.item(loadIcon, loadBtn.getX() + 1, loadBtn.getY());
		}
		extractGridLayout(graphics, mouseX, mouseY);
	}

	/**
	 * Hides grid positions the mode does not use. Brewing: the grid looks like the brewing stand (fuel top left, reagent
	 * top middle, bottles below, pipes in between) and empty positions explain themselves on hover.
	 */
	private void extractGridLayout(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean brewing = PatternData.BREWING.equals(menu.modeType().id());
		for (int i = 0; i < 9; i++) {
			if (menu.gridEnabled(PatternTerminalMenu.GRID + i)) continue;
			Slot sl = menu.slots.get(PatternTerminalMenu.GRID + i);
			g.fill(leftPos + sl.x - 1, topPos + sl.y - 1, leftPos + sl.x + 17, topPos + sl.y + 17, 0xFFC6C6C6);
		}
		if (!brewing) return;
		Slot reagent = menu.slots.get(PatternTerminalMenu.GRID + PatternEncoder.BREW_REAGENT);
		Slot left = menu.slots.get(PatternTerminalMenu.GRID + PatternEncoder.BREW_FIRST_BOTTLE);
		Slot right = menu.slots.get(PatternTerminalMenu.GRID + 8);
		int cx = leftPos + reagent.x + 7, top = topPos + reagent.y + 17, bottom = topPos + left.y - 1, mid = (top + bottom) / 2;
		int lx = leftPos + left.x + 7, rx = leftPos + right.x + 7;
		// Pipes: reagent down, split to the three bottles
		pipe(g, cx, top, cx + 2, mid + 2);
		pipe(g, lx, mid, rx + 2, mid + 2);
		pipe(g, lx, mid, lx + 2, bottom);
		pipe(g, cx, mid, cx + 2, bottom);
		pipe(g, rx, mid, rx + 2, bottom);
		// Brewing progress arrow right of the reagent
		Slot arrow = menu.slots.get(PatternTerminalMenu.GRID + 2);
		int ax = leftPos + arrow.x + 7, ay = topPos + arrow.y;
		g.fill(ax, ay + 1, ax + 2, ay + 13, 0xFF555555);
		for (int k = 0; k < 4; k++) g.fill(ax - 3 + k, ay + 10 + k, ax + 5 - k, ay + 11 + k, 0xFF555555);
		// Hover hint for empty positions
		if (hoveredSlot != null && !hoveredSlot.hasItem()) {
			int idx = menu.slots.indexOf(hoveredSlot) - PatternTerminalMenu.GRID;
			String key = idx == PatternEncoder.BREW_FUEL ? "tooltip.tsautocraft.brewing.fuel" : idx == PatternEncoder.BREW_REAGENT ? "tooltip.tsautocraft.brewing.reagent"
					: idx >= PatternEncoder.BREW_FIRST_BOTTLE && idx < 9 ? "tooltip.tsautocraft.brewing.bottle" : null;
			if (key != null) g.setTooltipForNextFrame(font, Component.translatable(key), mouseX, mouseY);
		}
	}

	private static void pipe(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
		g.fill(x1, y1, x2, y2, 0xFF555555);
		g.fill(x1 - 1, y1, x1, y2, 0xFFFFFFFF);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		if (messageTicks > 0) {
			graphics.text(font, message, 60, inventoryLabelY, 0xFF8B0000, false);
		}
	}
}
