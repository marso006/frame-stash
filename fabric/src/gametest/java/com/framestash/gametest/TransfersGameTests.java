package com.framestash.gametest;

import com.framestash.action.*;


import com.framestash.label.LabelledContainer;
import java.util.ArrayList;
import java.util.Objects;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Planner and apply tests on plain inventories, including the item-totals test from the roadmap. */
/**
 * The planner tests from fabric/src/test, run inside the GameTest server: on 26.x item stacks need
 * data components, which are only bound once a world's registries are loaded.
 */
public class TransfersGameTests {
    private static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "values differ");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected instanceof Number e && actual instanceof Number a ? e.longValue() != a.longValue() : !Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("expected true");
    }

    private static final int[] MAIN = IntStream.range(9, 36).toArray();
    private static final int[] ALL = IntStream.range(0, 36).toArray();


    // --- helpers -------------------------------------------------------------------------------

    private static LabelledContainer label(int x, Set<Item> primary, Set<Item> overflow, boolean catchAll) {
        BlockPos pos = new BlockPos(x, 64, 0);
        return new LabelledContainer(pos, List.of(pos), (double) x * x, primary, overflow, catchAll);
    }

    private static Target target(int x, Set<Item> primary, Set<Item> overflow, boolean catchAll) {
        return new Target(label(x, primary, overflow, catchAll), new SimpleContainer(27), false);
    }

    private static SimpleContainer player() {
        return new SimpleContainer(36);
    }

    private static ActionResult stash(Container inv, int[] slots, List<Target> targets, boolean overflow) {
        return Transfers.apply(StashAction.plan(inv, slots, targets, overflow), inv, targets);
    }

    private static ActionResult restock(Container inv, List<Target> targets) {
        return Transfers.apply(RestockAction.plan(inv, ALL, targets, true), inv, targets);
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        }
        return n;
    }

    /** Item counts keyed by item plus components, so a renamed stack counts separately. */
    private static Map<String, Integer> totals(Container inv, List<Target> targets) {
        Map<String, Integer> totals = new HashMap<>();
        List<Container> all = new ArrayList<>();
        all.add(inv);
        targets.forEach(t -> all.add(t.container()));
        for (Container c : all) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) {
                    totals.merge(s.getItem() + "|" + s.getComponentsPatch(), s.getCount(), Integer::sum);
                }
            }
        }
        return totals;
    }

    // --- core behaviour ------------------------------------------------------------------------

    private void threeChestsEachGetTheirItem() {
        List<Target> targets = List.of(
                target(1, Set.of(Items.STONE), Set.of(), false),
                target(2, Set.of(Items.IRON_INGOT), Set.of(), false),
                target(3, Set.of(Items.OAK_LOG), Set.of(), false));
        SimpleContainer inv = player();
        inv.setItem(10, new ItemStack(Items.STONE, 64));
        inv.setItem(11, new ItemStack(Items.IRON_INGOT, 20));
        inv.setItem(12, new ItemStack(Items.OAK_LOG, 5));
        inv.setItem(13, new ItemStack(Items.DIRT, 7)); // unlabelled, no catch-all: stays

        ActionResult result = stash(inv, MAIN, targets, true);

        assertEquals(64, count(targets.get(0).container(), Items.STONE));
        assertEquals(20, count(targets.get(1).container(), Items.IRON_INGOT));
        assertEquals(5, count(targets.get(2).container(), Items.OAK_LOG));
        assertEquals(7, count(inv, Items.DIRT));
        assertEquals(89, result.items());
        assertEquals(3, result.containers().size());
    }

    private void hotbarIsSkippedUnlessIncluded() {
        List<Target> targets = List.of(target(1, Set.of(Items.STONE), Set.of(), false));
        SimpleContainer inv = player();
        inv.setItem(0, new ItemStack(Items.STONE, 10));
        inv.setItem(20, new ItemStack(Items.STONE, 5));

        stash(inv, MAIN, targets, true);
        assertEquals(10, count(inv, Items.STONE));

        stash(inv, ALL, targets, true);
        assertEquals(0, count(inv, Items.STONE));
        assertEquals(15, count(targets.get(0).container(), Items.STONE));
    }

    private void nearestPrimaryFillsFirst() {
        Target near = target(1, Set.of(Items.STONE), Set.of(), false);
        Target far = target(5, Set.of(Items.STONE), Set.of(), false);
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.STONE, 30));

        stash(inv, MAIN, List.of(far, near), true);

        assertEquals(30, count(near.container(), Items.STONE));
        assertEquals(0, count(far.container(), Items.STONE));
    }

    private void overflowOnlyAfterEveryPrimaryIsFull() {
        Target primaryFar = target(6, Set.of(Items.STONE), Set.of(), false);
        Target overflowNear = target(1, Set.of(), Set.of(Items.STONE), false);
        // Fill the primary except for 10 items of room.
        for (int i = 0; i < 27; i++) primaryFar.container().setItem(i, new ItemStack(Items.DIRT, 64));
        primaryFar.container().setItem(26, new ItemStack(Items.STONE, 54)); // 64 max -> 10 room
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.STONE, 64));

        stash(inv, MAIN, List.of(primaryFar, overflowNear), true);

        assertEquals(64, count(primaryFar.container(), Items.STONE));
        assertEquals(54, count(overflowNear.container(), Items.STONE));
    }

    private void overflowDisabledTreatsGlowFramesAsPrimary() {
        Target overflowNear = target(1, Set.of(), Set.of(Items.STONE), false);
        Target primaryFar = target(6, Set.of(Items.STONE), Set.of(), false);
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.STONE, 20));

        stash(inv, MAIN, List.of(primaryFar, overflowNear), false);

        assertEquals(20, count(overflowNear.container(), Items.STONE));
    }

    private void catchAllTakesUnlabelledItems() {
        Target stone = target(1, Set.of(Items.STONE), Set.of(), false);
        Target bin = target(2, Set.of(), Set.of(), true);
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.STONE, 10));
        inv.setItem(10, new ItemStack(Items.DIRT, 10));
        inv.setItem(11, new ItemStack(Items.DIAMOND_SWORD));

        stash(inv, MAIN, List.of(stone, bin), true);

        assertEquals(10, count(stone.container(), Items.STONE));
        assertEquals(0, count(bin.container(), Items.STONE));
        assertEquals(10, count(bin.container(), Items.DIRT));
        assertEquals(1, count(bin.container(), Items.DIAMOND_SWORD));
    }

    private void shulkerBoxRefusesShulkerBoxes() {
        Target shulker = new Target(label(1, Set.of(Items.SHULKER_BOX, Items.STONE), Set.of(), false),
                new SimpleContainer(27), true);
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.SHULKER_BOX));
        inv.setItem(10, new ItemStack(Items.STONE, 3));

        stash(inv, MAIN, List.of(shulker), true);

        assertEquals(1, count(inv, Items.SHULKER_BOX));
        assertEquals(3, count(shulker.container(), Items.STONE));
    }

    private void stashMergesWithExistingStacksBeforeEmptySlots() {
        Target t = target(1, Set.of(Items.STONE), Set.of(), false);
        t.container().setItem(5, new ItemStack(Items.STONE, 54));
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.STONE, 20));

        stash(inv, MAIN, List.of(t), true);

        assertEquals(64, t.container().getItem(5).getCount());
        assertEquals(10, t.container().getItem(0).getCount());
    }

    private void renamedItemsDoNotMergeIntoPlainStacks() {
        Target t = target(1, Set.of(Items.STONE), Set.of(), false);
        for (int i = 0; i < 27; i++) t.container().setItem(i, new ItemStack(Items.STONE, 50));
        SimpleContainer inv = player();
        ItemStack named = new ItemStack(Items.STONE, 5);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Special"));
        inv.setItem(9, named);

        stash(inv, MAIN, List.of(t), true);

        assertEquals(5, inv.getItem(9).getCount(), "no empty slot and no identical stack, so it stays");
    }

    // --- restock -------------------------------------------------------------------------------

    private void restockTopsUpFromNearestMatchingContainer() {
        Target near = target(1, Set.of(Items.STONE), Set.of(), false);
        Target far = target(4, Set.of(Items.STONE), Set.of(), false);
        near.container().setItem(0, new ItemStack(Items.STONE, 30));
        far.container().setItem(0, new ItemStack(Items.STONE, 64));
        SimpleContainer inv = player();
        inv.setItem(0, new ItemStack(Items.STONE, 10)); // hotbar, needs 54 to reach 64

        restock(inv, List.of(far, near));

        assertEquals(64, inv.getItem(0).getCount());
        assertEquals(0, count(near.container(), Items.STONE));
        assertEquals(40, count(far.container(), Items.STONE));
    }

    private void restockDoesNothingWithoutMatch() {
        Target t = target(1, Set.of(Items.STONE), Set.of(), false);
        t.container().setItem(0, new ItemStack(Items.DIRT, 64)); // unlabelled item in the chest
        Target bin = target(2, Set.of(), Set.of(), true);
        bin.container().setItem(0, new ItemStack(Items.DIRT, 64)); // catch-alls are not used
        SimpleContainer inv = player();
        inv.setItem(0, new ItemStack(Items.DIRT, 1));
        inv.setItem(1, new ItemStack(Items.DIAMOND_SWORD)); // unstackable

        ActionResult result = restock(inv, List.of(t, bin));

        assertEquals(0, result.items());
        assertEquals(1, inv.getItem(0).getCount());
    }

    private void restockNeverMixesComponents() {
        Target t = target(1, Set.of(Items.STONE), Set.of(), false);
        ItemStack named = new ItemStack(Items.STONE, 64);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Special"));
        t.container().setItem(0, named);
        SimpleContainer inv = player();
        inv.setItem(0, new ItemStack(Items.STONE, 1));

        restock(inv, List.of(t));

        assertEquals(1, inv.getItem(0).getCount());
        assertEquals(64, t.container().getItem(0).getCount());
    }

    // --- safety --------------------------------------------------------------------------------

    private void totalsMatchOverFiftyRandomRuns() {
        Item[] pool = {Items.STONE, Items.IRON_INGOT, Items.OAK_LOG, Items.DIRT, Items.ENDER_PEARL,
                Items.DIAMOND_SWORD, Items.SHULKER_BOX, Items.EGG, Items.COBBLESTONE};
        Random random = new Random(12345);

        for (int run = 0; run < 50; run++) {
            List<Target> targets = new ArrayList<>();
            int containerCount = 1 + random.nextInt(6);
            for (int c = 0; c < containerCount; c++) {
                Set<Item> primary = random.nextBoolean() ? Set.of(pool[random.nextInt(pool.length)]) : Set.of();
                Set<Item> overflow = random.nextInt(3) == 0 ? Set.of(pool[random.nextInt(pool.length)]) : Set.of();
                Target t = new Target(label(1 + random.nextInt(4), primary, overflow, random.nextInt(4) == 0),
                        new SimpleContainer(9 + random.nextInt(19)), random.nextInt(5) == 0);
                fillRandom(t.container(), pool, random, 0.4);
                targets.add(t);
            }
            SimpleContainer inv = player();
            fillRandom(inv, pool, random, 0.7);

            Map<String, Integer> before = totals(inv, targets);
            stash(inv, random.nextBoolean() ? MAIN : ALL, targets, random.nextBoolean());
            assertEquals(before, totals(inv, targets), "stash changed item totals in run " + run);
            restock(inv, targets);
            assertEquals(before, totals(inv, targets), "restock changed item totals in run " + run);
        }
    }

    private void applyRevalidatesWhenSlotsChangeAfterPlanning() {
        Target t = target(1, Set.of(Items.STONE), Set.of(), false);
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.STONE, 40));
        List<Target> targets = List.of(t);
        List<Move> moves = StashAction.plan(inv, MAIN, targets, true);

        // Someone fills the planned slot with something else and takes half the player's stack.
        t.container().setItem(0, new ItemStack(Items.DIRT, 64));
        inv.getItem(9).shrink(20);
        Map<String, Integer> before = totals(inv, targets);

        Transfers.apply(moves, inv, targets);

        assertEquals(before, totals(inv, targets));
        assertEquals(64, count(t.container(), Items.DIRT));
    }

    private static void fillRandom(Container c, Item[] pool, Random random, double density) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (random.nextDouble() >= density) continue;
            Item item = pool[random.nextInt(pool.length)];
            ItemStack stack = new ItemStack(item);
            stack.setCount(1 + random.nextInt(stack.getMaxStackSize()));
            if (random.nextInt(10) == 0) stack.set(DataComponents.CUSTOM_NAME, Component.literal("Named"));
            c.setItem(i, stack);
        }
    }

    // --- compatibility with stack-size mods ------------------------------------------------------

    /** Imitates a big-stacks mod that patches containers to allow huge stacks of anything. */
    private static SimpleContainer big(int size, int limit) {
        return new SimpleContainer(size) {
            @Override
            public int getMaxStackSize() {
                return limit;
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return limit;
            }
        };
    }

    private void bigStackModLimitsAreUsedForStash() {
        Target t = new Target(label(1, Set.of(Items.STONE), Set.of(), false), big(27, 4096), false);
        SimpleContainer inv = big(36, 4096);
        inv.setItem(9, new ItemStack(Items.STONE, 3000));
        inv.setItem(10, new ItemStack(Items.STONE, 3000));
        Map<String, Integer> before = totals(inv, List.of(t));

        stash(inv, MAIN, List.of(t), true);

        assertEquals(4096, t.container().getItem(0).getCount());
        assertEquals(1904, t.container().getItem(1).getCount());
        assertEquals(before, totals(inv, List.of(t)));
    }

    private void bigStackModLimitsAreUsedForRestock() {
        Target t = new Target(label(1, Set.of(Items.STONE), Set.of(), false), big(27, 4096), false);
        t.container().setItem(0, new ItemStack(Items.STONE, 4000));
        t.container().setItem(1, new ItemStack(Items.STONE, 1000));
        SimpleContainer inv = big(36, 4096);
        inv.setItem(0, new ItemStack(Items.STONE, 10));

        restock(inv, List.of(t));

        assertEquals(4096, inv.getItem(0).getCount());
        assertEquals(914, count(t.container(), Items.STONE));
    }

    private void componentRaisedStackSizeIsRespected() {
        // Data packs and some mods raise the max_stack_size component (vanilla allows up to 99).
        ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 60);
        pearls.set(DataComponents.MAX_STACK_SIZE, 99);
        Target t = target(1, Set.of(Items.ENDER_PEARL), Set.of(), false);
        ItemStack inChest = pearls.copyWithCount(50);
        t.container().setItem(0, inChest);
        SimpleContainer inv = player();
        inv.setItem(9, pearls);

        stash(inv, MAIN, List.of(t), true);

        assertEquals(99, t.container().getItem(0).getCount());
        assertEquals(11, t.container().getItem(1).getCount());
    }

    private void cappedStacksRespectContainerLimit() {
        SimpleContainer small = new SimpleContainer(3) {
            @Override
            public int getMaxStackSize() {
                return 16;
            }
        };
        Target t = new Target(label(1, Set.of(Items.STONE), Set.of(), false), small, false);
        SimpleContainer inv = player();
        inv.setItem(9, new ItemStack(Items.STONE, 64));

        stash(inv, MAIN, List.of(t), true);

        assertEquals(48, count(small, Items.STONE));
        assertEquals(16, inv.getItem(9).getCount());
        for (int i = 0; i < 3; i++) assertTrue(small.getItem(i).getCount() <= 16);
    }

    @GameTest
    public void threeChestsEachGetTheirItem(GameTestHelper h) {
        threeChestsEachGetTheirItem();
        h.succeed();
    }

    @GameTest
    public void hotbarIsSkippedUnlessIncluded(GameTestHelper h) {
        hotbarIsSkippedUnlessIncluded();
        h.succeed();
    }

    @GameTest
    public void nearestPrimaryFillsFirst(GameTestHelper h) {
        nearestPrimaryFillsFirst();
        h.succeed();
    }

    @GameTest
    public void overflowOnlyAfterEveryPrimaryIsFull(GameTestHelper h) {
        overflowOnlyAfterEveryPrimaryIsFull();
        h.succeed();
    }

    @GameTest
    public void overflowDisabledTreatsGlowFramesAsPrimary(GameTestHelper h) {
        overflowDisabledTreatsGlowFramesAsPrimary();
        h.succeed();
    }

    @GameTest
    public void catchAllTakesUnlabelledItems(GameTestHelper h) {
        catchAllTakesUnlabelledItems();
        h.succeed();
    }

    @GameTest
    public void shulkerBoxRefusesShulkerBoxes(GameTestHelper h) {
        shulkerBoxRefusesShulkerBoxes();
        h.succeed();
    }

    @GameTest
    public void stashMergesWithExistingStacksBeforeEmptySlots(GameTestHelper h) {
        stashMergesWithExistingStacksBeforeEmptySlots();
        h.succeed();
    }

    @GameTest
    public void renamedItemsDoNotMergeIntoPlainStacks(GameTestHelper h) {
        renamedItemsDoNotMergeIntoPlainStacks();
        h.succeed();
    }

    @GameTest
    public void restockTopsUpFromNearestMatchingContainer(GameTestHelper h) {
        restockTopsUpFromNearestMatchingContainer();
        h.succeed();
    }

    @GameTest
    public void restockDoesNothingWithoutMatch(GameTestHelper h) {
        restockDoesNothingWithoutMatch();
        h.succeed();
    }

    @GameTest
    public void restockNeverMixesComponents(GameTestHelper h) {
        restockNeverMixesComponents();
        h.succeed();
    }

    @GameTest
    public void totalsMatchOverFiftyRandomRuns(GameTestHelper h) {
        totalsMatchOverFiftyRandomRuns();
        h.succeed();
    }

    @GameTest
    public void applyRevalidatesWhenSlotsChangeAfterPlanning(GameTestHelper h) {
        applyRevalidatesWhenSlotsChangeAfterPlanning();
        h.succeed();
    }

    @GameTest
    public void bigStackModLimitsAreUsedForStash(GameTestHelper h) {
        bigStackModLimitsAreUsedForStash();
        h.succeed();
    }

    @GameTest
    public void bigStackModLimitsAreUsedForRestock(GameTestHelper h) {
        bigStackModLimitsAreUsedForRestock();
        h.succeed();
    }

    @GameTest
    public void componentRaisedStackSizeIsRespected(GameTestHelper h) {
        componentRaisedStackSizeIsRespected();
        h.succeed();
    }

    @GameTest
    public void cappedStacksRespectContainerLimit(GameTestHelper h) {
        cappedStacksRespectContainerLimit();
        h.succeed();
    }
}
