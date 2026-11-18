package com.framestash.action;

import com.framestash.label.LabelledContainer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Plans a restock: every partial stackable stack is topped up from containers labelled for that
 * item, primary before overflow, nearest first. Only identical items (same components) are pulled,
 * so enchanted or renamed stacks are never mixed. Planning runs on copies only.
 */
public final class RestockAction {
    private RestockAction() {}

    public static List<Move> plan(Container playerInventory, int[] slots, List<Target> targets, boolean overflowEnabled) {
        List<ItemStack> inv = Transfers.snapshot(playerInventory);
        List<List<ItemStack>> sims = new ArrayList<>(targets.size());
        for (Target t : targets) {
            sims.add(Transfers.snapshot(t.container()));
        }

        List<Move> moves = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = inv.get(slot);
            if (stack.isEmpty() || !stack.isStackable()) continue;
            int need = playerInventory.getMaxStackSize(stack) - stack.getCount();
            if (need <= 0) continue;

            for (int t : order(stack.getItem(), targets, overflowEnabled)) {
                List<ItemStack> sim = sims.get(t);
                // Take from the end of the container first, which keeps the front tidy.
                for (int cs = sim.size() - 1; cs >= 0 && need > 0; cs--) {
                    ItemStack source = sim.get(cs);
                    if (source.isEmpty() || !ItemStack.isSameItemSameComponents(source, stack)) continue;
                    int n = Math.min(need, source.getCount());
                    source.shrink(n);
                    stack.grow(n);
                    need -= n;
                    moves.add(new Move(slot, t, cs, n, false));
                }
                if (need <= 0) break;
            }
        }
        return moves;
    }

    /** Containers labelled for the item: primary then overflow, each nearest first. Catch-alls are not used. */
    static List<Integer> order(Item item, List<Target> targets, boolean overflowEnabled) {
        Comparator<Integer> byDistance = Comparator
                .<Integer>comparingDouble(i -> targets.get(i).label().distanceSq())
                .thenComparingLong(i -> targets.get(i).label().pos().asLong());
        List<Integer> primary = new ArrayList<>();
        List<Integer> overflow = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            LabelledContainer label = targets.get(i).label();
            if (label.primary().contains(item) || (!overflowEnabled && label.overflow().contains(item))) {
                primary.add(i);
            } else if (label.overflow().contains(item)) {
                overflow.add(i);
            }
        }
        primary.sort(byDistance);
        overflow.sort(byDistance);
        primary.addAll(overflow);
        return primary;
    }
}
