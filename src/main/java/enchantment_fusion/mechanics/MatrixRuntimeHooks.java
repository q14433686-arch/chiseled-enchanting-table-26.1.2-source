package enchantment_fusion.mechanics;

import enchantment_fusion.model.TriggerType;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

/**
 * Wires Fabric server lifecycle, damage/death interception, combat, mining, item usage,
 * fishing hooks, disconnect cleanup (CPL-02), and throttled world scans (CPL-03).
 */
public final class MatrixRuntimeHooks {
    private static final EquipmentSlot[] ARMOR_AND_HAND_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.MAINHAND
    };

    private static final Map<UUID, Float> PEAK_FALL_DISTANCE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> IN_AFTER_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MatrixRuntimeHooks() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AberrantRegistry.flushAllCages(server);
            FlyingBladeManager.flushAll();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MatrixEffectExecutor.clearCooldowns();
            SignaturePairs.clearRuntimeState();
            AberrantRegistry.clearRuntimeState();
            CrossDomainTransmutations.clearRuntimeState();
            FlyingBladeManager.flushAll();
            PEAK_FALL_DISTANCE.clear();
        });

        // CPL-02: Evict per-player runtime caches on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID pid = handler.getPlayer().getUUID();
            PEAK_FALL_DISTANCE.remove(pid);
            SignaturePairs.onPlayerDisconnect(pid);
            CrossDomainTransmutations.onPlayerDisconnect(pid);
            FlyingBladeManager.onPlayerDisconnect(pid);
            MatrixEffectExecutor.onPlayerDisconnect(pid);
            CompendiumDiscoveryTracker.onPlayerDisconnect(pid);
        });

        // Pre-damage interception (API-04: passes DamageSource to respect BYPASSES_INVULNERABILITY)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                if (!SignaturePairs.allowIncomingDamage(serverPlayer, source, amount)) {
                    return false;
                }
                return CrossDomainTransmutations.allowIncomingDamage(serverLevel, serverPlayer, source, amount);
            }
            return true;
        });

        // Lethal interception (API-04: passes DamageSource to respect /kill)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                if (!SignaturePairs.allowPlayerDeath(serverPlayer, damageSource)) {
                    return false;
                }
                return CrossDomainTransmutations.allowPlayerDeath(serverLevel, serverPlayer, damageSource);
            }
            return true;
        });

        // Melee Strike + Riptide Spin-Attack (MIX-02) + Pet Protection + Charge-Gated Dispatch (CPL-01)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()
                && !player.isSpectator()
                && world instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && entity instanceof LivingEntity target) {
                if (!CrossDomainTransmutations.allowAttackTarget(serverPlayer, target)) {
                    return InteractionResult.FAIL;
                }
                ItemStack weapon = serverPlayer.getItemInHand(hand);
                float charge = serverPlayer.getAttackStrengthScale(0.5F);
                boolean riptideSpin = serverPlayer.isAutoSpinAttack();
                // CPL-01: Require >= 55% attack charge (or active Riptide spin) before firing bonus magic procs
                if (charge < 0.55F && !riptideSpin) {
                    return InteractionResult.PASS;
                }
                boolean fullCharge = charge >= 0.90F || riptideSpin;
                Set<TriggerType> triggers = EnumSet.of(TriggerType.ON_MELEE_STRIKE);
                if (fullCharge) {
                    triggers.add(TriggerType.ON_SWEEP_OR_CLEAVE);
                }
                if (riptideSpin || weapon.getItem() instanceof TridentItem) {
                    triggers.add(TriggerType.ON_TRIDENT_CHANNEL);
                }
                int savedInvuln = target.invulnerableTime;
                try {
                    MatrixEffectExecutor.dispatch(
                        serverLevel, serverPlayer, weapon, triggers, target, target.position(), fullCharge, charge
                    );
                    target.invulnerableTime = 0;
                    CrossDomainTransmutations.onMeleeAttack(serverLevel, serverPlayer, target, fullCharge);
                } finally {
                    target.invulnerableTime = savedInvuln;
                }
            }
            return InteractionResult.PASS;
        });

        // Right-Click Item Use (MIX-03: Fishing Rod reel-in signatures + direct 3D Returning Blade / Active Skills!)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (world.isClientSide()) {
                if (CrossDomainTransmutations.hasReadyRightClickAbility(player, held)) {
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                if (held.getItem() instanceof FishingRodItem && serverPlayer.fishing != null) {
                    FishingHook bobber = serverPlayer.fishing;
                    LivingEntity hookedMob = bobber.getHookedIn() instanceof LivingEntity liv ? liv : null;
                    boolean inWaterReel = bobber.isInWater() && hookedMob == null;
                    SignaturePairs.onFishingReel(serverLevel, serverPlayer, held, hookedMob, inWaterReel);
                    CrossDomainTransmutations.onFishingReel(serverLevel, serverPlayer, held, hookedMob, bobber.position());
                }
                if (CrossDomainTransmutations.onUseItem(serverLevel, serverPlayer, hand, held)) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        // Pre-Break Interception (API-03: only Adventure-checked infested stone purger cancels BEFORE)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                if (!CrossDomainTransmutations.onBeforeBlockBreak(serverLevel, serverPlayer, pos, state)) {
                    SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                    MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_BLOCK_HARVEST, null, Vec3.atCenterOf(pos));
                    MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_UTILITY_CYCLE, null, Vec3.atCenterOf(pos));
                    return false;
                }
            }
            return true;
        });

        // Post-Break Harvest (Auto-Smelt Pick, Charcoal Axe, 3x1 Excavate, Magnet, Matrix, etc.)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_BLOCK_HARVEST, null, Vec3.atCenterOf(pos));
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, tool, TriggerType.ON_UTILITY_CYCLE, null, Vec3.atCenterOf(pos));
                CrossDomainTransmutations.onAfterBlockBreak(serverLevel, serverPlayer, pos, state);
            }
        });

        // Post-Damage Counter
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (Boolean.TRUE.equals(IN_AFTER_DAMAGE.get())) {
                return;
            }
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                IN_AFTER_DAMAGE.set(Boolean.TRUE);
                try {
                    SignaturePairs.recordIncomingDamage(serverPlayer, damageTaken, serverLevel.getGameTime());
                    LivingEntity attacker = source.getEntity() instanceof LivingEntity liv ? liv : null;
                    for (EquipmentSlot slot : ARMOR_AND_HAND_SLOTS) {
                        ItemStack equipped = serverPlayer.getItemBySlot(slot);
                        if (!equipped.isEmpty()) {
                            MatrixEffectExecutor.dispatch(
                                serverLevel, serverPlayer, equipped, TriggerType.ON_DAMAGE_TAKEN, attacker, serverPlayer.position()
                            );
                        }
                    }
                    CrossDomainTransmutations.onAfterDamage(serverLevel, serverPlayer, source, damageTaken, blocked);
                } finally {
                    IN_AFTER_DAMAGE.set(Boolean.FALSE);
                }
            }
        });

        // Entity Kill hooks
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                SignaturePairs.onEntityKilled(serverLevel, serverPlayer, entity);
                ItemStack main = serverPlayer.getMainHandItem();
                MatrixEffectExecutor.dispatch(serverLevel, serverPlayer, main, TriggerType.ON_UTILITY_CYCLE, entity, entity.position());
                CrossDomainTransmutations.onEntityKilled(serverLevel, serverPlayer, entity);
            }
        });

        // Movement / Landing + Frosted Ice Cage Cracking + 3D Flying Blades + Gated Projectile Flight Physics (CPL-03)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FlyingBladeManager.tickServer(server);
            SignaturePairs.tickWorldDots(server);
            AberrantRegistry.tickFrostedCages(server);
            for (ServerLevel world : server.getAllLevels()) {
                CrossDomainTransmutations.tickWorldItems(world);
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player.level() instanceof ServerLevel serverLevel)) continue;
                SignaturePairs.tickServerPlayer(serverLevel, player);
                CrossDomainTransmutations.tickServerPlayer(serverLevel, player);

                // CPL-03: Only scan nearby in-flight projectiles when player holds a ranged/trident weapon with transmutations
                // (or on a 4-tick cadence for previously launched arrows whose weapon ItemStack is embedded on the projectile)
                boolean holdsTransmutedRanged = isTransmutedRangedWeapon(player.getMainHandItem())
                    || isTransmutedRangedWeapon(player.getOffhandItem());
                if (holdsTransmutedRanged || serverLevel.getGameTime() % 4L == 0L) {
                    for (Projectile proj : serverLevel.getEntitiesOfClass(
                        Projectile.class,
                        player.getBoundingBox().inflate(64.0D),
                        p -> p.isAlive() && p.getOwner() == player && p.tickCount <= 160
                    )) {
                        ItemStack weapon = proj.getWeaponItem();
                        if ((weapon == null || weapon.isEmpty()) && proj instanceof ProjectileLaunchWeaponAccess acc) {
                            weapon = acc.ef$getLaunchWeapon();
                        }
                        if (weapon != null && !weapon.isEmpty() && CrossDomainTransmutations.hasAnyTransmutedEnchants(weapon)) {
                            CrossDomainTransmutations.onProjectileTick(serverLevel, proj, player, weapon);
                        }
                    }
                }

                UUID id = player.getUUID();
                if (!player.onGround() && player.fallDistance > 1.5F) {
                    PEAK_FALL_DISTANCE.merge(id, (float) player.fallDistance, Math::max);
                } else if (player.onGround()) {
                    Float peak = PEAK_FALL_DISTANCE.remove(id);
                    if (peak != null && peak >= 3.0F) {
                        for (EquipmentSlot slot : ARMOR_AND_HAND_SLOTS) {
                            ItemStack gear = player.getItemBySlot(slot);
                            if (!gear.isEmpty()) {
                                MatrixEffectExecutor.dispatch(
                                    serverLevel, player, gear, TriggerType.ON_MOVEMENT_OR_LAND, null, player.position()
                                );
                            }
                        }
                    }
                }
            }
        });
    }

    private static boolean isTransmutedRangedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean ranged = stack.getItem() instanceof BowItem
            || stack.getItem() instanceof CrossbowItem
            || stack.getItem() instanceof TridentItem;
        return ranged && CrossDomainTransmutations.hasAnyTransmutedEnchants(stack);
    }
}
