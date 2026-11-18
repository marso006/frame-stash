package com.framestash.fabric;

import com.framestash.platform.Platform;
import eu.pb4.common.protection.api.CommonProtection;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class FabricPlatform implements Platform {
    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /**
     * Asks Common Protection API, which most Fabric claim mods implement (Flan, GOML, Open Parties and
     * Claims, ...). It is bundled, so with no claim mod installed this always allows.
     */
    @Override
    public boolean canInteract(ServerPlayer player, BlockPos pos) {
        return CommonProtection.canInteractBlock(player.level(), pos, player.getGameProfile(), player);
    }
}
