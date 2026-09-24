package com.nemonotfound.nemos.inventory.sorting.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nemonotfound.nemos.inventory.sorting.service.FavoriteSlotService;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.nemonotfound.nemos.inventory.sorting.Constants.MOD_ID;

@Mixin(AbstractContainerScreen.class)
public abstract class FavoriteSlotRenderMixin {
    @Unique
    private static final Identifier FAVORITE = Identifier.fromNamespaceAndPath(MOD_ID, "container/favorite_slot");

    @Unique
    private boolean nemosInventorySorting$isFavorite(Slot slot) {
        return !((Object) this instanceof CreativeModeInventoryScreen)
                && FavoriteSlotService.INSTANCE.isFavoritePlayerSlot(slot);
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void nemosInventorySorting$makeRoomForStar(
            GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, String count,
            Operation<Void> original, GuiGraphicsExtractor slotGraphics, Slot slot, int mouseX, int mouseY
    ) {
        if (!nemosInventorySorting$isFavorite(slot)) {
            original.call(graphics, font, stack, x, y, count);
            return;
        }
        // Preserve vanilla durability/cooldown rendering and move only the count left of the star.
        original.call(graphics, font, stack, x, y, "");
        if (!stack.isEmpty() && (stack.getCount() != 1 || count != null)) {
            String amount = count == null ? String.valueOf(stack.getCount()) : count;
            graphics.text(font, amount, x + 10 - font.width(amount), y + 9, -1, true);
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void nemosInventorySorting$renderFavorite(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (nemosInventorySorting$isFavorite(slot)) {
            // Just above the durability bar, in the lower-right portion of the slot.
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FAVORITE, slot.x + 11, slot.y + 8, 5, 5);
        }
    }
}
