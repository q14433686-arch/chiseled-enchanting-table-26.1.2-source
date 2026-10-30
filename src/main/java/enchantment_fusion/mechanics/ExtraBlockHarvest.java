package enchantment_fusion.mechanics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Defers extra mining until the original playerDestroy finishes, then uses the player's normal
 * break path. A single shared guard prevents signatures and transmutations recursing into each
 * other. The queue is thread-local because game-mode destroyBlock can itself be nested by mods.
 */
public final class ExtraBlockHarvest {
    private static final ThreadLocal<Deque<List<Runnable>>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private ExtraBlockHarvest() {}

    public static void begin() {
        FRAMES.get().push(new ArrayList<>());
    }

    public static void finish(boolean completed) {
        Deque<List<Runnable>> frames = FRAMES.get();
        List<Runnable> tasks = frames.pollFirst();
        if (frames.isEmpty()) FRAMES.remove();
        if (!completed || tasks == null || tasks.isEmpty() || isActive()) return;
        runGuarded(() -> tasks.forEach(Runnable::run));
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void defer(Runnable task) {
        if (isActive()) return;
        Deque<List<Runnable>> frames = FRAMES.get();
        if (frames.isEmpty()) {
            FRAMES.remove();
            runGuarded(task); // e.g. another mod explicitly invokes our hook outside game-mode mining
        } else {
            frames.peekFirst().add(task);
        }
    }

    private static void runGuarded(Runnable task) {
        ACTIVE.set(true);
        try {
            task.run();
        } finally {
            ACTIVE.remove();
        }
    }

    public static boolean hasOriginalTool(ServerPlayer player, ItemStack original) {
        return !original.isEmpty() && player.getMainHandItem() == original;
    }

    /** Permission checks here cover vanilla rules; the game-mode call also fires Fabric break events. */
    public static boolean tryBreak(ServerLevel level, ServerPlayer player, ItemStack originalTool,
                                   BlockPos pos, Predicate<BlockState> matches) {
        if (!isActive() || player.level() != level || !player.isAlive() || player.isSpectator()
            || !hasOriginalTool(player, originalTool) || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos)
            || !level.mayInteract(player, pos)
            || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return false;
        BlockState state = level.getBlockState(pos);
        if (!matches.test(state) || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) return false;
        return player.gameMode.destroyBlock(pos);
    }

    /** Unknown modded crop maturity is treated conservatively rather than destroying immature crops. */
    public static boolean isMatureCropOrNonCrop(BlockState state) {
        if (!state.is(BlockTags.CROPS)) return true;
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int maximum = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(Integer.MAX_VALUE);
                return state.getValue(age) >= maximum;
            }
        }
        return false;
    }
}
