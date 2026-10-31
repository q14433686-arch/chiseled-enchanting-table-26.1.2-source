package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Selected Efficiency/Silk Touch designs; all changes go through the player's normal actions. */
public final class PrecisionConstruction {
    public static final String SLICE = "vein_peel", LINE = "velvet_shear";
    private static final String EFFICIENCY = "minecraft:efficiency", SILK = "minecraft:silk_touch";
    private static final Set<FusionCarrier> TOOLS = Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE,
        FusionCarrier.SHOVEL, FusionCarrier.HOE, FusionCarrier.SHEARS);
    private static final long SAMPLE_LIFETIME = 1_200L;

    public enum Plane {
        OFF("message.enchantment_fusion.slice_off", new Direction[]{}),
        HORIZONTAL("message.enchantment_fusion.slice_horizontal", new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}),
        EAST_WEST("message.enchantment_fusion.slice_east_west", new Direction[]{Direction.UP, Direction.DOWN, Direction.WEST, Direction.EAST}),
        NORTH_SOUTH("message.enchantment_fusion.slice_north_south", new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH});
        final String key;
        final Direction[] neighbors;
        Plane(String key, Direction[] neighbors) { this.key = key; this.neighbors = neighbors; }
        Plane next() { return values()[(ordinal() + 1) % values().length]; }
    }
    private record SliceMode(ItemStack tool, ResourceKey<Level> dimension, Plane plane) {}
    private record Sample(ItemStack tool, ResourceKey<Level> dimension, BlockState state, long tick) {}
    private static final Map<UUID, SliceMode> MODES = new HashMap<>();
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private PrecisionConstruction() {}

    public static boolean isSlice(@Nullable FusedPair pair) {
        return pair != null && EFFICIENCY.equals(pair.primaryId()) && SILK.equals(pair.secondaryId());
    }
    public static boolean isLine(@Nullable FusedPair pair) {
        return pair != null && SILK.equals(pair.primaryId()) && EFFICIENCY.equals(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return (isSlice(pair) && SLICE.equals(signature)) || (isLine(pair) && LINE.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> carriers) {
        return carriers != null && carriers.stream().anyMatch(TOOLS::contains);
    }
    public static boolean applies(FusedPair pair, ItemStack stack, String signature) {
        return matches(pair, signature) && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(ItemStack tool, boolean slice) {
        if (tool == null || tool.isEmpty() || !supportsCarrier(FusionCarrier.classify(tool))) return null;
        for (FusedPair pair : tool.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((slice ? isSlice(pair) : isLine(pair))
                && SignatureCatalog.resolve(pair).map(e -> matches(pair, e.id())).orElse(false)) return pair;
        }
        return null;
    }
    public static boolean hasSlice(ServerPlayer player) { return selected(player.getMainHandItem(), true) != null; }
    public static boolean hasLine(ServerPlayer player) { return selected(player.getMainHandItem(), false) != null; }

    /** Linear in Efficiency level until the administrator's explicit absolute cap. */
    public static int scaledLimit(int efficiencyLevel, int perLevel, int cap) {
        return (int) Math.min(Math.clamp(cap, 1, 256), (long) Math.clamp(efficiencyLevel, 1, 255) * Math.clamp(perLevel, 1, 32));
    }
    private static int sliceLimit(int level) {
        return scaledLimit(level, FusionConfigStore.signatureInt(SLICE, "slice_extra_per_level", 6, 1, 32),
            FusionConfigStore.signatureInt(SLICE, "slice_extra_cap", 64, 1, 256));
    }
    private static int lineLimit(int level) {
        return scaledLimit(level, FusionConfigStore.signatureInt(LINE, "line_blocks_per_level", 4, 1, 32),
            FusionConfigStore.signatureInt(LINE, "line_blocks_cap", 32, 1, 256));
    }

    public static void cycleSlice(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !hasSlice(player)) return;
        ItemStack tool = player.getMainHandItem();
        SliceMode old = MODES.get(player.getUUID());
        Plane next = old != null && old.tool() == tool && old.dimension().equals(level.dimension()) ? old.plane().next() : Plane.HORIZONTAL;
        MODES.put(player.getUUID(), new SliceMode(tool, level.dimension(), next));
        player.sendSystemMessage(Component.translatable(next.key), true);
    }

    /** Invoked after the center actually breaks, but before its playerDestroy finishes. */
    public static void onHarvest(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos center, BlockState original, FusedPair pair) {
        SliceMode mode = MODES.get(player.getUUID());
        if (!isSlice(pair) || mode == null || mode.tool() != tool || !mode.dimension().equals(level.dimension())
            || mode.plane() == Plane.OFF || player.isShiftKeyDown() || !player.mayBuild()
            || ExtraBlockHarvest.isActive() || !ordinary(level, center, original)) return;
        Plane plane = mode.plane();
        int limit = sliceLimit(pair.primaryLevel());
        BlockPos origin = center.immutable();
        ExtraBlockHarvest.defer(() -> {
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            visited.add(origin);
            for (Direction direction : plane.neighbors) pending.add(origin.relative(direction));
            int broken = 0;
            while (!pending.isEmpty() && broken < limit && ExtraBlockHarvest.hasOriginalTool(player, tool)
                && pair.equals(selected(tool, true)) && MODES.get(player.getUUID()) == mode) {
                BlockPos pos = pending.removeFirst();
                if (!visited.add(pos)) continue;
                boolean didBreak = ExtraBlockHarvest.tryBreak(level, player, tool, pos,
                    state -> state.equals(original) && ordinary(level, pos, state));
                if (!didBreak || level.getBlockState(pos).equals(original)) continue;
                broken++;
                for (Direction direction : plane.neighbors) {
                    BlockPos next = pos.relative(direction);
                    if (!visited.contains(next)) pending.addLast(next);
                }
            }
            if (broken > 0) successful(level, player, pair, TriggerType.ON_BLOCK_HARVEST, Vec3.atCenterOf(origin));
        });
    }

    /** Restrict both skills to ordinary full collision cubes, not fluids or block entities. */
    private static boolean ordinary(ServerLevel level, BlockPos pos, BlockState state) {
        return state != null && !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty()
            && state.getDestroySpeed(level, pos) >= 0.0F && state.isCollisionShapeFullBlock(level, pos);
    }
    private static @Nullable BlockHitResult lookedAt(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        return hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
    }

    /** First G samples the looked-at cube; G again builds from a face. Sneak+G resamples. */
    public static void activateLine(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !hasLine(player)) return;
        BlockHitResult hit = lookedAt(player);
        if (hit == null) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.line_no_target"), true);
            return;
        }
        Sample sample = currentSample(player, level);
        if (sample == null || player.isShiftKeyDown()) {
            if (sampleAt(player, hit.getBlockPos())) player.sendSystemMessage(Component.translatable(
                "message.enchantment_fusion.line_sampled", level.getBlockState(hit.getBlockPos()).getBlock().getName()), true);
            else player.sendSystemMessage(Component.translatable("message.enchantment_fusion.line_invalid_sample"), true);
        } else {
            int built = placeLine(player, hit);
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.line_result", built), true);
        }
    }

    private static @Nullable Sample currentSample(ServerPlayer player, ServerLevel level) {
        Sample sample = SAMPLES.get(player.getUUID());
        if (sample == null) return null;
        long age = level.getGameTime() - sample.tick();
        if (sample.tool() != player.getMainHandItem() || !sample.dimension().equals(level.dimension())
            || age < 0 || age > SAMPLE_LIFETIME || selected(sample.tool(), false) == null) {
            SAMPLES.remove(player.getUUID());
            return null;
        }
        return sample;
    }

    public static boolean sampleAt(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || !hasLine(player)
            || !player.isWithinBlockInteractionRange(pos, 1.0D) || !level.hasChunkAt(pos)
            || !level.mayInteract(player, pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!ordinary(level, pos, state) || !(state.getBlock().asItem() instanceof BlockItem item)
            || item.getClass() != BlockItem.class || item.getBlock() != state.getBlock()) return false;
        SAMPLES.put(player.getUUID(), new Sample(player.getMainHandItem(), level.dimension(), state, level.getGameTime()));
        return true;
    }

    /** Uses the actual offhand stack and Fabric/vanilla use-on-block path for every cube. */
    public static int placeLine(ServerPlayer player, BlockHitResult hit) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
            || !player.mayBuild() || player.hasInfiniteMaterials() || hit == null
            || !player.isWithinBlockInteractionRange(hit.getBlockPos(), 1.0D)) return 0;
        Sample sample = currentSample(player, level);
        FusedPair pair = selected(player.getMainHandItem(), false);
        if (sample == null || pair == null) return 0;
        ItemStack material = player.getOffhandItem();
        if (!(material.getItem() instanceof BlockItem blockItem) || blockItem.getClass() != BlockItem.class
            || blockItem.getBlock() != sample.state().getBlock()) return 0;
        int limit = lineLimit(pair.secondaryLevel()), built = 0;
        BlockPos anchor = hit.getBlockPos().immutable();
        Direction direction = hit.getDirection();
        while (built < limit && !material.isEmpty() && player.getOffhandItem() == material
            && player.getMainHandItem() == sample.tool() && pair.equals(selected(sample.tool(), false))
            && player.level() == level && player.isAlive()) {
            BlockPos destination = anchor.relative(direction);
            if (!level.isInWorldBounds(anchor) || !level.isInWorldBounds(destination)
                || !level.hasChunkAt(anchor) || !level.hasChunkAt(destination)
                || !level.mayInteract(player, anchor) || !level.mayInteract(player, destination)
                || level.getBlockState(anchor).hasBlockEntity() || !level.getBlockState(destination).isAir()
                || !player.mayUseItemAt(destination, direction, material)) break;
            Vec3 point = Vec3.atCenterOf(anchor).add(direction.getStepX() * 0.5D, direction.getStepY() * 0.5D, direction.getStepZ() * 0.5D);
            BlockHitResult actualHit = new BlockHitResult(point, direction, anchor, false);
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.OFF_HAND, material, actualHit);
            BlockPlaceContext updated = blockItem.updatePlacementContext(context);
            if (updated == null || !updated.getClickedPos().equals(destination) || !updated.canPlace()) break;
            BlockState predicted = blockItem.getBlock().getStateForPlacement(updated);
            if (predicted == null) break;
            predicted = material.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(predicted);
            if (!predicted.equals(sample.state())) break;
            int before = material.getCount();
            InteractionResult result = player.gameMode.useItemOn(player, level, material, InteractionHand.OFF_HAND, actualHit);
            if (!result.consumesAction() || !level.getBlockState(destination).equals(sample.state())) break;
            built++;
            // If an interceptor placed the cube but left the same stack untouched, still pay
            // one. If it replaced the stack or applied an unexpected charge, stop rather than
            // guessing how to reverse another mod's world/inventory transaction.
            if (player.getOffhandItem() == material && material.getCount() == before) material.shrink(1);
            if (player.getOffhandItem() != material || material.getCount() != before - 1) break;
            anchor = destination;
        }
        if (built > 0) successful(level, player, pair, TriggerType.ON_UTILITY_CYCLE, Vec3.atCenterOf(hit.getBlockPos()));
        return built;
    }

    private static void successful(ServerLevel level, ServerPlayer player, FusedPair pair, TriggerType trigger, Vec3 pos) {
        CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
        if (pair.isAberrant() && MatrixEffectExecutor.acquireDeferredCooldown(level, player, pair, trigger)) {
            MatrixEffectExecutor.runDeferredAberration(level, player, pair, pos);
        }
    }

    public static void onDisconnect(UUID player) { MODES.remove(player); SAMPLES.remove(player); }
    public static void clear() { MODES.clear(); SAMPLES.clear(); }
}
