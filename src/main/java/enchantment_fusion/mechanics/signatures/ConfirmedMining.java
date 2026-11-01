package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** An AFTER event inside the actual game-mode break, with the exact original live tool. */
public final class ConfirmedMining {
    public interface Listener { void mined(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos, BlockState state); }
    public static final class Scope {
        private final @Nullable Scope previous;
        private final ServerLevel level;
        private final ServerPlayer player;
        private final ItemStack tool;
        private final BlockPos pos;
        private final BlockState before;
        private boolean fired;
        private Scope(@Nullable Scope previous, ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos, BlockState before) {
            this.previous = previous; this.level = level; this.player = player; this.tool = tool;
            this.pos = pos.immutable(); this.before = before;
        }
    }
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private ConfirmedMining() {}
    public static void register(Listener listener) { if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener); }
    public static Scope begin(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        Scope scope = new Scope(CURRENT.get(), level, player, player.getMainHandItem(), pos, state);
        CURRENT.set(scope); return scope;
    }
    public static void end(@Nullable Scope scope) {
        if (scope == null || CURRENT.get() != scope) return;
        if (scope.previous == null) CURRENT.remove(); else CURRENT.set(scope.previous);
    }
    public static void after(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.previous != null || scope.fired || scope.level != level || scope.player != player || !scope.pos.equals(pos)
            || scope.tool.isEmpty() || player.getMainHandItem() != scope.tool || scope.before.isAir()
            || scope.before.getBlock() != state.getBlock() || level.getBlockState(pos) == state
            || !MiningWorkSupport.legalBreak(level, player, pos, state)) return;
        scope.fired = true; // Commit before arbitrary subscribers/reentrant mod callbacks.
        for (Listener listener : LISTENERS) {
            try { listener.mined(level, player, scope.tool, pos, state); }
            catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Mining evidence callback failed", error); }
        }
    }
}
