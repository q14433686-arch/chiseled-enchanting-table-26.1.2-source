package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Riptide -> Lure: one attributed real release and collision-clipped movement, never a guessed spin flag. */
public final class GrapnelRiptide {
    public static final String RIPTIDE = "minecraft:riptide";
    private static final int MAX_PENDING = 256, MAX_STEPS = 4;
    private static final double MAX_STEP = 12.0D;
    private static final long WAIT_TICKS = 6;
    private static final ThreadLocal<Release> CURRENT = new ThreadLocal<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static boolean initialized;

    public static final class Release {
        final @Nullable Release previous; final ServerPlayer owner; final ServerLevel level;
        final ItemStack stack; final FusedPair pair; final EquipmentSlot slot; final long tick;
        boolean ambiguous, effectSeen, querySeen, queryCompleted, pushed; int queryDepth; Vec3 impulse;
        Release(@Nullable Release previous, ServerPlayer owner, ItemStack stack, FusedPair pair, EquipmentSlot slot) {
            this.previous = previous; this.owner = owner; this.level = owner.level(); this.stack = stack; this.pair = pair;
            this.slot = slot; this.tick = level.getGameTime();
        }
    }
    public static final class Push {
        final Release release; final Vec3 before, requested;
        Push(Release release, Vec3 before, Vec3 requested) { this.release = release; this.before = before; this.requested = requested; }
    }
    private static final class Pending {
        final ItemStack stack; final FusedPair pair; final EquipmentSlot slot; final ResourceKey<Level> dimension;
        final long expires; final Vec3 start, impulse; final List<ConfirmedPlayerMove.Step> steps = new ArrayList<>();
        Pending(Release release) {
            stack = release.stack; pair = release.pair; slot = release.slot; dimension = release.level.dimension();
            expires = release.tick + WAIT_TICKS; start = release.owner.position(); impulse = release.impulse;
        }
    }
    private GrapnelRiptide() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        ConfirmedPlayerMove.register(new ConfirmedPlayerMove.Observer() {
            @Override public boolean interested(ServerPlayer owner) { return PENDING.containsKey(owner.getUUID()); }
            @Override public double maxStep() { return MAX_STEP; }
            @Override public @Nullable Object before(ServerPlayer owner, Vec3 requested) {
                Pending pending = PENDING.get(owner.getUUID());
                return pending != null && valid(owner, pending) && owner.isAutoSpinAttack() && owner.getWeaponItem() == pending.stack
                    ? pending : null;
            }
            @Override public void moved(ServerPlayer owner, ConfirmedPlayerMove.Step step, @Nullable Object evidence) {
                Pending pending = PENDING.get(owner.getUUID());
                if (pending == null) return;
                if (evidence != pending || pending.steps.size() >= MAX_STEPS) PENDING.remove(owner.getUUID(), pending);
                else pending.steps.add(step);
            }
            @Override public void invalid(ServerPlayer owner) { PENDING.remove(owner.getUUID()); }
        });
    }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && RIPTIDE.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return roles != null && roles.contains(FusionCarrier.TRIDENT); }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.isBroken()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isPair(pair) && GrapnelSkills.applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    /** Shared primary projection: the same real Riptide release can back another dedicated direction. */
    private static @Nullable FusedPair selectedRelease(@Nullable ItemStack stack) {
        FusedPair grapnel = selected(stack), rail = ThunderRailSkills.selected(stack);
        if (grapnel == null) return rail;
        return rail == null || grapnel.primaryLevel() >= rail.primaryLevel() ? grapnel : rail;
    }
    private static boolean ready(ServerPlayer owner) {
        return owner.isAlive() && !owner.isCreative() && !owner.isSpectator() && !owner.isPassenger()
            && !owner.getAbilities().flying && !owner.isFallFlying();
    }
    private static boolean unambiguous(ItemStack stack) {
        return stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(pair -> pair.involves(RIPTIDE)).count() == 1
            && stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
                .noneMatch(retained -> RIPTIDE.equals(retained.id()));
    }
    private static boolean equipped(ServerPlayer owner, ItemStack stack, FusedPair pair, EquipmentSlot slot) {
        return owner.getItemBySlot(slot) == stack && pair.equals(selectedRelease(stack)) && unambiguous(stack);
    }
    public static boolean hasEquipped(ServerPlayer owner) { return selected(owner.getMainHandItem()) != null || selected(owner.getOffhandItem()) != null; }

    /** Preserve absent primary at vanilla's own strength query; never repeat an existing native enchantment. */
    public static float project(ItemStack stack, LivingEntity user, float strength) {
        FusedPair pair = selectedRelease(stack); if (pair == null || !Float.isFinite(strength)) return strength;
        Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(user.registryAccess(), RIPTIDE).orElse(null);
        if (enchantment == null || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) return strength;
        int level = pair.primaryLevel();
        for (FusedPair other : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (RIPTIDE.equals(other.primaryId())) level = Math.max(level, other.primaryLevel());
        for (RetainedEnchant retained : stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()))
            if (RIPTIDE.equals(retained.id())) level = Math.max(level, retained.level());
        MutableFloat value = new MutableFloat(strength);
        enchantment.value().modifyTridentSpinAttackStrength(user.getRandom(), level, value);
        return value.floatValue();
    }
    /** Sound is a separate data-pack effect; merge with other native sound sources by highest rank. */
    public static Optional<Holder<SoundEvent>> projectSound(ItemStack stack, Level world,
        DataComponentType<List<Holder<SoundEvent>>> component, Optional<Holder<SoundEvent>> nativeSound) {
        FusedPair pair = selectedRelease(stack); if (pair == null) return nativeSound;
        Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(world.registryAccess(), RIPTIDE).orElse(null);
        if (enchantment == null || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) return nativeSound;
        int level = pair.primaryLevel();
        for (FusedPair other : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (RIPTIDE.equals(other.primaryId())) level = Math.max(level, other.primaryLevel());
        for (RetainedEnchant retained : stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()))
            if (RIPTIDE.equals(retained.id())) level = Math.max(level, retained.level());
        var existing = EnchantmentHelper.getHighestLevel(stack, component);
        List<Holder<SoundEvent>> sounds = enchantment.value().effects().get(component);
        return sounds == null || sounds.isEmpty() || (existing != null && existing.getSecond() >= level)
            ? nativeSound : Optional.of(sounds.get(Math.min(level, sounds.size()) - 1));
    }

    public static @Nullable Release begin(ItemStack stack, Level level, LivingEntity user, int remaining) {
        Release previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        if (!(level instanceof ServerLevel world) || !(user instanceof ServerPlayer owner) || owner.level() != world || !ready(owner)
            || MatrixEffectExecutor.isExecuting() || !owner.isUsingItem() || owner.getUseItem() != stack
            || owner.getUseItemRemainingTicks() != remaining || stack.getUseDuration(user) - remaining < 10
            || !owner.isInWaterOrRain() || stack.nextDamageWillBreak()) return null;
        EquipmentSlot slot = owner.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        FusedPair pair = selectedRelease(stack);
        if (owner.getItemBySlot(slot) != stack || pair == null || !unambiguous(stack)) return null;
        Release release = new Release(previous, owner, stack, pair, slot); CURRENT.set(release); return release;
    }
    public static @Nullable Release beginQuery(ItemStack stack, LivingEntity owner) {
        Release release = CURRENT.get();
        if (release == null || release.owner != owner || release.stack != stack) return null;
        if (release.querySeen || release.queryDepth != 0) release.ambiguous = true;
        release.querySeen = true; release.queryDepth++; return release;
    }
    public static void endQuery(@Nullable Release release, float strength, boolean completed) {
        if (release == null) return;
        if (CURRENT.get() != release || release.queryDepth != 1 || !completed || !Float.isFinite(strength) || strength <= 0)
            release.ambiguous = true;
        release.queryCompleted = completed && Float.isFinite(strength) && strength > 0;
        if (release.queryDepth > 0) release.queryDepth--;
    }
    /** Observe the same actual data-pack value operation, including the single original random roll. */
    public static void observed(Enchantment enchantment, RandomSource random, int rank, float before, float after) {
        Release release = CURRENT.get();
        if (release == null || release.queryDepth != 1 || random != release.owner.getRandom()
            || !FusionStationOperations.lookupEnchantment(release.level.registryAccess(), RIPTIDE)
                .map(holder -> holder.value() == enchantment).orElse(false)) return;
        if (release.effectSeen || rank != release.pair.primaryLevel()) release.ambiguous = true;
        release.effectSeen = true;
        if (!Float.isFinite(before) || !Float.isFinite(after) || after <= before) release.ambiguous = true;
    }
    public static @Nullable Push beginPush(Player player, double x, double y, double z) {
        Release release = CURRENT.get();
        if (release == null || release.owner != player) return null;
        if (release.pushed) release.ambiguous = true;
        return new Push(release, player.getDeltaMovement(), new Vec3(x, y, z));
    }
    public static void endPush(@Nullable Push push, boolean completed) {
        if (push == null) return;
        Release release = push.release; Vec3 delta = release.owner.getDeltaMovement().subtract(push.before);
        if (CURRENT.get() != release || !completed || !matchesDirection(delta, push.requested)) release.ambiguous = true;
        else { release.pushed = true; release.impulse = delta; }
    }
    public static void end(@Nullable Release release, boolean result, boolean completed) {
        if (release == null) return;
        if (CURRENT.get() != release) { CURRENT.remove(); return; }
        if (release.previous == null) CURRENT.remove(); else CURRENT.set(release.previous);
        ServerPlayer owner = release.owner;
        if (!completed || !result || release.ambiguous || !release.effectSeen || !release.querySeen || !release.queryCompleted
            || release.queryDepth != 0 || !release.pushed || release.impulse == null || !ready(owner) || owner.level() != release.level
            || release.tick != release.level.getGameTime() || !equipped(owner, release.stack, release.pair, release.slot)
            || !owner.isAutoSpinAttack() || owner.getWeaponItem() != release.stack) return;
        if (ThunderRailSkills.matches(release.pair)) {
            ThunderRailSkills.released(owner, release.stack, release.pair, release.slot, release.impulse);
        } else if (PENDING.containsKey(owner.getUUID()) || PENDING.size() < MAX_PENDING) {
            PENDING.put(owner.getUUID(), new Pending(release));
        }
    }
    public static boolean matchesDirection(@Nullable Vec3 movement, @Nullable Vec3 impulse) {
        if (movement == null || impulse == null || !Double.isFinite(movement.x) || !Double.isFinite(movement.y) || !Double.isFinite(movement.z)
            || !Double.isFinite(impulse.x) || !Double.isFinite(impulse.y) || !Double.isFinite(impulse.z)) return false;
        double a = movement.lengthSqr(), b = impulse.lengthSqr(), dot = movement.dot(impulse);
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(dot) && a > 1.0E-5D && b > 1.0E-8D
            && dot > 0.5D * Math.sqrt(a * b);
    }
    private static boolean valid(ServerPlayer owner, Pending pending) {
        return ready(owner) && owner.level().dimension().equals(pending.dimension) && owner.level().getGameTime() < pending.expires
            && equipped(owner, pending.stack, pending.pair, pending.slot);
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(PENDING.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Pending pending = entry.getValue();
            if (owner == null || !valid(owner, pending) || !ConfirmedPlayerMove.connected(pending.start, owner.position(), pending.steps, MAX_STEP)) {
                PENDING.remove(entry.getKey(), pending); continue;
            }
            if (pending.steps.isEmpty()) continue;
            PENDING.remove(entry.getKey(), pending);
            if (matchesDirection(owner.position().subtract(pending.start), pending.impulse))
                GrapnelSkills.tryMarkPosition(owner, pending.slot, pending.stack, pending.pair, owner.position());
        }
    }
    public static void clearPlayer(UUID id) { PENDING.remove(id); }
    public static void clearServer() { PENDING.clear(); CURRENT.remove(); }
}
