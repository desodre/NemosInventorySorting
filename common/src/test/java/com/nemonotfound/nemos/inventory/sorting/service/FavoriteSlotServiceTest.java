package com.nemonotfound.nemos.inventory.sorting.service;

import com.google.gson.Gson;
import com.nemonotfound.nemos.inventory.sorting.models.config.LockedSlotsConfig;
import com.nemonotfound.nemos.inventory.sorting.models.config.SettingsConfig;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FavoriteSlotServiceTest {
    private final Gson gson = new Gson();
    private final Inventory inventory = mock(Inventory.class);
    private final AbstractContainerMenu menu = mock(AbstractContainerMenu.class);

    @Test
    void mapsLegacyInventoryAndHotbarFavoritesAcrossDifferentMenuIndices() {
        var config = gson.fromJson("{\"lockedSlots\":[{\"index\":0},{\"index\":27}]}", LockedSlotsConfig.class);
        var service = new FavoriteSlotService(() -> config, () -> true);
        var mainSlot = new Slot(inventory, 9, 0, 0);
        mainSlot.index = 54;
        var hotbarSlot = new Slot(inventory, 0, 0, 0);
        hotbarSlot.index = 81;

        assertThat(service.isFavoritePlayerSlot(mainSlot)).isTrue();
        assertThat(service.isFavoritePlayerSlot(hotbarSlot)).isTrue();
        assertThat(service.isFavoritePlayerSlot(new Slot(inventory, 10, 0, 0))).isFalse();
        assertThat(service.isFavoritePlayerSlot(new Slot(mock(Container.class), 9, 0, 0))).isFalse();
        assertThat(service.canFavorite(menu, new Slot(inventory, 36, 0, 0))).isFalse();
    }

    @Test
    void togglesAndRestoresFavoritesAfterSerializationAndReload() {
        var config = new AtomicReference<>(gson.fromJson("{}", LockedSlotsConfig.class));
        var service = new FavoriteSlotService(config::get, () -> true);
        var slot = new Slot(inventory, 8, 0, 0);
        assertThat(service.toggle(menu, slot)).isTrue();
        config.set(gson.fromJson(gson.toJson(config.get()), LockedSlotsConfig.class));
        assertThat(service.isFavoritePlayerSlot(slot)).isTrue();
        assertThat(service.toggle(menu, slot)).isTrue();
        assertThat(service.isFavoritePlayerSlot(slot)).isFalse();
    }

    @Test
    void respectsLegacySettingUnlessFavoritesAreExplicitlyConfigured() {
        assertThat(gson.fromJson("{}", SettingsConfig.class).areFavoritesEnabled()).isTrue();
        assertThat(gson.fromJson("{\"enableSlotLocking\":false}", SettingsConfig.class).areFavoritesEnabled()).isFalse();
        assertThat(gson.fromJson("{\"enableSlotLocking\":false,\"enableFavorites\":true}", SettingsConfig.class).areFavoritesEnabled()).isTrue();
        var config = gson.fromJson("{\"lockedSlots\":[{\"index\":0}]}", LockedSlotsConfig.class);
        var service = new FavoriteSlotService(() -> config, () -> false);
        assertThat(service.isFavoritePlayerSlot(new Slot(inventory, 9, 0, 0))).isFalse();
        assertThat(service.toggle(menu, new Slot(inventory, 9, 0, 0))).isFalse();
    }
}
