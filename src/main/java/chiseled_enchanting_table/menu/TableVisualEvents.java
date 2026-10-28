package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.mechanics.resonance.TomeParticleEffects;
import chiseled_enchanting_table.menu.TableVisualEventPayload.Kind;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side coordinator for cohesive, refined audio-visual feedback across ritual,
 * bookshelf-slot, Cleanse, Epiphany, Decipher, and table-cloth interactions.
 *
 * <p>Pairs gentle harmonic sound cues (amethyst chimes, book flips, brewing mist, crisp XP —
 * never jarring thunder/totem blasts) with compact vanilla particles and {@link TableVisualEventPayload}
 * broadcasts for custom 3D billboard sprites and GUI micro-animations.</p>
 */
public final class TableVisualEvents {
    private static final double BROADCAST_RADIUS_SQR = 48.0D * 48.0D;

    public static final int COLOR_CRIMSON_BACKLASH = 0xFFFF4258;
    public static final int COLOR_ARCANE_VIOLET = 0xFFD99BFF;
    public static final int COLOR_SACRED_GOLD = 0xFFFFD866;
    public static final int COLOR_JADE_MINT = 0xFF5CFFD0;
    public static final int COLOR_MENDING_EMERALD = 0xFF7CFFA6;
    public static final int COLOR_FROST_BLUE = 0xFF8CE8FF;
    public static final int COLOR_AMBER_EMBER = 0xFFFFAD5C;
    public static final int COLOR_WATER_WASH = 0xFF8FD4FF;
    public static final int COLOR_LOCATE_CYAN = 0xFF74F3FF;

    private TableVisualEvents() {}

    public static void broadcast(Level level, BlockPos origin, TableVisualEventPayload payload) {
        if (!(level instanceof ServerLevel serverLevel) || origin == null || payload == null) {
            return;
        }
        double ox = origin.getX() + 0.5D;
        double oy = origin.getY() + 0.5D;
        double oz = origin.getZ() + 0.5D;
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp.connection != null
                && sp.distanceToSqr(ox, oy, oz) <= BROADCAST_RADIUS_SQR
                && ServerPlayNetworking.canSend(sp, TableVisualEventPayload.TYPE)) {
                ServerPlayNetworking.send(sp, payload);
            }
        }
    }

    private static int shelfFacing2D(Level level, BlockPos shelfPos) {
        if (level == null || shelfPos == null) {
            return -1;
        }
        BlockState state = level.getBlockState(shelfPos);
        if (state.hasProperty(ChiseledBookShelfBlock.FACING)) {
            return state.getValue(ChiseledBookShelfBlock.FACING).get2DDataValue();
        }
        return Direction.SOUTH.get2DDataValue();
    }

    /** FORCE_RUSH burned a Master Tome at (shelfPos, slot): beam + 6s slot beacon + gentle scorched chord. */
    public static void onTomeBacklash(Level level, BlockPos tablePos, BlockPos shelfPos, int slot) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null || shelfPos == null) {
            return;
        }
        int facing2D = shelfFacing2D(serverLevel, shelfPos);
        Direction facing = facing2D >= 0 ? Direction.from2DDataValue(facing2D) : Direction.SOUTH;
        double[] target = TomeParticleEffects.slotWorldCoordinates(shelfPos, facing, slot);
        double sx = tablePos.getX() + 0.5D;
        double sy = tablePos.getY() + 1.05D;
        double sz = tablePos.getZ() + 0.5D;
        for (int i = 1; i <= 8; i++) {
            double t = i / 8.0D;
            double px = sx + (target[0] - sx) * t;
            double py = sy + (target[1] - sy) * t + Math.sin(t * Math.PI) * 0.16D;
            double pz = sz + (target[2] - sz) * t;
            serverLevel.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.02D, 0.02D, 0.02D, 0.004D);
        }
        serverLevel.sendParticles(ParticleTypes.SMOKE, target[0], target[1], target[2], 10, 0.08D, 0.10D, 0.08D, 0.015D);
        TomeParticleEffects.backlashAt(serverLevel, shelfPos, slot);
        serverLevel.playSound(null, tablePos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.70F, 0.65F);
        serverLevel.playSound(null, shelfPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.45F, 1.35F);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.RUSH_BACKLASH, tablePos, shelfPos, slot, facing2D, COLOR_CRIMSON_BACKLASH
        ));
    }

    /** Master Tome slot feeding energy into the table (tome copy or 1s active-ritual pulse). */
    public static void onTomeTether(Level level, BlockPos tablePos, BlockPos shelfPos, int slot, int argb) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null || shelfPos == null) {
            return;
        }
        int facing2D = shelfFacing2D(serverLevel, shelfPos);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.TOME_TETHER, tablePos, shelfPos, slot, facing2D, argb
        ));
    }

    /** SAFE_ABORT wear undo, MEND_ONE, or SANCTIFY_CATALYST flowing from table back to shelf slot. */
    public static void onTomeRestore(Level level, BlockPos tablePos, BlockPos shelfPos, int slot, int argb) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null || shelfPos == null) {
            return;
        }
        int facing2D = shelfFacing2D(serverLevel, shelfPos);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.TOME_RESTORE, tablePos, shelfPos, slot, facing2D, argb
        ));
    }

    /** Multi-select or single Tome Locate guide beam + 6s slot rune beacon at (shelfPos, slot). */
    public static void onTomeLocateBeam(Level level, BlockPos tablePos, BlockPos shelfPos, int slot) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null || shelfPos == null) {
            return;
        }
        int facing2D = shelfFacing2D(serverLevel, shelfPos);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.TOME_LOCATE, tablePos, shelfPos, slot, facing2D, COLOR_LOCATE_CYAN
        ));
    }

    /** Depleted Tome sacrificed into XP or ritual rush. */
    public static void onSacrificePages(Level level, BlockPos tablePos) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null) {
            return;
        }
        serverLevel.sendParticles(
            ParticleTypes.ENCHANT,
            tablePos.getX() + 0.5D, tablePos.getY() + 1.05D, tablePos.getZ() + 0.5D,
            10, 0.22D, 0.16D, 0.22D, 0.25D
        );
        serverLevel.playSound(null, tablePos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.65F, 0.92F);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.SACRIFICE_PAGES, tablePos, tablePos, -1, -1, COLOR_AMBER_EMBER
        ));
    }

    /** Ritual inscription completed, fuel-boosted, or catalyst instant-completed. */
    public static void onRitualPulse(Level level, BlockPos tablePos, int argb) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null) {
            return;
        }
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.RITUAL_COMPLETE, tablePos, tablePos, -1, -1, argb
        ));
    }

    /** Cleanse: single curse purge or purge-all-curses purification chord + sacred gold column. */
    public static void onCleansePurgeCurse(Level level, BlockPos tablePos, int curseCount) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null) {
            return;
        }
        double cx = tablePos.getX() + 0.5D;
        double cy = tablePos.getY() + 0.95D;
        double cz = tablePos.getZ() + 0.5D;
        int count = Math.min(18, 8 + Math.max(1, curseCount) * 3);
        serverLevel.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, count, 0.20D, 0.24D, 0.20D, 0.03D);
        serverLevel.sendParticles(ParticleTypes.GLOW, cx, cy + 0.15D, cz, 8, 0.18D, 0.18D, 0.18D, 0.01D);
        serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.72F, 1.32F);
        serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.50F, 1.15F);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.CLEANSE_PURGE_CURSE, tablePos, tablePos, -1, -1, COLOR_SACRED_GOLD
        ));
    }

    /** Cleanse: extract enchantment to book or strip single enchantment. */
    public static void onCleanseExtractOrStrip(Level level, BlockPos tablePos, boolean extractedToBook) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null) {
            return;
        }
        double cx = tablePos.getX() + 0.5D;
        double cy = tablePos.getY() + 0.98D;
        double cz = tablePos.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.20D, cz, 14, 0.24D, 0.18D, 0.24D, 0.35D);
        if (extractedToBook) {
            serverLevel.sendParticles(ParticleTypes.GLOW, cx, cy, cz, 8, 0.16D, 0.14D, 0.16D, 0.02D);
            serverLevel.playSound(null, tablePos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.75F, 1.18F);
            serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.62F, 1.42F);
        } else {
            serverLevel.playSound(null, tablePos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.42F, 1.45F);
            serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.55F, 1.18F);
        }
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.CLEANSE_EXTRACT_BOOK, tablePos, tablePos, -1, -1, COLOR_JADE_MINT
        ));
    }

    /** Cleanse XP refund directly repaired Mending gear on the player. */
    public static void onMendingGearRepaired(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        serverLevel.sendParticles(
            ParticleTypes.HAPPY_VILLAGER,
            player.getX(), player.getY() + 1.0D, player.getZ(),
            7, 0.32D, 0.35D, 0.32D, 0.02D
        );
        serverLevel.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55F, 1.38F);
        serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.48F, 1.55F);
        broadcast(serverLevel, pos, new TableVisualEventPayload(
            Kind.MENDING_REPAIR, pos, pos, -1, -1, COLOR_MENDING_EMERALD
        ));
    }

    /** Epiphany frozen or rerolled, Arcane Decipher, or item transformation pulse. */
    public static void onEpiphanyFreeze(Level level, BlockPos tablePos) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null) {
            return;
        }
        double cx = tablePos.getX() + 0.5D;
        double cy = tablePos.getY() + 1.02D;
        double cz = tablePos.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, cx, cy, cz, 10, 0.24D, 0.14D, 0.24D, 0.015D);
        serverLevel.playSound(null, tablePos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.52F, 1.65F);
        serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.65F, 1.50F);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.EPIPHANY_FREEZE, tablePos, tablePos, -1, -1, COLOR_FROST_BLUE
        ));
    }

    public static void onArcanePulse(Level level, BlockPos tablePos, int argb) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null) {
            return;
        }
        double cx = tablePos.getX() + 0.5D;
        double cy = tablePos.getY() + 1.02D;
        double cz = tablePos.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.15D, cz, 12, 0.24D, 0.18D, 0.24D, 0.30D);
        serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.62F, 1.26F);
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.ARCANE_PULSE, tablePos, tablePos, -1, -1, argb
        ));
    }

    /** Table cloth dyed (argb), jeb_ rainbow activated (argb == 0 for rainbow), or washed clean. */
    public static void onClothVisual(Level level, BlockPos tablePos, int argb, boolean washed, boolean rainbow) {
        if (!(level instanceof ServerLevel serverLevel) || tablePos == null) {
            return;
        }
        double cx = tablePos.getX() + 0.5D;
        double cy = tablePos.getY() + 0.82D;
        double cz = tablePos.getZ() + 0.5D;
        if (washed) {
            serverLevel.sendParticles(ParticleTypes.SPLASH, cx, cy, cz, 14, 0.28D, 0.08D, 0.28D, 0.08D);
            serverLevel.playSound(null, tablePos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.48F, 1.35F);
            serverLevel.playSound(null, tablePos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 0.65F, 1.12F);
        } else if (rainbow) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, cx, cy + 0.12D, cz, 12, 0.28D, 0.14D, 0.28D, 0.03D);
            serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.72F, 1.22F);
            serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.75F, 1.48F);
        } else {
            serverLevel.sendParticles(ParticleTypes.GLOW, cx, cy + 0.08D, cz, 8, 0.26D, 0.10D, 0.26D, 0.02D);
            serverLevel.playSound(null, tablePos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 0.68F, 1.18F);
            serverLevel.playSound(null, tablePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.55F, 1.30F);
        }
        int encodedColor = rainbow ? 0 : (washed ? COLOR_WATER_WASH : (0xFF000000 | argb));
        broadcast(serverLevel, tablePos, new TableVisualEventPayload(
            Kind.CLOTH_DYE_OR_WASH, tablePos, tablePos, washed ? 1 : 0, -1, encodedColor
        ));
    }
}
