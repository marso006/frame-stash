package com.framestash.label;

import com.framestash.FrameStashMod;
import com.framestash.config.FrameStashConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finds labelled containers around a point by querying item frames. No index, no per-tick work. */
public final class LabelScanner {
    /** Blocks that can be labelled. Data-driven so packs can extend it. */
    public static final TagKey<Block> CONTAINERS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FrameStashMod.MOD_ID, "containers"));

    /** Nearest first, then by position, so the order is the same every time. */
    public static final Comparator<LabelledContainer> ORDER = Comparator
            .comparingDouble(LabelledContainer::distanceSq)
            .thenComparingLong(c -> c.pos().asLong());

    private LabelScanner() {}

    /**
     * Labelled containers within {@code config.radius} of {@code origin}, nearest first, capped at
     * {@code config.maxContainers}.
     */
    public static List<LabelledContainer> scan(ServerLevel level, Vec3 origin, FrameStashConfig config) {
        double radius = config.radius;
        double radiusSq = radius * radius;
        Optional<Item> catchAll = config.catchAll();

        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
                new AABB(origin, origin).inflate(radius + 1.0), frame -> !frame.getItem().isEmpty());

        Map<BlockPos, Builder> byContainer = new HashMap<>();
        for (ItemFrame frame : frames) {
            BlockPos attached = frame.getPos().relative(frame.getDirection().getOpposite());
            if (!level.isLoaded(attached)) continue;
            BlockState state = level.getBlockState(attached);
            if (!state.is(CONTAINERS)) continue;

            List<BlockPos> blocks = blocksOf(level, attached, state);
            double distSq = Double.MAX_VALUE;
            for (BlockPos b : blocks) {
                distSq = Math.min(distSq, Vec3.atCenterOf(b).distanceToSqr(origin));
            }
            if (distSq > radiusSq) continue;

            BlockPos key = blocks.get(0);
            Builder builder = byContainer.computeIfAbsent(key, k -> new Builder(k, blocks));
            builder.distanceSq = Math.min(builder.distanceSq, distSq);

            Label label = toLabel(key, frame, catchAll);
            switch (label.kind()) {
                case PRIMARY -> builder.primary.add(label.item());
                case OVERFLOW -> builder.overflow.add(label.item());
                case CATCH_ALL -> builder.catchAll = true;
            }
        }

        return byContainer.values().stream()
                .map(Builder::build)
                .sorted(ORDER)
                .limit(config.maxContainers)
                .toList();
    }

    static Label toLabel(BlockPos container, ItemFrame frame, Optional<Item> catchAll) {
        Item item = frame.getItem().getItem();
        if (catchAll.isPresent() && catchAll.get() == item) {
            return new Label(container, item, LabelKind.CATCH_ALL);
        }
        return new Label(container, item, frame instanceof GlowItemFrame ? LabelKind.OVERFLOW : LabelKind.PRIMARY);
    }

    /**
     * The blocks that make up the container at {@code pos}, canonical (lowest) position first.
     * A double chest returns both halves, so a frame on either half labels the whole chest.
     */
    public static List<BlockPos> blocksOf(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.isLoaded(other)) {
                BlockState otherState = level.getBlockState(other);
                if (otherState.is(state.getBlock()) && otherState.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                        && other.relative(ChestBlock.getConnectedDirection(otherState)).equals(pos)) {
                    return pos.asLong() < other.asLong()
                            ? List.of(pos.immutable(), other.immutable())
                            : List.of(other.immutable(), pos.immutable());
                }
            }
        }
        return List.of(pos.immutable());
    }

    private static final class Builder {
        final BlockPos pos;
        final List<BlockPos> blocks;
        final Set<Item> primary = new LinkedHashSet<>();
        final Set<Item> overflow = new LinkedHashSet<>();
        boolean catchAll;
        double distanceSq = Double.MAX_VALUE;

        Builder(BlockPos pos, List<BlockPos> blocks) {
            this.pos = pos;
            this.blocks = new ArrayList<>(blocks);
        }

        LabelledContainer build() {
            return new LabelledContainer(pos, List.copyOf(blocks), distanceSq, Set.copyOf(primary),
                    Set.copyOf(overflow), catchAll);
        }
    }
}
