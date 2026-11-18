package com.framestash.label;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

/**
 * A container with at least one label, after merging all frames on it (and on both halves of a
 * double chest).
 *
 * @param pos        canonical position; for a double chest, the lower of the two halves
 * @param blocks     every block that makes up the container (one, or two for a double chest)
 * @param distanceSq squared distance from the player to the nearest block centre
 */
public record LabelledContainer(BlockPos pos, List<BlockPos> blocks, double distanceSq,
                                Set<Item> primary, Set<Item> overflow, boolean catchAll) {

    /** Whether a normal or glow frame on this container shows the item. */
    public boolean labels(Item item) {
        return primary.contains(item) || overflow.contains(item);
    }

    public boolean contains(BlockPos block) {
        return blocks.contains(block);
    }
}
