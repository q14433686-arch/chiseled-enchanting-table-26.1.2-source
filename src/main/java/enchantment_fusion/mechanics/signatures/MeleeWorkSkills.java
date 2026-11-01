package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Twelve selected melee/mining directions; both families share real-hit and real-break evidence. */
public final class MeleeWorkSkills {
    public static final String EFFICIENCY = "minecraft:efficiency", AQUA = "minecraft:aqua_affinity";
    private static final Set<FusionCarrier> TOOLS = Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE,
        FusionCarrier.SHOVEL, FusionCarrier.HOE);
    private static final Identifier SPEED = EnchantmentFusion.id("melee_work_speed");
    private static final int MAX_OWNERS = 256, MAX_STATES = 8;

    private record Gear(ItemStack stack, FusedPair pair) {}
    private static final class State {
        final Gear gear; final ResourceKey<Level> dimension;
        int streak, cuts; long lastHit, cutUntil;
        State(Gear gear, ResourceKey<Level> dimension) { this.gear = gear; this.dimension = dimension; }
    }
    private record Boost(Gear gear, ResourceKey<Level> dimension, long until, double multiplier, boolean waterOnly) {}
    private static final Map<UUID, List<State>> STATES = new HashMap<>();
    private static final Map<UUID, Boost> BOOSTS = new HashMap<>();
    private static final ThreadLocal<Attack> ATTACK = new ThreadLocal<>();
    private MeleeWorkSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeWorkSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_work", (pair, stack) -> isAttackFirst(pair) && applies(pair, stack));
    }
    private static boolean isWork(String id) { return EFFICIENCY.equals(id) || AQUA.equals(id); }
    public static boolean isAttackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && isWork(pair.secondaryId());
    }
    public static boolean isWorkFirst(@Nullable FusedPair pair) {
        return pair != null && isWork(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (isAttackFirst(pair) || isWorkFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsTool(@Nullable Set<FusionCarrier> roles) {
        return roles != null && roles.stream().anyMatch(TOOLS::contains);
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        if (pair == null || roles == null) return false;
        if (isAttackFirst(pair)) return MeleeAttackThemes.supportsWeapon(roles);
        return isWorkFirst(pair) && (EFFICIENCY.equals(pair.primaryId()) ? supportsTool(roles) : roles.contains(FusionCarrier.HEAD_ARMOR));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && matches(pair) && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean attackFirst, boolean water) {
        if (stack == null || stack.isEmpty()) return null;
        String work = water ? AQUA : EFFICIENCY;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((attackFirst ? isAttackFirst(pair) && work.equals(pair.secondaryId())
                : isWorkFirst(pair) && work.equals(pair.primaryId())) && applies(pair, stack)) return new Gear(stack, pair);
        }
        return null;
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (gear == null || !applies(gear.pair(), gear.stack())
            || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())) return false;
        if (isWorkFirst(gear.pair()) && AQUA.equals(gear.pair().primaryId()))
            return player.getItemBySlot(EquipmentSlot.HEAD) == gear.stack();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static void expire(State state, long now) {
        long elapsed = now - state.lastHit;
        if (state.streak > 0 && (now < state.lastHit || elapsed < 0 || elapsed > MeleeWearMath.STREAK_TICKS)) state.streak = 0;
        if (now >= state.cutUntil) state.cuts = 0;
    }
    private static @Nullable State state(ServerPlayer player, Gear gear, boolean create) {
        List<State> entries = STATES.get(player.getUUID()); long now = player.level().getGameTime();
        if (entries != null) {
            entries.removeIf(entry -> {
                if (!ready(player) || !entry.dimension.equals(player.level().dimension()) || !owns(player, entry.gear)) return true;
                expire(entry, now); return entry.streak == 0 && entry.cuts == 0;
            });
            for (State entry : entries) if (entry.gear.stack() == gear.stack() && entry.gear.pair().equals(gear.pair())) return entry;
            if (entries.isEmpty()) STATES.remove(player.getUUID());
        }
        if (!create || !ready(player) || !owns(player, gear)) return null;
        if (entries == null || entries.isEmpty()) {
            if (STATES.size() >= MAX_OWNERS) return null;
            entries = new ArrayList<>(); STATES.put(player.getUUID(), entries);
        }
        if (entries.size() >= MAX_STATES) entries.removeFirst();
        State result = new State(gear, player.level().dimension()); entries.add(result); return result;
    }

    /** A separate nested frame ensures a failed or cancelled direct attack resets only its own streak. */
    public static final class Attack {
        final @Nullable Attack previous; final Player player; final ItemStack weapon; final Entity target; final EquipmentSlot slot;
        final @Nullable Gear ordinary, aquatic; final boolean eligible;
        boolean confirmedOrdinary, confirmedAquatic;
        Attack(@Nullable Attack previous, Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
            this.previous = previous; this.player = player; this.weapon = weapon; this.target = target; this.slot = slot;
            ordinary = slot == EquipmentSlot.MAINHAND ? selected(weapon, true, false) : null;
            aquatic = slot == EquipmentSlot.MAINHAND ? selected(weapon, true, true) : null;
            eligible = !MatrixEffectExecutor.isExecuting() && !GlazedStrikeEffect.isApplyingBonus()
                && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
                && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        }
    }
    public static Attack beginAttack(Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
        Attack frame = new Attack(ATTACK.get(), player, weapon, target, slot); ATTACK.set(frame); return frame;
    }
    public static void endAttack(Attack frame) {
        if (ATTACK.get() != frame) return;
        try {
            if (frame.eligible && frame.player instanceof ServerPlayer owner && owner.getMainHandItem() == frame.weapon) {
                if (frame.ordinary != null && !frame.confirmedOrdinary) resetStreak(owner, frame.ordinary);
                if (frame.aquatic != null && !frame.confirmedAquatic) resetStreak(owner, frame.aquatic);
            }
        } finally { if (frame.previous == null) ATTACK.remove(); else ATTACK.set(frame.previous); }
    }
    private static void resetStreak(ServerPlayer owner, Gear gear) { State state = state(owner, gear, false); if (state != null) state.streak = 0; }
    private static void recordHit(ServerPlayer owner, Gear gear) {
        State state = state(owner, gear, true); if (state == null) return;
        int before = state.streak; long now = owner.level().getGameTime();
        state.streak = MeleeWearMath.nextStreak(state.streak, state.lastHit, now); state.lastHit = now;
        if (before < MeleeWearMath.MIN_STREAK && state.streak >= MeleeWearMath.MIN_STREAK)
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_work_ready"), true);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        Attack frame = ATTACK.get(); ServerPlayer owner = hit.player();
        if (frame == null || !frame.eligible || frame.player != owner || frame.weapon != hit.weapon()
            || frame.target != hit.target() || frame.slot != EquipmentSlot.MAINHAND || hit.slot() != EquipmentSlot.MAINHAND
            || !ready(owner) || owner.getMainHandItem() != hit.weapon() || !hit.eligibleBefore()) return;
        if (frame.ordinary != null && MeleeAttackThemes.matchesTarget(frame.ordinary.pair().primaryId(), hit.target())) {
            recordHit(owner, frame.ordinary); frame.confirmedOrdinary = true;
        }
        if (frame.aquatic != null && owner.isEyeInFluid(FluidTags.WATER)
            && MeleeAttackThemes.matchesTarget(frame.aquatic.pair().primaryId(), hit.target())) {
            recordHit(owner, frame.aquatic); frame.confirmedAquatic = true;
        }
        // One eligible item per hit; other independently earned work remains for the next hit.
        Gear tool = selected(owner.getMainHandItem(), false, false);
        Gear helmet = selected(owner.getItemBySlot(EquipmentSlot.HEAD), false, true);
        if (!release(owner, tool, hit)) release(owner, helmet, hit);
    }
    private static boolean release(ServerPlayer owner, @Nullable Gear gear, ConfirmedMelee.Hit hit) {
        if (gear == null || !owns(owner, gear) || !MeleeAttackThemes.matchesTarget(gear.pair().secondaryId(), hit.target())) return false;
        State state = state(owner, gear, false); if (state == null || state.cuts <= 0) return false;
        int spent = state.cuts; state.cuts = 0;
        VirtualBlade.cast(owner, owner.getLookAngle(), hit.target(), 4.5D,
            MeleeWorkMath.bladeDamage(gear.pair().primaryLevel(), gear.pair().secondaryLevel(), spent), 8,
            target -> MeleeAttackThemes.eligible(owner, target, gear.pair().secondaryId()),
            AQUA.equals(gear.pair().primaryId()) ? ParticleTypes.BUBBLE : ParticleTypes.SWEEP_ATTACK);
        CompendiumDiscoveryTracker.recordDiscovery(owner, gear.pair().primaryId(), gear.pair().secondaryId());
        return true;
    }

    public static boolean hasEquipped(ServerPlayer player, boolean water) { return selected(player.getMainHandItem(), true, water) != null; }
    public static boolean activate(ServerPlayer player, boolean water) {
        if (!ready(player) || player.containerMenu != player.inventoryMenu) return false;
        Gear gear = selected(player.getMainHandItem(), true, water); if (gear == null || !owns(player, gear)) return false;
        if (BOOSTS.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_work_active"), true); return false;
        }
        State entry = state(player, gear, false);
        if (entry == null || entry.streak < MeleeWearMath.MIN_STREAK) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_work_need"), true); return false;
        }
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (speed == null || speed.hasModifier(SPEED) || BOOSTS.size() >= MAX_OWNERS) return false;
        double multiplier = MeleeWorkMath.speedMultiplier(gear.pair().secondaryLevel(), entry.streak);
        Boost boost = new Boost(gear, player.level().dimension(), player.level().getGameTime() + MeleeWorkMath.BOOST_TICKS, multiplier, water);
        BOOSTS.put(player.getUUID(), boost);
        if (!refresh(player, boost)) { BOOSTS.remove(player.getUUID()); removeModifier(player, boost); return false; }
        entry.streak = 0;
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_work_set"), true);
        CompendiumDiscoveryTracker.recordDiscovery(player, gear.pair().primaryId(), gear.pair().secondaryId());
        return true;
    }
    private static AttributeModifier modifier(Boost boost) {
        return new AttributeModifier(SPEED, boost.multiplier() - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
    private static void removeModifier(ServerPlayer player, Boost boost) {
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        AttributeModifier actual = speed == null ? null : speed.getModifier(SPEED);
        if (actual != null && actual.equals(modifier(boost)) && !speed.getPermanentModifiers().contains(actual)) speed.removeModifier(SPEED);
    }
    private static boolean validBoost(ServerPlayer player, Boost boost) {
        return ready(player) && player.level().dimension().equals(boost.dimension()) && player.level().getGameTime() < boost.until()
            && owns(player, boost.gear());
    }
    /** The attribute syncs to the client; it exists only while an actual suitable tool is held. */
    private static boolean refresh(ServerPlayer player, Boost boost) {
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED); if (speed == null) return false;
        AttributeModifier existing = speed.getModifier(SPEED), wanted = modifier(boost);
        if (existing != null && (!existing.equals(wanted) || speed.getPermanentModifiers().contains(existing))) return false;
        boolean enabled = supportsTool(FusionCarrier.classify(player.getMainHandItem()))
            && (!boost.waterOnly() || player.isEyeInFluid(FluidTags.WATER));
        if (enabled && existing == null) speed.addTransientModifier(wanted);
        else if (!enabled && existing != null) speed.removeModifier(SPEED);
        return true;
    }
    /** Fabric AFTER fires only for the actual removed block, after all BEFORE cancellations. */
    public static void onMined(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState block) {
        if (!ready(player) || player.level() != level || ExtraBlockHarvest.isActive() || MatrixEffectExecutor.isExecuting()
            || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get()) || !level.isInWorldBounds(pos)
            || !level.hasChunkAt(pos) || !level.mayInteract(player, pos)
            || level.getServer().isUnderSpawnProtection(level, pos, player)
            || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
            || block.isAir() || block.getDestroySpeed(level, pos) < 0 || !supportsTool(FusionCarrier.classify(player.getMainHandItem()))) return;
        Boost boost = BOOSTS.get(player.getUUID());
        if (boost != null && validBoost(player, boost) && (!boost.waterOnly() || player.isEyeInFluid(FluidTags.WATER))) {
            AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
            if (speed != null && modifier(boost).equals(speed.getModifier(SPEED))) {
                BOOSTS.remove(player.getUUID()); removeModifier(player, boost);
            }
        }
        earnCut(player, selected(player.getMainHandItem(), false, false));
        if (player.isEyeInFluid(FluidTags.WATER)) earnCut(player, selected(player.getItemBySlot(EquipmentSlot.HEAD), false, true));
    }
    private static void earnCut(ServerPlayer player, @Nullable Gear gear) {
        if (gear == null || !owns(player, gear)) return;
        State entry = state(player, gear, true); if (entry == null) return;
        entry.cuts = MeleeWorkMath.addCut(entry.cuts); entry.cutUntil = player.level().getGameTime() + MeleeWorkMath.CUT_TICKS;
    }
    private static void addAttributeSource(List<PrimaryAttributeProjection.Source> sources, ServerPlayer player,
                                           EquipmentSlot slot, String primary) {
        ItemStack stack = player.getItemBySlot(slot); FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (primary.equals(pair.primaryId())
                && ((isWorkFirst(pair) && applies(pair, stack)) || (GrapnelSkills.miningFirst(pair) && GrapnelSkills.applies(pair, stack)))
                && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        }
        if (best != null) sources.add(new PrimaryAttributeProjection.Source(slot, stack, best));
    }
    private static void refreshPrimaryAttributes(ServerPlayer player) {
        List<PrimaryAttributeProjection.Source> sources = new ArrayList<>();
        addAttributeSource(sources, player, EquipmentSlot.MAINHAND, EFFICIENCY);
        addAttributeSource(sources, player, EquipmentSlot.HEAD, AQUA);
        PrimaryAttributeProjection.refresh(player, sources);
    }
    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) refreshPrimaryAttributes(player);
        for (var entry : List.copyOf(BOOSTS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey()); Boost boost = entry.getValue();
            if (player == null) { BOOSTS.remove(entry.getKey()); continue; }
            if (!validBoost(player, boost) || !refresh(player, boost)) { BOOSTS.remove(entry.getKey()); removeModifier(player, boost); }
        }
        for (var entry : List.copyOf(STATES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !ready(player)) { STATES.remove(entry.getKey()); continue; }
            long now = player.level().getGameTime();
            entry.getValue().removeIf(value -> {
                if (!value.dimension.equals(player.level().dimension()) || !owns(player, value.gear)) return true;
                expire(value, now); return value.streak == 0 && value.cuts == 0;
            });
            if (entry.getValue().isEmpty()) STATES.remove(entry.getKey());
        }
    }
    public static void clearPlayer(ServerPlayer player) {
        Boost boost = BOOSTS.remove(player.getUUID()); if (boost != null) removeModifier(player, boost);
        STATES.remove(player.getUUID()); PrimaryAttributeProjection.clear(player);
    }
    public static void clearServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) clearPlayer(player);
        PrimaryAttributeProjection.clear(server);
        BOOSTS.clear(); STATES.clear(); ATTACK.remove();
    }
}
