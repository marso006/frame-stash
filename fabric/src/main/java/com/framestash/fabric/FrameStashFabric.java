package com.framestash.fabric;

import com.framestash.FrameStashMod;
import com.framestash.command.FrameStashCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public final class FrameStashFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FrameStashMod.init(new FabricPlatform());
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> FrameStashCommands.register(dispatcher));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer
                    && FrameStashMod.onUseBlock(serverPlayer, hit.getBlockPos(), hand)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
