package com.framestash.gui;

import static com.framestash.gui.Icons.icon;
import static com.framestash.gui.Icons.line;
import static com.framestash.gui.Icons.text;

import com.framestash.FrameStashMod;
import com.framestash.action.Actions;
import com.framestash.action.ContainerAccess;
import com.framestash.config.FrameStashConfig;
import com.framestash.feedback.Feedback;
import com.framestash.label.LabelScanner;
import com.framestash.label.LabelledContainer;
import com.framestash.prefs.PlayerPrefs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The pages of the /framestash menu. */
public final class Menus {
    private static final Component TITLE = Component.literal(FrameStashMod.MOD_NAME);
    private static final int PER_PAGE = 45;

    private Menus() {}

    public static void openMain(ServerPlayer player) {
        StashMenu.open(player, TITLE, 3, Menus::main);
    }

    public static void openContainers(ServerPlayer player, int page) {
        StashMenu.open(player, Component.literal(FrameStashMod.MOD_NAME + " - Nearby"), 6, containers(page));
    }

    // --- main page (3 rows) ----------------------------------------------------------------------

    private static void main(StashMenu menu) {
        ServerPlayer player = menu.player();
        FrameStashConfig config = FrameStashMod.config();
        fill(menu);

        if (!menu.status.isEmpty()) {
            menu.set(4, icon(Items.PAPER, text(menu.status, ChatFormatting.YELLOW), List.of()), null);
        }

        boolean hotbar = PlayerPrefs.stashHotbar(player, config);
        menu.set(10, icon(Items.CHEST, text("Stash", ChatFormatting.GREEN, ChatFormatting.BOLD), List.of(
                line("Move matching items into the"),
                line("labelled containers around you."),
                line(hotbar ? "Includes your hotbar." : "Your hotbar is left alone."),
                Component.empty(),
                text("Click to stash", ChatFormatting.DARK_GRAY))),
                (m, type, button) -> runAction(m, Actions.Kind.STASH));

        if (config.restockEnabled) {
            menu.set(11, icon(Items.HOPPER, text("Restock", ChatFormatting.AQUA, ChatFormatting.BOLD), List.of(
                    line("Top up partial stacks from"),
                    line("containers labelled for them."),
                    Component.empty(),
                    text("Click to restock", ChatFormatting.DARK_GRAY))),
                    (m, type, button) -> runAction(m, Actions.Kind.RESTOCK));
        } else {
            menu.set(11, icon(Items.HOPPER, text("Restock", ChatFormatting.DARK_GRAY),
                    List.of(line("Turned off on this server"))), null);
        }

        List<LabelledContainer> nearby = LabelScanner.scan(com.framestash.compat.Compat.level(player), Actions.origin(player), config);
        long skipped = nearby.stream().filter(c -> !ContainerAccess.open(player, c, config).ok()).count();
        List<Component> nearbyLore = new ArrayList<>();
        nearbyLore.add(line(nearby.size() + " labelled container" + (nearby.size() == 1 ? "" : "s")
                + " within " + config.radius + " blocks"));
        if (skipped > 0) nearbyLore.add(text(skipped + " can't be used right now", ChatFormatting.RED));
        nearbyLore.add(Component.empty());
        nearbyLore.add(text("Click to browse", ChatFormatting.DARK_GRAY));
        menu.set(13, icon(Items.MAP, text("Nearby containers", ChatFormatting.GOLD), nearbyLore),
                (m, type, button) -> openContainers(m.player(), 0));

        menu.set(15, icon(Items.ITEM_FRAME, text("How to label", ChatFormatting.WHITE), List.of(
                line("1. Hang an item frame on a chest,"),
                line("   barrel or shulker box."),
                line("2. Put an item in the frame."),
                line("3. Use Stash here or type /stash."),
                Component.empty(),
                line("Frames on either half of a double"),
                line("chest label the whole chest."))), null);

        List<Component> special = new ArrayList<>();
        special.add(line(config.overflowEnabled
                ? "Glow item frame: overflow, used once"
                : "Glow item frame: same as a normal frame"));
        if (config.overflowEnabled) special.add(line("every normal-frame container is full."));
        config.catchAll().ifPresentOrElse(item -> {
            special.add(Component.empty());
            special.add(line("Frame with ").append(new ItemStack(item).getHoverName()).append(line(": catch-all")));
            special.add(line("for items with nowhere else to go."));
        }, () -> special.add(line("Catch-all is turned off.")));
        menu.set(16, icon(Items.GLOW_ITEM_FRAME, text("Overflow and catch-all", ChatFormatting.WHITE), special), null);

        menu.set(18, Icons.toggle(Items.LEVER, "Stash hotbar", hotbar,
                List.of(line("Let Stash move items"), line("out of your hotbar."))),
                (m, type, button) -> {
                    PlayerPrefs.toggleStashHotbar(m.player(), FrameStashMod.config());
                    click(m.player());
                    m.rebuild();
                });
        menu.set(19, toggleOrLocked(Items.BELL, "Sound", config.sound, PlayerPrefs.sound(player, config)),
                config.sound ? (m, type, button) -> {
                    PlayerPrefs.toggleSound(m.player(), FrameStashMod.config());
                    click(m.player());
                    m.rebuild();
                } : null);
        menu.set(20, toggleOrLocked(Items.BLAZE_POWDER, "Particles", config.particles,
                        PlayerPrefs.particles(player, config)),
                config.particles ? (m, type, button) -> {
                    PlayerPrefs.toggleParticles(m.player(), FrameStashMod.config());
                    click(m.player());
                    m.rebuild();
                } : null);

        menu.set(22, icon(Items.BARRIER, text("Close", ChatFormatting.RED), List.of()),
                (m, type, button) -> m.player().closeContainer());

        if (com.framestash.compat.Compat.hasLevel(player.createCommandSourceStack(), 2)) {
            menu.set(26, icon(Items.COMPARATOR, text("Reload config", ChatFormatting.LIGHT_PURPLE), List.of(
                    line("Re-read frame_stash.toml."),
                    text("Operators only", ChatFormatting.DARK_GRAY))),
                    (m, type, button) -> {
                        try {
                            FrameStashConfig.LoadResult result = FrameStashMod.reloadAndResync(com.framestash.compat.Compat.level(m.player()).getServer());
                            m.status = result.problems().isEmpty() ? "Config reloaded"
                                    : "Config reloaded with " + result.problems().size() + " problem(s), see /framestash reload";
                        } catch (IOException e) {
                            m.status = "Could not read the config: " + e.getMessage();
                        }
                        click(m.player());
                        m.rebuild();
                    });
        }
    }

    private static ItemStack toggleOrLocked(Item item, String name, boolean serverAllows, boolean on) {
        if (!serverAllows) {
            return icon(item, text(name, ChatFormatting.DARK_GRAY), List.of(line("Turned off on this server")));
        }
        return Icons.toggle(item, name, on, List.of(line("Your own setting.")));
    }

    private static void runAction(StashMenu menu, Actions.Kind kind) {
        Actions.Outcome outcome = Actions.perform(menu.player(), kind);
        menu.status = outcome.failure() != null ? outcome.failure() : Feedback.summary(kind, outcome.result());
        menu.rebuild();
    }

    // --- nearby containers (6 rows) --------------------------------------------------------------

    private static Consumer<StashMenu> containers(int requestedPage) {
        return menu -> {
            ServerPlayer player = menu.player();
            FrameStashConfig config = FrameStashMod.config();
            List<LabelledContainer> all = LabelScanner.scan(com.framestash.compat.Compat.level(player), Actions.origin(player), config);
            int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
            int page = Math.max(0, Math.min(requestedPage, pages - 1));

            for (int slot = PER_PAGE; slot < menu.size(); slot++) {
                menu.set(slot, Icons.filler(), null);
            }
            if (all.isEmpty()) {
                menu.set(22, icon(Items.ITEM_FRAME, text("No labelled containers nearby", ChatFormatting.YELLOW), List.of(
                        line("Hang an item frame with an item in it"),
                        line("on a chest, barrel or shulker box."))), null);
            }

            int start = page * PER_PAGE;
            for (int i = start; i < Math.min(all.size(), start + PER_PAGE); i++) {
                LabelledContainer container = all.get(i);
                menu.set(i - start, containerIcon(player, container, config), (m, type, button) -> {
                    Feedback.highlight(m.player(), container.pos());
                    BlockPos pos = container.pos();
                    com.framestash.compat.Compat.overlay(m.player(), Component.literal("Showing the way to " + pos.getX() + " "
                            + pos.getY() + " " + pos.getZ()));
                    click(m.player());
                });
            }

            menu.set(45, icon(Items.ARROW, text("Back", ChatFormatting.WHITE), List.of()),
                    (m, type, button) -> openMain(m.player()));
            if (page > 0) {
                menu.set(48, icon(Items.SPECTRAL_ARROW, text("Previous page", ChatFormatting.WHITE), List.of()),
                        (m, type, button) -> openContainers(m.player(), page - 1));
            }
            menu.set(49, icon(Items.PAPER, text("Page " + (page + 1) + " of " + pages, ChatFormatting.GRAY), List.of(
                    line(all.size() + " within " + config.radius + " blocks"))), null);
            if (page < pages - 1) {
                menu.set(50, icon(Items.SPECTRAL_ARROW, text("Next page", ChatFormatting.WHITE), List.of()),
                        (m, type, button) -> openContainers(m.player(), page + 1));
            }
            menu.set(53, icon(Items.BARRIER, text("Close", ChatFormatting.RED), List.of()),
                    (m, type, button) -> m.player().closeContainer());
        };
    }

    private static ItemStack containerIcon(ServerPlayer player, LabelledContainer container, FrameStashConfig config) {
        Item iconItem = !container.primary().isEmpty() ? container.primary().iterator().next()
                : !container.overflow().isEmpty() ? container.overflow().iterator().next()
                : config.catchAll().orElse(Items.HOPPER);
        BlockPos pos = container.pos();
        int distance = (int) Math.round(Math.sqrt(container.distanceSq()));
        MutableComponent name = Component.empty().withStyle(ChatFormatting.WHITE)
                .append(com.framestash.compat.Compat.level(player).getBlockState(pos).getBlock().getName())
                .append(text(" - " + distance + "m", ChatFormatting.GRAY));

        List<Component> lore = new ArrayList<>();
        lore.add(line(pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        if (!container.primary().isEmpty()) lore.add(itemList("Labels: ", container.primary(), ChatFormatting.WHITE));
        if (!container.overflow().isEmpty()) {
            lore.add(itemList(config.overflowEnabled ? "Overflow: " : "Labels (glow): ", container.overflow(),
                    ChatFormatting.AQUA));
        }
        if (container.catchAll()) lore.add(text("Catch-all", ChatFormatting.YELLOW));

        ContainerAccess.Access access = ContainerAccess.open(player, container, config);
        if (access.ok()) {
            Container inv = access.target().container();
            int used = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (!inv.getItem(i).isEmpty()) used++;
            }
            ChatFormatting color = used == inv.getContainerSize() ? ChatFormatting.RED
                    : used * 4 >= inv.getContainerSize() * 3 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
            lore.add(line("Slots used: ").append(text(used + "/" + inv.getContainerSize(), color)));
        } else {
            lore.add(text("Skipped: " + access.denial().text, ChatFormatting.RED));
        }
        lore.add(Component.empty());
        lore.add(text("Click to show the way", ChatFormatting.DARK_GRAY));
        return icon(iconItem, name, lore);
    }

    private static Component itemList(String prefix, Collection<Item> items, ChatFormatting color) {
        MutableComponent line = line(prefix);
        int shown = 0;
        for (Item item : items) {
            if (shown == 5) {
                line.append(line(", +" + (items.size() - 5) + " more"));
                break;
            }
            if (shown++ > 0) line.append(line(", "));
            line.append(new ItemStack(item).getHoverName().copy().withStyle(color));
        }
        return line;
    }

    // --- helpers -------------------------------------------------------------------------------

    private static void fill(StashMenu menu) {
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.set(slot, Icons.filler(), null);
        }
    }

    private static void click(ServerPlayer player) {
        com.framestash.compat.Compat.playTo(player, SoundEvents.UI_BUTTON_CLICK, 0.4f, 1.0f);
    }
}
