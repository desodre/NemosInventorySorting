package com.nemonotfound.nemos.inventory.sorting.service;

import com.google.gson.Gson;
import com.nemonotfound.nemos.inventory.sorting.models.ContainerInputContext;
import com.nemonotfound.nemos.inventory.sorting.models.SlotItem;
import com.nemonotfound.nemos.inventory.sorting.models.config.LockedSlotsConfig;
import com.nemonotfound.nemos.inventory.sorting.models.config.SettingsConfig;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FavoriteActionsTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private final LockedSlotsConfig previousConfig = LockedSlotsConfig.INSTANCE;
    private final SettingsConfig previousSettings = SettingsConfig.INSTANCE;
    private final AbstractContainerMenu menu = mock(AbstractContainerMenu.class, withSettings().useConstructor(null, 0));
    private final Inventory inventory = mock(Inventory.class);
    private final ContainerInputContext context = new ContainerInputContext(mock(MultiPlayerGameMode.class), mock(LocalPlayer.class));
    private final ItemStack cursor = mock(ItemStack.class);
    private Slot favorite;
    private Slot normal;

    @BeforeEach
    void setup() {
        var gson = new Gson();
        LockedSlotsConfig.INSTANCE = gson.fromJson("{\"lockedSlots\":[{\"index\":0}]}", LockedSlotsConfig.class);
        SettingsConfig.INSTANCE = gson.fromJson("{}", SettingsConfig.class);
        favorite = addSlot(inventory, 9);
        normal = addSlot(inventory, 10);
        when(menu.getCarried()).thenReturn(cursor);
        when(cursor.isEmpty()).thenReturn(true);
        when(menu.getSlot(anyInt())).thenAnswer(call -> menu.slots.get(call.getArgument(0, Integer.class)));
    }

    @AfterEach
    void restoreConfig() {
        LockedSlotsConfig.INSTANCE = previousConfig;
        SettingsConfig.INSTANCE = previousSettings;
    }

    @Test
    void neverSendsPickupDropOrQuickMoveForFavoriteSource() {
        var service = new ContainerInputService();
        service.pickup(menu, context, 0, 0);
        service.bulkAction(menu, context, 0, 1, ContainerInput.THROW);
        service.bulkAction(menu, context, 0, 0, ContainerInput.QUICK_MOVE);
        verifyNoInteractions(context.gameMode());

        service.bulkAction(menu, context, 1, 1, ContainerInput.THROW);
        verify(context.gameMode()).handleContainerInput(menu.containerId, 1, 1, ContainerInput.THROW, context.player());
    }

    @Test
    void sortExcludesFavoritesFromSourcesAndDestinations() {
        var favoriteStack = mock(ItemStack.class);
        var normalStack = mock(ItemStack.class);
        when(inventory.getItem(9)).thenReturn(favoriteStack);
        when(inventory.getItem(10)).thenReturn(normalStack);
        var comparing = mock(ComparingService.class);
        when(comparing.sort(anyList())).thenAnswer(call -> call.getArgument(0));
        var service = new SortingService(mock(SlotSwapService.class), comparing, null);
        var sorted = service.sortSlotItems(menu, 0, 2);
        assertThat(sorted).containsExactly(new SlotItem(1, normalStack));
        assertThat(service.retrieveSlotSwaps(menu, sorted, 0, 2)).isEmpty();
        verifyNoInteractions(favoriteStack);
    }

    @Test
    void mergingAndSwappingNeverPickUpFavoriteSourceOrTarget() {
        var service = spy(new SlotSwapService(mock(ContainerInputService.class)));
        service.mergeStack(menu, 0, 1);
        service.mergeStack(menu, 1, 0);
        verify(service, never()).pickUpItem(any(), anyInt());
    }

    @Test
    void quickMoveUsesExplicitNonFavoriteDestinationAndKeepsFavoriteCountUnchanged() {
        var container = mock(Container.class);
        var source = addSlot(container, 0);
        var stack = mock(ItemStack.class);
        when(container.getItem(0)).thenReturn(stack);
        when(stack.getCount()).thenReturn(12);
        var empty = mock(ItemStack.class);
        when(empty.isEmpty()).thenReturn(true);
        when(inventory.getItem(10)).thenReturn(empty);
        when(inventory.getMaxStackSize()).thenReturn(64);
        when(stack.getMaxStackSize()).thenReturn(64);
        var resolver = mock(QuickMoveTargetResolver.class);
        when(resolver.getQuickMoveTargetSlots(menu, source.index)).thenReturn(List.of(0, 1));
        var input = mock(ContainerInputService.class);
        doAnswer(call -> {
            when(menu.getCarried()).thenReturn(stack);
            return null;
        }).when(input).leftClickPickup(menu, context, source.index);
        doAnswer(call -> {
            when(menu.getCarried()).thenReturn(cursor);
            return null;
        }).when(input).leftClickPickup(menu, context, normal.index);

        new FavoriteQuickMoveService(input, resolver).move(menu, context, source.index, 0);

        var order = inOrder(input);
        order.verify(input).leftClickPickup(menu, context, source.index);
        order.verify(input).leftClickPickup(menu, context, normal.index);
        verifyNoMoreInteractions(input);
        verifyNoInteractions(context.gameMode());
        verify(inventory, never()).getItem(9);
    }

    @Test
    void quickMoveDoesNothingWhenOnlyFavoriteDestinationsAreAvailable() {
        var source = addSlot(mock(Container.class), 0);
        when(source.container.getItem(0)).thenReturn(mock(ItemStack.class));
        var resolver = mock(QuickMoveTargetResolver.class);
        when(resolver.getQuickMoveTargetSlots(menu, source.index)).thenReturn(List.of(favorite.index));
        var input = mock(ContainerInputService.class);

        new FavoriteQuickMoveService(input, resolver).move(menu, context, source.index, 0);
        verifyNoInteractions(input, context.gameMode());
    }

    @Test
    void quickMovePreservesCarriedStackWhenFavoritesRequireExplicitTransfers() {
        when(cursor.isEmpty()).thenReturn(false);
        when(inventory.getItem(10)).thenReturn(mock(ItemStack.class));
        var input = mock(ContainerInputService.class);
        var resolver = mock(QuickMoveTargetResolver.class);
        new FavoriteQuickMoveService(input, resolver).move(menu, context, 1, 0);
        verifyNoInteractions(input, resolver, context.gameMode());
    }

    @Test
    void scrollAndSplitNeverStartFromFavoriteSlots() {
        assertThat(ScrollTransferService.getInstance().handleSingleItemScrollMove(menu, 0, 1, true)).isFalse();
        assertThat(ScrollTransferTargetService.getInstance().getTransfer(menu, 0, -1, true)).isEmpty();
        SplitQuickMoveService.getInstance().handleSplitQuickMove(menu, 0);
        verifyNoInteractions(context.gameMode());
        verify(inventory, never()).getItem(9);
    }

    @Test
    void targetResolverExcludesFavoritesIncludingEmptyTemporaryStorage() {
        var resolver = QuickMoveTargetResolver.getInstance();
        assertThat(resolver.getAllSlots(menu)).containsExactly(1);
        assertThat(resolver.getQuickMoveTargetSlots(menu, 1)).doesNotContain(0);
        var source = addSlot(mock(Container.class), 0);
        var carried = mock(ItemStack.class);
        assertThat(ScrollTransferTargetService.getInstance()
                .getTemporaryCarriedSlot(menu, carried, source.index, normal.index)).isEmpty();
    }

    @Test
    void scrollTransferRejectsFavoriteDestinationBeforePickingUpSource() {
        var transfer = new ScrollTransferTargetService.SlotTransfer(normal, favorite, false);
        assertThat(ContainerItemTransferService.getInstance().transfer(menu, context, transfer)).isFalse();
        verifyNoInteractions(context.gameMode());
    }

    private Slot addSlot(Container container, int index) {
        var slot = new Slot(container, index, 0, 0);
        slot.index = menu.slots.size();
        menu.slots.add(slot);
        return slot;
    }
}
