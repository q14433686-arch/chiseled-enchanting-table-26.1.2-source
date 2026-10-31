package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.TemporaryFrostEffects;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Selected Feather Falling/Frost Walker skills, for actual worn boots. */
public final class FrostFallSkills {
    public static final String FEATHER = "minecraft:feather_falling";
    public static final String FROST = "minecraft:frost_walker";
    public static final String DEPTH = "minecraft:depth_strider";
    public static final String WAVE_SIGNATURE = "frost_meteor_fall";
    public static final String STEP_SIGNATURE = "feather_rime_glide";
    private static final int CHARGE_LIFETIME = 400;
    private static final int STEP_LIMIT = 3;
    private static final int STEP_COOLDOWN = 30;
    private static final int MAX_WAVES = 128;

    public record Gear(ItemStack stack, FusedPair pair) {}
    private record Charge(ResourceKey<Level> dimension, Gear gear, float amount, long expires) {}
    private record StepSession(ResourceKey<Level> dimension, int used, long nextUse) {}
    private static final class Wave {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final Vec3 origin;
        final Vec3 direction;
        final float charge;
        final int frostLevel;
        final double range;
        final Set<UUID> hit = new HashSet<>();
        int age;
        Wave(ServerPlayer player, Gear gear, float charge) {
            owner = player.getUUID();
            dimension = player.level().dimension();
            origin = player.position().add(0.0D, 0.4D, 0.0D);
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
            // Yaw still supplies a direction even when looking almost exactly up or down.
            direction = horizontal.lengthSqr() > 1.0E-8D ? horizontal.normalize()
                : new Vec3(-Math.sin(Math.toRadians(player.getYRot())), 0.0D, Math.cos(Math.toRadians(player.getYRot())));
            this.charge = charge;
            frostLevel = gear.pair().secondaryLevel();
            range = Math.min(9.0D, 3.0D + 0.25D * charge + 0.5D * Math.clamp(frostLevel, 1, 4));
        }
    }

    // Server-thread only; no block or item data is stored here and all state has bounded lifetime.
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final Map<UUID, StepSession> STEPS = new HashMap<>();
    private static final List<Wave> WAVES = new ArrayList<>();

    private FrostFallSkills() {}

    public static boolean isWavePair(@Nullable FusedPair pair) {
        return pair != null && FEATHER.equals(pair.primaryId()) && FROST.equals(pair.secondaryId());
    }
    public static boolean isStepPair(@Nullable FusedPair pair) {
        return pair != null && FROST.equals(pair.primaryId()) && FEATHER.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(Set<FusionCarrier> carriers) {
        return carriers != null && carriers.contains(FusionCarrier.FOOT_ARMOR);
    }
    public static boolean applies(@Nullable FusedPair pair, ItemStack stack, String signature) {
        if (stack == null || stack.isEmpty() || !supportsCarrier(FusionCarrier.classify(stack))) return false;
        return (isWavePair(pair) && WAVE_SIGNATURE.equals(signature))
            || (isStepPair(pair) && STEP_SIGNATURE.equals(signature));
    }

    public static @Nullable Gear find(ServerPlayer player, boolean wave) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty() || !supportsCarrier(FusionCarrier.classify(boots))) return null;
        Gear best = null;
        for (FusedPair pair : boots.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (!(wave ? isWavePair(pair) : isStepPair(pair))) continue;
            if (!SignatureCatalog.resolve(pair).map(entry -> applies(pair, boots, entry.id())).orElse(false)) continue;
            if (best == null || pair.primaryLevel() + (long) pair.secondaryLevel()
                > best.pair().primaryLevel() + (long) best.pair().secondaryLevel()) best = new Gear(boots, pair);
        }
        return best;
    }

    public static boolean stillEquipped(ServerPlayer player, Gear gear) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        return boots == gear.stack() && !boots.isEmpty()
            && boots.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            && SignatureCatalog.resolve(gear.pair()).map(entry -> applies(gear.pair(), boots, entry.id())).orElse(false);
    }

    public static boolean isFeatherEnchantment(ServerLevel level, Enchantment enchantment) {
        return FusionStationOperations.lookupEnchantment(level.registryAccess(), FEATHER)
            .map(holder -> holder.value() == enchantment).orElse(false);
    }

    /** Replace only the conflicting location-based Frost Walker water-freezing component. */
    public static boolean suppressNativeWaterFreeze(Enchantment enchantment, ServerLevel level,
                                                     EnchantedItemInUse item, LivingEntity wearer) {
        if (!(wearer instanceof ServerPlayer player) || item.inSlot() != EquipmentSlot.FEET
            || item.itemStack() != player.getItemBySlot(EquipmentSlot.FEET)
            || !supportsCarrier(FusionCarrier.classify(item.itemStack()))) return false;
        if (!FusionStationOperations.lookupEnchantment(level.registryAccess(), FROST)
            .map(holder -> holder.value() == enchantment).orElse(false)) return false;
        for (FusedPair pair : item.itemStack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            String sig = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
            if ((isStepPair(pair) && STEP_SIGNATURE.equals(sig))
                || (FrostWakeDiveSkills.isWakePair(pair) && FrostWakeDiveSkills.WAKE.equals(sig))
                || (FrostFireSkills.isBasaltPair(pair) && FrostFireSkills.BASALT.equals(sig))) return true;
        }
        return false;
    }

    /** Called only after a non-lethal, accepted fall with attributable vanilla protection. */
    public static void onAbsorbed(ServerPlayer player, Gear gear, float amount) {
        if (!(player.level() instanceof ServerLevel level) || !stillEquipped(player, gear)
            || !Float.isFinite(amount) || amount <= 0.0F) return;
        long now = level.getGameTime();
        Charge previous = CHARGES.get(player.getUUID());
        float old = previous != null && previous.dimension().equals(level.dimension())
            && previous.gear().equals(gear) && now < previous.expires() ? previous.amount() : 0.0F;
        float cap = Math.min(20.0F, 8.0F + 2.0F * Math.clamp(gear.pair().secondaryLevel(), 1, 6));
        float total = Math.min(cap, old + amount);
        CHARGES.put(player.getUUID(), new Charge(level.dimension(), gear, total, now + CHARGE_LIFETIME));
        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.3D, player.getZ(),
            10, 0.25D, 0.15D, 0.25D, 0.03D);
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.frost_charge", Math.round(total * 10.0F) / 10.0F), true);
        CompendiumDiscoveryTracker.recordDiscovery(player, gear.pair().primaryId(), gear.pair().secondaryId());
        runAberration(level, player, gear.pair(), TriggerType.ON_MOVEMENT_OR_LAND);
    }

    public static boolean releaseWave(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
            || player.containerMenu != player.inventoryMenu) return false;
        Gear gear = find(player, true);
        Charge charge = CHARGES.get(player.getUUID());
        if (gear == null || charge == null || !charge.gear().equals(gear) || !charge.dimension().equals(level.dimension())
            || level.getGameTime() >= charge.expires() || !stillEquipped(player, gear)) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.frost_no_charge"), true);
            return false;
        }
        if (WAVES.size() >= MAX_WAVES) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.frost_wave_busy"), true);
            return false;
        }
        WAVES.add(new Wave(player, gear, charge.amount()));
        CHARGES.remove(player.getUUID());
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    public static boolean createAirStep(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
            || player.containerMenu != player.inventoryMenu || player.getAbilities().flying) return false;
        Gear gear = find(player, false);
        if (gear == null || player.onGround() || player.isInWater()) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.frost_step_air_only"), true);
            return false;
        }
        long now = level.getGameTime();
        StepSession session = STEPS.get(player.getUUID());
        if (session == null || !session.dimension().equals(level.dimension())) session = new StepSession(level.dimension(), 0, 0);
        if (session.used() >= STEP_LIMIT || now < session.nextUse()) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.frost_step_limit"), true);
            return false;
        }
        int placed = TemporaryFrostEffects.placeAirStep(level, player, gear.pair().primaryLevel());
        if (placed <= 0) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.frost_step_no_space"), true);
            return false;
        }
        STEPS.put(player.getUUID(), new StepSession(level.dimension(), session.used() + 1, now + STEP_COOLDOWN));
        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() - 0.1D, player.getZ(),
            12, 0.3D, 0.08D, 0.3D, 0.03D);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.7F);
        CompendiumDiscoveryTracker.recordDiscovery(player, gear.pair().primaryId(), gear.pair().secondaryId());
        runAberration(level, player, gear.pair(), TriggerType.ON_MOVEMENT_OR_LAND);
        return true;
    }

    private static void runAberration(ServerLevel level, ServerPlayer player, FusedPair pair, TriggerType trigger) {
        if (pair.isAberrant() && !MatrixEffectExecutor.isExecuting()
            && MatrixEffectExecutor.acquireDeferredCooldown(level, player, pair, trigger)) {
            MatrixEffectExecutor.runDeferredAberration(level, player, pair, player.position());
        }
    }

    public static void tickServer(MinecraftServer server) {
        CHARGES.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Charge charge = entry.getValue();
            return player == null || !player.isAlive() || !player.level().dimension().equals(charge.dimension())
                || player.level().getGameTime() >= charge.expires() || !stillEquipped(player, charge.gear());
        });
        STEPS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(entry.getValue().dimension())) return true;
            // Landing on the temporary step itself is not a fresh jump budget.
            return player.isInWater() || (player.onGround() && !TemporaryFrostEffects.standingOnTemporaryPath(level, player));
        });
        for (Iterator<Wave> iterator = WAVES.iterator(); iterator.hasNext();) {
            Wave wave = iterator.next();
            ServerLevel level = server.getLevel(wave.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(wave.owner);
            if (level == null || owner == null || owner.level() != level || !owner.isAlive()) {
                iterator.remove();
                continue;
            }
            double distance = ++wave.age * 0.55D;
            Vec3 center = wave.origin.add(wave.direction.scale(distance));
            if (distance > wave.range || !level.hasChunkAt(BlockPos.containing(center))
                || level.clip(new ClipContext(wave.origin, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner))
                    .getType() != HitResult.Type.MISS) {
                iterator.remove();
                continue;
            }
            double radius = 0.65D + distance * 0.2D;
            level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 7, radius * 0.35D, 0.15D, radius * 0.35D, 0.015D);
            AABB bounds = AABB.ofSize(center, radius * 2.0D, 2.5D, radius * 2.0D);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> LoyaltyGuard.eligibleTarget(owner, entity))) {
                if (wave.hit.size() >= 128) break;
                if (wave.hit.contains(target.getUUID()) || !level.mayInteract(owner, target.blockPosition())) continue;
                Vec3 targetCenter = target.getBoundingBox().getCenter();
                if (level.clip(new ClipContext(wave.origin, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner))
                    .getType() != HitResult.Type.MISS) continue;
                wave.hit.add(target.getUUID());
                int freeze = Math.min(220, 40 + Math.round(wave.charge * 7.0F) + 10 * Math.clamp(wave.frostLevel, 1, 4));
                if (target.canFreeze()) target.setTicksFrozen(Math.max(target.getTicksFrozen(), freeze));
                SignatureRuntimeSupport.grantEffect(target, "slowness", Math.min(120, 35 + freeze / 2), 0);
            }
        }
    }

    public static void onPlayerDisconnect(UUID id) {
        CHARGES.remove(id);
        STEPS.remove(id);
        WAVES.removeIf(wave -> wave.owner.equals(id));
    }
    public static void clear() {
        CHARGES.clear();
        STEPS.clear();
        WAVES.clear();
    }
}
