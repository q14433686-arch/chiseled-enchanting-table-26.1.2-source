package chiseled_enchanting_table.block;
import chiseled_enchanting_table.utils.EnchantmentFinder;
import chiseled_enchanting_table.utils.BlockPosStream;

import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ChiseledEnchantingTableBlock extends BaseEntityBlock {
    public static final MapCodec<ChiseledEnchantingTableBlock> CODEC = simpleCodec(ChiseledEnchantingTableBlock::new);
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public static final List<BlockPos> POWER_PROVIDER_OFFSETS = BlockPos.betweenClosedStream(-2, 0, -2, 2, 1, 2).filter(pos -> {
        return Math.abs(pos.getX()) == 2 || Math.abs(pos.getZ()) == 2;
    }).map(BlockPos::immutable).toList();

    public ChiseledEnchantingTableBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        for (BlockPos offset : POWER_PROVIDER_OFFSETS) {
            if (random.nextInt(16) != 0) {
                continue;
            }
            BlockPos shelfPos = pos.offset(offset);
            if (!level.getBlockState(shelfPos).is(Blocks.CHISELED_BOOKSHELF)) {
                continue;
            }
            BlockPos betweenPos = pos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
            if (!level.getBlockState(betweenPos).getCollisionShape(level, betweenPos).isEmpty()) {
                continue;
            }
            if (level.getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity cbsbe
                && EnchantmentFinder.streamAllEnchantements(cbsbe, level).findAny().isPresent()) {
                level.addParticle(
                    ParticleTypes.ENCHANT,
                    pos.getX() + 0.5D,
                    pos.getY() + 2.0D,
                    pos.getZ() + 0.5D,
                    offset.getX() + random.nextFloat() - 0.5D,
                    offset.getY() - random.nextFloat() - 1.0F,
                    offset.getZ() + random.nextFloat() - 0.5D
                );
            }
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChiseledEnchantingTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, EntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE, ChiseledEnchantingTableBlockEntity::tick) : null;
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (stack.getItem() instanceof DyeItem dyeItem
            && level.getBlockEntity(pos) instanceof ChiseledEnchantingTableBlockEntity tableBlockEntity) {
            if (!level.isClientSide()) {
                int newColor = dyeItem.getDyeColor().getTextureDiffuseColor();
                int originalColor = tableBlockEntity.colorOrDefault();
                tableBlockEntity.color = ARGB.average(newColor, originalColor) & 0x00FFFFFF;
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                tableBlockEntity.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
                Advancement.give(player, "color_table");
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MenuProvider provider = createChiseledEnchantingTableMenuProvider(state, level, pos);
        if (provider != null) {
            player.openMenu(provider);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    protected MenuProvider createChiseledEnchantingTableMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ChiseledEnchantingTableBlockEntity tableBlockEntity)) {
            return null;
        }
        Component title = tableBlockEntity.getDisplayName();
        ShelfPower shelfPower = calculateShelfPower(level, pos);
        Set<EnchantmentWithLevel> unlockedEnchantments = this.get_unlocked_enchantements(state, level, pos);
        // The available set now carries only real enchantments. Transformations travel on their own
        // synced channel (TransformationDisplay) inside AvailableEnchantmentPayload, so they no longer
        // need to be (and must not be) mixed into the enchantment set.
        Set<EnchantmentWithLevel> availableEnchantments = Stream.concat(
            unlockedEnchantments.stream(), this.enchantments_unlocked_by_default(level).stream()
        ).collect(Collectors.toSet());

        return new ExtendedMenuProvider<AvailableEnchantmentPayload>() {
            @Override
            public AvailableEnchantmentPayload getScreenOpeningData(ServerPlayer player) {
                if (shelfPower.chiseledBookshelvesWithBooks() > 0) {
                    Advancement.give(player, "unlock_new_enchant");
                }
                return new AvailableEnchantmentPayload(availableEnchantments);
            }

            @Override
            public Component getDisplayName() {
                return title;
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
                return new ChiseledEnchantingTableScreenHandler(
                    syncId,
                    playerInventory,
                    new AvailableEnchantmentPayload(availableEnchantments),
                    ContainerLevelAccess.create(level, pos)
                );
            }
        };
    }

    /**
     * Default-unlocked enchantments, including any third-party enchantment whose mod tags it with
     * one of the JSON {@code tags} rules (e.g. {@code #minecraft:non_treasure} by default) — see
     * {@link ChiseledEnchantingTableData#defaultEnchantments(net.minecraft.core.HolderGetter)}.
     */
    public Set<EnchantmentWithLevel> enchantments_unlocked_by_default(Level level) {
        return ChiseledEnchantingTableData.defaultEnchantments(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
    }

    public Set<EnchantmentWithLevel> get_unlocked_enchantements(BlockState state, Level level, BlockPos pos) {
        Map<Identifier, Integer> highestLevels = new HashMap<>();
        ShelfPower shelfPower = calculateShelfPower(level, pos);

        // Regular bookshelves only raise the JSON default baseline.
        for (EnchantmentWithLevel base : this.enchantments_unlocked_by_default(level)) {
            mergeHighestLevel(highestLevels, withShelfBoost(level, base, shelfPower.regularBookshelves()));
        }

        // Chiseled bookshelves must depend on their actual stored books: each stored enchantment contributes
        // at least its book level, plus a small boost from surrounding regular bookshelves.
        BlockPosStream.streamPosInRadius(pos, 2)
            .filter(p -> !p.equals(pos))
            .forEach(p -> {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof ChiseledBookShelfBlockEntity cbsbe) {
                    EnchantmentFinder.streamAllEnchantements(cbsbe, level)
                        .map(enchantment -> withShelfBoost(level, enchantment, shelfPower.regularBookshelves()))
                        .forEach(enchantment -> mergeHighestLevel(highestLevels, enchantment));
                }
            });

        return highestLevels.entrySet().stream()
            .map(entry -> new EnchantmentWithLevel(entry.getKey(), entry.getValue()))
            .collect(Collectors.toSet());
    }

    private record ShelfPower(int regularBookshelves, int chiseledBookshelvesWithBooks) {}

    private static ShelfPower calculateShelfPower(Level level, BlockPos tablePos) {
        int regularBookshelves = 0;
        int chiseledBookshelvesWithBooks = 0;
        for (BlockPos scannedPos : BlockPosStream.streamPosInRadius(tablePos, 2).filter(pos -> !pos.equals(tablePos)).toList()) {
            BlockState nearbyState = level.getBlockState(scannedPos);
            if (nearbyState.is(Blocks.BOOKSHELF)) {
                regularBookshelves++;
            } else if (nearbyState.is(Blocks.CHISELED_BOOKSHELF)) {
                BlockEntity be = level.getBlockEntity(scannedPos);
                if (be instanceof ChiseledBookShelfBlockEntity cbsbe && EnchantmentFinder.streamAllEnchantements(cbsbe, level).findAny().isPresent()) {
                    chiseledBookshelvesWithBooks++;
                }
            }
        }
        return new ShelfPower(regularBookshelves, chiseledBookshelvesWithBooks);
    }

    private static EnchantmentWithLevel withShelfBoost(Level level, EnchantmentWithLevel enchantment, int regularBookshelfPower) {
        int boostedLevel = enchantment.enchantment_level() + regularBookshelfPower / 3;
        int maxLevel = maxLevel(level, enchantment.enchantment_id());
        return new EnchantmentWithLevel(enchantment.enchantment_id(), Math.max(1, Math.min(maxLevel, boostedLevel)));
    }

    private static int maxLevel(Level level, Identifier id) {
        try {
            Holder<Enchantment> holder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id));
            return Math.max(1, holder.value().getMaxLevel());
        } catch (Exception e) {
            return 1;
        }
    }

    private static void mergeHighestLevel(Map<Identifier, Integer> levels, EnchantmentWithLevel enchantment) {
        levels.merge(enchantment.enchantment_id(), enchantment.enchantment_level(), Math::max);
    }
}
