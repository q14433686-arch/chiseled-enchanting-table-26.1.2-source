package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Wind Burst -> Lure: a location mark only after the same real hit produces an upward Wind Burst. */
public final class GrapnelWind {
    private static final int MAX_PENDING = 256;
    private record Pending(ItemStack stack, FusedPair pair, UUID victim, DamageSource source,
                           ResourceKey<Level> dimension, long tick) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private GrapnelWind() {}
    public static void init() {
        ConfirmedMelee.register(GrapnelWind::onHit);
        ConfirmedWindBurst.register(GrapnelWind::onWindBurst);
    }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && MeleeWindSkills.WIND.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) {
        return roles != null && roles.contains(FusionCarrier.MACE);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isPair(pair) && GrapnelSkills.applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    public static int primaryLevel(ServerPlayer owner, ItemStack stack) {
        FusedPair pair = owner.getMainHandItem() == stack ? selected(stack) : null;
        return pair == null ? 0 : pair.primaryLevel();
    }
    public static boolean hasEquipped(ServerPlayer owner) { return selected(owner.getMainHandItem()) != null; }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isCreative() && !owner.isSpectator(); }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player();
        if (hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !ready(owner)
            || owner.getMainHandItem() != hit.weapon() || owner.level() != hit.target().level()) return;
        FusedPair pair = selected(hit.weapon());
        if (pair == null || (!PENDING.containsKey(owner.getUUID()) && PENDING.size() >= MAX_PENDING)) return;
        PENDING.put(owner.getUUID(), new Pending(hit.weapon(), pair, hit.target().getUUID(), hit.source(),
            owner.level().dimension(), owner.level().getGameTime()));
    }
    private static void onWindBurst(ConfirmedWindBurst.Effect effect) {
        ServerPlayer owner = effect.owner(); ServerLevel level = effect.level(); Pending pending = PENDING.get(owner.getUUID());
        if (pending == null || !ready(owner) || pending.source() != effect.source() || pending.tick() != level.getGameTime()
            || !pending.dimension().equals(level.dimension()) || !pending.victim().equals(effect.victim().getUUID())
            || pending.stack() != effect.stack() || owner.getMainHandItem() != pending.stack()
            || !pending.pair().equals(selected(pending.stack())) || effect.enchantmentLevel() < pending.pair().primaryLevel()) return;
        PENDING.remove(owner.getUUID(), pending);
        GrapnelSkills.markPosition(owner, EquipmentSlot.MAINHAND, pending.stack(), pending.pair(), owner.position());
    }
    public static void afterPostAttack(ServerPlayer owner, Entity victim, DamageSource source) {
        Pending pending = PENDING.get(owner.getUUID());
        if (pending != null && pending.source() == source && pending.victim().equals(victim.getUUID())) PENDING.remove(owner.getUUID(), pending);
    }
    public static void tickServer(MinecraftServer server) {
        PENDING.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Pending pending = entry.getValue();
            return owner == null || !ready(owner) || !owner.level().dimension().equals(pending.dimension())
                || owner.level().getGameTime() != pending.tick() || owner.getMainHandItem() != pending.stack()
                || !pending.pair().equals(selected(pending.stack()));
        });
    }
    public static void clearPlayer(UUID id) { PENDING.remove(id); }
    public static void clearServer() { PENDING.clear(); }
}
