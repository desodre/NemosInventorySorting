package com.nemonotfound.nemos.inventory.sorting.service;

import com.nemonotfound.nemos.inventory.sorting.models.ContainerInputContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

/** Uses explicit pickup clicks when vanilla QUICK_MOVE could fill a favorite destination. */
class FavoriteQuickMoveService {
    private final ContainerInputService input;
    private final QuickMoveTargetResolver targets;

    FavoriteQuickMoveService(ContainerInputService input, QuickMoveTargetResolver targets) {
        this.input = input;
        this.targets = targets;
    }

    void move(AbstractContainerMenu menu, ContainerInputContext context, int sourceIndex, int button) {
        var favorites = FavoriteSlotService.INSTANCE;
        if (favorites.isFavoritePlayerSlot(menu, sourceIndex)) {
            return;
        }
        if (menu.slots.stream().noneMatch(favorites::isFavoritePlayerSlot)) {
            context.gameMode().handleContainerInput(menu.containerId, sourceIndex, button, ContainerInput.QUICK_MOVE, context.player());
            return;
        }

        var source = menu.getSlot(sourceIndex);
        var stack = source.getItem();
        if (!menu.getCarried().isEmpty() || stack.isEmpty() || !source.isActive() || !source.mayPickup(context.player())) {
            return;
        }
        var destinations = targets.getQuickMoveTargetSlots(menu, sourceIndex).stream()
                .map(menu::getSlot)
                .filter(slot -> slot != source && !favorites.isFavoritePlayerSlot(slot))
                .filter(slot -> capacity(slot, stack) > 0)
                .sorted(Comparator.comparing(slot -> slot.getItem().isEmpty()))
                .toList();
        int capacity = destinations.stream().mapToInt(slot -> capacity(slot, stack)).sum();
        // Result slots cannot accept leftovers; require enough room for the whole result.
        if (capacity == 0 || (!source.mayPlace(stack) && capacity < stack.getCount())) {
            return;
        }

        input.leftClickPickup(menu, context, sourceIndex);
        for (var target : destinations) {
            if (menu.getCarried().isEmpty()) {
                break;
            }
            if (capacity(target, menu.getCarried()) > 0) {
                input.leftClickPickup(menu, context, target.index);
            }
        }
        if (!menu.getCarried().isEmpty()) {
            input.leftClickPickup(menu, context, sourceIndex);
        }
    }

    private static int capacity(Slot slot, ItemStack stack) {
        if (!slot.isActive() || !slot.mayPlace(stack)) {
            return 0;
        }
        var existing = slot.getItem();
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
            return 0;
        }
        return Math.max(0, slot.getMaxStackSize(stack) - existing.getCount());
    }
}
