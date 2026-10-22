package chiseled_enchanting_table.mechanics.structure;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.BookshelfBookPool;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.LootInjection;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.LootInjectionEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Fills a placed chiseled bookshelf with 3–5 books (user-tunable range) and picks a facing whose
 * <b>front face is toward air</b>.
 *
 * <p><b>Thread-safety contract (read this before calling anything here):</b> this class is split
 * into a <i>pure</i> half and a <i>live-world</i> half, because two very different callers need
 * the same data:</p>
 * <ul>
 *   <li>{@link #computeFill} / {@link #toBlockEntityTag} are <b>pure</b>: they only read the
 *       datapack-driven book pool, the enchantment registry (via a {@link HolderGetter}) and the
 *       {@link RandomSource} they are given. They never touch a level, so they are safe to call
 *       from a worldgen worker thread — which is exactly what
 *       {@link BookshelfReplacerProcessor#finalizeProcessing} does when it writes the finished
 *       shelf (state + block-entity NBT) straight into the {@code StructureBlockInfo} list.</li>
 *   <li>{@link #applyToPlacedBookshelf} / {@link #applyFacingToPlacedBookshelf} write to a
 *       <i>live</i> {@link ServerLevel} and are therefore <b>server-thread only</b>; both refuse
 *       to run anywhere else (see {@link #requireServerThread}) instead of blocking on a chunk
 *       that the worldgen worker is itself generating.</li>
 * </ul>
 *
 * <p><b>Why this split exists:</b> the previous implementation called the live-world fill from
 * {@code StructureProcessor#finalizeProcessing}, i.e. from a worldgen worker thread, with the
 * real {@code ServerLevel} unwrapped from the {@code WorldGenRegion}. For every shelf that meant
 * {@code level.getBlockState(pos)} / {@code level.getBlockEntity(pos)} on the chunk that was
 * <i>currently being generated</i>, which is by definition not {@code FULL} in the chunk cache:
 * the worker blocked in {@code CompletableFuture.join()} while the server thread waited for the
 * same chunk future. Under Distant Horizons' FEATURES LOD generator (its own thread pool, outside
 * the vanilla scheduler) that was a deterministic hard deadlock. See
 * {@code docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md}.</p>
 *
 * <p><b>Fill algorithm (schema v2):</b></p>
 * <ol>
 *   <li>Pick {@code N ∈ [minBooks, maxBooks]} uniformly (default 3-5).</li>
 *   <li>Pick {@code N} distinct slot indices out of 6.</li>
 *   <li>For each of the {@code N} books, roll {@code enchantedBookProbability} (default 0.20)
 *       to decide plain-book vs enchanted-book.</li>
 *   <li>Enchanted-book selection weights the candidate set (aggregated from
 *       {@link ChiseledEnchantingTableData#lootInjections()} by matching level) as
 *       {@code baseWeight × vanillaWeightFactor × levelFalloff^level}. Vanilla weight factor
 *       reads {@link Enchantment#getWeight()} which encodes rarity (Sharpness=10 vs Mending=2 vs
 *       Silk Touch=1); {@code levelFalloff^level} decays exponentially with book level so a
 *       level-5 book is {@code levelFalloff^5} ≈ 1/32 as likely as a level-1 with everything else
 *       equal.</li>
 * </ol>
 *
 * <p><b>Facing algorithm (v3 — score-based, replaces the strict two-condition rule):</b> the v2
 * rule "back must be a sturdy face" rejected two visually-fine arrangements the user reported
 * bugs for:
 * <ul>
 *   <li>Back is a chest / half-slab / stairs / another shelf: {@code isFaceSturdy} returns
 *       {@code false} for these, so v2 rejected them and fell back to the default facing — which
 *       under template-pool rotation looked "random" across pieces.</li>
 *   <li>Corridor with side walls but nothing behind (chest room aisle, wide library isle): v2
 *       picked "back to a side wall" because that was the only axis with a sturdy back, even
 *       though the row of adjacent shelves face along the perpendicular axis.</li>
 * </ul>
 * <p>v3 replaces the two-value check with a small ranking:
 * <ol>
 *   <li><b>Hard filter:</b> the front direction's neighbour must be air. Facing INTO a chest,
 *       slab or wall never looks right, no matter how good the back is.</li>
 *   <li><b>Score:</b> {@code +2} if the back is a sturdy wall, {@code +1} if the back is a
 *       non-air block that isn't sturdy (chest / slab / other shelf — still a visually valid
 *       backing), {@code 0} if the back is air. Add {@code +1} for each of the two perpendicular
 *       sides that contains a non-air block (both sides walled = corridor arrangement,
 *       stronger signal than a single wall behind).</li>
 *   <li><b>Tie break:</b> fixed order — axes X then Z, negative direction then positive within
 *       each axis (WEST, EAST, NORTH, SOUTH). This means a whole row of shelves in the same
 *       corridor geometry independently picks the same facing, which is what "as regular as
 *       vanilla shelves" means in the user's original feedback.</li>
 *   <li><b>Fall back:</b> if every horizontal direction has a non-air front (fully buried
 *       shelf), keep the incoming state's facing rather than force-picking one.</li>
 * </ol>
 * <p>Concrete effect vs v2:
 * <ul>
 *   <li>Shelf with chest behind: v2 → fall back to default (rotated randomly by template).
 *       v3 → chest back gets +1, side/front scoring still identifies the open direction. Fixed.</li>
 *   <li>Shelf between two side blocks, front &amp; back air: v2 → picks "back toward a side block".
 *       v3 → E/W sides now score +1 each ({@code +2} corridor bonus) on the N-S axis, whereas
 *       facing INTO the side block fails the hard filter. Shelf faces along the corridor axis.
 *       Fixed.</li>
 * </ul>
 * <p>The facing pass only runs <b>after</b> the structure piece (and its neighbours) are actually
 * placed — see {@link #applyFacingToPlacedBookshelf}. During worldgen the piece's own walls are
 * still in the template, not in the world, so scoring there would see an empty room.</p>
 */
public final class BookshelfBookFiller {
    /** Block-entity id written into the structure-tag path, matching vanilla template NBT. */
    public static final String BOOKSHELF_BLOCK_ENTITY_ID = "minecraft:chiseled_bookshelf";

    /**
     * Lore line stamped on every generated enchanted book.
     *
     * <p>Translatable, not a literal: the component is stored in the item stack, and vanilla
     * resolves {@code translate} components per-client, so a book generated by a server shows the
     * line in each player's own language instead of English.</p>
     */
    private static final Component LORE = Component
        .translatable("book.chiseled_enchanting_table.lore")
        .withStyle(style -> style.withColor(0x800080));

    private static final BooleanProperty[] SLOT_OCCUPIED = new BooleanProperty[] {
        BlockStateProperties.SLOT_0_OCCUPIED,
        BlockStateProperties.SLOT_1_OCCUPIED,
        BlockStateProperties.SLOT_2_OCCUPIED,
        BlockStateProperties.SLOT_3_OCCUPIED,
        BlockStateProperties.SLOT_4_OCCUPIED,
        BlockStateProperties.SLOT_5_OCCUPIED,
    };

    private static final int SLOT_COUNT = SLOT_OCCUPIED.length;

    /** One-shot latch so a mis-threaded caller logs once instead of once per shelf. */
    private static final AtomicBoolean OFF_THREAD_WARNING_LOGGED = new AtomicBoolean();

    private BookshelfBookFiller() {}

    /**
     * Result of one fill roll: the chiseled-bookshelf {@link BlockState} with the six
     * {@code slot_*_occupied} properties already applied, plus the six slot contents.
     *
     * <p>{@code state} keeps whatever {@code facing} the caller passed in — deciding the facing is
     * a separate, main-thread-only step (see {@link #applyFacingToPlacedBookshelf}).</p>
     */
    public record Fill(BlockState state, NonNullList<ItemStack> items) {}

    // ---------------------------------------------------------------------------------------
    // Pure half — safe on any thread, including worldgen workers.
    // ---------------------------------------------------------------------------------------

    /**
     * Rolls the book contents for one shelf. Pure: reads only the datapack pool, the given
     * enchantment lookup and the given {@link RandomSource}; never touches a level.
     *
     * @param baseState a {@link Blocks#CHISELED_BOOKSHELF} state (its {@code facing} is preserved)
     */
    public static Fill computeFill(
        BlockState baseState,
        RandomSource random,
        HolderGetter<Enchantment> enchantmentLookup
    ) {
        BookshelfBookPool pool = ChiseledEnchantingTableData.bookshelfBookPool();

        // Compute the book count and choose which slots receive a book.
        int minBooks = Math.max(0, Math.min(SLOT_COUNT, pool.minBooks()));
        int maxBooks = Math.max(minBooks, Math.min(SLOT_COUNT, pool.maxBooks()));
        int bookCount = minBooks == maxBooks
            ? minBooks
            : minBooks + random.nextInt(maxBooks - minBooks + 1);

        List<Integer> slotOrder = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) slotOrder.add(i);
        Collections.shuffle(slotOrder, new java.util.Random(random.nextLong()));
        List<Integer> occupiedSlots = slotOrder.subList(0, bookCount);

        NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        // Pre-aggregate enchant candidates once per shelf so we don't redo the O(N_injections)
        // walk per book.
        List<WeightedEntry> enchantCandidates = pool.enchantedBookProbability() > 0.0
            ? aggregateEnchantCandidates(pool, enchantmentLookup)
            : List.of();
        for (int occupiedSlot : occupiedSlots) {
            boolean enchanted = random.nextDouble() < pool.enchantedBookProbability();
            items.set(occupiedSlot, (enchanted && !enchantCandidates.isEmpty())
                ? pickWeightedEnchantedBook(random, enchantCandidates)
                : new ItemStack(Items.BOOK));
        }

        BlockState state = baseState;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            state = state.setValue(SLOT_OCCUPIED[slot], !items.get(slot).isEmpty());
        }
        return new Fill(state, items);
    }

    /**
     * Encodes the slot contents exactly the way {@link ChiseledBookShelfBlockEntity} persists them
     * ({@code ContainerHelper.saveAllItems} → {@code Items: [{Slot, id, count, components}, ...]}),
     * so vanilla's {@code StructureTemplate.placeInWorld} can hand the tag to
     * {@code BlockEntity#loadWithComponents} when the shelf is actually placed.
     *
     * <p>Uses {@link TagValueOutput#createWithContext} so the item components (enchantments,
     * lore, …) are encoded through registry-aware ops — no datapack/registry lookup is required on
     * the calling thread beyond the {@code registries} argument itself.</p>
     */
    public static CompoundTag toBlockEntityTag(NonNullList<ItemStack> items, HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        ContainerHelper.saveAllItems(output, items, true);
        CompoundTag tag = output.buildResult();
        // Vanilla template NBT carries the block-entity id; loadWithComponents ignores it, but
        // keeping it makes the generated tag indistinguishable from a hand-built structure file.
        tag.putString("id", BOOKSHELF_BLOCK_ENTITY_ID);
        return tag;
    }

    // ---------------------------------------------------------------------------------------
    // Live-world half — server thread only.
    // ---------------------------------------------------------------------------------------

    /**
     * Main-thread enrichment path: turns a placed plain {@link Blocks#BOOKSHELF} (stronghold
     * libraries and friends) into a filled chiseled bookshelf, or re-fills an already chiseled one.
     *
     * <p><b>Server thread only.</b> Worldgen must never reach this method: see the class javadoc.
     * Callers in worldgen land ({@code finalizeProcessing}) use {@link #computeFill} +
     * {@link #toBlockEntityTag} instead and let vanilla place the finished block.</p>
     */
    public static void applyToPlacedBookshelf(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!requireServerThread(level, "applyToPlacedBookshelf")) return;

        BlockState existing = level.getBlockState(pos);
        boolean replacePlainBookshelf = false;
        if (existing.is(Blocks.BOOKSHELF)) {
            existing = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
            replacePlainBookshelf = true;
        } else if (!existing.is(Blocks.CHISELED_BOOKSHELF)) {
            return; // Not something we should touch.
        }

        // Pick a facing whose front is toward air (see class javadoc). Uses the same RandomSource
        // as the fill so a fixed positional seed keeps the outcome deterministic across regens.
        Direction facing = pickFrontFacing(level, pos)
            .orElse(existing.getValue(BlockStateProperties.HORIZONTAL_FACING));
        BlockState base = existing.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        Fill fill = computeFill(
            base,
            random,
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
        );
        if (replacePlainBookshelf || fill.state() != base) {
            level.setBlock(pos, fill.state(), Block.UPDATE_CLIENTS);
        }

        // Commit to the block entity. Items are written through getItems() rather than setItem():
        // setItem() re-validates against #minecraft:bookshelf_books and then rewrites the whole
        // block state per slot, which is both redundant (we already computed the state) and
        // stricter than the NBT-load path vanilla itself uses for structures.
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ChiseledBookShelfBlockEntity shelf)) {
            ChiseledEnchantingTable.LOGGER.warn(
                "applyToPlacedBookshelf: expected ChiseledBookShelfBlockEntity at {}, got {}",
                pos, blockEntity == null ? "null" : blockEntity.getClass().getSimpleName()
            );
            return;
        }
        NonNullList<ItemStack> target = shelf.getItems();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            target.set(slot, fill.items().get(slot));
        }
        shelf.setChanged();
    }

    /**
     * Main-thread facing refinement for a shelf whose books were already written during worldgen
     * (the {@code StructureBlockInfo} NBT path). Runs once the piece is fully placed, so the
     * neighbour scan sees the real geometry — including the walls that belong to the same piece.
     *
     * <p><b>Server thread only.</b> Does nothing when the shelf is gone, buried, or already faces
     * the best direction.</p>
     */
    public static void applyFacingToPlacedBookshelf(ServerLevel level, BlockPos pos) {
        if (!requireServerThread(level, "applyFacingToPlacedBookshelf")) return;

        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.CHISELED_BOOKSHELF)) return;

        Direction currentFacing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Optional<Direction> best = pickFrontFacing(level, pos);
        if (best.isEmpty() || best.get() == currentFacing) return;
        level.setBlock(pos, state.setValue(BlockStateProperties.HORIZONTAL_FACING, best.get()), Block.UPDATE_CLIENTS);
    }

    /**
     * Refuses to run off the server thread. The pre-fix code did exactly that from worldgen and
     * deadlocked the integrated server; a loud no-op is strictly better than a frozen game, and
     * keeps any future re-introduction of the pattern visible in the log instead of in a thread
     * dump.
     */
    private static boolean requireServerThread(ServerLevel level, String caller) {
        MinecraftServer server = level.getServer();
        if (server != null && server.isSameThread()) return true;
        if (OFF_THREAD_WARNING_LOGGED.compareAndSet(false, true)) {
            ChiseledEnchantingTable.LOGGER.error(
                "{} was called off the server thread ({}). Refusing to read/write the live level: "
                    + "worldgen must build the filled shelf through "
                    + "BookshelfReplacerProcessor#finalizeProcessing instead.",
                caller, Thread.currentThread().getName()
            );
        }
        return false;
    }

    /**
     * See class javadoc, "Facing algorithm (v3)". Returns {@link Optional#empty()} to signal
     * "the shelf is fully buried, keep the caller's default facing".
     *
     * <p>Fully deterministic — same position, same geometry, same choice — which is the whole
     * point of the "shelves in a row should all face the same way" requirement.</p>
     *
     * <p>Reads go through {@link #getLoadedBlockState}, which never loads or generates a chunk.
     * A neighbour in an unloaded chunk counts as "unknown": it fails the hard filter when it is
     * the front (we never rotate a shelf into a chunk we cannot see) and contributes no backing
     * or side score otherwise.</p>
     */
    private static Optional<Direction> pickFrontFacing(LevelReader level, BlockPos pos) {
        // Fixed iteration order gives every shelf in the same corridor the same tie-break
        // outcome. Order matches the intent of the class javadoc: axes X then Z, negative then
        // positive within each axis (WEST, EAST, NORTH, SOUTH).
        Direction[] order = new Direction[] {
            Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH,
        };
        Direction best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Direction direction : order) {
            int score = scoreFrontFacing(level, pos, direction);
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        // A score of Integer.MIN_VALUE means the hard filter rejected every direction (all four
        // horizontal neighbours are non-air — fully-buried shelf). Signal fallback by returning
        // empty. Any non-negative score wins.
        if (best == null || bestScore == Integer.MIN_VALUE) return Optional.empty();
        return Optional.of(best);
    }

    /**
     * Returns a score for using {@code front} as the shelf's book-side direction, or
     * {@link Integer#MIN_VALUE} if the hard filter (front neighbour must be air) fails.
     *
     * <p>Rubric (higher = better):
     * <ul>
     *   <li><b>Open-axis bonus (+100)</b>: if the front-back axis has air on <em>both</em>
     *       directions, huge bonus. This dominates every other signal and is the fix for Bug 2
     *       ("正背面都无方块遮挡且左右存在方块" → shelf must face along the open axis, not sideways
     *       toward the perpendicular block).</li>
     *   <li><b>Sturdy back +10</b> / <b>non-sturdy non-air back +6</b>: when no fully-open axis
     *       exists (typical corridor with wall behind), these decide the direction. The +6 branch
     *       is the fix for Bug 1 (chest / other shelf / slab behind: {@code isFaceSturdy} is
     *       false but the arrangement is still valid backing, so we don't fall through to the
     *       default facing).</li>
     *   <li><b>Perpendicular side scoring</b>: +3 for both sides walled ("corridor"), +1 for
     *       exactly one side walled. Used only as tie-break within the same back tier.</li>
     * </ul>
     * <p>Because the open-axis bonus (+100) is much larger than the best back score (+10) plus
     * the best side score (+3), an open axis <em>always</em> wins over a wall-behind on the
     * perpendicular axis. That's the ranking the user asked for.</p>
     */
    private static int scoreFrontFacing(LevelReader level, BlockPos pos, Direction front) {
        // Hard filter: never point the book side into a non-air block (chest, wall, slab, ...).
        // Unknown (chunk not loaded) counts as "blocked" — rotating blindly is the one thing the
        // player would notice.
        BlockPos frontPos = pos.relative(front);
        BlockState frontState = getLoadedBlockState(level, frontPos);
        if (frontState == null || !frontState.isAir()) return Integer.MIN_VALUE;

        int score = 0;

        // Open-axis bonus: both directions on this axis are air. Every candidate direction on
        // an "open" axis gets the same big bonus, so tie-break falls to the fixed direction
        // ordering; two shelves in the same open geometry independently arrive at the same
        // answer.
        BlockPos backPos = pos.relative(front.getOpposite());
        BlockState backState = getLoadedBlockState(level, backPos);
        if (backState != null) {
            if (backState.isAir()) {
                // The front is air (hard filter), and now we know the back is air too — this axis
                // is fully open.
                score += 100;
            } else if (backState.isFaceSturdy(level, backPos, front)) {
                score += 10;
            } else {
                // Non-air non-sturdy back (chest / slab / other shelf / stairs): still a valid
                // "backing" visually. Beats no-backing and loses to open-axis.
                score += 6;
            }
        }

        // Perpendicular side scoring. Tie-break only (weights are all under +10). Two walled
        // sides (corridor along front-back axis) mildly beats one walled side.
        Direction.Axis perpendicularAxis =
            front.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction sideA = Direction.get(Direction.AxisDirection.NEGATIVE, perpendicularAxis);
        Direction sideB = sideA.getOpposite();
        boolean sideAWalled = isNonAir(getLoadedBlockState(level, pos.relative(sideA)));
        boolean sideBWalled = isNonAir(getLoadedBlockState(level, pos.relative(sideB)));
        if (sideAWalled && sideBWalled) {
            score += 3;
        } else if (sideAWalled || sideBWalled) {
            score += 1;
        }

        return score;
    }

    private static boolean isNonAir(@Nullable BlockState state) {
        return state != null && !state.isAir();
    }

    /**
     * Reads a block state <b>without ever loading or generating a chunk</b>; {@code null} means
     * "not currently loaded, treat as unknown".
     *
     * <p>{@code ServerChunkCache#getChunkNow} is the vanilla accessor for exactly this: it is a
     * pure cache lookup (and returns {@code null} off the server thread, which the callers have
     * already excluded). Using {@code level.getBlockState(pos)} here would synchronously load the
     * neighbour chunk — the classic "loading a chunk from inside chunk generation" hazard.</p>
     */
    private static @Nullable BlockState getLoadedBlockState(LevelReader level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ())
        );
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    // ---------------------------------------------------------------------------------------
    // Weighted enchant selection
    // ---------------------------------------------------------------------------------------

    /**
     * Pre-aggregates the enchanted-book candidate pool for one shelf. Walks
     * {@code loot_injections.json} once, filtering to non-empty non-special entries, resolves each
     * enchantment id to a Holder (dropping unknown ones with a warn), and computes the composite
     * weight per entry.
     */
    private static List<WeightedEntry> aggregateEnchantCandidates(BookshelfBookPool pool, HolderGetter<Enchantment> lookup) {
        List<WeightedEntry> out = new ArrayList<>();
        if ("loot_injections".equals(pool.enchantPoolSource())) {
            for (LootInjection injection : ChiseledEnchantingTableData.lootInjections()) {
                for (LootInjectionEntry entry : injection.entries()) {
                    if (entry.empty() || entry.special() != null) continue;
                    appendWeightedCandidate(out, pool, lookup, entry.enchantmentId(), entry.level(), entry.weight());
                }
            }
        }
        for (List<ChiseledEnchantingTableData.WeightedEnchantEntry> set : pool.enchantSets()) {
            for (ChiseledEnchantingTableData.WeightedEnchantEntry entry : set) {
                appendWeightedCandidate(out, pool, lookup, entry.enchantmentId(), entry.level(), entry.weight());
            }
        }
        return out;
    }

    private static void appendWeightedCandidate(
        List<WeightedEntry> out,
        BookshelfBookPool pool,
        HolderGetter<Enchantment> lookup,
        net.minecraft.resources.Identifier enchantmentId,
        int rawLevel,
        int baseWeight
    ) {
        Optional<Holder.Reference<Enchantment>> holder = lookup.get(
            ResourceKey.create(Registries.ENCHANTMENT, enchantmentId)
        );
        if (holder.isEmpty()) return;
        Enchantment enchantment = holder.get().value();
        double weight = 1.0;
        if (pool.useBaseWeight()) weight *= Math.max(1, baseWeight);
        if (pool.useVanillaWeightFactor()) weight *= Math.max(1, enchantment.getWeight());
        int maxLevel = Math.max(1, enchantment.getMaxLevel());
        int level = Math.max(1, Math.min(rawLevel, maxLevel));
        double relativeLevel = maxLevel == 1 ? 0.0 : (double)(level - 1) / (double)(maxLevel - 1);
        weight *= Math.pow(pool.maxLevelWeightRatio(), relativeLevel);
        if (weight > 0.0) {
            out.add(new WeightedEntry(enchantmentId, holder.get(), level, weight));
        }
    }

    private static ItemStack pickWeightedEnchantedBook(RandomSource random, List<WeightedEntry> candidates) {
        double total = 0.0;
        for (WeightedEntry entry : candidates) total += entry.weight;
        if (total <= 0.0) return new ItemStack(Items.BOOK);
        double roll = random.nextDouble() * total;
        double cumulative = 0.0;
        WeightedEntry picked = candidates.get(candidates.size() - 1);
        for (WeightedEntry entry : candidates) {
            cumulative += entry.weight;
            if (roll < cumulative) { picked = entry; break; }
        }
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        int level = picked.level;
        Holder<Enchantment> holder = picked.holder;
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(holder, level));
        stack.set(DataComponents.LORE, new ItemLore(List.of(LORE)));
        return stack;
    }

    private record WeightedEntry(
        net.minecraft.resources.Identifier enchantmentId,
        Holder<Enchantment> holder,
        int level,
        double weight
    ) {}
}
