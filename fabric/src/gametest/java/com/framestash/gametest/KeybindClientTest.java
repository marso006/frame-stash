package com.framestash.gametest;

import com.framestash.client.FrameStashClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;

/**
 * Presses the real Stash and Restock keys in a real client: once in the world, once with the
 * inventory open. Checks the chests on the integrated server afterwards.
 */
public class KeybindClientTest implements FabricClientGameTest {
    private static final BlockPos STONE_CHEST = new BlockPos(0, 100, -3);
    private static final BlockPos TORCH_CHEST = new BlockPos(1, 100, -3);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runOnServer(KeybindClientTest::setup);
            context.waitTicks(10);

            // 1. G in the world stashes.
            context.getInput().pressKey(FrameStashClient.stash);
            context.waitTicks(10);
            check(context, world, STONE_CHEST, Items.STONE, 64, "stone after pressing Stash in the world");

            // 2. G with the inventory open stashes too (quick stack).
            world.getServer().runOnServer(s -> {
                player(s).getInventory().setItem(10, new ItemStack(Items.STONE, 20));
                player(s).inventoryMenu.broadcastChanges();
            });
            context.waitTicks(25); // server cooldown
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(2);
            context.getInput().pressKey(FrameStashClient.stash);
            context.waitTicks(10);
            context.setScreen(() -> null);
            check(context, world, STONE_CHEST, Items.STONE, 84, "stone after pressing Stash in the inventory");

            // 3. H restocks the hotbar torches.
            context.waitTicks(25);
            context.getInput().pressKey(FrameStashClient.restock);
            context.waitTicks(10);
            int torches = world.getServer().computeOnServer(s -> player(s).getInventory().getItem(0).getCount());
            if (torches != 64) {
                throw new AssertionError("torches after pressing Restock: expected 64 but was " + torches);
            }
        }
    }

    private static void setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = player(server);
        // A floor so the player doesn't fall out of range between key presses.
        for (int x = -3; x <= 3; x++) {
            for (int z = -4; z <= 3; z++) {
                level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        player.teleportTo(level, 0.5, 100, 0.5, java.util.Set.of(), 180f, 10f, true);
        level.setBlock(STONE_CHEST.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(TORCH_CHEST.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(STONE_CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        level.setBlock(TORCH_CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        frame(level, STONE_CHEST, Items.STONE);
        frame(level, TORCH_CHEST, Items.TORCH);
        ((Container) level.getBlockEntity(TORCH_CHEST)).setItem(0, new ItemStack(Items.TORCH, 64));

        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(0, new ItemStack(Items.TORCH, 3));
        player.inventoryMenu.broadcastChanges();
    }

    private static void frame(ServerLevel level, BlockPos container, Item item) {
        ItemFrame frame = new ItemFrame(level, container.relative(Direction.SOUTH), Direction.SOUTH);
        frame.setItem(new ItemStack(item));
        level.addFreshEntity(frame);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void check(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos,
                              Item item, int expected, String what) {
        int actual = world.getServer().computeOnServer(server -> {
            Container c = (Container) server.overworld().getBlockEntity(pos);
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
            }
            return n;
        });
        if (actual != expected) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }
}
