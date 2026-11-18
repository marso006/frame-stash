package com.framestash.action;

import com.framestash.label.LabelledContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * A labelled container that passed every access check, with the inventory to move items through.
 *
 * @param refusesContainerItems true for shulker boxes, which must not receive items that cannot
 *                              fit inside container items (shulker boxes, bundles of them, ...)
 */
public record Target(LabelledContainer label, Container container, boolean refusesContainerItems) {
    public static Target of(LabelledContainer label, Container container) {
        return new Target(label, container, container instanceof ShulkerBoxBlockEntity);
    }
}
