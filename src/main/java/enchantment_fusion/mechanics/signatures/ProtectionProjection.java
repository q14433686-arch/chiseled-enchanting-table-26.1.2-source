package enchantment_fusion.mechanics.signatures;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

/** One actual protection pass: prevent replaying the same item's already-observed enchantment. */
public final class ProtectionProjection {
    public static final class Frame {
        final @Nullable Frame previous; final ServerPlayer player; final DamageSource source;
        final Map<ItemStack, Map<Enchantment, Integer>> applied = new IdentityHashMap<>();
        Frame(@Nullable Frame previous, ServerPlayer player, DamageSource source) {
            this.previous = previous; this.player = player; this.source = source;
        }
    }
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private ProtectionProjection() {}
    public static Frame begin(ServerPlayer player, DamageSource source) {
        Frame frame = new Frame(CURRENT.get(), player, source); CURRENT.set(frame); return frame;
    }
    public static void observe(Enchantment enchantment, int level, ItemStack item, Entity victim, DamageSource source) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.player != victim || frame.source != source) return;
        frame.applied.computeIfAbsent(item, ignored -> new IdentityHashMap<>()).merge(enchantment, level, Math::max);
    }
    public static boolean applied(ServerPlayer player, DamageSource source, ItemStack item, Enchantment enchantment) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.player != player || frame.source != source) return false;
        Map<Enchantment, Integer> enchantments = frame.applied.get(item);
        return enchantments != null && enchantments.containsKey(enchantment);
    }
    public static void clear() { CURRENT.remove(); }
    public static void end(Frame frame) {
        if (CURRENT.get() != frame) { CURRENT.remove(); return; }
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
    }
}
