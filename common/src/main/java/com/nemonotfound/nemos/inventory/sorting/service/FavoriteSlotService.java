package com.nemonotfound.nemos.inventory.sorting.service;

import com.nemonotfound.nemos.inventory.sorting.models.LockedSlot;
import com.nemonotfound.nemos.inventory.sorting.models.config.LockedSlotsConfig;
import com.nemonotfound.nemos.inventory.sorting.models.config.SettingsConfig;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/** Favorites belong to player inventory slots, including empty reserved slots. */
public class FavoriteSlotService {

    public static final FavoriteSlotService INSTANCE = new FavoriteSlotService(
            () -> LockedSlotsConfig.INSTANCE, () -> SettingsConfig.INSTANCE.areFavoritesEnabled());

    private final Supplier<LockedSlotsConfig> config;
    private final BooleanSupplier enabled;

    FavoriteSlotService(Supplier<LockedSlotsConfig> config, BooleanSupplier enabled) {
        this.config = config;
        this.enabled = enabled;
    }

    public boolean isFavoritePlayerSlot(AbstractContainerMenu menu, int index) {
        return !(menu instanceof CreativeModeInventoryScreen.ItemPickerMenu)
                && isFavoritePlayerSlot(menu.getSlot(index));
    }

    public boolean isFavoritePlayerSlot(Slot slot) {
        int index = legacyIndex(slot);
        return enabled.getAsBoolean() && index >= 0
                && config.get().getLockedSlots().contains(new LockedSlot(index));
    }

    public boolean canFavorite(AbstractContainerMenu menu, Slot slot) {
        return enabled.getAsBoolean()
                && !(menu instanceof CreativeModeInventoryScreen.ItemPickerMenu)
                && legacyIndex(slot) >= 0 && slot.isActive();
    }

    /** Returns whether a valid slot was toggled; callers persist only successful toggles. */
    public boolean toggle(AbstractContainerMenu menu, Slot slot) {
        if (!canFavorite(menu, slot)) {
            return false;
        }
        var favorite = new LockedSlot(legacyIndex(slot));
        if (!config.get().remove(favorite)) {
            config.get().add(favorite);
        }
        return true;
    }

    public List<Integer> getUnfavoritedSlots(AbstractContainerMenu menu, int start, int end) {
        return IntStream.range(start, end)
                .filter(index -> !isFavoritePlayerSlot(menu, index))
                .boxed().toList();
    }

    // Keep locked-slots.json compatible: main inventory is 0..26, hotbar is 27..35.
    private static int legacyIndex(Slot slot) {
        if (slot == null || !(slot.container instanceof Inventory)) {
            return -1;
        }
        int index = slot.getContainerSlot();
        return index >= 0 && index < 36 ? (index + 27) % 36 : -1;
    }
}
