package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.TemporaryFrostEffects;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Retained Frost Walker/Fire Protection gameplay, with explicit direction and equipment rules. */
public final class FrostFireSkills {
    public static final String BASALT = "magma_basalt_step";
    public static final String HEARTH = "glacial_hearth";
    private static final String FROST = "minecraft:frost_walker";
    private static final String FIRE_PROTECTION = "minecraft:fire_protection";
    private static final Set<FusionCarrier> ARMOR = Set.of(FusionCarrier.HEAD_ARMOR, FusionCarrier.CHEST_ARMOR,
        FusionCarrier.LEG_ARMOR, FusionCarrier.FOOT_ARMOR);

    private FrostFireSkills() {}

    public static boolean isBasaltPair(@Nullable FusedPair pair) {
        return pair != null && FROST.equals(pair.primaryId()) && FIRE_PROTECTION.equals(pair.secondaryId());
    }

    public static boolean isHearthPair(@Nullable FusedPair pair) {
        return pair != null && FIRE_PROTECTION.equals(pair.primaryId()) && FROST.equals(pair.secondaryId());
    }

    public static boolean supportsCarrier(FusedPair pair, Set<FusionCarrier> carriers) {
        if (carriers == null) return false;
        if (isBasaltPair(pair)) return carriers.contains(FusionCarrier.FOOT_ARMOR);
        return isHearthPair(pair) && carriers.stream().anyMatch(ARMOR::contains);
    }

    public static boolean matches(FusedPair pair, String signature) {
        return (isBasaltPair(pair) && BASALT.equals(signature)) || (isHearthPair(pair) && HEARTH.equals(signature));
    }

    public static boolean applies(FusedPair pair, ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty()
            && supportsCarrier(pair, FusionCarrier.classify(stack));
    }

    public static boolean correctSlot(Set<FusionCarrier> carriers, EquipmentSlot slot) {
        if (carriers == null || slot == null) return false;
        return switch (slot) {
            case HEAD -> carriers.contains(FusionCarrier.HEAD_ARMOR);
            case CHEST -> carriers.contains(FusionCarrier.CHEST_ARMOR);
            case LEGS -> carriers.contains(FusionCarrier.LEG_ARMOR);
            case FEET -> carriers.contains(FusionCarrier.FOOT_ARMOR);
            default -> false;
        };
    }

    /** Prefer real worn selected armor over a legacy carrier; a selected item in hand does not activate. */
    public static @Nullable SignatureCatalog.EquippedSignature select(ServerPlayer player, boolean basalt) {
        String expected = basalt ? BASALT : HEARTH;
        SignatureCatalog.EquippedSignature best = null;
        boolean bestSelected = false;
        for (EquipmentSlot slot : SignatureCatalog.ALL_EQUIP_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
                String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
                if (!expected.equals(signature)) continue;
                boolean selected = applies(pair, stack, signature);
                if (selected && (!player.isAlive() || player.isSpectator()
                    || (basalt ? slot != EquipmentSlot.FEET : !correctSlot(FusionCarrier.classify(stack), slot)))) continue;
                SignatureCatalog.EquippedSignature candidate = new SignatureCatalog.EquippedSignature(signature, pair, slot, stack);
                if (best == null || (selected && !bestSelected)
                    || (selected == bestSelected && candidate.totalLevel() > best.totalLevel())) {
                    best = candidate;
                    bestSelected = selected;
                }
            }
        }
        return best;
    }

    /** Original 3x3 surface lava -> permanent basalt; all changes get vanilla world/permission checks. */
    public static void tickBasalt(ServerLevel level, ServerPlayer player, SignatureCatalog.EquippedSignature equipped,
                                  boolean selected) {
        if (!player.mayBuild() || player.isShiftKeyDown()) return;
        if (selected) TemporaryFrostEffects.placeWake(level, player, equipped.pair().primaryLevel());
        BlockPos feet = player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos lava = feet.offset(dx, -1, dz);
                BlockPos above = lava.above();
                if (!level.isInWorldBounds(lava) || !level.isInWorldBounds(above) || !level.hasChunkAt(lava)
                    || !level.mayInteract(player, lava)
                    || player.blockActionRestricted(level, lava, player.gameMode.getGameModeForPlayer())) continue;
                BlockState state = level.getBlockState(lava);
                if (state.is(Blocks.LAVA) && state.getFluidState().isSource() && level.getBlockState(above).isAir()) {
                    level.setBlockAndUpdate(lava, Blocks.BASALT.defaultBlockState());
                }
            }
        }
    }

    /** Preserve the original broad non-allied chill; selected armor respects PvP and vanilla interaction checks. */
    public static void tickHearth(ServerLevel level, ServerPlayer player, boolean selected) {
        player.setTicksFrozen(0);
        for (LivingEntity target : SignatureRuntimeSupport.nearbyHostiles(level, player, player.position(), 3.2D, null)) {
            if (selected && (!mayChill(level, player, target))) continue;
            target.setTicksFrozen(Math.min(200, target.getTicksFrozen() + 25));
        }
    }

    private static boolean mayChill(ServerLevel level, ServerPlayer player, LivingEntity target) {
        if (target.isInvulnerable() || target.isSpectator() || target.isAlliedTo(player) || player.isAlliedTo(target)
            || !level.mayInteract(player, target.blockPosition())) return false;
        return !(target instanceof Player other) || (!other.getAbilities().invulnerable && player.canHarmPlayer(other));
    }
}
