package enchantment_fusion.mechanics.signatures;

import static enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.*;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.signatures.SignatureCatalog.EquippedSignature;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Armor, Boots/Leggings/Helmet Mobility, Pre-Damage &amp; Lethal Saves, Counter-Projectiles,
 * and Wildcard Curse Signature Resonances.
 */
public final class ArmorCurseSignatures {
    private ArmorCurseSignatures() {}

    public static boolean allowIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        if (!(player.level() instanceof ServerLevel level) || amount <= 0.0F) {
            return true;
        }
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        long now = level.getGameTime();
        Map<String, EquippedSignature> unique = SignatureCatalog.collectUniqueEquippedSignatures(player);

        EquippedSignature lucky = unique.get("lucky_golden_body");
        if (lucky != null && now >= LUCKY_GOLDEN_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
            float procChance = 0.10F + 0.05F * lucky.pair().primaryLevel();
            if (level.getRandom().nextFloat() < procChance) {
                LUCKY_GOLDEN_COOLDOWN.put(player.getUUID(), now + 100L);
                if (lucky.stack().isDamageableItem()) {
                    int extraDurabilityLoss = Math.max(2, Math.round(amount * 2.0F));
                    lucky.stack().hurtAndBreak(extraDurabilityLoss, player, lucky.slot());
                }
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 14, 0.35D, 0.4D, 0.35D, 0.08D);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.4F);
                return false;
            }
        }

        EquippedSignature glass = unique.get("glass_holy_shield");
        if (glass != null) {
            long readyAt = GLASS_SHIELD_COOLDOWN.getOrDefault(player.getUUID(), 0L);
            if (player.getHealth() >= player.getMaxHealth() - 0.1F && now >= readyAt) {
                GLASS_SHIELD_COOLDOWN.put(player.getUUID(), now + 300L);
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 18, 0.4D, 0.5D, 0.4D, 0.06D);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.95F, 1.55F);
                return false;
            }
        }
        return true;
    }

    public static boolean allowPlayerDeath(ServerPlayer player, DamageSource source) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        boolean isVoidFall = source != null && source.is(DamageTypes.FELL_OUT_OF_WORLD);
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isVoidFall) {
            return true;
        }
        long now = level.getGameTime();
        Map<String, EquippedSignature> unique = SignatureCatalog.collectUniqueEquippedSignatures(player);

        if (!isVoidFall) {
            EquippedSignature grace = unique.get("fortune_grace");
            if (grace != null) {
                long readyAt = FORTUNE_GRACE_COOLDOWN.getOrDefault(player.getUUID(), 0L);
                float saveChance = 0.18F + 0.08F * grace.pair().secondaryLevel();
                if (now >= readyAt && level.getRandom().nextFloat() < saveChance) {
                    FORTUNE_GRACE_COOLDOWN.put(player.getUUID(), now + 1200L);
                    player.setHealth(1.0F);
                    grantEffect(player, "absorption", 100, 1);
                    level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.4D, 0.5D, 0.4D, 0.12D);
                    return false;
                }
            }
        }

        EquippedSignature loyal = unique.get("loyal_lifeline");
        if (loyal != null && (isVoidFall || player.getY() < level.getMinY() + 2)) {
            long readyAt = LOYAL_LIFELINE_COOLDOWN.getOrDefault(player.getUUID(), 0L);
            SafeGround anchor = LAST_SAFE_GROUND.get(player.getUUID());
            if (now >= readyAt && isSafeLifelineAnchor(level, player, anchor)) {
                Vec3 safe = anchor.position();
                LOYAL_LIFELINE_COOLDOWN.put(player.getUUID(), now + 600L);
                player.setHealth(Math.max(2.0F, player.getHealth()));
                player.fallDistance = 0.0F;
                player.teleportTo(safe.x, safe.y + 0.5D, safe.z);
                if (loyal.stack().isDamageableItem()) {
                    loyal.stack().hurtAndBreak(20, player, loyal.slot());
                }
                level.sendParticles(ParticleTypes.PORTAL, safe.x, safe.y + 1.0D, safe.z, 30, 0.4D, 0.6D, 0.4D, 0.15D);
                return false;
            }
        }
        return true;
    }

    public static boolean executeCombat(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        FusedPair pair,
        String sigId,
        @Nullable LivingEntity target,
        Vec3 pos
    ) {
        RandomSource rng = level.getRandom();
        int pLv = Math.max(1, pair.primaryLevel());
        int sLv = Math.max(1, pair.secondaryLevel());

        switch (sigId) {
            case "soulburn_elegy_curse" -> {
                if (target != null) {
                    SOUL_FIRE_TICKS.put(target.getUUID(), 80);
                    target.hurtServer(level, level.damageSources().magic(), 2.5F + 0.6F * pLv);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.8D, pos.z, 12, 0.25D, 0.25D, 0.25D, 0.04D);
                }
                return true;
            }
            case "blood_pact_curse" -> {
                player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 4.0F));
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.2F + 0.5F * sLv);
                    Vec3 drain = player.position().subtract(mob.position()).normalize().scale(0.35D);
                    mob.push(drain.x, 0.15D, drain.z);
                }
                player.heal(1.2F);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.9D, player.getZ(), 14, 0.4D, 0.3D, 0.4D, 0.04D);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.75F);
                return true;
            }
            case "spirit_grapnel" -> {
                if (target != null) {
                    Vec3 pull = player.position().subtract(target.position()).normalize().scale(0.85D + 0.15D * sLv);
                    target.push(pull.x, 0.25D, pull.z);
                    level.sendParticles(ParticleTypes.FISHING, pos.x, pos.y + 0.6D, pos.z, 10, 0.2D, 0.2D, 0.2D, 0.05D);
                }
                return true;
            }
            case "searing_counter", "pyro_thorns" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.6D, null)) {
                    mob.igniteForSeconds(4.0F);
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.55D);
                    mob.push(away.x, 0.2D, away.z);
                }
                return true;
            }
            case "cinder_backlash" -> {
                player.clearFire();
                grantEffect(player, "fire_resistance", 80, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.6D, null)) {
                    mob.igniteForSeconds(3.0F + pLv);
                    mob.hurtServer(level, level.damageSources().thorns(player), 2.0F + 0.4F * sLv);
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.48D);
                    mob.push(away.x, 0.22D, away.z);
                }
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8D, player.getZ(), 16, 0.45D, 0.3D, 0.45D, 0.05D);
                return true;
            }
            case "blast_thorns", "kinetic_payback", "reactive_plate" -> {
                if ("reactive_plate".equals(sigId)) {
                    player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 3.0F));
                } else {
                    grantEffect(player, "strength", 80, 0);
                }
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.6D, null)) {
                    mob.hurtServer(level, level.damageSources().thorns(player), 2.4F + 0.4F * sLv);
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.78D);
                    mob.push(away.x, 0.3D, away.z);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.8D, player.getZ(), 2, 0.25D, 0.2D, 0.25D, 0.02D);
                return true;
            }
            case "concussion_shrapnel" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.2D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.2F + 0.45F * pLv);
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.55D);
                    mob.push(away.x, 0.24D, away.z);
                    grantEffect(mob, "weakness", 70, 0);
                }
                level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.9D, player.getZ(), 14, 0.45D, 0.3D, 0.45D, 0.12D);
                return true;
            }
            case "deflection_field", "sniper_thorns", "ricochet_ward" -> {
                if ("ricochet_ward".equals(sigId)) {
                    grantEffect(player, "resistance", 60, 0);
                }
                if (target != null && target != player) {
                    Vec3 aim = target.getEyePosition().subtract(player.getEyePosition()).normalize();
                    spawnCounterSpectralBolt(level, player, aim, 2.2D + 0.45D * pLv);
                } else {
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 8.0D, null)) {
                        Vec3 aim = mob.getEyePosition().subtract(player.getEyePosition()).normalize();
                        spawnCounterSpectralBolt(level, player, aim, 2.0D + 0.4D * pLv);
                        break;
                    }
                }
                return true;
            }
            case "porcelain_bastion" -> {
                long now = level.getGameTime();
                if (player.getHealth() >= player.getMaxHealth() * 0.5F
                    && now >= PORCELAIN_BASTION_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
                    PORCELAIN_BASTION_COOLDOWN.put(player.getUUID(), now + 240L);
                    player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 4.0F));
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.04D);
                }
                return true;
            }
            case "arrow_stride" -> {
                grantEffect(player, "speed", 90, 1);
                Vec3 lateral = new Vec3(-player.getLookAngle().z, 0.16D, player.getLookAngle().x).normalize().scale(0.55D);
                player.push(lateral.x, 0.14D, lateral.z);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3D, player.getZ(), 10, 0.3D, 0.15D, 0.3D, 0.05D);
                return true;
            }
            case "shakedown_thorns" -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().thorns(player), 1.8F + 0.45F * pLv);
                    if (rng.nextFloat() < 0.32F) {
                        spawnItem(level, player.position(), new ItemStack(rng.nextBoolean() ? Items.GOLD_NUGGET : Items.EMERALD, 1));
                        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.55F);
                    }
                }
                return true;
            }
            case "extortion_carapace" -> {
                if (target != null && target != player) {
                    target.hurtServer(level, level.damageSources().thorns(player), 1.8F + 0.5F * pLv);
                    Vec3 shove = target.position().subtract(player.position()).normalize().scale(0.45D);
                    target.push(shove.x, 0.16D, shove.z);
                    if (rng.nextFloat() < 0.28F) {
                        ExperienceOrb.award(level, player.position(), 1 + sLv);
                    }
                }
                return true;
            }
            case "frost_spike_armor" -> {
                if (target != null) {
                    target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + 150));
                    Vec3 spike = target.position().subtract(player.position()).normalize().scale(0.42D);
                    target.push(spike.x, 0.22D, spike.z);
                }
                return true;
            }
            case "gale_guardian_armor", "zephyr_cushion" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.85D);
                    mob.push(away.x, 0.42D, away.z);
                }
                player.fallDistance = 0.0F;
                player.push(0.0D, "zephyr_cushion".equals(sigId) ? 0.35D : 0.45D, 0.0D);
                player.hurtMarked = true;
                if ("zephyr_cushion".equals(sigId)) grantEffect(player, "slow_falling", 55, 0);
                return true;
            }
            case "hydrothermal_wake" -> {
                if (target != null) {
                    target.igniteForSeconds(3.0F + sLv);
                    float bonus = (player.isInWaterOrRain() || target.isInWaterOrRain()) ? (3.2F + 0.6F * pLv) : 1.8F;
                    target.hurtServer(level, level.damageSources().magic(), bonus);
                }
                if (player.isInWaterOrRain()) {
                    Vec3 jet = player.getLookAngle().normalize().scale(0.45D);
                    player.push(jet.x, 0.08D, jet.z);
                    player.hurtMarked = true;
                }
                return true;
            }
            case "self_healing_lattice" -> {
                if (target != null && hostStack.isDamageableItem()) {
                    float missingRatio = (float) hostStack.getDamageValue() / (float) Math.max(1, hostStack.getMaxDamage());
                    target.hurtServer(level, level.damageSources().magic(), 1.8F + 4.2F * missingRatio + 0.4F * pLv);
                }
                if (hostStack.isDamaged() && rng.nextFloat() < 0.50F) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - (1 + sLv / 2)));
                }
                return true;
            }
            case "frost_meteor_fall" -> {
                if (player.mayBuild()) AberrantRegistry.placeFrostedIceSnare(level, player.blockPosition());
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    mob.setTicksFrozen(Math.min(320, mob.getTicksFrozen() + 130));
                    mob.hurtServer(level, level.damageSources().freeze(), 3.0F + 0.7F * pLv);
                }
                return true;
            }
            case "feather_rime_glide" -> {
                player.fallDistance = 0.0F;
                grantEffect(player, "slow_falling", 60, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                    mob.setTicksFrozen(Math.min(240, mob.getTicksFrozen() + 90));
                }
                return true;
            }
            case "soul_stride_landing" -> {
                grantEffect(player, "speed", 110, 1);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F + 0.6F * sLv);
                    SOUL_FIRE_TICKS.put(mob.getUUID(), 60);
                }
                return true;
            }
            case "homing_plume" -> {
                player.fallDistance = 0.0F;
                grantEffect(player, "slow_falling", 70, 0);
                AABB box = AABB.ofSize(player.position(), 8.0D, 5.0D, 8.0D);
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
                    item.setNoPickUpDelay();
                    item.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                }
                return true;
            }
            case "bitter_medicine_carapace" -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().thorns(player), 2.2F + 0.7F * pLv);
                }
                for (EquipmentSlot eqSlot : SignatureCatalog.ALL_EQUIP_SLOTS) {
                    ItemStack eq = player.getItemBySlot(eqSlot);
                    if (!eq.isEmpty() && eq.isDamaged()) {
                        eq.setDamageValue(Math.max(0, eq.getDamageValue() - (1 + pLv / 2)));
                    }
                }
                player.heal(1.0F);
                return true;
            }
            case "forged_retaliation" -> {
                int stored = FORGED_QUILL_STACKS.getOrDefault(player.getUUID(), 0);
                int release = Math.max(1, stored + 1);
                FORGED_QUILL_STACKS.put(player.getUUID(), 0);
                float volleyDmg = 1.6F + release * (0.85F + 0.20F * sLv);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.4D, null)) {
                    mob.hurtServer(level, level.damageSources().thorns(player), volleyDmg);
                }
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.8D, pos.z, 8 + release * 3, 0.45D, 0.3D, 0.45D, 0.14D);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean isSafeLifelineAnchor(ServerLevel level, ServerPlayer player, @Nullable SafeGround anchor) {
        if (anchor == null || !level.dimension().equals(anchor.dimension())) return false;
        Vec3 safe = anchor.position();
        BlockPos feet = BlockPos.containing(safe);
        BlockPos below = BlockPos.containing(safe.x, safe.y - 0.05D, safe.z);
        BlockPos arrival = BlockPos.containing(safe.add(0.0D, 0.5D, 0.0D));
        if (!level.isInWorldBounds(arrival) || !level.isLoaded(feet) || !level.isLoaded(below)
            || !level.getWorldBorder().isWithinBounds(arrival) || !level.getFluidState(feet).isEmpty()) return false;
        BlockState support = level.getBlockState(below);
        if (support.is(FusionBlocks.TEMPORARY_FROST) || support.is(FusionBlocks.TEMPORARY_FROST_PATH)
            || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CACTUS)) return false;
        Vec3 offset = safe.subtract(player.position());
        AABB standing = player.getBoundingBox().move(offset);
        // The old floor may have been mined away, or a wall placed at the arrival point.
        return !level.noCollision(player, standing.move(0.0D, -0.10D, 0.0D))
            && level.noCollision(player, standing.move(0.0D, 0.5D, 0.0D));
    }

    public static void tickServerPlayer(ServerLevel level, ServerPlayer player) {
        if (player.onGround() && player.getY() > level.getMinY() + 2) {
            LAST_SAFE_GROUND.put(player.getUUID(), new SafeGround(level.dimension(), player.position()));
        }
        if (level.getGameTime() % 10L != 0L) {
            return;
        }

        Map<String, EquippedSignature> signatures = new LinkedHashMap<>(SignatureCatalog.collectUniqueEquippedSignatures(player));
        EquippedSignature wake = FrostWakeDiveSkills.select(player, true);
        EquippedSignature dive = FrostWakeDiveSkills.select(player, false);
        if (wake == null) signatures.remove(FrostWakeDiveSkills.WAKE); else signatures.put(FrostWakeDiveSkills.WAKE, wake);
        if (dive == null) signatures.remove(FrostWakeDiveSkills.DIVE); else signatures.put(FrostWakeDiveSkills.DIVE, dive);
        for (EquippedSignature eq : signatures.values()) {
            String sigId = eq.sigId();
            EquipmentSlot slot = eq.slot();
            boolean selectedWaterBoots = FrostWakeDiveSkills.applies(eq.pair(), eq.stack(), sigId);
            if (selectedWaterBoots && slot != EquipmentSlot.FEET) continue;

            switch (sigId) {
                case "loyal_lifeline" -> {
                    if (eq.stack().isDamaged() && level.getGameTime() % 60L == 0L) {
                        eq.stack().setDamageValue(Math.max(0, eq.stack().getDamageValue() - 1));
                    }
                }
                case "furnace_aegis", "true_gold_refinery" -> {
                    if (player.isOnFire()) {
                        player.clearFire();
                        grantEffect(player, "fire_resistance", 60, 0);
                    }
                }
                case "forged_retaliation" -> {
                    if (level.getGameTime() % 40L == 0L) {
                        FORGED_QUILL_STACKS.merge(player.getUUID(), 1, (a, b) -> Math.min(8, a + b));
                    }
                }
                case "glacial_hearth" -> {
                    player.setTicksFrozen(0);
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.2D, null)) {
                        mob.setTicksFrozen(Math.min(200, mob.getTicksFrozen() + 25));
                    }
                }
                case "tide_breaker_dive", "permafrost_wake" -> FrostWakeDiveSkills.tick(level, player, eq, selectedWaterBoots);
                case "magma_basalt_step" -> {
                    if (player.mayBuild() && !player.isShiftKeyDown()) {
                        BlockPos feet = player.blockPosition();
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos lavaPos = feet.offset(dx, -1, dz);
                                BlockState st = level.getBlockState(lavaPos);
                                if (st.is(Blocks.LAVA) && st.getFluidState().isSource() && level.getBlockState(lavaPos.above()).isAir()) {
                                    level.setBlockAndUpdate(lavaPos, Blocks.BASALT.defaultBlockState());
                                }
                            }
                        }
                    }
                }
                case "ice_thorn_trail" -> {
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.5D, null)) {
                        BlockState under = level.getBlockState(mob.blockPosition().below());
                        if (under.is(BlockTags.ICE)) {
                            mob.hurtServer(level, level.damageSources().cactus(), 1.6F);
                            mob.setTicksFrozen(Math.min(220, mob.getTicksFrozen() + 45));
                            mob.push(0.0D, 0.22D, 0.0D);
                        }
                    }
                }
                case "phantom_assassin_step" -> {
                    if (player.isShiftKeyDown()) {
                        grantEffect(player, "speed", 30, 1);
                        grantEffect(player, "invisibility", 30, 0);
                    }
                }
                case "abyssal_sight" -> {
                    if (player.isInWater()) {
                        // Night vision does not duplicate the chosen ability. Free water breathing
                        // would erase its oxygen cost; retain that legacy effect only on old,
                        // unsupported carriers instead of changing their behavior or item data.
                        grantEffect(player, "night_vision", 240, 0);
                        if (!UnderwaterMiningSkills.applies(eq.pair(), eq.stack(), sigId)) {
                            grantEffect(player, "water_breathing", 30, 0);
                        }
                    }
                }
                case "rapid_bowstring", "swift_draw_burst" -> {
                    if (slot == EquipmentSlot.MAINHAND) {
                        grantEffect(player, "haste", 30, 1);
                    }
                }
                case "blood_pact_curse" -> {
                    if (player.getAbsorptionAmount() < 2.0F && level.getGameTime() % 60L == 0L) {
                        player.setAbsorptionAmount(2.0F);
                    }
                }
                default -> {}
            }
        }
    }
}
