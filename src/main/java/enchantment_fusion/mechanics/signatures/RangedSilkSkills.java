package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 45.4: one-target follow-up or vanilla pickup of the owner's one actual arrow. */
public final class RangedSilkSkills {
    public static final String SILK = "minecraft:silk_touch";
    private static final int MAX_OWNERS = 256, MAX_GEAR = 8, MAX_PUSHES = 1024;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Mark(Gear gear, UUID target, UUID arrow, ResourceKey<Level> dimension, long expires) {}
    private record Recent(Gear gear, AbstractArrow arrow, ResourceKey<Level> dimension, long born) {}
    private record Push(Gear gear, UUID owner, UUID target, ResourceKey<Level> dimension, long born) {}
    private static final Map<UUID, List<Mark>> MARKS = new HashMap<>();
    private static final Map<UUID, List<Recent>> RECENT = new HashMap<>();
    private static final Map<AbstractArrow, Push> PUSHES = new WeakHashMap<>();
    private static final ConfirmedArrow.Listener HITS = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) { return RangedSilkSkills.applies(pair, launcher); }
        @Override public void hit(ConfirmedArrow.Hit hit) { onHit(hit); }
    };
    private static final ConfirmedArrow.InsertionListener INSERTIONS = new ConfirmedArrow.InsertionListener() {
        @Override public @Nullable Object capture(ServerPlayer owner, ItemStack launcher) { return selected(launcher, true); }
        @Override public void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object raw) {
            if (!(raw instanceof Gear gear) || gear.stack() != launcher || !owns(owner, gear) || arrow.getOwner() != owner
                || owner.level().getEntity(arrow.getUUID()) != arrow || PhantomVolley.isGhost(arrow)) return;
            List<Recent> list = RECENT.get(owner.getUUID());
            if (list == null) {
                if (RECENT.size() >= MAX_OWNERS) return;
                list = new ArrayList<>(); RECENT.put(owner.getUUID(), list);
            }
            // Never fall back to an older arrow when the newest one is intangible or gone.
            list.removeIf(entry -> entry.gear().stack() == launcher);
            if (list.size() >= MAX_GEAR) list.removeFirst();
            list.add(new Recent(gear, arrow, owner.level().dimension(), owner.level().getGameTime()));
        }
    };
    private RangedSilkSkills() {}
    public static void init() { ConfirmedArrow.register(HITS); ConfirmedArrow.registerInsertion(INSERTIONS); }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && RangedWearSkills.shooting(pair.primaryId()) && SILK.equals(pair.secondaryId());
    }
    public static boolean silkFirst(@Nullable FusedPair pair) {
        return pair != null && SILK.equals(pair.primaryId()) && RangedWearSkills.shooting(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || silkFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return RangedWearSkills.supportsCarrier(roles); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static int primaryLevel(@Nullable ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return 0; int result = 0;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (shootingFirst(pair) && id.equals(pair.primaryId()) && applies(pair, stack)) result = Math.max(result, pair.primaryLevel());
        return result;
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean reverse) {
        if (stack == null || stack.isEmpty()) return null; FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((reverse ? silkFirst(pair) : shootingFirst(pair)) && applies(pair, stack)
                && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best == null ? null : new Gear(stack, best);
    }
    private static boolean ready(ServerPlayer owner) {
        return owner.isAlive() && !owner.isSpectator() && !owner.isCreative() && !owner.hasInfiniteMaterials();
    }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        if (!ready(owner) || !applies(gear.pair(), gear.stack())
            || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())) return false;
        int count = 0;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.stack()) count++;
        return count == 1;
    }
    private static boolean valid(ServerPlayer owner, Mark mark) {
        long now = owner.level().getGameTime();
        return now < mark.expires() && now >= mark.expires() - RangedSilkMath.MARK_TICKS
            && mark.dimension().equals(owner.level().dimension()) && owns(owner, mark.gear());
    }
    private static @Nullable Mark find(ServerPlayer owner, Gear gear) {
        List<Mark> list = MARKS.get(owner.getUUID()); if (list == null) return null;
        list.removeIf(mark -> !valid(owner, mark));
        Mark result = list.stream().filter(mark -> mark.gear().stack() == gear.stack() && mark.gear().pair().equals(gear.pair())).findFirst().orElse(null);
        if (list.isEmpty()) MARKS.remove(owner.getUUID()); return result;
    }
    private static @Nullable Mark matching(ConfirmedArrow.DamageEvidence hit, String primary) {
        for (FusedPair pair : hit.pairs()) {
            if (!shootingFirst(pair) || !primary.equals(pair.primaryId()) || !applies(pair, hit.launcher())) continue;
            Mark mark = find(hit.owner(), new Gear(hit.launcher(), pair));
            if (mark != null && mark.target().equals(hit.target().getUUID()) && !mark.arrow().equals(hit.arrow().getUUID())) return mark;
        }
        return null;
    }
    /** Add to the one real arrow damage call; it cannot create a second damage event. */
    public static float bonusDamage(@Nullable ConfirmedArrow.Damage evidence, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return amount;
        ConfirmedArrow.DamageEvidence hit = ConfirmedArrow.evidence(evidence); if (hit == null) return amount;
        Mark mark = matching(hit, RangedWearSkills.POWER);
        if (mark == null || !RangedWearSkills.POWER.equals(mark.gear().pair().primaryId())) return amount;
        float result = amount + RangedSilkMath.damage(mark.gear().pair().secondaryLevel());
        return Float.isFinite(result) ? result : amount;
    }
    private static void onHit(ConfirmedArrow.Hit hit) {
        FusedPair pair = hit.pair(); if (!shootingFirst(pair)) return;
        Gear gear = new Gear(hit.launcher(), pair); if (!owns(hit.owner(), gear)) return;
        Mark previous = find(hit.owner(), gear);
        boolean focused = previous != null && previous.target().equals(hit.target().getUUID())
            && !previous.arrow().equals(hit.arrow().getUUID());
        if (focused && RangedWearSkills.PUNCH.equals(pair.primaryId()) && PUSHES.size() < MAX_PUSHES)
            PUSHES.put(hit.arrow(), new Push(gear, hit.owner().getUUID(), hit.target().getUUID(), hit.owner().level().dimension(), hit.owner().level().getGameTime()));
        if (focused && RangedWearSkills.FLAME.equals(pair.primaryId()) && hit.burning() && hit.target().isAlive()
            && hit.target().getType() != EntityType.ENDERMAN && !hit.target().fireImmune()) {
            // Vanilla Flame has already lit this target for five seconds. Extend actual
            // burning rather than re-igniting for less than the native duration, and never
            // shorten a longer independent fire source.
            int existing = hit.target().getRemainingFireTicks();
            if (existing > 0 && existing < 200)
                hit.target().igniteForTicks(Math.min(200, existing + RangedSilkMath.fireBonusTicks(pair.secondaryLevel())));
        }
        List<Mark> list = MARKS.get(hit.owner().getUUID());
        if (!hit.target().isAlive()) {
            if (list != null) { list.removeIf(mark -> mark.gear().stack() == gear.stack() && mark.gear().pair().equals(gear.pair())); if (list.isEmpty()) MARKS.remove(hit.owner().getUUID()); }
            return;
        }
        if (list == null) {
            if (MARKS.size() >= MAX_OWNERS) return;
            list = new ArrayList<>(); MARKS.put(hit.owner().getUUID(), list);
        }
        list.removeIf(mark -> mark.gear().stack() == gear.stack() && mark.gear().pair().equals(gear.pair()));
        if (list.size() >= MAX_GEAR) list.removeFirst();
        list.add(new Mark(gear, hit.target().getUUID(), hit.arrow().getUUID(), hit.owner().level().dimension(),
            hit.owner().level().getGameTime() + RangedSilkMath.MARK_TICKS));
        hit.owner().level().sendParticles(ParticleTypes.ENCHANT, hit.location().x, hit.location().y, hit.location().z, 7, .15D, .15D, .15D, .01D);
        CompendiumDiscoveryTracker.recordDiscovery(hit.owner(), pair.primaryId(), pair.secondaryId());
    }
    /** Only the matching previously marked target receives this arrow's Punch addition. */
    public static float bonusKnockback(AbstractArrow arrow, Entity target, DamageSource source, float value) {
        Push push = PUSHES.remove(arrow);
        if (push == null || !(arrow.getOwner() instanceof ServerPlayer owner) || !push.owner().equals(owner.getUUID())
            || !push.target().equals(target.getUUID()) || source.getDirectEntity() != arrow || source.getEntity() != owner
            || !push.dimension().equals(owner.level().dimension()) || owner.level().getGameTime() != push.born()
            || !owns(owner, push.gear()) || !Float.isFinite(value)) return value;
        return Math.max(value, Math.min(16F, value + RangedSilkMath.knockback(push.gear().pair().secondaryLevel())));
    }
    private static boolean valid(ServerPlayer owner, Recent recent) {
        long now = owner.level().getGameTime(); AbstractArrow arrow = recent.arrow();
        return now >= recent.born() && now - recent.born() < RangedSilkMath.ARROW_TICKS
            && recent.dimension().equals(owner.level().dimension()) && owns(owner, recent.gear())
            && arrow.isAlive() && arrow.level() == owner.level() && owner.level().getEntity(arrow.getUUID()) == arrow
            && arrow.getOwner() == owner && !PhantomVolley.isGhost(arrow);
    }
    private static @Nullable Recent recent(ServerPlayer owner, Gear gear) {
        List<Recent> list = RECENT.get(owner.getUUID()); if (list == null) return null;
        list.removeIf(entry -> !valid(owner, entry));
        Recent found = list.stream().filter(entry -> entry.gear().stack() == gear.stack() && entry.gear().pair().equals(gear.pair())).findFirst().orElse(null);
        if (list.isEmpty()) RECENT.remove(owner.getUUID()); return found;
    }
    public static boolean hasEquipped(ServerPlayer owner) {
        return selected(owner.getMainHandItem(), true) != null || selected(owner.getOffhandItem(), true) != null;
    }
    public static boolean activate(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu) return false;
        Gear main = selected(owner.getMainHandItem(), true), off = selected(owner.getOffhandItem(), true);
        Recent candidate = main == null ? null : recent(owner, main);
        if (candidate == null && off != null) candidate = recent(owner, off);
        if (candidate == null) { owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_silk_missing"), true); return false; }
        AbstractArrow arrow = candidate.arrow(); ItemStack original = arrow.getPickupItemStackOrigin();
        // Restrict custom projectile pickup implementations until their own provenance and
        // item conversion can be verified; never change an infinite arrow's pickup policy.
        boolean vanillaArrow = (arrow.getClass() == Arrow.class && arrow.getType() == EntityType.ARROW)
            || (arrow.getClass() == SpectralArrow.class && arrow.getType() == EntityType.SPECTRAL_ARROW);
        Vec3 eye = owner.getEyePosition(), position = arrow.position();
        if (!vanillaArrow || arrow.pickup != AbstractArrow.Pickup.ALLOWED || arrow.isNoPhysics()
            || !((AbstractArrowAccessor) arrow).ef$isInGround() || arrow.shakeTime > 0
            || original.isEmpty() || original.getCount() != 1 || original.has(DataComponents.INTANGIBLE_PROJECTILE)
            || !RangedTideMath.finite(position) || !RangedTideMath.finite(eye)
            || !RangedSilkMath.inRange(eye.distanceToSqr(position))) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_silk_unrecoverable"), true); return false;
        }
        // Back off from the block face by a tiny amount; the entire approach must otherwise
        // be clear and loaded. This does not ray through the block that contains the arrow.
        Vec3 outward = eye.subtract(position);
        Vec3 endpoint = outward.lengthSqr() < .01D ? position : position.add(outward.normalize().scale(.1D));
        if (!VirtualCombatSight.clear(owner.level(), owner, eye, endpoint)
            || !owner.level().mayInteract(owner, arrow.blockPosition())) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_silk_blocked"), true); return false;
        }
        arrow.playerTouch(owner); // Vanilla capacity, effects, pickup policy, item data and discard; no copy is manufactured here.
        if (!arrow.isRemoved()) { owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_silk_full"), true); return false; }
        List<Recent> list = RECENT.get(owner.getUUID());
        if (list != null) { list.remove(candidate); if (list.isEmpty()) RECENT.remove(owner.getUUID()); }
        owner.level().sendParticles(ParticleTypes.ENCHANT, position.x, position.y, position.z, 8, .12D, .12D, .12D, .01D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, candidate.gear().pair().primaryId(), candidate.gear().pair().secondaryId());
        return true;
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(MARKS.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) { MARKS.remove(entry.getKey()); continue; }
            entry.getValue().removeIf(mark -> !valid(owner, mark) || !(owner.level().getEntity(mark.target()) instanceof net.minecraft.world.entity.LivingEntity target)
                || !target.isAlive() || !LoyaltyGuard.eligibleTarget(owner, target));
            if (entry.getValue().isEmpty()) MARKS.remove(entry.getKey());
        }
        for (var entry : List.copyOf(RECENT.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) { RECENT.remove(entry.getKey()); continue; }
            entry.getValue().removeIf(recent -> !valid(owner, recent));
            if (entry.getValue().isEmpty()) RECENT.remove(entry.getKey());
        }
        PUSHES.entrySet().removeIf(entry -> {
            Push push = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(push.owner());
            return owner == null || !owns(owner, push.gear()) || !push.dimension().equals(owner.level().dimension())
                || owner.level().getGameTime() != push.born() || entry.getKey().isRemoved();
        });
    }
    public static void clearPlayer(UUID id) { MARKS.remove(id); RECENT.remove(id); PUSHES.entrySet().removeIf(entry -> entry.getValue().owner().equals(id)); }
    public static void clearServer() { MARKS.clear(); RECENT.clear(); PUSHES.clear(); }
}
