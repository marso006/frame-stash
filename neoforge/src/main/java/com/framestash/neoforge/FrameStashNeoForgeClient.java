package com.framestash.neoforge;

import com.framestash.FrameStashMod;
import com.framestash.client.FrameStashClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Optional client part: keybinds. Only constructed on the physical client. */
@Mod(value = FrameStashMod.MOD_ID, dist = Dist.CLIENT)
public final class FrameStashNeoForgeClient {
    public FrameStashNeoForgeClient(IEventBus modBus) {
        modBus.addListener(FrameStashNeoForgeClient::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(FrameStashNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(FrameStashNeoForgeClient::onScreenKey);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = new KeyMapping.Category(com.framestash.compat.Compat.id("main"));
        event.registerCategory(category);
        FrameStashClient.createKeys(category).forEach(event::register);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        FrameStashClient.onClientTick(Minecraft.getInstance());
    }

    private static void onScreenKey(ScreenEvent.KeyPressed.Post event) {
        FrameStashClient.onScreenKey(Minecraft.getInstance(), event.getScreen(), event.getKeyEvent());
    }
}
