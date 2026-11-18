package com.framestash.command;

import com.framestash.FrameStashMod;
import com.framestash.action.Actions;
import com.framestash.action.ContainerAccess;
import com.framestash.config.FrameStashConfig;
import com.framestash.gui.Menus;
import com.framestash.label.LabelScanner;
import com.framestash.label.LabelledContainer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** /stash, /restock, /framestash (menu), /framestash info and /framestash reload. */
public final class FrameStashCommands {
    private static final int INFO_LINES = 20;

    private FrameStashCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        Predicate<CommandSourceStack> canUse =
                source -> com.framestash.compat.Compat.hasLevel(source, FrameStashMod.config().commandPermissionLevel);

        dispatcher.register(Commands.literal("stash")
                .requires(canUse)
                .executes(ctx -> run(ctx, Actions.Kind.STASH)));

        dispatcher.register(Commands.literal("restock")
                .requires(canUse)
                .executes(ctx -> run(ctx, Actions.Kind.RESTOCK)));

        dispatcher.register(Commands.literal("framestash")
                .requires(canUse)
                .executes(ctx -> {
                    Menus.openMain(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("info").executes(FrameStashCommands::info))
                .then(Commands.literal("reload")
                        .requires(source -> com.framestash.compat.Compat.hasLevel(source, 2))
                        .executes(FrameStashCommands::reload)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Actions.Kind kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Actions.Outcome outcome = Actions.perform(player, kind);
        if (outcome.failure() != null) {
            ctx.getSource().sendFailure(Component.literal(outcome.failure()));
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, outcome.result().items());
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FrameStashConfig config = FrameStashMod.config();
        List<LabelledContainer> containers = LabelScanner.scan(player.level(), Actions.origin(player), config);
        CommandSourceStack source = ctx.getSource();

        if (containers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No labelled containers within " + config.radius + " blocks"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(containers.size() + " labelled container"
                + (containers.size() == 1 ? "" : "s") + " within " + config.radius + " blocks ("
                + FrameStashMod.platform().loaderName() + "):").withStyle(ChatFormatting.GOLD), false);

        int shown = 0;
        for (LabelledContainer container : containers) {
            if (shown++ >= INFO_LINES) {
                int rest = containers.size() - INFO_LINES;
                source.sendSuccess(() -> Component.literal("...and " + rest + " more").withStyle(ChatFormatting.GRAY), false);
                break;
            }
            ContainerAccess.Access access = ContainerAccess.open(player, container, config);
            MutableComponent line = describe(player, container, config);
            if (!access.ok()) {
                line.append(Component.literal(" (skipped: " + access.denial().text + ")").withStyle(ChatFormatting.RED));
            }
            source.sendSuccess(() -> line, false);
        }
        return containers.size();
    }

    private static MutableComponent describe(ServerPlayer player, LabelledContainer container, FrameStashConfig config) {
        BlockPos pos = container.pos();
        int distance = (int) Math.round(Math.sqrt(container.distanceSq()));
        MutableComponent line = Component.literal("- ")
                .append(player.level().getBlockState(pos).getBlock().getName())
                .append(Component.literal(" at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        + ", " + distance + "m: ").withStyle(ChatFormatting.GRAY));
        boolean any = appendItems(line, container.primary(), false);
        if (!container.overflow().isEmpty()) {
            if (any) line.append(Component.literal("; "));
            line.append(Component.literal(config.overflowEnabled ? "overflow " : "").withStyle(ChatFormatting.AQUA));
            appendItems(line, container.overflow(), false);
            any = true;
        }
        if (container.catchAll()) {
            if (any) line.append(Component.literal("; "));
            line.append(Component.literal("catch-all").withStyle(ChatFormatting.YELLOW));
        }
        return line;
    }

    private static boolean appendItems(MutableComponent line, Set<Item> items, boolean any) {
        for (Item item : items) {
            if (any) line.append(Component.literal(", "));
            line.append(new ItemStack(item).getHoverName());
            any = true;
        }
        return any;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            FrameStashConfig.LoadResult result = FrameStashMod.reloadAndResync(source.getServer());
            source.sendSuccess(() -> Component.literal("Reloaded " + FrameStashConfig.FILE_NAME), true);
            for (String problem : result.problems()) {
                source.sendSuccess(() -> Component.literal("  " + problem).withStyle(ChatFormatting.YELLOW), false);
            }
            return 1;
        } catch (IOException e) {
            FrameStashMod.LOGGER.error("Could not reload config", e);
            source.sendFailure(Component.literal("Could not read " + FrameStashConfig.FILE_NAME + ": " + e.getMessage()));
            return 0;
        }
    }
}
