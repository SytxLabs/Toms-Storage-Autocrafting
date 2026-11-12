package eu.sytxlabs.tsautocraft.client;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import com.tom.storagemod.screen.TSContainerScreen;
import com.tom.storagemod.util.IDataReceiver;

import eu.sytxlabs.tsautocraft.TsAutocraft;
import eu.sytxlabs.tsautocraft.blockentity.RequesterBlockEntity;
import eu.sytxlabs.tsautocraft.menu.MenuUtil;
import eu.sytxlabs.tsautocraft.menu.RequesterMenu;

/** Per row: ghost item, target amount (text field), status. */
public class RequesterScreen extends TSContainerScreen<RequesterMenu> implements IDataReceiver {
	private static final Identifier GUI = TsAutocraft.id("textures/gui/requester.png");
	private final EditBox[] boxes = new EditBox[RequesterBlockEntity.ENTRIES];
	private boolean updating;

	public RequesterScreen(RequesterMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 176, 224);
		this.inventoryLabelY = RequesterMenu.PLAYER_Y - 11;
		menu.onUpdate = this::onUpdate;
	}

	@Override
	protected void init() {
		super.init();
		for (int i = 0; i < boxes.length; i++) {
			final int row = i;
			EditBox b = new EditBox(font, leftPos + 28, topPos + RequesterMenu.ROW_Y + i * RequesterMenu.ROW_H + 1, 48, 14, Component.translatable("label.tsautocraft.amount"));
			b.setMaxLength(7);
			b.setValue(Integer.toString(menu.amounts[i]));
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
					out.putInt("set", row);
					out.putInt("amt", v);
				});
			});
			boxes[i] = addRenderableWidget(b);
		}
	}

	private void onUpdate() {
		updating = true;
		try {
			for (int i = 0; i < boxes.length; i++) {
				if (boxes[i] != null && !boxes[i].isFocused()) boxes[i].setValue(Integer.toString(menu.amounts[i]));
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
		for (EditBox b : boxes) {
			if (b != null && b.isFocused() && (b.keyPressed(event) || b.canConsumeInput())) return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gr, int mouseX, int mouseY, float a) {
		super.extractBackground(gr, mouseX, mouseY, a);
		gr.blit(RenderPipelines.GUI_TEXTURED, GUI, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		super.extractLabels(g, xm, ym);
		for (int i = 0; i < RequesterBlockEntity.ENTRIES; i++) {
			int y = RequesterMenu.ROW_Y + i * RequesterMenu.ROW_H + 4;
			Component s = statusText(menu.status[i], menu.statusItems[i]);
			g.text(font, font.plainSubstrByWidth(s.getString(), 88), 80, y, statusColor(menu.status[i]), false);
		}
	}

	private static Component statusText(int st, ItemStack item) {
		return switch (st) {
		case RequesterBlockEntity.ST_STOCKED -> Component.translatable("status.tsautocraft.requester.stocked");
		case RequesterBlockEntity.ST_CRAFTING -> Component.translatable("status.tsautocraft.requester.crafting");
		case RequesterBlockEntity.ST_MISSING -> item.isEmpty() ? Component.translatable("status.tsautocraft.requester.missing_any") : Component.translatable("status.tsautocraft.requester.missing", item.getHoverName());
		case RequesterBlockEntity.ST_NO_PATTERN -> Component.translatable("status.tsautocraft.requester.no_pattern");
		case RequesterBlockEntity.ST_NO_NETWORK -> Component.translatable("status.tsautocraft.requester.no_network");
		default -> Component.empty();
		};
	}

	private static int statusColor(int st) {
		return switch (st) {
			case RequesterBlockEntity.ST_STOCKED -> 0xFF004D00;
			case RequesterBlockEntity.ST_CRAFTING -> 0xFF1E3A8A;
			default -> 0xFF8B0000;
		};
	}

	@Override
	public void getExclusionAreas(Consumer<Box> consumer) {
	}
}
