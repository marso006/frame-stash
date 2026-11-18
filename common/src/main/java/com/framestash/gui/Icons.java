package com.framestash.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Builds the icon items used in menus. Names and lore are plain text, so vanilla clients show them as-is. */
final class Icons {
    private Icons() {}

    static ItemStack icon(Item item, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withItalic(false)));
        List<Component> lines = new ArrayList<>(lore.size());
        for (Component line : lore) {
            lines.add(line.copy().withStyle(s -> s.withItalic(false)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        // Hide the item's own extra tooltip lines (attributes, map ids, ...) so only our text shows.
        com.framestash.compat.Compat.hideExtraTooltip(stack);
        return stack;
    }

    static ItemStack toggle(Item item, String name, boolean on, List<Component> lore) {
        List<Component> lines = new ArrayList<>(lore);
        lines.add(Component.empty());
        lines.add(on ? text("On", ChatFormatting.GREEN) : text("Off", ChatFormatting.RED));
        lines.add(text("Click to switch", ChatFormatting.DARK_GRAY));
        ItemStack stack = icon(item, text(name, on ? ChatFormatting.GREEN : ChatFormatting.GRAY), lines);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on);
        return stack;
    }

    /** A pane with no tooltip at all, used to fill empty slots. */
    static ItemStack filler() {
        ItemStack stack = new ItemStack(com.framestash.compat.Compat.fillerItem());
        com.framestash.compat.Compat.hideTooltip(stack);
        return stack;
    }

    static MutableComponent text(String text, ChatFormatting... formats) {
        return Component.literal(text).withStyle(formats);
    }

    static MutableComponent line(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }
}
