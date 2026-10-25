package chiseled_enchanting_table.mechanics.resonance;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Field feedback (round 4): small particle cues at a tome's shelf slot so players can
 * see what happened without opening the page. Locking and full depletion get
 * enderman-teleport particles ({@link ParticleTypes#PORTAL}); a finished charge,
 * a single restored charge and a manual early unlock get end-rod sparkles
 * ({@link ParticleTypes#END_ROD}). Effects live for a handful of seconds at a few
 * particles per second and are hard-capped, so this never becomes a performance sink.
 */
public final class TomeParticleEffects {
    private TomeParticleEffects() {}

    private static final class Burst {
        final ServerLevel level;
        final double x;
        final double y;
        final double z;
        final ParticleOptions type;
        int ticksLeft;

        Burst(ServerLevel level, double x, double y, double z, ParticleOptions type, int ticksLeft) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final List<Burst> ACTIVE = new ArrayList<>();
    private static final int MAX_ACTIVE_BURSTS = 48;
    private static final int PORTAL_TICKS = 50;   // ~2.5s
    private static final int END_ROD_TICKS = 40;  // ~2s

    /** Distance out from block center (0.5) to place particles just outside the shelf front face. */
    public static final double SHELF_FRONT_FACE_OFFSET = 0.56D;
    /** Horizontal spacing between adjacent columns in the 3x2 chiseled-bookshelf slot grid. */
    public static final double SHELF_SLOT_COLUMN_SPACING = 0.25D;
    /** Vertical Y offset of the top row (slots 0..2) in a chiseled bookshelf. */
    public static final double SHELF_TOP_ROW_Y = 0.72D;
    /** Vertical Y offset of the bottom row (slots 3..5) in a chiseled bookshelf. */
    public static final double SHELF_BOTTOM_ROW_Y = 0.34D;

    /**
     * Computes facing-aware world coordinates {@code [x, y, z]} on the front face of a 3×2
     * chiseled bookshelf for {@code slot} ({@code 0..5}) (Audit COUP-07).
     *
     * <p>When looking toward the shelf's front face (against {@code facing}), column {@code 0} is
     * on the viewer's left and column {@code 2} is on the viewer's right, matching
     * {@code facing.getCounterClockWise()}.</p>
     */
    public static double[] slotWorldCoordinates(BlockPos shelfPos, Direction facing, int slot) {
        int s = Math.max(0, Math.min(5, slot));
        Direction dir = (facing != null && facing.getAxis().isHorizontal()) ? facing : Direction.SOUTH;
        Direction viewerRight = dir.getCounterClockWise();
        int column = s % 3;
        int row = s / 3;
        double sideOffset = (column - 1) * SHELF_SLOT_COLUMN_SPACING;
        double x = shelfPos.getX() + 0.5D + dir.getStepX() * SHELF_FRONT_FACE_OFFSET + viewerRight.getStepX() * sideOffset;
        double y = shelfPos.getY() + (row == 0 ? SHELF_TOP_ROW_Y : SHELF_BOTTOM_ROW_Y);
        double z = shelfPos.getZ() + 0.5D + dir.getStepZ() * SHELF_FRONT_FACE_OFFSET + viewerRight.getStepZ() * sideOffset;
        return new double[] { x, y, z };
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE.isEmpty()) return;
            Iterator<Burst> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Burst b = it.next();
                if (b.level.getServer() != server) continue;
                RandomSource rnd = b.level.getRandom();
                b.level.sendParticles(b.type,
                    b.x + (rnd.nextDouble() - 0.5) * 0.35,
                    b.y + (rnd.nextDouble() - 0.5) * 0.25,
                    b.z + (rnd.nextDouble() - 0.5) * 0.35,
                    2, 0.02, 0.05, 0.02, 0.02);
                if (--b.ticksLeft <= 0) {
                    it.remove();
                }
            }
        });
    }

    /** Book position inside its shelf: slot 0..5 maps to the facing-rotated 3x2 chiseled-shelf grid. */
    private static void burst(Level level, BlockPos shelfPos, int slot, ParticleOptions type, int ticks) {
        if (!(level instanceof ServerLevel serverLevel) || shelfPos == null) return;
        BlockState state = serverLevel.getBlockState(shelfPos);
        Direction facing = state.hasProperty(ChiseledBookShelfBlock.FACING)
            ? state.getValue(ChiseledBookShelfBlock.FACING)
            : Direction.SOUTH;
        double[] coords = slotWorldCoordinates(shelfPos, facing, slot);
        if (ACTIVE.size() >= MAX_ACTIVE_BURSTS) ACTIVE.removeFirst();
        ACTIVE.add(new Burst(serverLevel, coords[0], coords[1], coords[2], type, ticks));
    }

    /** Locked just now, or an enchantment's durability fully depleted at this slot. */
    public static void portalAt(Level level, BlockPos shelfPos, int slot) {
        burst(level, shelfPos, slot, ParticleTypes.PORTAL, PORTAL_TICKS);
    }

    /** Charge completed, one charge restored, or a lock released by hand. */
    public static void endRodAt(Level level, BlockPos shelfPos, int slot) {
        burst(level, shelfPos, slot, ParticleTypes.END_ROD, END_ROD_TICKS);
    }

    /**
     * Charge-restored cue from the regeneration settle loop: fires only when a tome
     * actually gained charge since its pre-settle data was sampled.
     */
    public static void onRegenSettled(ServerLevel world, BlockPos pos, int slot,
                                      ItemStack before, ItemStack after) {
        if (before == null || after == null) return;
        int gained = TomeWearData.getWearUsed(before) - TomeWearData.getWearUsed(after);
        boolean lockExpired = TomeLockData.getLock(before) != null
            && TomeLockData.getLock(after) == null;
        if (gained > 0 || lockExpired) {
            endRodAt(world, pos, slot);
        }
    }
}
