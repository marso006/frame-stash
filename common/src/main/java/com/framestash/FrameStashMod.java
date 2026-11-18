package com.framestash;

import com.framestash.action.Actions;
import com.framestash.config.FrameStashConfig;
import com.framestash.label.LabelScanner;
import com.framestash.platform.Platform;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constants, the live config, and the loader-independent entry points. */
public final class FrameStashMod {
    public static final String MOD_ID = "frame_stash";
    public static final String MOD_NAME = "Frame Stash";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static Platform platform;
    private static volatile FrameStashConfig config = new FrameStashConfig();

    private FrameStashMod() {}

    /** Called once by each loader's entrypoint. */
    public static void init(Platform loaderPlatform) {
        platform = loaderPlatform;
        try {
            FrameStashConfig.LoadResult result = FrameStashConfig.load(configPath());
            config = result.config();
            result.problems().forEach(p -> LOGGER.warn("{}: {}", FrameStashConfig.FILE_NAME, p));
        } catch (IOException e) {
            LOGGER.error("Could not read {}; using defaults", configPath(), e);
        }
        LOGGER.info("{} initialised on {}", MOD_NAME, loaderPlatform.loaderName());
    }

    public static Platform platform() {
        if (platform == null) {
            throw new IllegalStateException(MOD_NAME + " used before init");
        }
        return platform;
    }

    public static FrameStashConfig config() {
        return config;
    }

    public static Path configPath() {
        return platform().configDir().resolve(FrameStashConfig.FILE_NAME);
    }

    /** Re-reads the config file. On an I/O error the current config stays in place. */
    public static FrameStashConfig.LoadResult reloadConfig() throws IOException {
        FrameStashConfig.LoadResult result = FrameStashConfig.load(configPath());
        config = result.config();
        return result;
    }

    /** Reloads the config and resends the command tree, since the permission level may have changed. */
    public static FrameStashConfig.LoadResult reloadAndResync(MinecraftServer server) throws IOException {
        FrameStashConfig.LoadResult result = reloadConfig();
        server.getPlayerList().getPlayers().forEach(p -> server.getCommands().sendCommands(p));
        return result;
    }

    /**
     * Optional gesture: sneak and right-click a labelled container with an empty main hand.
     *
     * @return true if the click was used for a stash and the container must not open
     */
    public static boolean onUseBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        FrameStashConfig cfg = config;
        if (!cfg.sneakGesture || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getMainHandItem().isEmpty() || player.isSpectator()) {
            return false;
        }
        if (!player.level().getBlockState(pos).is(LabelScanner.CONTAINERS)) {
            return false;
        }
        boolean labelled = LabelScanner.scan(player.level(), Actions.origin(player), cfg).stream()
                .anyMatch(c -> c.contains(pos));
        if (!labelled) {
            return false;
        }
        Actions.Outcome outcome = Actions.perform(player, Actions.Kind.STASH);
        if (outcome.failure() != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(outcome.failure()), true);
        }
        return true;
    }
}
