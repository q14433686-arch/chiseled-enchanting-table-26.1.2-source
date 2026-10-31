package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Actual direct kills create virtual soul webs; confirmed arthropod hits build and release a cocoon. */
public final class SoulThreadSkills {
    public static final String SMITE = "minecraft:smite", BANE = "minecraft:bane_of_arthropods";
    public static final String WEB = "blight_halo", COCOON = "soul_cocoon";
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final int MAX_WEBS = 128, MAX_COCOONS = 256;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Web(UUID owner, ResourceKey<Level> dimension, Gear gear, Vec3 position, long created, long expires) {}
    private static final class Cocoon {
        final UUID owner, target;
        final ResourceKey<Level> dimension;
        final Gear gear;
        int pressure;
        long expires;
        Cocoon(ServerPlayer owner, LivingEntity target, Gear gear) {
            this.owner = owner.getUUID(); this.target = target.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear;
        }
    }
    public static final class Swing {
        final @Nullable Swing previous;
        final Player player;
        final Entity target;
        final ItemStack weapon;
        final boolean full;
        Swing(@Nullable Swing previous, Player player, Entity target) {
            this.previous = previous; this.player = player; this.target = target; this.weapon = player.getWeaponItem();
            this.full = player instanceof ServerPlayer && player.getAttackStrengthScale(0.5F) >= FusionMatrixRegistry.serverSnapshot().economy().meleeFullCharge();
        }
    }
    public static final class Hit {
        final @Nullable Hit previous;
        final @Nullable ServerPlayer player;
        final @Nullable LivingEntity target;
        final @Nullable Gear gear;
        final DamageSource source;
        final boolean full, undead, arthropod;
        boolean confirmed, lethal;
        Hit(@Nullable Hit previous, @Nullable ServerPlayer player, @Nullable LivingEntity target, @Nullable Gear gear,
            DamageSource source, boolean full, boolean undead, boolean arthropod) {
            this.previous = previous; this.player = player; this.target = target; this.gear = gear; this.source = source;
            this.full = full; this.undead = undead; this.arthropod = arthropod;
        }
    }
    private static final ThreadLocal<Swing> SWING = new ThreadLocal<>();
    private static final ThreadLocal<Hit> HIT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IN_PRIMARY_REPLAY = ThreadLocal.withInitial(() -> false);
    private static final List<Web> WEBS = new ArrayList<>();
    private static final Map<UUID, Cocoon> COCOONS = new HashMap<>();
    private SoulThreadSkills() {}
    private static int param(String id, String key, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, key, fallback, min, max);
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (SMITE.equals(pair.primaryId()) && BANE.equals(pair.secondaryId()) && WEB.equals(signature)
            || BANE.equals(pair.primaryId()) && SMITE.equals(pair.secondaryId()) && COCOON.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return roles != null && roles.stream().anyMatch(WEAPONS::contains); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    private static @Nullable Gear selected(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !supportsCarrier(FusionCarrier.classify(stack))) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(e -> applies(pair, stack, e.id())).orElse(false)) return new Gear(stack, pair);
        }
        return null;
    }
    /** Whether this handler will replay this primary on the exact selected legacy pair. */
    public static boolean replaysPrimary(ItemStack weapon, String primary) {
        Gear gear = selected(weapon);
        return gear != null && primary.equals(gear.pair().primaryId());
    }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (gear.stack().isEmpty() || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            || !SignatureCatalog.resolve(gear.pair()).map(e -> applies(gear.pair(), gear.stack(), e.id())).orElse(false)) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean playerReady(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    private static boolean explicitMob(ServerPlayer player, LivingEntity target) {
        return target instanceof Mob && target.isAlive() && !target.isInvulnerable()
            && !PhantomVolley.isProtected(player, target) && player.level().mayInteract(player, target.blockPosition());
    }
    public static Swing beginSwing(Player player, Entity target) { Swing swing = new Swing(SWING.get(), player, target); SWING.set(swing); return swing; }
    public static void endSwing(Swing swing) { if (swing.previous == null) SWING.remove(); else SWING.set(swing.previous); }
    public static boolean swingFull(Player player, ItemStack stack, Entity target) {
        Swing swing = SWING.get();
        return swing != null && swing.player == player && swing.weapon == stack && swing.target == target && swing.full;
    }
    public static boolean stabFull(Player player, EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND || stack != player.getMainHandItem()) return false;
        KineticWeapon kinetic = stack.get(DataComponents.KINETIC_WEAPON);
        boolean using = kinetic != null && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND && player.getUseItem() == stack;
        return GlazedStrikeEffect.stabReady(using, player.getTicksUsingItem(), using ? kinetic.delayTicks() : 0,
            !using && player.getAttackStrengthScale(0.5F) >= FusionMatrixRegistry.serverSnapshot().economy().meleeFullCharge());
    }
    /** Always push a frame, including unrelated nested attacks, to mask the outer hit. */
    public static Hit beginHit(Player player, ItemStack stack, Entity entity, DamageSource source, boolean full) {
        ServerPlayer owner = player instanceof ServerPlayer p ? p : null;
        LivingEntity target = entity instanceof LivingEntity living ? living : null;
        Gear gear = owner != null && owner.getMainHandItem() == stack ? selected(stack) : null;
        boolean qualifies = owner != null && playerReady(owner) && target != null && explicitMob(owner, target)
            && source.getEntity() == owner && source.getDirectEntity() == owner
            && !MatrixEffectExecutor.isExecuting() && !GlazedStrikeEffect.isApplyingBonus()
            && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get()) && !Boolean.TRUE.equals(IN_PRIMARY_REPLAY.get());
        boolean undead = target != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD);
        boolean arthropod = target != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.ARTHROPOD);
        Hit frame = new Hit(HIT.get(), owner, target, qualifies ? gear : null, source, full, undead, arthropod);
        HIT.set(frame);
        return frame;
    }
    public static void afterDamage(LivingEntity entity, DamageSource source, float damage, boolean blocked) {
        Hit frame = HIT.get();
        if (frame != null && frame.gear != null && frame.target == entity && frame.source == source && !blocked && damage > 0.0F) frame.confirmed = true;
    }
    public static void afterDeath(LivingEntity entity, DamageSource source) {
        Hit frame = HIT.get();
        boolean ours = frame != null && frame.gear != null && frame.target == entity && frame.source == source;
        if (ours) { frame.confirmed = true; frame.lethal = true; }
        // Preserve an existing cocoon only until this exact lethal direct hit unwinds.
        if (!ours || !frame.arthropod || !BANE.equals(frame.gear.pair().primaryId())) COCOONS.remove(entity.getUUID());
    }
    public static void endHit(Hit frame, boolean succeeded) {
        if (frame.previous == null) HIT.remove(); else HIT.set(frame.previous);
        ServerPlayer owner = frame.player; LivingEntity target = frame.target; Gear gear = frame.gear;
        if (!succeeded || !frame.confirmed || owner == null || target == null || gear == null || !playerReady(owner)
            || owner.getMainHandItem() != gear.stack() || !owns(owner, gear) || owner.level() != target.level()
            || !owner.level().mayInteract(owner, target.blockPosition())) return;
        ServerLevel level = owner.level();
        if (SMITE.equals(gear.pair().primaryId()) && frame.undead && frame.lethal) createWeb(level, owner, gear, target.position());
        else if (BANE.equals(gear.pair().primaryId()) && frame.arthropod && (frame.lethal || explicitMob(owner, target))) {
            hitCocoon(level, owner, gear, target, frame.full, frame.lethal);
        }
    }
    private static void createWeb(ServerLevel level, ServerPlayer owner, Gear gear, Vec3 position) {
        if (WEBS.size() >= MAX_WEBS) return;
        List<Web> owned = WEBS.stream().filter(w -> w.owner().equals(owner.getUUID())).toList();
        if (owned.size() >= 2) WEBS.remove(owned.getFirst());
        long now = level.getGameTime();
        WEBS.add(new Web(owner.getUUID(), level.dimension(), gear, position, now,
            now + param(WEB, "web_lifetime_ticks", 100, 20, 400)));
        level.sendParticles(ParticleTypes.SOUL, position.x, position.y + 0.4D, position.z, 24, 0.6D, 0.3D, 0.6D, 0.03D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, SMITE, BANE);
    }
    private static void hitCocoon(ServerLevel level, ServerPlayer owner, Gear gear, LivingEntity target, boolean full, boolean lethal) {
        long now = level.getGameTime(); Cocoon existing = COCOONS.get(target.getUUID());
        if (existing != null && (!existing.owner.equals(owner.getUUID()) || existing.gear.stack() != gear.stack()
            || !existing.gear.pair().equals(gear.pair()) || !existing.dimension.equals(level.dimension()) || now >= existing.expires)) {
            COCOONS.remove(target.getUUID(), existing); existing = null;
        }
        if (existing == null) {
            if (lethal || COCOONS.size() >= MAX_COCOONS) return; // a first lethal hit cannot create a dead cocoon
            existing = new Cocoon(owner, target, gear); existing.pressure = -1; COCOONS.put(target.getUUID(), existing);
        }
        int threshold = param(COCOON, "pressure_hits", 2, 1, 20);
        SoulThreadMath.Step step = SoulThreadMath.advance(existing.pressure, threshold, full);
        existing.pressure = step.pressure(); existing.expires = now + param(COCOON, "cocoon_lifetime_ticks", 160, 20, 1200);
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
            target.getZ(), 9, 0.25D, 0.3D, 0.25D, 0.015D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, BANE, SMITE);
        if (step.release()) {
            COCOONS.remove(target.getUUID(), existing);
            burst(level, owner, target, gear);
        } else if (lethal) {
            COCOONS.remove(target.getUUID(), existing);
        } else owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.soul_pressure", existing.pressure, threshold), true);
    }
    private static boolean clearPath(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        return level.clipIncludingBorder(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    private static void burst(ServerLevel level, ServerPlayer owner, LivingEntity center, Gear gear) {
        Vec3 origin = center.getBoundingBox().getCenter();
        double radius = Math.min(6.0D, (param(COCOON, "burst_range_tenths", 25, 10, 60)
            + 2 * Math.clamp(gear.pair().secondaryLevel() - 1, 0, 9)) / 10.0D);
        AABB area = AABB.ofSize(origin, radius * 2, radius * 2, radius * 2);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, area,
            entity -> entity == center ? explicitMob(owner, entity) : LoyaltyGuard.eligibleTarget(owner, entity));
        candidates.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        int count = 0;
        for (LivingEntity victim : candidates) {
            Vec3 delta = origin.subtract(victim.getBoundingBox().getCenter());
            if (delta.lengthSqr() > radius * radius || !level.mayInteract(owner, victim.blockPosition())
                || (victim != center && !clearPath(level, owner, origin, victim.getBoundingBox().getCenter()))) continue;
            if (count++ >= 32) break;
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
            if (victim != center && delta.lengthSqr() > 1.0E-8D) {
                Vec3 pull = delta.normalize().scale(0.28D); victim.push(pull.x, 0.04D, pull.z); victim.hurtMarked = true;
            }
        }
        level.sendParticles(ParticleTypes.SOUL, origin.x, origin.y, origin.z, 35, 0.65D, 0.4D, 0.65D, 0.06D);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.soul_released"), true);
    }
    /** Preserve the actual data-pack primary once on a non-native carrier such as a trident. */
    public static float nonNativeDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float vanilla) {
        if (Boolean.TRUE.equals(IN_PRIMARY_REPLAY.get()) || !(source.getEntity() instanceof ServerPlayer owner)
            || source.getDirectEntity() != owner || owner.getMainHandItem() != weapon) return vanilla;
        Gear gear = selected(weapon);
        if (gear == null) return vanilla;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        if (primary == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return vanilla;
        MutableFloat result = new MutableFloat(vanilla); IN_PRIMARY_REPLAY.set(true);
        try { primary.value().modifyDamage(level, gear.pair().primaryLevel(), weapon, victim, source, result); }
        finally { IN_PRIMARY_REPLAY.remove(); }
        return Float.isFinite(result.floatValue()) ? result.floatValue() : vanilla;
    }
    public static void nonNativePostAttack(ServerLevel level, Player attacker, ItemStack weapon, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(IN_PRIMARY_REPLAY.get()) || !(attacker instanceof ServerPlayer owner)
            || source.getEntity() != owner || source.getDirectEntity() != owner || owner.getMainHandItem() != weapon) return;
        Gear gear = selected(weapon);
        if (gear == null) return;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        if (primary == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return;
        IN_PRIMARY_REPLAY.set(true);
        try { primary.value().doPostAttack(level, gear.pair().primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
            EnchantmentTarget.ATTACKER, victim, source); }
        finally { IN_PRIMARY_REPLAY.remove(); }
    }
    public static void tickServer(MinecraftServer server) {
        for (Web web : List.copyOf(WEBS)) {
            ServerLevel level = server.getLevel(web.dimension()); ServerPlayer owner = server.getPlayerList().getPlayer(web.owner());
            if (level == null || owner == null || owner.level() != level || !playerReady(owner) || !owns(owner, web.gear())
                || level.getGameTime() >= web.expires()) { WEBS.remove(web); continue; }
            if ((level.getGameTime() - web.created()) % 10 != 0) continue;
            double radius = Math.min(6.0D, (param(WEB, "web_range_tenths", 25, 10, 60)
                + 2 * Math.clamp(web.gear().pair().secondaryLevel() - 1, 0, 9)) / 10.0D);
            level.sendParticles(ParticleTypes.SOUL, web.position().x, web.position().y + 0.4D, web.position().z,
                7, radius * 0.4D, 0.3D, radius * 0.4D, 0.01D);
            Vec3 center = web.position().add(0, 0.4D, 0);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                e -> LoyaltyGuard.eligibleTarget(owner, e));
            victims.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(center)));
            int count = 0;
            for (LivingEntity victim : victims) {
                Vec3 to = victim.getBoundingBox().getCenter();
                if (center.distanceToSqr(to) > radius * radius || !level.mayInteract(owner, victim.blockPosition())
                    || !clearPath(level, owner, center, to)) continue;
                if (count++ >= 32) break;
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 2));
            }
        }
        for (Cocoon cocoon : List.copyOf(COCOONS.values())) {
            ServerLevel level = server.getLevel(cocoon.dimension); ServerPlayer owner = server.getPlayerList().getPlayer(cocoon.owner);
            Entity target = level == null ? null : level.getEntity(cocoon.target);
            if (level == null || owner == null || owner.level() != level || !playerReady(owner) || !owns(owner, cocoon.gear)
                || level.getGameTime() >= cocoon.expires || !(target instanceof LivingEntity living) || !explicitMob(owner, living)) {
                COCOONS.remove(cocoon.target, cocoon);
            }
        }
    }
    public static void onDisconnect(UUID id) { WEBS.removeIf(w -> w.owner().equals(id)); COCOONS.values().removeIf(c -> c.owner.equals(id)); }
    public static void clear() { WEBS.clear(); COCOONS.clear(); SWING.remove(); HIT.remove(); IN_PRIMARY_REPLAY.remove(); }
}
