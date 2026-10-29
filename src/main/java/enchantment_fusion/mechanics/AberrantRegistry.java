package enchantment_fusion.mechanics;

import enchantment_fusion.EnchantmentFusion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 6 double-edged Aberrant Fusions produced when repulsive mutually-exclusive max-level
 * enchantments destabilize or when high-tier synthesis suffers an unstable failure.
 * Also manages temporary {@code Blocks.FROSTED_ICE} prisons &amp; snares with unloaded-chunk retention
 * and shutdown cleanup (EF-012 / NUM-5 / NUM-6).
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

    private static final Set<String> BLOOD_FRENZY_PATHS = Set.of("smite", "sharpness", "bane_of_arthropods");
    private static final Set<String> ROGUE_LIGHTNING_PATHS = Set.of("channeling", "riptide", "loyalty");
    private static final Set<String> GREEDY_ABYSS_PATHS = Set.of("fortune", "silk_touch", "mending", "infinity");
    private static final Set<String> MUTUAL_GLACIER_PATHS = Set.of("frost_walker", "depth_strider");
    private static final Set<String> HOT_POTATO_PATHS = Set.of("fire_protection", "fire_aspect", "flame");
    private static final Set<String> RECOIL_SKID_PATHS = Set.of("blast_protection", "projectile_protection", "protection", "lunge");

    public record TrackedFrostedCell(
        ResourceKey<Level> dimension,
        BlockPos pos,
        boolean wasWater,
        int stage,
        long nextStepTick,
        long createdAtTick
    ) {}

    private static final Map<String, TrackedFrostedCell> TRACKED_FROSTED_ICE = new ConcurrentHashMap<>();

    private AberrantRegistry() {}

    public static List<String> allIds() {
        return ALL_IDS;
    }

    public static void clearRuntimeState() {
        TRACKED_FROSTED_ICE.clear();
    }

    /**
     * Restores all loaded {@code Blocks.FROSTED_ICE} cells on shutdown while retaining any unloaded
     * entries instead of blindly dropping them before chunk save completes (NUM-6).
     */
    public static void flushAllCages(MinecraftServer server) {
        if (TRACKED_FROSTED_ICE.isEmpty()) {
            return;
        }
        int retainedUnloaded = 0;
        for (Map.Entry<String, TrackedFrostedCell> entry : new ArrayList<>(TRACKED_FROSTED_ICE.entrySet())) {
            TrackedFrostedCell cell = entry.getValue();
            ServerLevel level = server.getLevel(cell.dimension());
            if (level == null) {
                TRACKED_FROSTED_ICE.remove(entry.getKey());
                continue;
            }
            if (!level.isLoaded(cell.pos())) {
                retainedUnloaded++;
                continue;
            }
            if (level.getBlockState(cell.pos()).is(Blocks.FROSTED_ICE)) {
                level.setBlockAndUpdate(
                    cell.pos(),
                    cell.wasWater() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState()
                );
            }
            TRACKED_FROSTED_ICE.remove(entry.getKey());
        }
        if (retainedUnloaded > 0) {
            EnchantmentFusion.LOGGER.debug(
                "Retained {} unloaded frosted-ice cage cells across server stop; vanilla scheduled ticks will melt them on reload",
                retainedUnloaded
            );
        }
    }

    public static String pickRandomAberration(RandomSource rng) {
        return ALL_IDS.get(rng.nextInt(ALL_IDS.size()));
    }

    private static String extractEnchantPath(@Nullable String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return "";
        }
        Identifier parsed = Identifier.tryParse(idStr.trim());
        if (parsed != null) {
            return parsed.getPath();
        }
        int colon = idStr.indexOf(':');
        return colon >= 0 ? idStr.substring(colon + 1).trim() : idStr.trim();
    }

    private static boolean matchesAnyPath(Set<String> pairPaths, Set<String> candidates) {
        for (String p : pairPaths) {
            if (candidates.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selects a thematic aberration by exact {@link Identifier#getPath()} matching rather than
     * substring {@code contains(...)} so custom namespaces/enchantment names never false-match (NUM-5).
     */
    public static String pickAberration(String primaryId, String secondaryId, RandomSource rng) {
        Set<String> pairPaths = new HashSet<>(2);
        String pPath = extractEnchantPath(primaryId);
        String sPath = extractEnchantPath(secondaryId);
        if (!pPath.isEmpty()) pairPaths.add(pPath);
        if (!sPath.isEmpty()) pairPaths.add(sPath);

        if (matchesAnyPath(pairPaths, BLOOD_FRENZY_PATHS)) {
            return BLOOD_FRENZY;
        }
        if (matchesAnyPath(pairPaths, ROGUE_LIGHTNING_PATHS)) {
            return ROGUE_LIGHTNING;
        }
        if (matchesAnyPath(pairPaths, GREEDY_ABYSS_PATHS)) {
            return GREEDY_ABYSS;
        }
        if (matchesAnyPath(pairPaths, MUTUAL_GLACIER_PATHS)) {
            return MUTUAL_GLACIER;
        }
        if (matchesAnyPath(pairPaths, HOT_POTATO_PATHS)) {
            return HOT_POTATO;
        }
        if (matchesAnyPath(pairPaths, RECOIL_SKID_PATHS)) {
            return RECOIL_SKID;
        }
        return pickRandomAberration(rng);
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
        CompendiumDiscoveryTracker.recordAberrantMastered(player, id);
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
                if (rng.nextFloat() < 0.25F && player.mayBuild()) {
                    BlockPos center = target != null ? target.blockPosition() : player.blockPosition();
                    placeFrostedIceCage3x3x3(level, center, false);
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

    public static void placeFrostedIceCage3x3x3(ServerLevel level, BlockPos center, boolean solidCore) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!solidCore && dx == 0 && dz == 0 && (dy == 0 || dy == 1)) {
                        continue;
                    }
                    placeSingleFrostedIce(level, center.offset(dx, dy, dz));
                }
            }
        }
    }

    public static void placeIceCage3x3x3(ServerLevel level, BlockPos center, BlockState ignoredLegacyState, boolean solidCore) {
        placeFrostedIceCage3x3x3(level, center, solidCore);
    }

    public static void placeFrostedIceSnare(ServerLevel level, BlockPos center) {
        placeSingleFrostedIce(level, center);
        placeSingleFrostedIce(level, center.north());
        placeSingleFrostedIce(level, center.south());
        placeSingleFrostedIce(level, center.east());
        placeSingleFrostedIce(level, center.west());
    }

    public static void placeSingleFrostedIce(ServerLevel level, BlockPos p) {
        BlockState cur = level.getBlockState(p);
        boolean wasWater = cur.is(Blocks.WATER) && cur.getFluidState().isSource();
        if (cur.isAir() || cur.canBeReplaced() || wasWater) {
            BlockPos imm = p.immutable();
            level.setBlockAndUpdate(imm, Blocks.FROSTED_ICE.defaultBlockState());
            int delay = Mth.nextInt(level.getRandom(), 24, 48);
            long now = level.getGameTime();
            level.scheduleTick(imm, Blocks.FROSTED_ICE, delay);
            if (TRACKED_FROSTED_ICE.size() < 4096) {
                String cellKey = level.dimension().identifier() + "@" + imm.asLong();
                TRACKED_FROSTED_ICE.put(
                    cellKey,
                    new TrackedFrostedCell(level.dimension(), imm, wasWater, 0, now + delay, now)
                );
            }
        }
    }

    /**
     * Advances tracked {@code Blocks.FROSTED_ICE} cells through vanilla's 4 cracking states
     * even in dark caves; retains temporarily unloaded chunks until reloaded (EF-012).
     */
    public static void tickFrostedCages(MinecraftServer server) {
        if (TRACKED_FROSTED_ICE.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        List<BlockState> states = Blocks.FROSTED_ICE.getStateDefinition().getPossibleStates();
        for (Map.Entry<String, TrackedFrostedCell> entry : new ArrayList<>(TRACKED_FROSTED_ICE.entrySet())) {
            String key = entry.getKey();
            TrackedFrostedCell cell = entry.getValue();
            ServerLevel level = server.getLevel(cell.dimension());
            if (level == null) {
                TRACKED_FROSTED_ICE.remove(key);
                continue;
            }
            long now = level.getGameTime();
            if (!level.isLoaded(cell.pos())) {
                if (now - cell.createdAtTick() > 18000L) {
                    TRACKED_FROSTED_ICE.remove(key);
                }
                continue;
            }
            if (now < cell.nextStepTick()) {
                continue;
            }
            BlockState cur = level.getBlockState(cell.pos());
            if (!cur.is(Blocks.FROSTED_ICE)) {
                TRACKED_FROSTED_ICE.remove(key);
                continue;
            }
            int nextStage = cell.stage() + 1;
            if (nextStage < states.size()) {
                level.setBlockAndUpdate(cell.pos(), states.get(nextStage));
                int delay = Mth.nextInt(level.getRandom(), 20, 36);
                TRACKED_FROSTED_ICE.put(
                    key,
                    new TrackedFrostedCell(cell.dimension(), cell.pos(), cell.wasWater(), nextStage, now + delay, cell.createdAtTick())
                );
            } else {
                level.setBlockAndUpdate(cell.pos(), cell.wasWater() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                level.sendParticles(
                    ParticleTypes.SNOWFLAKE,
                    cell.pos().getX() + 0.5D, cell.pos().getY() + 0.5D, cell.pos().getZ() + 0.5D,
                    4, 0.25D, 0.25D, 0.25D, 0.02D
                );
                TRACKED_FROSTED_ICE.remove(key);
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
