package com.nemonotfound.nemos.inventory.sorting.client.service;

import net.minecraft.world.Container;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FavoriteSlotServiceTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void favoritePersistsWithTheModernVersionFormat() throws Exception {
        Path file = directory.resolve("locked-slots.json");
        var service = new FavoriteSlotService(file);
        Slot mainInventorySlot = new Slot(mock(Inventory.class), 9, 0, 0);
        AbstractContainerMenu menu = new TestMenu();

        assertTrue(service.toggle(menu, mainInventorySlot));
        assertTrue(service.isFavorite(mainInventorySlot));
        assertTrue(Files.readString(file).contains("\"index\":0"));
        assertTrue(new FavoriteSlotService(file).isFavorite(mainInventorySlot));

        assertTrue(service.toggle(menu, mainInventorySlot));
        assertFalse(service.isFavorite(mainInventorySlot));
    }

    @Test
    void onlyPlayerStorageAndHotbarSlotsCanBeFavorites() {
        var service = new FavoriteSlotService(directory.resolve("locked-slots.json"));
        var menu = new TestMenu();
        assertFalse(service.toggle(menu, new Slot(mock(Container.class), 0, 0, 0)));
        assertFalse(service.toggle(menu, new Slot(mock(Inventory.class), 36, 0, 0)));
        assertTrue(service.toggle(menu, new Slot(mock(Inventory.class), 0, 0, 0)));
        assertTrue(service.isFavorite(new Slot(mock(Inventory.class), 0, 0, 0)));
    }

    @Test
    void sortingTargetsSkipFavoritePositions() {
        var service = new FavoriteSlotService(directory.resolve("locked-slots.json"));
        var menu = new TestMenu();
        var playerInventory = mock(Inventory.class);
        menu.slot(new Slot(playerInventory, 9, 0, 0));
        menu.slot(new Slot(playerInventory, 10, 0, 0));
        menu.slot(new Slot(playerInventory, 11, 0, 0));
        assertTrue(service.toggle(menu, menu.slots.get(1)));

        assertEquals(List.of(0, 2), service.movableSlots(menu, 0, 3));
    }

    private static class TestMenu extends AbstractContainerMenu {
        TestMenu() { super(null, 0); }
        void slot(Slot slot) { addSlot(slot); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }
}
