package eu.sytxlabs.tsautocraft.client;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import com.tom.storagemod.screen.TSContainerScreen;
import com.tom.storagemod.util.IDataReceiver;

import eu.sytxlabs.tsautocraft.TsAutocraft;
import eu.sytxlabs.tsautocraft.blockentity.AutocraftingTerminalBlockEntity;
import eu.sytxlabs.tsautocraft.job.CraftingJob.JobView;
import eu.sytxlabs.tsautocraft.job.CraftingJob;
import eu.sytxlabs.tsautocraft.job.JobHistoryEntry;
import eu.sytxlabs.tsautocraft.job.JobText;
import eu.sytxlabs.tsautocraft.menu.AutocraftingTerminalMenu.Craftable;
import eu.sytxlabs.tsautocraft.menu.AutocraftingTerminalMenu;
import eu.sytxlabs.tsautocraft.menu.MenuUtil;

/**
 * Top: craftable items (9x3, mouse wheel scrolls). Click selects, double click requests.
 * Middle: amount + "Request", below that the message/missing items.
 * Bottom: tabs "running" / "history"; running jobs with priority arrow (click cycles), pause/resume and the cross to cancel,
 * or the finished jobs of the network (newest first, cross on the tab bar clears them). Both crosses need a second click within 3 s.
 * The tooltip of a job lists the ETA and every step with why it is stuck.
 */
public class AutocraftingTerminalScreen extends TSContainerScreen<AutocraftingTerminalMenu> implements IDataReceiver {
	private static final Identifier GUI = TsAutocraft.id("textures/gui/autocrafting_terminal.png");
	private static final int COLS = 9, ROWS = 3, GRID_X = 8, GRID_Y = 18;
	private static final int JOBS_Y = 128, JOB_H = 11, JOB_ROWS = 3;
	private static final int PRIO_X = 138, PAUSE_X = 149, CANCEL_X = 161, LINE_W = 128;
	private static final int TABS_Y = 113, TAB_H = 9, TAB_X = 9, TAB_GAP = 8;
	private static final int GLYPH_H = 9;
	private static final long CONFIRM_MILLIS = 3000;
	private static final String ARM_CLEAR = "hclear";

	private EditBox amountBox;
	private int scroll, jobScroll;
	private boolean showHistory;
	private ItemStack selected = ItemStack.EMPTY;
	/** Destructive buttons need a second click: target of the first click ({@link #ARM_CLEAR} or "cancel" + job id) and until when it counts. */
	private String armed;
	private long armedUntil;

	public AutocraftingTerminalScreen(AutocraftingTerminalMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 176, 256);
		this.inventoryLabelY = AutocraftingTerminalMenu.PLAYER_Y - 11;
		menu.onUpdate = this::onUpdate;
	}

	@Override
	protected void init() {
		super.init();
		amountBox = new EditBox(font, leftPos + 8, topPos + 95, 40, 14, Component.translatable("label.tsautocraft.amount"));
		amountBox.setMaxLength(6);
		amountBox.setValue("1");
		amountBox.setResponder(s -> digitsOnly(amountBox, s));
		addRenderableWidget(amountBox);
		addRenderableWidget(Button.builder(Component.literal("+16"), _ -> addAmount(16)).bounds(leftPos + 50, topPos + 94, 24, 16).build());
		addRenderableWidget(Button.builder(Component.literal("+64"), _ -> addAmount(64)).bounds(leftPos + 75, topPos + 94, 24, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.request"), b -> request()).bounds(leftPos + 102, topPos + 93, 68, 18).build());
	}

	private void addAmount(int n) {
		long v = parseAmount() + n;
		if (parseAmount() == 1 && n > 1) v = n;
		amountBox.setValue(Long.toString(Math.min(v, 100_000)));
	}

	private long parseAmount() {
		try {
			return Math.max(1, Long.parseLong(amountBox.getValue()));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	private void request() {
		if (selected.isEmpty()) return;
		minecraft.gui.setScreen(new CraftRequestScreen(this, selected, parseAmount()));
	}

	/** Tooltips and row texts per synced job/history entry (new instances with every update), built once instead of every frame. */
	private final java.util.Map<Object, List<Component>> tooltipCache = new java.util.IdentityHashMap<>();
	private final java.util.Map<Object, String> rowCache = new java.util.IdentityHashMap<>();
	private static final java.time.format.DateTimeFormatter WHEN = java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT);

	private void onUpdate() {
		tooltipCache.clear();
		rowCache.clear();
		if (!selected.isEmpty() && menu.craftables.stream().noneMatch(c -> ItemStack.isSameItemSameComponents(c.item(), selected))) {
			selected = ItemStack.EMPTY;
		}
		scroll = Mth.clamp(scroll, 0, maxScroll());
		jobScroll = Mth.clamp(jobScroll, 0, maxJobScroll());
	}

	private int maxJobScroll() {
		return Math.max(0, listSize() - JOB_ROWS);
	}

	/** Entries of the shown tab (running jobs or history). */
	private int listSize() {
		return showHistory ? menu.history.size() : menu.jobs.size();
	}

	private int maxScroll() {
		int rows = (menu.craftables.size() + COLS - 1) / COLS;
		return Math.max(0, rows - ROWS);
	}

	@Override
	public void receive(ValueInput in) {
		menu.receive(in);
	}

	//region Input

	private boolean isArmed(String target) {
		return target.equals(armed) && System.currentTimeMillis() < armedUntil;
	}

	/** First click arms {@code target} and returns false, the second click on it within {@link #CONFIRM_MILLIS} returns true. */
	private boolean confirm(String target, boolean wasArmed) {
		if (wasArmed) return true;
		armed = target;
		armedUntil = System.currentTimeMillis() + CONFIRM_MILLIS;
		return false;
	}

	private static String cancelTarget(int jobId) {
		return "cancel" + jobId;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x() - leftPos, my = event.y() - topPos;
		String wasArmed = System.currentTimeMillis() < armedUntil ? armed : null;
		armed = null; // any click disarms; a click on the armed target confirms below
		int idx = gridIndexAt(mx, my);
		if (idx >= 0 && idx < menu.craftables.size()) {
			selected = menu.craftables.get(idx).item();
			if (doubleClick) request();
			return true;
		}
		if (my >= TABS_Y && my < TABS_Y + TAB_H) {
			if (showHistory && !menu.history.isEmpty() && mx >= CANCEL_X && mx < 170) {
				if (confirm(ARM_CLEAR, ARM_CLEAR.equals(wasArmed)) && minecraft.player != null) MenuUtil.sendToServer(minecraft.player, out -> out.putBoolean("hclear", true));
				return true;
			}
			int tab = tabAt(mx);
			if (tab >= 0) {
				if (showHistory != (tab == 1)) jobScroll = 0;
				showHistory = tab == 1;
				return true;
			}
		}
		JobHistoryEntry again = againAt(mx, my);
		if (again != null) {
			minecraft.gui.setScreen(new CraftRequestScreen(this, again.target(), again.amount()));
			return true;
		}
		if (!showHistory && mx >= PRIO_X && mx < 170 && my >= JOBS_Y && my < JOBS_Y + JOB_ROWS * JOB_H) {
			int row = (int) ((my - JOBS_Y) / JOB_H);
			List<JobView> visible = visibleJobs();
			if (row < visible.size() && visible.get(row).state() <= CraftingJob.State.WAITING.ordinal()) {
				if (minecraft.player == null) return false;
				int id = visible.get(row).id();
				String key = mx >= CANCEL_X ? "cancel" : mx >= PAUSE_X ? "pause" : "prio";
				if (key.equals("cancel") && !confirm(cancelTarget(id), cancelTarget(id).equals(wasArmed))) return true;
				MenuUtil.sendToServer(minecraft.player, out -> out.putInt(key, id));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		double mx = x - leftPos, my = y - topPos;
		if (mx >= GRID_X && mx < GRID_X + COLS * 18 && my >= GRID_Y && my < GRID_Y + ROWS * 18) {
			scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, maxScroll());
			return true;
		}
		if (mx >= GRID_X && mx < 170 && my >= JOBS_Y && my < JOBS_Y + JOB_ROWS * JOB_H) {
			jobScroll = Mth.clamp(jobScroll - (int) Math.signum(dy), 0, maxJobScroll());
			return true;
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) return super.keyPressed(event);
		if (amountBox != null && amountBox.isFocused()) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				request();
				return true;
			}
			if (amountBox.keyPressed(event) || amountBox.canConsumeInput()) return true;
		}
		return super.keyPressed(event);
	}

	private int gridIndexAt(double mx, double my) {
		if (mx < GRID_X || my < GRID_Y) return -1;
		int col = (int) ((mx - GRID_X) / 18), row = (int) ((my - GRID_Y) / 18);
		if (col >= COLS || row >= ROWS) return -1;
		return (row + scroll) * COLS + col;
	}

	private List<JobView> visibleJobs() {
		if (showHistory) return List.of();
		int from = Mth.clamp(jobScroll, 0, maxJobScroll());
		return menu.jobs.subList(from, Math.min(from + JOB_ROWS, menu.jobs.size()));
	}

	private List<JobHistoryEntry> visibleHistory() {
		if (!showHistory) return List.of();
		int from = Mth.clamp(jobScroll, 0, maxJobScroll());
		return menu.history.subList(from, Math.min(from + JOB_ROWS, menu.history.size()));
	}

	private Component tabLabel(int tab) {
		return tab == 0 ? Component.translatable("label.tsautocraft.tab.running", menu.jobs.size()) : Component.translatable("label.tsautocraft.tab.history", menu.history.size());
	}

	/** Tab under the mouse (0 running, 1 history), -1 none. */
	private int tabAt(double mx) {
		int x = TAB_X;
		for (int t = 0; t < 2; t++) {
			int w = font.width(tabLabel(t));
			if (mx >= x - 2 && mx < x + w + 2) return t;
			x += w + TAB_GAP;
		}
		return -1;
	}
	//endregion

	//region Rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor gr, int mouseX, int mouseY, float a) {
		super.extractBackground(gr, mouseX, mouseY, a);
		gr.blit(RenderPipelines.GUI_TEXTURED, GUI, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
		GuiUtil.upgradePanel(gr, leftPos, topPos, imageWidth, AutocraftingTerminalBlockEntity.UPGRADE_SLOTS);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int left, int top) {
		return super.hasClickedOutside(mx, my, left, top) && !GuiUtil.inUpgradePanel(mx, my, left, top, imageWidth, AutocraftingTerminalBlockEntity.UPGRADE_SLOTS);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int idx = gridIndexAt(mouseX - leftPos, mouseY - topPos);
		if (idx >= 0 && idx < menu.craftables.size()) {
			graphics.setTooltipForNextFrame(font, menu.craftables.get(idx).item(), mouseX, mouseY);
		}
		JobView hovered = jobAt(mouseX - leftPos, mouseY - topPos);
		if (hovered != null) graphics.setComponentTooltipForNextFrame(font, tooltipCache.computeIfAbsent(hovered, _ -> jobTooltip(hovered)), mouseX, mouseY);
		JobHistoryEntry past = historyAt(mouseX - leftPos, mouseY - topPos);
		if (past != null) graphics.setComponentTooltipForNextFrame(font, tooltipCache.computeIfAbsent(past, _ -> historyTooltip(past)), mouseX, mouseY);
		Component tab = tabTooltip(mouseX - leftPos, mouseY - topPos);
		if (tab != null) graphics.setTooltipForNextFrame(font, tab, mouseX, mouseY);
		if (againAt(mouseX - leftPos, mouseY - topPos) != null) graphics.setTooltipForNextFrame(font, Component.translatable("tooltip.tsautocraft.history.again"), mouseX, mouseY);
		Component button = jobButtonTooltip(mouseX - leftPos, mouseY - topPos);
		if (button != null) graphics.setTooltipForNextFrame(font, button, mouseX, mouseY);
	}

	private Component jobButtonTooltip(double mx, double my) {
		if (mx < PRIO_X || mx >= 170 || my < JOBS_Y || my >= JOBS_Y + JOB_ROWS * JOB_H) return null;
		int row = (int) ((my - JOBS_Y) / JOB_H);
		List<JobView> visible = visibleJobs();
		if (row >= visible.size() || visible.get(row).state() > CraftingJob.State.WAITING.ordinal()) return null;
		JobView j = visible.get(row);
		if (mx >= CANCEL_X) return Component.translatable(isArmed(cancelTarget(j.id())) ? "tooltip.tsautocraft.job.cancel_confirm" : "tooltip.tsautocraft.job.cancel");
		if (mx >= PAUSE_X) return Component.translatable(j.paused() ? "tooltip.tsautocraft.job.resume" : "tooltip.tsautocraft.job.pause");
		return Component.translatable("tooltip.tsautocraft.job.priority", priorityName(j.priority()));
	}

	/** History entry whose "request again" button is under the mouse (player jobs only), or null. */
	private JobHistoryEntry againAt(double mx, double my) {
		if (!showHistory || mx < CANCEL_X || mx >= 170 || my < JOBS_Y || my >= JOBS_Y + JOB_ROWS * JOB_H) return null;
		int row = (int) ((my - JOBS_Y) / JOB_H);
		List<JobHistoryEntry> visible = visibleHistory();
		if (row >= visible.size()) return null;
		JobHistoryEntry h = visible.get(row);
		return h.automatic() || h.target().isEmpty() || h.amount() <= 0 ? null : h;
	}

	private Component tabTooltip(double mx, double my) {
		if (my < TABS_Y || my >= TABS_Y + TAB_H) return null;
		if (showHistory && !menu.history.isEmpty() && mx >= CANCEL_X && mx < 170) return Component.translatable(isArmed(ARM_CLEAR) ? "tooltip.tsautocraft.history.clear_confirm" : "tooltip.tsautocraft.history.clear");
		int tab = tabAt(mx);
		return tab < 0 ? null : Component.translatable(tab == 0 ? "tooltip.tsautocraft.tab.running" : "tooltip.tsautocraft.tab.history");
	}

	private JobHistoryEntry historyAt(double mx, double my) {
		if (mx < GRID_X || mx >= CANCEL_X || my < JOBS_Y || my >= JOBS_Y + JOB_ROWS * JOB_H) return null;
		int row = (int) ((my - JOBS_Y) / JOB_H);
		List<JobHistoryEntry> visible = visibleHistory();
		return row < visible.size() ? visible.get(row) : null;
	}

	/** Target, result, crafts, duration and when the job ended. */
	private static List<Component> historyTooltip(JobHistoryEntry h) {
		List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.translatable("label.tsautocraft.item_count", h.amount(), h.target().getHoverName()));
		lines.add(Component.translatable(h.cancelled() ? "status.tsautocraft.cancelled" : "status.tsautocraft.done").withStyle(h.cancelled() ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.DARK_GREEN));
		lines.add(Component.translatable("label.tsautocraft.history.crafts", h.done(), h.total()).withStyle(net.minecraft.ChatFormatting.GRAY));
		if (h.ticks() >= 0) lines.add(Component.translatable("label.tsautocraft.history.duration", JobText.duration(h.ticks() / 20)).withStyle(net.minecraft.ChatFormatting.GRAY));
		if (h.endMillis() > 0) {
			String when = WHEN.format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(h.endMillis()), java.time.ZoneId.systemDefault()));
			lines.add(Component.translatable("label.tsautocraft.history.ended", when).withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		if (h.automatic()) lines.add(Component.translatable("label.tsautocraft.history.automatic").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		return lines;
	}

	private JobView jobAt(double mx, double my) {
		if (mx < GRID_X || mx >= PRIO_X || my < JOBS_Y || my >= JOBS_Y + JOB_ROWS * JOB_H) return null;
		int row = (int) ((my - JOBS_Y) / JOB_H);
		List<JobView> visible = visibleJobs();
		return row < visible.size() ? visible.get(row) : null;
	}

	/** Job name, status, priority and the progress of every step with why it is stuck. */
	private static List<Component> jobTooltip(JobView j) {
		List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.translatable("label.tsautocraft.item_count", j.amount(), j.target().getHoverName()));
		lines.add(statusText(j).copy().withStyle(net.minecraft.ChatFormatting.GRAY));
		if (j.state() <= CraftingJob.State.WAITING.ordinal()) lines.add(JobText.eta(j).copy().withStyle(net.minecraft.ChatFormatting.GRAY));
		lines.add(Component.translatable("label.tsautocraft.priority", priorityName(j.priority())).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		for (CraftingJob.StepView st : j.steps()) {
			boolean done = st.done() >= st.total();
			lines.add(Component.translatable("label.tsautocraft.step_line", st.output().getHoverName(), st.done(), st.total(), percent(st.done(), st.total())).withStyle(done ? net.minecraft.ChatFormatting.DARK_GREEN : net.minecraft.ChatFormatting.GRAY));
			Component why = stepReason(st);
			if (!done && why != null) lines.add(Component.literal("  ").append(why).withStyle(net.minecraft.ChatFormatting.RED));
		}
		return lines;
	}

	/** "Missing: 3x Iron Ingot (1/4)" / "Provider busy (12, 64, -3)", null if the step is not stuck. */
	private static Component stepReason(CraftingJob.StepView st) {
		CraftingJob.Reason r = CraftingJob.Reason.values()[Mth.clamp(st.reason(), 0, CraftingJob.Reason.values().length - 1)];
		if (r == CraftingJob.Reason.NONE) return null;
		MutableComponent c;
		if (r == CraftingJob.Reason.WAITING_ITEMS && st.need() > 0) {
			c = Component.translatable("reason.tsautocraft.step_missing", st.reasonItem().getHoverName(), st.have(), st.need());
		} else {
			String key = "reason.tsautocraft." + r.name().toLowerCase(java.util.Locale.ROOT);
			c = st.reasonItem().isEmpty() ? Component.translatable(key) : Component.translatable(key, st.reasonItem().getHoverName());
		}
		if (st.providerName().isPresent() && st.provider().isPresent()) {
			net.minecraft.core.BlockPos p = st.provider().get();
			c.append(" ").append(Component.translatable("label.tsautocraft.at_provider_pos", st.providerName().get(), p.getX(), p.getY(), p.getZ()));
		} else if (st.providerName().isPresent()) c.append(" ").append(Component.translatable("label.tsautocraft.at_provider", st.providerName().get()));
		else st.provider().ifPresent(p -> c.append(" ").append(Component.translatable("label.tsautocraft.at_pos", p.getX(), p.getY(), p.getZ())));
		return c;
	}

	static Component priorityName(int prio) {
		return Component.translatable(prio > 0 ? "label.tsautocraft.priority.high" : prio < 0 ? "label.tsautocraft.priority.low" : "label.tsautocraft.priority.normal");
	}

	private static long percent(long done, long total) {
		return total <= 0 ? 100 : Math.clamp(done * 100 / total, 0, 100);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		super.extractLabels(g, xm, ym);
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int i = (r + scroll) * COLS + c;
				if (i >= menu.craftables.size()) continue;
				Craftable cr = menu.craftables.get(i);
				int x = GRID_X + c * 18 + 1, y = GRID_Y + r * 18 + 1;
				if (!selected.isEmpty() && ItemStack.isSameItemSameComponents(cr.item(), selected)) {
					g.fill(x - 1, y - 1, x + 17, y + 17, 0x8000AAFF);
				}
				g.item(cr.item(), x, y);
				if (cr.stock() > 0) {
					String s = shortNumber(cr.stock());
					g.text(font, s, x + 17 - font.width(s), y + 9, 0xFFFFFFFF, true);
				}
			}
		}
		if (maxScroll() > 0) {
			g.text(font, (scroll + 1) + "/" + (maxScroll() + 1), 150, 6, 0xFF404040, false);
		}

		if (!selected.isEmpty()) {
			g.item(selected, 8, 75);
			g.text(font, selected.getHoverName(), 28, 79, 0xFF404040, false);
		} else {
			g.text(font, Component.translatable("label.tsautocraft.select"), 8, 79, 0xFF404040, false);
		}

		int tx = TAB_X;
		for (int t = 0; t < 2; t++) {
			Component label = tabLabel(t);
			boolean active = showHistory == (t == 1);
			if (active) g.fill(tx - 2, TABS_Y, tx + font.width(label) + 2, TABS_Y + TAB_H, 0xFFDCDCDC);
			g.text(font, label, tx, TABS_Y + 1, active ? 0xFF202020 : 0xFF404040, false);
			tx += font.width(label) + TAB_GAP;
		}
		int mx = xm - leftPos, my = ym - topPos;
		if (showHistory && !menu.history.isEmpty()) cancelGlyph(g, TABS_Y, TABS_Y + 1, isArmed(ARM_CLEAR), mx, my);
		if (showHistory) extractHistory(g, mx, my);
		List<JobView> visible = visibleJobs();
		if (!showHistory && visible.isEmpty()) {
			g.text(font, Component.translatable("label.tsautocraft.no_jobs"), 8, JOBS_Y + 1, 0xFF404040, false);
		}
		for (int i = 0; i < visible.size(); i++) {
			JobView j = visible.get(i);
			int y = JOBS_Y + i * JOB_H + 1;
			int color = j.paused() ? 0xFF404040 : j.requester() ? 0xFF1A3A80 : 0xFF303030;
			// Paused rows say so in words, not only through the play/pause glyph
			g.text(font, rowCache.computeIfAbsent(j, _ -> font.plainSubstrByWidth((j.paused() ? Component.translatable("label.tsautocraft.job.paused_line", JobText.line(j)) : JobText.line(j)).getString(), LINE_W)), 8, y, color, false);
			if (j.state() <= CraftingJob.State.WAITING.ordinal()) {
				int cellY = JOBS_Y + i * JOB_H;
				hoverCell(g, PRIO_X, PAUSE_X, cellY, mx, my);
				hoverCell(g, PAUSE_X, CANCEL_X, cellY, mx, my);
				String prio = j.priority() > 0 ? "↑" : j.priority() < 0 ? "↓" : "·";
				g.text(font, prio, PRIO_X + 1, y, j.priority() > 0 ? 0xFF0F4A0F : j.priority() < 0 ? 0xFF4F3C00 : 0xFF404040, false);
				g.text(font, j.paused() ? "▶" : "Ⅱ", PAUSE_X + 1, y, 0xFF1A3A80, false);
				cancelGlyph(g, cellY, y, isArmed(cancelTarget(j.id())), mx, my);
			}
			int barY = JOBS_Y + i * JOB_H + JOB_H - 2, barW = LINE_W;
			g.fill(8, barY, 8 + barW, barY + 1, 0xFF8B8B8B);
			if (j.total() > 0) g.fill(8, barY, 8 + (int) (barW * Math.clamp(j.done(), 0, j.total()) / j.total()), barY + 1, j.requester() ? 0xFF3F6FCF : 0xFF2E9E2E);
		}
		if (listSize() > JOB_ROWS) {
			int from = Mth.clamp(jobScroll, 0, maxJobScroll());
			String range = (from + 1) + "-" + Math.min(from + JOB_ROWS, listSize()) + "/" + listSize();
			g.text(font, range, 170 - font.width(range), JOBS_Y + JOB_ROWS * JOB_H, 0xFF404040, false);
		}
	}

	/** History rows: "64x Torch: Done (1m 20s)", green done / blue automatic / red cancelled. */
	private void extractHistory(GuiGraphicsExtractor g, int mx, int my) {
		List<JobHistoryEntry> visible = visibleHistory();
		if (visible.isEmpty()) g.text(font, Component.translatable("label.tsautocraft.history.empty"), 8, JOBS_Y + 1, 0xFF404040, false);
		for (int i = 0; i < visible.size(); i++) {
			JobHistoryEntry h = visible.get(i);
			int y = JOBS_Y + i * JOB_H + 1;
			String text = rowCache.computeIfAbsent(h, _ -> {
				Component result = Component.translatable(h.cancelled() ? "status.tsautocraft.cancelled" : "status.tsautocraft.done");
				Component c = h.ticks() >= 0 ? Component.translatable("label.tsautocraft.history.line_time", h.amount(), h.target().getHoverName(), result, JobText.duration(h.ticks() / 20)) : Component.translatable("label.tsautocraft.history.line", h.amount(), h.target().getHoverName(), result);
				return font.plainSubstrByWidth(c.getString(), CANCEL_X - 8 - 2);
			});
			g.text(font, text, 8, y, h.cancelled() ? 0xFF8B0000 : h.automatic() ? 0xFF1A3A80 : 0xFF0F4A0F, false);
			if (!h.automatic() && !h.target().isEmpty()) {
				hoverCell(g, CANCEL_X, 170, JOBS_Y + i * JOB_H, mx, my);
				g.text(font, "↻", CANCEL_X, y, 0xFF1A3A80, false);
			}
		}
	}

	/** Light background under the glyph button cell {@code x0..x1} (row top {@code y0}) while the mouse is over it. */
	private static void hoverCell(GuiGraphicsExtractor g, int x0, int x1, int y0, int mx, int my) {
		if (mx >= x0 && mx < x1 && my >= y0 && my < y0 + GLYPH_H) g.fill(x0, y0, x1, y0 + GLYPH_H, 0x40FFFFFF);
	}

	/** The cross of a destructive button: dark red, white on red while armed (waiting for the confirming click). */
	private void cancelGlyph(GuiGraphicsExtractor g, int cellY, int textY, boolean armedNow, int mx, int my) {
		if (armedNow) g.fill(CANCEL_X - 1, cellY, 170, cellY + GLYPH_H, 0xFF7F0000);
		else hoverCell(g, CANCEL_X, 170, cellY, mx, my);
		g.text(font, "✕", CANCEL_X, textY, armedNow ? 0xFFFFFFFF : 0xFF7F0000, false);
	}

	static Component statusText(JobView j) {
		return JobText.status(j);
	}

	/** Strips everything but digits; re-entrant call from setValue is a no-op because the text is then clean. */
	static void digitsOnly(EditBox box, String s) {
		String digits = s.replaceAll("[^0-9]", "");
		if (!digits.equals(s)) box.setValue(digits);
	}

	static String shortNumber(long n) {
		if (n < 1000) return Long.toString(n);
		if (n < 1_000_000) return Component.translatable("label.tsautocraft.number_thousands", n / 1000).getString();
		return Component.translatable("label.tsautocraft.number_millions", n / 1_000_000).getString();
	}

	@Override
	public void getExclusionAreas(Consumer<Box> consumer) {
		consumer.accept(GuiUtil.upgradeBox(leftPos, topPos, imageWidth, AutocraftingTerminalBlockEntity.UPGRADE_SLOTS));
	}
	//endregion
}
