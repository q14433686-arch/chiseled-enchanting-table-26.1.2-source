package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** User's two blast/protection mechanics, scoped to actually worn armor. */
public final class BlastDefenseSkills {
    public static final String BLAST = "minecraft:blast_protection";
    public static final String PROTECTION = "minecraft:protection";
    public static final String COUNTER = "kinetic_payback";
    public static final String GUARD = "reactive_plate";
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Set<FusionCarrier> ARMOR = Set.of(FusionCarrier.HEAD_ARMOR, FusionCarrier.CHEST_ARMOR,
        FusionCarrier.LEG_ARMOR, FusionCarrier.FOOT_ARMOR);
    private static final ThreadLocal<Deque<ProtectionFrame>> PROTECTION_FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<DamageFrame>> DAMAGE_FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Boolean> IN_COUNTER = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();
    private static final int MAX_NESTING = 8;

    private BlastDefenseSkills() {}
    public record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private record Hit(long tick, float amount) {}
    private static final class Window {
        final ResourceKey<Level> dimension;
        final Gear gear;
        final Deque<Hit> hits = new ArrayDeque<>();
        Window(ResourceKey<Level> dimension, Gear gear) { this.dimension = dimension; this.gear = gear; }
    }
    public static final class DamageFrame {
        final ServerPlayer player; final DamageSource source; final Gear gear; float actual;
        DamageFrame(ServerPlayer player, DamageSource source, Gear gear) {
            this.player = player; this.source = source; this.gear = gear;
        }
    }
    public static final class ProtectionFrame {
        final ServerPlayer player; final DamageSource source; final List<Gear> gear;
        final Map<ItemStack, Float> observedMain = new IdentityHashMap<>();
        ProtectionFrame(ServerPlayer player, DamageSource source, List<Gear> gear) {
            this.player = player; this.source = source; this.gear = gear;
        }
    }

    public static boolean isCounter(@Nullable FusedPair pair) {
        return pair != null && BLAST.equals(pair.primaryId()) && PROTECTION.equals(pair.secondaryId());
    }
    public static boolean isGuard(@Nullable FusedPair pair) {
        return pair != null && PROTECTION.equals(pair.primaryId()) && BLAST.equals(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair, String id) {
        return (isCounter(pair) && COUNTER.equals(id)) || (isGuard(pair) && GUARD.equals(id));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return roles.stream().anyMatch(ARMOR::contains); }
    public static boolean applies(FusedPair pair, ItemStack item, String id) {
        return matches(pair, id) && supportsCarrier(FusionCarrier.classify(item));
    }
    private static boolean supportsSlot(ItemStack item, EquipmentSlot slot) {
        Set<FusionCarrier> roles = FusionCarrier.classify(item);
        return switch (slot) {
            case HEAD -> roles.contains(FusionCarrier.HEAD_ARMOR);
            case CHEST -> roles.contains(FusionCarrier.CHEST_ARMOR);
            case LEGS -> roles.contains(FusionCarrier.LEG_ARMOR);
            case FEET -> roles.contains(FusionCarrier.FOOT_ARMOR);
            default -> false;
        };
    }
    public static @Nullable Gear findInSlot(ServerPlayer player, EquipmentSlot slot, boolean counter) {
        ItemStack item = player.getItemBySlot(slot);
        if (item.isEmpty() || !supportsSlot(item, slot)) return null;
        for (FusedPair pair : item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((counter ? isCounter(pair) : isGuard(pair))
                && SignatureCatalog.resolve(pair).map(entry -> applies(pair, item, entry.id())).orElse(false)) {
                return new Gear(slot, item, pair);
            }
        }
        return null;
    }
    private static boolean stillWorn(ServerPlayer player, Gear gear) {
        return player.getItemBySlot(gear.slot()) == gear.stack() && !gear.stack().isEmpty()
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            && supportsSlot(gear.stack(), gear.slot())
            && SignatureCatalog.resolve(gear.pair()).map(entry -> applies(gear.pair(), gear.stack(), entry.id())).orElse(false);
    }
    private static boolean sameGear(Gear left, Gear right) {
        return left.slot() == right.slot() && left.stack() == right.stack() && left.pair().equals(right.pair());
    }
    public static @Nullable Gear strongest(ServerPlayer player) {
        Gear best = null;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            Gear candidate = findInSlot(player, slot, true);
            if (candidate != null && (best == null || candidate.pair().primaryLevel() + candidate.pair().secondaryLevel()
                > best.pair().primaryLevel() + best.pair().secondaryLevel())) best = candidate;
        }
        return best;
    }
    private static @Nullable Holder<Enchantment> enchant(ServerLevel level, String id) {
        return FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
    }
    private static boolean nativeEnchantment(ItemStack stack, @Nullable Holder<Enchantment> holder) {
        return holder != null && stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) > 0;
    }

    /** Minimum blast resistance for non-native armor; native resistance is never applied twice. */
    public static double explosionResistance(ServerPlayer player, double nativeResistance) {
        Gear gear = strongest(player);
        if (gear == null || !stillWorn(player, gear)) return nativeResistance;
        int percent = FusionConfigStore.signatureInt(COUNTER, "knockback_per_level_percent", 15, 0, 100);
        return BlastDefenseMath.knockbackResistance(nativeResistance, gear.pair().primaryLevel(), percent);
    }

    /** Wrap the single vanilla protection pass; do not replay any already-projected enchantment. */
    public static @Nullable ProtectionFrame beginProtection(ServerPlayer player, DamageSource source) {
        if (!source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return null;
        List<Gear> gear = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            Gear counter = findInSlot(player, slot, true), guard = findInSlot(player, slot, false);
            if (counter != null) gear.add(counter);
            if (guard != null) gear.add(guard);
        }
        Deque<ProtectionFrame> stack = PROTECTION_FRAMES.get();
        if (gear.isEmpty() && stack.isEmpty()) {
            PROTECTION_FRAMES.remove();
            return null;
        }
        // A nested call, including one with no selected gear, masks its parent.
        ProtectionFrame frame = new ProtectionFrame(player, source, stack.size() >= MAX_NESTING ? List.of() : gear);
        stack.push(frame);
        return frame;
    }
    public static void observeProtection(Enchantment enchantment, ServerLevel level, ItemStack item,
                                         Entity victim, DamageSource source, float before, float after) {
        Deque<ProtectionFrame> stack = PROTECTION_FRAMES.get();
        ProtectionFrame frame = stack.peek();
        if (frame == null) { PROTECTION_FRAMES.remove(); return; }
        if (frame.player != victim || frame.source != source || !Float.isFinite(before) || !Float.isFinite(after)) return;
        Holder<Enchantment> main = enchant(level, PROTECTION);
        if (main == null || main.value() != enchantment) return;
        if (enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION).stream()
            .anyMatch(effect -> !(effect.effect() instanceof AddValue))) return;
        for (Gear gear : frame.gear) {
            if (isGuard(gear.pair()) && gear.stack() == item) {
                frame.observedMain.merge(item, Math.max(0.0F, after - before), Float::sum);
                break;
            }
        }
    }
    public static float finishProtection(ProtectionFrame frame, float nativeTotal, ServerLevel level) {
        float total = nativeTotal;
        Holder<Enchantment> protection = enchant(level, PROTECTION), blast = enchant(level, BLAST);
        // First restore native primary effects only on armor on which they could not be projected.
        for (Gear gear : frame.gear) {
            if (!stillWorn(frame.player, gear)) continue;
            Holder<Enchantment> primary = isCounter(gear.pair()) ? blast : protection;
            if (primary == null || nativeEnchantment(gear.stack(), primary)) continue;
            MutableFloat value = new MutableFloat(total);
            primary.value().modifyDamageProtection(level, gear.pair().primaryLevel(), gear.stack(), frame.player, frame.source, value);
            total = value.floatValue();
        }
        for (Gear gear : frame.gear) {
            if (!isGuard(gear.pair()) || !stillWorn(frame.player, gear)) continue;
            // Apply the actual datapack secondary effect only if it is not already native.
            if (blast != null && !nativeEnchantment(gear.stack(), blast)) {
                MutableFloat value = new MutableFloat(total);
                blast.value().modifyDamageProtection(level, gear.pair().secondaryLevel(), gear.stack(), frame.player, frame.source, value);
                total = value.floatValue();
            }
            int percent = FusionConfigStore.signatureInt(GUARD, "protection_conversion_percent", 50, 0, 100);
            total += BlastDefenseMath.convertedProtection(frame.observedMain.getOrDefault(gear.stack(), 0.0F), percent);
            CompendiumDiscoveryTracker.recordDiscovery(frame.player, gear.pair().primaryId(), gear.pair().secondaryId());
        }
        return Float.isFinite(total) ? total : nativeTotal;
    }
    public static void endProtection(@Nullable ProtectionFrame frame) {
        if (frame == null) return;
        Deque<ProtectionFrame> stack = PROTECTION_FRAMES.get();
        if (stack.peek() == frame) stack.pop(); else stack.remove(frame);
        if (stack.isEmpty()) PROTECTION_FRAMES.remove();
    }

    /** Capture damage across actual health/absorption subtraction and defer the counter until after totem handling. */
    public static @Nullable DamageFrame beginDamage(ServerPlayer player, DamageSource source) {
        Deque<DamageFrame> stack = DAMAGE_FRAMES.get();
        boolean skip = Boolean.TRUE.equals(IN_COUNTER.get()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || stack.size() >= MAX_NESTING;
        Gear gear = skip ? null : strongest(player);
        if (gear == null && stack.isEmpty()) {
            DAMAGE_FRAMES.remove();
            return null;
        }
        // An empty nested frame masks the parent's victim/source even under recursive callbacks.
        DamageFrame frame = new DamageFrame(player, source, gear);
        stack.push(frame);
        return frame;
    }
    public static void observeActualDamage(ServerPlayer player, DamageSource source, float beforeHealth, float beforeAbsorption) {
        Deque<DamageFrame> stack = DAMAGE_FRAMES.get();
        DamageFrame frame = stack.peek();
        if (frame == null) { DAMAGE_FRAMES.remove(); return; }
        if (frame.gear == null || frame.player != player || frame.source != source) return;
        float damage = Math.max(0.0F, beforeHealth - player.getHealth())
            + Math.max(0.0F, beforeAbsorption - player.getAbsorptionAmount());
        if (Float.isFinite(damage) && damage > 0.0F) frame.actual += damage;
    }
    public static void endDamage(@Nullable DamageFrame frame, boolean success) {
        if (frame == null) return;
        Deque<DamageFrame> stack = DAMAGE_FRAMES.get();
        boolean balanced = stack.peek() == frame;
        if (balanced) stack.pop(); else stack.remove(frame);
        if (stack.isEmpty()) DAMAGE_FRAMES.remove();
        if (!balanced || !success || frame.gear == null || frame.actual <= 0.0F
            || !frame.player.isAlive() || !stillWorn(frame.player, frame.gear)) return;
        ServerPlayer player = frame.player;
        ServerLevel level = player.level();
        long now = level.getGameTime();
        int duration = FusionConfigStore.signatureInt(COUNTER, "counter_window_ticks", 40, 1, 1200);
        Window window = WINDOWS.get(player.getUUID());
        if (window == null || !window.dimension.equals(level.dimension()) || !sameGear(window.gear, frame.gear)) {
            window = new Window(level.dimension(), frame.gear);
            WINDOWS.put(player.getUUID(), window);
        }
        while (!window.hits.isEmpty() && now - window.hits.peekFirst().tick() >= duration) window.hits.removeFirst();
        // Aggregate same-tick events to bound memory even when other mods produce many hits.
        float amount = frame.actual;
        if (!window.hits.isEmpty() && window.hits.peekLast().tick() == now) {
            amount = Math.min(Float.MAX_VALUE, window.hits.removeLast().amount() + amount);
        }
        window.hits.addLast(new Hit(now, amount));
        double total = window.hits.stream().mapToDouble(Hit::amount).sum();
        int threshold = FusionConfigStore.signatureInt(COUNTER, "counter_threshold_health", 10, 1, 1000);
        if (total > threshold) {
            WINDOWS.remove(player.getUUID());
            counter(level, player, frame.gear);
        }
    }

    private static void counter(ServerLevel level, ServerPlayer player, Gear gear) {
        int blastLevel = gear.pair().primaryLevel(), protectionLevel = gear.pair().secondaryLevel();
        double base = FusionConfigStore.signatureInt(COUNTER, "counter_radius_tenths", 30, 1, 1000) / 10.0D;
        double cap = FusionConfigStore.signatureInt(COUNTER, "counter_radius_cap", 64, 1, 128);
        float baseDamage = FusionConfigStore.signatureInt(COUNTER, "counter_damage_tenths", 40, 1, 1000) / 10.0F;
        float damageCap = FusionConfigStore.signatureInt(COUNTER, "counter_damage_cap", 100, 1, 1000);
        double radius = BlastDefenseMath.counterRadius(base, cap, blastLevel, protectionLevel, player.getHealth(), player.getMaxHealth());
        float damage = BlastDefenseMath.counterDamage(baseDamage, damageCap, blastLevel, protectionLevel, player.getHealth(), player.getMaxHealth());
        if (radius <= 0.0D || damage <= 0.0F) return;
        Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 8, 0.5D, 0.4D, 0.5D, 0.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        int maxTargets = FusionConfigStore.signatureInt(COUNTER, "counter_target_cap", 256, 1, 1024);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D),
            entity -> LoyaltyGuard.eligibleTarget(player, entity) && entity.distanceToSqr(center) <= radius * radius);
        targets.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        IN_COUNTER.set(true);
        try {
            int considered = 0;
            for (LivingEntity target : targets) {
                if (considered++ >= maxTargets) break;
                if (!level.hasChunkAt(target.blockPosition()) || !level.mayInteract(player, target.blockPosition())) continue;
                if (level.clip(new ClipContext(center, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
                if (target.hurtServer(level, level.damageSources().explosion(player, player), damage)) {
                    Vec3 away = target.position().subtract(player.position()).normalize().scale(0.65D);
                    target.push(away.x, 0.25D, away.z);
                }
            }
        } finally { IN_COUNTER.remove(); }
        CompendiumDiscoveryTracker.recordDiscovery(player, gear.pair().primaryId(), gear.pair().secondaryId());
    }

    public static void tickServer(MinecraftServer server) {
        WINDOWS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Window window = entry.getValue();
            if (player == null || !player.isAlive() || !player.level().dimension().equals(window.dimension) || !stillWorn(player, window.gear)) return true;
            int duration = FusionConfigStore.signatureInt(COUNTER, "counter_window_ticks", 40, 1, 1200);
            long now = player.level().getGameTime();
            while (!window.hits.isEmpty() && now - window.hits.peekFirst().tick() >= duration) window.hits.removeFirst();
            return window.hits.isEmpty();
        });
    }
    public static void onDisconnect(UUID player) { WINDOWS.remove(player); }
    public static void clear() { WINDOWS.clear(); }
}
