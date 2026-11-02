package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Active fire-to-wear conversion or stored mitigation released as a directional heat wave. */
public final class FireTradeoffSkills {
    public static final String PROTECTION = "minecraft:protection", FIRE = "minecraft:fire_protection";
    public static final String COMPENSATE = "furnace_aegis", WAVE = "searing_counter";
    private static final Set<FusionCarrier> CARRIERS = Set.of(FusionCarrier.HEAD_ARMOR, FusionCarrier.CHEST_ARMOR,
        FusionCarrier.LEG_ARMOR, FusionCarrier.FOOT_ARMOR, FusionCarrier.SHIELD);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private record Mode(Gear gear, ResourceKey<Level> dimension) {}
    private static final class Heat {
        final ItemStack stack;
        final FusedPair pair;
        final ResourceKey<Level> dimension;
        float amount;
        long expires;
        Heat(ServerPlayer player, Gear gear) {
            this.stack = gear.stack(); this.pair = gear.pair(); this.dimension = player.level().dimension();
        }
    }
    private static final Map<UUID, Mode> MODES = new HashMap<>();
    private static final Map<UUID, Heat> HEAT = new HashMap<>();
    private static final ThreadLocal<Boolean> SETTLING_WEAR = ThreadLocal.withInitial(() -> false);
    private FireTradeoffSkills() {}
    private static int param(String id, String key, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, key, fallback, min, max);
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (PROTECTION.equals(pair.primaryId()) && FIRE.equals(pair.secondaryId()) && COMPENSATE.equals(signature)
            || FIRE.equals(pair.primaryId()) && PROTECTION.equals(pair.secondaryId()) && WAVE.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return roles != null && roles.stream().anyMatch(CARRIERS::contains); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && stack.isDamageableItem()
            && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static boolean supportsHost(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isDamageableItem() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static boolean slotMatches(ItemStack stack, EquipmentSlot slot) {
        Set<FusionCarrier> roles = FusionCarrier.classify(stack);
        return switch (slot) {
            case MAINHAND, OFFHAND -> roles.contains(FusionCarrier.SHIELD);
            case HEAD -> roles.contains(FusionCarrier.HEAD_ARMOR);
            case CHEST -> roles.contains(FusionCarrier.CHEST_ARMOR);
            case LEGS -> roles.contains(FusionCarrier.LEG_ARMOR);
            case FEET -> roles.contains(FusionCarrier.FOOT_ARMOR);
            default -> false;
        };
    }
    private static @Nullable Gear gear(ServerPlayer player, EquipmentSlot slot, boolean compensation) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!slotMatches(stack, slot)) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((compensation ? PROTECTION : FIRE).equals(pair.primaryId())
                && SignatureCatalog.resolve(pair).map(entry -> applies(pair, stack, entry.id())).orElse(false)) return new Gear(slot, stack, pair);
        }
        return null;
    }
    private static boolean equipped(ServerPlayer player, Gear gear) {
        if (player.getItemBySlot(gear.slot()) != gear.stack() || !slotMatches(gear.stack(), gear.slot())) return false;
        Gear current = gear(player, gear.slot(), PROTECTION.equals(gear.pair().primaryId()));
        return current != null && current.pair().equals(gear.pair());
    }
    private static @Nullable Gear first(ServerPlayer player, boolean compensation) {
        for (EquipmentSlot slot : SLOTS) { Gear gear = gear(player, slot, compensation); if (gear != null) return gear; }
        return null;
    }
    public static boolean hasEquipped(ServerPlayer player, boolean compensation) { return first(player, compensation) != null; }
    public static boolean hasLegacyPassive(ServerPlayer player) {
        for (EquipmentSlot slot : SignatureCatalog.ALL_EQUIP_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                String id = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
                if (COMPENSATE.equals(id) && !applies(pair, stack, id)) return true;
            }
        }
        return false;
    }
    private static boolean eligible(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    private static boolean same(ServerPlayer player, ItemStack stack, FusedPair pair, ResourceKey<Level> dimension) {
        if (!eligible(player) || stack.isEmpty() || !player.level().dimension().equals(dimension)
            || !SignatureCatalog.resolve(pair).map(entry -> applies(pair, stack, entry.id())).orElse(false)
            || !stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair)) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == stack) return true;
        return false;
    }
    private static boolean heatEquipped(ServerPlayer player, Heat heat) {
        for (EquipmentSlot slot : SLOTS) {
            Gear current = gear(player, slot, false);
            if (current != null && current.stack() == heat.stack && current.pair().equals(heat.pair)) return true;
        }
        return false;
    }
    /** Collect the one maximum requested level before any protection effect is evaluated. */
    static Map<String, Integer> protectionLevels(ItemStack stack, EquipmentSlot slot, DamageSource source) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        if (stack == null || stack.isEmpty()) return levels;
        FusedEnchantmentList fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (fused.isEmpty() || !slotMatches(stack, slot)) return levels;
        for (FusedPair pair : fused.pairs()) {
            String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
            boolean blastPair = BlastDefenseSkills.applies(pair, stack, signature);
            boolean primaryDefense = applies(pair, stack, signature)
                || (FIRE.equals(pair.primaryId()) && FireThornsSkills.applies(pair, stack, signature))
                || (MeleeGuardSkills.isProtectionFirst(pair) && MeleeGuardSkills.applies(pair, stack))
                || (RangedGuardSkills.protectionFirst(pair) && RangedGuardSkills.applies(pair, stack))
                || GrapnelDefense.applies(pair, stack) || blastPair;
            if (primaryDefense) levels.merge(pair.primaryId(), pair.primaryLevel(), Math::max);
            // The reactive-plate secondary is protection only during the actual explosion pass.
            if (blastPair && BlastDefenseSkills.isGuard(pair) && source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                levels.merge(BlastDefenseSkills.BLAST, pair.secondaryLevel(), Math::max);
            }
        }
        return levels;
    }

    /** Preserve native primary data-pack protection on actual armor/shields without replaying a native slot effect. */
    public static float primaryProtection(ServerLevel level, ServerPlayer player, DamageSource source, float vanilla) {
        if (player.level() != level) return vanilla;
        float total = vanilla;
        for (EquipmentSlot slot : SLOTS) {
            if (player.level() != level) return vanilla;
            ItemStack stack = player.getItemBySlot(slot);
            for (var entry : protectionLevels(stack, slot, source).entrySet()) {
                if (player.level() != level) return vanilla;
                Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), entry.getKey()).orElse(null);
                if (primary == null) continue;
                int nativeLevel = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary);
                if (nativeLevel > 0 && primary.value().matchingSlot(slot)
                    || ProtectionProjection.applied(player, source, stack, primary.value())) continue;
                MutableFloat value = new MutableFloat(total);
                primary.value().modifyDamageProtection(level, Math.max(entry.getValue(), nativeLevel), stack, player, source, value);
                total = value.floatValue();
                if (!Float.isFinite(total) || player.level() != level) return vanilla;
            }
        }
        return total;
    }
    /** Toggle one actual held shield or worn armor; a different item never inherits the mode. */
    public static boolean toggle(ServerPlayer player) {
        if (!eligible(player)) return false;
        Gear selected = first(player, true);
        if (selected == null) return false;
        Mode existing = MODES.get(player.getUUID());
        if (existing != null && existing.gear().stack() == selected.stack() && existing.dimension().equals(player.level().dimension())) {
            MODES.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fire_compensation_off"), true);
        } else {
            MODES.put(player.getUUID(), new Mode(selected, player.level().dimension()));
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fire_compensation_on"), true);
        }
        return true;
    }
    /** Called after normal armor/resistance/enchantment mitigation but before absorption and health. */
    public static float afterMagic(ServerPlayer player, DamageSource source, float beforeMagic, float afterMagic) {
        if (!eligible(player) || !source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
            || !Float.isFinite(beforeMagic) || !Float.isFinite(afterMagic)
            || Boolean.TRUE.equals(SETTLING_WEAR.get())) return afterMagic;
        Gear accumulator = first(player, false);
        float mitigated = Math.max(0.0F, beforeMagic - afterMagic);
        if (accumulator != null && mitigated > 0.0F) {
            Heat heat = HEAT.get(player.getUUID());
            if (heat == null || heat.stack != accumulator.stack() || !heat.pair.equals(accumulator.pair())
                || !heat.dimension.equals(player.level().dimension()) || player.level().getGameTime() >= heat.expires) {
                heat = new Heat(player, accumulator); HEAT.put(player.getUUID(), heat);
            }
            int cap = param(WAVE, "heat_cap", 40, 1, 200);
            int before = (int) heat.amount;
            heat.amount = FireTradeoffMath.addHeat(heat.amount, mitigated, cap);
            heat.expires = player.level().getGameTime() + param(WAVE, "heat_lifetime_ticks", 400, 20, 1200);
            if ((int) heat.amount > before) player.sendSystemMessage(Component.translatable(
                "message.enchantment_fusion.fire_heat_ready", (int) heat.amount, cap), true);
            CompendiumDiscoveryTracker.recordDiscovery(player, FIRE, PROTECTION);
        }
        Mode mode = MODES.get(player.getUUID());
        if (mode == null || !mode.dimension().equals(player.level().dimension()) || !equipped(player, mode.gear())
            || player.hasInfiniteMaterials()) return afterMagic;
        ItemStack stack = mode.gear().stack();
        FireTradeoffMath.Plan plan = FireTradeoffMath.plan(afterMagic,
            param(COMPENSATE, "fire_conversion_percent", 50, 0, 100),
            param(COMPENSATE, "wear_per_damage", 2, 1, 100), stack.getMaxDamage() - stack.getDamageValue());
        if (plan.requestedWear() <= 0) return afterMagic;
        SETTLING_WEAR.set(true);
        try {
            // Uses the ordinary durability/enchantment pipeline but vanilla caps at one remaining point.
            stack.hurtWithoutBreaking(plan.requestedWear(), player);
        } finally { SETTLING_WEAR.remove(); }
        if (!equipped(player, mode.gear())) return afterMagic;
        CompendiumDiscoveryTracker.recordDiscovery(player, PROTECTION, FIRE);
        return Math.max(0.0F, afterMagic - plan.prevented());
    }
    private static boolean lineOfSight(ServerLevel level, ServerPlayer owner, Vec3 to) {
        return level.clipIncludingBorder(new ClipContext(owner.getEyePosition(), to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    /** Consume stored heat only when a legal visible target can actually be pushed. */
    public static boolean release(ServerPlayer player) {
        Heat heat = HEAT.get(player.getUUID());
        if (heat == null || player.level().getGameTime() >= heat.expires || !same(player, heat.stack, heat.pair, heat.dimension)
            || !heatEquipped(player, heat) || heat.amount < param(WAVE, "heat_required", 4, 1, 200)) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fire_wave_no_heat"), true);
            return false;
        }
        ServerLevel level = player.level();
        Vec3 origin = player.getEyePosition(), aim = player.getLookAngle().normalize();
        if (aim.lengthSqr() < 1.0E-8D) return false;
        double radius = param(WAVE, "wave_range_tenths", 60, 10, 120) / 10.0D;
        AABB bounds = AABB.ofSize(origin, radius * 2, radius * 2, radius * 2);
        List<Entity> targets = new ArrayList<>();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, bounds,
            e -> LoyaltyGuard.eligibleTarget(player, e) && level.mayInteract(player, e.blockPosition()))) {
            Vec3 delta = victim.getBoundingBox().getCenter().subtract(origin);
            if (delta.lengthSqr() > 1.0E-8D && delta.lengthSqr() <= radius * radius && aim.dot(delta.normalize()) >= 0.5D
                && lineOfSight(level, player, victim.getBoundingBox().getCenter())
                && victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0D) targets.add(victim);
        }
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, bounds,
            p -> p.isAlive() && !p.noPhysics && p.getDeltaMovement().lengthSqr() > 0.01D
                && (!(p instanceof AbstractArrow arrow) || !((AbstractArrowAccessor) arrow).ef$isInGround())
                && p.getOwner() instanceof LivingEntity attacker
                && LoyaltyGuard.eligibleTarget(player, attacker) && level.mayInteract(player, p.blockPosition()))) {
            Vec3 delta = projectile.position().subtract(origin);
            if (delta.lengthSqr() > 1.0E-8D && delta.lengthSqr() <= radius * radius && aim.dot(delta.normalize()) >= 0.5D
                && lineOfSight(level, player, projectile.position())) targets.add(projectile);
        }
        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fire_wave_no_target"), true);
            return false;
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        HEAT.remove(player.getUUID()); // reserve before callbacks and velocity changes
        double force = Math.min(2.5D, 0.55D + heat.amount / 20.0D);
        int count = 0;
        for (Entity target : targets) {
            if (count++ >= 32) break;
            double resistance = target instanceof LivingEntity living ? Math.clamp(1.0D - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D) : 1.0D;
            target.push(aim.x * force * resistance, Math.max(0.05D, aim.y * force * resistance), aim.z * force * resistance);
            target.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.FLAME, origin.x + aim.x, origin.y + aim.y, origin.z + aim.z,
            18, 0.35D, 0.25D, 0.35D, 0.04D);
        CompendiumDiscoveryTracker.recordDiscovery(player, FIRE, PROTECTION);
        return true;
    }
    public static void tickPlayer(ServerPlayer player) {
        Mode mode = MODES.get(player.getUUID());
        if (mode != null && (!eligible(player) || !mode.dimension().equals(player.level().dimension()) || !equipped(player, mode.gear()))) MODES.remove(player.getUUID());
        Heat heat = HEAT.get(player.getUUID());
        if (heat != null && (player.level().getGameTime() >= heat.expires || !same(player, heat.stack, heat.pair, heat.dimension))) HEAT.remove(player.getUUID());
    }
    public static void onDisconnect(UUID player) { MODES.remove(player); HEAT.remove(player); }
    public static void clear() { MODES.clear(); HEAT.clear(); SETTLING_WEAR.remove(); }
}
