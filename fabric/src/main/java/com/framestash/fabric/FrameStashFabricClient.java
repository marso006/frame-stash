package com.framestash.fabric;

import com.framestash.client.FrameStashClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;

/** Optional client part: keybinds. Not needed to join a server running Frame Stash. */
public final class FrameStashFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(com.framestash.compat.Compat.id("main"));
        FrameStashClient.createKeys(category).forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(FrameStashClient::onClientTick);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenKeyboardEvents.afterKeyPress(screen).register(
                        (s, key) -> FrameStashClient.onScreenKey(client, s, key)));
    }
}
