package enchantment_fusion.mechanics.transmute;

import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.getStandaloneEnchantLevel;
import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.getTransmutedLevel;
import static enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport.*;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.TemporaryFrostEffects;
import enchantment_fusion.mechanics.CrossDomainTransmutations.SmeltOutcome;
import enchantment_fusion.mechanics.SignaturePairs;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mining, Kiln/Charcoal-Smelt, Excavation &amp; Fishing Rod Cross-Domain Transmutations.
 */
public final class MiningFishingTransmutes {
    private MiningFishingTransmutes() {}

    public static float modifyDestroySpeed(Player player, ItemStack tool, BlockState state, float speed) {
        if (tool.isEmpty() || speed <= 1.0F) {
            return speed;
        }
        float mult = 1.0F;
        int faLv = getStandaloneEnchantLevel(tool, "minecraft:fire_aspect");
        if (faLv > 0 && (getTransmutedLevel(tool, "auto_smelt_pick") > 0 || getTransmutedLevel(tool, "auto_smelt_axe") > 0)) {
            mult *= (faLv == 1 ? 0.58F : 0.82F);
        }
        int featherLv = getTransmutedLevel(tool, "light_feather");
        if (featherLv > 0) mult *= (1.0F + 0.08F * featherLv);
        if (getTransmutedLevel(tool, "heat_shield_pick") > 0 && (player.isOnFire() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.NETHERRACK))) {
            mult *= 1.45F;
        }
        if (getTransmutedLevel(tool, "nest_purger") > 0 && state.is(Blocks.COBWEB)) {
            mult *= 5.0F;
        }
        int dredgeLv = getTransmutedLevel(tool, "abyssal_dredge");
        if (dredgeLv > 0 && player.isInWaterOrRain()) mult *= (1.35F + 0.20F * dredgeLv);
        int riptideLv = getTransmutedLevel(tool, "torrent_mine");
        if (riptideLv > 0 && player.isInWaterOrRain()) mult *= (1.0F + 0.45F * riptideLv);
        int impaleLv = getTransmutedLevel(tool, "prismarine_chisel");
        if (impaleLv > 0 && (state.is(Blocks.PRISMARINE) || state.is(Blocks.DARK_PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS) || state.is(Blocks.SEA_LANTERN))) {
            mult *= (1.5F + 0.25F * impaleLv);
        }
        if (getTransmutedLevel(tool, "heavy_plummet") > 0 && player.getXRot() > 45.0F) {
            mult *= 1.25F;
        }
        return speed * mult;
    }

    public static boolean onBeforeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (player.getAbilities().instabuild || player.isSpectator()) return true;
        if (!player.mayBuild() || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
            return true;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return true;

        if (getTransmutedLevel(tool, "nest_purger") > 0) {
            Item cleanStone = mapInfestedBlockToCleanItem(state.getBlock());
            if (cleanStone != null) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(cleanStone, 1));
                level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.25D, 0.25D, 0.25D, 0.05D);
                damageItemSafely(tool, 1, player);
                onAfterBlockBreak(level, player, pos, state);
                return false;
            }
        }
        return true;
    }

    public static void onPostDestroyBlockDrops(
        ServerLevel level,
        ServerPlayer player,
        BlockPos pos,
        BlockState state,
        ItemStack toolSnapshot
    ) {
        ItemStack tool = (toolSnapshot == null || toolSnapshot.isEmpty()) ? player.getMainHandItem() : toolSnapshot;
        if (tool.isEmpty()) return;

        int axeLv = getTransmutedLevel(tool, "auto_smelt_axe");
        if (axeLv > 0 && !player.isShiftKeyDown()) {
            RandomSource rng = level.getRandom();
            AABB dropBox = new AABB(pos).inflate(1.9D);
            if (state.is(BlockTags.LOGS)) {
                float charcoalChance = axeLv >= 2 ? 1.0F : 0.65F;
                boolean charred = false;
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, dropBox, e -> e.isAlive() && e.getAge() <= 2)) {
                    if (ie.getItem().is(ItemTags.LOGS) && rng.nextFloat() < charcoalChance) {
                        int outCount = ie.getItem().getCount() + (axeLv >= 2 && rng.nextFloat() < 0.25F ? 1 : 0);
                        ie.setItem(new ItemStack(Items.CHARCOAL, outCount));
                        charred = true;
                    }
                }
                if (charred) {
                    ExperienceOrb.award(level, Vec3.atCenterOf(pos), 1);
                    level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 7, 0.25D, 0.25D, 0.25D, 0.02D);
                }
            } else if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_FENCES)) {
                float ashChance = axeLv >= 2 ? 0.20F : 0.40F;
                float charcoalThreshold = axeLv >= 2 ? 0.85F : 0.78F;
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, dropBox, e -> e.isAlive() && e.getAge() <= 2)) {
                    float roll = rng.nextFloat();
                    if (roll < ashChance) {
                        ie.discard();
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.25D, 0.25D, 0.25D, 0.03D);
                        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.3F);
                    } else if (roll < charcoalThreshold) {
                        ie.setItem(new ItemStack(Items.CHARCOAL, 1));
                        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 5, 0.2D, 0.2D, 0.2D, 0.02D);
                    }
                }
            }
        }

        if (!player.isShiftKeyDown() && getTransmutedLevel(tool, "auto_smelt_pick") > 0) {
            smeltFreshBlockDropsInPlace(level, pos, state, 1.0F);
        }

        boolean magnetActive = (getTransmutedLevel(tool, "current_anchor") > 0 && player.isInWater())
            || (getTransmutedLevel(tool, "abyssal_dredge") > 0 && player.isInWaterOrRain())
            || getTransmutedLevel(tool, "auto_loot_return") > 0
            || SignaturePairs.stackHasSignature(tool, "magnetic_pick")
            || SignaturePairs.playerHasEquippedSignature(player, "magnetic_pick");
        if (magnetActive) {
            AABB pullBox = new AABB(pos).inflate(2.4D);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, pullBox, e -> e.isAlive() && e.getAge() <= 3)) {
                drop.setNoPickUpDelay();
                drop.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
            }
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, pullBox, Entity::isAlive)) {
                orb.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
            }
        }
    }

    public static boolean smeltFreshBlockDropsInPlace(
        ServerLevel level,
        BlockPos pos,
        BlockState brokenState,
        float xpMultiplier
    ) {
        AABB dropBox = new AABB(pos).inflate(1.9D);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, dropBox, e -> e.isAlive() && e.getAge() <= 2);
        if (drops.isEmpty()) return false;
        boolean smeltedAny = false;
        float totalXp = 0.0F;
        for (ItemEntity ie : drops) {
            ItemStack rawStack = ie.getItem();
            if (rawStack.isEmpty()) continue;
            SmeltOutcome outcome = resolveSmeltedStack(level, rawStack, brokenState.getBlock());
            if (outcome != null && !outcome.result().isEmpty()) {
                ie.setItem(outcome.result());
                smeltedAny = true;
                totalXp += outcome.xp();
            }
        }
        if (smeltedAny) {
            ExperienceOrb.award(level, Vec3.atCenterOf(pos), Math.max(1, Math.round(totalXp * xpMultiplier)));
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 7, 0.25D, 0.25D, 0.25D, 0.02D);
        }
        return smeltedAny;
    }

    public static @Nullable SmeltOutcome resolveSmeltedStack(@Nullable ServerLevel level, ItemStack rawStack, @Nullable Block brokenBlock) {
        if (rawStack == null || rawStack.isEmpty()) return null;
        int inputCount = Math.max(1, rawStack.getCount());
        if (level != null) {
            SingleRecipeInput singleInput = new SingleRecipeInput(rawStack.copyWithCount(1));
            Optional<RecipeHolder<SmeltingRecipe>> recipeOpt =
                level.recipeAccess().getRecipeFor(RecipeType.SMELTING, singleInput, level);
            if (recipeOpt.isPresent()) {
                SmeltingRecipe recipe = recipeOpt.get().value();
                ItemStack assembled = recipe.assemble(singleInput);
                if (!assembled.isEmpty() && !ItemStack.isSameItem(assembled, rawStack)) {
                    int outCount = Math.min(99, inputCount * Math.max(1, assembled.getCount()));
                    return new SmeltOutcome(assembled.copyWithCount(outCount), Math.max(0.2F, recipe.experience()) * inputCount);
                }
            }
        }
        Item fallback = mapItemAutoSmeltFallback(rawStack.getItem(), brokenBlock);
        if (fallback != null && !rawStack.is(fallback)) {
            return new SmeltOutcome(new ItemStack(fallback, inputCount), 0.7F * inputCount);
        }
        return null;
    }

    public static void onAfterBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        if (getTransmutedLevel(tool, "frost_seal") > 0) {
            TemporaryFrostEffects.sealAdjacentFluids(level, player, pos);
        }
        if (getTransmutedLevel(tool, "heat_shield_pick") > 0 && player.isOnFire()) {
            player.clearFire();
        }
        int gillLv = getTransmutedLevel(tool, "gill_membrane");
        if (gillLv > 0 && player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 30 * gillLv));
            level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.25D, 0.25D, 0.25D, 0.04D);
        }
        int soulExcLv = getTransmutedLevel(tool, "soul_excavator");
        if (soulExcLv > 0) {
            grantEffect(player, "speed", 50 + 15 * soulExcLv, Math.min(1, soulExcLv - 1));
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(Vec3.atCenterOf(pos), 8.0D, 6.0D, 8.0D), Entity::isAlive)) {
                orb.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
            }
            level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 5, 0.2D, 0.2D, 0.2D, 0.02D);
        }
        int briarLv = getTransmutedLevel(tool, "briar_mattock");
        if (briarLv > 0) {
            for (LivingEntity mob : nearbyHostiles(level, player, Vec3.atCenterOf(pos), 3.6D, null)) {
                Vec3 snag = Vec3.atCenterOf(pos).subtract(mob.position()).normalize().scale(0.35D);
                mob.push(snag.x, 0.14D, snag.z);
                mob.hurtServer(level, level.damageSources().thorns(player), 1.4F + 0.7F * briarLv);
                grantEffect(mob, "slowness", 40, 1);
            }
        }
        int blastGuardLv = getTransmutedLevel(tool, "blast_guard");
        if (blastGuardLv > 0 && !Boolean.TRUE.equals(IN_MULTI_BREAK.get()) && level.getRandom().nextFloat() < 0.16F * blastGuardLv) {
            IN_MULTI_BREAK.set(Boolean.TRUE);
            try {
                BlockPos adj = pos.relative(Direction.getRandom(level.getRandom()));
                BlockState adjSt = level.getBlockState(adj);
                if (adjSt.is(BlockTags.BASE_STONE_OVERWORLD) || adjSt.is(Blocks.COBBLESTONE) || adjSt.is(Blocks.NETHERRACK)) {
                    level.destroyBlock(adj, true, player);
                }
            } finally {
                IN_MULTI_BREAK.set(Boolean.FALSE);
            }
        }
        int tunnelLv = getTransmutedLevel(tool, "lunge_tunnel_rush");
        if (tunnelLv > 0) {
            Vec3 step = new Vec3(player.getLookAngle().x, 0.0D, player.getLookAngle().z);
            if (step.lengthSqr() > 0.01D && player.onGround()) {
                Vec3 push = step.normalize().scale(0.24D + 0.08D * tunnelLv);
                player.push(push.x, 0.04D, push.z);
                player.hurtMarked = true;
            }
            grantEffect(player, "speed", 50, Math.min(1, tunnelLv - 1));
        }
        int chainLv = getTransmutedLevel(tool, "chain_tempo");
        if (chainLv > 0) {
            grantEffect(player, "haste", 50, Math.min(1, chainLv - 1));
        }

        RandomSource rng = level.getRandom();
        int forageLv = getTransmutedLevel(tool, "forager_luck");
        if (forageLv > 0 && rng.nextFloat() < 0.09F * forageLv) {
            if (state.is(Blocks.GRAVEL)) spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(rng.nextBoolean() ? Items.FLINT : Items.IRON_NUGGET, 1));
            else if (state.is(BlockTags.LEAVES)) spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(Items.APPLE, 1));
            else if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)) {
                spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(rng.nextFloat() < 0.2F ? Items.EMERALD : Items.BONE_MEAL, 1));
            }
        }
        int panLv = getTransmutedLevel(tool, "sand_panning");
        if (panLv > 0 && (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD))
            && rng.nextFloat() < 0.11F * panLv) {
            Item prize = switch (rng.nextInt(4)) {
                case 0 -> Items.PRISMARINE_CRYSTALS;
                case 1 -> Items.GOLD_NUGGET;
                case 2 -> Items.LAPIS_LAZULI;
                default -> Items.EMERALD;
            };
            spawnItem(level, Vec3.atCenterOf(pos), new ItemStack(prize, 1));
        }

        if (!player.isShiftKeyDown() && !Boolean.TRUE.equals(IN_MULTI_BREAK.get())) {
            IN_MULTI_BREAK.set(Boolean.TRUE);
            try {
                if (getTransmutedLevel(tool, "wide_reap") > 0
                    && (state.is(BlockTags.CROPS) || state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.SNOW))) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            BlockPos adj = pos.offset(dx, 0, dz);
                            if (level.getBlockState(adj).is(state.getBlock())) level.destroyBlock(adj, true, player);
                        }
                    }
                } else if (getTransmutedLevel(tool, "trifork_mine") > 0 || getTransmutedLevel(tool, "wide_reap") > 0) {
                    Direction right = player.getDirection().getClockWise();
                    for (int s : new int[]{-1, 1}) {
                        BlockPos adj = pos.relative(right, s);
                        BlockState adjSt = level.getBlockState(adj);
                        if (adjSt.is(state.getBlock()) && adjSt.getDestroySpeed(level, adj) >= 0.0F) level.destroyBlock(adj, true, player);
                    }
                }
                int boreLv = getTransmutedLevel(tool, "deep_bore");
                if (boreLv > 0) {
                    Direction forward = Direction.orderedByNearest(player)[0];
                    for (int d = 1; d <= Math.min(2, boreLv); d++) {
                        BlockPos deeper = pos.relative(forward, d);
                        BlockState deepSt = level.getBlockState(deeper);
                        if (deepSt.is(state.getBlock()) && deepSt.getDestroySpeed(level, deeper) >= 0.0F) level.destroyBlock(deeper, true, player);
                        else break;
                    }
                }
            } finally {
                IN_MULTI_BREAK.set(Boolean.FALSE);
            }
        }
    }

    public static void onFishingReel(
        ServerLevel level,
        ServerPlayer player,
        ItemStack rod,
        @Nullable LivingEntity hookedMob,
        Vec3 bobberPos
    ) {
        if (rod == null || rod.isEmpty()) return;
        RandomSource rng = level.getRandom();

        int boilLv = getTransmutedLevel(rod, "boiling_cast");
        if (boilLv > 0) {
            if (hookedMob != null) {
                hookedMob.igniteForSeconds(3.0F + 2.0F * boilLv);
                hookedMob.hurtServer(level, level.damageSources().magic(), 2.2F + 0.8F * boilLv);
            }
            level.getServer().execute(() -> {
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3.5D), e -> e.isAlive() && e.getAge() <= 4)) {
                    Item cooked = mapRawFishToCooked(ie.getItem().getItem());
                    if (cooked != null) ie.setItem(new ItemStack(cooked, ie.getItem().getCount()));
                }
            });
            level.sendParticles(ParticleTypes.FLAME, bobberPos.x, bobberPos.y + 0.3D, bobberPos.z, 8, 0.25D, 0.2D, 0.25D, 0.03D);
        }

        int iceHoleLv = getTransmutedLevel(rod, "ice_hole_angler");
        if (iceHoleLv > 0) {
            if (hookedMob != null) {
                hookedMob.setTicksFrozen(Math.min(350, hookedMob.getTicksFrozen() + 90 * iceHoleLv));
                grantEffect(hookedMob, "slowness", 60, iceHoleLv - 1);
            }
            if (player.mayBuild()) AberrantRegistry.placeSingleFrostedIce(level, BlockPos.containing(bobberPos));
            level.sendParticles(ParticleTypes.SNOWFLAKE, bobberPos.x, bobberPos.y + 0.4D, bobberPos.z, 10, 0.35D, 0.2D, 0.35D, 0.03D);
        }

        if (getTransmutedLevel(rod, "gentle_reel") > 0 && hookedMob != null && !(hookedMob instanceof Player)) {
            hookedMob.resetFallDistance();
            Vec3 gentle = player.position().add(player.getLookAngle().scale(1.4D));
            hookedMob.teleportTo(gentle.x, gentle.y + 0.2D, gentle.z);
        }

        if (getTransmutedLevel(rod, "auto_reel") > 0 || getTransmutedLevel(rod, "abyssal_dredge") > 0) {
            level.getServer().execute(() -> {
                for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(bobberPos, 6.0D, 4.0D, 6.0D), Entity::isAlive)) {
                    ie.setNoPickUpDelay();
                    ie.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                }
            });
        }

        int netLv = getTransmutedLevel(rod, "whirlwind_hem");
        if (netLv > 0) {
            for (LivingEntity nearby : nearbyHostiles(level, player, bobberPos, 2.8D + 0.5D * netLv, null)) {
                Vec3 yank = player.position().subtract(nearby.position()).normalize().scale(0.65D);
                nearby.push(yank.x, 0.28D, yank.z);
                nearby.hurtServer(level, level.damageSources().playerAttack(player), 1.6F + 0.6F * netLv);
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, bobberPos.x, bobberPos.y + 0.5D, bobberPos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        if (hookedMob != null) {
            int holyLv = getTransmutedLevel(rod, "holy_bolt");
            if (holyLv > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(hookedMob.getType()).is(EntityTypeTags.UNDEAD)) {
                hookedMob.hurtServer(level, level.damageSources().magic(), 2.8F * holyLv);
                hookedMob.igniteForSeconds(3.0F);
            }
            int arthLv = getTransmutedLevel(rod, "arthropod_bolt");
            if (arthLv > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(hookedMob.getType()).is(EntityTypeTags.ARTHROPOD)) {
                hookedMob.hurtServer(level, level.damageSources().magic(), 2.8F * arthLv);
                grantEffect(hookedMob, "slowness", 70, 2);
            }
            int fortuneLv = getTransmutedLevel(rod, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < 0.35F + 0.12F * fortuneLv) {
                triggerThreeDollarRoulette(level, player, hookedMob, fortuneLv);
            }
            if (getTransmutedLevel(rod, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(hookedMob.blockPosition())) {
                AberrantRegistry.spawnLightning(level, hookedMob.position());
            }
        }

        int gillLv = getTransmutedLevel(rod, "gill_membrane");
        if (gillLv > 0) player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 45 * gillLv));
        int soulLv = getTransmutedLevel(rod, "soul_excavator");
        if (soulLv > 0) grantEffect(player, "speed", 60, Math.min(1, soulLv - 1));
        int stormGlide = getTransmutedLevel(rod, "storm_glider");
        if (stormGlide > 0 && player.isInWaterOrRain()) {
            Vec3 pull = bobberPos.subtract(player.position());
            if (pull.lengthSqr() > 1.0D) {
                Vec3 dash = pull.normalize().scale(0.85D + 0.20D * stormGlide);
                player.push(dash.x, Math.max(0.24D, dash.y * 0.5D), dash.z);
                player.hurtMarked = true;
            }
        }
        int jetRodLv = getTransmutedLevel(rod, "jetstream_boost");
        if (jetRodLv > 0 && !player.isUnderWater()) {
            player.push(0.0D, 0.42D + 0.10D * jetRodLv, 0.0D);
            player.hurtMarked = true;
            player.resetFallDistance();
        }
    }

    private static @Nullable Item mapInfestedBlockToCleanItem(Block b) {
        if (b == Blocks.INFESTED_STONE || b == Blocks.INFESTED_COBBLESTONE) return Items.COBBLESTONE;
        if (b == Blocks.INFESTED_DEEPSLATE) return Items.COBBLED_DEEPSLATE;
        if (b == Blocks.INFESTED_STONE_BRICKS || b == Blocks.INFESTED_MOSSY_STONE_BRICKS
            || b == Blocks.INFESTED_CRACKED_STONE_BRICKS || b == Blocks.INFESTED_CHISELED_STONE_BRICKS) {
            return Items.STONE_BRICKS;
        }
        return null;
    }

    private static @Nullable Item mapRawFishToCooked(Item raw) {
        if (raw == Items.COD) return Items.COOKED_COD;
        if (raw == Items.SALMON) return Items.COOKED_SALMON;
        if (raw == Items.KELP) return Items.DRIED_KELP;
        return null;
    }

    public static @Nullable Item mapItemAutoSmeltFallback(Item rawItem, @Nullable Block brokenBlock) {
        if (rawItem == Items.RAW_IRON || rawItem == Items.IRON_ORE || rawItem == Items.DEEPSLATE_IRON_ORE) return Items.IRON_INGOT;
        if (rawItem == Items.RAW_GOLD || rawItem == Items.GOLD_ORE || rawItem == Items.DEEPSLATE_GOLD_ORE) return Items.GOLD_INGOT;
        if (rawItem == Items.RAW_COPPER || rawItem == Items.COPPER_ORE || rawItem == Items.DEEPSLATE_COPPER_ORE) return Items.COPPER_INGOT;
        if (rawItem == Items.ANCIENT_DEBRIS) return Items.NETHERITE_SCRAP;
        if (rawItem == Items.COBBLESTONE) return Items.STONE;
        if (rawItem == Items.STONE) return Items.SMOOTH_STONE;
        if (rawItem == Items.COBBLED_DEEPSLATE) return Items.DEEPSLATE;
        if (rawItem == Items.SAND || rawItem == Items.RED_SAND) return Items.GLASS;
        if (rawItem == Items.CLAY_BALL) return Items.BRICK;
        if (rawItem == Items.CLAY) return Items.TERRACOTTA;
        if (rawItem == Items.NETHERRACK) return Items.NETHER_BRICK;
        if (rawItem == Items.WET_SPONGE) return Items.SPONGE;
        if (rawItem == Items.CACTUS) return Items.GREEN_DYE;
        if (rawItem == Items.KELP) return Items.DRIED_KELP;
        if (brokenBlock != null) {
            if (brokenBlock == Blocks.IRON_ORE || brokenBlock == Blocks.DEEPSLATE_IRON_ORE) return Items.IRON_INGOT;
            if (brokenBlock == Blocks.GOLD_ORE || brokenBlock == Blocks.DEEPSLATE_GOLD_ORE) return Items.GOLD_INGOT;
            if (brokenBlock == Blocks.COPPER_ORE || brokenBlock == Blocks.DEEPSLATE_COPPER_ORE) return Items.COPPER_INGOT;
            if (brokenBlock == Blocks.ANCIENT_DEBRIS) return Items.NETHERITE_SCRAP;
        }
        return null;
    }
}
