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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Fills a placed chiseled bookshelf with 3–5 books (user-tunable range) and picks a facing whose
 * <b>front face is toward air</b>.
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
 *       {@code false} for these, so v2 rejected them and fell back to the default facing —
 *       which under template-pool rotation looked "random" across pieces.</li>
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
 *   <li>Shelf between two side blocks, front & back air: v2 → picks "back toward a side block".
 *       v3 → E/W sides now score +1 each ({@code +2} corridor bonus) on the N-S axis, whereas
 *       facing INTO the side block fails the hard filter. Shelf faces along the corridor axis.
 *       Fixed.</li>
 * </ul>
 */
public final class BookshelfBookFiller {
    private static final Component LORE = Component.literal("Chiseled Enchanting Table")
        .withStyle(style -> style.withColor(0x800080));

    private static final BooleanProperty[] SLOT_OCCUPIED = new BooleanProperty[] {
        BlockStateProperties.SLOT_0_OCCUPIED,
        BlockStateProperties.SLOT_1_OCCUPIED,
        BlockStateProperties.SLOT_2_OCCUPIED,
        BlockStateProperties.SLOT_3_OCCUPIED,
        BlockStateProperties.SLOT_4_OCCUPIED,
        BlockStateProperties.SLOT_5_OCCUPIED,
    };

    private BookshelfBookFiller() {}

    /**
     * Called from both filler paths (template-pool `finalizeProcessing` and stronghold-library
     * `postProcessBookshelf`). Handles state swap (if the block is still a plain bookshelf),
     * facing picking, and 6-slot item fill.
     */
    public static void applyToPlacedBookshelf(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState existing = level.getBlockState(pos);
        boolean freshlyReplaced = false;
        if (existing.is(Blocks.BOOKSHELF)) {
            existing = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
            freshlyReplaced = true;
        } else if (!existing.is(Blocks.CHISELED_BOOKSHELF)) {
            return; // Not something we should touch.
        }

        // Pick a facing whose front is toward air (see class javadoc). Uses the same RandomSource
        // as the fill so a fixed positional seed keeps the outcome deterministic across regens.
        Direction facing = pickFrontFacing(level, pos, random)
            .orElse(existing.getValue(BlockStateProperties.HORIZONTAL_FACING));
        BlockState chiseled = existing.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        if (freshlyReplaced || chiseled != existing) {
            level.setBlock(pos, chiseled, Block.UPDATE_CLIENTS);
        }

        BookshelfBookPool pool = ChiseledEnchantingTableData.bookshelfBookPool();
        HolderGetter<Enchantment> enchantmentLookup =
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        // Compute the book count and choose which slots receive a book.
        int minBooks = Math.max(0, Math.min(6, pool.minBooks()));
        int maxBooks = Math.max(minBooks, Math.min(6, pool.maxBooks()));
        int bookCount = minBooks == maxBooks
            ? minBooks
            : minBooks + random.nextInt(maxBooks - minBooks + 1);

        List<Integer> slotOrder = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) slotOrder.add(i);
        Collections.shuffle(slotOrder, new java.util.Random(random.nextLong()));
        List<Integer> occupiedSlots = slotOrder.subList(0, bookCount);

        ItemStack[] slots = new ItemStack[6];
        for (int i = 0; i < 6; i++) slots[i] = ItemStack.EMPTY;
        // Pre-aggregate enchant candidates once per shelf so we don't redo the O(N_injections)
        // walk per book.
        List<WeightedEntry> enchantCandidates = pool.enchantedBookProbability() > 0.0
            ? aggregateEnchantCandidates(pool, enchantmentLookup)
            : List.of();
        for (int occupiedSlot : occupiedSlots) {
            boolean enchanted = random.nextDouble() < pool.enchantedBookProbability();
            slots[occupiedSlot] = (enchanted && !enchantCandidates.isEmpty())
                ? pickWeightedEnchantedBook(random, enchantCandidates)
                : new ItemStack(Items.BOOK);
        }

        // Commit to the block entity + block state.
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ChiseledBookShelfBlockEntity cbsbe)) {
            ChiseledEnchantingTable.LOGGER.warn(
                "applyToPlacedBookshelf: expected ChiseledBookShelfBlockEntity at {}, got {}",
                pos, be == null ? "null" : be.getClass().getSimpleName()
            );
            return;
        }

        BlockState updated = chiseled;
        for (int i = 0; i < 6; i++) {
            cbsbe.setItem(i, slots[i]);
            updated = updated.setValue(SLOT_OCCUPIED[i], !slots[i].isEmpty());
        }
        if (updated != chiseled) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
        cbsbe.setChanged();
    }

    /**
     * See class javadoc, "Facing algorithm (v3)". Returns {@link Optional#empty()} to signal
     * "the shelf is fully buried, keep the caller's default facing".
     *
     * <p>{@code random} is accepted (rather than being removed from the signature) so a future
     * tweak can, e.g., break ties on truly-symmetrical arrangements randomly instead of by
     * fixed order. The current implementation is fully deterministic — same position, same
     * choice — which is the whole point of the "shelves in a row should all face the same way"
     * requirement.</p>
     */
    private static Optional<Direction> pickFrontFacing(ServerLevel level, BlockPos pos, RandomSource random) {
        // Fixed iteration order gives every shelf in the same corridor the same tie-break
        // outcome. Order matches the intent of the class javadoc: axes X then Z, negative then
        // positive within each axis (WEST, EAST, NORTH, SOUTH).
        Direction[] order = new Direction[] {
            Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH,
        };
        Direction best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Direction d : order) {
            int score = scoreFrontFacing(level, pos, d);
            if (score > bestScore) {
                bestScore = score;
                best = d;
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
    private static int scoreFrontFacing(ServerLevel level, BlockPos pos, Direction front) {
        // Hard filter: never point the book side into a non-air block (chest, wall, slab, ...).
        BlockPos frontPos = pos.relative(front);
        if (!level.getBlockState(frontPos).isAir()) return Integer.MIN_VALUE;

        int score = 0;

        // Open-axis bonus: both directions on this axis are air. Every candidate direction on
        // an "open" axis gets the same big bonus, so tie-break falls to the fixed direction
        // ordering; two shelves in the same open geometry independently arrive at the same
        // answer.
        BlockPos backPos = pos.relative(front.getOpposite());
        BlockState backState = level.getBlockState(backPos);
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

        // Perpendicular side scoring. Tie-break only (weights are all under +10). Two walled
        // sides (corridor along front-back axis) mildly beats one walled side.
        Direction.Axis perpAxis = front.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction sideA = Direction.get(Direction.AxisDirection.NEGATIVE, perpAxis);
        Direction sideB = sideA.getOpposite();
        boolean sideAWalled = !level.getBlockState(pos.relative(sideA)).isAir();
        boolean sideBWalled = !level.getBlockState(pos.relative(sideB)).isAir();
        if (sideAWalled && sideBWalled) {
            score += 3;
        } else if (sideAWalled || sideBWalled) {
            score += 1;
        }

        return score;
    }

    /**
     * Pre-aggregates the enchanted-book candidate pool for one shelf. Walks
     * {@code loot_injections.json} once, filtering to non-empty non-special entries, resolves each
     * enchantment id to a Holder (dropping unknown ones with a warn), and computes the composite
     * weight per entry.
     */
    private static List<WeightedEntry> aggregateEnchantCandidates(BookshelfBookPool pool, HolderGetter<Enchantment> lookup) {
        if (!"loot_injections".equals(pool.enchantPoolSource())) return List.of();
        List<WeightedEntry> out = new ArrayList<>();
        for (LootInjection injection : ChiseledEnchantingTableData.lootInjections()) {
            for (LootInjectionEntry entry : injection.entries()) {
                if (entry.empty() || entry.special() != null) continue;
                Optional<Holder.Reference<Enchantment>> holder = lookup.get(
                    ResourceKey.create(Registries.ENCHANTMENT, entry.enchantmentId())
                );
                if (holder.isEmpty()) continue;
                Enchantment enchantment = holder.get().value();
                double weight = 1.0;
                if (pool.useBaseWeight()) weight *= Math.max(1, entry.weight());
                if (pool.useVanillaWeightFactor()) weight *= Math.max(1, enchantment.getWeight());
                // Relative-level penalty: a book at its enchantment's max level always gets
                // maxLevelWeightRatio^1 penalty regardless of the numeric level; a level-1 book
                // gets no penalty; intermediate levels interpolate exponentially. Enchantments
                // with maxLevel == 1 (Mending, Silk Touch, Infinity, Curse of Vanishing, ...)
                // are never level-penalised. This matches the user's intent of "penalise
                // relative to how strong the book is for its own enchantment", not "penalise by
                // absolute numeric level" which is unfair to naturally-single-level enchantments.
                int maxLevel = Math.max(1, enchantment.getMaxLevel());
                int level = Math.max(1, Math.min(entry.level(), maxLevel));
                double relativeLevel = maxLevel == 1 ? 0.0 : (double)(level - 1) / (double)(maxLevel - 1);
                weight *= Math.pow(pool.maxLevelWeightRatio(), relativeLevel);
                if (weight > 0.0) {
                    out.add(new WeightedEntry(entry.enchantmentId(), holder.get(), entry.level(), weight));
                }
            }
        }
        return out;
    }

    private static ItemStack pickWeightedEnchantedBook(RandomSource random, List<WeightedEntry> candidates) {
        double total = 0.0;
        for (WeightedEntry e : candidates) total += e.weight;
        if (total <= 0.0) return new ItemStack(Items.BOOK);
        double roll = random.nextDouble() * total;
        double cum = 0.0;
        WeightedEntry picked = candidates.get(candidates.size() - 1);
        for (WeightedEntry e : candidates) {
            cum += e.weight;
            if (roll < cum) { picked = e; break; }
        }
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        int level = picked.level;
        Holder<Enchantment> h = picked.holder;
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(h, level));
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
