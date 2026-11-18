package com.framestash.neoforge;

import com.framestash.platform.Platform;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

final class NeoForgePlatform implements Platform {
    private static final ThreadLocal<Boolean> PROBING = ThreadLocal.withInitial(() -> false);

    static boolean isProbing() {
        return PROBING.get();
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    /**
     * NeoForge claim mods (FTB Chunks, Open Parties and Claims, ...) protect containers by cancelling
     * the right-click-block event, so a simulated empty-hand right click is posted and its outcome
     * read. Nothing else is done with the event. Server owners can turn this off with
     * {@code protectionCheck = false} if another mod reacts badly to it.
     */
    @Override
    public boolean canInteract(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PROBING.set(true);
        try {
            PlayerInteractEvent.RightClickBlock event = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
            return !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
        } finally {
            PROBING.set(false);
        }
    }
}
