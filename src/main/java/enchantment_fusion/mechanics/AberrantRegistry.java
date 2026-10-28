package enchantment_fusion.mechanics;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Handcrafted pool of unstable "Aberrant Fusions" ("畸融异变") produced when forcing
 * max-level mutually-exclusive enchantments together without a Resonance Stabilizer.
 * Cannot be removed by Grindstone, Anvil, or Fusion Table — only via Chiseled Enchanting Table curse-purge.
 */
public final class AberrantRegistry {
    public static final String BLOOD_FRENZY = "blood_frenzy";
    public static final String ROGUE_LIGHTNING = "rogue_lightning";
    public static final String GREEDY_ABYSS = "greedy_abyss";
    public static final String RECOIL_SKID = "recoil_skid";
    public static final String MUTUAL_GLACIER = "mutual_glacier";
    public static final String HOT_POTATO = "hot_potato";

    public static final List<String> ALL_IDS = List.of(
        BLOOD_FRENZY,
        ROGUE_LIGHTNING,
        GREEDY_ABYSS,
        RECOIL_SKID,
        MUTUAL_GLACIER,
        HOT_POTATO
    );

    private AberrantRegistry() {}

    public static String pickRandomAberration(RandomSource random) {
        return ALL_IDS.get(random.nextInt(ALL_IDS.size()));
    }

    public static String titleKey(String aberrationId) {
        String safe = (aberrationId == null || !ALL_IDS.contains(aberrationId)) ? BLOOD_FRENZY : aberrationId;
        return "aberration.enchantment_fusion." + safe + ".title";
    }

    public static String descKey(String aberrationId) {
        String safe = (aberrationId == null || !ALL_IDS.contains(aberrationId)) ? BLOOD_FRENZY : aberrationId;
        return "aberration.enchantment_fusion." + safe + ".desc";
    }

    public static void executeAberration(
        ServerLevel level,
        ServerPlayer player,
        String aberrationId,
        @Nullable LivingEntity target,
        Vec3 pos
    ) {
        String id = (aberrationId == null || aberrationId.isBlank()) ? BLOOD_FRENZY : aberrationId;
        RandomSource rng = level.getRandom();

        switch (id) {
            case BLOOD_FRENZY -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 4.5F);
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y + 0.8D, pos.z, 6, 0.25D, 0.25D, 0.25D, 0.05D);
                }
                if (rng.nextFloat() < 0.28F) {
                    player.hurtServer(level, level.damageSources().magic(), 1.0F);
                }
            }
            case ROGUE_LIGHTNING -> {
                Vec3 boltPos = target != null ? target.position() : pos;
                spawnLightning(level, boltPos);
                if (rng.nextFloat() < 0.15F) {
                    player.hurtServer(level, level.damageSources().lightningBolt(), 2.0F);
                    grantEffect(player, "slowness", 30, 1);
                }
            }
            case GREEDY_ABYSS -> {
                if (player.totalExperience >= 3) {
                    player.giveExperiencePoints(-3);
                    if (target != null) {
                        target.hurtServer(level, level.damageSources().magic(), 3.5F);
                    }
                    grantEffect(player, "luck", 100, 1);
                }
            }
            case RECOIL_SKID -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
                }
                Vec3 back = player.getLookAngle().scale(-1.15D);
                player.push(back.x, 0.28D, back.z);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4D, player.getZ(), 10, 0.3D, 0.2D, 0.3D, 0.06D);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.65F);
            }
            case MUTUAL_GLACIER -> {
                if (rng.nextFloat() < 0.25F) {
                    BlockPos center = target != null ? target.blockPosition() : player.blockPosition();
                    placeIceCage3x3x3(level, center, Blocks.PACKED_ICE.defaultBlockState(), false);
                    player.teleportTo(center.getX() + 0.5D, center.getY() + 0.1D, center.getZ() + 0.5D);
                    level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.85F, 0.7F);
                }
            }
            case HOT_POTATO -> {
                if (target != null) {
                    target.igniteForSeconds(6.0F);
                    target.hurtServer(level, level.damageSources().onFire(), 3.0F);
                }
                if (rng.nextFloat() < 0.20F) {
                    player.igniteForSeconds(2.0F);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
                }
            }
        }
    }

    /**
     * Places a 3x3x3 ice prison centered at {@code center} (replacing only air/replaceable vegetation).
     * When {@code solidCore} is true, even the inner space gets ice blocks; when false, leaves a 1x2x1 breathing pocket.
     */
    public static void placeIceCage3x3x3(ServerLevel level, BlockPos center, BlockState iceState, boolean solidCore) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!solidCore && dx == 0 && dz == 0 && (dy == 0 || dy == 1)) {
                        continue;
                    }
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState cur = level.getBlockState(p);
                    if (cur.isAir() || cur.canBeReplaced()) {
                        level.setBlockAndUpdate(p, iceState);
                    }
                }
            }
        }
    }

    public static void spawnLightning(ServerLevel level, Vec3 pos) {
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(bolt);
    }

    private static void grantEffect(LivingEntity entity, String path, int ticks, int amp) {
        BuiltInRegistries.MOB_EFFECT
            .get(Identifier.withDefaultNamespace(path))
            .ifPresent(h -> entity.addEffect(new MobEffectInstance(h, ticks, amp, true, true)));
    }
}
