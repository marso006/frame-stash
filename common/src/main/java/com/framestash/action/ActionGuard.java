package com.framestash.action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Per-player cooldown between actions. Only touched on the server thread. */
public final class ActionGuard {
    private static final Map<UUID, Integer> LAST_USE = new HashMap<>();

    private ActionGuard() {}

    /** Records a use and returns true if the player's cooldown has passed. */
    public static boolean tryUse(ServerPlayer player, int cooldownTicks) {
        int now = com.framestash.compat.Compat.level(player).getServer().getTickCount();
        Integer last = LAST_USE.get(player.getUUID());
        if (last != null && now - last < cooldownTicks && now >= last) {
            return false;
        }
        LAST_USE.put(player.getUUID(), now);
        if (LAST_USE.size() > 1024) {
            LAST_USE.values().removeIf(t -> now - t > 1200);
        }
        return true;
    }
}
