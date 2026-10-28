package enchantment_fusion.block;

import com.mojang.serialization.MapCodec;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking.OpenStationPayload;
import enchantment_fusion.registry.FusionBlockEntities;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Enchantment Fusion Table ("附魔融合台") workstation block.
 */
public final class EnchantmentFusionTableBlock extends BaseEntityBlock {
    public static final MapCodec<EnchantmentFusionTableBlock> CODEC = simpleCodec(EnchantmentFusionTableBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public EnchantmentFusionTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantmentFusionTableBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        return level.isClientSide()
            ? createTickerHelper(type, FusionBlockEntities.ENCHANTMENT_FUSION_TABLE_BE, EnchantmentFusionTableBlockEntity::clientTick)
            : null;
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof EnchantmentFusionTableBlockEntity tableBe)) {
            return null;
        }
        return new ExtendedMenuProvider<OpenStationPayload>() {
            @Override
            public OpenStationPayload getScreenOpeningData(ServerPlayer serverPlayer) {
                return new OpenStationPayload(pos);
            }

            @Override
            public Component getDisplayName() {
                return tableBe.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player p) {
                return new EnchantmentFusionMenu(
                    syncId,
                    playerInventory,
                    tableBe,
                    ContainerLevelAccess.create(level, pos),
                    pos
                );
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MenuProvider provider = this.getMenuProvider(state, level, pos);
        if (provider != null) {
            player.openMenu(provider);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void affectNeighborsAfterRemoval(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        boolean moved
    ) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            Containers.dropContents(level, pos, container);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;
            double y = pos.getY() + 1.02D;
            double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0D, 0.05D, 0.0D);
        }
    }
}
