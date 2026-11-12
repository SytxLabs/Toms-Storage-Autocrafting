package eu.sytxlabs.tsautocraft.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import eu.sytxlabs.tsautocraft.ModContent;
import eu.sytxlabs.tsautocraft.TsAutocraft;

/**
 * Client-only guide book, drawn as an open book (turning moves one spread). Page text:
 * {@code assets/tsautocraft/guide/<lang>/<id>.txt} (line 1 = title, en_us fallback); optional diagram:
 * {@code guide/diagrams/<id>.txt}. A new page needs an entry in {@link #PAGES} and a file in every language folder.
 * <p>
 * Diagram file: one line per row, cells separated by spaces, {@code #} starts a comment line. A cell is an item id
 * ({@code mc:} = minecraft, {@code ts:} = Tom's Storage, {@code ac:} = this mod, or a full id), an arrow ({@code > < ^ v}),
 * a cable ({@code -} horizontal, {@code |} vertical, {@code +} junction or corner: joins every neighbouring cable or item)
 * or {@code .} (empty).
 */
public class GuideScreen extends Screen {
	/** Book: cover border, two pages of {@code PAGE_W} side by side. Text coordinates are relative to a page. */
	private static final int COVER = 8, PAGE_W = 150, W = 2 * COVER + 2 * PAGE_W, H = 204;
	private static final int TEXT_X = 10, TEXT_W = PAGE_W - 20, TITLE_Y = 12, ICON_Y = 22, TEXT_Y = 42, TEXT_BOTTOM = H - 30;
	private static final int TOC_ROW_H = 12;
	private static final int CELL = 20;
	private static final int TEXT_COLOR = 0xFF303030, TITLE_COLOR = 0xFF1F3F5F, LINK_COLOR = 0xFF1F4F8F, LINK_HOVER = 0xFF1E5A1E;

	private record Page(String id, List<Supplier<ItemLike>> icons) {}

	private static int columns(List<String[]> diagram) {
		int cols = 0;
		for (String[] r : diagram) cols = Math.max(cols, r.length);
		return cols;
	}

	/** Wide diagrams are scaled down to the page width. */
	private static float diagramScale(List<String[]> diagram) {
		return Math.min(1f, TEXT_W / (float) (columns(diagram) * CELL + 6));
	}

	private static int diagramHeight(List<String[]> diagram) {
		return diagram.isEmpty() ? 0 : (int) Math.ceil(diagram.size() * CELL * diagramScale(diagram)) + 8;
	}

	/** Title and text of one page in the selected language, plus its diagram. */
	private record Content(Component title, Component text, List<String[]> diagram) {}

	/** Reads {@code guide/<lang>/<id>.txt} (selected language, else en_us); first line = title, the rest = text. */
	private static Content load(String id) {
		Minecraft mc = Minecraft.getInstance();
		String lang = mc.getLanguageManager().getSelected();
		for (String l : lang.equals(DEFAULT_LANG) ? List.of(DEFAULT_LANG) : List.of(lang, DEFAULT_LANG)) {
			var res = mc.getResourceManager().getResource(TsAutocraft.id("guide/" + l + "/" + id + ".txt"));
			if (res.isEmpty()) continue;
			try (BufferedReader r = res.get().openAsReader()) {
				String title = r.readLine();
				if (title == null) continue;
				String text = r.lines().collect(Collectors.joining("\n")).strip();
				return new Content(Component.literal(title.replace("﻿", "").strip()), Component.literal(text), loadDiagram(id));
			} catch (IOException e) {
				TsAutocraft.LOGGER.warn("Could not read guide page {} ({})", id, l, e);
			}
		}
		return new Content(Component.literal(id), Component.empty(), loadDiagram(id));
	}

	/** Reads {@code guide/diagrams/<id>.txt}: rows of cells (see class doc); empty if there is none. */
	private static List<String[]> loadDiagram(String id) {
		var res = Minecraft.getInstance().getResourceManager().getResource(TsAutocraft.id("guide/diagrams/" + id + ".txt"));
		if (res.isEmpty()) return List.of();
		List<String[]> rows = new ArrayList<>();
		try (BufferedReader r = res.get().openAsReader()) {
			for (String line : (Iterable<String>) r.lines()::iterator) {
				String t = line.replace("﻿", "").strip();
				if (t.isEmpty() || t.startsWith("#")) continue;
				rows.add(t.split("\\s+"));
			}
		} catch (IOException e) {
			TsAutocraft.LOGGER.warn("Could not read guide diagram {}", id, e);
		}
		return rows;
	}

	@SafeVarargs
	private static Page page(String id, Supplier<ItemLike>... icons) {
		return new Page(id, List.of(icons));
	}

	@SuppressWarnings("unchecked")
	private static final List<Page> PAGES = List.of(
			page("overview", () -> ModContent.GUIDE_BOOK),
			page("setup", () -> ModContent.AUTOCRAFTING_TERMINAL, () -> ModContent.PATTERN_TERMINAL, () -> ModContent.PATTERN_PROVIDER, () -> Items.CRAFTER, () -> ModContent.IMPORTER),
			page("pattern_terminal", () -> ModContent.PATTERN_TERMINAL, () -> ModContent.BLANK_PATTERN, () -> ModContent.PATTERN),
			page("pattern_provider", () -> ModContent.PATTERN_PROVIDER, () -> ModContent.PATTERN_PROVIDER_CONNECTOR, () -> ModContent.PATTERN_PROVIDER_CONNECTOR_FRAMED),
			page("autocrafting_terminal", () -> ModContent.AUTOCRAFTING_TERMINAL),
			page("requesting", () -> ModContent.AUTOCRAFTING_TERMINAL),
			page("pattern_access_terminal", () -> ModContent.PATTERN_ACCESS_TERMINAL),
			page("requester", () -> ModContent.REQUESTER),
			page("level_maintainer", () -> ModContent.LEVEL_MAINTAINER),
			page("crafting_emitter", () -> ModContent.CRAFTING_EMITTER, () -> ModContent.CRAFTING_EMITTER_DIRECTIONAL, () -> ModContent.CRAFTING_EMITTER_DIRECTIONAL_FRAMED),
			page("exporter_importer", () -> ModContent.EXPORTER, () -> ModContent.IMPORTER),
			page("upgrades", () -> ModContent.CAPACITY_UPGRADE, () -> ModContent.SPEED_UPGRADE, () -> ModContent.STACK_UPGRADE, () -> ModContent.CRAFTING_UPGRADE, () -> ModContent.REDSTONE_UPGRADE),
			page("memory_card", () -> ModContent.MEMORY_CARD),
			page("mode_crafting", () -> Items.CRAFTER),
			page("mode_processing", () -> Items.CHEST),
			page("mode_smelting", () -> Items.FURNACE),
			page("mode_fluid", () -> Items.WATER_BUCKET),
			page("mode_brewing", () -> Items.BREWING_STAND),
			page("jei", () -> Items.KNOWLEDGE_BOOK),
			page("config", () -> Items.COMPARATOR));

	/** One screen of text: the page it belongs to and its lines; page -1 = table of contents. */
	private record Sheet(int page, List<FormattedCharSequence> lines, int part, int parts) {}

	private static final String DEFAULT_LANG = "en_us";

	private final List<Content> contents = new ArrayList<>();
	private final List<Sheet> sheets = new ArrayList<>();
	private final int[] firstSheet = new int[PAGES.size()];
	private int current;
	/** Highlighted table of contents entry (page index, -1 none), set by mouse hover and Up/Down alike. */
	private int tocSelected = -1;
	private int left, top;
	private Button prevBtn, nextBtn, tocBtn;

	public GuideScreen() {
		super(Component.translatable("screen.tsautocraft.guide"));
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = Math.max(4, (height - H - 22) / 2);
		buildSheets();
		// Page arrows in the bottom corners of the pages, contents in the middle below the book
		prevBtn = addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.guide.prev"), _ -> turn(-1)).bounds(pageX(0) + 6, top + H - 24, 18, 16).build());
		nextBtn = addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.guide.next"), _ -> turn(1)).bounds(pageX(1) + PAGE_W - 24, top + H - 24, 18, 16).build());
		tocBtn = addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.guide.contents"), _ -> show(0)).bounds(left + W / 2 - 35, top + H + 4, 70, 18).build());
		show(current);
	}

	/** Splits every page into sheets that fit the text area (depends on the font, so done in init). */
	private void buildSheets() {
		sheets.clear();
		contents.clear();
		for (Page p : PAGES) contents.add(load(p.id()));
		int tocSheets = Math.max(1, (PAGES.size() + tocRows() - 1) / tocRows());
		for (int i = 0; i < tocSheets; i++) sheets.add(new Sheet(-1, List.of(), i, tocSheets));
		int perSheet = (TEXT_BOTTOM - TEXT_Y) / font.lineHeight;
		for (int p = 0; p < PAGES.size(); p++) {
			List<FormattedCharSequence> lines = font.split(contents.get(p).text(), TEXT_W);
			int first = Math.max(1, perSheet - (diagramHeight(contents.get(p).diagram()) + font.lineHeight - 1) / font.lineHeight); // the diagram takes room on the first sheet
			int parts = 1 + Math.max(0, (lines.size() - first + perSheet - 1) / perSheet);
			firstSheet[p] = sheets.size();
			for (int i = 0, from = 0; i < parts; i++) {
				int to = Math.min(lines.size(), from + (i == 0 ? first : perSheet));
				sheets.add(new Sheet(p, lines.subList(from, to), i, parts));
				from = to;
			}
		}
	}

	private static int tocRows() {
		return (TEXT_BOTTOM - TEXT_Y) / TOC_ROW_H;
	}

	private void turn(int spreads) {
		show(current + 2 * spreads);
	}

	/** Opens the spread holding {@code sheet} (spreads start with an even sheet). */
	private void show(int sheet) {
		current = Math.clamp(sheet, 0, sheets.size() - 1) & ~1;
		if (prevBtn != null) prevBtn.active = current > 0;
		if (nextBtn != null) nextBtn.active = current + 2 < sheets.size();
		if (tocBtn != null) tocBtn.active = current > 0;
	}

	/** Left edge of the left (side 0) or right (side 1) page. */
	private int pageX(int side) {
		return left + COVER + side * PAGE_W;
	}

	/** Index of the page whose table of contents entry is under the mouse, -1 if none. */
	private int tocEntryAt(double mx, double my) {
		for (int side = 0; side < 2; side++) {
			int si = current + side;
			if (si >= sheets.size() || sheets.get(si).page() >= 0) continue;
			int x = (int) mx - pageX(side) - TEXT_X, y = (int) my - top - TEXT_Y;
			if (x < 0 || y < 0 || x >= TEXT_W || y >= tocRows() * TOC_ROW_H) continue;
			int idx = sheets.get(si).part() * tocRows() + y / TOC_ROW_H;
			return idx < PAGES.size() ? idx : -1;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int idx = tocEntryAt(event.x(), event.y());
		if (idx >= 0) {
			show(firstSheet[idx]);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void mouseMoved(double x, double y) {
		int idx = tocEntryAt(x, y);
		if (idx >= 0 && idx != tocSelected) {
			tocSelected = idx;
			setFocused(null); // Enter then opens the entry instead of pressing a previously clicked button
		}
		super.mouseMoved(x, y);
	}

	/** Is a table of contents sheet shown in the current spread? */
	private boolean tocShown() {
		return sheets.get(current).page() < 0;
	}

	/** Moves the table of contents highlight by {@code delta} rows, turning to the spread that shows it. */
	private void moveTocSelection(int delta) {
		int sel = tocSelected < 0 ? sheets.get(current).part() * tocRows() : Math.clamp(tocSelected + delta, 0, PAGES.size() - 1);
		tocSelected = sel;
		setFocused(null);
		show(sel / tocRows()); // table of contents sheet i = part i
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (dy != 0) {
			turn(dy > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_PAGEUP) {
			turn(-1);
			return true;
		}
		if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_PAGEDOWN) {
			turn(1);
			return true;
		}
		if (key == InputConstants.KEY_HOME) {
			show(0);
			return true;
		}
		if (tocShown()) {
			if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
				moveTocSelection(key == InputConstants.KEY_UP ? -1 : 1);
				return true;
			}
			// A focused button (Tab navigation) keeps Enter for itself
			if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && tocSelected >= 0 && getFocused() == null) {
				show(firstSheet[tocSelected]);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		// Leather cover around the pages
		g.fill(left, top, left + W, top + H, 0xFF2A1A0E);
		g.fill(left + 1, top + 1, left + W - 1, top + H - 1, 0xFF6B3F1F);
		g.fill(left + 3, top + 3, left + W - 3, top + H - 3, 0xFF5A3418);
		// Pages: cream paper with a shadow towards the spine
		int y0 = top + 4, y1 = top + H - 4;
		for (int side = 0; side < 2; side++) {
			int px = pageX(side);
			g.fill(px, y0, px + PAGE_W, y1, 0xFFF3ECD8);
			for (int i = 0; i < 6; i++) {
				int shade = (0x18 - i * 4) << 24;
				int x = side == 0 ? px + PAGE_W - 1 - i : px + i;
				g.fill(x, y0, x + 1, y1, shade);
			}
		}
		int spine = left + COVER + PAGE_W;
		g.fill(spine - 1, top + 2, spine + 1, top + H - 2, 0xFF3A2410);
		// Edges of the page stack on the outer sides
		g.fill(pageX(0) - 2, y0 + 1, pageX(0), y1 + 1, 0xFFD8CFB4);
		g.fill(pageX(1) + PAGE_W, y0 + 1, pageX(1) + PAGE_W + 2, y1 + 1, 0xFFD8CFB4);

		ItemStack hovered = ItemStack.EMPTY;
		for (int side = 0; side < 2; side++) {
			int si = current + side;
			if (si >= sheets.size()) continue;
			ItemStack h = drawSheet(g, sheets.get(si), pageX(side), mouseX, mouseY);
			if (!h.isEmpty()) hovered = h;
			Component pageNo = Component.translatable("guide.tsautocraft.page", si + 1, sheets.size());
			g.text(font, pageNo, pageX(side) + (PAGE_W - font.width(pageNo)) / 2, top + H - 20, 0xFF6A5A40, false);
		}
		if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
	}

	/** One page of the spread at {@code px}; returns the item under the mouse (or empty). */
	private ItemStack drawSheet(GuiGraphicsExtractor g, Sheet s, int px, int mouseX, int mouseY) {
		ItemStack hovered = ItemStack.EMPTY;
		if (s.page() < 0) {
			g.text(font, font.plainSubstrByWidth(Component.translatable("guide.tsautocraft.contents").getString(), TEXT_W), px + TEXT_X, top + TITLE_Y, TITLE_COLOR, false);
			g.fill(px + TEXT_X, top + TITLE_Y + 10, px + TEXT_X + TEXT_W, top + TITLE_Y + 11, 0xFFC9BB98);
			for (int i = 0; i < tocRows(); i++) {
				int idx = s.part() * tocRows() + i;
				if (idx >= PAGES.size()) break;
				String label = font.plainSubstrByWidth(Component.translatable("guide.tsautocraft.toc_entry", idx + 1, contents.get(idx).title()).getString(), TEXT_W);
				int ty = top + TEXT_Y + i * TOC_ROW_H + 2;
				boolean sel = idx == tocSelected;
				g.text(font, label, px + TEXT_X, ty, sel ? LINK_HOVER : LINK_COLOR, false);
				// Underline: the highlight is not only a change of hue
				if (sel) g.fill(px + TEXT_X, ty + font.lineHeight, px + TEXT_X + font.width(label), ty + font.lineHeight + 1, LINK_HOVER);
			}
			return hovered;
		}
		Page p = PAGES.get(s.page());
		Component pageTitle = contents.get(s.page()).title();
		Component title = s.parts() > 1 ? Component.translatable("guide.tsautocraft.part", pageTitle, s.part() + 1, s.parts()) : pageTitle;
		g.text(font, font.plainSubstrByWidth(title.getString(), TEXT_W), px + TEXT_X, top + TITLE_Y, TITLE_COLOR, false);
		g.fill(px + TEXT_X, top + TITLE_Y + 10, px + TEXT_X + TEXT_W, top + TITLE_Y + 11, 0xFFC9BB98);
		for (int i = 0; i < p.icons().size(); i++) {
			ItemStack icon = new ItemStack(p.icons().get(i).get());
			int x = px + TEXT_X + i * 17, y = top + ICON_Y;
			g.item(icon, x, y);
			if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = icon;
		}
		int y = top + TEXT_Y;
		List<String[]> diagram = contents.get(s.page()).diagram();
		if (s.part() == 0 && !diagram.isEmpty()) {
			ItemStack h = drawDiagram(g, diagram, px, y, mouseX, mouseY);
			if (!h.isEmpty()) hovered = h;
			y += diagramHeight(diagram);
		}
		for (FormattedCharSequence line : s.lines()) {
			g.text(font, line, px + TEXT_X, y, TEXT_COLOR, false);
			y += font.lineHeight;
		}
		return hovered;
	}

	/** Draws the page's diagram centered on the page at {@code y}, scaled down to the page width; returns the item under the mouse (or empty). */
	private ItemStack drawDiagram(GuiGraphicsExtractor g, List<String[]> rows, int px, int y, int mouseX, int mouseY) {
		int cols = columns(rows);
		float scale = diagramScale(rows);
		float x0 = px + (PAGE_W - cols * CELL * scale) / 2f;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x0, y);
		pose.scale(scale, scale);
		g.fill(-3, -1, cols * CELL + 3, rows.size() * CELL + 3, 0xFFE4DAC0);
		ItemStack hovered = ItemStack.EMPTY;
		float mx = (mouseX - x0) / scale, my = (mouseY - y) / scale; // mouse in diagram coordinates
		for (int row = 0; row < rows.size(); row++) {
			String[] cells = rows.get(row);
			for (int col = 0; col < cells.length; col++) {
				int x = col * CELL, cy = 1 + row * CELL;
				String c = cells[col];
				switch (c) {
					case "." -> {}
					case "-" -> cable(g, x, cy, true, true, false, false);
					case "|" -> cable(g, x, cy, false, false, true, true);
					case "+" -> cable(g, x, cy, joins(rows, row, col - 1), joins(rows, row, col + 1), joins(rows, row - 1, col), joins(rows, row + 1, col));
					case ">", "<", "^", "v" -> {
						String arrow = switch (c) {
							case ">" -> "→";
							case "<" -> "←";
							case "^" -> "↑";
							default -> "↓";
						};
						g.text(font, arrow, x + (CELL - font.width(arrow)) / 2, cy + (CELL - font.lineHeight) / 2 + 1, 0xFF8F2F1F, false);
					}
					default -> {
						ItemStack st = diagramItem(c);
						if (st.isEmpty()) continue;
						g.fill(x + 1, cy + 1, x + CELL - 1, cy + CELL - 1, 0xFFB8AE94);
						g.item(st, x + 2, cy + 2);
						if (mx >= x && mx < x + CELL && my >= cy && my < cy + CELL) hovered = st;
					}
				}
			}
		}
		pose.popMatrix();
		return hovered;
	}

	private static final int CABLE = 0xFF6B6B6B, CABLE_EDGE = 0xFF4A4A4A;

	/** Cable piece in a cell: a knot in the middle and arms to the given sides. */
	private static void cable(GuiGraphicsExtractor g, int x, int y, boolean left, boolean right, boolean up, boolean down) {
		int c = CELL / 2, t = 2; // half thickness
		int x0 = left ? x : x + c - t, x1 = right ? x + CELL : x + c + t + 1;
		int y0 = up ? y : y + c - t, y1 = down ? y + CELL : y + c + t + 1;
		if (left || right) {
			g.fill(x0, y + c - t - 1, x1, y + c + t + 1, CABLE_EDGE);
			g.fill(x0, y + c - t, x1, y + c + t, CABLE);
		}
		if (up || down) {
			g.fill(x + c - t - 1, y0, x + c + t + 1, y1, CABLE_EDGE);
			g.fill(x + c - t, y0, x + c + t, y1, CABLE);
		}
		g.fill(x + c - t, y + c - t, x + c + t, y + c + t, CABLE);
	}

	/** Does the cell at {@code row}/{@code col} take a cable from a junction next to it (cable or item, not empty or arrow)? */
	private static boolean joins(List<String[]> rows, int row, int col) {
		if (row < 0 || row >= rows.size() || col < 0 || col >= rows.get(row).length) return false;
		return switch (rows.get(row)[col]) {
			case ".", ">", "<", "^", "v" -> false;
			default -> true;
		};
	}

	/** Parsed diagram cells (drawn every frame). Read only stacks. */
	private static final java.util.Map<String, ItemStack> DIAGRAM_ITEMS = new java.util.HashMap<>();

	private static ItemStack diagramItem(String cell) {
		return DIAGRAM_ITEMS.computeIfAbsent(cell, GuideScreen::parseDiagramItem);
	}

	private static ItemStack parseDiagramItem(String cell) {
		String id = cell.startsWith("mc:") ? "minecraft:" + cell.substring(3) : cell.startsWith("ts:") ? "toms_storage:" + cell.substring(3) : cell.startsWith("ac:") ? "tsautocraft:" + cell.substring(3) : cell;
		Identifier key = Identifier.tryParse(id);
		if (key == null) return ItemStack.EMPTY;
		Item item = BuiltInRegistries.ITEM.getValue(key);
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}
}
