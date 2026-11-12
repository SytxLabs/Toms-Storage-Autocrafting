package eu.sytxlabs.tsautocraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import com.tom.storagemod.screen.TSContainerScreen;
import com.tom.storagemod.util.IDataReceiver;

import eu.sytxlabs.tsautocraft.menu.MenuUtil;
import eu.sytxlabs.tsautocraft.menu.PatternAccessTerminalMenu.Entry;
import eu.sytxlabs.tsautocraft.menu.PatternAccessTerminalMenu.Line;
import eu.sytxlabs.tsautocraft.menu.PatternAccessTerminalMenu;
import eu.sytxlabs.tsautocraft.pattern.PatternData;
import eu.sytxlabs.tsautocraft.pattern.PatternItem;

/**
 * Pattern Access Terminal: {@value PatternAccessTerminalMenu#LINES} lines (provider header or a row of its pattern
 * slots), scrollbar on the right (mouse wheel or drag). Search box below the list filters by provider name or pattern
 * output; the button next to it hides full providers. Pattern slots show the pattern's output, Shift shows the pattern.
 * Filtering happens here; the server gets the matching provider positions and the first line.
 */
public class PatternAccessTerminalScreen extends TSContainerScreen<PatternAccessTerminalMenu> implements IDataReceiver {
	private static final int LINES = PatternAccessTerminalMenu.LINES, COLS = PatternAccessTerminalMenu.COLS;
	private static final int GRID_X = PatternAccessTerminalMenu.GRID_X, GRID_Y = PatternAccessTerminalMenu.GRID_Y;
	private static final int BAR_X = 174, BAR_W = 12, BAR_H = LINES * 18, THUMB_H = 15;
	private static final int SEARCH_Y = 130;
	/** Remembered while the game runs. */
	private static boolean hideFull;

	private EditBox search;
	private Button hideBtn;
	private int scroll;
	private boolean dragging;
	private String lastRequest = "";

	public PatternAccessTerminalScreen(PatternAccessTerminalMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, PatternAccessTerminalMenu.WIDTH, PatternAccessTerminalMenu.HEIGHT);
		this.inventoryLabelY = PatternAccessTerminalMenu.PLAYER_Y - 11;
		menu.onUpdate = this::sendRequest;
	}

	@Override
	protected void init() {
		super.init();
		hideShown = null;
		search = new EditBox(font, leftPos + 72, topPos + SEARCH_Y, 96, 12, Component.translatable("label.tsautocraft.search"));
		search.setMaxLength(50);
		search.setHint(Component.translatable("label.tsautocraft.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setResponder(_ -> {
			scroll = 0;
			sendRequest();
		});
		addRenderableWidget(search);
		hideBtn = addRenderableWidget(Button.builder(Component.empty(), _ -> {
			hideFull = !hideFull;
			scroll = 0;
			sendRequest();
		}).bounds(leftPos + 170, topPos + SEARCH_Y - 1, 16, 14).build());
		lastRequest = "";
		sendRequest();
	}

	//region Filter and request

	private List<Entry> shownCache;
	private String shownQuery;
	private boolean shownHide;
	private int shownRevision = -1;

	/** Filtered entries, rebuilt only when the search, "hide full" or the directory changed (it is used every frame). */
	private List<Entry> shown() {
		String q = search != null ? search.getValue().trim().toLowerCase(Locale.ROOT) : "";
		if (shownCache != null && q.equals(shownQuery) && hideFull == shownHide && menu.revision == shownRevision) return shownCache;
		shownQuery = q;
		shownHide = hideFull;
		shownRevision = menu.revision;
		List<Entry> out = new ArrayList<>();
		for (Entry e : menu.entries) {
			if (hideFull && e.full()) continue;
			if (!q.isEmpty() && !matches(e, q)) continue;
			out.add(e);
		}
		return shownCache = out;
	}

	private static boolean matches(Entry e, String q) {
		if (e.name().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
		for (ItemStack o : e.outputs()) if (o.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	private int maxScroll(List<Entry> shown) {
		int total = 0;
		for (Entry e : shown) total += 1 + e.rows();
		return Math.max(0, total - LINES);
	}

	/** Sends filter + scroll to the server if they changed (also after every directory update, the filter may match other providers now). */
	private void sendRequest() {
		if (minecraft.player == null) return;
		List<Entry> shown = shown();
		scroll = Mth.clamp(scroll, 0, maxScroll(shown));
		boolean all = !hideFull && (search == null || search.getValue().isBlank());
		StringBuilder key = new StringBuilder().append(scroll).append(all);
		if (!all) for (Entry e : shown) key.append(',').append(e.pos());
		if (key.toString().equals(lastRequest)) return;
		lastRequest = key.toString();
		int s = scroll;
		MenuUtil.sendToServer(minecraft.player, out -> PatternAccessTerminalMenu.writeRequest(out, shown, all, s));
	}

	private void setScroll(int s) {
		scroll = s;
		sendRequest();
	}

	@Override
	public void receive(ValueInput in) {
		menu.receive(in);
	}
	//endregion

	//region Input

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		double mx = x - leftPos, my = y - topPos;
		if (mx >= GRID_X - 1 && mx < BAR_X + BAR_W && my >= GRID_Y - 1 && my < GRID_Y + BAR_H) {
			setScroll(scroll - (int) Math.signum(dy));
			return true;
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && onBar(event.x() - leftPos, event.y() - topPos)) {
			dragging = true;
			dragTo(event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			dragTo(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	private boolean onBar(double mx, double my) {
		return mx >= BAR_X && mx < BAR_X + BAR_W && my >= GRID_Y && my < GRID_Y + BAR_H;
	}

	private void dragTo(double y) {
		int max = maxScroll(shown());
		if (max <= 0) return;
		double f = (y - topPos - GRID_Y - THUMB_H / 2.0) / (BAR_H - THUMB_H);
		setScroll((int) Math.round(Mth.clamp(f, 0, 1) * max));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) return super.keyPressed(event);
		if (search != null && search.isFocused()) {
			if (search.keyPressed(event) || search.canConsumeInput()) return true;
		}
		return super.keyPressed(event);
	}
	//endregion

	//region Rendering

	/** State the hide button shows (its tooltip is only rebuilt on change). */
	private Boolean hideShown;

	private Entry entryAt(long pos) {
		for (Entry e : menu.entries) if (e.pos() == pos) return e;
		return null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (hideBtn != null && (hideShown == null || hideShown != hideFull)) {
			hideShown = hideFull;
			hideBtn.setMessage(Component.literal(hideFull ? "◐" : "●"));
			hideBtn.setTooltip(Tooltip.create(Component.translatable(hideFull ? "tooltip.tsautocraft.access.show_full" : "tooltip.tsautocraft.access.hide_full")));
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int line = (mouseY - topPos - GRID_Y) / 18;
		if (mouseX - leftPos >= GRID_X && mouseX - leftPos < GRID_X + COLS * 18 && mouseY - topPos >= GRID_Y && line < menu.lines.size() && menu.lines.get(line).header()) {
			Entry e = entryAt(menu.lines.get(line).pos());
			if (e != null) {
				BlockPos p = BlockPos.of(e.pos());
				graphics.setComponentTooltipForNextFrame(font, List.of(e.name(),
						Component.translatable("label.tsautocraft.at_pos", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GRAY),
						Component.translatable("jade.tsautocraft.patterns", e.used(), e.slots()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
			}
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gr, int mouseX, int mouseY, float a) {
		super.extractBackground(gr, mouseX, mouseY, a);
		GuiUtil.panel(gr, leftPos, topPos, imageWidth, imageHeight);
		for (int l = 0; l < menu.lines.size(); l++) {
			int y = topPos + GRID_Y - 1 + l * 18;
			if (menu.lines.get(l).header()) {
				gr.fill(leftPos + GRID_X - 1, y + 2, leftPos + GRID_X - 1 + COLS * 18, y + 16, 0xFFB4B4B4);
			} else {
				for (int c = 0; c < COLS; c++) GuiUtil.slot(gr, leftPos + GRID_X - 1 + c * 18, y);
			}
		}
		for (int i = 0; i < 36; i++) {
			int row = i / 9;
			GuiUtil.slot(gr, leftPos + 7 + i % 9 * 18, topPos + PatternAccessTerminalMenu.PLAYER_Y - 1 + (row < 3 ? row * 18 : 58));
		}
		// Scrollbar
		int bx = leftPos + BAR_X, by = topPos + GRID_Y;
		gr.fill(bx, by, bx + BAR_W, by + BAR_H, 0xFF8B8B8B);
		gr.fill(bx, by, bx + BAR_W - 1, by + 1, 0xFF373737);
		gr.fill(bx, by, bx + 1, by + BAR_H - 1, 0xFF373737);
		gr.fill(bx + 1, by + BAR_H - 1, bx + BAR_W, by + BAR_H, 0xFFFFFFFF);
		gr.fill(bx + BAR_W - 1, by + 1, bx + BAR_W, by + BAR_H, 0xFFFFFFFF);
		int max = maxScroll(shown());
		int ty = by + 1 + (max > 0 ? (BAR_H - 2 - THUMB_H) * Mth.clamp(scroll, 0, max) / max : 0);
		int thumb = max > 0 ? 0xFFC6C6C6 : 0xFFA0A0A0;
		gr.fill(bx + 1, ty, bx + BAR_W - 1, ty + THUMB_H, 0xFF555555);
		gr.fill(bx + 1, ty, bx + BAR_W - 2, ty + THUMB_H - 1, 0xFFFFFFFF);
		gr.fill(bx + 2, ty + 1, bx + BAR_W - 2, ty + THUMB_H - 1, thumb);
	}

	/** Pattern slots show the pattern's output (with count) instead of the pattern, unless Shift is held. */
	@Override
	protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
		if (slot.index < PatternAccessTerminalMenu.WINDOW && slot.hasItem() && !PatternItem.shiftDown.getAsBoolean()) {
			PatternData d = PatternItem.get(slot.getItem());
			ItemStack out = d != null ? d.primaryOutput() : ItemStack.EMPTY;
			if (!out.isEmpty()) {
				g.item(out, slot.x, slot.y);
				g.itemDecorations(font, out, slot.x, slot.y);
				return;
			}
		}
		super.extractSlot(g, slot, mouseX, mouseY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		super.extractLabels(g, xm, ym);
		for (int l = 0; l < menu.lines.size(); l++) {
			Line line = menu.lines.get(l);
			if (!line.header()) continue;
			Entry e = entryAt(line.pos());
			if (e == null) continue;
			int y = GRID_Y + l * 18 + 4;
			BlockPos p = BlockPos.of(e.pos());
			String pos = p.getX() + " " + p.getY() + " " + p.getZ();
			int posW = font.width(pos);
			int nameW = COLS * 18 - 6 - posW - 6;
			g.text(font, font.plainSubstrByWidth(e.name().getString(), nameW), GRID_X + 2, y, 0xFF303030, false);
			g.text(font, pos, GRID_X + COLS * 18 - 3 - posW, y, 0xFF404040, false);
		}
		if (menu.entries.isEmpty()) {
			Component c = Component.translatable("label.tsautocraft.no_providers");
			g.text(font, font.plainSubstrByWidth(c.getString(), COLS * 18), GRID_X, GRID_Y + 4, 0xFF404040, false);
		} else if (menu.lines.isEmpty()) {
			Component c = Component.translatable("label.tsautocraft.no_match");
			g.text(font, font.plainSubstrByWidth(c.getString(), COLS * 18), GRID_X, GRID_Y + 4, 0xFF404040, false);
		}
	}

	@Override
	public void getExclusionAreas(Consumer<Box> consumer) {
		// No panels outside the GUI
	}
	//endregion
}
