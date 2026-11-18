package com.framestash.action;

import com.framestash.FrameStashMod;
import com.framestash.config.FrameStashConfig;
import com.framestash.label.LabelScanner;
import com.framestash.label.LabelledContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Decides whether a labelled container may be used, and returns its inventory if so. */
public final class ContainerAccess {
    private ContainerAccess() {}

    public enum Denial {
        UNLOADED("unloaded"),
        NOT_A_CONTAINER("not a container"),
        LOCKED("locked"),
        BLOCKED("blocked"),
        PROTECTED("protected");

        public final String text;

        Denial(String text) {
            this.text = text;
        }
    }

    /** Either a usable target or the reason it was skipped. */
    public record Access(Target target, Denial denial) {
        public boolean ok() {
            return target != null;
        }
    }

    public static Access open(ServerPlayer player, LabelledContainer label, FrameStashConfig config) {
        ServerLevel level = player.level();
        for (BlockPos block : label.blocks()) {
            if (!level.isLoaded(block)) return denied(Denial.UNLOADED);
        }
        BlockState state = level.getBlockState(label.pos());
        if (!state.is(LabelScanner.CONTAINERS)) return denied(Denial.NOT_A_CONTAINER);

        // Locked containers (vanilla "lock" component) are skipped: stashing never uses a key.
        for (BlockPos block : label.blocks()) {
            if (level.getBlockEntity(block) instanceof BaseContainerBlockEntity be && be.isLocked()) {
                return denied(Denial.LOCKED);
            }
        }

        for (BlockPos block : label.blocks()) {
            if (!player.mayInteract(level, block)) return denied(Denial.PROTECTED); // spawn protection, border
            if (config.protectionCheck && !FrameStashMod.platform().canInteract(player, block)) {
                return denied(Denial.PROTECTED);
            }
        }

        Container container;
        if (state.getBlock() instanceof ChestBlock chest) {
            // Vanilla helper: returns the combined inventory for double chests, or null if blocked.
            container = ChestBlock.getContainer(chest, state, level, label.pos(), !config.skipBlockedChests);
            if (container == null) return denied(Denial.BLOCKED);
        } else {
            BlockEntity be = level.getBlockEntity(label.pos());
            if (!(be instanceof Container c)) return denied(Denial.NOT_A_CONTAINER);
            container = c;
        }
        return new Access(Target.of(label, container), null);
    }

    private static Access denied(Denial denial) {
        return new Access(null, denial);
    }
}
