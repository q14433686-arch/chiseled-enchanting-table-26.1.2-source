package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** One actually quickened crossbow load can mark the target of its own real arrow. */
public final class GrapnelQuickCharge {
    private static final int MAX_OWNERS = 256, MAX_WEAPONS = 4;
    private static final long LIFETIME = 400;
    private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Loading> LOADING = new ThreadLocal<>();
    private static final ThreadLocal<Shot> SHOT = new ThreadLocal<>();
    private record Ready(ItemStack weapon, FusedPair pair, ResourceKey<Level> dimension,
                         ChargedProjectiles ammunition, long expires) {}
    private static final Map<UUID, List<Ready>> READY = new HashMap<>();
    public static final class Loading {
        final @Nullable Loading previous; final @Nullable ServerPlayer owner; final ItemStack weapon;
        final @Nullable FusedPair pair; final @Nullable Enchantment enchantment;
        int calls; boolean reduced, proof;
        Loading(@Nullable Loading previous, @Nullable ServerPlayer owner, ItemStack weapon,
                @Nullable FusedPair pair, @Nullable Enchantment enchantment) {
            this.previous = previous; this.owner = owner; this.weapon = weapon; this.pair = pair; this.enchantment = enchantment;
        }
    }
    public static final class Shot {
        final @Nullable Shot previous; final @Nullable ServerPlayer owner; final ItemStack weapon; final @Nullable FusedPair pair;
        Shot(@Nullable Shot previous, @Nullable ServerPlayer owner, ItemStack weapon, @Nullable FusedPair pair) {
            this.previous = previous; this.owner = owner; this.weapon = weapon; this.pair = pair;
        }
    }
    private GrapnelQuickCharge() {}
    private static @Nullable FusedPair selected(ItemStack weapon) {
        FusedPair best = null;
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (GrapnelSkills.QUICK.equals(pair.primaryId()) && GrapnelSkills.applies(pair, weapon)
                && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    private static boolean eligible(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator() && !owner.isCreative(); }
    private static boolean held(Player owner, ItemStack weapon) {
        return owner.getMainHandItem() == weapon || owner.getOffhandItem() == weapon;
    }
    private static boolean owns(ServerPlayer owner, ItemStack weapon) {
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == weapon) return true;
        return false;
    }
    /** Project the datapack's primary exactly at vanilla charge-time calculation, on both logical sides. */
    public static float chargeTime(ItemStack weapon, LivingEntity user, float vanilla) {
        if (Boolean.TRUE.equals(REPLAY.get()) || !(user instanceof Player player) || !held(player, weapon)) return vanilla;
        FusedPair pair = selected(weapon); if (pair == null) return vanilla;
        Holder<Enchantment> quick = FusionStationOperations.lookupEnchantment(user.registryAccess(), GrapnelSkills.QUICK).orElse(null);
        if (quick == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(quick) > 0) return vanilla;
        MutableFloat value = new MutableFloat(vanilla); REPLAY.set(true);
        try { quick.value().modifyCrossbowChargeTime(user.getRandom(), pair.primaryLevel(), value); }
        finally { REPLAY.remove(); }
        return Float.isFinite(value.floatValue()) ? Math.max(0.0F, value.floatValue()) : vanilla;
    }
    /** Merge non-native charging sounds only if their level beats the actual native highest-level component. */
    public static CrossbowItem.ChargingSounds chargingSounds(ItemStack weapon, LivingEntity user, CrossbowItem.ChargingSounds vanilla) {
        if (!(user instanceof Player player) || !held(player, weapon)) return vanilla;
        FusedPair pair = selected(weapon); if (pair == null) return vanilla;
        Holder<Enchantment> quick = FusionStationOperations.lookupEnchantment(user.registryAccess(), GrapnelSkills.QUICK).orElse(null);
        if (quick == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(quick) > 0) return vanilla;
        var highest = EnchantmentHelper.getHighestLevel(weapon, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS);
        if (highest != null && highest.getSecond() >= pair.primaryLevel()) return vanilla;
        List<CrossbowItem.ChargingSounds> sounds = quick.value().getEffects(EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS);
        return sounds.isEmpty() ? vanilla : sounds.get(Math.min(pair.primaryLevel(), sounds.size()) - 1);
    }
    /** Scope only the actual onUseTick call; unrelated charge-time queries cannot prove a load. */
    public static @Nullable Loading beginLoading(LivingEntity user, ItemStack weapon) {
        Loading previous = LOADING.get(); ServerPlayer owner = user instanceof ServerPlayer player ? player : null;
        FusedPair pair = owner != null && eligible(owner) && held(owner, weapon) && owner.isUsingItem() && owner.getUseItem() == weapon
            ? selected(weapon) : null;
        Holder<Enchantment> quick = pair == null ? null : FusionStationOperations.lookupEnchantment(user.registryAccess(), GrapnelSkills.QUICK).orElse(null);
        if (pair == null && previous == null) return null;
        Loading frame = new Loading(previous, owner, weapon, pair, quick == null ? null : quick.value()); LOADING.set(frame); return frame;
    }
    /** Observe the real invocation, not a second hypothetical random roll. */
    public static void observe(Enchantment enchantment, int level, float before, float after) {
        Loading frame = LOADING.get();
        if (frame == null || frame.enchantment != enchantment || frame.pair == null) return;
        frame.calls++;
        frame.reduced = GrapnelQuickChargeMath.contribution(frame.calls, level, frame.pair.primaryLevel(), before, after);
    }
    public static void finishTime(ItemStack weapon, LivingEntity user, float baseline, float result) {
        Loading frame = LOADING.get();
        if (frame == null || frame.weapon != weapon || frame.owner != user) return;
        frame.proof = GrapnelQuickChargeMath.faster(frame.calls, frame.reduced, baseline, result);
    }
    public static void loaded(LivingEntity user, ItemStack weapon) {
        Loading frame = LOADING.get();
        if (frame == null || frame.owner != user || frame.weapon != weapon || frame.pair == null || !frame.proof
            || !eligible(frame.owner) || !held(frame.owner, weapon) || !frame.owner.isUsingItem()
            || frame.owner.getUseItem() != weapon || !frame.pair.equals(selected(weapon))) return;
        ChargedProjectiles charged = weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        List<ItemStack> ammunition = charged.itemCopies();
        if (ammunition.isEmpty() || !(ammunition.getFirst().getItem() instanceof ArrowItem)) return;
        UUID id = frame.owner.getUUID();
        if (!READY.containsKey(id) && READY.size() >= MAX_OWNERS) return;
        List<Ready> entries = READY.computeIfAbsent(id, ignored -> new ArrayList<>());
        entries.removeIf(entry -> entry.weapon() == weapon);
        if (entries.size() >= MAX_WEAPONS) entries.removeFirst();
        entries.add(new Ready(weapon, frame.pair, frame.owner.level().dimension(), charged, frame.owner.level().getGameTime() + LIFETIME));
    }
    public static void endLoading(@Nullable Loading frame) {
        if (frame == null) return;
        if (LOADING.get() != frame) { LOADING.remove(); return; }
        if (frame.previous == null) LOADING.remove(); else LOADING.set(frame.previous);
    }
    public static Shot beginShot(LivingEntity user, ItemStack weapon) {
        ServerPlayer owner = user instanceof ServerPlayer player ? player : null; FusedPair pair = null;
        if (owner != null) {
            List<Ready> entries = READY.get(owner.getUUID());
            if (entries != null) {
                for (Iterator<Ready> it = entries.iterator(); it.hasNext();) {
                    Ready entry = it.next(); if (entry.weapon() != weapon) continue;
                    it.remove();
                    if (valid(owner, entry) && held(owner, weapon) && weapon.get(DataComponents.CHARGED_PROJECTILES) == entry.ammunition()) pair = entry.pair();
                    break;
                }
                if (entries.isEmpty()) READY.remove(owner.getUUID());
            }
        }
        Shot frame = new Shot(SHOT.get(), owner, weapon, pair); SHOT.set(frame); return frame;
    }
    public static boolean proven(ServerPlayer owner, ItemStack weapon, FusedPair pair) {
        Shot shot = SHOT.get();
        return shot != null && shot.owner == owner && shot.weapon == weapon && pair.equals(shot.pair);
    }
    public static void endShot(Shot shot) {
        if (SHOT.get() != shot) { SHOT.remove(); return; }
        if (shot.previous == null) SHOT.remove(); else SHOT.set(shot.previous);
    }
    private static boolean valid(ServerPlayer owner, Ready ready) {
        return eligible(owner) && owner.level().dimension().equals(ready.dimension()) && owner.level().getGameTime() < ready.expires()
            && owns(owner, ready.weapon()) && ready.pair().equals(selected(ready.weapon()))
            && ready.weapon().get(DataComponents.CHARGED_PROJECTILES) == ready.ammunition();
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(READY.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            entry.getValue().removeIf(ready -> owner == null || !valid(owner, ready));
            if (entry.getValue().isEmpty()) READY.remove(entry.getKey());
        }
    }
    public static void clearPlayer(UUID id) { READY.remove(id); }
    public static void clearServer() { READY.clear(); LOADING.remove(); SHOT.remove(); REPLAY.remove(); }
}
