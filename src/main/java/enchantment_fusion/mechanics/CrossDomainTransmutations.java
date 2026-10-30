package enchantment_fusion.mechanics;

import enchantment_fusion.mechanics.transmute.ArmorRelicTransmutes;
import enchantment_fusion.mechanics.transmute.MeleeSpearTransmutes;
import enchantment_fusion.mechanics.transmute.MiningFishingTransmutes;
import enchantment_fusion.mechanics.transmute.RangedProjectileTransmutes;
import enchantment_fusion.mechanics.transmute.RelicWorldItemTransmutes;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Clean facade for Enchantment Fusion's curated Cross-Domain Transmutation system ("异位化形").
 * <p>
 * Mapping data lives in {@code data/enchantment_fusion/enchantment_fusion/transmutations.json}
 * ({@link TransmuteCatalog}), while domain behavior is partitioned across modular {@code <500}-line
 * handlers in {@code enchantment_fusion.mechanics.transmute.*}.
 */
public final class CrossDomainTransmutations {
    public enum TargetRole {
        SWORD,
        SPEAR,
        AXE,
        MACE,
        TRIDENT,
        BOW,
        CROSSBOW,
        DIG_TOOL,
        SHIELD,
        HEAD_ARMOR,
        CHEST_ARMOR,
        LEG_ARMOR,
        FOOT_ARMOR,
        ELYTRA,
        FISHING_ROD,
        TOTEM,
        GLASS_BOTTLE,
        CONSUMABLE,
        OTHER;

        public boolean isMeleeWeapon() {
            return this == SWORD || this == SPEAR || this == AXE || this == MACE || this == TRIDENT;
        }

        public boolean isRangedWeapon() {
            return this == BOW || this == CROSSBOW;
        }

        public boolean isAttackWeapon() {
            return isMeleeWeapon() || isRangedWeapon();
        }

        public boolean isTool() {
            return this == DIG_TOOL || this == AXE;
        }

        public boolean isArmor() {
            return this == HEAD_ARMOR || this == CHEST_ARMOR || this == LEG_ARMOR || this == FOOT_ARMOR;
        }

        public boolean isArmorOrShield() {
            return isArmor() || this == SHIELD || this == ELYTRA;
        }
    }

    public record TransmuteVariant(String id, String sourceEnchantId) {
        public String titleKey() {
            return "transmute.enchantment_fusion." + id + ".title";
        }

        public String descKey() {
            return "transmute.enchantment_fusion." + id + ".desc";
        }
    }

    public record SmeltOutcome(ItemStack result, float xp) {}

    private CrossDomainTransmutations() {}

    public static List<TransmuteVariant> allVariants() {
        return TransmuteCatalog.allVariants();
    }

    public static void onPlayerDisconnect(UUID playerId) {
        TransmuteRuntimeSupport.onPlayerDisconnect(playerId);
    }

    public static void clearRuntimeState() {
        TransmuteRuntimeSupport.clearRuntimeState();
    }

    public static boolean isEligibleCrossHost(ItemStack stack) {
        return TransmuteCatalog.isEligibleCrossHost(stack);
    }

    public static boolean isEligibleCrossDomainHost(ItemStack stack) {
        return TransmuteCatalog.isEligibleCrossHost(stack);
    }

    public static boolean isSpecialNonEquipmentHost(ItemStack stack) {
        return TransmuteCatalog.isSpecialNonEquipmentHost(stack);
    }

    public static TargetRole classifyTarget(ItemStack stack) {
        return TransmuteCatalog.classifyTarget(stack);
    }

    public static Optional<TransmuteVariant> resolve(String enchantId, ItemStack target) {
        return TransmuteCatalog.resolve(enchantId, target);
    }

    public static Optional<TransmuteVariant> resolve(ItemStack target, String enchantId) {
        return TransmuteCatalog.resolve(enchantId, target);
    }

    public static Optional<TransmuteVariant> resolveForRole(String enchantId, TargetRole role, boolean damageable) {
        return TransmuteCatalog.resolveForRole(enchantId, role, damageable);
    }

    public static boolean hasAnyTransmutedEnchants(ItemStack stack) {
        return TransmuteCatalog.hasAnyTransmutedEnchants(stack);
    }

    public static int getTransmutedLevel(ItemStack stack, String variantId) {
        return TransmuteCatalog.getTransmutedLevel(stack, variantId);
    }

    public static int getStandaloneEnchantLevel(ItemStack stack, String enchId) {
        return TransmuteCatalog.getStandaloneEnchantLevel(stack, enchId);
    }

    public static int getEffectiveUseSpeedBoost(ItemStack stack) {
        return TransmuteCatalog.getEffectiveUseSpeedBoost(stack);
    }

    public static double modifyEntityReach(Player player, double baseReach) {
        return MeleeSpearTransmutes.modifyEntityReach(player, baseReach);
    }

    public static float modifyAttackDelay(Player player, float baseDelay) {
        return MeleeSpearTransmutes.modifyAttackDelay(player, baseDelay);
    }

    public static float modifyDestroySpeed(Player player, ItemStack tool, BlockState state, float speed) {
        return MiningFishingTransmutes.modifyDestroySpeed(player, tool, state, speed);
    }

    public static boolean onBeforeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        return MiningFishingTransmutes.onBeforeBlockBreak(level, player, pos, state);
    }

    public static void onPostDestroyBlockDrops(
        ServerLevel level,
        ServerPlayer player,
        BlockPos pos,
        BlockState state,
        ItemStack toolSnapshot
    ) {
        MiningFishingTransmutes.onPostDestroyBlockDrops(level, player, pos, state, toolSnapshot);
    }

    public static boolean smeltFreshBlockDropsInPlace(
        ServerLevel level,
        BlockPos pos,
        BlockState brokenState,
        float xpMultiplier
    ) {
        return MiningFishingTransmutes.smeltFreshBlockDropsInPlace(level, pos, brokenState, xpMultiplier);
    }

    public static @Nullable SmeltOutcome resolveSmeltedStack(
        @Nullable ServerLevel level,
        ItemStack rawStack,
        @Nullable Block brokenBlock
    ) {
        return MiningFishingTransmutes.resolveSmeltedStack(level, rawStack, brokenBlock);
    }

    public static void onAfterBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        MiningFishingTransmutes.onAfterBlockBreak(level, player, pos, state);
    }

    public static boolean allowAttackTarget(Player player, Entity target) {
        return MeleeSpearTransmutes.allowAttackTarget(player, target);
    }

    public static void onMeleeAttack(ServerLevel level, ServerPlayer player, LivingEntity target, boolean fullCharge) {
        MeleeSpearTransmutes.onMeleeAttack(level, player, target, fullCharge);
    }

    public static void onFlyingBladeHit(ServerLevel level, ServerPlayer player, ItemStack weapon, LivingEntity target) {
        MeleeSpearTransmutes.onFlyingBladeHit(level, player, weapon, target);
    }

    public static boolean canTriggerDirectRightClick(boolean playerSneaking, ItemStack stack) {
        return MeleeSpearTransmutes.canTriggerDirectRightClick(playerSneaking, stack);
    }

    public static boolean hasReadyRightClickAbility(Player player, ItemStack stack) {
        return MeleeSpearTransmutes.hasReadyRightClickAbility(player, stack);
    }

    public static boolean onUseItem(ServerLevel level, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        return MeleeSpearTransmutes.onUseItem(level, player, hand, stack);
    }

    public static void onFishingReel(
        ServerLevel level,
        ServerPlayer player,
        ItemStack rod,
        @Nullable LivingEntity hookedMob,
        Vec3 bobberPos
    ) {
        MiningFishingTransmutes.onFishingReel(level, player, rod, hookedMob, bobberPos);
    }

    public static void onConsumableFinished(ServerLevel level, ServerPlayer player, ItemStack snapshotBeforeConsume) {
        RelicWorldItemTransmutes.onConsumableFinished(level, player, snapshotBeforeConsume);
    }

    public static void onProjectileLaunched(
        ServerLevel level,
        Projectile proj,
        ServerPlayer shooter,
        ItemStack weapon,
        double dirX,
        double dirY,
        double dirZ,
        float velocity,
        float inaccuracy
    ) {
        RangedProjectileTransmutes.onProjectileLaunched(level, proj, shooter, weapon, dirX, dirY, dirZ, velocity, inaccuracy);
    }

    public static void onProjectileTick(ServerLevel level, Projectile proj, ServerPlayer shooter, ItemStack weapon) {
        RangedProjectileTransmutes.onProjectileTick(level, proj, shooter, weapon);
    }

    public static void onProjectileImpact(
        ServerLevel level,
        @Nullable Projectile proj,
        ServerPlayer shooter,
        ItemStack weapon,
        @Nullable LivingEntity target,
        Vec3 hitPos
    ) {
        RangedProjectileTransmutes.onProjectileImpact(level, proj, shooter, weapon, target, hitPos);
    }

    public static boolean allowIncomingDamage(ServerLevel level, ServerPlayer player, DamageSource source, float amount) {
        return ArmorRelicTransmutes.allowIncomingDamage(level, player, source);
    }

    public static void onAfterDamage(ServerLevel level, ServerPlayer player, DamageSource source, float damageTaken, boolean blocked) {
        ArmorRelicTransmutes.onAfterDamage(level, player, source, damageTaken, blocked);
    }

    public static boolean allowPlayerDeath(ServerLevel level, ServerPlayer player, DamageSource source) {
        return RelicWorldItemTransmutes.allowPlayerDeath(level, player, source);
    }

    public static void onEntityKilled(ServerLevel level, ServerPlayer killer, LivingEntity victim) {
        ArmorRelicTransmutes.onEntityKilled(level, killer, victim);
    }

    public static void tickServerPlayer(ServerLevel level, ServerPlayer player) {
        ArmorRelicTransmutes.tickServerPlayer(level, player);
    }

    public static void tickWorldItems(ServerLevel level) {
        RelicWorldItemTransmutes.tickWorldItems(level);
    }

    public static boolean checkSkillCooldown(ServerPlayer player, String skillKey, long now, int ticks) {
        return TransmuteRuntimeSupport.checkSkillCooldown(player, skillKey, now, ticks);
    }

    public static @Nullable Item mapItemAutoSmeltFallback(Item rawItem, @Nullable Block brokenBlock) {
        return MiningFishingTransmutes.mapItemAutoSmeltFallback(rawItem, brokenBlock);
    }
}
