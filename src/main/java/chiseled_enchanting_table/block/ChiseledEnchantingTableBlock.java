package chiseled_enchanting_table.block;
import chiseled_enchanting_table.utils.EnchantmentFinder;
import chiseled_enchanting_table.utils.BlockPosStream;

import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
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
    // 26.3 removed per-block codecs; do not restore the 26.1.2 codec override.
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public static final List<BlockPos> POWER_PROVIDER_OFFSETS = BlockPos.betweenClosedStream(-3, -2, -3, 3, 2, 3)
        .filter(pos -> !(pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0))
        .map(BlockPos::immutable)
        .toList();

    private static boolean hasOccupiedChiseledSlot(BlockState state) {
        for (var prop : net.minecraft.world.level.block.ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
            if (state.hasProperty(prop) && state.getValue(prop)) {
                return true;
            }
        }
        return false;
    }

    public ChiseledEnchantingTableBlock(BlockBehaviour.Properties settings) {
        super(settings);
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
            boolean vanillaRing = Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())) <= 2
                && offset.getY() >= 0 && offset.getY() <= 1;
            if (random.nextInt(vanillaRing ? 16 : 24) != 0) {
                continue;
            }
            BlockPos shelfPos = pos.offset(offset);
            BlockState shelfState = level.getBlockState(shelfPos);
            boolean activeShelf = shelfState.is(net.minecraft.tags.BlockTags.ENCHANTMENT_POWER_PROVIDER)
                || (shelfState.is(Blocks.CHISELED_BOOKSHELF) && hasOccupiedChiseledSlot(shelfState));
            if (!activeShelf) {
                continue;
            }
            BlockPos betweenPos = pos.offset(
                Integer.signum(offset.getX()),
                net.minecraft.util.Mth.clamp(offset.getY(), 0, 1),
                Integer.signum(offset.getZ())
            );
            if (!betweenPos.equals(shelfPos) && !level.getBlockState(betweenPos).getCollisionShape(level, betweenPos).isEmpty()) {
                continue;
            }
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
        DyeColor dyeColor = stack.get(DataComponents.DYE);
        if (dyeColor != null
            && level.getBlockEntity(pos) instanceof ChiseledEnchantingTableBlockEntity tableBlockEntity) {
            if (!level.isClientSide()) {
                int newColor = dyeColor.getTextureDiffuseColor();
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

        AvailableEnchantmentPayload openingPayload = new AvailableEnchantmentPayload(
            availableEnchantments,
            shelfPower.xpDiscountPercent(),
            shelfPower.regularBookshelves(),
            shelfPower.totalChiseledEnchantedBooks(),
            tableBlockEntity.getOrInitEpiphanySeed(),
            shelfPower.storedMasterLevels()
        );

        return new ExtendedMenuProvider<AvailableEnchantmentPayload>() {
            @Override
            public AvailableEnchantmentPayload getScreenOpeningData(ServerPlayer player) {
                if (shelfPower.chiseledBookshelvesWithBooks() > 0) {
                    Advancement.give(player, "unlock_new_enchant");
                }
                if (shelfPower.xpDiscountPercent() >= 22 || shelfPower.peakEffectiveXpDiscountPercent(level) >= 35) {
                    Advancement.give(player, "library_resonance");
                }
                if (shelfPower.regularBookshelves() >= ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves()) {
                    Advancement.give(player, "grand_library");
                }
                return openingPayload;
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
                    openingPayload,
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

        // Track 1 (Wooden Bookshelves): scale default baseline smoothly up to the mid-tier ceiling
        // ceil(maxLevel / 2.0) at +1 per 5 wooden bookshelves. Graduation tiers always require
        // Master Tomes stored in Chiseled Bookshelves.
        for (EnchantmentWithLevel base : this.enchantments_unlocked_by_default(level)) {
            mergeHighestLevel(highestLevels, withWoodenShelfBoost(level, base, shelfPower.regularBookshelves()));
        }

        // Track 2 (Chiseled Bookshelves): unlock each stored Master Tome 1:1 up to its stored book level.
        streamLibraryPositions(pos)
            .forEach(p -> {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof ChiseledBookShelfBlockEntity cbsbe) {
                    EnchantmentFinder.streamAllEnchantements(cbsbe, level)
                        .map(enchantment -> clampToMaxLevel(level, enchantment))
                        .forEach(enchantment -> mergeHighestLevel(highestLevels, enchantment));
                }
            });

        java.util.HashSet<EnchantmentWithLevel> unlocked = new java.util.HashSet<>();
        for (Map.Entry<Identifier, Integer> entry : highestLevels.entrySet()) {
            int top = Math.max(1, entry.getValue());
            for (int lvl = 1; lvl <= top; lvl++) {
                unlocked.add(new EnchantmentWithLevel(entry.getKey(), lvl));
            }
        }
        return unlocked;
    }

    public static final int MAX_XP_DISCOUNT_PERCENT =
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.MAX_TOTAL_XP_DISCOUNT_PERCENT;

    private static java.util.stream.Stream<BlockPos> streamLibraryPositions(BlockPos origin) {
        java.util.ArrayList<BlockPos> list = new java.util.ArrayList<>(195);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    list.add(origin.offset(dx, dy, dz));
                }
            }
        }
        return list.stream();
    }

    private record ShelfPower(
        int regularBookshelves,
        int chiseledBookshelvesWithBooks,
        int totalChiseledEnchantedBooks,
        double totalMasteryUnits,
        Map<Identifier, Integer> storedMasterLevels
    ) {
        int xpDiscountPercent() {
            return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.baseXpDiscountPercent(
                regularBookshelves,
                totalMasteryUnits
            );
        }

        int peakEffectiveXpDiscountPercent(Level level) {
            int peak = xpDiscountPercent();
            for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
                int maxLvl = maxLevel(level, entry.getKey());
                peak = Math.max(
                    peak,
                    chiseled_enchanting_table.mechanics.resonance.LibraryResonance.effectiveXpDiscountPercent(
                        regularBookshelves,
                        totalMasteryUnits,
                        entry.getValue(),
                        maxLvl
                    )
                );
            }
            return peak;
        }
    }

    private static ShelfPower calculateShelfPower(Level level, BlockPos tablePos) {
        int regularBookshelves = 0;
        int chiseledBookshelvesWithBooks = 0;
        int totalChiseledEnchantedBooks = 0;
        Map<Identifier, Integer> storedMasterLevels = new HashMap<>();
        for (BlockPos scannedPos : streamLibraryPositions(tablePos).toList()) {
            BlockState nearbyState = level.getBlockState(scannedPos);
            if (nearbyState.is(Blocks.BOOKSHELF)) {
                regularBookshelves++;
            } else if (nearbyState.is(Blocks.CHISELED_BOOKSHELF)) {
                BlockEntity be = level.getBlockEntity(scannedPos);
                if (be instanceof ChiseledBookShelfBlockEntity cbsbe) {
                    List<EnchantmentWithLevel> found = EnchantmentFinder.streamAllEnchantements(cbsbe, level).toList();
                    if (!found.isEmpty()) {
                        chiseledBookshelvesWithBooks++;
                        totalChiseledEnchantedBooks += found.size();
                        for (EnchantmentWithLevel e : found) {
                            EnchantmentWithLevel clamped = clampToMaxLevel(level, e);
                            mergeHighestLevel(storedMasterLevels, clamped);
                        }
                    }
                }
            }
        }
        double totalUnits = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.totalMasteryUnits(level, storedMasterLevels);
        return new ShelfPower(
            regularBookshelves,
            chiseledBookshelvesWithBooks,
            totalChiseledEnchantedBooks,
            totalUnits,
            Map.copyOf(storedMasterLevels)
        );
    }

    private static EnchantmentWithLevel withWoodenShelfBoost(Level level, EnchantmentWithLevel enchantment, int regularBookshelfPower) {
        int maxLvl = maxLevel(level, enchantment.enchantment_id());
        int woodenCap = maxLvl <= 1 ? 1 : (maxLvl + 1) / 2;
        int boostedLevel = enchantment.enchantment_level() + regularBookshelfPower / 5;
        return new EnchantmentWithLevel(enchantment.enchantment_id(), Math.max(1, Math.min(woodenCap, boostedLevel)));
    }

    private static EnchantmentWithLevel clampToMaxLevel(Level level, EnchantmentWithLevel enchantment) {
        int maxLvl = maxLevel(level, enchantment.enchantment_id());
        return new EnchantmentWithLevel(enchantment.enchantment_id(), Math.max(1, Math.min(maxLvl, enchantment.enchantment_level())));
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
