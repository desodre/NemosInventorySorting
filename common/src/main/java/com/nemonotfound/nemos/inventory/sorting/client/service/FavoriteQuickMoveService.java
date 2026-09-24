package com.nemonotfound.nemos.inventory.sorting.client.service;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Simulates quick-move with pickup clicks so a favorite is never a destination. */
public final class FavoriteQuickMoveService {
    private FavoriteQuickMoveService() {}

    public static void move(AbstractContainerMenu menu, int sourceIndex, Minecraft minecraft) {
        var favorites = FavoriteSlotService.INSTANCE;
        if (favorites.isFavorite(menu, sourceIndex) || minecraft.gameMode == null || minecraft.player == null
                || !menu.getCarried().isEmpty()) return;

        Slot source = menu.slots.get(sourceIndex);
        ItemStack stack = source.getItem();
        if (stack.isEmpty() || !source.isActive() || !source.mayPickup(minecraft.player)) return;

        // Storage menus place the player's 36 inventory slots at the end.
        int playerStart = menu.slots.size() - 36;
        if (playerStart <= 0) return;
        int targetStart = sourceIndex < playerStart ? playerStart : 0;
        int targetEnd = sourceIndex < playerStart ? menu.slots.size() : playerStart;
        List<Integer> targets = IntStream.range(targetStart, targetEnd).boxed()
                .filter(index -> !favorites.isFavorite(menu, index))
                .filter(index -> capacity(menu.slots.get(index), stack) > 0)
                .sorted(Comparator.comparing(index -> menu.slots.get(index).getItem().isEmpty()))
                .toList();
        int totalCapacity = targets.stream().mapToInt(index -> capacity(menu.slots.get(index), stack)).sum();
        if (totalCapacity == 0 || (!source.mayPlace(stack) && totalCapacity < stack.getCount())) return;

        click(menu, sourceIndex, minecraft);
        for (int index : targets) {
            if (menu.getCarried().isEmpty()) break;
            if (capacity(menu.slots.get(index), menu.getCarried()) > 0) click(menu, index, minecraft);
        }
        if (!menu.getCarried().isEmpty()) click(menu, sourceIndex, minecraft);
    }

    private static int capacity(Slot slot, ItemStack stack) {
        if (!slot.isActive() || !slot.mayPlace(stack)) return 0;
        ItemStack existing = slot.getItem();
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return 0;
        return Math.max(0, slot.getMaxStackSize(stack) - existing.getCount());
    }

    private static void click(AbstractContainerMenu menu, int index, Minecraft minecraft) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, index, 0, ClickType.PICKUP, minecraft.player);
    }
}
