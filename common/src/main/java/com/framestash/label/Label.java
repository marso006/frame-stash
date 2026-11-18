package com.framestash.label;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

/** One item frame's meaning: the container it is attached to, the item it shows, and its kind. */
public record Label(BlockPos container, Item item, LabelKind kind) {}
