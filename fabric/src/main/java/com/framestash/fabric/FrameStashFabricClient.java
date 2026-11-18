package com.framestash.fabric;

import com.framestash.client.FrameStashClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

/** Optional client part: keybinds. Not needed to join a server running Frame Stash. */
public final class FrameStashFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FrameStashClient.createKeys().forEach(KeyBindingHelper::registerKeyBinding);
        // Rebuild the key table once everything is loaded so KeyMappingMixin can claim our keys.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> KeyMapping.resetMapping());
        ClientTickEvents.END_CLIENT_TICK.register(FrameStashClient::onClientTick);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenKeyboardEvents.afterKeyPress(screen).register(
                        (s, key, scancode, modifiers) -> FrameStashClient.onScreenKey(client, s, key, scancode)));
    }
}
