package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Thorns -> Lure: attribute actual reflected damage to this exact equipped item and original hit. */
public final class GrapnelThorns {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int MAX_PENDING = 256;
    private record Gear(EquipmentSlot slot, ItemStack stack, FusedPair pair) {}
    private record Pending(ServerPlayer owner, LivingEntity attacker, DamageSource source, ResourceKey<Level> dimension,
                           long tick, List<Gear> gears) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<HurtFrame> HURT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

    /** Any nested enchantment invocation or conflicting damage makes attribution conservative. */
    public static final class Evidence {
        private int hits; private boolean ambiguous;
        public void nested() { ambiguous = true; }
        public void damage(boolean sameTarget, boolean expectedSource, float loss) {
            if (!sameTarget || !Float.isFinite(loss) || loss <= 0.0F) return;
            if (expectedSource) hits++; else ambiguous = true;
        }
        public boolean confirmed(boolean completed, float healthBefore, float absorptionBefore, float healthAfter, float absorptionAfter) {
            return completed && !ambiguous && hits == 1
                && ConfirmedMeleeMath.actualLoss(healthBefore, absorptionBefore, healthAfter, absorptionAfter) > 0.0F;
        }
    }
    /** Capture equipment before the real hurt call and require both its accepted result and net loss. */
    public static final class HurtFrame {
        private final @Nullable HurtFrame previous; private final ServerPlayer owner; private final DamageSource source;
        private final @Nullable LivingEntity attacker; private final ServerLevel level; private final long tick;
        private final List<Gear> gears; private final float health, absorption;
        private int observations; private boolean ambiguous;
        private HurtFrame(@Nullable HurtFrame previous, ServerPlayer owner, DamageSource source,
                          @Nullable LivingEntity attacker, List<Gear> gears) {
            this.previous = previous; this.owner = owner; this.source = source; this.attacker = attacker;
            this.level = owner.level(); this.tick = level.getGameTime(); this.gears = gears;
            this.health = owner.getHealth(); this.absorption = owner.getAbsorptionAmount();
        }
    }
    public static final class Scope {
        private final @Nullable Scope previous; private final @Nullable Pending pending; private final @Nullable Gear gear;
        private final float health, absorption; private final Evidence evidence = new Evidence();
        private Scope(@Nullable Scope previous, @Nullable Pending pending, @Nullable Gear gear) {
            this.previous = previous; this.pending = pending; this.gear = gear;
            this.health = pending == null ? 0 : pending.attacker().getHealth();
            this.absorption = pending == null ? 0 : pending.attacker().getAbsorptionAmount();
        }
    }
    private GrapnelThorns() {}
    public static void init() { ConfirmedLivingDamage.register(GrapnelThorns::onDamage); }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && FireThornsSkills.THORNS.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return FireTradeoffSkills.supportsCarrier(roles); }
    public static boolean supportsHost(@Nullable ItemStack stack) { return stack != null && FireTradeoffSkills.supportsHost(stack); }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }
    private static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isPair(pair) && GrapnelSkills.applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    private static boolean uniqueSlot(ServerPlayer owner, EquipmentSlot slot, ItemStack stack) {
        int count = 0;
        for (EquipmentSlot candidate : SLOTS) if (owner.getItemBySlot(candidate) == stack) count++;
        return count == 1 && owner.getItemBySlot(slot) == stack;
    }
    public static boolean stillEquipped(ServerPlayer owner, EquipmentSlot slot, ItemStack stack, FusedPair pair) {
        return uniqueSlot(owner, slot, stack) && FireTradeoffSkills.slotMatches(stack, slot) && pair.equals(selected(stack));
    }
    private static List<Gear> equipped(ServerPlayer owner) {
        List<Gear> result = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = owner.getItemBySlot(slot); FusedPair pair = selected(stack);
            if (pair != null && stillEquipped(owner, slot, stack, pair)) result.add(new Gear(slot, stack, pair));
        }
        return List.copyOf(result);
    }
    public static boolean hasEquipped(ServerPlayer owner) { return !equipped(owner).isEmpty(); }
    private static boolean disallowed(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS);
    }
    public static @Nullable HurtFrame beginHurt(ServerPlayer owner, DamageSource source) {
        PENDING.remove(owner.getUUID()); // never reuse evidence from an older hit
        HurtFrame previous = HURT.get();
        if (previous != null) previous.ambiguous = true;
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        boolean skip = ACTIVE.get() != null || attacker == null || attacker == owner || !ready(owner) || disallowed(source)
            || MatrixEffectExecutor.isExecuting() || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        List<Gear> gears = skip ? List.of() : equipped(owner).stream()
            .filter(gear -> GrapnelSkills.eligibleTarget(owner, attacker, gear.pair())).toList();
        if (gears.isEmpty() && previous == null) return null;
        HurtFrame frame = new HurtFrame(previous, owner, source, attacker, gears); HURT.set(frame); return frame;
    }
    public static void endHurt(@Nullable HurtFrame frame, boolean completed, boolean success) {
        if (frame == null) return;
        if (HURT.get() != frame) { HURT.remove(); return; }
        if (frame.previous == null) HURT.remove(); else HURT.set(frame.previous);
        ServerPlayer owner = frame.owner;
        if (!completed || !success || frame.ambiguous || frame.observations != 1 || frame.attacker == null || frame.gears.isEmpty()
            || !ready(owner) || owner.level() != frame.level || frame.level.getGameTime() != frame.tick
            || frame.attacker.level() != frame.level || ConfirmedMeleeMath.actualLoss(frame.health, frame.absorption,
                owner.getHealth(), owner.getAbsorptionAmount()) <= 0.0F
            || (!PENDING.containsKey(owner.getUUID()) && PENDING.size() >= MAX_PENDING)) return;
        List<Gear> gears = frame.gears.stream().filter(gear -> stillEquipped(owner, gear.slot(), gear.stack(), gear.pair())
            && GrapnelSkills.eligibleTarget(owner, frame.attacker, gear.pair())).toList();
        if (!gears.isEmpty()) PENDING.put(owner.getUUID(), new Pending(owner, frame.attacker, frame.source,
            frame.level.dimension(), frame.tick, gears));
    }
    private static void onDamage(ConfirmedLivingDamage.Hit hit) {
        Scope scope = ACTIVE.get();
        if (scope != null) {
            Pending pending = scope.pending;
            if (pending != null) {
                boolean target = hit.victim() == pending.attacker();
                boolean source = hit.source().getEntity() == pending.owner() && hit.source().getDirectEntity() == pending.owner();
                scope.evidence.damage(target, source, hit.loss());
                if (hit.victim() == pending.owner()) scope.evidence.nested();
            }
            return;
        }
        HurtFrame frame = HURT.get();
        if (frame == null) return;
        if (hit.victim() == frame.owner && hit.source() == frame.source) frame.observations++;
        else if (hit.victim() == frame.owner) frame.ambiguous = true;
    }
    /** Wrap every post-attack invocation so reentry cannot be credited to its parent. */
    public static @Nullable Scope begin(Enchantment enchantment, ServerLevel level, int enchantmentLevel,
                                         EnchantedItemInUse item, EnchantmentTarget side, Entity victim, DamageSource source) {
        Scope previous = ACTIVE.get();
        if (previous != null) previous.evidence.nested();
        Pending pending = victim instanceof ServerPlayer owner ? PENDING.get(owner.getUUID()) : null;
        Gear gear = null;
        if (pending != null && side == EnchantmentTarget.VICTIM && item.owner() == pending.owner()
            && source == pending.source() && pending.owner().level() == level && pending.attacker().level() == level
            && pending.dimension().equals(level.dimension()) && pending.tick() == level.getGameTime()
            && FusionStationOperations.lookupEnchantment(level.registryAccess(), FireThornsSkills.THORNS)
                .map(h -> h.value() == enchantment).orElse(false)) {
            for (Gear candidate : pending.gears()) if (candidate.slot() == item.inSlot() && candidate.stack() == item.itemStack()
                && enchantmentLevel >= candidate.pair().primaryLevel()
                && stillEquipped(pending.owner(), candidate.slot(), candidate.stack(), candidate.pair())) { gear = candidate; break; }
        }
        if (gear == null) pending = null;
        if (previous == null && pending == null) return null;
        Scope frame = new Scope(previous, pending, gear); ACTIVE.set(frame); return frame;
    }
    public static void finish(@Nullable Scope frame, boolean completed) {
        if (frame == null) return;
        if (ACTIVE.get() != frame) { ACTIVE.remove(); return; }
        if (frame.previous == null) ACTIVE.remove(); else ACTIVE.set(frame.previous);
        Pending pending = frame.pending; Gear gear = frame.gear;
        if (pending == null || gear == null || PENDING.get(pending.owner().getUUID()) != pending
            || !ready(pending.owner()) || !stillEquipped(pending.owner(), gear.slot(), gear.stack(), gear.pair())
            || pending.owner().level() != pending.attacker().level() || pending.tick() != pending.owner().level().getGameTime()
            || !frame.evidence.confirmed(completed, frame.health, frame.absorption,
                pending.attacker().getHealth(), pending.attacker().getAbsorptionAmount())) return;
        GrapnelSkills.markEquippedTarget(pending.owner(), pending.attacker(), gear.slot(), gear.stack(), gear.pair());
    }
    public static boolean needsReplay(int nativeLevel, boolean slotMatches) { return nativeLevel <= 0 || !slotMatches; }
    /** Preserve the current datapack's missing primary once per equipped item, never duplicating native Thorns. */
    public static void replayVictimPostAttack(ServerLevel level, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(REPLAYING.get()) || !(victim instanceof ServerPlayer owner) || owner.level() != level) return;
        Holder<Enchantment> thorns = FusionStationOperations.lookupEnchantment(level.registryAccess(), FireThornsSkills.THORNS).orElse(null);
        if (thorns == null) return;
        REPLAYING.set(true);
        try {
            for (Gear gear : equipped(owner)) {
                if (!stillEquipped(owner, gear.slot(), gear.stack(), gear.pair())) continue;
                int nativeLevel = gear.stack().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(thorns);
                if (!needsReplay(nativeLevel, thorns.value().matchingSlot(gear.slot()))) continue;
                thorns.value().doPostAttack(level, Math.max(nativeLevel, gear.pair().primaryLevel()),
                    new EnchantedItemInUse(gear.stack(), gear.slot(), owner), EnchantmentTarget.VICTIM, victim, source);
            }
        } finally { REPLAYING.remove(); }
    }
    public static void afterPostAttack(ServerLevel level, Entity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer owner)) return;
        Pending pending = PENDING.get(owner.getUUID());
        if (pending != null && pending.source() == source && pending.dimension().equals(level.dimension())) PENDING.remove(owner.getUUID(), pending);
    }
    public static void tickServer(MinecraftServer server) {
        PENDING.entrySet().removeIf(entry -> {
            Pending pending = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            return owner != pending.owner() || !ready(owner) || !owner.level().dimension().equals(pending.dimension())
                || owner.level().getGameTime() != pending.tick() || pending.attacker().level() != owner.level();
        });
    }
    public static void clearPlayer(UUID id) {
        PENDING.remove(id); PENDING.entrySet().removeIf(entry -> entry.getValue().attacker().getUUID().equals(id));
    }
    public static void clearServer() { PENDING.clear(); ACTIVE.remove(); HURT.remove(); REPLAYING.remove(); }
}
