package com.framestash.platform;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** The small surface each loader module implements. Everything else lives in common. */
public interface Platform {
    /** "Fabric" or "NeoForge", for logs and /framestash info. */
    String loaderName();

    /** The loader's config directory; frame_stash.toml lives here. */
    Path configDir();

    /**
     * Whether claim and protection mods let the player use the block at {@code pos}. Vanilla rules
     * (spawn protection, world border) are checked separately in common code.
     */
    boolean canInteract(ServerPlayer player, BlockPos pos);
}
