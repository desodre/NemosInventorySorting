package com.nemonotfound.nemos.inventory.sorting.service;

import com.google.gson.Gson;
import com.nemonotfound.nemos.inventory.sorting.models.ContainerInputContext;
import com.nemonotfound.nemos.inventory.sorting.models.SlotItem;
import com.nemonotfound.nemos.inventory.sorting.models.config.LockedSlotsConfig;
import com.nemonotfound.nemos.inventory.sorting.models.config.SettingsConfig;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Executes real vanilla menu pickup logic, with only the network/player environment mocked. */
class FavoriteTransferIntegrationTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.3 loads default components from data packs; this fixture only needs stack limits.
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : List.of(Items.STONE, Items.DIRT, Items.DIAMOND)) {
            if (!item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder().bindComponents(components);
            }
        }
    }

    private final LockedSlotsConfig previousConfig = LockedSlotsConfig.INSTANCE;
    private final SettingsConfig previousSettings = SettingsConfig.INSTANCE;
    private final SimpleContainer playerItems = new SimpleContainer(36);
    private final SimpleContainer storage = new SimpleContainer(1);
    private final Inventory inventory = mock(Inventory.class);
    private final AbstractContainerMenu menu = mock(AbstractContainerMenu.class,
            withSettings().useConstructor(null, 0).defaultAnswer(CALLS_REAL_METHODS));
    private final LocalPlayer player = mock(LocalPlayer.class);
    private final MultiPlayerGameMode gameMode = mock(MultiPlayerGameMode.class);
    private final ContainerInputContext context = new ContainerInputContext(gameMode, player);
    private final ContainerInputService input = spy(new ContainerInputService());

    @BeforeEach
    void setup() {
        var gson = new Gson();
        LockedSlotsConfig.INSTANCE = gson.fromJson("{\"lockedSlots\":[{\"index\":0}]}", LockedSlotsConfig.class);
        SettingsConfig.INSTANCE = gson.fromJson("{}", SettingsConfig.class);
        when(inventory.getItem(anyInt())).thenAnswer(call -> playerItems.getItem(call.getArgument(0)));
        when(inventory.removeItem(anyInt(), anyInt())).thenAnswer(call -> playerItems.removeItem(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { playerItems.setItem(call.getArgument(0), call.getArgument(1)); return null; })
                .when(inventory).setItem(anyInt(), any());
        when(inventory.getMaxStackSize()).thenReturn(64);
        when(player.getInventory()).thenReturn(inventory);
        var level = mock(ClientLevel.class);
        when(player.level()).thenReturn(level);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        addSlot(new Slot(storage, 0, 0, 0));
        for (int index = 9; index < 36; index++) addSlot(new Slot(inventory, index, 0, 0));
        for (int index = 0; index < 9; index++) addSlot(new Slot(inventory, index, 0, 0));
        doReturn(Optional.of(context)).when(input).getContext();
        doAnswer(call -> {
            menu.clicked(call.getArgument(1), call.getArgument(2), call.getArgument(3), player);
            return null;
        }).when(gameMode).handleContainerInput(anyInt(), anyInt(), anyInt(), any(), eq(player));
    }

    @AfterEach
    void restore() {
        LockedSlotsConfig.INSTANCE = previousConfig;
        SettingsConfig.INSTANCE = previousSettings;
    }

    @Test
    void incomingBulkMoveMergesOnlyUnfavoritedStacksAndConservesItems() {
        storage.setItem(0, new ItemStack(Items.STONE, 12));
        playerItems.setItem(9, new ItemStack(Items.STONE, 20));
        playerItems.setItem(10, new ItemStack(Items.STONE, 60));

        input.bulkAction(menu, context, 0, 0, ContainerInput.QUICK_MOVE);

        assertThat(playerItems.getItem(9).getCount()).isEqualTo(20);
        assertThat(playerItems.getItem(10).getCount()).isEqualTo(64);
        assertThat(playerItems.getItem(8).getCount()).isEqualTo(8);
        assertThat(storage.getItem(0).isEmpty()).isTrue();
        assertThat(menu.getCarried().isEmpty()).isTrue();
        assertThat(totalItems()).isEqualTo(92);
    }

    @Test
    void fullInventoryDoesNotUseEmptyReservedFavoriteOrLoseItems() {
        for (int i = 0; i < 36; i++) playerItems.setItem(i, new ItemStack(Items.DIRT, 64));
        playerItems.setItem(9, ItemStack.EMPTY);
        storage.setItem(0, new ItemStack(Items.STONE, 12));

        input.bulkAction(menu, context, 0, 0, ContainerInput.QUICK_MOVE);

        assertThat(storage.getItem(0).getCount()).isEqualTo(12);
        assertThat(playerItems.getItem(9).isEmpty()).isTrue();
        assertThat(menu.getCarried().isEmpty()).isTrue();
        verifyNoInteractions(gameMode);
    }

    @Test
    void splitTransferPreservesFavoriteAndMovesOnlyHalfOfSource() throws Exception {
        storage.setItem(0, new ItemStack(Items.STONE, 12));
        playerItems.setItem(9, new ItemStack(Items.STONE, 20));
        playerItems.setItem(10, new ItemStack(Items.STONE, 60));
        var constructor = SplitQuickMoveService.class.getDeclaredConstructor(ContainerInputService.class, QuickMoveTargetResolver.class);
        constructor.setAccessible(true);
        var split = constructor.newInstance(input, QuickMoveTargetResolver.getInstance());

        split.handleSplitQuickMove(menu, 0);

        assertThat(storage.getItem(0).getCount()).isEqualTo(6);
        assertThat(playerItems.getItem(9).getCount()).isEqualTo(20);
        assertThat(playerItems.getItem(10).getCount()).isEqualTo(64);
        assertThat(playerItems.getItem(8).getCount()).isEqualTo(2);
        assertThat(menu.getCarried().isEmpty()).isTrue();
        assertThat(totalItems()).isEqualTo(92);
    }

    @Test
    void scrollTransferRestoresCursorWithoutUsingEmptyFavoriteForTemporaryStorage() throws Exception {
        storage.setItem(0, new ItemStack(Items.STONE, 12));
        playerItems.setItem(10, new ItemStack(Items.STONE, 60));
        menu.setCarried(new ItemStack(Items.DIAMOND));
        var constructor = ContainerItemTransferService.class.getDeclaredConstructor(ContainerInputService.class, ScrollTransferTargetService.class);
        constructor.setAccessible(true);
        var transfer = constructor.newInstance(input, ScrollTransferTargetService.getInstance());

        assertThat(transfer.transfer(menu, context,
                new ScrollTransferTargetService.SlotTransfer(menu.getSlot(0), menu.getSlot(2), false))).isTrue();

        assertThat(storage.getItem(0).getCount()).isEqualTo(11);
        assertThat(playerItems.getItem(10).getCount()).isEqualTo(61);
        assertThat(playerItems.getItem(9).isEmpty()).isTrue();
        assertThat(menu.getCarried().is(Items.DIAMOND)).isTrue();
        assertThat(menu.getCarried().getCount()).isEqualTo(1);
        assertThat(totalItems()).isEqualTo(73);
    }

    @Test
    void sortingAndMergingPreserveFavoriteStackAndTotalCount() throws Exception {
        playerItems.setItem(9, new ItemStack(Items.STONE, 7));
        playerItems.setItem(10, new ItemStack(Items.STONE, 4));
        playerItems.setItem(11, new ItemStack(Items.STONE, 8));
        playerItems.setItem(12, new ItemStack(Items.DIAMOND, 1));
        // The bundle/backpack click policy has its own tests; exercise the real swap algorithm here.
        var swaps = new SlotSwapService(input) {
            @Override void pickUpItem(AbstractContainerMenu target, int slot) {
                input.leftClickPickup(target, context, slot);
            }
        };
        var comparing = mock(ComparingService.class);
        when(comparing.sort(anyList())).thenAnswer(call -> call.<List<SlotItem>>getArgument(0).stream()
                .sorted(Comparator.comparingInt((SlotItem item) -> item.itemStack().getCount()).reversed()).toList());
        var sorting = new SortingService(swaps, comparing, null);
        var constructor = MergingService.class.getDeclaredConstructor(SlotSwapService.class, Minecraft.class);
        constructor.setAccessible(true);
        var merging = constructor.newInstance(swaps, null);

        merging.mergeAllItems(menu, sorting.sortSlotItems(menu, 1, 28));
        var sorted = sorting.sortSlotItems(menu, 1, 28);
        sorting.sortItemsInInventory(menu, sorting.retrieveSlotSwaps(menu, sorted, 1, 28));

        assertThat(playerItems.getItem(9).getCount()).isEqualTo(7);
        assertThat(playerItems.getItem(10).getCount()).isEqualTo(12);
        assertThat(playerItems.getItem(11).is(Items.DIAMOND)).isTrue();
        assertThat(playerItems.getItem(12).isEmpty()).isTrue();
        assertThat(menu.getCarried().isEmpty()).isTrue();
        assertThat(totalItems()).isEqualTo(20);
    }

    @Test
    void directVanillaMousePickupRemainsAvailableOnFavorite() {
        playerItems.setItem(9, new ItemStack(Items.STONE, 7));
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        assertThat(playerItems.getItem(9).isEmpty()).isTrue();
        assertThat(menu.getCarried().getCount()).isEqualTo(7);
        menu.clicked(2, 0, ContainerInput.PICKUP, player);
        assertThat(playerItems.getItem(10).getCount()).isEqualTo(7);
        assertThat(FavoriteSlotService.INSTANCE.isFavoritePlayerSlot(menu, 1)).isTrue();
        assertThat(FavoriteSlotService.INSTANCE.isFavoritePlayerSlot(menu, 2)).isFalse();
    }

    private void addSlot(Slot slot) {
        slot.index = menu.slots.size();
        menu.slots.add(slot);
    }

    private int totalItems() {
        return menu.slots.stream().mapToInt(slot -> slot.getItem().getCount()).sum() + menu.getCarried().getCount();
    }
}
