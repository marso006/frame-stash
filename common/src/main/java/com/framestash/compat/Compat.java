package com.framestash.compat;

import com.framestash.FrameStashMod;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.LockCode;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/**
 * The few calls that differ between Minecraft versions. Each version branch has its own copy of this
 * class; everything else in common is the same on every branch.
 *
 * <p>This copy: Minecraft 1.21.5 (ResourceLocation, integer permission levels, lock read from components).
 */
public final class Compat {
    private Compat() {}

    /** An id in the mod's namespace, e.g. {@code frame_stash:containers}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrameStashMod.MOD_ID, path);
    }

    /** Looks up an item by its id string, such as {@code minecraft:hopper}. */
    public static Optional<Item> item(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(parsed);
    }

    /** Whether the source has at least the classic permission level (0 = everyone ... 4 = owner). */
    public static boolean hasLevel(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }

    /** Whether the container has a vanilla lock (the "lock" component). */
    public static boolean isLocked(BaseContainerBlockEntity container) {
        LockCode lock = container.collectComponents().get(DataComponents.LOCK);
        return lock != null && !lock.equals(LockCode.NO_LOCK);
    }

    /** The server level the player is in. */
    public static net.minecraft.server.level.ServerLevel level(ServerPlayer player) {
        return player.serverLevel();
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

    /** Shows a short message on the player's action bar. */
    public static void overlay(net.minecraft.world.entity.player.Player player, net.minecraft.network.chat.Component message) {
        player.displayClientMessage(message, true);
    }

    /** The item used for empty menu slots. */
    public static Item fillerItem() {
        return net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE;
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
