package com.framestash.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A read-only chest menu made of icon items. It uses the vanilla chest menu type, so unmodded clients
 * render it. Every click is cancelled on the server and the client is resynced, so icons can never be
 * taken and the player's own inventory cannot be changed through it.
 */
public final class StashMenu extends ChestMenu {
    /** What a clickable icon does. */
    @FunctionalInterface
    public interface Button {
        void click(StashMenu menu, ContainerInput type, int mouseButton);
    }

    private final ServerPlayer player;
    private final SimpleContainer icons;
    private final Consumer<StashMenu> builder;
    private final Map<Integer, Button> buttons = new HashMap<>();
    /** One line shown on the main page after an action, such as "Stashed 42 items into 3 containers". */
    String status = "";

    private StashMenu(int id, ServerPlayer player, int rows, SimpleContainer icons, Consumer<StashMenu> builder) {
        super(rows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3, id, player.getInventory(), icons, rows);
        this.player = player;
        this.icons = icons;
        this.builder = builder;
        rebuild();
    }

    /** Opens a menu of 3 or 6 rows whose icons come from {@code builder}. */
    public static void open(ServerPlayer player, Component title, int rows, Consumer<StashMenu> builder) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new StashMenu(id, player, rows, new SimpleContainer(rows * 9), builder), title));
    }

    public ServerPlayer player() {
        return player;
    }

    public int size() {
        return icons.getContainerSize();
    }

    /** Places an icon; a null button makes it decoration only. */
    public void set(int slot, ItemStack icon, Button button) {
        icons.setItem(slot, icon);
        if (button != null) {
            buttons.put(slot, button);
        } else {
            buttons.remove(slot);
        }
    }

    /** Re-runs the builder, for example after a toggle or an action. */
    public void rebuild() {
        icons.clearContent();
        buttons.clear();
        builder.accept(this);
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int mouseButton, ContainerInput type, Player clicker) {
        if (slotId >= 0 && slotId < icons.getContainerSize()
                && (type == ContainerInput.PICKUP || type == ContainerInput.QUICK_MOVE)) {
            Button button = buttons.get(slotId);
            if (button != null) {
                button.click(this, type, mouseButton);
            }
        }
        // Undo whatever the client predicted (picked-up icon, moved stack, ...).
        if (player.containerMenu == this) {
            sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }
}
