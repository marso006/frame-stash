package com.framestash.gametest;

import com.framestash.FrameStashMod;
import com.framestash.action.ActionResult;
import com.framestash.action.Actions;
import com.framestash.config.FrameStashConfig;
import com.framestash.gui.Menus;
import com.framestash.gui.StashMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/**
 * In-world checks for the roadmap's test plan: real chests, item frames, block entities and a mock
 * server player. Each test builds its setup in an empty 8x8x8 area, runs one action and asserts.
 */
public class FrameStashGameTests {

    // --- helpers -------------------------------------------------------------------------------

    private static void frame(GameTestHelper h, BlockPos container, Direction face, Item item, boolean glow) {
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(container).relative(face);
        ItemFrame frame = glow ? new GlowItemFrame(level, at, face) : new ItemFrame(level, at, face);
        frame.setItem(new ItemStack(item));
        level.addFreshEntity(frame);
    }

    @SuppressWarnings("removal") // still the only mock-player helper on 1.21.10
    private static ServerPlayer player(GameTestHelper h, BlockPos standAt) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        Vec3 pos = Vec3.atBottomCenterOf(h.absolutePos(standAt));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static Container chest(GameTestHelper h, BlockPos pos) {
        return (Container) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        }
        return n;
    }

    private static void check(GameTestHelper h, boolean ok, String message) {
        h.assertTrue(ok, Component.literal(message));
    }

    private static void eq(GameTestHelper h, long expected, long actual, String what) {
        h.assertTrue(expected == actual, Component.literal(what + ": expected " + expected + " but was " + actual));
    }

    private static FrameStashConfig config() {
        return FrameStashMod.config();
    }

    // --- core behaviour ------------------------------------------------------------------------

    @GameTest
    public void threeChestsEachGetTheirItem(GameTestHelper h) {
        BlockPos stone = new BlockPos(1, 1, 2), iron = new BlockPos(3, 1, 2), log = new BlockPos(5, 1, 2);
        for (BlockPos p : new BlockPos[] {stone, iron, log}) h.setBlock(p, Blocks.CHEST);
        frame(h, stone, Direction.NORTH, Items.STONE, false);
        frame(h, iron, Direction.NORTH, Items.IRON_INGOT, false);
        frame(h, log, Direction.NORTH, Items.OAK_LOG, false);

        ServerPlayer player = player(h, new BlockPos(3, 1, 5));
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(10, new ItemStack(Items.IRON_INGOT, 20));
        player.getInventory().setItem(11, new ItemStack(Items.OAK_LOG, 5));
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 3)); // hotbar: kept by default

        ActionResult result = Actions.stash(player, config());

        eq(h, 64, count(chest(h, stone), Items.STONE), "stone chest");
        eq(h, 20, count(chest(h, iron), Items.IRON_INGOT), "iron chest");
        eq(h, 5, count(chest(h, log), Items.OAK_LOG), "log chest");
        eq(h, 3, player.getInventory().getItem(0).getCount(), "hotbar stone");
        eq(h, 3, result.containers().size(), "containers touched");
        h.succeed();
    }

    @GameTest
    public void frameOnOneHalfLabelsWholeDoubleChest(GameTestHelper h) {
        BlockPos left = new BlockPos(2, 1, 2), right = new BlockPos(3, 1, 2);
        h.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT));
        h.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        // Fill the labelled half completely so items must go into the other half.
        Container labelledHalf = chest(h, right);
        for (int i = 0; i < 27; i++) labelledHalf.setItem(i, new ItemStack(Items.DIRT, 64));
        frame(h, right, Direction.NORTH, Items.COBBLESTONE, false);

        ServerPlayer player = player(h, new BlockPos(2, 1, 5));
        player.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 40));

        Actions.stash(player, config());

        eq(h, 40, count(chest(h, left), Items.COBBLESTONE), "cobblestone in the unlabelled half");
        h.succeed();
    }

    @GameTest
    public void barrelAndShulkerBoxWork(GameTestHelper h) {
        BlockPos barrel = new BlockPos(2, 1, 2), shulker = new BlockPos(5, 1, 2);
        h.setBlock(barrel, Blocks.BARREL);
        h.setBlock(shulker, Blocks.SHULKER_BOX);
        frame(h, barrel, Direction.NORTH, Items.REDSTONE, false);
        frame(h, shulker, Direction.NORTH, Items.SHULKER_BOX, false);
        frame(h, shulker, Direction.EAST, Items.GLOWSTONE_DUST, false);

        ServerPlayer player = player(h, new BlockPos(3, 1, 5));
        player.getInventory().setItem(9, new ItemStack(Items.REDSTONE, 30));
        player.getInventory().setItem(10, new ItemStack(Items.GLOWSTONE_DUST, 7));
        player.getInventory().setItem(11, new ItemStack(Items.SHULKER_BOX));

        Actions.stash(player, config());

        eq(h, 30, count(chest(h, barrel), Items.REDSTONE), "barrel redstone");
        eq(h, 7, count(chest(h, shulker), Items.GLOWSTONE_DUST), "shulker glowstone");
        eq(h, 0, count(chest(h, shulker), Items.SHULKER_BOX), "shulker box inside shulker box");
        eq(h, 1, player.getInventory().getItem(11).getCount(), "shulker box kept by player");
        h.succeed();
    }

    @GameTest
    public void glowFrameIsOverflowOnly(GameTestHelper h) {
        BlockPos primary = new BlockPos(1, 1, 2), overflow = new BlockPos(3, 1, 2);
        h.setBlock(primary, Blocks.CHEST);
        h.setBlock(overflow, Blocks.CHEST);
        frame(h, primary, Direction.NORTH, Items.SAND, false);
        frame(h, overflow, Direction.NORTH, Items.SAND, true);
        Container primaryChest = chest(h, primary);
        for (int i = 0; i < 26; i++) primaryChest.setItem(i, new ItemStack(Items.DIRT, 64));

        // Overflow is nearer, but must wait until the primary is full.
        ServerPlayer player = player(h, new BlockPos(4, 1, 4));
        player.getInventory().setItem(9, new ItemStack(Items.SAND, 64));
        player.getInventory().setItem(10, new ItemStack(Items.SAND, 10));

        Actions.stash(player, config());

        eq(h, 64, count(primaryChest, Items.SAND), "sand in primary");
        eq(h, 10, count(chest(h, overflow), Items.SAND), "sand in overflow");
        h.succeed();
    }

    @GameTest
    public void hopperFrameIsCatchAll(GameTestHelper h) {
        BlockPos bin = new BlockPos(2, 1, 2);
        h.setBlock(bin, Blocks.CHEST);
        frame(h, bin, Direction.NORTH, Items.HOPPER, false);
        ServerPlayer player = player(h, new BlockPos(2, 1, 5));
        player.getInventory().setItem(9, new ItemStack(Items.ROTTEN_FLESH, 12));

        String before = config().catchAllItem;
        try {
            Actions.stash(player, config());
            eq(h, 12, count(chest(h, bin), Items.ROTTEN_FLESH), "catch-all flesh");
        } finally {
            config().catchAllItem = before;
        }
        h.succeed();
    }

    @GameTest
    public void restockTopsUpFromNearest(GameTestHelper h) {
        BlockPos near = new BlockPos(2, 1, 3), far = new BlockPos(6, 1, 1);
        h.setBlock(near, Blocks.CHEST);
        h.setBlock(far, Blocks.CHEST);
        frame(h, near, Direction.NORTH, Items.TORCH, false);
        frame(h, far, Direction.NORTH, Items.TORCH, false);
        chest(h, near).setItem(0, new ItemStack(Items.TORCH, 20));
        chest(h, far).setItem(0, new ItemStack(Items.TORCH, 64));

        ServerPlayer player = player(h, new BlockPos(2, 1, 5));
        player.getInventory().setItem(0, new ItemStack(Items.TORCH, 4));
        player.getInventory().setItem(1, new ItemStack(Items.BREAD, 3)); // no matching container

        Actions.restock(player, config());

        eq(h, 64, player.getInventory().getItem(0).getCount(), "torches after restock");
        eq(h, 0, count(chest(h, near), Items.TORCH), "near chest emptied first");
        eq(h, 24, count(chest(h, far), Items.TORCH), "far chest gave the rest");
        eq(h, 3, player.getInventory().getItem(1).getCount(), "bread untouched");
        h.succeed();
    }

    // --- safety --------------------------------------------------------------------------------

    @GameTest
    public void lockedChestIsSkipped(GameTestHelper h) {
        BlockPos locked = new BlockPos(2, 1, 2);
        h.setBlock(locked, Blocks.CHEST);
        ItemStack lockSource = new ItemStack(Items.CHEST);
        lockSource.set(DataComponents.LOCK, new LockCode(ItemPredicate.Builder.item()
                .of(h.getLevel().registryAccess().lookupOrThrow(Registries.ITEM), Items.DIAMOND).build()));
        h.getLevel().getBlockEntity(h.absolutePos(locked)).applyComponentsFromItemStack(lockSource);
        frame(h, locked, Direction.NORTH, Items.STONE, false);

        ServerPlayer player = player(h, new BlockPos(2, 1, 5));
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 10));

        Actions.stash(player, config());

        eq(h, 0, count(chest(h, locked), Items.STONE), "stone in locked chest");
        h.succeed();
    }

    @GameTest
    public void blockedChestIsSkipped(GameTestHelper h) {
        BlockPos blocked = new BlockPos(2, 1, 2), open = new BlockPos(5, 1, 2);
        h.setBlock(blocked, Blocks.CHEST);
        h.setBlock(blocked.above(), Blocks.STONE);
        h.setBlock(open, Blocks.CHEST);
        frame(h, blocked, Direction.NORTH, Items.APPLE, false);
        frame(h, open, Direction.NORTH, Items.APPLE, false);

        // The blocked chest is nearer; it must be skipped in favour of the open one.
        ServerPlayer player = player(h, new BlockPos(2, 1, 5));
        player.getInventory().setItem(9, new ItemStack(Items.APPLE, 10));

        Actions.stash(player, config());

        eq(h, 0, count(chest(h, blocked), Items.APPLE), "apples in blocked chest");
        eq(h, 10, count(chest(h, open), Items.APPLE), "apples in open chest");
        h.succeed();
    }

    @GameTest
    public void containerBeyondRadiusIsSkipped(GameTestHelper h) {
        BlockPos far = new BlockPos(1, 1, 1);
        h.setBlock(far, Blocks.CHEST);
        frame(h, far, Direction.SOUTH, Items.FEATHER, false);
        ServerPlayer player = player(h, new BlockPos(7, 1, 7)); // about 8.5 blocks away
        player.getInventory().setItem(9, new ItemStack(Items.FEATHER, 5));

        int before = config().radius;
        config().radius = 4;
        try {
            Actions.stash(player, config());
        } finally {
            config().radius = before;
        }
        eq(h, 0, count(chest(h, far), Items.FEATHER), "feathers beyond radius");
        h.succeed();
    }

    @GameTest
    public void removedFrameStopsLabelling(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, Blocks.CHEST);
        frame(h, pos, Direction.NORTH, Items.STRING, false);
        ServerPlayer player = player(h, new BlockPos(2, 1, 5));
        player.getInventory().setItem(9, new ItemStack(Items.STRING, 5));

        h.getLevel().getEntitiesOfClass(ItemFrame.class, new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(2))
                .forEach(f -> f.discard());
        Actions.stash(player, config());

        eq(h, 0, count(chest(h, pos), Items.STRING), "string after frame removed");
        h.succeed();
    }

    // --- menu ----------------------------------------------------------------------------------

    @GameTest
    public void menuStashButtonWorksAndIconsCannotBeTaken(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, Blocks.CHEST);
        frame(h, pos, Direction.NORTH, Items.CLAY_BALL, false);
        ServerPlayer player = player(h, new BlockPos(2, 1, 5));
        player.getInventory().setItem(9, new ItemStack(Items.CLAY_BALL, 16));

        Menus.openMain(player);
        check(h, player.containerMenu instanceof StashMenu, "menu should be open");
        StashMenu menu = (StashMenu) player.containerMenu;

        // Try to take an icon and to shift-click a player stack into the menu.
        ItemStack icon = menu.getSlot(15).getItem().copy();
        menu.clicked(15, 0, ContainerInput.PICKUP, player);
        check(h, menu.getCarried().isEmpty(), "icon must not be picked up");
        check(h, ItemStack.matches(icon, menu.getSlot(15).getItem()), "icon must stay in place");
        int playerSlotInMenu = 27 + 0; // first main-inventory row (inventory slot 9)
        menu.clicked(playerSlotInMenu, 0, ContainerInput.QUICK_MOVE, player);
        eq(h, 16, player.getInventory().getItem(9).getCount(), "shift-click must not move items");

        // Press Stash.
        menu.clicked(10, 0, ContainerInput.PICKUP, player);
        eq(h, 16, count(chest(h, pos), Items.CLAY_BALL), "clay stashed from the menu");
        player.closeContainer();
        h.succeed();
    }
}
