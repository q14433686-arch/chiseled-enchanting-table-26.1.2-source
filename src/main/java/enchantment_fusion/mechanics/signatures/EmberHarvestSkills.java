package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Fire Aspect -> Looting: withdraw observed fire to mark one enemy's own ordinary death loot. */
public final class EmberHarvestSkills {
    public static final String FIRE = "minecraft:fire_aspect", LOOTING = "minecraft:looting", SIGNATURE = "alchemic_pyre";
    private static final String CHANNEL = "ember-harvest";
    private static final int BURN_TICKS = 20, MARK_TICKS = 120, MAX_OWNERS = 512;
    private static final OwnedTargetMarks MARKS = new OwnedTargetMarks(MAX_OWNERS, EmberHarvestSkills::applies);
    private record Gear(ItemStack stack, FusedPair pair) {}
    private EmberHarvestSkills() {}
    public static void init() {
        ConfirmedMelee.register(EmberHarvestCapture::confirmedHit);
        ConfirmedMelee.register(EmberHarvestSkills::onHit);
    }
    public static boolean selectedSignature(@Nullable SignatureEntry entry) {
        return entry != null && SIGNATURE.equals(entry.id()) && FIRE.equals(entry.primaryId()) && LOOTING.equals(entry.secondaryId());
    }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && FIRE.equals(pair.primaryId()) && LOOTING.equals(pair.secondaryId())
            && SignatureCatalog.resolve(pair).map(EmberHarvestSkills::selectedSignature).orElse(false);
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return MeleeAttackThemes.supportsWeapon(roles); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return isPair(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (applies(pair, stack)) return pair;
        return null;
    }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        return owner.isAlive() && !owner.isSpectator() && owner.getMainHandItem() == gear.stack()
            && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        MeleeImpulseSkills.Attack frame = MeleeImpulseSkills.currentAttack(); ServerPlayer owner = hit.player();
        if (frame == null || frame.ambiguous || frame.player != owner || frame.weapon != hit.weapon() || frame.target != hit.target()
            || hit.slot() != EquipmentSlot.MAINHAND || !hit.full() || !hit.sneakingBefore() || hit.fireBefore() <= 0
            || !hit.eligibleBefore() || !hit.target().isAlive() || !hit.target().isOnFire()
            || hit.target().getRemainingFireTicks() <= 0 || !BurningTargetHistory.observedFor(hit.target(), BURN_TICKS)
            || owner.getMainHandItem() != hit.weapon() || owner.distanceToSqr(hit.target()) > 36.0D) return;
        FusedPair pair = selected(hit.weapon()); if (pair == null || MARKS.get(owner, CHANNEL) != null) return;
        if (!frame.completions.isEmpty()) { frame.ambiguous = true; return; }
        Gear gear = new Gear(hit.weapon(), pair); LivingEntity target = hit.target(); ServerLevel level = owner.level();
        frame.completions.add(() -> {
            try { extract(owner, target, level, gear); }
            catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Unable to extract target fire", error); }
        });
    }
    private static void extract(ServerPlayer owner, LivingEntity target, ServerLevel level, Gear gear) {
        if (!owns(owner, gear) || owner.level() != level || target.level() != level || !target.isAlive()
            || !MeleeAttackThemes.eligible(owner, target, MeleeAttackThemes.SHARP) || !target.isOnFire()
            || target.getRemainingFireTicks() <= 0 || !BurningTargetHistory.observedFor(target, BURN_TICKS)
            || MARKS.get(owner, CHANNEL) != null) return;
        if (!MARKS.put(owner, CHANNEL, EquipmentSlot.MAINHAND, gear.stack(), gear.pair(), target, MARK_TICKS)) return;
        OwnedTargetMarks.Mark mark = MARKS.get(owner, CHANNEL);
        try {
            target.clearFire();
            if (target.getRemainingFireTicks() > 0 || !owns(owner, gear) || !target.isAlive()) {
                if (mark != null) MARKS.remove(owner, CHANNEL, mark); return;
            }
        } catch (RuntimeException error) {
            if (mark != null) MARKS.remove(owner, CHANNEL, mark); throw error;
        }
        BurningTargetHistory.forget(target);
        level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.6D), target.getZ(), 7, 0.15D, 0.2D, 0.15D, 0.01D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, FIRE, LOOTING);
    }
    static @Nullable OwnedTargetMarks.Mark mark(ServerPlayer owner, LivingEntity victim, ItemStack weapon) {
        OwnedTargetMarks.Mark mark = MARKS.get(owner, CHANNEL);
        return mark != null && mark.stack() == weapon && mark.target().equals(victim.getUUID()) ? mark : null;
    }
    static boolean consume(ServerPlayer owner, OwnedTargetMarks.Mark mark) { return MARKS.remove(owner, CHANNEL, mark); }
    public static void tickServer(MinecraftServer server) { MARKS.tickServer(server); }
    public static void clearPlayer(UUID id) { MARKS.clearPlayer(id); }
    public static void clearServer() { MARKS.clear(); EmberHarvestCapture.clear(); }
}
