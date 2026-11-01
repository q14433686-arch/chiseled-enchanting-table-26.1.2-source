package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** One shared projection for Channeling-first trident families; never repeat native or sibling effects. */
public final class ChannelingTridentPrimary {
    private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(() -> false);
    private ChannelingTridentPrimary() {}
    static boolean replaying() { return Boolean.TRUE.equals(REPLAY.get()); }
    static int highestLevel(int first, int second) { return Math.max(0, Math.max(first, second)); }

    private static int rank(ServerLevel level, ItemStack stack, Entity projectile) {
        if (replaying() || MatrixEffectExecutor.isExecuting() || stack == null || stack.isEmpty()
            || !(projectile instanceof ThrownTrident trident) || !(trident.getOwner() instanceof ServerPlayer owner)
            || owner.level() != level || trident.level() != level || trident.getWeaponItem() != stack) return 0;
        FusedPair lure = GrapnelChanneling.selected(stack);
        FusedPair lightning = MeleeLightningSkills.primaryProjectile(level, stack, trident);
        int result = highestLevel(lure == null ? 0 : lure.primaryLevel(), lightning == null ? 0 : lightning.primaryLevel());
        if (lure != null) {
            for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
                if (GrapnelChanneling.CHANNELING.equals(pair.primaryId())
                    && (GrapnelSkills.applies(pair, stack) || MeleeLightningSkills.applies(pair, stack))) result = Math.max(result, pair.primaryLevel());
            for (RetainedEnchant retained : stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()))
                if (GrapnelChanneling.CHANNELING.equals(retained.id())) result = Math.max(result, retained.level());
        }
        return result;
    }
    private static @Nullable Holder<Enchantment> missing(ServerLevel level, ItemStack stack) {
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), GrapnelChanneling.CHANNELING).orElse(null);
        return primary == null || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0
            ? null : primary;
    }
    public static float damage(ServerLevel level, ItemStack stack, Entity victim, DamageSource source, float value) {
        Entity projectile = source.getDirectEntity(); int rank = rank(level, stack, projectile);
        if (rank == 0 || !(projectile instanceof ThrownTrident trident) || source.getEntity() != trident.getOwner()) return value;
        Holder<Enchantment> primary = missing(level, stack); if (primary == null) return value;
        MutableFloat out = new MutableFloat(value); REPLAY.set(true);
        try { primary.value().modifyDamage(level, rank, stack, victim, source, out); }
        finally { REPLAY.remove(); }
        return Float.isFinite(out.floatValue()) ? out.floatValue() : value;
    }
    public static void post(ServerLevel level, ItemStack stack, Entity victim, DamageSource source, boolean alreadyObserved) {
        Entity projectile = source.getDirectEntity(); int rank = rank(level, stack, projectile);
        if (alreadyObserved || rank == 0 || !(projectile instanceof ThrownTrident trident)
            || !(trident.getOwner() instanceof ServerPlayer owner) || source.getEntity() != owner) return;
        Holder<Enchantment> primary = missing(level, stack);
        if (primary == null || !primary.value().matchingSlot(EquipmentSlot.MAINHAND)) return;
        REPLAY.set(true);
        try { primary.value().doPostAttack(level, rank, new EnchantedItemInUse(stack, EquipmentSlot.MAINHAND, owner),
            EnchantmentTarget.ATTACKER, victim, source); }
        finally { REPLAY.remove(); }
    }
    public static void block(ServerLevel level, ItemStack stack, Entity projectile, Vec3 position, BlockState state,
                             @Nullable Consumer<Item> onBreak, boolean alreadyObserved) {
        int rank = rank(level, stack, projectile);
        if (alreadyObserved || rank == 0 || !(projectile instanceof ThrownTrident trident)
            || !(trident.getOwner() instanceof ServerPlayer owner)) return;
        Holder<Enchantment> primary = missing(level, stack); if (primary == null) return;
        REPLAY.set(true);
        try { primary.value().onHitBlock(level, rank, new EnchantedItemInUse(stack, null, owner, onBreak), projectile, position, state); }
        finally { REPLAY.remove(); }
    }
    public static void clearServer() { REPLAY.remove(); }
}
