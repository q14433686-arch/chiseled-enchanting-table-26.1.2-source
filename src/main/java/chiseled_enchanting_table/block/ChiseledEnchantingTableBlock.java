package chiseled_enchanting_table.block;
import chiseled_enchanting_table.utils.EnchantmentFinder;

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
    public static final MapCodec<ChiseledEnchantingTableBlock> CODEC = simpleCodec(ChiseledEnchantingTableBlock::new);
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public static final net.minecraft.tags.TagKey<Block> REGULAR_BOOKSHELVES_TAG =
        chiseled_enchanting_table.registry.CetTags.Blocks.REGULAR_BOOKSHELVES;

    public static boolean isRegularBookshelf(BlockState state) {
        return !state.is(Blocks.CHISELED_BOOKSHELF)
            && (state.is(REGULAR_BOOKSHELVES_TAG) || state.is(net.minecraft.tags.BlockTags.ENCHANTMENT_POWER_PROVIDER));
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, List<BlockPos>> SCAN_OFFSETS_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int ANIMATE_PARTICLE_SAMPLE_BUDGET = 16;

    public static List<BlockPos> getScanOffsets(
        chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config cfg
    ) {
        int rx = cfg == null
            ? chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.DEFAULT_SCAN_HORIZONTAL_RADIUS
            : Math.max(1, Math.min(
                chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.MAX_SCAN_HORIZONTAL_RADIUS,
                cfg.horizontalScanRadius()
            ));
        int ry = cfg == null
            ? chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.DEFAULT_SCAN_VERTICAL_RADIUS
            : Math.max(0, Math.min(
                chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.MAX_SCAN_VERTICAL_RADIUS,
                cfg.verticalScanRadius()
            ));
        long key = (((long) rx) << 32) | (ry & 0xFFFFFFFFL);
        return SCAN_OFFSETS_CACHE.computeIfAbsent(key, ignored -> {
            int capacity = Math.max(8, (2 * rx + 1) * (2 * rx + 1) * (2 * ry + 1) - 1);
            java.util.ArrayList<BlockPos> offsets = new java.util.ArrayList<>(capacity);
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    for (int dz = -rx; dz <= rx; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        offsets.add(new BlockPos(dx, dy, dz));
                    }
                }
            }
            return List.copyOf(offsets);
        });
    }

    public static final List<BlockPos> POWER_PROVIDER_OFFSETS = getScanOffsets(null);

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
        var sg = level.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.shelfGatesConfig()
            : ChiseledEnchantingTableData.shelfGatesConfig();
        List<BlockPos> offsets = getScanOffsets(sg);
        if (offsets.isEmpty()) {
            return;
        }
        int samples = Math.min(ANIMATE_PARTICLE_SAMPLE_BUDGET, offsets.size());
        for (int i = 0; i < samples; i++) {
            BlockPos offset = offsets.get(random.nextInt(offsets.size()));
            boolean vanillaRing = Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())) <= 2
                && offset.getY() >= 0 && offset.getY() <= 1;
            if (!vanillaRing && random.nextInt(2) != 0) {
                continue;
            }
            BlockPos shelfPos = pos.offset(offset);
            if (!level.hasChunkAt(shelfPos)) {
                continue;
            }
            BlockState shelfState = level.getBlockState(shelfPos);
            boolean activeShelf = isRegularBookshelf(shelfState)
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
        return createTickerHelper(
            type,
            EntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE,
            level.isClientSide()
                ? ChiseledEnchantingTableBlockEntity::tick
                : ChiseledEnchantingTableBlockEntity::serverTick
        );
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

    /**
     * Audit: the Reopen-table payload resolves the provider through
     * {@code BlockState#getMenuProvider}, which delegates here. Without this override the
     * block only exposed its provider to direct right-clicks and Reopen silently did
     * nothing. Both paths now share the same provider factory.
     */
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return createChiseledEnchantingTableMenuProvider(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MenuProvider provider = createChiseledEnchantingTableMenuProvider(state, level, pos);
        if (provider != null) {
            player.openMenu(provider);
            if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                menu.pushRitualStateToViewer();
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void affectNeighborsAfterRemoval(
        BlockState state,
        net.minecraft.server.level.ServerLevel level,
        BlockPos pos,
        boolean moved
    ) {
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
            if (container instanceof ChiseledEnchantingTableBlockEntity tableBe) {
                tableBe.clearRitualState();
            }
            net.minecraft.world.Containers.dropContents(level, pos, container);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(
        BlockState state,
        Level level,
        BlockPos pos,
        net.minecraft.core.Direction dir
    ) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return createChiseledEnchantingTableMenuProvider(state, level, pos);
    }

    @Nullable
    protected MenuProvider createChiseledEnchantingTableMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ChiseledEnchantingTableBlockEntity tableBlockEntity)) {
            return null;
        }
        Component title = tableBlockEntity.getDisplayName();
        ShelfPower shelfPower = calculateShelfPower(level, pos);
        Set<EnchantmentWithLevel> unlockedEnchantments = this.getUnlockedEnchantmentsFromPower(level, shelfPower);
        Set<EnchantmentWithLevel> availableEnchantments = Stream.concat(
            unlockedEnchantments.stream(), this.enchantments_unlocked_by_default(level).stream()
        ).collect(Collectors.toSet());
        int epiphanySeed = tableBlockEntity.getOrInitEpiphanySeed();

        return new ExtendedMenuProvider<AvailableEnchantmentPayload>() {
            private AvailableEnchantmentPayload buildPayloadFor(Player targetPlayer) {
                return new AvailableEnchantmentPayload(
                    availableEnchantments,
                    shelfPower.xpDiscountPercent(),
                    shelfPower.regularBookshelves(),
                    shelfPower.totalChiseledEnchantedBooks(),
                    shelfPower.highTierChiseledBooks(),
                    epiphanySeed,
                    shelfPower.storedMasterLevels(),
                    shelfPower.crampedLibrary(),
                    shelfPower.chiseledBookshelvesWithBooks(),
                    shelfPower.emptyChiseledBookshelves(),
                    shelfPower.masterTomeCharges(),
                    shelfPower.totalMasteryUnits(),
                    targetPlayer
                ).withTablePos(pos);
            }

            @Override
            public AvailableEnchantmentPayload getScreenOpeningData(ServerPlayer player) {
                if (shelfPower.totalChiseledEnchantedBooks() > 0) {
                    Advancement.give(player, "unlock_new_enchant");
                }
                // Audit: trigger on actually REACHING the configured total cap, not on the
                // old hardcoded 22%/35% proxies that drifted from datapack-tunable values
                // (and from the "-40%" the advancement text advertised).
                int maxTotalDiscount = ChiseledEnchantingTableData.libraryResonanceConfig().maxTotalXpDiscountPercent();
                if (maxTotalDiscount > 0 && shelfPower.peakEffectiveXpDiscountPercent(level) >= maxTotalDiscount) {
                    Advancement.give(player, "library_resonance");
                }
                if (shelfPower.regularBookshelves() >= ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves()) {
                    Advancement.give(player, "grand_library");
                }
                return buildPayloadFor(player);
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
                    buildPayloadFor(player),
                    ContainerLevelAccess.create(level, pos)
                );
            }
        };
    }

    private static final List<Identifier> CLASSIC_103_DEFAULT_TREASURES = List.of(
        Identifier.withDefaultNamespace("frost_walker"),
        Identifier.withDefaultNamespace("binding_curse"),
        Identifier.withDefaultNamespace("soul_speed"),
        Identifier.withDefaultNamespace("swift_sneak"),
        Identifier.withDefaultNamespace("mending"),
        Identifier.withDefaultNamespace("vanishing_curse"),
        Identifier.withDefaultNamespace("wind_burst")
    );

    /**
     * Default-unlocked enchantments, including any third-party enchantment whose mod tags it with
     * one of the JSON {@code tags} rules (e.g. {@code #minecraft:non_treasure} by default) — see
     * {@link ChiseledEnchantingTableData#defaultEnchantments(net.minecraft.core.HolderGetter)}.
     */
    public Set<EnchantmentWithLevel> enchantments_unlocked_by_default(Level level) {
        var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Set<EnchantmentWithLevel> base = ChiseledEnchantingTableData.defaultEnchantments(lookup);
        if (!chiseled_enchanting_table.config.ModRuntimeConfig.effective().classic103Mode()) {
            return base;
        }
        java.util.HashSet<EnchantmentWithLevel> classic = new java.util.HashSet<>(base);
        for (Identifier id : CLASSIC_103_DEFAULT_TREASURES) {
            if (lookup.get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id)).isPresent()) {
                classic.add(new EnchantmentWithLevel(id, 1));
            }
        }
        return classic;
    }

    public Set<EnchantmentWithLevel> get_unlocked_enchantements(BlockState state, Level level, BlockPos pos) {
        ShelfPower shelfPower = calculateShelfPower(level, pos);
        return getUnlockedEnchantmentsFromPower(level, shelfPower);
    }

    private Set<EnchantmentWithLevel> getUnlockedEnchantmentsFromPower(Level level, ShelfPower shelfPower) {
        Map<Identifier, Integer> highestLevels = new HashMap<>();
        boolean classic103 = chiseled_enchanting_table.config.ModRuntimeConfig.effective().classic103Mode();

        if (classic103) {
            // 1.0.3 exact rule: both default enchantments and stored Chiseled Bookshelf books gain +W/3 up to maxLevel.
            for (EnchantmentWithLevel base : this.enchantments_unlocked_by_default(level)) {
                mergeHighestLevel(highestLevels, withClassic103ShelfBoost(level, base, shelfPower.regularBookshelves()));
            }
            for (Map.Entry<Identifier, Integer> entry : shelfPower.storedMasterLevels().entrySet()) {
                if (entry.getValue() > 0) {
                    mergeHighestLevel(
                        highestLevels,
                        withClassic103ShelfBoost(
                            level,
                            new EnchantmentWithLevel(entry.getKey(), entry.getValue()),
                            shelfPower.regularBookshelves()
                        )
                    );
                }
            }
        } else {
            // Track 1 (Wooden Bookshelves): scale default baseline smoothly along the blank cap curve.
            for (EnchantmentWithLevel base : this.enchantments_unlocked_by_default(level)) {
                mergeHighestLevel(highestLevels, withWoodenShelfBoost(level, base, shelfPower.regularBookshelves()));
            }

            // Track 3 (Chiseled Bookshelves): unlock each stored Master Tome 1:1 from the already-scanned map.
            for (Map.Entry<Identifier, Integer> entry : shelfPower.storedMasterLevels().entrySet()) {
                if (entry.getValue() > 0) {
                    mergeHighestLevel(highestLevels, new EnchantmentWithLevel(entry.getKey(), entry.getValue()));
                }
            }
        }

        java.util.HashSet<EnchantmentWithLevel> unlocked = new java.util.HashSet<>();
        for (Map.Entry<Identifier, Integer> entry : highestLevels.entrySet()) {
            int top = Math.max(1, entry.getValue());
            for (int lvl = 1; lvl <= top; lvl++) {
                unlocked.add(new EnchantmentWithLevel(entry.getKey(), lvl));
            }
        }
        return unlocked;
    }

    private static EnchantmentWithLevel withClassic103ShelfBoost(Level level, EnchantmentWithLevel enchantment, int regularBookshelfPower) {
        int boostedLevel = enchantment.enchantment_level() + Math.max(0, regularBookshelfPower) / 3;
        int maxLvl = maxLevel(level, enchantment.enchantment_id());
        return new EnchantmentWithLevel(enchantment.enchantment_id(), Math.max(1, Math.min(maxLvl, boostedLevel)));
    }

    public static final int MAX_XP_DISCOUNT_PERCENT =
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.MAX_TOTAL_XP_DISCOUNT_PERCENT;

    private static java.util.stream.Stream<BlockPos> streamLibraryPositions(BlockPos origin) {
        List<BlockPos> cachedOffsets = getScanOffsets(ChiseledEnchantingTableData.shelfGatesConfig());
        return cachedOffsets.stream().map(origin::offset);
    }

    public static chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.LiveShelfStatePayload computeLiveShelfState(
        Level level,
        BlockPos pos,
        int containerId
    ) {
        return computeLiveShelfState(level, pos, containerId, null);
    }

    public static chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.LiveShelfStatePayload computeLiveShelfState(
        Level level,
        BlockPos pos,
        int containerId,
        @Nullable Player viewer
    ) {
        BlockState state = level.getBlockState(pos);
        ShelfPower shelfPower = calculateShelfPower(level, pos);
        Set<EnchantmentWithLevel> unlocked = state.getBlock() instanceof ChiseledEnchantingTableBlock cetb
            ? cetb.getUnlockedEnchantmentsFromPower(level, shelfPower)
            : Set.of();

        var lrCfg = ChiseledEnchantingTableData.libraryResonanceConfig();
        int totalLibrary = shelfPower.totalLibraryBookshelves();
        int regenTier = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.regenTierIndex(lrCfg, totalLibrary);
        int nextGap = switch (regenTier) {
            case 0 -> Math.max(0, lrCfg.tomeRegenTier1Shelves() - totalLibrary);
            case 1 -> Math.max(0, lrCfg.tomeRegenTier2Shelves() - totalLibrary);
            default -> 0;
        };
        boolean regenActive = lrCfg.tomeRegenEnabled()
            && lrCfg.tomeConsumptionMode() == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR;
        int interval = regenActive
            ? chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.computeRegenIntervalTicks(lrCfg, totalLibrary)
            : -1;
        if (interval == Integer.MAX_VALUE) interval = -1;

        var sgCfg = ChiseledEnchantingTableData.shelfGatesConfig();
        boolean checkLos = sgCfg != null && sgCfg.requireLineOfSight();
        java.util.List<chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry> tomeBooks =
            new java.util.ArrayList<>();
        java.util.Set<BlockPos> qualifyingShelves = new java.util.HashSet<>();
        for (BlockPos offset : getScanOffsets(sgCfg)) {
            BlockPos scanPos = pos.offset(offset);
            if (!level.hasChunkAt(scanPos)) continue;
            if (checkLos && !hasClearLineOfSight(level, pos, scanPos)) continue;
            if (!(level.getBlockEntity(scanPos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
            boolean qualifies = false;
            for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
                ItemStack stack = shelf.getItem(slot);
                if (!stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) continue;
                qualifies = true;
                if (tomeBooks.size() >= chiseled_enchanting_table.menu.PayloadLimits.MAX_TOME_BOOK_ENTRIES) continue;
                java.util.List<EnchantmentWithLevel> ench = new java.util.ArrayList<>();
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(stack)
                    .entrySet()
                    .stream()
                    .filter(e -> e.getIntValue() > 0 && e.getKey().unwrapKey().isPresent())
                    .map(e -> new EnchantmentWithLevel(
                        e.getKey().unwrapKey().orElseThrow().identifier(), e.getIntValue()))
                    .sorted(java.util.Comparator.comparing((EnchantmentWithLevel e) -> e.enchantment_id().toString())
                        .thenComparingInt(EnchantmentWithLevel::enchantment_level))
                    .forEach(ench::add);
                int wear = chiseled_enchanting_table.mechanics.resonance.TomeWearData.getWearUsed(stack);
                boolean immune = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                    .hasEffectiveWearImmunity(lrCfg, stack);
                int nonCurseCount = chiseled_enchanting_table.mechanics.resonance.TomeWearData
                    .countNonCurseStoredEnchantments(stack);
                boolean purelyImmune = immune
                    && wear <= 0
                    && nonCurseCount <= lrCfg.wearImmuneFullWaiverMaxEnchants();
                int effectiveMax = chiseled_enchanting_table.mechanics.resonance.TomeWearData
                    .effectiveMaxUses(stack, lrCfg.maxTomeUses(), lrCfg.wearImmuneMaxGeneration());
                int remaining = purelyImmune ? effectiveMax
                    : chiseled_enchanting_table.mechanics.resonance.TomeWearData.remainingCharges(
                        stack, lrCfg.maxTomeUses(), lrCfg.wearImmuneMaxGeneration());
                boolean charging = regenActive && wear > 0;
                long progress = charging
                    ? chiseled_enchanting_table.mechanics.resonance.WorldRegeneration.regenProgressTicks(level, scanPos, slot)
                    : -1L;
                int etaNext = charging
                    ? chiseled_enchanting_table.mechanics.resonance.TomeLockData.ticksToNextCharge(
                        wear, Math.max(0L, progress), interval)
                    : -1;
                int etaFull = charging
                    ? chiseled_enchanting_table.mechanics.resonance.TomeLockData.ticksToFullCharge(
                        wear, Math.max(0L, progress), interval)
                    : -1;
                var lock = chiseled_enchanting_table.mechanics.resonance.TomeLockData.getLock(stack);
                boolean effectivelyLocked = lock != null && !lock.expiredAt(wear);
                int lockMode = !effectivelyLocked ? -1 : switch (lock.mode()) {
                    case NEXT_CHARGE -> 0;
                    case FULL_CHARGE -> 1;
                    case PERMANENT -> 2;
                };
                // Reference enchantment for the exhaustion preview: the one that last
                // consumed this tome when known (step-down only ever touches that one),
                // otherwise the book's first listed enchantment.
                String lastUsedEnchantId =
                    chiseled_enchanting_table.mechanics.resonance.TomeWearData.getLastUsedEnchant(stack);
                chiseled_enchanting_table.utils.EnchantmentWithLevel refEnchant = null;
                if (!lastUsedEnchantId.isEmpty()) {
                    for (var candidate : ench) {
                        if (candidate.enchantment_id().toString().equals(lastUsedEnchantId)) {
                            refEnchant = candidate;
                            break;
                        }
                    }
                }
                if (refEnchant == null && !ench.isEmpty()) refEnchant = ench.getFirst();
                int depleteOutcome = 0;
                String depleteEnchant = refEnchant == null ? "" : refEnchant.enchantment_id().toString();
                int depleteLevel = refEnchant == null ? 0 : refEnchant.enchantment_level();
                if (!immune && refEnchant != null) {
                    var consumptionMode = lrCfg.tomeConsumptionMode();
                    if (consumptionMode
                        == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR) {
                        boolean losesEnchant = lrCfg.tomeDepleteBehavior()
                                == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.DepleteBehavior.DEPLETED_TOME
                            || refEnchant.enchantment_level() <= 1;
                        depleteOutcome = losesEnchant ? (ench.size() <= 1 ? 2 : 3) : 1;
                    } else if (consumptionMode
                        == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.CONSUME) {
                        depleteOutcome = ench.size() <= 1 ? 5 : 4;
                    }
                }
                tomeBooks.add(new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry(
                    scanPos.immutable(),
                    slot,
                    stack.getHoverName().getString(),
                    ench,
                    wear,
                    remaining,
                    effectiveMax,
                    chiseled_enchanting_table.mechanics.resonance.TomeWearData.getGeneration(stack),
                    charging,
                    immune,
                    etaNext,
                    etaFull,
                    lockMode,
                    effectivelyLocked ? lock.owner().toString() : "",
                    effectivelyLocked ? lock.ownerName() : "",
                    effectivelyLocked ? lock.targetWear() : -1,
                    effectivelyLocked && viewer != null && lock.ownedBy(viewer.getUUID()),
                    chiseled_enchanting_table.mechanics.resonance.TomeWearData.getLastUsedEnchant(stack),
                    chiseled_enchanting_table.mechanics.resonance.TomeWearData.getLastUsedLevel(stack),
                    depleteOutcome,
                    depleteEnchant,
                    depleteLevel
                ));
            }
            if (qualifies) qualifyingShelves.add(scanPos.immutable());
        }
        tomeBooks.sort(java.util.Comparator
            .comparing((chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry e) -> e.shelf_pos().asLong())
            .thenComparingInt(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry::slot));

        boolean cleanseUnlocked = false;
        boolean tomeUnlocked = false;
        int sacrificeProgress = 0;
        if (viewer instanceof ServerPlayer serverViewer) {
            int qualifying = qualifyingShelves.size();
            if (qualifying >= lrCfg.tomePageRequiredShelves()
                && !Advancement.hasCompleted(serverViewer, Advancement.HOW_MUCH_LONGER)) {
                Advancement.give(serverViewer, Advancement.HOW_MUCH_LONGER);
            }
            int sacrificeTotal = Advancement.totalCriteria(serverViewer, Advancement.DISENCHANT_SACRIFICE);
            int sacrificeReq = sacrificeTotal > 0
                ? Math.min(Math.max(1, lrCfg.sacrificeRequired()), sacrificeTotal)
                : Math.max(1, lrCfg.sacrificeRequired());
            sacrificeProgress = Advancement.completedCriteria(serverViewer, Advancement.DISENCHANT_SACRIFICE);
            cleanseUnlocked = sacrificeProgress >= sacrificeReq;
            tomeUnlocked = Advancement.hasCompleted(serverViewer, Advancement.HOW_MUCH_LONGER);
        }

        return new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.LiveShelfStatePayload(
            containerId,
            unlocked,
            shelfPower.xpDiscountPercent(),
            shelfPower.regularBookshelves(),
            shelfPower.totalChiseledEnchantedBooks(),
            shelfPower.highTierChiseledBooks(),
            shelfPower.storedMasterLevels(),
            shelfPower.crampedLibrary(),
            shelfPower.chiseledBookshelvesWithBooks(),
            shelfPower.emptyChiseledBookshelves(),
            shelfPower.masterTomeCharges(),
            cleanseUnlocked,
            tomeUnlocked,
            sacrificeProgress,
            Math.max(1, lrCfg.sacrificeRequired()),
            qualifyingShelves.size(),
            Math.max(1, lrCfg.tomePageRequiredShelves()),
            totalLibrary,
            regenTier,
            nextGap,
            interval,
            tomeBooks,
            shelfPower.totalMasteryUnits()
        );
    }

    public record ShelfPower(
        int regularBookshelves,
        int chiseledBookshelvesWithBooks,
        int emptyChiseledBookshelves,
        int totalChiseledEnchantedBooks,
        int highTierChiseledBooks,
        double totalMasteryUnits,
        Map<Identifier, Integer> storedMasterLevels,
        boolean crampedLibrary,
        Map<EnchantmentWithLevel, Integer> masterTomeCharges
    ) {
        /**
         * Broad-sense bookshelf count ({@code 广义书架}):
         * {@code 普通书架 + 有书的雕纹书架（含普通书、附魔书、秘典及褪魔残卷）} — empty chiseled bookshelves excluded.
         */
        public int totalLibraryBookshelves() {
            return chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.computeBroadBookshelfCount(
                regularBookshelves,
                emptyChiseledBookshelves,
                chiseledBookshelvesWithBooks
            );
        }

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

    public static int effectiveShelvesAfterBreathingRoom(
        int rawRegularBookshelves,
        boolean ceilingBlocked,
        int openAdjacentBlocks,
        chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config cfg
    ) {
        return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression
            .effectiveShelvesAfterBreathingRoom(rawRegularBookshelves, ceilingBlocked, openAdjacentBlocks, cfg);
    }

    public static boolean hasShelfLineOfSight(Level level, BlockPos tablePos, BlockPos shelfPos) { return hasClearLineOfSight(level, tablePos, shelfPos); }
    public static boolean hasClearLineOfSight(Level level, BlockPos tablePos, BlockPos shelfPos) {
        int dx = shelfPos.getX() - tablePos.getX();
        int dy = shelfPos.getY() - tablePos.getY();
        int dz = shelfPos.getZ() - tablePos.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        for (int s = 1; s < steps; s++) {
            double t = (double) s / (double) steps;
            BlockPos mid = tablePos.offset(
                (int) Math.round(dx * t),
                (int) Math.round(dy * t),
                (int) Math.round(dz * t)
            );
            if (!mid.equals(tablePos) && !mid.equals(shelfPos)
                && (!level.hasChunkAt(mid) || !level.getBlockState(mid).getCollisionShape(level, mid).isEmpty())) {
                return false;
            }
        }
        return true;
    }

    public static int countOpenAdjacentBlocks(Level level, BlockPos tablePos) {
        int openAdjacent = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos adj = tablePos.offset(dx, dy, dz);
                    if (level.hasChunkAt(adj) && level.getBlockState(adj).getCollisionShape(level, adj).isEmpty()) {
                        openAdjacent++;
                    }
                }
            }
        }
        return openAdjacent;
    }

    public static boolean isTableCeilingBlocked(Level level, BlockPos tablePos) {
        BlockPos abovePos = tablePos.above();
        return !level.hasChunkAt(abovePos) || !level.getBlockState(abovePos).getCollisionShape(level, abovePos).isEmpty();
    }

    public static ShelfPower calculateShelfPower(Level level, BlockPos tablePos) {
        return calculateShelfPower(level, tablePos, true);
    }
    public static ShelfPower calculateShelfPower(Level level, BlockPos tablePos, boolean includeCharges) {
        var sgCfg = level.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.shelfGatesConfig()
            : ChiseledEnchantingTableData.shelfGatesConfig();
        var lrCfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.configForSide(level.isClientSide());
        int openAdjacent = countOpenAdjacentBlocks(level, tablePos);
        boolean ceilingBlocked = isTableCeilingBlocked(level, tablePos);
        boolean cramped = sgCfg.requireBreathingRoom() && (ceilingBlocked || openAdjacent < sgCfg.minOpenAdjacentBlocks());

        int rawRegularBookshelves = 0;
        int rawOccupiedChiseledBookshelves = 0;
        int rawEmptyChiseledBookshelves = 0;
        int totalChiseledEnchantedBooks = 0;
        double slotCappedMasteryUnits = 0.0;
        Map<Identifier, Integer> storedMasterLevels = new HashMap<>();
        for (BlockPos scannedPos : getScanOffsets(sgCfg).stream().map(tablePos::offset).toList()) {
            if (!level.hasChunkAt(scannedPos)) {
                continue;
            }
            if (sgCfg.requireLineOfSight() && !hasClearLineOfSight(level, tablePos, scannedPos)) {
                continue;
            }
            BlockState nearbyState = level.getBlockState(scannedPos);
            if (isRegularBookshelf(nearbyState)) {
                if (chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                    .classifyBookshelf(true, false, false, false).isRegularBookshelf()) {
                    rawRegularBookshelves++;
                }
            } else if (nearbyState.is(Blocks.CHISELED_BOOKSHELF)) {
                BlockEntity be = level.getBlockEntity(scannedPos);
                if (be instanceof ChiseledBookShelfBlockEntity cbsbe) {
                    boolean anyBookInShelf = false;
                    boolean anyEnchantedInShelf = false;
                    for (int slot = 0; slot < cbsbe.getContainerSize(); slot++) {
                        ItemStack slotBook = cbsbe.getItem(slot);
                        if (!slotBook.isEmpty()) {
                            anyBookInShelf = true;
                        }
                        List<EnchantmentWithLevel> slotEnchants = EnchantmentFinder.extractUnlockedSlotEnchantments(slotBook);
                        if (!slotEnchants.isEmpty()) {
                            anyEnchantedInShelf = true;
                            totalChiseledEnchantedBooks += slotEnchants.size();
                            Map<Identifier, Integer> slotLevels = new HashMap<>(slotEnchants.size() * 2);
                            for (EnchantmentWithLevel e : slotEnchants) {
                                EnchantmentWithLevel clamped = clampToMaxLevel(level, e);
                                mergeHighestLevel(storedMasterLevels, clamped);
                                mergeHighestLevel(slotLevels, clamped);
                            }
                            slotCappedMasteryUnits += chiseled_enchanting_table.mechanics.resonance.LibraryResonance
                                .capSingleSlotMasteryUnits(
                                    lrCfg,
                                    chiseled_enchanting_table.mechanics.resonance.LibraryResonance
                                        .totalMasteryUnits(lrCfg, level, slotLevels)
                                );
                        }
                    }
                    var kind = chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                        .classifyBookshelf(false, true, anyBookInShelf, anyEnchantedInShelf);
                    if (kind == chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.ShelfTaxonomyKind.EMPTY_CHISELED_BOOKSHELF) {
                        rawEmptyChiseledBookshelves++;
                    } else if (kind.isBroadBookshelf()) {
                        rawOccupiedChiseledBookshelves++;
                    }
                } else {
                    boolean occupied = hasOccupiedChiseledSlot(nearbyState);
                    var kind = chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                        .classifyBookshelf(false, true, occupied, false);
                    if (kind == chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.ShelfTaxonomyKind.EMPTY_CHISELED_BOOKSHELF) {
                        rawEmptyChiseledBookshelves++;
                    } else if (kind.isBroadBookshelf()) {
                        rawOccupiedChiseledBookshelves++;
                    }
                }
            }
        }
        int effectiveRegularBookshelves = effectiveShelvesAfterBreathingRoom(
            rawRegularBookshelves,
            ceilingBlocked,
            openAdjacent,
            sgCfg
        );
        int effectiveOccupiedChiseled = effectiveShelvesAfterBreathingRoom(
            rawOccupiedChiseledBookshelves,
            ceilingBlocked,
            openAdjacent,
            sgCfg
        );
        int effectiveEmptyChiseled = effectiveShelvesAfterBreathingRoom(
            rawEmptyChiseledBookshelves,
            ceilingBlocked,
            openAdjacent,
            sgCfg
        );
        Map<Identifier, Integer> immutableStored = Map.copyOf(storedMasterLevels);
        int highTierChiseledBooks = chiseled_enchanting_table.mechanics.resonance.BookshelfProgression
            .countQualifyingStoredMasterEnchantments(level, immutableStored);
        double dedupMasteryUnits = chiseled_enchanting_table.mechanics.resonance.LibraryResonance
            .totalMasteryUnits(lrCfg, level, immutableStored);
        double totalUnits = chiseled_enchanting_table.mechanics.resonance.LibraryResonance
            .effectiveTotalMasteryUnits(lrCfg, dedupMasteryUnits, slotCappedMasteryUnits);
        Map<EnchantmentWithLevel, Integer> masterTomeCharges = !includeCharges ? Map.of() : chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
            .snapshotLiveMasterCharges(
                level,
                tablePos,
                sgCfg,
                lrCfg,
                immutableStored
            );
        return new ShelfPower(
            effectiveRegularBookshelves,
            effectiveOccupiedChiseled,
            effectiveEmptyChiseled,
            totalChiseledEnchantedBooks,
            highTierChiseledBooks,
            totalUnits,
            immutableStored,
            cramped,
            masterTomeCharges
        );
    }

    private static EnchantmentWithLevel withWoodenShelfBoost(Level level, EnchantmentWithLevel enchantment, int regularBookshelfPower) {
        int maxLvl = maxLevel(level, enchantment.enchantment_id());
        int unlockedCap = chiseled_enchanting_table.mechanics.resonance.BookshelfProgression
            .maxBlankWoodenTier(
                ChiseledEnchantingTableData.shelfGatesConfig(),
                enchantment.enchantment_id(),
                maxLvl,
                regularBookshelfPower
            );
        return new EnchantmentWithLevel(enchantment.enchantment_id(), Math.max(enchantment.enchantment_level(), unlockedCap));
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
