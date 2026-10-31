package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Delayed melee thorns and a short contact curtain born from real fire mitigation. */
public final class FireThornsSkills {
    public static final String THORNS = "minecraft:thorns", FIRE = "minecraft:fire_protection";
    public static final String AMBUSH = "pyro_thorns", CURTAIN = "cinder_backlash";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int MAX_MARKS = 512, MAX_PENDING = 256;
    private record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private record Key(UUID owner, UUID attacker, ResourceKey<Level> dimension) {}
    private record Mark(Gear gear, long expires) {}
    private record Retaliation(Key key, Gear gear) {}
    private static final class Ring {
        final Gear gear;
        final ResourceKey<Level> dimension;
        final long created;
        final Map<UUID, Long> readyAt = new HashMap<>();
        long expires;
        Ring(Gear gear, ResourceKey<Level> dimension, long created) { this.gear = gear; this.dimension = dimension; this.created = created; }
    }
    private static final Map<Key, Mark> MARKS = new HashMap<>();
    private static final List<Retaliation> PENDING = new ArrayList<>();
    private static final Map<UUID, Ring> RINGS = new HashMap<>();
    private static final ThreadLocal<Boolean> IN_SKILL = ThreadLocal.withInitial(() -> false);
    private FireThornsSkills() {}
    private static int param(String id, String key, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, key, fallback, min, max);
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (THORNS.equals(pair.primaryId()) && FIRE.equals(pair.secondaryId()) && AMBUSH.equals(signature)
            || FIRE.equals(pair.primaryId()) && THORNS.equals(pair.secondaryId()) && CURTAIN.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return FireTradeoffSkills.supportsCarrier(roles); }
    public static boolean supportsHost(ItemStack stack) { return FireTradeoffSkills.supportsHost(stack); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && supportsHost(stack);
    }
    private static @Nullable Gear gear(ServerPlayer player, EquipmentSlot slot, boolean ambush) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!FireTradeoffSkills.slotMatches(stack, slot)) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((ambush ? THORNS : FIRE).equals(pair.primaryId())
                && SignatureCatalog.resolve(pair).map(e -> applies(pair, stack, e.id())).orElse(false)) return new Gear(slot, stack, pair);
        }
        return null;
    }
    private static @Nullable Gear first(ServerPlayer player, boolean ambush) {
        for (EquipmentSlot slot : SLOTS) { Gear item = gear(player, slot, ambush); if (item != null) return item; }
        return null;
    }
    private static boolean equipped(ServerPlayer player, Gear gear) {
        if (player.getItemBySlot(gear.slot()) != gear.stack() || !FireTradeoffSkills.slotMatches(gear.stack(), gear.slot())) return false;
        return gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            && SignatureCatalog.resolve(gear.pair()).map(e -> applies(gear.pair(), gear.stack(), e.id())).orElse(false);
    }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (gear.stack().isEmpty() || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            || !SignatureCatalog.resolve(gear.pair()).map(e -> applies(gear.pair(), gear.stack(), e.id())).orElse(false)) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    /** Explicit vanilla melee types only; unknown modded damage types keep normal Thorns. */
    private static boolean melee(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
            || source.is(DamageTypes.SPEAR) || source.is(DamageTypes.MACE_SMASH) || source.is(DamageTypes.STING);
    }
    private static boolean legalAttacker(ServerPlayer wearer, LivingEntity attacker) {
        if (attacker == wearer || !attacker.isAlive() || attacker.isInvulnerable() || attacker.isSpectator()
            || wearer.isAlliedTo(attacker) || attacker.isAlliedTo(wearer) || !wearer.level().mayInteract(wearer, attacker.blockPosition())) return false;
        if (attacker instanceof Player player) return !player.getAbilities().invulnerable
            && wearer.canHarmPlayer(player) && player.canHarmPlayer(wearer);
        return attacker instanceof Mob && !PhantomVolley.isProtected(wearer, attacker);
    }
    /** Replace only this pair's instant melee POST_ATTACK; projectile and other primary effects remain native. */
    public static boolean suppressInstantThorns(Enchantment enchantment, ServerLevel level, EnchantedItemInUse item,
                                                  EnchantmentTarget side, Entity victim, DamageSource source) {
        if (side != EnchantmentTarget.VICTIM || !(victim instanceof ServerPlayer player) || item.owner() != player
            || item.inSlot() == null || player.getItemBySlot(item.inSlot()) != item.itemStack() || !melee(source)
            || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || Boolean.TRUE.equals(IN_SKILL.get())) return false;
        if (!FusionStationOperations.lookupEnchantment(level.registryAccess(), THORNS).map(h -> h.value() == enchantment).orElse(false)) return false;
        Gear gear = gear(player, item.inSlot(), true);
        return gear != null && gear.stack() == item.itemStack();
    }
    /** Observe actual health/absorption loss once; defer any retaliation outside the current hit. */
    public static void observeDamage(ServerPlayer wearer, DamageSource source, float beforeHealth, float beforeAbsorption) {
        if (Boolean.TRUE.equals(IN_SKILL.get()) || wearer.isSpectator() || !melee(source)
            || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || !legalAttacker(wearer, attacker)) return;
        float lost = Math.max(0.0F, beforeHealth - wearer.getHealth()) + Math.max(0.0F, beforeAbsorption - wearer.getAbsorptionAmount());
        if (!Float.isFinite(lost) || lost <= 0.0F) return;
        Gear gear = first(wearer, true);
        if (gear == null) return;
        long now = wearer.level().getGameTime(); Key key = new Key(wearer.getUUID(), attacker.getUUID(), wearer.level().dimension());
        Mark previous = MARKS.get(key);
        if (previous != null && now < previous.expires() && previous.gear().stack() == gear.stack()
            && previous.gear().pair().equals(gear.pair()) && equipped(wearer, previous.gear())) {
            if (PENDING.size() >= MAX_PENDING) return; // retain the marker if the bounded queue is busy
            MARKS.remove(key);
            PENDING.add(new Retaliation(key, previous.gear()));
        } else {
            if (MARKS.size() >= MAX_MARKS && !MARKS.containsKey(key)) return;
            MARKS.put(key, new Mark(gear, now + param(AMBUSH, "mark_lifetime_ticks", 120, 20, 1200)));
            wearer.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6D,
                attacker.getZ(), 8, 0.2D, 0.25D, 0.2D, 0.01D);
            CompendiumDiscoveryTracker.recordDiscovery(wearer, THORNS, FIRE);
        }
    }
    /** Called before a different fusion's optional extra reduction; real enchantment/resistance mitigation only. */
    public static void observeFire(ServerPlayer wearer, DamageSource source, float beforeMagic, float afterMagic) {
        if (Boolean.TRUE.equals(IN_SKILL.get()) || !ready(wearer) || !source.is(DamageTypeTags.IS_FIRE)
            || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !Float.isFinite(beforeMagic) || !Float.isFinite(afterMagic)
            || beforeMagic <= afterMagic) return;
        Gear gear = first(wearer, false); if (gear == null) return;
        Ring ring = RINGS.get(wearer.getUUID());
        if (ring == null || ring.gear.stack() != gear.stack() || ring.gear.slot() != gear.slot() || !ring.gear.pair().equals(gear.pair())
            || !ring.dimension.equals(wearer.level().dimension()) || wearer.level().getGameTime() >= ring.expires) {
            ring = new Ring(gear, wearer.level().dimension(), wearer.level().getGameTime()); RINGS.put(wearer.getUUID(), ring);
            wearer.level().sendParticles(ParticleTypes.FLAME, wearer.getX(), wearer.getY() + 0.7D, wearer.getZ(),
                14, 0.5D, 0.35D, 0.5D, 0.02D);
        }
        ring.expires = wearer.level().getGameTime() + param(CURTAIN, "curtain_lifetime_ticks", 80, 20, 600);
        CompendiumDiscoveryTracker.recordDiscovery(wearer, FIRE, THORNS);
    }
    private static boolean clearPath(ServerLevel level, ServerPlayer owner, Vec3 to) {
        return level.clipIncludingBorder(new ClipContext(owner.getEyePosition(), to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    private static void retaliate(MinecraftServer server, Retaliation pending) {
        ServerLevel level = server.getLevel(pending.key().dimension()); ServerPlayer wearer = server.getPlayerList().getPlayer(pending.key().owner());
        Entity entity = level == null ? null : level.getEntity(pending.key().attacker());
        if (level == null || wearer == null || wearer.level() != level || !ready(wearer) || !equipped(wearer, pending.gear())
            || !(entity instanceof LivingEntity attacker) || !legalAttacker(wearer, attacker)
            || wearer.distanceToSqr(attacker) > 64.0D || !clearPath(level, wearer, attacker.getBoundingBox().getCenter())) return;
        float amount = (float) Math.min(20.0D, 1.0D + Math.clamp(pending.gear().pair().primaryLevel(), 1, 10));
        boolean nested = Boolean.TRUE.equals(IN_SKILL.get()); IN_SKILL.set(true);
        try {
            if (attacker.hurtServer(level, level.damageSources().thorns(wearer), amount)) {
                if (!attacker.fireImmune()) attacker.igniteForSeconds(param(AMBUSH, "ignition_seconds", 3, 1, 20));
                // Equivalent to a successful ordinary Thorns proc: real item wear and ordinary break handling.
                if (equipped(wearer, pending.gear())) pending.gear().stack().hurtAndBreak(
                    param(AMBUSH, "retaliation_wear", 2, 0, 20), wearer, pending.gear().slot());
                CompendiumDiscoveryTracker.recordDiscovery(wearer, THORNS, FIRE);
            }
        } finally { if (nested) IN_SKILL.set(true); else IN_SKILL.remove(); }
    }
    private static void tickRing(ServerLevel level, ServerPlayer wearer, Ring ring) {
        if (!equipped(wearer, ring.gear)) return; // can resume if the same equipment is re-equipped before expiry
        if ((level.getGameTime() - ring.created) % 5 != 0) return;
        int levelBonus = Math.clamp(ring.gear.pair().secondaryLevel() - 1, 0, 9);
        double reach = Math.min(3.0D, (param(CURTAIN, "contact_reach_tenths", 7, 1, 30) + levelBonus) / 10.0D);
        AABB bounds = wearer.getBoundingBox().inflate(reach, 0.4D, reach);
        level.sendParticles(ParticleTypes.FLAME, wearer.getX(), wearer.getY() + 0.7D, wearer.getZ(), 8, reach, 0.4D, reach, 0.005D);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, bounds, e -> LoyaltyGuard.eligibleTarget(wearer, e));
        victims.sort(Comparator.comparingDouble(e -> e.distanceToSqr(wearer)));
        ring.readyAt.entrySet().removeIf(e -> level.getGameTime() >= e.getValue());
        int count = 0;
        for (LivingEntity victim : victims) {
            if (!ready(wearer) || !equipped(wearer, ring.gear) || RINGS.get(wearer.getUUID()) != ring) break;
            if (ring.readyAt.containsKey(victim.getUUID()) || !level.mayInteract(wearer, victim.blockPosition())
                || !clearPath(level, wearer, victim.getBoundingBox().getCenter())) continue;
            if (count++ >= 8 || ring.readyAt.size() >= 64) break;
            float damage = Math.min(10.0F, 1.0F + 0.5F * Math.clamp(ring.gear.pair().secondaryLevel(), 1, 10));
            boolean nested = Boolean.TRUE.equals(IN_SKILL.get()); IN_SKILL.set(true);
            try {
                if (victim.hurtServer(level, level.damageSources().thorns(wearer), damage)) {
                    ring.readyAt.put(victim.getUUID(), level.getGameTime() + param(CURTAIN, "target_cooldown_ticks", 20, 5, 200));
                    if (!victim.fireImmune()) victim.igniteForSeconds(param(CURTAIN, "ignition_seconds", 2, 1, 20));
                }
            } finally { if (nested) IN_SKILL.set(true); else IN_SKILL.remove(); }
        }
    }
    public static void tickServer(MinecraftServer server) {
        List<Retaliation> pending = List.copyOf(PENDING); PENDING.clear();
        for (Retaliation strike : pending) retaliate(server, strike);
        for (var entry : List.copyOf(MARKS.entrySet())) {
            Key key = entry.getKey(); Mark mark = entry.getValue();
            ServerLevel level = server.getLevel(key.dimension()); ServerPlayer wearer = server.getPlayerList().getPlayer(key.owner());
            Entity attacker = level == null ? null : level.getEntity(key.attacker());
            if (level == null || wearer == null || wearer.level() != level || !ready(wearer) || !owns(wearer, mark.gear())
                || level.getGameTime() >= mark.expires() || !(attacker instanceof LivingEntity living) || !living.isAlive()) MARKS.remove(key, mark);
        }
        for (var entry : List.copyOf(RINGS.entrySet())) {
            ServerPlayer wearer = server.getPlayerList().getPlayer(entry.getKey()); Ring ring = entry.getValue();
            if (wearer == null || !ready(wearer) || !wearer.level().dimension().equals(ring.dimension)
                || !owns(wearer, ring.gear) || wearer.level().getGameTime() >= ring.expires) { RINGS.remove(entry.getKey(), ring); continue; }
            tickRing(wearer.level(), wearer, ring);
        }
    }
    public static void onDisconnect(UUID player) {
        MARKS.keySet().removeIf(k -> k.owner().equals(player) || k.attacker().equals(player));
        PENDING.removeIf(r -> r.key().owner().equals(player) || r.key().attacker().equals(player)); RINGS.remove(player);
    }
    public static void clear() { MARKS.clear(); PENDING.clear(); RINGS.clear(); IN_SKILL.remove(); }
}
