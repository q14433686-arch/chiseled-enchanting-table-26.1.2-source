package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.menu.FusionSkillNetworking;
import enchantment_fusion.mixin.LivingEntityAttackChargeAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 33.2 and 44.3: ordinary real melee is retained; Fortune adds bounded rhythm or a prepared stance. */
public final class MeleeFortuneSkills {
    public static final String FORTUNE = "minecraft:fortune", EXACT_FORWARD = "midas_cleave", CONFIG = EXACT_FORWARD;
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE);
    private static final int MAX_OWNERS = 256;
    private enum Outcome { NONE, HEAVY, SCATTER, REFRESH }
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Armed(Gear gear, ResourceKey<Level> dimension, long expires, Outcome outcome) {}
    private static final Map<UUID, Armed> ARMED = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ARM = new HashMap<>();
    private static final ThreadLocal<Damage> DAMAGE = new ThreadLocal<>();
    private MeleeFortuneSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeFortuneSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_fortune", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && FORTUNE.equals(pair.secondaryId());
    }
    public static boolean fortuneFirst(@Nullable FusedPair pair) {
        return pair != null && FORTUNE.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    /** The selected Sharpness exception outranks the family template; datapack overrides remain authoritative. */
    public static boolean matches(@Nullable FusedPair pair) {
        if (!(attackFirst(pair) || fortuneFirst(pair))) return false;
        var signature = SignatureCatalog.resolve(pair);
        String attack = attackFirst(pair) ? pair.primaryId() : pair.secondaryId();
        if (MeleeAttackThemes.SHARP.equals(attack) && attackFirst(pair))
            return signature.map(entry -> EXACT_FORWARD.equals(entry.id())).orElse(false);
        return signature.isEmpty();
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return roles != null && roles.stream().anyMatch(WEAPONS::contains); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean reverse) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((reverse ? fortuneFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    private static boolean owns(ServerPlayer player, Gear gear) {
        return player.getMainHandItem() == gear.stack() && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    private static boolean valid(ServerPlayer player, Armed armed) {
        return ready(player) && player.level().dimension().equals(armed.dimension()) && player.level().getGameTime() < armed.expires()
            && owns(player, armed.gear());
    }
    private static int chance(int fortuneLevel) {
        return MeleeFortuneMath.chance(FusionConfigStore.signatureInt(CONFIG, "chance_base_percent", 10, 0, 90),
            FusionConfigStore.signatureInt(CONFIG, "chance_per_level_percent", 10, 0, 50), fortuneLevel);
    }
    private static boolean roll(ServerPlayer player, int fortuneLevel) { return player.getRandom().nextInt(100) < chance(fortuneLevel); }
    public static boolean hasEquipped(ServerPlayer player) { return selected(player.getMainHandItem(), true) != null; }
    /** Roll secretly once, not on every cancelled attack; switching items cannot immediately reroll. */
    public static boolean activate(ServerPlayer player) {
        if (!ready(player) || player.containerMenu != player.inventoryMenu) return false;
        Gear gear = selected(player.getMainHandItem(), true);
        if (gear == null || !owns(player, gear)) return false;
        long now = player.level().getGameTime(); Armed old = ARMED.get(player.getUUID());
        if (old != null && valid(player, old)) { player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_stance_ready"), true); return false; }
        ARMED.remove(player.getUUID());
        if (now < NEXT_ARM.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_stance_wait"), true); return false;
        }
        if (!NEXT_ARM.containsKey(player.getUUID()) && NEXT_ARM.size() >= MAX_OWNERS) return false;
        if (!ARMED.containsKey(player.getUUID()) && ARMED.size() >= MAX_OWNERS) return false;
        Outcome outcome = roll(player, gear.pair().primaryLevel()) ? Outcome.values()[1 + player.getRandom().nextInt(3)] : Outcome.NONE;
        int lifetime = FusionConfigStore.signatureInt(CONFIG, "stance_ticks", 160, 20, 1200);
        int cooldown = FusionConfigStore.signatureInt(CONFIG, "stance_cooldown_ticks", 40, 1, 1200);
        ARMED.put(player.getUUID(), new Armed(gear, player.level().dimension(), now + lifetime, outcome));
        NEXT_ARM.put(player.getUUID(), now + cooldown);
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_stance_ready"), true);
        return true;
    }
    public static final class Damage {
        final @Nullable Damage previous; final @Nullable ConfirmedMelee.Observation observation;
        final @Nullable Armed armed; final float damage;
        Damage(@Nullable Damage previous, @Nullable ConfirmedMelee.Observation observation, @Nullable Armed armed, float damage) {
            this.previous = previous; this.observation = observation; this.armed = armed; this.damage = damage;
        }
        public float damage() { return damage; }
    }
    /** Heavy outcome modifies the same real damage request; a cancelled hit retains the same hidden roll. */
    public static Damage beginDamage(@Nullable ConfirmedMelee.Observation observation, float damage) {
        Damage previous = DAMAGE.get(); Armed armed = null;
        if (previous == null && observation != null && observation.slot() == EquipmentSlot.MAINHAND && observation.eligibleBefore()
            && ready(observation.player()) && observation.player().getMainHandItem() == observation.weapon()
            && Float.isFinite(damage) && damage > 0) {
            Armed candidate = ARMED.get(observation.player().getUUID());
            if (candidate != null && valid(observation.player(), candidate)
                && MeleeAttackThemes.matchesTarget(candidate.gear().pair().secondaryId(), observation.target())) armed = candidate;
        }
        float adjusted = armed != null && armed.outcome() == Outcome.HEAVY
            ? MeleeFortuneMath.heavyDamage(damage, armed.gear().pair().secondaryLevel()) : damage;
        Damage frame = new Damage(previous, observation, armed, adjusted); DAMAGE.set(frame); return frame;
    }
    public static void endDamage(Damage frame) {
        if (DAMAGE.get() != frame) { DAMAGE.remove(); return; }
        if (frame.previous == null) DAMAGE.remove(); else DAMAGE.set(frame.previous);
    }
    private static boolean refresh(ServerPlayer player) {
        int ticks = MeleeFortuneMath.chargeTicks(player.getCurrentItemAttackStrengthDelay());
        if (ticks == 0) return false;
        ((LivingEntityAttackChargeAccessor) (Object) player).ef$setAttackStrengthTicker(ticks);
        if (ServerPlayNetworking.canSend(player, FusionSkillNetworking.RefreshChargePayload.TYPE))
            ServerPlayNetworking.send(player, new FusionSkillNetworking.RefreshChargePayload(ticks));
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_refresh"), true);
        return true;
    }
    private static Vec3 rotate(Vec3 v, double angle) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        Damage frame = DAMAGE.get(); ServerPlayer player = hit.player();
        if (frame == null || frame.observation == null || frame.previous != null || frame.observation.player() != player
            || frame.observation.target() != hit.target() || frame.observation.weapon() != hit.weapon()
            || hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !ready(player) || player.getMainHandItem() != hit.weapon()) return;
        Gear forward = selected(hit.weapon(), false);
        if (forward != null && hit.full() && owns(player, forward)
            && MeleeAttackThemes.matchesTarget(forward.pair().primaryId(), hit.target()) && roll(player, forward.pair().secondaryLevel())
            && refresh(player)) CompendiumDiscoveryTracker.recordDiscovery(player, forward.pair().primaryId(), forward.pair().secondaryId());
        Armed armed = frame.armed;
        if (armed == null || !valid(player, armed) || !ARMED.remove(player.getUUID(), armed)) return;
        if (armed.outcome() == Outcome.REFRESH) refresh(player);
        else if (armed.outcome() == Outcome.SCATTER) {
            float damage = MeleeFortuneMath.scatterDamage(hit.actualLoss(), armed.gear().pair().primaryLevel());
            String id = armed.gear().pair().secondaryId(); Vec3 look = player.getLookAngle();
            for (double angle : new double[]{-0.22D, 0.0D, 0.22D})
                VirtualBlade.cast(player, rotate(look, angle), hit.target(), 5.0D, damage, 3,
                    victim -> MeleeAttackThemes.eligible(player, victim, id), ParticleTypes.SWEEP_ATTACK);
        }
        if (armed.outcome() != Outcome.NONE)
            CompendiumDiscoveryTracker.recordDiscovery(player, armed.gear().pair().primaryId(), armed.gear().pair().secondaryId());
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.fortune_stance_spent"), true);
    }
    public static void tickServer(MinecraftServer server) {
        ARMED.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || !valid(player, entry.getValue());
        });
        NEXT_ARM.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || player.level().getGameTime() >= entry.getValue();
        });
    }
    public static void clearPlayer(UUID id) { ARMED.remove(id); NEXT_ARM.remove(id); }
    public static void clearServer() { ARMED.clear(); NEXT_ARM.clear(); DAMAGE.remove(); }
}
