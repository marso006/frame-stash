package com.framestash.feedback;

import com.framestash.action.ActionResult;
import com.framestash.action.Actions;
import com.framestash.config.FrameStashConfig;
import com.framestash.label.LabelledContainer;
import com.framestash.prefs.PlayerPrefs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Action bar summary, particle trail and sound. Text is literal, not translated, because vanilla
 * clients do not have this mod's language files.
 */
public final class Feedback {
    private static final int MAX_TRAILS = 16;

    private Feedback() {}

    public static void report(ServerPlayer player, Actions.Kind kind, ActionResult result, FrameStashConfig config) {
        Component message = Component.literal(summary(kind, result));
        if (config.actionBar) {
            player.displayClientMessage(message, true);
        } else {
            player.sendSystemMessage(message);
        }
        if (result.items() == 0) return;

        if (PlayerPrefs.particles(player, config)) {
            ServerLevel level = com.framestash.compat.Compat.level(player);
            Vec3 from = Actions.origin(player);
            int trails = 0;
            for (LabelledContainer container : result.containers()) {
                if (trails++ >= MAX_TRAILS) break;
                trail(level, from, Vec3.atCenterOf(container.pos()));
            }
        }
        if (PlayerPrefs.sound(player, config)) {
            com.framestash.compat.Compat.playTo(player, SoundEvents.ITEM_PICKUP, 0.25f,
                    kind == Actions.Kind.STASH ? 1.4f : 1.1f);
        }
    }

    public static String summary(Actions.Kind kind, ActionResult result) {
        long items = result.items();
        int containers = result.containers().size();
        if (items == 0) {
            return kind == Actions.Kind.STASH ? "Nothing to stash" : "Nothing to restock";
        }
        String itemText = items + (items == 1 ? " item" : " items");
        String containerText = containers + (containers == 1 ? " container" : " containers");
        return kind == Actions.Kind.STASH
                ? "Stashed " + itemText + " into " + containerText
                : "Restocked " + itemText + " from " + containerText;
    }

    /** A particle trail from the player to one container, used by the menu's "show the way". */
    public static void highlight(ServerPlayer player, BlockPos pos) {
        trail(com.framestash.compat.Compat.level(player), Actions.origin(player), Vec3.atCenterOf(pos));
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        int steps = Math.max(3, Math.min(24, (int) (distance * 1.5)));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
