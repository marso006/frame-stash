package com.framestash.gametest;

import com.framestash.action.Actions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Not a test: a scripted photo shoot for the mod page. Builds a storage room in a fresh world,
 * then saves raw screenshots to {@code docs/raw} (from the project root). Run with
 * {@code ./gradlew :fabric:runClientGameTest}; a game window opens for about a minute.
 */
public class ScreenshotShoot implements FabricClientGameTest {
    private static final int Y = 100;
    /**
     * Wall layout, left to right: chests on the top row (y+1) and barrels on the bottom row (y).
     * Chests go on top because vanilla refuses to open a chest with a solid block above it.
     */
    private static final Item[] TOP = {Items.COBBLESTONE, Items.STONE, Items.STONE, Items.DIRT,
            Items.SAND, Items.BONE, Items.HOPPER};
    private static final Item[] BOTTOM = {Items.OAK_LOG, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND,
            Items.REDSTONE, Items.COAL, Items.WHEAT};
    /** Index in TOP that gets a glow frame (overflow for stone). */
    private static final int OVERFLOW = 2;

    private Path out;

    @Override
    public void runTest(ClientGameTestContext context) {
        out = Paths.get(System.getProperty("framestash.shots", "../../docs/raw")).toAbsolutePath().normalize();
        try {
            Files.createDirectories(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        context.getInput().resizeWindow(1920, 1080);
        context.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.options.fov().set(70);
            mc.resizeDisplay();
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            for (String cmd : List.of("time set 6000", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "difficulty peaceful",
                    "gamerule announceAdvancements false", "gamemode survival @a", "kill @e[type=!player]")) {
                world.getServer().runCommand(cmd);
            }
            world.getServer().runOnServer(ScreenshotShoot::buildRoom);
            context.waitTicks(5);

            // 1. The labelled wall, no HUD.
            camera(context, world, 0.5, Y, 7.5, 180f, 8f);
            hud(context, false);
            context.waitTicks(10);
            shot(context, "01_wall");

            // 2. Inventory before.
            world.getServer().runOnServer(ScreenshotShoot::fillInventory);
            camera(context, world, 0.5, Y, 3.5, 180f, 5f);
            hud(context, true);
            context.waitTicks(5);
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(5);
            shot(context, "02_inventory_before");
            context.setScreen(() -> null);

            // 3. Third-person stash with trails and the action bar, recorded as frames for the GIF.
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            camera(context, world, 0.5, Y, 4.5, 180f, 12f);
            context.waitTicks(10);
            for (int i = 0; i < 4; i++) {
                frame(context, i);
                context.waitTicks(2);
            }
            world.getServer().runOnServer(server -> Actions.perform(player(server), Actions.Kind.STASH));
            for (int i = 4; i < 30; i++) {
                context.waitTick();
                frame(context, i);
            }
            shot(context, "03_action_bar");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            // 4. Inventory after.
            context.waitTicks(5);
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(5);
            shot(context, "04_inventory_after");
            context.setScreen(() -> null);

            // 5. Overflow: the full primary stone chest, then the glow-frame chest that took the rest.
            openContainer(context, world, new BlockPos(-2, Y + 1, -1));
            shot(context, "05_primary_full");
            openContainer(context, world, new BlockPos(-1, Y + 1, -1));
            shot(context, "06_overflow");
            context.setScreen(() -> null);

            // 6. The menu and the nearby-containers page.
            context.waitTicks(25); // let the cooldown pass
            world.getServer().runCommand("execute as @a run framestash");
            context.waitForScreen(ContainerScreen.class);
            context.waitTicks(5);
            shot(context, "07_menu");
            world.getServer().runOnServer(server -> com.framestash.gui.Menus.openContainers(player(server), 0));
            context.waitTicks(5);
            shot(context, "08_nearby");
            context.setScreen(() -> null);

            // 7. Close-up of a few labels for the "how it works" strip.
            hud(context, false);
            camera(context, world, -0.5, Y + 0.4, 1.8, 180f, 8f);
            context.waitTicks(10);
            shot(context, "09_closeup");
        }
    }

    // --- scene ---------------------------------------------------------------------------------

    private static void buildRoom(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // Clear a box and lay a floor.
        for (int x = -9; x <= 9; x++) {
            for (int z = -4; z <= 10; z++) {
                for (int y = Y - 1; y <= Y + 6; y++) {
                    BlockState state = Blocks.AIR.defaultBlockState();
                    if (y == Y - 1) state = ((x + z) & 1) == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                            : Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    if (z == -2 && y < Y + 5) state = Blocks.STONE_BRICKS.defaultBlockState();
                    if (z == -2 && y < Y + 5 && (x == -5 || x == 5)) state = Blocks.SPRUCE_LOG.defaultBlockState();
                    if ((x == -9 || x == 9) && z < 6 && y < Y + 5) state = Blocks.STONE_BRICKS.defaultBlockState();
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        }
        // Light and a bit of decoration.
        for (int x : new int[] {-7, 7}) {
            level.setBlock(new BlockPos(x, Y, -1), Blocks.BARREL.defaultBlockState()
                    .setValue(BarrelBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(x, Y + 1, -1), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int x = -4; x <= 4; x += 4) {
            level.setBlock(new BlockPos(x, Y + 4, -1), Blocks.LANTERN.defaultBlockState()
                    .setValue(LanternBlock.HANGING, false), Block.UPDATE_CLIENTS);
        }
        level.setBlock(new BlockPos(-6, Y, 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(6, Y, 1), Blocks.POTTED_FERN.defaultBlockState(), Block.UPDATE_CLIENTS);

        // The storage wall: columns x = -3..3, barrels on the bottom row, chests on top.
        for (int i = 0; i < 7; i++) {
            int x = i - 3;
            BlockPos bottom = new BlockPos(x, Y, -1);
            BlockPos top = new BlockPos(x, Y + 1, -1);
            level.setBlock(bottom, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.SOUTH),
                    Block.UPDATE_CLIENTS);
            level.setBlock(top, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH),
                    Block.UPDATE_CLIENTS);
            frame(level, top, TOP[i], i == OVERFLOW);
            frame(level, bottom, BOTTOM[i], false);
        }
        // Make the primary stone chest nearly full so the overflow chest is used.
        var primary = (net.minecraft.world.Container) level.getBlockEntity(new BlockPos(-2, Y + 1, -1));
        for (int s = 0; s < 27; s++) {
            primary.setItem(s, new ItemStack(Items.STONE, s < 25 ? 64 : 0));
        }
    }

    private static void frame(ServerLevel level, BlockPos container, Item item, boolean glow) {
        BlockPos at = container.relative(Direction.SOUTH);
        ItemFrame frame = glow ? new GlowItemFrame(level, at, Direction.SOUTH) : new ItemFrame(level, at, Direction.SOUTH);
        frame.setItem(new ItemStack(item));
        frame.setInvulnerable(true);
        level.addFreshEntity(frame);
    }

    private static void fillInventory(MinecraftServer server) {
        Inventory inv = player(server).getInventory();
        inv.clearContent();
        Item[] main = {Items.COBBLESTONE, Items.STONE, Items.OAK_LOG, Items.IRON_INGOT, Items.DIRT, Items.SAND,
                Items.REDSTONE, Items.COAL, Items.WHEAT, Items.GOLD_INGOT, Items.DIAMOND, Items.BONE,
                Items.STONE, Items.COBBLESTONE, Items.ROTTEN_FLESH, Items.GRAVEL, Items.STRING, Items.OAK_LOG,
                Items.COAL, Items.STONE, Items.DIRT, Items.FLINT};
        int[] counts = {64, 64, 23, 41, 37, 64, 30, 52, 18, 12, 5, 9, 64, 33, 14, 21, 8, 40, 16, 64, 20, 6};
        for (int i = 0; i < main.length; i++) {
            inv.setItem(9 + i, new ItemStack(main[i], counts[i]));
        }
        inv.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        inv.setItem(1, new ItemStack(Items.IRON_SWORD));
        inv.setItem(2, new ItemStack(Items.TORCH, 32));
        inv.setItem(3, new ItemStack(Items.BREAD, 12));
        inv.setItem(8, new ItemStack(Items.WATER_BUCKET));
        player(server).inventoryMenu.broadcastChanges();
    }

    // --- camera and capture --------------------------------------------------------------------

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void camera(ClientGameTestContext context, TestSingleplayerContext world,
                               double x, double y, double z, float yaw, float pitch) {
        world.getServer().runOnServer(server -> {
            ServerPlayer p = player(server);
            p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), yaw, pitch, true);
        });
        context.waitTicks(3);
        world.getClientWorld().waitForChunksRender();
    }

    private static void hud(ClientGameTestContext context, boolean visible) {
        context.runOnClient(mc -> mc.options.hideGui = !visible);
    }

    private void openContainer(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
        world.getServer().runOnServer(server -> {
            if (server.overworld().getBlockEntity(pos) instanceof MenuProvider provider) {
                player(server).openMenu(provider);
            }
        });
        context.waitForScreen(ContainerScreen.class);
        context.waitTicks(3);
    }

    private void shot(ClientGameTestContext context, String name) {
        context.getInput().setCursorPos(1900, 1060); // keep the mouse away from slots (no tooltips)
        context.waitTick();
        context.runOnClient(mc -> mc.getToastManager().clear());
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }

    private void frame(ClientGameTestContext context, int index) {
        context.runOnClient(mc -> mc.getToastManager().clear());
        context.takeScreenshot(TestScreenshotOptions.of(String.format("gif_%02d", index))
                .disableCounterPrefix().withDestinationDir(out.resolve("gif")));
    }
}
