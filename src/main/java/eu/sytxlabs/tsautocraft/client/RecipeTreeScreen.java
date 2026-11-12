package eu.sytxlabs.tsautocraft.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import com.tom.storagemod.util.IDataReceiver;

import eu.sytxlabs.tsautocraft.job.CraftingJob.TreeLine;

/**
 * Full screen recipe tree of a request preview: target on top, pattern inputs below, right-angled edges; frame and
 * badge show missing / crafted / stored. Starts collapsed from depth 2 except the paths to missing items. "Start"
 * returns to the request dialog and starts the job there.
 */
public class RecipeTreeScreen extends Screen implements IDataReceiver {
	private static final int NODE = 20, GAP_X = 6, LEVEL_H = 34, MARGIN = 6, TOP = 32, BOTTOM = 28, BADGE = 7;
	private static final float MIN_ZOOM = 0.5f, MAX_ZOOM = 4f;
	private static final int FIT_PAD = 12;
	// Frames are dark so they keep >= 3:1 against the #8B8B8B canvas; the badges carry the state without color.
	private static final int COLOR_MISSING = 0xFF800000, COLOR_CRAFTED = 0xFF16386F, COLOR_STORED = 0xFF3A3A3A;
	private static final int BADGE_MISSING = 0xFFB02020, BADGE_CRAFTED = 0xFF2050A0, SLOT = 0xFF6F6F6F;
	private static final int STORED = 0, CRAFTED = 1, MISSING = 2;

	private static final class Node {
		final TreeLine line;
		final List<Node> children = new ArrayList<>();
		boolean collapsed;
		int x, y, width;

		Node(TreeLine line) {
			this.line = line;
		}

		boolean more() {
			return line.item().isEmpty();
		}

		boolean missingBelow() {
			if (line.missing() > 0) return true;
			for (Node c : children) if (c.missingBelow()) return true;
			return false;
		}

		int descendants() {
			int n = 0;
			for (Node c : children) n += 1 + c.descendants();
			return n;
		}
	}

	private final CraftRequestScreen parent;
	private final ItemStack target;
	private final long amount;
	private final boolean planOk;
	private final Node root;
	private float camX, camY, zoom = 1;
	private boolean centered;
	private boolean dragging, dragged;
	private double pressX, pressY;

	public RecipeTreeScreen(CraftRequestScreen parent, List<TreeLine> lines, ItemStack target, long amount, boolean planOk) {
		super(Component.translatable("screen.tsautocraft.recipe_tree"));
		this.parent = parent;
		this.target = target;
		this.amount = amount;
		this.planOk = planOk;
		this.root = build(lines);
		if (root != null) initCollapse(root, 0);
		layout();
	}

	/** Pre-order lines with depth -> nodes; the trailing "n more" line hangs below the target. */
	private static Node build(List<TreeLine> lines) {
		Node root = null;
		List<Node> stack = new ArrayList<>();
		for (TreeLine l : lines) {
			Node n = new Node(l);
			if (l.item().isEmpty()) {
				if (root != null) root.children.add(n);
				continue;
			}
			if (root == null) {
				root = n;
				stack.add(n);
				continue;
			}
			int d = Math.max(1, l.depth());
			while (stack.size() > d) stack.removeLast();
			stack.getLast().children.add(n);
			stack.add(n);
		}
		return root;
	}

	private static void initCollapse(Node n, int depth) {
		n.collapsed = depth >= 2 && !n.children.isEmpty() && !n.missingBelow();
		for (Node c : n.children) initCollapse(c, depth + 1);
	}

	//region Layout (world coordinates, origin = top left of the target's subtree)

	private void layout() {
		if (root == null) return;
		measure(root);
		place(root, 0, 0);
	}

	private static int measure(Node n) {
		if (n.collapsed || n.children.isEmpty()) return n.width = NODE;
		int w = 0;
		for (Node c : n.children) w += measure(c);
		w += GAP_X * (n.children.size() - 1);
		return n.width = Math.max(NODE, w);
	}

	private static void place(Node n, int left, int y) {
		n.x = left + (n.width - NODE) / 2;
		n.y = y;
		if (n.collapsed) return;
		int span = 0;
		for (Node c : n.children) span += c.width;
		span += GAP_X * Math.max(0, n.children.size() - 1);
		int cx = left + (n.width - span) / 2;
		for (Node c : n.children) {
			place(c, cx, y + LEVEL_H);
			cx += c.width + GAP_X;
		}
	}

	/** Zooms the visible tree to fill the view and centers it; a tree too big even at {@link #MIN_ZOOM} starts at the top center. */
	private void center() {
		zoom = 1;
		if (root == null) return;
		float treeW = root.width, treeH = bottom(root) + 10; // + room for the "+N" label under collapsed nodes
		float viewW = width - 2 * MARGIN - 2 * FIT_PAD, viewH = viewBottom() - viewTop() - 2 * FIT_PAD;
		float fit = Math.min(viewW / treeW, viewH / treeH);
		if (fit >= MIN_ZOOM) {
			zoom = Math.min(fit, MAX_ZOOM);
			camX = treeW / 2f;
			camY = treeH / 2f;
		} else {
			zoom = MIN_ZOOM;
			camX = root.x + NODE / 2f;
			camY = ((viewBottom() - viewTop()) / 2f - FIT_PAD) / zoom;
		}
	}

	/** Lowest edge of the visible nodes below {@code n} (world y). */
	private static int bottom(Node n) {
		int b = n.y + NODE;
		if (!n.collapsed) for (Node c : n.children) b = Math.max(b, bottom(c));
		return b;
	}
	//endregion

	@Override
	protected void init() {
		int by = height - BOTTOM + 5, bw = Math.min(90, (width - 2 * MARGIN - 12) / 4);
		int x = width / 2 - (bw * 4 + 12) / 2;
		addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.back"), _ -> onClose()).bounds(x, by, bw, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.tree.center"), _ -> center()).bounds(x + bw + 4, by, bw, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.tree.expand_all"), _ -> expandAll()).bounds(x + 2 * (bw + 4), by, bw, 18).build());
		Button start = addRenderableWidget(Button.builder(Component.translatable("button.tsautocraft.start"), _ -> start()).bounds(x + 3 * (bw + 4), by, bw, 18).build());
		start.active = planOk;
		if (!centered) {
			centered = true;
			center();
		}
	}

	private void start() {
		minecraft.gui.setScreen(parent);
		parent.start();
	}

	/** Opens every node; the target keeps its place on screen. */
	private void expandAll() {
		if (root == null) return;
		int oldX = root.x;
		expand(root);
		layout();
		camX += root.x - oldX;
	}

	private static void expand(Node n) {
		n.collapsed = false;
		for (Node c : n.children) expand(c);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Tom's sync packets and request results go to the request dialog. */
	@Override
	public void receive(ValueInput in) {
		parent.receive(in);
	}

	private int viewTop() {
		return TOP;
	}

	private int viewBottom() {
		return height - BOTTOM;
	}

	private boolean inView(double mx, double my) {
		return mx >= MARGIN && mx < width - MARGIN && my >= viewTop() && my < viewBottom();
	}

	private float worldX(double mx) {
		return camX + (float) (mx - width / 2.0) / zoom;
	}

	private float worldY(double my) {
		return camY + (float) (my - (viewTop() + viewBottom()) / 2.0) / zoom;
	}

	private Node nodeAt(double mx, double my) {
		if (root == null || !inView(mx, my)) return null;
		return find(root, worldX(mx), worldY(my));
	}

	private static Node find(Node n, float wx, float wy) {
		if (wx >= n.x && wx < n.x + NODE && wy >= n.y && wy < n.y + NODE) return n;
		if (n.collapsed) return null;
		for (Node c : n.children) {
			Node f = find(c, wx, wy);
			if (f != null) return f;
		}
		return null;
	}

	//region Input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inView(event.x(), event.y())) {
			dragging = true;
			dragged = false;
			pressX = event.x();
			pressY = event.y();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) return super.mouseDragged(event, dx, dy);
		if (Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) > 3) dragged = true;
		camX -= (float) dx / zoom;
		camY -= (float) dy / zoom;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = false;
			if (!dragged) {
				Node n = nodeAt(event.x(), event.y());
				if (n != null && !n.children.isEmpty()) toggle(n, event.x(), event.y());
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	/** Collapses / expands {@code n}; the node stays under the mouse. */
	private void toggle(Node n, double mx, double my) {
		float offX = worldX(mx) - n.x, offY = worldY(my) - n.y;
		n.collapsed = !n.collapsed;
		layout();
		camX = n.x + offX - (float) (mx - width / 2.0) / zoom;
		camY = n.y + offY - (float) (my - (viewTop() + viewBottom()) / 2.0) / zoom;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (!inView(x, y) || dy == 0) return super.mouseScrolled(x, y, dx, dy);
		zoomAt(x, y, dy > 0);
		return true;
	}

	/** One zoom step (in / out), the world point under {@code x, y} stays in place. */
	private void zoomAt(double x, double y, boolean in) {
		float wx = worldX(x), wy = worldY(y);
		zoom = Mth.clamp(zoom * (in ? 1.25f : 0.8f), MIN_ZOOM, MAX_ZOOM);
		if (Math.abs(zoom - 1) < 0.05f) zoom = 1;
		camX = wx - (float) (x - width / 2.0) / zoom;
		camY = wy - (float) (y - (viewTop() + viewBottom()) / 2.0) / zoom;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		float step = 24 / zoom;
		switch (event.key()) {
			case InputConstants.KEY_LEFT -> camX -= step;
			case InputConstants.KEY_RIGHT -> camX += step;
			case InputConstants.KEY_UP -> camY -= step;
			case InputConstants.KEY_DOWN -> camY += step;
			case InputConstants.KEY_HOME -> center();
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				// A focused button (Tab navigation) keeps Enter for itself.
				if (getFocused() != null || !planOk) return super.keyPressed(event);
				start();
			}
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	/** +/- by typed character: works on every keyboard layout and for the numpad keys (no double step via keyPressed). */
	@Override
	public boolean charTyped(CharacterEvent event) {
		switch (event.codepoint()) {
			case '+', '=' -> zoomAt(width / 2.0, (viewTop() + viewBottom()) / 2.0, true);
			case '-' -> zoomAt(width / 2.0, (viewTop() + viewBottom()) / 2.0, false);
			default -> {
				return super.charTyped(event);
			}
		}
		return true;
	}
	//endregion

	//region Rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(MARGIN - 1, viewTop() - 1, width - MARGIN + 1, viewBottom() + 1, 0xFF000000);
		g.fill(MARGIN, viewTop(), width - MARGIN, viewBottom(), 0xFF8B8B8B);
		Component head = Component.translatable("label.tsautocraft.tree.title", AutocraftingTerminalScreen.shortNumber(amount), target.getHoverName());
		g.text(font, head, width / 2 - font.width(head) / 2, 6, 0xFFFFFFFF, true);
		String z = Math.round(zoom * 100) + "%";
		g.text(font, z, width - MARGIN - font.width(z), 6, 0xFFA0A0A0, true);
		legend(g, 19);
		if (root == null) return;

		g.enableScissor(MARGIN, viewTop(), width - MARGIN, viewBottom());
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(width / 2f, (viewTop() + viewBottom()) / 2f);
		pose.scale(zoom, zoom);
		pose.translate(-camX, -camY);
		edges(g, root);
		nodes(g, root);
		// Badges overlap the item's corner, so they go into a stratum above the items.
		g.nextStratum();
		badges(g, root);
		pose.popMatrix();
		g.disableScissor();

		Node hovered = dragging ? null : nodeAt(mouseX, mouseY);
		if (hovered != null) g.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
	}

	private static void edges(GuiGraphicsExtractor g, Node n) {
		if (n.collapsed || n.children.isEmpty()) return;
		int px = n.x + NODE / 2, mid = n.y + NODE + (LEVEL_H - NODE) / 2;
		g.fill(px, n.y + NODE, px + 1, mid, 0xFF3A3A3A);
		Node first = n.children.getFirst(), last = n.children.getLast();
		g.fill(Math.min(px, first.x + NODE / 2), mid, Math.max(px, last.x + NODE / 2) + 1, mid + 1, 0xFF3A3A3A);
		for (Node c : n.children) {
			g.fill(c.x + NODE / 2, mid, c.x + NODE / 2 + 1, c.y, 0xFF3A3A3A);
			edges(g, c);
		}
	}

	private static int state(Node n) {
		TreeLine l = n.line;
		return n.more() ? STORED : l.missing() > 0 ? MISSING : l.crafts() > 0 ? CRAFTED : STORED;
	}

	private static int frameColor(int state) {
		return state == MISSING ? COLOR_MISSING : state == CRAFTED ? COLOR_CRAFTED : COLOR_STORED;
	}

	/** Square of {@code size} with a 2px frame in the state color around a slot background. */
	private static void frame(GuiGraphicsExtractor g, int x, int y, int size, int state) {
		g.fill(x, y, x + size, y + size, frameColor(state));
		g.fill(x + 2, y + 2, x + size - 2, y + size - 2, SLOT);
	}

	/** {@link #BADGE} px badge with a pixel glyph: "!" = missing, hammer = crafted; stored has none. */
	private static void badge(GuiGraphicsExtractor g, int x, int y, int state) {
		if (state == STORED) return;
		g.fill(x, y, x + BADGE, y + BADGE, 0xFF000000);
		g.fill(x + 1, y + 1, x + BADGE - 1, y + BADGE - 1, state == MISSING ? BADGE_MISSING : BADGE_CRAFTED);
		if (state == MISSING) {
			g.fill(x + 3, y + 1, x + 4, y + 4, 0xFFFFFFFF);
			g.fill(x + 3, y + 5, x + 4, y + 6, 0xFFFFFFFF);
		} else {
			g.fill(x + 1, y + 1, x + 6, y + 3, 0xFFFFFFFF);
			g.fill(x + 3, y + 3, x + 4, y + 6, 0xFFFFFFFF);
		}
	}

	/** Centered row "[swatch] missing  [swatch] crafted  [swatch] stored", swatches drawn like small nodes. */
	private void legend(GuiGraphicsExtractor g, int y) {
		int[] states = {MISSING, CRAFTED, STORED};
		Component[] names = {
				Component.translatable("label.tsautocraft.tree.legend.missing"),
				Component.translatable("label.tsautocraft.tree.legend.crafted"),
				Component.translatable("label.tsautocraft.tree.legend.stored")};
		int sw = 10, total = 0;
		for (Component c : names) total += sw + 4 + font.width(c);
		total += 12 * (names.length - 1);
		int x = width / 2 - total / 2;
		for (int i = 0; i < states.length; i++) {
			frame(g, x, y, sw, states[i]);
			badge(g, x + sw - BADGE + 2, y - 2, states[i]);
			x += sw + 4;
			g.text(font, names[i], x, y + 1, 0xFFFFFFFF, true);
			x += font.width(names[i]) + 12;
		}
	}

	private static void badges(GuiGraphicsExtractor g, Node n) {
		badge(g, n.x + NODE - BADGE + 2, n.y - 2, state(n));
		if (n.collapsed) return;
		for (Node c : n.children) badges(g, c);
	}

	private void nodes(GuiGraphicsExtractor g, Node n) {
		TreeLine l = n.line;
		frame(g, n.x, n.y, NODE, state(n));
		if (n.more()) {
			String s = "…";
			g.text(font, s, n.x + NODE / 2 - font.width(s) / 2, n.y + 6, 0xFFFFFFFF, true);
		} else {
			g.item(l.item(), n.x + 2, n.y + 2);
			g.itemDecorations(font, l.item(), n.x + 2, n.y + 2, l.amount() > 1 ? AutocraftingTerminalScreen.shortNumber(l.amount()) : null);
		}
		if (n.collapsed) {
			String s = "+" + n.descendants();
			g.text(font, s, n.x + NODE / 2 - font.width(s) / 2, n.y + NODE + 2, 0xFFFFFF80, true);
			return;
		}
		for (Node c : n.children) nodes(g, c);
	}

	/** Item and amount, then how the need is covered (stored / crafts -> made / missing). */
	private static List<Component> tooltip(Node n) {
		TreeLine l = n.line;
		List<Component> lines = new ArrayList<>();
		if (n.more()) {
			lines.add(Component.translatable("label.tsautocraft.tree.more", l.amount()));
			return lines;
		}
		lines.add(Component.translatable("label.tsautocraft.item_count", l.amount(), l.item().getHoverName()));
		if (l.stored() > 0) lines.add(Component.translatable("tooltip.tsautocraft.tree.stored", l.stored()).withStyle(ChatFormatting.GRAY));
		if (l.crafts() > 0) lines.add(Component.translatable("tooltip.tsautocraft.tree.crafted", l.crafts(), l.made()).withStyle(ChatFormatting.AQUA));
		if (l.missing() > 0) lines.add(Component.translatable("tooltip.tsautocraft.tree.missing", l.missing()).withStyle(ChatFormatting.RED));
		if (!n.children.isEmpty()) lines.add(Component.translatable(n.collapsed ? "tooltip.tsautocraft.tree.expand" : "tooltip.tsautocraft.tree.collapse").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}
	//endregion
}
