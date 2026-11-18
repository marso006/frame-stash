package com.framestash.neoforge;

import com.framestash.FrameStashMod;
import com.framestash.command.FrameStashCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(FrameStashMod.MOD_ID)
public final class FrameStashNeoForge {
    public FrameStashNeoForge(IEventBus modBus) {
        FrameStashMod.init(new NeoForgePlatform());
        NeoForge.EVENT_BUS.addListener(FrameStashNeoForge::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(FrameStashNeoForge::onRightClickBlock);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        FrameStashCommands.register(event.getDispatcher());
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (NeoForgePlatform.isProbing() || event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer player
                && FrameStashMod.onUseBlock(player, event.getPos(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
