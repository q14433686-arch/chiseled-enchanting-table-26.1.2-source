package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** A shared observation of net health/absorption loss at the real actuallyHurt boundary. */
public final class ConfirmedLivingDamage {
    public record Hit(ServerLevel level, LivingEntity victim, DamageSource source, float loss) {}
    private static final List<Consumer<Hit>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> DISPATCHING = ThreadLocal.withInitial(() -> false);
    private ConfirmedLivingDamage() {}
    public static void register(Consumer<Hit> listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }
    public static void observed(LivingEntity victim, @Nullable DamageSource source, float health, float absorption) {
        if (LISTENERS.isEmpty() || victim == null || source == null || !(victim.level() instanceof ServerLevel level)
            || Boolean.TRUE.equals(DISPATCHING.get())) return;
        float loss = ConfirmedMeleeMath.actualLoss(health, absorption, victim.getHealth(), victim.getAbsorptionAmount());
        if (loss <= 0.0F) return;
        Hit hit = new Hit(level, victim, source, loss);
        DISPATCHING.set(true);
        try {
            for (Consumer<Hit> listener : LISTENERS) try { listener.accept(hit); }
            catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Confirmed living-damage listener failed", error); }
        } finally { DISPATCHING.remove(); }
    }
}
