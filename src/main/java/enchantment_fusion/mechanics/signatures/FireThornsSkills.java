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
import java.util.HashSet;
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
    private static final Map<UUID, Set<Key>> MARKS_BY_ATTACKER = new HashMap<>();
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
    private static boolean directMelee(DamageSource source, LivingEntity attacker) {
        return !Boolean.TRUE.equals(IN_SKILL.get()) && melee(source) && source.getEntity() == attacker
            && source.getDirectEntity() == attacker && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            && !source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS);
    }
    private static void putMark(Key key, Mark mark) {
        MARKS.put(key, mark);
        MARKS_BY_ATTACKER.computeIfAbsent(key.attacker(), ignored -> new HashSet<>()).add(key);
    }
    private static void removeMark(Key key, Mark expected) {
        if (!MARKS.remove(key, expected)) return;
        Set<Key> indexed = MARKS_BY_ATTACKER.get(key.attacker());
        if (indexed != null) {
            indexed.remove(key);
            if (indexed.isEmpty()) MARKS_BY_ATTACKER.remove(key.attacker());
        }
    }
    private static Map<Key, Mark> snapshot(LivingEntity attacker) {
        Set<Key> keys = MARKS_BY_ATTACKER.get(attacker.getUUID());
        if (keys == null || keys.isEmpty()) return Map.of();
        Map<Key, Mark> result = new HashMap<>();
        for (Key key : List.copyOf(keys)) {
            Mark mark = MARKS.get(key);
            if (mark != null) result.put(key, mark);
        }
        return result;
    }
    /** Only marks present before this hit may be consumed; a mark created by this hit is not its own second hit. */
    private static Set<UUID> consume(ServerLevel level, LivingEntity attacker, Map<Key, Mark> candidates) {
        if (candidates.isEmpty()) return Set.of();
        Set<UUID> triggered = new HashSet<>();
        MinecraftServer server = level.getServer();
        for (var entry : candidates.entrySet()) {
            Key key = entry.getKey(); Mark mark = entry.getValue();
            if (MARKS.get(key) != mark || !key.attacker().equals(attacker.getUUID()) || !key.dimension().equals(level.dimension())) continue;
            ServerPlayer wearer = server.getPlayerList().getPlayer(key.owner());
            if (wearer == null || wearer.level() != level || !ready(wearer) || !owns(wearer, mark.gear()) || level.getGameTime() >= mark.expires()) {
                removeMark(key, mark); continue;
            }
            if (!equipped(wearer, mark.gear()) || !legalAttacker(wearer, attacker) || PENDING.size() >= MAX_PENDING) continue;
            removeMark(key, mark);
            PENDING.add(new Retaliation(key, mark.gear()));
            triggered.add(key.owner());
        }
        return triggered;
    }
    /** Observe actual health/absorption loss on any living target and mark an equipped victim separately. */
    public static void observeDamage(LivingEntity victim, DamageSource source, float beforeHealth, float beforeAbsorption) {
        if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker)
            || !directMelee(source, attacker)) return;
        float lost = Math.max(0.0F, beforeHealth - victim.getHealth()) + Math.max(0.0F, beforeAbsorption - victim.getAbsorptionAmount());
        if (!Float.isFinite(lost) || lost <= 0.0F) return;
        Set<UUID> triggered = consume(level, attacker, snapshot(attacker));
        if (!(victim instanceof ServerPlayer wearer) || !ready(wearer) || triggered.contains(wearer.getUUID())
            || !legalAttacker(wearer, attacker)) return;
        Gear gear = first(wearer, true);
        if (gear == null) return;
        long now = level.getGameTime(); Key key = new Key(wearer.getUUID(), attacker.getUUID(), level.dimension());
        Mark previous = MARKS.get(key);
        if (previous != null && now < previous.expires() && previous.gear().stack() == gear.stack()
            && previous.gear().pair().equals(gear.pair()) && equipped(wearer, previous.gear())) return;
        if (MARKS.size() >= MAX_MARKS && previous == null) return;
        putMark(key, new Mark(gear, now + param(AMBUSH, "mark_lifetime_ticks", 120, 20, 1200)));
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6D,
            attacker.getZ(), 8, 0.2D, 0.25D, 0.2D, 0.01D);
        CompendiumDiscoveryTracker.recordDiscovery(wearer, THORNS, FIRE);
    }
    /** Snapshot prior marks around real vanilla attack calls, including targets with no LivingEntity health. */
    public static final class AttackSnapshot {
        private final Map<Key, Mark> candidates;
        private final float health, absorption;
        private AttackSnapshot(Map<Key, Mark> candidates, float health, float absorption) {
            this.candidates = candidates; this.health = health; this.absorption = absorption;
        }
    }
    public static @Nullable AttackSnapshot beforeAttack(LivingEntity attacker, Entity victim, DamageSource source) {
        if (!(attacker.level() instanceof ServerLevel) || !directMelee(source, attacker)) return null;
        Map<Key, Mark> candidates = snapshot(attacker);
        if (candidates.isEmpty()) return null;
        float health = victim instanceof LivingEntity living ? living.getHealth() : 0.0F;
        float absorption = victim instanceof LivingEntity living ? living.getAbsorptionAmount() : 0.0F;
        return new AttackSnapshot(candidates, health, absorption);
    }
    public static void afterAttack(@Nullable AttackSnapshot frame, LivingEntity attacker, Entity victim, DamageSource source, boolean accepted) {
        if (frame == null || !accepted || !(attacker.level() instanceof ServerLevel level) || !directMelee(source, attacker)) return;
        if (victim instanceof LivingEntity living) {
            float lost = Math.max(0.0F, frame.health - living.getHealth()) + Math.max(0.0F, frame.absorption - living.getAbsorptionAmount());
            if (!Float.isFinite(lost) || lost <= 0.0F) return;
        }
        consume(level, attacker, frame.candidates);
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
            || !(entity instanceof LivingEntity attacker) || !legalAttacker(wearer, attacker)) return;
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
                || level.getGameTime() >= mark.expires() || !(attacker instanceof LivingEntity living) || !living.isAlive()) removeMark(key, mark);
        }
        for (var entry : List.copyOf(RINGS.entrySet())) {
            ServerPlayer wearer = server.getPlayerList().getPlayer(entry.getKey()); Ring ring = entry.getValue();
            if (wearer == null || !ready(wearer) || !wearer.level().dimension().equals(ring.dimension)
                || !owns(wearer, ring.gear) || wearer.level().getGameTime() >= ring.expires) { RINGS.remove(entry.getKey(), ring); continue; }
            tickRing(wearer.level(), wearer, ring);
        }
    }
    public static void onDisconnect(UUID player) {
        for (var entry : List.copyOf(MARKS.entrySet())) {
            Key key = entry.getKey();
            if (key.owner().equals(player) || key.attacker().equals(player)) removeMark(key, entry.getValue());
        }
        PENDING.removeIf(r -> r.key().owner().equals(player) || r.key().attacker().equals(player)); RINGS.remove(player);
    }
    public static void clear() { MARKS.clear(); MARKS_BY_ATTACKER.clear(); PENDING.clear(); RINGS.clear(); IN_SKILL.remove(); }
}
