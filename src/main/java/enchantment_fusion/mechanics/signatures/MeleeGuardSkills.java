package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Six attack/protection templates. All marks are session-only and bound to real items. */
public final class MeleeGuardSkills {
    public static final String PROTECTION = "minecraft:protection";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int MAX_OWNERS = 256, MAX_EQUIPPED = 6, GUARD_TICKS = 60, CHARGE_TICKS = 200;
    private static final float CHARGE_CAP = 12.0F, CHARGE_MIN = 2.0F, CHARGE_SPEND = 4.0F;
    public record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private static final class Guard {
        final UUID owner; final ResourceKey<Level> dimension; final ItemStack weapon; final FusedPair pair;
        final long created, expires; boolean skipInitialSwing = true; int remaining = 3;
        Guard(ServerPlayer owner, ItemStack weapon, FusedPair pair, long now) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.weapon = weapon; this.pair = pair;
            this.created = now; this.expires = now + GUARD_TICKS;
        }
    }
    private static final class Charge {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear; float amount; long expires;
        Charge(ServerPlayer owner, Gear gear, long now) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear; this.expires = now + CHARGE_TICKS;
        }
    }
    private static final Map<UUID, Guard> GUARDS = new HashMap<>();
    private static final Map<UUID, List<Charge>> CHARGES = new HashMap<>();
    private static final ThreadLocal<Boolean> POST_REPLAY = ThreadLocal.withInitial(() -> false);
    private MeleeGuardSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeGuardSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_guard", (pair, stack) -> isAttackFirst(pair) && applies(pair, stack));
    }
    public static boolean isAttackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && PROTECTION.equals(pair.secondaryId());
    }
    public static boolean isProtectionFirst(@Nullable FusedPair pair) {
        return pair != null && PROTECTION.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (isAttackFirst(pair) || isProtectionFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, Set<FusionCarrier> roles) {
        return isAttackFirst(pair) ? MeleeAttackThemes.supportsWeapon(roles)
            : isProtectionFirst(pair) && FireTradeoffSkills.supportsCarrier(roles);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static @Nullable FusedPair forward(ItemStack weapon) {
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isAttackFirst(pair) && applies(pair, weapon)) return pair;
        return null;
    }
    public static List<Gear> equipped(ServerPlayer player) {
        List<Gear> result = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!FireTradeoffSkills.slotMatches(stack, slot)) continue;
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                if (isProtectionFirst(pair) && applies(pair, stack)) { result.add(new Gear(slot, stack, pair)); break; }
            }
        }
        return result;
    }
    public static boolean stillEquipped(ServerPlayer player, Gear gear) {
        return gear != null && player.getItemBySlot(gear.slot()) == gear.stack() && !gear.stack().isEmpty()
            && FireTradeoffSkills.slotMatches(gear.stack(), gear.slot()) && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    private static boolean same(Gear left, Gear right) {
        return left.slot() == right.slot() && left.stack() == right.stack() && left.pair().equals(right.pair());
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    private static boolean direct(ServerPlayer owner, DamageSource source) {
        return source.getEntity() == owner && source.getDirectEntity() == owner
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !MatrixEffectExecutor.isExecuting()
            && !GlazedStrikeEffect.isApplyingBonus() && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
    }
    private static boolean valid(ServerPlayer owner, Guard guard) {
        return ready(owner) && owner.getUUID().equals(guard.owner) && owner.level().dimension().equals(guard.dimension)
            && owner.level().getGameTime() < guard.expires && guard.remaining > 0
            && owner.getMainHandItem() == guard.weapon && applies(guard.pair, guard.weapon)
            && guard.weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(guard.pair);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player();
        if (hit.slot() != EquipmentSlot.MAINHAND || owner.getMainHandItem() != hit.weapon() || !hit.eligibleBefore()
            || !ready(owner) || !direct(owner, hit.source())) return;
        FusedPair pair = forward(hit.weapon());
        if (pair != null && MeleeAttackThemes.matchesTarget(pair.primaryId(), hit.target())) {
            Guard old = GUARDS.get(owner.getUUID());
            if (old == null || !valid(owner, old)) {
                if (GUARDS.containsKey(owner.getUUID()) || GUARDS.size() < MAX_OWNERS) {
                    GUARDS.put(owner.getUUID(), new Guard(owner, hit.weapon(), pair, owner.level().getGameTime()));
                    owner.level().sendParticles(ParticleTypes.SWEEP_ATTACK, owner.getX(), owner.getY(0.8D), owner.getZ(), 2, 0.2D, 0.15D, 0.2D, 0);
                }
            }
        }
        List<Charge> charges = CHARGES.get(owner.getUUID());
        if (charges == null) return;
        long now = owner.level().getGameTime();
        Charge chosen = charges.stream().filter(charge -> now < charge.expires && charge.amount >= CHARGE_MIN
            && charge.dimension.equals(owner.level().dimension()) && stillEquipped(owner, charge.gear)
            && MeleeAttackThemes.matchesTarget(charge.gear.pair().secondaryId(), hit.target()))
            .max(Comparator.comparingDouble(charge -> charge.amount)).orElse(null);
        if (chosen == null) return;
        float spent = Math.min(CHARGE_SPEND, chosen.amount); chosen.amount -= spent;
        if (chosen.amount < 1.0E-5F) charges.remove(chosen);
        if (charges.isEmpty()) CHARGES.remove(owner.getUUID());
        Gear gear = chosen.gear;
        VirtualBlade.cast(owner, owner.getLookAngle(), null, 5.0D,
            MeleeGuardMath.counterDamage(gear.pair().primaryLevel(), gear.pair().secondaryLevel(), spent), 8,
            target -> MeleeAttackThemes.eligible(owner, target, gear.pair().secondaryId()), ParticleTypes.SWEEP_ATTACK);
        CompendiumDiscoveryTracker.recordDiscovery(owner, gear.pair().primaryId(), gear.pair().secondaryId());
    }
    /** Preserve a non-native Protection primary's datapack victim post-attack effects once per actual gear. */
    public static void replayVictimPostAttack(ServerLevel level, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(POST_REPLAY.get()) || !(victim instanceof ServerPlayer player) || player.level() != level) return;
        Holder<Enchantment> protection = FusionStationOperations.lookupEnchantment(level.registryAccess(), PROTECTION).orElse(null);
        if (protection == null) return;
        POST_REPLAY.set(true);
        try {
            List<Gear> meleeGear = equipped(player); List<GrapnelDefense.Gear> grapnelGear = GrapnelDefense.equipped(player);
            List<RangedGuardSkills.Gear> rangedGear = RangedGuardSkills.equipped(player);
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = player.getItemBySlot(slot); int primaryLevel = 0;
                for (Gear gear : meleeGear) if (gear.slot() == slot && gear.stack() == stack && stillEquipped(player, gear))
                    primaryLevel = Math.max(primaryLevel, gear.pair().primaryLevel());
                for (GrapnelDefense.Gear gear : grapnelGear) if (gear.slot() == slot && gear.stack() == stack
                    && GrapnelDefense.PROTECTION.equals(gear.pair().primaryId()) && GrapnelDefense.stillEquipped(player, gear))
                    primaryLevel = Math.max(primaryLevel, gear.pair().primaryLevel());
                for (RangedGuardSkills.Gear gear : rangedGear) if (gear.slot() == slot && gear.stack() == stack && RangedGuardSkills.stillEquipped(player, gear))
                    primaryLevel = Math.max(primaryLevel, gear.pair().primaryLevel());
                if (primaryLevel <= 0) continue;
                int nativeLevel = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(protection);
                if (nativeLevel > 0 && protection.value().matchingSlot(slot)) continue;
                protection.value().doPostAttack(level, Math.max(nativeLevel, primaryLevel), new EnchantedItemInUse(stack, slot, player),
                    EnchantmentTarget.VICTIM, victim, source);
            }
        } finally { POST_REPLAY.remove(); }
    }
    /** Credit only protection attributable to this exact still-equipped item. */
    public static void addCharge(ServerPlayer owner, Gear gear, float amount) {
        if (!ready(owner) || !stillEquipped(owner, gear) || !Float.isFinite(amount) || amount <= 0) return;
        List<Charge> charges = CHARGES.get(owner.getUUID());
        if (charges == null) {
            if (CHARGES.size() >= MAX_OWNERS) return;
            charges = new ArrayList<>(); CHARGES.put(owner.getUUID(), charges);
        }
        long now = owner.level().getGameTime();
        Charge chosen = null;
        for (Charge charge : charges) if (same(charge.gear, gear) && charge.dimension.equals(owner.level().dimension()) && now < charge.expires) {
            chosen = charge; break;
        }
        if (chosen == null) {
            if (charges.size() >= MAX_EQUIPPED) return;
            chosen = new Charge(owner, gear, now); charges.add(chosen);
        }
        chosen.amount = Math.min(CHARGE_CAP, chosen.amount + amount); chosen.expires = now + CHARGE_TICKS;
    }
    /** Client's next main-hand animation recalls; ignore the original hit's own animation packet. */
    public static void onSwing(ServerPlayer owner, InteractionHand hand) {
        Guard guard = GUARDS.get(owner.getUUID());
        if (hand != InteractionHand.MAIN_HAND || guard == null || !valid(owner, guard)) return;
        long now = owner.level().getGameTime();
        if (guard.skipInitialSwing && now - guard.created <= 1) { guard.skipInitialSwing = false; return; }
        if (now - guard.created < 4) return;
        GUARDS.remove(owner.getUUID(), guard);
    }
    /** Run only at the real projectile/entity impact, before the target's hit callback. */
    public static boolean deflect(Projectile projectile, ServerPlayer owner, EntityHitResult hit) {
        Guard guard = GUARDS.get(owner.getUUID());
        if (guard == null || !valid(owner, guard) || projectile.level() != owner.level() || !projectile.isAlive()
            || projectile instanceof LlamaSpit || !(projectile.getOwner() instanceof LivingEntity shooter)
            || !LoyaltyGuard.eligibleTarget(owner, shooter) || !owner.level().mayInteract(owner, shooter.blockPosition())
            || (shooter instanceof Player other && !other.canHarmPlayer(owner))) return false;
        Vec3 look = owner.getLookAngle(), velocity = projectile.getDeltaMovement();
        Vec3 offset = hit.getLocation().subtract(owner.getBoundingBox().getCenter());
        if (!MeleeGuardMath.front(look.x, look.y, look.z, velocity.x, velocity.y, velocity.z, offset.x, offset.y, offset.z)) return false;
        if (!projectile.deflect(ProjectileDeflection.REVERSE, owner, EntityReference.of(shooter), false)) return false;
        guard.remaining--;
        if (guard.remaining <= 0) GUARDS.remove(owner.getUUID(), guard);
        owner.level().sendParticles(ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 6, 0.08D, 0.08D, 0.08D, 0.01D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, guard.pair.primaryId(), guard.pair.secondaryId());
        return true;
    }
    public static void tickServer(MinecraftServer server) {
        GUARDS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || !valid(player, entry.getValue());
        });
        for (var entry : List.copyOf(CHARGES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !ready(player)) { CHARGES.remove(entry.getKey()); continue; }
            long now = player.level().getGameTime();
            entry.getValue().removeIf(charge -> !charge.dimension.equals(player.level().dimension()) || now >= charge.expires
                || !stillEquipped(player, charge.gear));
            if (entry.getValue().isEmpty()) CHARGES.remove(entry.getKey());
        }
    }
    public static void clearPlayer(UUID id) { GUARDS.remove(id); CHARGES.remove(id); }
    public static void clearServer() { GUARDS.clear(); CHARGES.clear(); POST_REPLAY.remove(); MeleeGuardCapture.clear(); }
}
