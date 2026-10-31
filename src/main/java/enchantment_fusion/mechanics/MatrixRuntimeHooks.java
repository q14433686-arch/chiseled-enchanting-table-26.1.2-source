package enchantment_fusion.mechanics;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.mechanics.signatures.MirageSnapbackSkill;
import enchantment_fusion.mechanics.signatures.FlameKineticEffects;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.mechanics.signatures.FrostFallSkills;
import enchantment_fusion.mechanics.signatures.FrostLandingCapture;
import enchantment_fusion.mechanics.signatures.LoyaltyGuard;
import enchantment_fusion.mechanics.signatures.PrecisionConstruction;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.entity.EntityType;
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
 * fishing hooks, disconnect cleanup (CPL-02), and trajectory path caching ({@link ProjectilePathTracker}).
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
    private static final Set<UUID> ACTIVE_RIPTIDE_SPINS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> IN_AFTER_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MatrixRuntimeHooks() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FlyingBladeManager.flushAll();
            ProjectilePathTracker.flushAll();
            UnderwaterMiningSkills.clear(server);
            AmberSealSkill.clear();
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> AmberSealSkill.onEntityUnload(entity));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MatrixEffectExecutor.clearCooldowns();
            SignaturePairs.clearRuntimeState();
            CrossDomainTransmutations.clearRuntimeState();
            FlyingBladeManager.flushAll();
            ProjectilePathTracker.flushAll();
            PEAK_FALL_DISTANCE.clear();
            ACTIVE_RIPTIDE_SPINS.clear();
            MirageSnapbackSkill.clearRuntimeState();
            GlazedStrikeEffect.clear();
            FrostFallSkills.clear();
            PrecisionConstruction.clear();
            BlastDefenseSkills.clear();
            FusionMatrixRegistry.setClientSnapshot(null);
        });

        // CPL-02: Evict per-player runtime caches on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID pid = handler.getPlayer().getUUID();
            PEAK_FALL_DISTANCE.remove(pid);
            ACTIVE_RIPTIDE_SPINS.remove(pid);
            MirageSnapbackSkill.onPlayerDisconnect(pid);
            GlazedStrikeEffect.onPlayerDisconnect(pid);
            AmberSealSkill.onPlayerDisconnect(pid);
            FrostFallSkills.onPlayerDisconnect(pid);
            UnderwaterMiningSkills.onPlayerDisconnect(handler.getPlayer());
            PrecisionConstruction.onDisconnect(pid);
            BlastDefenseSkills.onDisconnect(pid);
            SignaturePairs.onPlayerDisconnect(pid);
            CrossDomainTransmutations.onPlayerDisconnect(pid);
            FlyingBladeManager.onPlayerDisconnect(pid);
            ProjectilePathTracker.onPlayerDisconnect(pid);
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

        // Melee Strike + Riptide Spin-Attack (MIX-02) + Pet Protection + Multi-Slot Stacking Dispatch
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()
                && !player.isSpectator()
                && world instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && entity instanceof LivingEntity target) {
                if (!CrossDomainTransmutations.allowAttackTarget(serverPlayer, target)) {
                    return InteractionResult.FAIL;
                }
                EconomyConfig eco = FusionMatrixRegistry.serverSnapshot().economy();
                ItemStack weapon = serverPlayer.getItemInHand(hand);
                FusionStationOperations.normalizeRedesignedPrimary(weapon, serverLevel.registryAccess());
                float charge = serverPlayer.getAttackStrengthScale(0.5F);
                boolean riptideSpin = serverPlayer.isAutoSpinAttack();
                if (charge < (float) eco.meleeMinCharge() && !riptideSpin) {
                    return InteractionResult.PASS;
                }
                boolean fullCharge = charge >= (float) eco.meleeFullCharge() || riptideSpin;
                Set<TriggerType> triggers = EnumSet.of(TriggerType.ON_MELEE_STRIKE);
                if (fullCharge) {
                    triggers.add(TriggerType.ON_SWEEP_OR_CLEAVE);
                }
                if (riptideSpin || weapon.getItem() instanceof TridentItem) {
                    triggers.add(TriggerType.ON_TRIDENT_CHANNEL);
                }
                int savedInvuln = target.invulnerableTime;
                try {
                    MatrixEffectExecutor.dispatchAcrossEquippedSlots(
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

        // Right-Click Item Use (Spears preserve vanilla Hold-Right-Click Charge; Sneak+Right-Click activates Spear javelin skills!)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer) {
                FusionStationOperations.normalizeRedesignedPrimary(held, serverLevel.registryAccess());
            }
            if (world.isClientSide()) {
                if (MirageSnapbackSkill.canUse(player, held)
                    || CrossDomainTransmutations.hasReadyRightClickAbility(player, held)) {
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                if (MirageSnapbackSkill.use(serverLevel, serverPlayer, held)) return InteractionResult.SUCCESS;
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
                    MatrixEffectExecutor.dispatchAcrossEquippedSlots(
                        serverLevel, serverPlayer, tool,
                        EnumSet.of(TriggerType.ON_BLOCK_HARVEST, TriggerType.ON_UTILITY_CYCLE),
                        null, Vec3.atCenterOf(pos), true, 1.0F
                    );
                    return false;
                }
            }
            return true;
        });

        // Post-Break Block Structural Effects (3x1 / Vein Peel / Haste; drop smelting runs post-playerDestroy in ServerPlayerGameModeHarvestMixin!)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ItemStack tool = serverPlayer.getMainHandItem();
                UnderwaterMiningSkills.onMined(serverLevel, serverPlayer, pos);
                SignaturePairs.onBlockHarvested(serverLevel, serverPlayer, tool, pos, state);
                var slice = PrecisionConstruction.selected(tool, true);
                if (slice != null) PrecisionConstruction.onHarvest(serverLevel, serverPlayer, tool, pos, state, slice);
                MatrixEffectExecutor.dispatchAcrossEquippedSlots(
                    serverLevel, serverPlayer, tool,
                    EnumSet.of(TriggerType.ON_BLOCK_HARVEST, TriggerType.ON_UTILITY_CYCLE),
                    null, Vec3.atCenterOf(pos), true, 1.0F
                );
                CrossDomainTransmutations.onAfterBlockBreak(serverLevel, serverPlayer, pos, state);
            }
        });

        // Vanilla-style lethal-damage interception: never consume a real totem, and run
        // before death processing so secondary Vanishing Curse sees only genuine deaths.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
            if (entity instanceof ServerPlayer player && entity.level() instanceof ServerLevel level) {
                return LoyaltyGuard.allowDeath(level, player, source);
            }
            return true;
        });

        // Post-Damage Counter
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            AmberSealSkill.afterDamage(entity, source, damageTaken, blocked);
            if (entity instanceof ServerPlayer player) FrostLandingCapture.confirm(player, source, blocked);
            if (!blocked && damageTaken > 0.0F && entity.level() instanceof ServerLevel level
                && source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                && !MatrixEffectExecutor.isExecuting() && !GlazedStrikeEffect.isApplyingBonus()
                && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
                && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get())) {
                FlameKineticEffects.confirm(level, attacker, entity);
                GlazedStrikeEffect.onConfirmedDamage(level, attacker, entity, source);
            }
            if (Boolean.TRUE.equals(IN_AFTER_DAMAGE.get())) {
                return;
            }
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                IN_AFTER_DAMAGE.set(Boolean.TRUE);
                try {
                    SignaturePairs.recordIncomingDamage(serverPlayer, damageTaken, serverLevel.getGameTime());
                    LivingEntity attacker = source.getEntity() instanceof LivingEntity liv ? liv : null;
                    MatrixEffectExecutor.dispatchAcrossEquippedSlots(
                        serverLevel, serverPlayer, serverPlayer.getMainHandItem(),
                        EnumSet.of(TriggerType.ON_DAMAGE_TAKEN),
                        attacker, serverPlayer.position(), true, 1.0F
                    );
                    CrossDomainTransmutations.onAfterDamage(serverLevel, serverPlayer, source, damageTaken, blocked);
                } finally {
                    IN_AFTER_DAMAGE.set(Boolean.FALSE);
                }
            }
        });

        // Entity Kill hooks
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            AmberSealSkill.afterDeath(entity, damageSource);
            if (damageSource.getEntity() instanceof ServerPlayer attacker && entity.level() instanceof ServerLevel level) {
                GlazedStrikeEffect.onConfirmedDeath(level, attacker, entity, damageSource);
            }
            if (entity instanceof ServerPlayer deadPlayer) {
                MirageSnapbackSkill.onPlayerDisconnect(deadPlayer.getUUID());
                UnderwaterMiningSkills.onPlayerDisconnect(deadPlayer);
                FrostFallSkills.onPlayerDisconnect(deadPlayer.getUUID());
                BlastDefenseSkills.onDisconnect(deadPlayer.getUUID());
                ACTIVE_RIPTIDE_SPINS.remove(deadPlayer.getUUID());
            }
            if (damageSource.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                SignaturePairs.onEntityKilled(serverLevel, serverPlayer, entity);
                ItemStack main = serverPlayer.getMainHandItem();
                MatrixEffectExecutor.dispatchAcrossEquippedSlots(
                    serverLevel, serverPlayer, main,
                    EnumSet.of(TriggerType.ON_UTILITY_CYCLE),
                    entity, entity.position(), true, 1.0F
                );
                CrossDomainTransmutations.onEntityKilled(serverLevel, serverPlayer, entity);
            }
        });

        // Movement / Landing + 3D Flying Blades + Trajectory Path Cache
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FlameKineticEffects.tickServer(server);
            GlazedStrikeEffect.tickServer(server);
            AmberSealSkill.tickServer(server);
            UnderwaterMiningSkills.tickServer(server);
            FrostFallSkills.tickServer(server);
            BlastDefenseSkills.tickServer(server);
            FlyingBladeManager.tickServer(server);
            ProjectilePathTracker.tickServer(server);
            SignaturePairs.tickWorldDots(server);
            for (ServerLevel world : server.getAllLevels()) {
                CrossDomainTransmutations.tickWorldItems(world);
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player.level() instanceof ServerLevel serverLevel)) continue;
                boolean normalized = false;
                for (EquipmentSlot slot : ARMOR_AND_HAND_SLOTS) {
                    normalized |= FusionStationOperations.normalizeRedesignedPrimary(player.getItemBySlot(slot), serverLevel.registryAccess());
                }
                if (serverLevel.getGameTime() % 20L == 0L) {
                    var inventory = player.getInventory();
                    for (int i = 0; i < inventory.getContainerSize(); i++) {
                        normalized |= FusionStationOperations.normalizeRedesignedPrimary(inventory.getItem(i), serverLevel.registryAccess());
                    }
                }
                if (normalized) player.getInventory().setChanged();
                SignaturePairs.tickServerPlayer(serverLevel, player);
                CrossDomainTransmutations.tickServerPlayer(serverLevel, player);

                // Vanilla spin attacks do not necessarily pass through AttackEntityCallback.
                // Dispatch once at the actual start of a Riptide launch, not every spin tick.
                if (player.isAutoSpinAttack()) {
                    if (ACTIVE_RIPTIDE_SPINS.add(player.getUUID())) {
                        ItemStack main = player.getMainHandItem();
                        ItemStack spinWeapon = main.getItem() instanceof TridentItem ? main : player.getOffhandItem();
                        if (spinWeapon.getItem() instanceof TridentItem) {
                            MatrixEffectExecutor.dispatchAcrossEquippedSlots(serverLevel, player, spinWeapon,
                                EnumSet.of(TriggerType.ON_TRIDENT_CHANNEL), null, player.position(), true, 1.0F);
                        }
                    }
                } else {
                    ACTIVE_RIPTIDE_SPINS.remove(player.getUUID());
                }

                boolean holdsRelevantRanged = isTrajectoryRangedWeapon(player.getMainHandItem())
                    || isTrajectoryRangedWeapon(player.getOffhandItem());
                if (holdsRelevantRanged || serverLevel.getGameTime() % 2L == 0L) {
                    for (Projectile proj : serverLevel.getEntitiesOfClass(
                        Projectile.class,
                        player.getBoundingBox().inflate(72.0D),
                        p -> p.isAlive() && p.getOwner() == player && p.getType() != EntityType.FISHING_BOBBER && p.tickCount <= 180
                    )) {
                        ItemStack weapon = proj.getWeaponItem();
                        if ((weapon == null || weapon.isEmpty()) && proj instanceof ProjectileLaunchWeaponAccess acc) {
                            weapon = acc.ef$getLaunchWeapon();
                        }
                        if (weapon != null && !weapon.isEmpty() && (hasCustomFusionOrTransmute(weapon) || playerHasAnyEquippedFusion(player))) {
                            ProjectilePathTracker.recordProjectileStep(serverLevel, proj, player, weapon);
                            if (CrossDomainTransmutations.hasAnyTransmutedEnchants(weapon)) {
                                CrossDomainTransmutations.onProjectileTick(serverLevel, proj, player, weapon);
                            }
                        }
                    }
                }

                UUID id = player.getUUID();
                if (!player.onGround() && player.fallDistance > 1.5F) {
                    PEAK_FALL_DISTANCE.merge(id, (float) player.fallDistance, Math::max);
                } else if (player.onGround()) {
                    Float peak = PEAK_FALL_DISTANCE.remove(id);
                    if (peak != null && peak >= 3.0F) {
                        MatrixEffectExecutor.dispatchAcrossEquippedSlots(
                            serverLevel, player, player.getMainHandItem(),
                            EnumSet.of(TriggerType.ON_MOVEMENT_OR_LAND),
                            null, player.position(), true, 1.0F
                        );
                    }
                }
            }
        });
    }

    private static boolean hasCustomFusionOrTransmute(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (CrossDomainTransmutations.hasAnyTransmutedEnchants(stack)) return true;
        return !stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty();
    }

    private static boolean playerHasAnyEquippedFusion(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_AND_HAND_SLOTS) {
            ItemStack eq = player.getItemBySlot(slot);
            if (!eq.isEmpty() && !eq.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrajectoryRangedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean ranged = stack.getItem() instanceof BowItem
            || stack.getItem() instanceof CrossbowItem
            || stack.getItem() instanceof TridentItem;
        return ranged && hasCustomFusionOrTransmute(stack);
    }
}
