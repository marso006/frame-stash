package com.framestash.compat;

import com.framestash.FrameStashMod;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;

/**
 * The few calls that differ between Minecraft versions. Each version branch has its own copy of this
 * class; everything else in common is the same on every branch.
 *
 * <p>This copy: Minecraft 1.21.11 (Identifier, permission sets).
 */
public final class Compat {
    private Compat() {}

    /** An id in the mod's namespace, e.g. {@code frame_stash:containers}. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FrameStashMod.MOD_ID, path);
    }

    /** Looks up an item by its id string, such as {@code minecraft:hopper}. */
    public static Optional<Item> item(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(parsed);
    }

    /** Whether the source has at least the classic permission level (0 = everyone ... 4 = owner). */
    public static boolean hasLevel(CommandSourceStack source, int level) {
        return switch (level) {
            case 0 -> true;
            case 1 -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
            case 2 -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            case 3 -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
            default -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
        };
    }

    /** Whether the container has a vanilla lock (the "lock" component). */
    public static boolean isLocked(net.minecraft.world.level.block.entity.BaseContainerBlockEntity container) {
        return container.isLocked();
    }

    /** The server level the player is in. */
    public static net.minecraft.server.level.ServerLevel level(ServerPlayer player) {
        return player.level();
    }

    /** Hides an icon's own extra tooltip lines (attributes, enchantments, map ids, ...), keeping name and lore. */
    public static void hideExtraTooltip(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.component.TooltipDisplay display = net.minecraft.world.item.component.TooltipDisplay.DEFAULT
                .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
                .withHidden(DataComponents.ENCHANTMENTS, true)
                .withHidden(DataComponents.STORED_ENCHANTMENTS, true)
                .withHidden(DataComponents.POTION_CONTENTS, true)
                .withHidden(DataComponents.CONTAINER, true)
                .withHidden(DataComponents.BUNDLE_CONTENTS, true)
                .withHidden(DataComponents.MAP_ID, true);
        stack.set(DataComponents.TOOLTIP_DISPLAY, display);
    }

    /** Hides an icon's tooltip completely (menu filler panes). */
    public static void hideTooltip(net.minecraft.world.item.ItemStack stack) {
        stack.set(DataComponents.TOOLTIP_DISPLAY,
                new net.minecraft.world.item.component.TooltipDisplay(true, new java.util.LinkedHashSet<>()));
    }

    /** Plays a sound that only this player hears, at their position. */
    public static void playTo(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        playTo(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
    }

    public static void playTo(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }
}
