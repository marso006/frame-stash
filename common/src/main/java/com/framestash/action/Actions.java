package com.framestash.action;

import com.framestash.FrameStashMod;
import com.framestash.config.FrameStashConfig;
import com.framestash.feedback.Feedback;
import com.framestash.prefs.PlayerPrefs;
import com.framestash.label.LabelScanner;
import com.framestash.label.LabelledContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

/** Runs a full action for a player: scan, check access, plan, apply, sync. */
public final class Actions {
    public enum Kind { STASH, RESTOCK }

    /** Hotbar is slots 0-8 and the main inventory 9-35; armor and offhand are never touched. */
    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_SIZE = 36;
    private static final int[] MAIN_ONLY = IntStream.range(HOTBAR_SIZE, MAIN_SIZE).toArray();
    private static final int[] HOTBAR_AND_MAIN = IntStream.range(0, MAIN_SIZE).toArray();

    private Actions() {}

    /** Result of {@link #perform}: either what happened, or why nothing ran. */
    public record Outcome(ActionResult result, String failure) {}

    /**
     * Runs an action with every check (feature toggle, cooldown) and the feedback. Used by the
     * commands, the menu and the gesture so they all behave the same.
     */
    public static Outcome perform(ServerPlayer player, Kind kind) {
        FrameStashConfig config = FrameStashMod.config();
        if (kind == Kind.RESTOCK && !config.restockEnabled) {
            return new Outcome(ActionResult.NOTHING, "Restock is turned off on this server");
        }
        if (!ActionGuard.tryUse(player, config.cooldownTicks)) {
            return new Outcome(ActionResult.NOTHING, "Please wait a moment before trying again");
        }
        ActionResult result = kind == Kind.STASH ? stash(player, config) : restock(player, config);
        Feedback.report(player, kind, result, config);
        return new Outcome(result, null);
    }

    /** The point distances are measured from: the middle of the player. */
    public static Vec3 origin(ServerPlayer player) {
        return player.position().add(0, player.getBbHeight() / 2.0, 0);
    }

    public static ActionResult stash(ServerPlayer player, FrameStashConfig config) {
        List<Target> targets = targets(player, config);
        if (targets.isEmpty()) return ActionResult.NOTHING;
        Inventory inventory = player.getInventory();
        List<Move> moves = StashAction.plan(inventory, PlayerPrefs.stashHotbar(player, config) ? HOTBAR_AND_MAIN : MAIN_ONLY,
                targets, config.overflowEnabled);
        return finish(player, moves, targets);
    }

    public static ActionResult restock(ServerPlayer player, FrameStashConfig config) {
        List<Target> targets = targets(player, config);
        if (targets.isEmpty()) return ActionResult.NOTHING;
        List<Move> moves = RestockAction.plan(player.getInventory(), HOTBAR_AND_MAIN, targets, config.overflowEnabled);
        return finish(player, moves, targets);
    }

    private static List<Target> targets(ServerPlayer player, FrameStashConfig config) {
        List<Target> targets = new ArrayList<>();
        for (LabelledContainer label : LabelScanner.scan(player.level(), origin(player), config)) {
            ContainerAccess.Access access = ContainerAccess.open(player, label, config);
            if (access.ok()) targets.add(access.target());
        }
        return targets;
    }

    private static ActionResult finish(ServerPlayer player, List<Move> moves, List<Target> targets) {
        if (moves.isEmpty()) return ActionResult.NOTHING;
        ActionResult result = Transfers.apply(moves, player.getInventory(), targets);
        if (result.items() > 0) {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        }
        return result;
    }
}
