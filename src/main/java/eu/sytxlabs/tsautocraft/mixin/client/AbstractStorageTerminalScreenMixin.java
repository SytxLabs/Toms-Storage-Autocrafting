package eu.sytxlabs.tsautocraft.mixin.client;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.tom.storagemod.client.ClientUtil;
import com.tom.storagemod.menu.StorageTerminalMenu;
import com.tom.storagemod.menu.StorageTerminalMenu.SlotStorage;
import com.tom.storagemod.screen.AbstractStorageTerminalScreen;

import eu.sytxlabs.tsautocraft.client.CraftRequestScreen;
import eu.sytxlabs.tsautocraft.menu.CraftableTerminal;

/**
 * Requests from Tom's terminals: craftable items get a green "+" marker and a tooltip hint. A normal click on a craftable item that is not in storage (amount 0), or Ctrl + click on any craftable item, opens {@link CraftRequestScreen}.
 */
@Mixin(value = AbstractStorageTerminalScreen.class, remap = false)
public abstract class AbstractStorageTerminalScreenMixin {
	@Shadow protected int slotIDUnderMouse;

	/** The hint already went into Tom's amount lines this frame. */
	@Unique
	private boolean tsac$hintAdded;

	@Unique
	private AbstractStorageTerminalScreen<?> tsac$self() {
		return (AbstractStorageTerminalScreen<?>) (Object) this;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void tsac$requestClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (slotIDUnderMouse < 0) return;
		AbstractStorageTerminalScreen<?> self = tsac$self();
		StorageTerminalMenu menu = self.getMenu();
		if (!(menu instanceof CraftableTerminal ct) || !menu.getCarried().isEmpty()) return;
		SlotStorage slot = menu.getSlotByID(slotIDUnderMouse);
		if (slot.stack == null || !ct.tsac$isCraftable(slot.stack) || !self.pullNormal(event)) return;
		if (slot.stack.getQuantity() > 0 && !event.hasControlDown()) return;
		Minecraft.getInstance().gui.setScreen(new CraftRequestScreen(self, slot.stack.getStack()));
		cir.setReturnValue(true);
	}

	@Inject(method = "drawSlot", at = @At("RETURN"))
	private void tsac$drawCraftable(GuiGraphicsExtractor g, SlotStorage slot, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
		if (slot.stack == null || !(tsac$self().getMenu() instanceof CraftableTerminal ct) || !ct.tsac$isCraftable(slot.stack)) return;
		int x = slot.xDisplayPosition, y = slot.yDisplayPosition;
		// dark backdrop: green on the light grey slot alone is too faint
		g.fill(x, y, x + 6, y + 6, 0xFF373737);
		g.pose().pushMatrix();
		g.pose().translate(x + 1, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(Minecraft.getInstance().font, "+", 0, 0, 0xFF55FF55, false);
		g.pose().popMatrix();
	}

	/** Hint line for the hovered terminal stack, null if it is not craftable. */
	@Unique
	private Component tsac$hint() {
		if (slotIDUnderMouse < 0 || !(tsac$self().getMenu() instanceof CraftableTerminal ct)) return null;
		SlotStorage slot = tsac$self().getMenu().getSlotByID(slotIDUnderMouse);
		if (slot.stack == null || !ct.tsac$isCraftable(slot.stack)) return null;
		return Component.translatable(slot.stack.getQuantity() > 0 ? "tooltip.tsautocraft.terminal_craftable" : "tooltip.tsautocraft.terminal_craftable_empty")
				.withStyle(ChatFormatting.GRAY);
	}

	/*
	 * Tom's item tooltip gets its extra lines from ClientUtil.setTooltip (collected by Tom's item tooltip callback), so the hint
	 * goes in there: appended to Tom's amount lines when he sets them, else set alone right before the item tooltip.
	 */
	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lcom/tom/storagemod/client/ClientUtil;setTooltip([Lnet/minecraft/network/chat/Component;)V", ordinal = 0), index = 0)
	private Component[] tsac$appendHint(Component[] lines) {
		Component hint = tsac$hint();
		if (hint == null) return lines;
		tsac$hintAdded = true;
		Component[] out = Arrays.copyOf(lines, lines.length + 1);
		out[lines.length] = hint;
		return out;
	}

	@Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
	private void tsac$hintOnly(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!tsac$hintAdded) {
			Component hint = tsac$hint();
			if (hint != null) ClientUtil.setTooltip(hint); // Tom clears it again right after the tooltip
		}
		tsac$hintAdded = false;
	}
}
