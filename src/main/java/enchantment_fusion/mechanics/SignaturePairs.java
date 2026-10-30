package enchantment_fusion.mechanics;

import enchantment_fusion.mechanics.signatures.ArmorCurseSignatures;
import enchantment_fusion.mechanics.signatures.ElementalSpearMaceSignatures;
import enchantment_fusion.mechanics.signatures.FlameKineticEffects;
import enchantment_fusion.mechanics.signatures.MeleeBladeSignatures;
import enchantment_fusion.mechanics.signatures.MeleeTacticsSignatures;
import enchantment_fusion.mechanics.signatures.MiningFishingSignatures;
import enchantment_fusion.mechanics.signatures.RangedSignatures;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Clean facade for Enchantment Fusion's curated directional Signature Resonance system.
 * <p>
 * Mapping data lives in {@code data/enchantment_fusion/enchantment_fusion/signatures.json}
 * ({@link SignatureCatalog}), while domain behavior is partitioned across modular {@code <500}-line
 * handlers in {@code enchantment_fusion.mechanics.signatures.*}.
 */
public final class SignaturePairs {
    public record SignatureEntry(
        String id,
        String primaryId,
        String secondaryId
    ) {
        public String titleKey() {
            return "signature.enchantment_fusion." + id + ".title";
        }

        public String descKey() {
            return "signature.enchantment_fusion." + id + ".desc";
        }
    }

    private SignaturePairs() {}

    /** Preconditions that make the entire combat signature a no-op when unsatisfied.
     * Evaluate before charging its cooldown, so a partial hit cannot suppress the next eligible hit.
     * Signatures with an independent partial-hit effect are deliberately not listed here.
     */
    public static boolean isCombatTriggerEligible(String signatureId, boolean fullCharge, boolean sprinting, boolean hasTarget) {
        return switch (signatureId) {
            case "reaping_gale", "phantom_triple_slash", "flying_sword_qi", "hydro_flying_sword",
                "arc_lightning_cleave", "gale_vault_thrust", "sky_spear_dive", "blazing_dragoon" -> fullCharge;
            case "clear_sky_thunder", "thunder_iaido" -> fullCharge && hasTarget;
            case "land_water_dragon", "lightning_step_spear" -> fullCharge || sprinting;
            default -> true;
        };
    }

    public static boolean launchesFlyingWeapon(String signatureId) {
        return "hydro_flying_sword".equals(signatureId) || "flying_sword_qi".equals(signatureId)
            || "phantom_triple_slash".equals(signatureId);
    }

    public static List<SignatureEntry> allUniqueSignatures() {
        return SignatureCatalog.allUniqueSignatures();
    }

    public static List<SignatureEntry> allOrderedEntries() {
        return SignatureCatalog.allUniqueSignatures();
    }

    public static Optional<SignatureEntry> resolve(String primaryId, String secondaryId) {
        return SignatureCatalog.resolve(primaryId, secondaryId);
    }

    public static Optional<SignatureEntry> resolve(FusedPair pair) {
        return SignatureCatalog.resolve(pair);
    }

    public static boolean stackHasSignature(ItemStack stack, String signatureId) {
        return SignatureCatalog.stackHasSignature(stack, signatureId);
    }

    public static boolean playerHasEquippedSignature(ServerPlayer player, String signatureId) {
        return SignatureCatalog.playerHasEquippedSignature(player, signatureId);
    }

    public static void onPlayerDisconnect(UUID playerId) {
        SignatureRuntimeSupport.onPlayerDisconnect(playerId);
        FlameKineticEffects.onPlayerDisconnect(playerId);
    }

    public static void clearRuntimeState() {
        SignatureRuntimeSupport.clearRuntimeState();
        FlameKineticEffects.clear();
    }

    public static void tickProjectile(ServerLevel level, Projectile proj) {
        RangedSignatures.tickProjectile(level, proj);
    }

    public static void onFishingReel(
        ServerLevel level,
        ServerPlayer player,
        ItemStack rod,
        @Nullable LivingEntity hookedMob,
        boolean caughtFishOrLoot
    ) {
        FusedEnchantmentList list = rod.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) {
            return;
        }
        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sigOpt = resolve(pair);
            if (sigOpt.isEmpty()) continue;
            String sid = sigOpt.get().id();
            if ("raging_hook".equals(sid) && hookedMob != null) {
                executeSignatureCombat(level, player, rod, pair, sid, hookedMob, hookedMob.position(), true);
            } else if ("tidal_double_catch".equals(sid) && caughtFishOrLoot) {
                executeSignatureCombat(level, player, rod, pair, sid, null, player.position(), false);
            }
        }
    }

    public static boolean allowIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        return ArmorCurseSignatures.allowIncomingDamage(player, source, amount);
    }

    public static boolean allowPlayerDeath(ServerPlayer player, DamageSource source) {
        return ArmorCurseSignatures.allowPlayerDeath(player, source);
    }

    public static void recordIncomingDamage(ServerPlayer player, float damageTaken, long gameTime) {
        if (damageTaken > 0.25F) {
            SignatureRuntimeSupport.RECENT_DAMAGE_TAKEN.put(player.getUUID(), damageTaken);
            SignatureRuntimeSupport.RECENT_DAMAGE_UNTIL.put(player.getUUID(), gameTime + 65L);
        }
    }

    public static boolean executeSignatureCombat(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        FusedPair pair,
        String sigId,
        @Nullable LivingEntity target,
        Vec3 pos,
        boolean fullChargeMelee
    ) {
        // This signature is an explicitly activated skill, not an invisible melee toggle.
        if ("mirage_snapback".equals(sigId)) return true;
        CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
        if (MeleeBladeSignatures.executeCombat(level, player, hostStack, pair, sigId, target, pos, fullChargeMelee)) {
            return true;
        }
        if (MeleeTacticsSignatures.executeCombat(level, player, hostStack, pair, sigId, target, pos, fullChargeMelee)) {
            return true;
        }
        if (ElementalSpearMaceSignatures.executeCombat(level, player, hostStack, pair, sigId, target, pos, fullChargeMelee)) {
            return true;
        }
        if (RangedSignatures.executeCombat(level, player, hostStack, pair, sigId, target, pos)) {
            return true;
        }
        if (MiningFishingSignatures.executeCombat(level, player, hostStack, pair, sigId, target, pos)) {
            return true;
        }
        return ArmorCurseSignatures.executeCombat(level, player, hostStack, pair, sigId, target, pos);
    }

    public static void onBlockHarvested(
        ServerLevel level,
        ServerPlayer player,
        ItemStack tool,
        BlockPos pos,
        BlockState minedState
    ) {
        MiningFishingSignatures.onBlockHarvested(level, player, tool, pos, minedState);
    }

    public static void onPostDestroyHarvestDrops(
        ServerLevel level,
        ServerPlayer player,
        ItemStack tool,
        BlockPos pos,
        BlockState minedState
    ) {
        MiningFishingSignatures.onPostDestroyHarvestDrops(level, player, tool, pos, minedState);
    }

    public static void onEntityKilled(ServerLevel level, ServerPlayer killer, LivingEntity victim) {
        ItemStack weapon = killer.getMainHandItem();
        if (weapon.isEmpty()) return;
        SignatureRuntimeSupport.PLUCKED_LIVING_MOBS.remove(victim.getUUID());
        FusedEnchantmentList list = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair pair : list.pairs()) {
            resolve(pair).ifPresent(sig -> MeleeTacticsSignatures.onEntityKilled(level, killer, victim, sig.id()));
        }
    }

    public static void tickServerPlayer(ServerLevel level, ServerPlayer player) {
        ArmorCurseSignatures.tickServerPlayer(level, player);
    }

    public static void tickWorldDots(MinecraftServer server) {
        SignatureRuntimeSupport.tickWorldDots(server);
    }
}
