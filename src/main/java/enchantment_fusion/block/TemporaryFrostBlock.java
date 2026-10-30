package enchantment_fusion.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Short-lived, frosted-ice-looking effect block. Unlike vanilla IceBlock it never creates
 * water when broken or melted. Age and the scheduled tick are saved with the chunk,
 * so unloading/restarting cannot turn a cage into water.
 */
public final class TemporaryFrostBlock extends HalfTransparentBlock {
    public static final MapCodec<TemporaryFrostBlock> CODEC = simpleCodec(TemporaryFrostBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    public TemporaryFrostBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends HalfTransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, Mth.nextInt(level.getRandom(), 24, 48));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < 3) {
            level.setBlockAndUpdate(pos, state.setValue(AGE, age + 1));
            level.scheduleTick(pos, this, Mth.nextInt(random, 20, 36));
        } else {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, 4, 0.25D, 0.25D, 0.25D, 0.02D);
        }
    }

}
