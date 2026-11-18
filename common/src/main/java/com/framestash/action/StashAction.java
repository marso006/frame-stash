package com.framestash.action;

import com.framestash.label.LabelledContainer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Plans a stash: every source stack goes to its primary containers (nearest first, then most free
 * space), then overflow containers, then catch-alls. Planning runs on copies only.
 */
public final class StashAction {
    private StashAction() {}

    public static List<Move> plan(Container playerInventory, int[] sourceSlots, List<Target> targets,
                                  boolean overflowEnabled) {
        List<ItemStack> inv = Transfers.snapshot(playerInventory);
        List<List<ItemStack>> sims = new ArrayList<>(targets.size());
        for (Target t : targets) {
            sims.add(Transfers.snapshot(t.container()));
        }

        List<Move> moves = new ArrayList<>();
        for (int slot : sourceSlots) {
            ItemStack stack = inv.get(slot);
            if (stack.isEmpty()) continue;
            for (int t : order(stack, targets, sims, overflowEnabled)) {
                if (stack.isEmpty()) break;
                fill(slot, stack, t, targets.get(t), sims.get(t), moves);
            }
        }
        return moves;
    }

    /** Merges into matching partial stacks first, then empty slots. Updates the copies. */
    private static void fill(int playerSlot, ItemStack stack, int t, Target target, List<ItemStack> sim, List<Move> moves) {
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            boolean wantEmpty = pass == 1;
            for (int slot = 0; slot < sim.size() && !stack.isEmpty(); slot++) {
                ItemStack inSlot = sim.get(slot);
                if (inSlot.isEmpty() != wantEmpty) continue;
                int n = Math.min(stack.getCount(),
                        Transfers.room(target.container(), target.refusesContainerItems(), slot, inSlot, stack));
                if (n <= 0) continue;
                if (inSlot.isEmpty()) {
                    sim.set(slot, stack.copyWithCount(n));
                } else {
                    inSlot.grow(n);
                }
                stack.shrink(n);
                moves.add(new Move(playerSlot, t, slot, n, true));
            }
        }
    }

    /**
     * Target indices for a stack, in fill order: primary, overflow, catch-all. Within a tier,
     * nearest first, then most free space, then position. With overflow disabled, glow frames count
     * as primary.
     */
    static List<Integer> order(ItemStack stack, List<Target> targets, List<List<ItemStack>> sims, boolean overflowEnabled) {
        Item item = stack.getItem();
        List<Integer> primary = new ArrayList<>();
        List<Integer> overflow = new ArrayList<>();
        List<Integer> catchAll = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            LabelledContainer label = targets.get(i).label();
            if (label.primary().contains(item) || (!overflowEnabled && label.overflow().contains(item))) {
                primary.add(i);
            } else if (label.overflow().contains(item)) {
                overflow.add(i);
            } else if (label.catchAll()) {
                catchAll.add(i);
            }
        }
        Comparator<Integer> byDistanceThenRoom = Comparator
                .<Integer>comparingDouble(i -> targets.get(i).label().distanceSq())
                .thenComparing(i -> -Transfers.totalRoom(targets.get(i).container(),
                        targets.get(i).refusesContainerItems(), sims.get(i), stack))
                .thenComparingLong(i -> targets.get(i).label().pos().asLong());
        primary.sort(byDistanceThenRoom);
        overflow.sort(byDistanceThenRoom);
        catchAll.sort(byDistanceThenRoom);

        Set<Integer> ordered = new LinkedHashSet<>(primary);
        ordered.addAll(overflow);
        ordered.addAll(catchAll);
        return List.copyOf(ordered);
    }
}
