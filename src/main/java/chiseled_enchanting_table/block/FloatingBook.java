package chiseled_enchanting_table.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class FloatingBook {
    public float pageAngle;
    public float nextPageAngle;
    public float flipRandom;
    public float flipTurn;
    public float bookRotation;
    public float lastBookRotation;
    public float targetBookRotation;
    public float bookRotationVelocity;
    public float bookYOffset;

    public static void tick(Level level, BlockPos pos, BlockState state, FloatingBook book) {
        // TODO P2/P3: restore vanilla enchanting-table-style book animation after client renderer migration.
    }
}
