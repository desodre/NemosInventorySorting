package com.nemonotfound.nemos.inventory.sorting.client.service;

import com.google.gson.Gson;
import com.nemonotfound.nemos.inventory.sorting.Constants;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** Favorites are player inventory positions, not item identities. */
public final class FavoriteSlotService {
    private static final Path FILE = Path.of("config/nemos-inventory-sorting/locked-slots.json");
    private static final Gson GSON = new Gson();
    public static final FavoriteSlotService INSTANCE = new FavoriteSlotService(FILE);

    private final Path file;
    private final Set<Integer> favorites = new HashSet<>();
    private boolean loaded;

    FavoriteSlotService(Path file) {
        this.file = file;
    }

    public boolean isFavorite(AbstractContainerMenu menu, int menuIndex) {
        return !(menu instanceof CreativeModeInventoryScreen.ItemPickerMenu)
                && menuIndex >= 0 && menuIndex < menu.slots.size()
                && isFavorite(menu.slots.get(menuIndex));
    }

    public boolean isFavorite(Slot slot) {
        load();
        return favorites.contains(legacyIndex(slot));
    }

    public boolean toggle(AbstractContainerMenu menu, Slot slot) {
        int index = legacyIndex(slot);
        if (menu instanceof CreativeModeInventoryScreen.ItemPickerMenu || index < 0 || !slot.isActive()) {
            return false;
        }
        load();
        if (!favorites.remove(index)) {
            favorites.add(index);
        }
        save();
        return true;
    }

    public List<Integer> movableSlots(AbstractContainerMenu menu, int start, int end) {
        return IntStream.range(start, end).filter(index -> !isFavorite(menu, index)).boxed().toList();
    }

    public boolean hasFavorites(AbstractContainerMenu menu) {
        return menu.slots.stream().anyMatch(this::isFavorite);
    }

    // Same index layout as the modern version's locked-slots.json: main 0..26, hotbar 27..35.
    private static int legacyIndex(Slot slot) {
        if (slot == null || !(slot.container instanceof Inventory)) return -1;
        int index = slot.getContainerSlot();
        return index >= 0 && index < 36 ? (index + 27) % 36 : -1;
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            FavoriteData data = GSON.fromJson(reader, FavoriteData.class);
            if (data != null && data.lockedSlots != null) {
                for (FavoriteIndex favorite : data.lockedSlots) {
                    if (favorite != null && favorite.index >= 0 && favorite.index < 36) favorites.add(favorite.index);
                }
            }
        } catch (Exception e) {
            Constants.LOG.warn("Could not read favorite slots", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            var entries = favorites.stream().sorted().map(FavoriteIndex::new).toList();
            Files.writeString(file, GSON.toJson(new FavoriteData(entries)));
        } catch (Exception e) {
            Constants.LOG.error("Could not save favorite slots", e);
        }
    }

    private record FavoriteIndex(int index) {}
    private record FavoriteData(List<FavoriteIndex> lockedSlots) {}
}
