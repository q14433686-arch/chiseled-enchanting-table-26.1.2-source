package enchantment_fusion.mechanics.signatures;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

/**
 * Scope native Frost Walker location effects on selected worn boots. Only individual
 * ReplaceDisk/ReplaceBlock writes into water are suppressed; other location effects,
 * activation/deactivation bookkeeping and writes to other materials still run normally.
 */
public final class NativeFrostWaterGuard {
    private static final ThreadLocal<Deque<Boolean>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private NativeFrostWaterGuard() {}

    /** Nested unrelated enchantment calls temporarily mask an enclosing guard. */
    public static boolean enter(boolean guarded) {
        Deque<Boolean> scopes = SCOPES.get();
        if (!guarded && scopes.isEmpty()) {
            SCOPES.remove();
            return false;
        }
        scopes.push(guarded);
        return true;
    }

    public static void exit(boolean entered) {
        if (!entered) return;
        Deque<Boolean> scopes = SCOPES.get();
        scopes.pop();
        if (scopes.isEmpty()) SCOPES.remove();
    }

    public static boolean blocksWaterWrite(ServerLevel level, BlockPos pos) {
        Deque<Boolean> scopes = SCOPES.get();
        boolean guarded = !scopes.isEmpty() && scopes.peek();
        if (scopes.isEmpty()) SCOPES.remove();
        return guarded && level.getFluidState(pos).is(FluidTags.WATER);
    }
}
