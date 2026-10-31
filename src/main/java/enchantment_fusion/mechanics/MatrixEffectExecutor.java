package enchantment_fusion.mechanics;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.ProjectilePathTracker.CachedFlightPath;
import enchantment_fusion.mechanics.ProjectilePathTracker.PathTrailStyle;
import enchantment_fusion.mechanics.signatures.FlameKineticEffects;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.mechanics.signatures.FrostFallSkills;
import enchantment_fusion.mechanics.signatures.FrostWakeDiveSkills;
import enchantment_fusion.mechanics.signatures.LoyaltyGuard;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.PrecisionConstruction;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Executes Aberrant mutations, Handcrafted Signature Resonances, and the Directional
 * Catalyst x Law Synergy Engine ({@code [A ▷ B] != [B ▷ A]}) across any host equipment ("万物可铭刻"),
 * supporting multi-pair ({@code ◇◇}) and multi-slot stacking while deduplicating identical pairs (BAL-02).
 */
public final class MatrixEffectExecutor {
    private static final ThreadLocal<Boolean> IN_EXECUTION = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final EquipmentSlot[] SECONDARY_EQUIP_SLOTS = {
        EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private MatrixEffectExecutor() {}

    public static void clearCooldowns() {
        COOLDOWNS.clear();
        SignaturePairs.clearRuntimeState();
        GlazedStrikeEffect.clear();
    }

    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId == null) return;
        String prefix = playerId + "|";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static boolean isExecuting() {
        return Boolean.TRUE.equals(IN_EXECUTION.get());
    }

    /** Charge a deferred signature only after its corresponding vanilla attack did damage. */
    public static boolean acquireDeferredCooldown(ServerLevel level, ServerPlayer player, FusedPair pair, TriggerType trigger) {
        String key = cooldownKey(player.getUUID(), pair.primaryId(), pair.secondaryId());
        long now = level.getGameTime();
        if (now < COOLDOWNS.getOrDefault(key, 0L)) return false;
        COOLDOWNS.put(key, now + Math.max(6, FusionMatrixRegistry.serverSnapshot().economy().cooldownTicksFor(trigger)));
        return true;
    }

    /** A redesigned skill can retain its aberration without recursively dispatching a fusion. */
    public static void runDeferredAberration(ServerLevel level, ServerPlayer player, FusedPair pair, Vec3 pos) {
        if (!pair.isAberrant() || Boolean.TRUE.equals(IN_EXECUTION.get())) return;
        IN_EXECUTION.set(Boolean.TRUE);
        try {
            AberrantRegistry.executeAberration(level, player, pair.aberrationId(), null, pos);
        } finally {
            IN_EXECUTION.set(Boolean.FALSE);
        }
    }

    public static void dispatch(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        TriggerType firedTrigger,
        @Nullable LivingEntity targetEntity,
        Vec3 originPos
    ) {
        dispatch(level, player, hostStack, EnumSet.of(firedTrigger), targetEntity, originPos, true, 1.0F);
    }

    public static void dispatch(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        TriggerType firedTrigger,
        @Nullable LivingEntity targetEntity,
        Vec3 originPos,
        boolean fullChargeMelee
    ) {
        dispatch(level, player, hostStack, EnumSet.of(firedTrigger), targetEntity, originPos, fullChargeMelee, 1.0F);
    }

    /**
     * Dispatches all fused pairs on {@code primaryHost} PLUS any distinct matching fused pairs equipped across
     * Offhand and Armor slots, enabling true cross-slot enchantment stacking ("万物可铭刻 · 异种叠加，同种不叠").
     */
    public static void dispatchAcrossEquippedSlots(
        ServerLevel level,
        ServerPlayer player,
        @Nullable ItemStack primaryHost,
        Set<TriggerType> firedTriggers,
        @Nullable LivingEntity targetEntity,
        Vec3 originPos,
        boolean fullChargeMelee,
        float chargeScale
    ) {
        dispatchAcrossEquippedSlots(
            level, player, primaryHost, firedTriggers, targetEntity, originPos, fullChargeMelee, chargeScale, false, 0
        );
    }

    /**
     * Dispatches all fused pairs on {@code primaryHost} PLUS any distinct matching fused pairs equipped across
     * Offhand and Armor slots. When {@code capBonusHurtToSingleProc} is true (projectile impacts, MIX-02),
     * {@code targetEntity.invulnerableTime} is restored immediately after the first bonus damage proc lands
     * so secondary armor/offhand slots cannot multiply unrestricted burst damage in a single arrow hit.
     */
    public static void dispatchAcrossEquippedSlots(
        ServerLevel level,
        ServerPlayer player,
        @Nullable ItemStack primaryHost,
        Set<TriggerType> firedTriggers,
        @Nullable LivingEntity targetEntity,
        Vec3 originPos,
        boolean fullChargeMelee,
        float chargeScale,
        boolean capBonusHurtToSingleProc,
        int restoreInvulnTicksOnFirstHurt
    ) {
        if (Boolean.TRUE.equals(IN_EXECUTION.get()) || firedTriggers == null || firedTriggers.isEmpty()) {
            return;
        }
        float hpBaseline = targetEntity != null ? targetEntity.getHealth() : 0.0F;
        Set<String> seenPairKeys = new HashSet<>();
        if (primaryHost != null && !primaryHost.isEmpty()) {
            dispatchInternal(
                level, player, primaryHost, firedTriggers, targetEntity, originPos, fullChargeMelee, chargeScale,
                seenPairKeys, true, capBonusHurtToSingleProc, hpBaseline, restoreInvulnTicksOnFirstHurt
            );
        }
        for (EquipmentSlot slot : SECONDARY_EQUIP_SLOTS) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (equipped.isEmpty() || equipped == primaryHost) {
                continue;
            }
            if (capBonusHurtToSingleProc && targetEntity != null && targetEntity.getHealth() < hpBaseline) {
                targetEntity.invulnerableTime = Math.max(targetEntity.invulnerableTime, restoreInvulnTicksOnFirstHurt);
            }
            dispatchInternal(
                level, player, equipped, firedTriggers, targetEntity, originPos, fullChargeMelee, chargeScale,
                seenPairKeys, false, capBonusHurtToSingleProc, hpBaseline, restoreInvulnTicksOnFirstHurt
            );
        }
    }

    public static void dispatch(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        Set<TriggerType> firedTriggers,
        @Nullable LivingEntity targetEntity,
        Vec3 originPos,
        boolean fullChargeMelee,
        float chargeScale
    ) {
        if (Boolean.TRUE.equals(IN_EXECUTION.get())
            || hostStack == null
            || hostStack.isEmpty()
            || firedTriggers == null
            || firedTriggers.isEmpty()) {
            return;
        }
        dispatchInternal(
            level, player, hostStack, firedTriggers, targetEntity, originPos, fullChargeMelee, chargeScale,
            new HashSet<>(), true, false, 0.0F, 0
        );
    }

    private static void dispatchInternal(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        Set<TriggerType> firedTriggers,
        @Nullable LivingEntity targetEntity,
        Vec3 originPos,
        boolean fullChargeMelee,
        float chargeScale,
        Set<String> seenPairKeys,
        boolean allowHostUniversalFallback,
        boolean capBonusHurtToSingleProc,
        float hpBaseline,
        int restoreInvulnTicksOnFirstHurt
    ) {
        FusedEnchantmentList list = hostStack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) {
            return;
        }

        HostForm hostForm = HostForm.resolve(hostStack);
        FusionMatrixRegistry.Snapshot snapshot = FusionMatrixRegistry.serverSnapshot();
        long now = level.getGameTime();
        var registries = level.registryAccess();
        boolean trajectoryEvent = firedTriggers.contains(TriggerType.ON_PROJECTILE_IMPACT)
            || firedTriggers.contains(TriggerType.ON_TRIDENT_CHANNEL);

        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sigOpt = SignaturePairs.resolve(pair);
            boolean waterBoots = sigOpt.isPresent() && FrostWakeDiveSkills.applies(pair, hostStack, sigOpt.get().id());
            if (waterBoots && player.getItemBySlot(EquipmentSlot.FEET) != hostStack) continue;
            boolean waterDirection = sigOpt.isPresent()
                && ((FrostWakeDiveSkills.isWakePair(pair) && FrostWakeDiveSkills.WAKE.equals(sigOpt.get().id()))
                    || (FrostWakeDiveSkills.isDivePair(pair) && FrostWakeDiveSkills.DIVE.equals(sigOpt.get().id())));
            if (waterDirection) {
                var preferred = FrostWakeDiveSkills.select(player, FrostWakeDiveSkills.isWakePair(pair));
                if (preferred != null && FrostWakeDiveSkills.applies(preferred.pair(), preferred.stack(), preferred.sigId())
                    && preferred.stack() != hostStack) continue;
            }
            String dedupKey = pair.primaryId() + ">" + pair.secondaryId();
            if (!seenPairKeys.add(dedupKey)) {
                continue;
            }

            Holder<Enchantment> pHolder = FusionStationOperations.lookupEnchantment(registries, pair.primaryId()).orElse(null);
            FusionMatrixRegistry.MatrixProfile primaryProfile = snapshot.resolve(pair.primaryId(), pHolder);

            TriggerType matchedTrigger = null;
            for (TriggerType ft : firedTriggers) {
                if (allowHostUniversalFallback
                    ? matchesUniversalHostTrigger(primaryProfile.trigger(), ft, hostForm)
                    : (primaryProfile.trigger() == ft)) {
                    matchedTrigger = ft;
                    break;
                }
            }
            if (matchedTrigger == null) {
                continue;
            }

            // These redesigned skills have dedicated triggers; generic dispatch would grant
            // unrelated bonuses and/or spend a cooldown before their real action happens.
            if (sigOpt.isPresent() && LoyaltyGuard.matches(pair, sigOpt.get().id())) continue;
            if (sigOpt.isPresent() && UnderwaterMiningSkills.applies(pair, hostStack, sigOpt.get().id())) continue;
            if (sigOpt.isPresent() && FrostFallSkills.applies(pair, hostStack, sigOpt.get().id())) continue;
            if (sigOpt.isPresent() && AmberSealSkill.applies(pair, hostStack, sigOpt.get().id())) continue;
            boolean redesignedPhantom = sigOpt.isPresent() && PhantomVolley.applies(pair, hostStack, sigOpt.get().id());
            boolean redesignedPrecision = sigOpt.isPresent() && PrecisionConstruction.applies(pair, hostStack, sigOpt.get().id());
            boolean redesignedDurability = sigOpt.isPresent() && DurabilityEconomy.applies(pair, hostStack, sigOpt.get().id());
            boolean redesignedBlastDefense = sigOpt.isPresent() && BlastDefenseSkills.applies(pair, hostStack, sigOpt.get().id());
            boolean redesignedHeavyCrush = sigOpt.isPresent() && HeavyCrushSkill.applies(pair, hostStack, sigOpt.get().id());
            boolean redesignedSoulStep = sigOpt.isPresent() && SoulStepSkills.applies(pair, hostStack, sigOpt.get().id());
            boolean dedicatedAction = redesignedPhantom || redesignedPrecision || redesignedDurability
                || redesignedBlastDefense || redesignedHeavyCrush || redesignedSoulStep;
            if (dedicatedAction) {
                // Dedicated hooks own the clean signature, but a pre-existing independent
                // aberration still uses its normal trigger, real target and shared cooldown.
                // Ghost arrows never reach this dispatcher (see ProjectileImpactFusionMixin).
                CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
                if (!pair.isAberrant()) continue;
            }
            if (sigOpt.isPresent() && GlazedStrikeEffect.applies(pair, hostStack, sigOpt.get().id())) {
                // The real Player.attack / spear-stab damage calls prepare and confirm this skill.
                // Pre-attack dispatch must not charge a cooldown or also execute the legacy path.
                continue;
            }
            boolean redesignedMelee = sigOpt.isPresent() && FlameKineticEffects.matches(pair, sigOpt.get().id());
            // This confirmed design requires an actual full-charge melee hit, including
            // on aberrant pairs. Other triggers must not silently spend its cooldown.
            if (redesignedMelee && (matchedTrigger != TriggerType.ON_MELEE_STRIKE || !fullChargeMelee
                || chargeScale < snapshot.economy().meleeFullCharge() || targetEntity == null)) {
                continue;
            }
            // Do not charge a clean signature for partial/no-target hits that cannot perform
            // any of its effects. Aberrant pairs can still have an independent mutation.
            if (!pair.isAberrant() && sigOpt.isPresent()
                && !SignaturePairs.isCombatTriggerEligible(
                    sigOpt.get().id(), fullChargeMelee, player.isSprinting(), targetEntity != null)) {
                continue;
            }
            // Synthetic flight impacts may run other effects but must never form chains of
            // self-generating water/phantom weapons. Preserve cooldown when a quota blocks a blade.
            if (sigOpt.isPresent() && SignaturePairs.launchesFlyingWeapon(sigOpt.get().id())) {
                if (FlyingBladeManager.isHandlingGeneratedFlight()) continue;
                if (("hydro_flying_sword".equals(sigOpt.get().id()) || "flying_sword_qi".equals(sigOpt.get().id()))
                    && !FlyingBladeManager.canLaunchFor(player.getUUID())) continue;
            }
            String cdKey = cooldownKey(player.getUUID(), pair.primaryId(), pair.secondaryId());
            long readyAt = COOLDOWNS.getOrDefault(cdKey, 0L);
            if (now < readyAt) {
                continue;
            }
            if (redesignedMelee) {
                // AttackEntityCallback precedes vanilla damage. Defer both the effect and
                // cooldown until an unblocked hit is confirmed by AFTER_DAMAGE.
                FlameKineticEffects.prepare(level, player, targetEntity, pair, sigOpt.get().id(), matchedTrigger);
                continue;
            }
            COOLDOWNS.put(cdKey, now + Math.max(6, snapshot.economy().cooldownTicksFor(matchedTrigger)));

            if (capBonusHurtToSingleProc && targetEntity != null && targetEntity.getHealth() < hpBaseline) {
                targetEntity.invulnerableTime = Math.max(targetEntity.invulnerableTime, restoreInvulnTicksOnFirstHurt);
            }

            // Scope IN_EXECUTION per individual pair so Pair #1's hurtServer never suppresses sibling Pair #2!
            IN_EXECUTION.set(Boolean.TRUE);
            try {
                if (pair.isAberrant()) {
                    AberrantRegistry.executeAberration(level, player, pair.aberrationId(), targetEntity, originPos);
                }
                // Do not also run the retired generic signature or matrix payload. The pair
                // retained its aberration; its dedicated ability is handled elsewhere.
                if (dedicatedAction) continue;

                if (sigOpt.isPresent()) {
                    boolean handled = SignaturePairs.executeSignatureCombat(
                        level, player, hostStack, pair, sigOpt.get().id(), targetEntity, originPos, fullChargeMelee
                    );
                    if (handled) {
                        continue;
                    }
                }

                Holder<Enchantment> sHolder = FusionStationOperations.lookupEnchantment(registries, pair.secondaryId()).orElse(null);
                PayloadType payload = snapshot.resolve(pair.secondaryId(), sHolder).payload();
                float catalystBoost = 1.0F + 0.14F * Math.max(1, pair.primaryLevel());
                float lawScale = (0.60F + 0.35F * Math.max(1, pair.secondaryLevel()))
                    * catalystBoost
                    * Math.clamp(chargeScale, 0.6F, 1.0F);
                float scale = Math.clamp(lawScale, 0.55F, 2.6F);
                executeDirectionalSynergy(
                    level, player, hostStack, hostForm, primaryProfile.trigger(), payload, scale, targetEntity, originPos,
                    trajectoryEvent, waterBoots && FrostWakeDiveSkills.isDivePair(pair)
                );
            } finally {
                IN_EXECUTION.set(Boolean.FALSE);
            }
        }
    }

    private static boolean matchesUniversalHostTrigger(
        TriggerType nativeTrigger,
        TriggerType firedTrigger,
        HostForm hostForm
    ) {
        if (nativeTrigger == firedTrigger) {
            return true;
        }
        return switch (hostForm) {
            case MELEE -> firedTrigger == TriggerType.ON_MELEE_STRIKE;
            case RANGED -> firedTrigger == TriggerType.ON_PROJECTILE_IMPACT || firedTrigger == TriggerType.ON_TRIDENT_CHANNEL;
            case TOOL -> firedTrigger == TriggerType.ON_BLOCK_HARVEST || firedTrigger == TriggerType.ON_MELEE_STRIKE;
            case ARMOR -> firedTrigger == TriggerType.ON_DAMAGE_TAKEN || firedTrigger == TriggerType.ON_MOVEMENT_OR_LAND;
        };
    }

    private static String cooldownKey(UUID uuid, String primaryId, String secondaryId) {
        return uuid + "|" + primaryId + ">" + secondaryId;
    }

    /**
     * Directional Catalyst x Law Synergy Engine:
     * {@code catalystTrigger} shapes the spatial geometry (aoe ring vs forward beam vs cached flight corridor),
     * while {@code payload} supplies the emergent arcane law, making {@code [A ▷ B]} and {@code [B ▷ A]} distinct.
     */
    private static void executeDirectionalSynergy(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        HostForm hostForm,
        TriggerType catalystTrigger,
        PayloadType payload,
        float scale,
        @Nullable LivingEntity target,
        Vec3 pos,
        boolean trajectoryEvent,
        boolean filterDiveCryoTargets
    ) {
        boolean utility = (hostForm == HostForm.TOOL || hostForm == HostForm.ARMOR);
        boolean wideCatalyst = (catalystTrigger == TriggerType.ON_SWEEP_OR_CLEAVE
            || catalystTrigger == TriggerType.ON_DAMAGE_TAKEN
            || catalystTrigger == TriggerType.ON_MOVEMENT_OR_LAND);
        double aoeRadius = wideCatalyst ? 4.4D : 3.2D;

        CachedFlightPath cachedPath = null;
        if (trajectoryEvent) {
            Optional<CachedFlightPath> latest = ProjectilePathTracker.findLatestPathForOwner(player.getUUID(), level.dimension());
            if (latest.isPresent() && (level.getGameTime() - latest.get().createdGameTime()) <= ProjectilePathTracker.ACTIVE_MAX_AGE_TICKS) {
                cachedPath = latest.get();
            }
        }

        switch (payload) {
            case PYRO -> {
                if (cachedPath != null) {
                    ProjectilePathTracker.applyPathTrailEffect(level, player, cachedPath, PathTrailStyle.PYRO_WAKE, scale * 0.75F);
                }
                if (target != null) {
                    boolean alreadyBurning = target.isOnFire();
                    target.igniteForSeconds(Math.max(3.0F, 3.2F * scale));
                    if (alreadyBurning) {
                        target.hurtServer(level, level.damageSources().magic(), 2.2F * scale);
                        for (LivingEntity adj : nearbyHostiles(level, player, pos, aoeRadius, target)) {
                            adj.igniteForSeconds(2.5F * scale);
                            adj.hurtServer(level, level.damageSources().magic(), 1.4F * scale);
                        }
                        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.6D, pos.z, 6, 0.3D, 0.25D, 0.3D, 0.04D);
                    }
                } else if (hostForm == HostForm.ARMOR) {
                    if (player.isOnFire()) player.clearFire();
                    grantEffect(player, "fire_resistance", Math.round(70 * scale), 0);
                    for (LivingEntity adj : nearbyHostiles(level, player, pos, aoeRadius, null)) {
                        adj.igniteForSeconds(3.0F);
                    }
                } else if (utility) {
                    if (player.isOnFire()) player.clearFire();
                    grantEffect(player, "haste", Math.round(65 * scale), 0);
                }
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5D, pos.z, 12, 0.35D, 0.25D, 0.35D, 0.04D);
            }
            case CRYO -> {
                if (cachedPath != null) {
                    ProjectilePathTracker.applyPathTrailEffect(level, player, cachedPath, PathTrailStyle.CRYO_WAKE, scale * 0.75F);
                }
                if (target != null) {
                    if (!filterDiveCryoTargets || (LoyaltyGuard.eligibleTarget(player, target)
                        && level.mayInteract(player, target.blockPosition()))) {
                        boolean wasChilled = target.getTicksFrozen() >= 60;
                        target.setTicksFrozen(Math.min(350, target.getTicksFrozen() + Math.round(105 * scale)));
                        grantEffect(target, "slowness", Math.round(65 * scale), 1);
                        if (wasChilled) {
                            target.hurtServer(level, level.damageSources().magic(), 2.6F * scale);
                            level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.65F);
                        }
                    }
                } else if (hostForm == HostForm.ARMOR) {
                    for (LivingEntity adj : nearbyHostiles(level, player, pos, aoeRadius, null)) {
                        if (filterDiveCryoTargets && (!LoyaltyGuard.eligibleTarget(player, adj)
                            || !level.mayInteract(player, adj.blockPosition()))) continue;
                        adj.setTicksFrozen(Math.min(300, adj.getTicksFrozen() + 95));
                        grantEffect(adj, "slowness", 60, 1);
                    }
                }
                TemporaryFrostEffects.placeSurfacePatch(level, player, BlockPos.containing(pos));
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.5D, pos.z, 14, 0.4D, 0.25D, 0.4D, 0.03D);
            }
            case VOLT -> {
                if (cachedPath != null) {
                    ProjectilePathTracker.applyPathTrailEffect(level, player, cachedPath, PathTrailStyle.VOLT_CHAIN, scale * 0.80F);
                }
                float arcDmg = (level.isThundering() || player.isInWaterOrRain()) ? 3.2F * scale : 2.1F * scale;
                if (target != null) {
                    target.hurtServer(level, level.damageSources().magic(), arcDmg);
                    grantEffect(target, "slowness", 30, 1);
                }
                int maxJumps = wideCatalyst ? 3 : 2;
                int jumped = 0;
                Vec3 prevArcPos = pos.add(0.0D, 0.7D, 0.0D);
                for (LivingEntity extra : nearbyHostiles(level, player, pos, aoeRadius + 1.2D, target)) {
                    if (++jumped > maxJumps) break;
                    extra.hurtServer(level, level.damageSources().magic(), arcDmg * 0.72F);
                    Vec3 nextArcPos = extra.position().add(0.0D, 0.7D, 0.0D);
                    spawnParticleBeam(level, ParticleTypes.ELECTRIC_SPARK, prevArcPos, nextArcPos, 5);
                    prevArcPos = nextArcPos;
                }
                if (utility && target == null) {
                    grantEffect(player, "haste", Math.round(75 * scale), 0);
                    grantEffect(player, "speed", Math.round(55 * scale), 0);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6D, pos.z, 14, 0.35D, 0.3D, 0.35D, 0.08D);
                level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.65F, 1.42F);
            }
            case GALE -> {
                if (cachedPath != null) {
                    ProjectilePathTracker.applyPathTrailEffect(level, player, cachedPath, PathTrailStyle.GALE_VACUUM_LINE, scale * 0.80F);
                }
                if (hostForm == HostForm.TOOL) {
                    pullNearbyDrops(level, player, pos, 6.0D);
                    grantEffect(player, "speed", 55, 0);
                } else {
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, aoeRadius + 0.8D, null)) {
                        Vec3 push = mob.position().subtract(player.position()).normalize().scale(0.62D * scale);
                        mob.push(push.x, 0.34D * Math.min(1.5F, scale), push.z);
                    }
                    if (!player.onGround() && player.fallDistance > 2.0F) {
                        player.fallDistance *= 0.35F;
                    }
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.4D, pos.z, 14, 0.45D, 0.2D, 0.45D, 0.06D);
            }
            case TIDE -> {
                boolean wet = player.isInWaterOrRain() || (target != null && target.isInWaterOrRain());
                if (target != null) {
                    target.hurtServer(level, level.damageSources().magic(), (wet ? 2.8F : 1.7F) * scale);
                    target.push(0.0D, 0.28D, 0.0D);
                }
                if (player.isOnFire()) player.clearFire();
                if (player.getAirSupply() < player.getMaxAirSupply()) {
                    player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 35));
                }
                grantEffect(player, player.isInWater() ? "dolphins_grace" : "speed", Math.round(65 * scale), 0);
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.4D, pos.z, 14, 0.35D, 0.25D, 0.35D, 0.07D);
            }
            case RETURN -> {
                if (cachedPath != null) {
                    ProjectilePathTracker.schedulePathEcho(cachedPath, 10L, 2.2F * scale, true, true);
                }
                pullNearbyDrops(level, player, pos, 7.0D);
                if (target != null) {
                    if (hostForm == HostForm.RANGED && level.getRandom().nextFloat() < 0.35F) {
                        player.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, 1));
                    } else if (player.distanceToSqr(target) > 9.0D) {
                        Vec3 tether = player.position().subtract(target.position()).normalize().scale(0.48D);
                        target.push(tether.x, 0.18D, tether.z);
                    }
                }
                level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 0.5D, pos.z, 10, 0.3D, 0.3D, 0.3D, 0.06D);
            }
            case PIERCE -> {
                if (cachedPath != null) {
                    ProjectilePathTracker.schedulePathEcho(cachedPath, 8L, 2.0F * scale, false, true);
                }
                if (target != null) {
                    if (target.isBlocking()) target.stopUsingItem();
                    float armorBonus = target.getArmorValue() * 0.14F;
                    target.hurtServer(level, level.damageSources().magic(), 2.1F * scale + armorBonus);
                    Vec3 dir = player.getLookAngle().normalize();
                    Vec3 lanceEnd = pos.add(dir.scale(3.6D));
                    spawnParticleBeam(level, ParticleTypes.CRIT, pos.add(0.0D, 0.8D, 0.0D), lanceEnd.add(0.0D, 0.8D, 0.0D), 6);
                    for (LivingEntity back : nearbyHostiles(level, player, pos.add(dir.scale(2.0D)), 2.2D, target)) {
                        back.hurtServer(level, level.damageSources().magic(), 1.9F * scale);
                    }
                } else if (hostForm == HostForm.ARMOR) {
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                        mob.hurtServer(level, level.damageSources().thorns(player), 1.8F * scale);
                    }
                } else if (utility) {
                    grantEffect(player, "haste", 70, 0);
                }
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.6D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.09D);
            }
            case SCATTER -> {
                if (cachedPath != null) {
                    ProjectilePathTracker.schedulePathEcho(cachedPath, 9L, 1.8F * scale, false, false);
                }
                int hitCount = 0;
                for (LivingEntity extra : nearbyHostiles(level, player, pos, aoeRadius + 0.8D, target)) {
                    if (++hitCount > 3) break;
                    extra.hurtServer(level, level.damageSources().playerAttack(player), 2.1F * scale);
                    spawnParticleBeam(
                        level,
                        ParticleTypes.ENCHANTED_HIT,
                        pos.add(0.0D, 0.7D, 0.0D),
                        extra.position().add(0.0D, 0.7D, 0.0D),
                        4
                    );
                }
                if (hitCount == 0 && target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 1.8F * scale);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.6D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            case GRAVITY -> {
                float slamDamage = Math.max(2.2F, (float) Math.min(12.0D, player.fallDistance * 0.85D + 2.3F * scale));
                for (LivingEntity mob : nearbyHostiles(level, player, pos, aoeRadius + 1.0D, null)) {
                    Vec3 pullIn = pos.subtract(mob.position()).scale(0.32D);
                    mob.push(pullIn.x, -0.18D, pullIn.z);
                    mob.hurtServer(level, level.damageSources().playerAttack(player), slamDamage);
                    grantEffect(mob, "slowness", 40, 1);
                }
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.15D, pos.z, 14, 0.65D, 0.12D, 0.65D, 0.04D);
                level.playSound(null, BlockPos.containing(pos), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.38F);
            }
            case EDGE -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().playerAttack(player), 1.9F * scale);
                    if (wideCatalyst) {
                        for (LivingEntity adj : nearbyHostiles(level, player, pos, 3.0D, target)) {
                            adj.hurtServer(level, level.damageSources().playerAttack(player), 1.4F * scale);
                        }
                    }
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.75D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                } else if (hostForm == HostForm.ARMOR) {
                    for (LivingEntity adj : nearbyHostiles(level, player, pos, 3.0D, null)) {
                        adj.hurtServer(level, level.damageSources().thorns(player), 1.7F * scale);
                    }
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.8D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                } else {
                    grantEffect(player, "haste", Math.round(70 * scale), 0);
                }
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 0.6D, pos.z, 10, 0.28D, 0.25D, 0.28D, 0.06D);
            }
            case BANE -> {
                if (target != null) {
                    var typeHolder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType());
                    boolean cursedSpecies = typeHolder.is(EntityTypeTags.UNDEAD)
                        || typeHolder.is(EntityTypeTags.ARTHROPOD)
                        || typeHolder.is(EntityTypeTags.ILLAGER);
                    float baneDmg = (cursedSpecies ? 3.2F : 2.0F) * scale;
                    target.hurtServer(level, level.damageSources().magic(), baneDmg);
                    grantEffect(target, "weakness", Math.round(70 * scale), 0);
                    grantEffect(target, "slowness", Math.round(55 * scale), 1);
                } else if (utility) {
                    grantEffect(player, "resistance", 65, 0);
                }
                level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.5D, pos.z, 10, 0.3D, 0.25D, 0.3D, 0.03D);
            }
            case FORTUNE -> {
                if (target != null && level.getRandom().nextFloat() < 0.32F) {
                    target.hurtServer(level, level.damageSources().magic(), 2.4F * scale);
                    level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.70F, 1.58F);
                }
                if (level.getRandom().nextFloat() < Math.min(0.65F, 0.35F * scale)) {
                    ExperienceOrb.award(level, pos, Math.max(1, Math.round(2.5F * scale)));
                }
                if (hostForm == HostForm.TOOL && level.getRandom().nextFloat() < Math.min(0.22F, 0.09F * scale)) {
                    Item bonusItem = switch (level.getRandom().nextInt(3)) {
                        case 0 -> Items.AMETHYST_SHARD;
                        case 1 -> Items.LAPIS_LAZULI;
                        default -> Items.GOLD_NUGGET;
                    };
                    level.addFreshEntity(new ItemEntity(level, pos.x, pos.y + 0.3D, pos.z, new ItemStack(bonusItem, 1)));
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.5D, pos.z, 6, 0.3D, 0.25D, 0.3D, 0.03D);
            }
            case AEGIS -> {
                grantEffect(player, "absorption", Math.round(90 * scale), 0);
                grantEffect(player, "resistance", Math.round(55 * scale), 0);
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 2.8D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.42D);
                    mob.push(away.x, 0.16D, away.z);
                }
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.8D, player.getZ(), 8, 0.3D, 0.35D, 0.3D, 0.03D);
            }
            case MEND -> {
                int repair = Math.max(1, Math.round(2.0F * scale));
                if (!repairHostOrArmor(player, hostStack, repair)) {
                    player.heal(Math.min(2.5F, 0.9F * scale));
                    level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.15D, player.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.02D);
                }
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 0.9D, player.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.05D);
            }
        }
    }

    private static boolean repairHostOrArmor(ServerPlayer player, ItemStack hostStack, int amount) {
        if (hostStack.isDamaged()) {
            hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - amount));
            return true;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack piece = player.getItemBySlot(slot);
            if (!piece.isEmpty() && piece.isDamaged()) {
                piece.setDamageValue(Math.max(0, piece.getDamageValue() - amount));
                return true;
            }
        }
        return false;
    }

    private static List<LivingEntity> nearbyHostiles(
        ServerLevel level,
        ServerPlayer player,
        Vec3 center,
        double radius,
        @Nullable LivingEntity exclude
    ) {
        AABB box = AABB.ofSize(center, radius * 2.0D, 3.2D, radius * 2.0D);
        return level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            e -> e != player && e != exclude && e.isAlive() && !e.isAlliedTo(player)
        );
    }

    private static void spawnParticleBeam(ServerLevel level, ParticleOptions type, Vec3 from, Vec3 to, int steps) {
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            Vec3 p = from.lerp(to, t);
            level.sendParticles(type, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    private static void pullNearbyDrops(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
        AABB box = AABB.ofSize(center, radius * 2.0D, radius, radius * 2.0D);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
            itemEntity.setNoPickUpDelay();
            itemEntity.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
        }
    }

    private static void grantEffect(LivingEntity entity, String vanillaEffectPath, int durationTicks, int amplifier) {
        BuiltInRegistries.MOB_EFFECT
            .get(Identifier.withDefaultNamespace(vanillaEffectPath))
            .ifPresent(holder -> entity.addEffect(new MobEffectInstance(holder, Math.max(10, durationTicks), amplifier, true, true)));
    }
}
