package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.TemporaryFrostEffects;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Carrier and slot rules for the two retained Frost Walker/Depth Strider abilities. */
public final class FrostWakeDiveSkills {
    public static final String WAKE = "permafrost_wake";
    public static final String DIVE = "tide_breaker_dive";
    private FrostWakeDiveSkills() {}

    public static boolean isWakePair(@Nullable FusedPair pair) {
        return pair != null && FrostFallSkills.FROST.equals(pair.primaryId()) && FrostFallSkills.DEPTH.equals(pair.secondaryId());
    }
    public static boolean isDivePair(@Nullable FusedPair pair) {
        return pair != null && FrostFallSkills.DEPTH.equals(pair.primaryId()) && FrostFallSkills.FROST.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(Set<FusionCarrier> carriers) {
        return carriers != null && carriers.contains(FusionCarrier.FOOT_ARMOR);
    }
    public static boolean applies(@Nullable FusedPair pair, ItemStack stack, String signature) {
        if (stack == null || stack.isEmpty() || !supportsCarrier(FusionCarrier.classify(stack))) return false;
        return (isWakePair(pair) && WAKE.equals(signature)) || (isDivePair(pair) && DIVE.equals(signature));
    }

    public static void tick(ServerLevel level, ServerPlayer player, SignatureCatalog.EquippedSignature equipped,
                            boolean selectedWaterBoots) {
        if (DIVE.equals(equipped.sigId())) {
            if (player.isShiftKeyDown()) TemporaryFrostEffects.removeDivePath(level, player);
            if (player.isInWater() && player.isShiftKeyDown()) {
                Vec3 dive = player.getLookAngle().normalize().scale(0.28D);
                player.push(dive.x, dive.y * 0.5D, dive.z);
                player.hurtMarked = true;
            }
        } else if (WAKE.equals(equipped.sigId())) {
            TemporaryFrostEffects.placeWake(level, player, equipped.pair().primaryLevel());
            player.clearFire();
            for (LivingEntity mob : SignatureRuntimeSupport.nearbyHostiles(level, player, player.position(), 2.8D, null)) {
                if (selectedWaterBoots && (!LoyaltyGuard.eligibleTarget(player, mob)
                    || !level.mayInteract(player, mob.blockPosition()))) continue;
                mob.setTicksFrozen(Math.min(300, mob.getTicksFrozen() + 50));
            }
        }
    }

    /** Prefer worn selected boots, but keep the prior path for existing unsupported carriers. */
    public static @Nullable SignatureCatalog.EquippedSignature select(ServerPlayer player, boolean wake) {
        String expected = wake ? WAKE : DIVE;
        SignatureCatalog.EquippedSignature best = null;
        boolean bestWornSelected = false;
        for (EquipmentSlot slot : SignatureCatalog.ALL_EQUIP_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
                if (!expected.equals(signature)) continue;
                boolean selected = applies(pair, stack, signature);
                if (selected && slot != EquipmentSlot.FEET) continue;
                var candidate = new SignatureCatalog.EquippedSignature(signature, pair, slot, stack);
                if (best == null || (selected && !bestWornSelected)
                    || (selected == bestWornSelected && candidate.totalLevel() > best.totalLevel())) {
                    best = candidate;
                    bestWornSelected = selected;
                }
            }
        }
        return best;
    }
}
