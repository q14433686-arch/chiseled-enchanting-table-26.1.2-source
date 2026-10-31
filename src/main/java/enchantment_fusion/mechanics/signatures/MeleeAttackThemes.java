package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.mutable.MutableFloat;

/** Shared target semantics and once-only native-primary replay for selected melee templates. */
public final class MeleeAttackThemes {
    public static final String SHARP = "minecraft:sharpness", SMITE = "minecraft:smite", BANE = "minecraft:bane_of_arthropods";
    private static final Set<String> IDS = Set.of(SHARP, SMITE, BANE);
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final Map<String, BiPredicate<FusedPair, ItemStack>> PRIMARY_TEMPLATES = new LinkedHashMap<>();
    private static final ThreadLocal<Boolean> PRIMARY_REPLAY = ThreadLocal.withInitial(() -> false);
    private MeleeAttackThemes() {}

    public static boolean isAttack(String id) { return IDS.contains(id); }
    public static boolean supportsWeapon(Set<FusionCarrier> roles) { return roles != null && roles.stream().anyMatch(WEAPONS::contains); }
    public static boolean matchesTarget(String id, LivingEntity target) {
        if (SHARP.equals(id)) return true;
        var type = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType());
        return SMITE.equals(id) && type.is(EntityTypeTags.UNDEAD) || BANE.equals(id) && type.is(EntityTypeTags.ARTHROPOD);
    }
    public static boolean eligible(ServerPlayer owner, LivingEntity target, String id) {
        return target.level() == owner.level() && matchesTarget(id, target) && LoyaltyGuard.eligibleTarget(owner, target)
            && owner.level().mayInteract(owner, target.blockPosition()) && (!(target instanceof Player other) || other.canHarmPlayer(owner));
    }
    /** Register one actually implemented attack-first template, not coarse profile mappings. */
    public static void registerPrimaryTemplate(String name, BiPredicate<FusedPair, ItemStack> applicable) {
        if (name == null || name.isBlank() || applicable == null) throw new IllegalArgumentException("Invalid melee template");
        PRIMARY_TEMPLATES.put(name, applicable);
    }
    private static boolean ownedTemplate(FusedPair pair, ItemStack stack) {
        if (!isAttack(pair.primaryId())) return false;
        for (var applies : PRIMARY_TEMPLATES.values()) if (applies.test(pair, stack)) return true;
        return false;
    }
    private static boolean replayedEarlier(ItemStack weapon, String primary) {
        return SoulThreadSkills.replaysPrimary(weapon, primary) || SoulBladeSkills.replaysPrimary(weapon, primary)
            || SegmentThreadSkills.replaysPrimary(weapon, primary);
    }
    public static float nonNativeDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float value) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || MatrixEffectExecutor.isExecuting()
            || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get())
            || !(source.getEntity() instanceof ServerPlayer owner) || source.getDirectEntity() != owner
            || owner.getMainHandItem() != weapon) return value;
        FusedEnchantmentList pairs = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (pairs.isEmpty()) return value;
        Set<String> processed = new HashSet<>(); float result = value;
        for (FusedPair pair : pairs.pairs()) {
            String id = pair.primaryId();
            if (!ownedTemplate(pair, weapon) || !processed.add(id) || replayedEarlier(weapon, id)) continue;
            Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
            if (enchantment == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) continue;
            MutableFloat modified = new MutableFloat(result); PRIMARY_REPLAY.set(true);
            try { enchantment.value().modifyDamage(level, pair.primaryLevel(), weapon, victim, source, modified); }
            finally { PRIMARY_REPLAY.remove(); }
            if (Float.isFinite(modified.floatValue())) result = modified.floatValue();
        }
        return result;
    }
    public static void nonNativePostAttack(ServerLevel level, Player player, ItemStack weapon, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || MatrixEffectExecutor.isExecuting()
            || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get())
            || !(player instanceof ServerPlayer owner) || owner.getMainHandItem() != weapon
            || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        FusedEnchantmentList pairs = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (pairs.isEmpty()) return;
        Set<String> processed = new HashSet<>();
        for (FusedPair pair : pairs.pairs()) {
            String id = pair.primaryId();
            if (!ownedTemplate(pair, weapon) || !processed.add(id) || replayedEarlier(weapon, id)) continue;
            Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
            if (enchantment == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) continue;
            PRIMARY_REPLAY.set(true);
            try { enchantment.value().doPostAttack(level, pair.primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
                EnchantmentTarget.ATTACKER, victim, source); }
            finally { PRIMARY_REPLAY.remove(); }
        }
    }
}
