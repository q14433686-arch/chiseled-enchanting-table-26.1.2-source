package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.TemporaryFrostEffects;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 43.2: shared target marks and ice-constrained virtual cutting across the attack family. */
public final class MeleeFrostSkills {
    public static final String FROST = "minecraft:frost_walker";
    private static final int LIFETIME = 120, MAX_OWNERS = 256;
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE);
    private record Gear(ItemStack stack, FusedPair pair, EquipmentSlot slot) {}
    private record Armed(Gear gear, ResourceKey<Level> dimension, long expires) {}
    private static final OwnedTargetMarks MARKS = new OwnedTargetMarks(MAX_OWNERS, MeleeFrostSkills::applies);
    private static final Map<UUID, Armed> ARMED = new HashMap<>();
    private static final ThreadLocal<Damage> DAMAGE = new ThreadLocal<>();
    private MeleeFrostSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeFrostSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_frost", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && FROST.equals(pair.secondaryId());
    }
    public static boolean frostFirst(@Nullable FusedPair pair) {
        return pair != null && FROST.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (attackFirst(pair) || frostFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, Set<FusionCarrier> roles) {
        if (roles == null) return false;
        return attackFirst(pair) ? roles.stream().anyMatch(WEAPONS::contains) : frostFirst(pair) && roles.contains(FusionCarrier.FOOT_ARMOR);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static @Nullable Gear select(ServerPlayer owner, boolean reverse) {
        EquipmentSlot slot = reverse ? EquipmentSlot.FEET : EquipmentSlot.MAINHAND; ItemStack stack = owner.getItemBySlot(slot);
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((reverse ? frostFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair, slot);
        return null;
    }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        return owner.isAlive() && !owner.isSpectator() && owner.getItemBySlot(gear.slot()) == gear.stack() && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    private static boolean valid(ServerPlayer owner, Armed armed) {
        return owner != null && owner.level().dimension().equals(armed.dimension()) && owner.level().getGameTime() < armed.expires()
            && owns(owner, armed.gear());
    }
    public static boolean hasEquipped(ServerPlayer owner) { return select(owner, true) != null; }
    public static boolean activate(ServerPlayer owner) {
        Gear gear = select(owner, true); if (gear == null || !owns(owner, gear) || !FrostGroundBlade.onIce(owner)) return false;
        Armed old = ARMED.get(owner.getUUID());
        if (old != null && valid(owner, old)) return false;
        if (!ARMED.containsKey(owner.getUUID()) && ARMED.size() >= MAX_OWNERS) return false;
        ARMED.put(owner.getUUID(), new Armed(gear, owner.level().dimension(), owner.level().getGameTime() + LIFETIME));
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_frost_ready"), true); return true;
    }
    public static final class Damage {
        final @Nullable Damage previous; final @Nullable ConfirmedMelee.Observation observation;
        final @Nullable OwnedTargetMarks.Mark marked; final float amount;
        Damage(@Nullable Damage previous, @Nullable ConfirmedMelee.Observation observation, @Nullable OwnedTargetMarks.Mark marked, float amount) {
            this.previous = previous; this.observation = observation; this.marked = marked; this.amount = amount;
        }
        public float damage() { return amount; }
    }
    public static float markedDamage(float amount, int frostLevel) {
        if (!Float.isFinite(amount) || amount <= 0) return amount;
        return Math.max(amount, Math.min(1024.0F, amount * (1.15F + 0.05F * Math.clamp(frostLevel, 1, 10))));
    }
    /** The stored mark modifies the same real request, not a second hit through invulnerability. */
    public static Damage beginDamage(@Nullable ConfirmedMelee.Observation observation, float amount) {
        Damage previous = DAMAGE.get(); OwnedTargetMarks.Mark mark = null;
        if (previous == null && observation != null && observation.slot() == EquipmentSlot.MAINHAND && observation.eligibleBefore()
            && observation.player().getMainHandItem() == observation.weapon() && Float.isFinite(amount) && amount > 0) {
            OwnedTargetMarks.Mark candidate = MARKS.get(observation.player(), "weapon");
            Gear selected = select(observation.player(), false);
            if (candidate != null && selected != null && selected.pair().equals(candidate.pair())
                && candidate.stack() == observation.weapon() && candidate.target().equals(observation.target().getUUID())
                && MeleeAttackThemes.matchesTarget(candidate.pair().primaryId(), observation.target())) mark = candidate;
        }
        Damage frame = new Damage(previous, observation, mark, mark == null ? amount : markedDamage(amount, mark.pair().secondaryLevel()));
        DAMAGE.set(frame); return frame;
    }
    public static void endDamage(Damage frame) {
        if (DAMAGE.get() != frame) { DAMAGE.remove(); return; }
        if (frame.previous == null) DAMAGE.remove(); else DAMAGE.set(frame.previous);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); LivingEntity target = hit.target(); Damage frame = DAMAGE.get();
        if (frame == null || frame.previous != null || frame.observation == null || frame.observation.player() != owner
            || frame.observation.target() != target || frame.observation.weapon() != hit.weapon() || hit.slot() != EquipmentSlot.MAINHAND
            || owner.getMainHandItem() != hit.weapon() || !hit.eligibleBefore() || !owner.isAlive() || owner.isSpectator()) return;
        Gear weapon = select(owner, false);
        if (weapon != null && owns(owner, weapon) && MeleeAttackThemes.matchesTarget(weapon.pair().primaryId(), target)) {
            OwnedTargetMarks.Mark mark = frame.marked;
            if (mark != null && mark.stack() == weapon.stack() && mark.pair().equals(weapon.pair()) && MARKS.remove(owner, "weapon", mark)) {
                if (target.isAlive() && MeleeAttackThemes.eligible(owner, target, weapon.pair().primaryId()))
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
                owner.level().sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6D), target.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
                CompendiumDiscoveryTracker.recordDiscovery(owner, weapon.pair().primaryId(), weapon.pair().secondaryId());
            } else if (target.isAlive() && MARKS.put(owner, "weapon", EquipmentSlot.MAINHAND, weapon.stack(), weapon.pair(), target, LIFETIME)) {
                owner.level().sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6D), target.getZ(), 4, 0.15D, 0.2D, 0.15D, 0.01D);
            }
        }
        Armed armed = ARMED.get(owner.getUUID());
        if (armed == null || !valid(owner, armed) || !FrostGroundBlade.onIce(owner)
            || !MeleeAttackThemes.matchesTarget(armed.gear().pair().secondaryId(), target)) return;
        if (!ARMED.remove(owner.getUUID(), armed)) return;
        float amount = Math.min(8.0F, 2.0F + 0.35F * Math.clamp(armed.gear().pair().primaryLevel(), 1, 10)
            + 0.25F * Math.clamp(armed.gear().pair().secondaryLevel(), 1, 10));
        FrostGroundBlade.cast(owner, target, amount,
            victim -> MeleeAttackThemes.eligible(owner, victim, armed.gear().pair().secondaryId()));
        CompendiumDiscoveryTracker.recordDiscovery(owner, armed.gear().pair().primaryId(), armed.gear().pair().secondaryId());
    }
    /** Preserve frost-walking affordance with thin ice above, rather than replacing original water. */
    public static void tickPlayer(ServerPlayer player) {
        Gear gear = select(player, true);
        if (gear != null && owns(player, gear) && player.level().getGameTime() % 5L == 0)
            TemporaryFrostEffects.placeWake(player.level(), player, gear.pair().primaryLevel());
    }
    public static void tickServer(MinecraftServer server) {
        MARKS.tickServer(server);
        ARMED.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !valid(owner, entry.getValue());
        });
    }
    public static void clearPlayer(UUID id) { MARKS.clearPlayer(id); ARMED.remove(id); }
    public static void clearServer() { MARKS.clear(); ARMED.clear(); DAMAGE.remove(); }
}
