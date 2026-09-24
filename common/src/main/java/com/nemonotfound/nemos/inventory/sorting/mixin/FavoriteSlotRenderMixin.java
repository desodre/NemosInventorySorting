package com.nemonotfound.nemos.inventory.sorting.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nemonotfound.nemos.inventory.sorting.client.service.FavoriteSlotService;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation FAVORITE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "container/favorite_slot");

    @Unique
    private boolean nemosInventorySorting$isFavorite(Slot slot) {
        return !((Object) this instanceof CreativeModeInventoryScreen)
                && FavoriteSlotService.INSTANCE.isFavorite(slot);
    }

    @WrapOperation(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void nemosInventorySorting$makeRoomForStar(
            GuiGraphics graphics, Font font, ItemStack stack, int x, int y, String count,
            Operation<Void> original, GuiGraphics guiGraphics, Slot slot
    ) {
        if (!nemosInventorySorting$isFavorite(slot)) {
            original.call(graphics, font, stack, x, y, count);
            return;
        }
        original.call(graphics, font, stack, x, y, "");
        if (!stack.isEmpty() && (stack.getCount() != 1 || count != null)) {
            String amount = count == null ? String.valueOf(stack.getCount()) : count;
            graphics.drawString(font, amount, x + 10 - font.width(amount), y + 9, -1, true);
        }
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void nemosInventorySorting$renderFavorite(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (nemosInventorySorting$isFavorite(slot)) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            graphics.blitSprite(FAVORITE, slot.x + 11, slot.y + 8, 5, 5);
            graphics.pose().popPose();
        }
    }
}
