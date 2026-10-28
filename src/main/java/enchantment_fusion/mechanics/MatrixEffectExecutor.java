package enchantment_fusion.mechanics;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Executes the 8 Triggers x 14 Payloads adaptive matrix on the server thread with
 * per-player internal cooldowns and re-entrancy protection.
 */
public final class MatrixEffectExecutor {
    private static final ThreadLocal<Boolean> IN_EXECUTION = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private MatrixEffectExecutor() {}

    public static void clearCooldowns() {
        COOLDOWNS.clear();
    }

    public static void dispatch(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        TriggerType firedTrigger,
        @Nullable LivingEntity targetEntity,
        Vec3 originPos
    ) {
        if (Boolean.TRUE.equals(IN_EXECUTION.get()) || hostStack == null || hostStack.isEmpty()) {
            return;
        }
        FusedEnchantmentList list = hostStack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) {
            return;
        }

        HostForm hostForm = HostForm.resolve(hostStack);
        FusionMatrixRegistry.Snapshot snapshot = FusionMatrixRegistry.current();
        long now = level.getGameTime();

        IN_EXECUTION.set(Boolean.TRUE);
        try {
            for (FusedPair pair : list.pairs()) {
                FusionMatrixRegistry.MatrixProfile primaryProfile = snapshot.resolve(pair.primaryId());
                if (primaryProfile.trigger() != firedTrigger) {
                    continue;
                }
                String cdKey = cooldownKey(player.getUUID(), firedTrigger, pair.secondaryId());
                long readyAt = COOLDOWNS.getOrDefault(cdKey, 0L);
                if (now < readyAt) {
                    continue;
                }
                COOLDOWNS.put(cdKey, now + firedTrigger.cooldownTicks());

                PayloadType payload = snapshot.resolve(pair.secondaryId()).payload();
                float scale = Math.max(0.35F, Math.min(2.0F, pair.secondaryLevel() * 0.45F));
                executePayload(level, player, hostStack, hostForm, payload, scale, targetEntity, originPos);
            }
        } finally {
            IN_EXECUTION.set(Boolean.FALSE);
        }
    }

    private static String cooldownKey(UUID uuid, TriggerType trigger, String secondaryId) {
        return uuid + "|" + trigger.name() + "|" + secondaryId;
    }

    private static void executePayload(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        HostForm hostForm,
        PayloadType payload,
        float scale,
        @Nullable LivingEntity target,
        Vec3 pos
    ) {
        switch (payload) {
            case PYRO -> {
                if (target != null) {
                    target.igniteForSeconds(Math.max(2.0F, 3.0F * scale));
                }
                if (hostForm == HostForm.ARMOR && player.isOnFire()) {
                    player.clearFire();
                }
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.03D);
            }
            case CRYO -> {
                if (target != null) {
                    target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + Math.round(90 * scale)));
                    grantEffect(target, "slowness", Math.round(50 * scale), 1);
                }
                freezeNearbySurfaceWater(level, BlockPos.containing(pos));
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.5D, pos.z, 10, 0.35D, 0.25D, 0.35D, 0.02D);
            }
            case VOLT -> {
                if (target != null) {
                    float bonus = (level.isThundering() || target.isInWaterOrRain()) ? 3.5F * scale : 2.0F * scale;
                    target.hurtServer(level, level.damageSources().magic(), bonus);
                    grantEffect(target, "slowness", 30, 1);
                } else if (hostForm == HostForm.TOOL) {
                    grantEffect(player, "haste", Math.round(60 * scale), 0);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.08D);
                level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.65F, 1.35F);
            }
            case GALE -> {
                if (hostForm == HostForm.TOOL) {
                    pullNearbyDrops(level, player, pos, 5.0D);
                } else {
                    AABB box = AABB.ofSize(pos, 5.0D, 3.0D, 5.0D);
                    List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
                    for (LivingEntity mob : nearby) {
                        Vec3 push = mob.position().subtract(player.position()).normalize().scale(0.55D * scale);
                        mob.push(push.x, 0.28D * scale, push.z);
                    }
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.4D, pos.z, 10, 0.4D, 0.2D, 0.4D, 0.05D);
            }
            case TIDE -> {
                if (target != null && (player.isInWaterOrRain() || target.isInWaterOrRain())) {
                    target.hurtServer(level, level.damageSources().magic(), 2.2F * scale);
                }
                grantEffect(player, player.isInWater() ? "dolphins_grace" : "speed", Math.round(55 * scale), 0);
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.4D, pos.z, 10, 0.3D, 0.2D, 0.3D, 0.06D);
            }
            case RETURN -> {
                pullNearbyDrops(level, player, pos, 6.5D);
                level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 0.5D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
            }
            case PIERCE -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 2.0F * scale);
                } else if (hostForm == HostForm.ARMOR) {
                    grantEffect(player, "strength", 50, 0);
                }
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.6D, pos.z, 8, 0.2D, 0.2D, 0.2D, 0.08D);
            }
            case SCATTER -> {
                AABB area = AABB.ofSize(pos, 4.0D, 2.5D, 4.0D);
                List<LivingEntity> splashTargets = level.getEntitiesOfClass(
                    LivingEntity.class, area, e -> e != player && e != target && e.isAlive()
                );
                int hitCount = 0;
                for (LivingEntity extra : splashTargets) {
                    if (++hitCount > 3) break;
                    extra.hurtServer(level, level.damageSources().playerAttack(player), 1.8F * scale);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.6D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            case GRAVITY -> {
                AABB slamBox = AABB.ofSize(pos, 5.5D, 2.5D, 5.5D);
                List<LivingEntity> slammed = level.getEntitiesOfClass(LivingEntity.class, slamBox, e -> e != player && e.isAlive());
                float slamDamage = Math.max(2.0F, (float) Math.min(10.0D, player.fallDistance * 0.8D + 2.2D * scale));
                for (LivingEntity mob : slammed) {
                    mob.hurtServer(level, level.damageSources().playerAttack(player), slamDamage);
                    mob.push(0.0D, 0.32D, 0.0D);
                }
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.15D, pos.z, 12, 0.6D, 0.1D, 0.6D, 0.04D);
                level.playSound(null, BlockPos.containing(pos), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.4F);
            }
            case EDGE -> {
                if (hostForm == HostForm.TOOL) {
                    grantEffect(player, "haste", Math.round(55 * scale), 0);
                } else {
                    grantEffect(player, "strength", Math.round(45 * scale), 0);
                }
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 0.6D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
            }
            case BANE -> {
                if (target != null) {
                    grantEffect(target, "slowness", Math.round(60 * scale), 1);
                    target.hurtServer(level, level.damageSources().magic(), 1.8F * scale);
                }
                level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.5D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.02D);
            }
            case FORTUNE -> {
                if (level.getRandom().nextFloat() < 0.35F * scale) {
                    ExperienceOrb.award(level, pos, Math.max(1, Math.round(2 * scale)));
                }
                if (hostForm == HostForm.TOOL && level.getRandom().nextFloat() < 0.08F * scale) {
                    Item bonusItem = level.getRandom().nextBoolean() ? Items.FLINT : Items.AMETHYST_SHARD;
                    ItemEntity drop = new ItemEntity(level, pos.x, pos.y + 0.3D, pos.z, new ItemStack(bonusItem, 1));
                    level.addFreshEntity(drop);
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.5D, pos.z, 5, 0.25D, 0.25D, 0.25D, 0.02D);
            }
            case AEGIS -> {
                grantEffect(player, "absorption", Math.round(70 * scale), 0);
                grantEffect(player, "resistance", Math.round(45 * scale), 0);
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.6D, pos.z, 6, 0.25D, 0.3D, 0.25D, 0.02D);
            }
            case MEND -> {
                if (hostStack.isDamaged()) {
                    int repair = Math.max(1, Math.round(2 * scale));
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - repair));
                }
                grantEffect(player, "speed", 40, 0);
                level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y + 0.8D, pos.z, 8, 0.3D, 0.3D, 0.3D, 0.05D);
            }
        }
    }

    private static void pullNearbyDrops(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
        AABB box = AABB.ofSize(center, radius * 2.0D, radius, radius * 2.0D);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
            itemEntity.setNoPickUpDelay();
            itemEntity.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
        }
    }

    private static void freezeNearbySurfaceWater(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos waterPos = center.offset(dx, -1, dz);
                BlockState state = level.getBlockState(waterPos);
                if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(waterPos.above()).isAir()) {
                    level.setBlockAndUpdate(waterPos, Blocks.FROSTED_ICE.defaultBlockState());
                }
            }
        }
    }

    private static void grantEffect(LivingEntity entity, String vanillaEffectPath, int durationTicks, int amplifier) {
        BuiltInRegistries.MOB_EFFECT
            .get(Identifier.withDefaultNamespace(vanillaEffectPath))
            .ifPresent(holder -> entity.addEffect(new MobEffectInstance(holder, Math.max(10, durationTicks), amplifier, true, true)));
    }
}
