package com.framestash.client;

import com.framestash.FrameStashMod;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional client side: keybinds for Stash, Restock and the menu. Only loaded by the loaders' client
 * entrypoints, never on a dedicated server.
 *
 * <p>A key press simply sends the matching command ({@code /stash}, {@code /restock},
 * {@code /framestash}), so there are no custom packets: the server stays fully usable by vanilla
 * clients, and the client part works on any server that has the mod.
 */
public final class FrameStashClient {
    public static final ResourceLocation CATEGORY_ID = ResourceLocation.fromNamespaceAndPath(FrameStashMod.MOD_ID, "main");
    /** Ignore repeats faster than this many ticks; the server has its own cooldown too. */
    private static final int MIN_TICKS_BETWEEN_PRESSES = 5;

    public static KeyMapping stash;
    public static KeyMapping restock;
    public static KeyMapping menu;

    /** Game time of the last command sent, or -1 if none yet. */
    private static long lastSend = -1;

    private FrameStashClient() {}

    /** Creates the key mappings in the given category. Each loader registers them its own way. */
    public static List<KeyMapping> createKeys(KeyMapping.Category category) {
        stash = new KeyMapping("key.frame_stash.stash", InputConstants.KEY_G, category);
        restock = new KeyMapping("key.frame_stash.restock", InputConstants.KEY_H, category);
        menu = new KeyMapping("key.frame_stash.menu", InputConstants.UNKNOWN.getValue(), category);
        return List.of(stash, restock, menu);
    }

    /** Called every client tick: handles key presses while no screen is open. */
    public static void onClientTick(Minecraft mc) {
        if (stash == null || mc.player == null) return;
        while (stash.consumeClick()) send(mc, "stash");
        while (restock.consumeClick()) send(mc, "restock");
        while (menu.consumeClick()) send(mc, "framestash");
    }

    /**
     * Called after a key press inside a screen, so Stash and Restock also work with your inventory or
     * a chest open (quick stack). Ignored in the creative inventory and while typing in a text box.
     */
    public static void onScreenKey(Minecraft mc, Screen screen, KeyEvent key) {
        if (stash == null || mc.player == null) return;
        if (!(screen instanceof AbstractContainerScreen<?>) || screen instanceof CreativeModeInventoryScreen) return;
        if (screen.getFocused() instanceof EditBox) return;
        if (stash.matches(key)) {
            send(mc, "stash");
        } else if (restock.matches(key)) {
            send(mc, "restock");
        }
    }

    private static void send(Minecraft mc, String command) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        if (lastSend >= 0 && now >= lastSend && now - lastSend < MIN_TICKS_BETWEEN_PRESSES) return;
        lastSend = now;
        if (connection.getCommands().getRoot().getChild(command) == null) {
            // Server without Frame Stash, or the player lacks permission for this command.
            mc.player.displayClientMessage(Component.literal("Frame Stash isn't available on this server"), true);
            return;
        }
        connection.sendCommand(command);
    }
}
