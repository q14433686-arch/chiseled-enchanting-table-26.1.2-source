package enchantment_fusion.mechanics.signatures;

import static enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.*;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.ProjectilePathTracker;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
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
 * Mining, Kiln/Smelt-Fortune, Prospecting, Utility &amp; Fishing Rod Signature Resonances.
 */
public final class MiningFishingSignatures {
    private MiningFishingSignatures() {}

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
            case "perpetual_craft" -> {
                if (hostStack.isDamaged() && rng.nextFloat() < 0.75F) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 1));
                }
                int stacks = PERSISTENCE_STACKS.getOrDefault(player.getUUID(), 0) + 1;
                grantEffect(player, "haste", 70, Math.min(2, stacks / 2));
                if (stacks >= 4) {
                    PERSISTENCE_STACKS.put(player.getUUID(), 0);
                    Vec3 start = player.getEyePosition();
                    Vec3 end = start.add(player.getLookAngle().normalize().scale(5.2D));
                    for (LivingEntity mob : ProjectilePathTracker.querySweptSegmentHostiles(level, player, start, end, 1.25D)) {
                        hurtBypassingIframe(level, mob, level.damageSources().magic(), 3.6F + 0.55F * sLv);
                    }
                    level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.8D, pos.z, 14, 0.3D, 0.2D, 0.3D, 0.08D);
                } else {
                    PERSISTENCE_STACKS.put(player.getUUID(), stacks);
                }
                return true;
            }
            case "velvet_shear" -> {
                if (target != null) {
                    float seamTrueDmg = 2.0F + target.getArmorValue() * 0.20F + 0.35F * pLv;
                    target.hurtServer(level, level.damageSources().magic(), seamTrueDmg);
                    if (hostStack.isDamaged()) hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 1));
                }
                return true;
            }
            case "controlled_demolition" -> {
                grantEffect(player, "resistance", 60, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.65D);
                    mob.push(away.x, 0.25D, away.z);
                }
                return true;
            }
            case "glazed_kiln_edge" -> {
                if (target != null) {
                    boolean wasBurning = target.isOnFire();
                    target.igniteForSeconds(5.0F + pLv);
                    if (wasBurning) {
                        float shatter = 3.2F + target.getArmorValue() * 0.24F + 0.6F * pLv;
                        target.hurtServer(level, level.damageSources().magic(), shatter);
                        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.7F);
                    }
                }
                return true;
            }
            case "true_gold_refinery" -> {
                if (player.isOnFire()) grantEffect(player, "fire_resistance", 100, 0);
                if (target != null) {
                    boolean tempered = player.isOnFire() || target.isOnFire();
                    target.igniteForSeconds(4.0F + sLv);
                    target.hurtServer(level, level.damageSources().magic(), (tempered ? 3.8F : 2.0F) + 0.6F * pLv);
                }
                return true;
            }
            case "cinder_jackpot" -> {
                if (target != null) {
                    boolean riskyFire = target.isOnFire() || player.isOnFire();
                    target.igniteForSeconds(5.0F);
                    target.hurtServer(level, level.damageSources().onFire(), 2.2F + 0.55F * pLv);
                    if (riskyFire && rng.nextFloat() < 0.38F + 0.07F * sLv) {
                        Item chestLoot = switch (rng.nextInt(4)) {
                            case 0 -> Items.EMERALD;
                            case 1 -> Items.BLAZE_POWDER;
                            case 2 -> Items.GOLD_INGOT;
                            default -> Items.GOLD_NUGGET;
                        };
                        spawnItem(level, pos, new ItemStack(chestLoot, 1));
                        ExperienceOrb.award(level, pos, 2 + sLv);
                    }
                }
                return true;
            }
            case "geyser_bore" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.4D, null)) {
                    mob.igniteForSeconds(3.0F + pLv);
                    mob.hurtServer(level, level.damageSources().magic(), 2.4F + 0.55F * pLv);
                    mob.push(0.0D, 0.42D, 0.0D);
                }
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6D, pos.z, 16, 0.35D, 0.3D, 0.35D, 0.08D);
                return true;
            }
            case "raging_hook" -> {
                if (target != null) {
                    Vec3 pull = player.position().subtract(target.position()).normalize().scale(0.95D + 0.15D * pLv);
                    target.push(pull.x, 0.32D, pull.z);
                    target.hurtMarked = true;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 3.2F + 0.8F * pLv);
                    grantEffect(target, "slowness", 60, 1);
                    if (rng.nextFloat() < 0.22F + 0.06F * sLv) {
                        Item catchDrop = rng.nextFloat() < 0.20F ? Items.NAUTILUS_SHELL : (rng.nextBoolean() ? Items.COD : Items.SALMON);
                        spawnItem(level, player.position(), new ItemStack(catchDrop, 1));
                    }
                }
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6D, pos.z, 16, 0.35D, 0.35D, 0.35D, 0.08D);
                return true;
            }
            case "tidal_double_catch" -> {
                ExperienceOrb.award(level, player.position(), 2 + sLv);
                if (rng.nextFloat() < 0.35F + 0.10F * pLv) {
                    Item bonusCatch = switch (rng.nextInt(5)) {
                        case 0 -> Items.PRISMARINE_CRYSTALS;
                        case 1 -> Items.LAPIS_LAZULI;
                        case 2 -> Items.SALMON;
                        case 3 -> Items.TROPICAL_FISH;
                        default -> Items.NAUTILUS_SHELL;
                    };
                    spawnItem(level, player.position(), new ItemStack(bonusCatch, 1));
                    level.sendParticles(ParticleTypes.FISHING, player.getX(), player.getY() + 0.9D, player.getZ(), 12, 0.3D, 0.2D, 0.3D, 0.05D);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void onBlockHarvested(
        ServerLevel level,
        ServerPlayer player,
        ItemStack tool,
        BlockPos pos,
        BlockState minedState
    ) {
        FusedEnchantmentList list = tool.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) return;
        RandomSource rng = level.getRandom();

        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sigOpt = SignatureCatalog.resolve(pair);
            if (sigOpt.isEmpty()) continue;
            String sigId = sigOpt.get().id();

            switch (sigId) {
                case "controlled_demolition" -> {
                    grantEffect(player, "resistance", 60, 0);
                    level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.25D, 0.25D, 0.25D, 0.03D);
                }
                case "velvet_shear" -> grantEffect(player, "haste", 80, 1);
                case "pulse_prospect" -> {
                    grantEffect(player, "haste", 100, 1);
                    for (int dx = -4; dx <= 4; dx++) {
                        for (int dy = -3; dy <= 3; dy++) {
                            for (int dz = -4; dz <= 4; dz++) {
                                BlockPos scan = pos.offset(dx, dy, dz);
                                if (bonusRawForOre(level.getBlockState(scan).getBlock()) != null) {
                                    level.sendParticles(ParticleTypes.END_ROD, scan.getX() + 0.5D, scan.getY() + 0.5D, scan.getZ() + 0.5D, 2, 0.1D, 0.1D, 0.1D, 0.0D);
                                }
                            }
                        }
                    }
                }
                case "geyser_bore" -> {
                    grantEffect(player, "haste", 80, 1);
                    for (LivingEntity mob : nearbyHostiles(level, player, Vec3.atCenterOf(pos), 3.0D, null)) {
                        mob.hurtServer(level, level.damageSources().onFire(), 2.0F);
                    }
                }
                case "self_healing_lattice" -> {
                    if (tool.isDamaged() && rng.nextFloat() < 0.45F) tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
                }
                case "perpetual_craft", "eternal_bowstring", "overflow_plating", "tempered_anvil_heart" -> {
                    if (tool.isDamaged() && rng.nextFloat() < 0.75F) tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
                }
                case "ocean_breath_mine" -> player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 45));
                case "rock_splitter_pick", "triple_excavate", "vein_peel" -> {
                    if (player.mayBuild() && !player.isShiftKeyDown() && !ExtraBlockHarvest.isActive()) {
                        ExtraBlockHarvest.defer(() -> {
                            Direction side = player.getDirection().getClockWise();
                            for (int offset : new int[]{-1, 1}) {
                                if (!ExtraBlockHarvest.hasOriginalTool(player, tool)) break;
                                BlockPos adj = pos.relative(side, offset);
                                ExtraBlockHarvest.tryBreak(level, player, tool, adj, st -> st.is(minedState.getBlock()));
                            }
                        });
                    }
                }
                default -> {}
            }
        }
    }

    public static void onPostDestroyHarvestDrops(
        ServerLevel level,
        ServerPlayer player,
        ItemStack tool,
        BlockPos pos,
        BlockState minedState
    ) {
        if (tool == null || tool.isEmpty() || player.isCreative()) return;
        FusedEnchantmentList list = tool.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) return;
        CompendiumDiscoveryTracker.scanPlayerInventory(player);
        RandomSource rng = level.getRandom();
        boolean pullDropsToPlayer = false;

        for (FusedPair pair : list.pairs()) {
            Optional<SignatureEntry> sigOpt = SignatureCatalog.resolve(pair);
            if (sigOpt.isEmpty()) continue;
            String sigId = sigOpt.get().id();

            switch (sigId) {
                case "raw_ore_geode" -> {
                    Item bonusOre = bonusRawForOre(minedState.getBlock());
                    if (bonusOre != null && rng.nextFloat() < 0.55F) {
                        spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(bonusOre, 1 + rng.nextInt(Math.max(1, pair.secondaryLevel()))));
                    }
                }
                case "smelt_fortune", "molten_core_pick", "true_gold_refinery", "glazed_kiln_edge", "geyser_bore" -> {
                    if (!player.isShiftKeyDown()) {
                        float xpMult = "smelt_fortune".equals(sigId) ? 1.5F : 1.0F;
                        if (CrossDomainTransmutations.smeltFreshBlockDropsInPlace(level, pos, minedState, xpMult)) {
                            if ("smelt_fortune".equals(sigId)) {
                                Item ingot = smeltedResultForBlock(minedState.getBlock());
                                if (ingot != null && rng.nextFloat() < 0.35F + 0.12F * pair.primaryLevel()) {
                                    spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(ingot, 1));
                                }
                            } else if ("true_gold_refinery".equals(sigId) && rng.nextFloat() < 0.45F) {
                                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(Items.GOLD_NUGGET, 1 + rng.nextInt(3)));
                            } else if ("glazed_kiln_edge".equals(sigId) && (minedState.is(Blocks.CLAY) || minedState.is(BlockTags.TERRACOTTA))) {
                                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(Items.MAGENTA_GLAZED_TERRACOTTA, 1));
                            }
                        }
                    }
                }
                case "cinder_jackpot" -> {
                    boolean nearHeat = player.isOnFire();
                    if (!nearHeat) {
                        for (Direction d : Direction.values()) {
                            BlockState adj = level.getBlockState(pos.relative(d));
                            if (adj.is(Blocks.LAVA) || adj.is(BlockTags.FIRE) || adj.is(Blocks.MAGMA_BLOCK)) {
                                nearHeat = true;
                                break;
                            }
                        }
                    }
                    if (nearHeat && rng.nextFloat() < 0.42F) {
                        spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(rng.nextBoolean() ? Items.EMERALD : Items.GOLD_NUGGET, 1));
                    }
                }
                case "velvet_shear" -> {
                    if (minedState.is(BlockTags.LEAVES) && rng.nextFloat() < 0.30F) {
                        spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(Items.APPLE, 1));
                    }
                }
                case "magnetic_pick", "beacon_bonfire" -> pullDropsToPlayer = true;
                default -> {}
            }
        }

        if (pullDropsToPlayer) {
            AABB box = AABB.ofSize(Vec3.atCenterOf(pos), 6.0D, 6.0D, 6.0D);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
                item.setNoPickUpDelay();
                item.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
            }
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive)) {
                orb.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
            }
        }
    }
}
