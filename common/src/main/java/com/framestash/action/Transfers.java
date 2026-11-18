package com.framestash.action;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Shared helpers for planning (on copies) and applying (on the real inventories) moves.
 *
 * <p>Apply never trusts the plan blindly: each move re-checks the live slots and moves at most what
 * fits, so an item is only ever removed from one stack by the amount added to another. Item totals
 * are conserved by construction.
 *
 * <p>Stack limits always come from the live container and the stack itself
 * ({@link Container#getMaxStackSize(ItemStack)}), never a hard-coded 64, so mods that raise stack
 * sizes (item components, or mixins on containers and items) are respected automatically.
 */
public final class Transfers {
    private Transfers() {}

    /** Copies every slot of a container so planning never touches real stacks. */
    static List<ItemStack> snapshot(Container container) {
        List<ItemStack> copy = new ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) {
            copy.add(container.getItem(i).copy());
        }
        return copy;
    }

    /**
     * How many of {@code stack} the container slot can take, given what is currently in it.
     * Follows the container's own rules: {@code canPlaceItem}, its stack limit, and the shulker box rule.
     */
    @SuppressWarnings("deprecation") // NeoForge prefers a stack-aware overload; the vanilla rule is what we want
    static int room(Container container, boolean refusesContainerItems, int slot, ItemStack inSlot, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (refusesContainerItems && !stack.getItem().canFitInsideContainerItems()) return 0;
        if (!container.canPlaceItem(slot, stack)) return 0;
        if (inSlot.isEmpty()) {
            return container.getMaxStackSize(stack);
        }
        if (!ItemStack.isSameItemSameComponents(inSlot, stack)) return 0;
        return Math.max(0, container.getMaxStackSize(inSlot) - inSlot.getCount());
    }

    /** Total room for {@code stack} across a snapshot. Used to break distance ties by free space. */
    static long totalRoom(Container container, boolean refusesContainerItems, List<ItemStack> sim, ItemStack stack) {
        long total = 0;
        for (int i = 0; i < sim.size(); i++) {
            total += room(container, refusesContainerItems, i, sim.get(i), stack);
        }
        return total;
    }

    /**
     * Applies planned moves to the real inventories, re-validating each one, then marks every
     * changed container so comparators and viewers update.
     */
    public static ActionResult apply(List<Move> moves, Container playerInventory, List<Target> targets) {
        long moved = 0;
        Set<Integer> touched = new LinkedHashSet<>();
        for (Move move : moves) {
            Target target = targets.get(move.target());
            int n = move.intoContainer()
                    ? transfer(playerInventory, move.playerSlot(), target.container(), move.targetSlot(),
                            move.count(), target.refusesContainerItems())
                    : transfer(target.container(), move.targetSlot(), playerInventory, move.playerSlot(),
                            move.count(), false);
            if (n > 0) {
                moved += n;
                touched.add(move.target());
            }
        }
        if (moved > 0) {
            playerInventory.setChanged();
        }
        List<com.framestash.label.LabelledContainer> containers = new ArrayList<>(touched.size());
        for (int index : touched) {
            Target target = targets.get(index);
            target.container().setChanged();
            containers.add(target.label());
        }
        return new ActionResult(moved, containers);
    }

    /** Moves up to {@code count} items between two live slots. Returns how many actually moved. */
    static int transfer(Container from, int fromSlot, Container to, int toSlot, int count, boolean toRefusesContainerItems) {
        ItemStack src = from.getItem(fromSlot);
        if (src.isEmpty() || count <= 0) return 0;
        ItemStack dst = to.getItem(toSlot);
        int n = Math.min(count, Math.min(src.getCount(), room(to, toRefusesContainerItems, toSlot, dst, src)));
        if (n <= 0) return 0;
        if (dst.isEmpty()) {
            to.setItem(toSlot, src.split(n));
        } else {
            dst.grow(n);
            src.shrink(n);
        }
        if (src.isEmpty()) {
            from.setItem(fromSlot, ItemStack.EMPTY);
        }
        return n;
    }
}
