package chiseled_enchanting_table.mechanics.ritual;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Pure deterministic mechanics &amp; world helpers for:
 * <ol>
 *   <li><b>Deep-Resonance Inscription Ritual Waiting &amp; Bounded Persistent Queue Accumulation</b>:
 *       active if and only if {@code nearbyBookshelves >= median(tier1, tier2, maxDirect)}
 *       OR {@code totalMasterTomeEnchantments >= 10}; single step bounded by {@code <= 5 min}
 *       ({@code 6,000 ticks}) with bounded {@code long} accumulation across multiple queued
 *       enchantments.</li>
 *   <li><b>Dual Skip Options</b> (enabled only when at least one Master Tome exists in scan range):
 *       <ul>
 *         <li><i>Sacrifice XP Skip</i>: consumes {@code 1..15} player XP levels scaled smoothly
 *             from remaining wait time, with {@code 0%} degradation risk.</li>
 *         <li><i>Force Rush Skip (No XP Sacrifice)</i>: live remaining-time probability up to
 *             {@code 50%} normally or {@code 70%} when enchanting from an {@code Infinity}-bearing
 *             Master Tome; on backfire, degrades the lowest-priority Master Tome (lowest relative
 *             level, lowest enchantment count, lowest rarity first) into {@code depleted_tome}
 *             ({@code 褪魔残卷}) and awards {@code haste_makes_waste} (「心急吃不了热豆腐」).</li>
 *       </ul>
 *   </li>
 *   <li><b>Overloaded {@code Infinity} Master Tome Transcription Slip («抄错»)</b>:
 *       when any Master Tome in nearby Chiseled Bookshelves carries {@code minecraft:infinity}
 *       AND {@code > 3} other enchantments ({@code totalEnchants >= 5}), transcription slip
 *       probability scales across single-tome enchantment count AND detected shelf count, capped
 *       strictly at {@code <= 20%}, downgrading the applied enchantment to a lower tier.</li>
 *   <li><b>Rarity &amp; Universality Weighted Treasure Epiphany Selection</b>:
 *       weights Treasure Epiphany candidates by inverse universality ({@code supportedItems.size()})
 *       and enchantment rarity weight.</li>
 * </ol>
 */
public final class InscriptionRitualMechanics {
    public static final int TICKS_PER_GAME_DAY = 24_000;
    public static final int SECONDS_PER_GAME_DAY = 1_200;

    /** Maximum wait ticks for ANY single enchantment step: 5 real minutes = 300s = 6,000 ticks. */
    public static final int MAX_SINGLE_WAIT_TICKS = 20 * 300;
    /** Minimum surrounding Master Tome enchantments that activate ritual waiting below the shelf threshold. */
    public static final int MIN_TOME_ENCHANTS_FOR_WAIT = 10;
    /** Maximum direct XP levels sacrificed when skipping an active ritual via XP sacrifice. */
    public static final int MAX_SACRIFICE_XP_LEVELS = 15;
    /** Hard cap on non-sacrifice rush degradation chance normally (50%) vs with Infinity tome (70%). */
    public static final double MAX_RUSH_DEGRADE_NORMAL = 0.50;
    public static final double MAX_RUSH_DEGRADE_INFINITY = 0.70;
    /** Hard cap on overloaded Infinity Master Tome transcription slip («抄错») chance (20%). */
    public static final double MAX_TRANSCRIPTION_SLIP_CHANCE = 0.20;

    public static final Identifier MENDING_ID = Identifier.withDefaultNamespace("mending");
    public static final Identifier UNBREAKING_ID = Identifier.withDefaultNamespace("unbreaking");
    private static final Set<Identifier> HIGH_APPLICABILITY_IDS = Set.of(
        Identifier.withDefaultNamespace("protection"),
        Identifier.withDefaultNamespace("sharpness"),
        Identifier.withDefaultNamespace("efficiency"),
        Identifier.withDefaultNamespace("fortune"),
        Identifier.withDefaultNamespace("looting"),
        Identifier.withDefaultNamespace("power"),
        Identifier.withDefaultNamespace("silk_touch"),
        Identifier.withDefaultNamespace("wind_burst"),
        Identifier.withDefaultNamespace("breach"),
        Identifier.withDefaultNamespace("density")
    );

    private InscriptionRitualMechanics() {}

    /** Single queued enchantment inscription persisted on {@code ChiseledEnchantingTableBlockEntity}. */
    public record QueuedInscription(
        Identifier enchantmentId,
        int targetLevel,
        int previousLevel,
        boolean masterTomeUsed,
        boolean usedInfinityTome,
        int conflictRefundPoints,
        boolean consumedEpiphany
    ) {
        public String encode() {
            return enchantmentId.toString()
                + "|" + targetLevel
                + "|" + previousLevel
                + "|" + (masterTomeUsed ? 1 : 0)
                + "|" + (usedInfinityTome ? 1 : 0)
                + "|" + conflictRefundPoints
                + "|" + (consumedEpiphany ? 1 : 0);
        }

        @Nullable
        public static QueuedInscription decode(String token) {
            if (token == null || token.isBlank()) {
                return null;
            }
            String[] parts = token.split("\\|", -1);
            if (parts.length != 7 || token.length() > 1024) {
                return null;
            }
            try {
                Identifier id = Identifier.parse(parts[0].trim());
                int targetLvl = Math.max(1, Integer.parseInt(parts[1].trim()));
                int prevLvl = Math.max(0, Integer.parseInt(parts[2].trim()));
                boolean tomeUsed = "1".equals(parts[3].trim());
                boolean infTome = "1".equals(parts[4].trim());
                int refund = Math.max(0, Integer.parseInt(parts[5].trim()));
                boolean epip = "1".equals(parts[6].trim());
                if (targetLvl > 255 || prevLvl > 255 || Integer.parseInt(parts[1].trim()) < 1
                    || Integer.parseInt(parts[2].trim()) < 0 || Integer.parseInt(parts[5].trim()) < 0
                    || !Set.of("0", "1").contains(parts[3].trim())
                    || !Set.of("0", "1").contains(parts[4].trim())
                    || !Set.of("0", "1").contains(parts[6].trim())) return null;
                return new QueuedInscription(id, targetLvl, prevLvl, tomeUsed, infTome, refund, epip);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    public static String serializeQueue(List<QueuedInscription> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < queue.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(queue.get(i).encode());
        }
        return sb.toString();
    }

    public static List<QueuedInscription> deserializeQueue(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        if (raw.length() > 32_000) throw new IllegalArgumentException("Oversized persisted ritual");
        List<QueuedInscription> out = new ArrayList<>();
        long refund = 0;
        for (String part : raw.split(";", -1)) {
            if (out.size() >= 256) throw new IllegalArgumentException("Too many ritual steps");
            QueuedInscription parsed = QueuedInscription.decode(part);
            if (parsed == null) throw new IllegalArgumentException("Malformed ritual step");
            refund += parsed.conflictRefundPoints();
            if (refund > Integer.MAX_VALUE) throw new IllegalArgumentException("Ritual refund exceeds budget");
            out.add(parsed);
        }
        return out;
    }

    /** Snapshot of surrounding bookshelves &amp; Chiseled Bookshelf Master Tomes within scan range. */
    public record LibraryRitualScan(
        int totalDetectedBookshelves,
        boolean hasAnyMasterTome,
        int totalMasterTomeEnchantments,
        int maxOverloadedInfinityTomeEnchants
    ) {}

    /** Metadata for one candidate Master Tome when resolving non-sacrifice skip degradation. */
    public record TomeCandidate(
        BlockPos shelfPos,
        int slot,
        int enchantCount,
        double averageRelativeLevel,
        double averageRarityScore,
        boolean hasInfinity
    ) {
        /**
         * Lower score = more expendable = degraded FIRST («相对低等级、低附魔数、低稀有度最优先»).
         */
        public double preservationScore() {
            double countNorm = Math.min(1.5, Math.max(1, enchantCount) / 5.0);
            double relNorm = Mth.clamp(averageRelativeLevel, 0.05, 1.0);
            double rarityNorm = Mth.clamp(averageRarityScore, 0.05, 1.0);
            return 0.40 * countNorm + 0.35 * relNorm + 0.25 * rarityNorm;
        }
    }

    /** Exact median (middle tier value) of the 3 bookshelf gate thresholds. */
    public static int medianShelfGate(BookshelfProgression.Config cfg) {
        BookshelfProgression.Config c = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        return medianOfThree(c.tier1Bookshelves(), c.tier2Bookshelves(), c.maxDirectBookshelves());
    }

    public static int medianOfThree(int a, int b, int c) {
        return a + b + c - Math.min(a, Math.min(b, c)) - Math.max(a, Math.max(b, c));
    }

    /**
     * Strict iff activation gate: waiting exists IF AND ONLY IF nearby shelves {@code >= middleShelfTier}
     * OR surrounding Master Tome enchantments {@code >= 10}.
     */
    public static boolean shouldRequireRitualWait(
        int nearbyBookshelves,
        int middleShelfTier,
        int totalMasterTomeEnchantments
    ) {
        return Math.max(0, nearbyBookshelves) >= Math.max(1, middleShelfTier)
            || Math.max(0, totalMasterTomeEnchantments) >= MIN_TOME_ENCHANTS_FOR_WAIT;
    }

    /** Applicability factor reflecting enchantment universality &amp; keystone impact ({@code >= 1.0}). */
    public static double computeApplicabilityFactor(Identifier enchantmentId, int supportedItemCount, int anvilWeight) {
        double itemBreadth = 1.0 + 0.42 * Mth.clamp((supportedItemCount - 1) / 24.0, 0.0, 1.0);
        double rarityBoost = anvilWeight <= 1 ? 1.18 : (anvilWeight <= 2 ? 1.12 : (anvilWeight <= 5 ? 1.05 : 1.0));
        double keystone = 1.0;
        if (MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(enchantmentId) || MENDING_ID.equals(enchantmentId)) {
            keystone = 1.55;
        } else if (UNBREAKING_ID.equals(enchantmentId)) {
            keystone = 1.32;
        } else if (HIGH_APPLICABILITY_IDS.contains(enchantmentId)) {
            keystone = 1.20;
        }
        return Mth.clamp(itemBreadth * rarityBoost * keystone, 1.0, 2.35);
    }

    public static double computeApplicabilityFactor(Identifier enchantmentId, Holder<Enchantment> holder) {
        int supportedCount = holder.value().definition().supportedItems().size();
        int weight = holder.value().definition().weight();
        return computeApplicabilityFactor(enchantmentId, supportedCount, weight);
    }

    /**
     * Smooth saturating single-enchantment casting duration in ticks ({@code 0} when gate is inactive;
     * otherwise bounded strictly within {@code [100, 6000]} ticks = {@code [5s, 5m]}).
     */
    public static int computeSingleEnchantWaitTicks(
        int nearbyBookshelves,
        int middleShelfTier,
        int totalMasterTomeEnchantments,
        int targetLevel,
        int maxLevel,
        double applicabilityFactor
    ) {
        if (!shouldRequireRitualWait(nearbyBookshelves, middleShelfTier, totalMasterTomeEnchantments)) {
            return 0;
        }
        double relLevel = Mth.clamp((double) Math.max(1, targetLevel) / (double) Math.max(1, maxLevel), 0.10, 1.0);
        double shelfLoad = Math.max(0.0, nearbyBookshelves) / 42.0;
        double tomeLoad = Math.max(0.0, totalMasterTomeEnchantments) / 22.0;
        double intensity = (0.42 * shelfLoad + 0.58 * tomeLoad)
            * (0.35 + 0.65 * Math.pow(relLevel, 1.15))
            * Math.max(1.0, applicabilityFactor);
        double seconds = 300.0 * (1.0 - Math.exp(-0.62 * intensity));
        int ticks = (int) Math.round(seconds * 20.0);
        return Mth.clamp(ticks, 100, MAX_SINGLE_WAIT_TICKS);
    }

    /**
     * Direct XP level sacrifice cost ({@code 1..15} levels) derived smoothly from live remaining ticks.
     */
    public static int computeSacrificeXpLevels(long remainingTicks) {
        if (remainingTicks <= 0L) {
            return 0;
        }
        double remSeconds = remainingTicks / 20.0;
        int raw = (int) Math.ceil(MAX_SACRIFICE_XP_LEVELS * (1.0 - Math.exp(-remSeconds / 240.0)));
        return Mth.clamp(raw, 1, MAX_SACRIFICE_XP_LEVELS);
    }

    /**
     * Live Master Tome degradation probability when forcing a rush skip without sacrificing XP:
     * updates every tick from {@code remainingTicks}, strictly {@code <= 50%} normally and
     * {@code <= 70%} when enchanting using an {@code Infinity}-bearing Master Tome.
     */
    public static double computeRushDegradeChance(long remainingTicks, boolean usedInfinityMasterTome) {
        if (remainingTicks <= 0L) {
            return 0.0;
        }
        double cap = usedInfinityMasterTome ? MAX_RUSH_DEGRADE_INFINITY : MAX_RUSH_DEGRADE_NORMAL;
        double remSeconds = remainingTicks / 20.0;
        double raw = cap * (1.0 - Math.exp(-remSeconds / 210.0));
        return Mth.clamp(raw, cap * 0.02, cap);
    }

    /**
     * Overloaded {@code Infinity} Master Tome transcription slip («抄错») probability:
     * active ONLY when a tome in scan range holds {@code minecraft:infinity} AND {@code > 3} other
     * enchantments ({@code maxOverloadedTomeEnchants >= 5}); scales across both single-tome
     * enchantment count and detected shelf count, strictly capped at {@code 20%}.
     */
    public static double computeTranscriptionSlipChance(
        int maxOverloadedTomeEnchants,
        int totalDetectedBookshelves
    ) {
        // Must have Infinity (1) + strictly more than 3 other enchantments (>= 4) => total >= 5.
        if (maxOverloadedTomeEnchants < 5) {
            return 0.0;
        }
        double tomeComponent = switch (Math.min(8, maxOverloadedTomeEnchants)) {
            case 5 -> 0.04;
            case 6 -> 0.075;
            case 7 -> 0.11;
            default -> 0.14;
        };
        int s = Math.max(0, totalDetectedBookshelves);
        double shelfComponent;
        if (s < 15) {
            shelfComponent = 0.01;
        } else if (s < 30) {
            shelfComponent = 0.025;
        } else if (s < 38) {
            shelfComponent = 0.04;
        } else if (s < 42) {
            shelfComponent = 0.05;
        } else {
            shelfComponent = 0.06;
        }
        return Math.min(MAX_TRANSCRIPTION_SLIP_CHANCE, tomeComponent + shelfComponent);
    }

    /**
     * Resolves the post-transcription enchantment level given {@code slipChance} and uniform rolls
     * in {@code [0, 1)}. When {@code targetLevel > 1} and {@code slipRoll < slipChance}, downgrades
     * to a strictly lower valid tier {@code [1 .. targetLevel - 1]}.
     */
    public static int resolveTranscribedLevel(
        int targetLevel,
        int maxOverloadedTomeEnchants,
        double slipChance,
        double slipRoll,
        double severityRoll
    ) {
        int safeTarget = Math.max(1, targetLevel);
        if (safeTarget <= 1 || slipChance <= 0.0 || slipRoll >= slipChance) {
            return safeTarget;
        }
        int drop = (maxOverloadedTomeEnchants >= 7 && safeTarget >= 3 && severityRoll < 0.25) ? 2 : 1;
        return Math.max(1, safeTarget - drop);
    }

    /**
     * Computes deterministic selection weight for Treasure Epiphany candidates:
     * rarer enchantments (lower {@code definition.weight()}) AND broader universal enchantments
     * (larger {@code supportedItems.size()}, such as {@code Mending}) appear less frequently than
     * specialized niche treasures.
     */
    public static double computeEpiphanySelectionWeight(int enchantmentWeight, int supportedItemCount) {
        double rarityFactor = Math.pow(Mth.clamp(enchantmentWeight, 1, 16) / 2.0, 0.75);
        double universalityFactor = 1.0 / (1.0 + 0.085 * Math.max(0, supportedItemCount - 1));
        return Math.max(0.05, rarityFactor * universalityFactor);
    }

    public static double computeEpiphanySelectionWeight(Holder<Enchantment> holder) {
        int w = holder.value().definition().weight();
        int sup = holder.value().definition().supportedItems().size();
        return computeEpiphanySelectionWeight(w, sup);
    }

    /** Deterministic weighted roulette-wheel pick without replacement using an unsigned 64-bit hash. */
    public static int pickWeightedIndex(List<Double> weights, long unsignedHash) {
        if (weights == null || weights.isEmpty()) {
            return 0;
        }
        double total = 0.0;
        for (double w : weights) {
            total += Math.max(1e-6, w);
        }
        double u = ((unsignedHash >>> 11) * (1.0 / (1L << 53))) * total;
        double acc = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            acc += Math.max(1e-6, weights.get(i));
            if (u <= acc) {
                return i;
            }
        }
        return weights.size() - 1;
    }

    /** Selects the highest-priority degradation victim (minimum preservation score first). */
    @Nullable
    public static TomeCandidate selectDegradationVictim(List<TomeCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
            .min(Comparator
                .comparingDouble(TomeCandidate::preservationScore)
                .thenComparingInt(TomeCandidate::enchantCount)
                .thenComparingDouble(TomeCandidate::averageRelativeLevel)
                .thenComparingDouble(TomeCandidate::averageRarityScore)
                .thenComparing(c -> c.shelfPos().asLong())
                .thenComparingInt(TomeCandidate::slot))
            .orElse(null);
    }

    /** Scans all nearby bookshelves &amp; Chiseled Bookshelf Master Tomes around {@code tablePos}. */
    public static LibraryRitualScan scanLibraryRitualContext(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg
    ) {
        if (level == null || tablePos == null) {
            return new LibraryRitualScan(0, false, 0, 0);
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        int shelves = 0;
        boolean anyTome = false;
        int totalTomeEnchants = 0;
        int maxOverloadedInf = 0;

        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            BlockState state = level.getBlockState(p);
            if (ChiseledEnchantingTableBlock.isRegularBookshelf(state)) {
                shelves++;
                continue;
            }
            if (!state.is(Blocks.CHISELED_BOOKSHELF)) {
                continue;
            }
            // Broad bookshelves (广义书架) include regular bookshelves, empty chiseled bookshelves,
            // AND book-holding chiseled bookshelves (including Depleted Tomes); only slots holding
            // Enchanted Books count toward Master Tome resonance below.
            shelves++;
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                int count = enchants.size();
                if (count <= 0) continue;
                anyTome = true;
                totalTomeEnchants += count;
                boolean hasInf = MasterTomeWear.hasInfinityImmunity(book);
                // Strictly: possesses Infinity (1) AND strictly more than 3 OTHER enchantments (> 3 => count - 1 > 3 => count >= 5).
                if (hasInf && (count - 1) > 3 && count > maxOverloadedInf) {
                    maxOverloadedInf = count;
                }
            }
        }
        return new LibraryRitualScan(shelves, anyTome, totalTomeEnchants, maxOverloadedInf);
    }

    /** Returns {@code true} if any nearby Master Tome providing {@code enchantmentId >= minLevel} carries {@code minecraft:infinity}. */
    public static boolean doesMatchingMasterTomeHaveInfinity(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg,
        Identifier enchantmentId,
        int minLevel
    ) {
        if (MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(enchantmentId)) {
            return true;
        }
        if (level == null || tablePos == null || enchantmentId == null) {
            return false;
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty() || !MasterTomeWear.hasInfinityImmunity(book)) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                for (var entry : enchants.entrySet()) {
                    var keyOpt = entry.getKey().unwrapKey();
                    if (keyOpt.isPresent()
                        && enchantmentId.equals(keyOpt.get().identifier())
                        && entry.getIntValue() >= minLevel) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Degrades the lowest-priority Master Tome in scan range directly inside its Chiseled Bookshelf
     * slot into {@link BlockRegistry#DEPLETED_TOME} ({@code 褪魔残卷}), returning the degraded book's
     * prior stack copy (or {@code null} if no tome existed).
     */
    @Nullable
    public static ItemStack degradeLowestPriorityMasterTomeInWorld(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg
    ) {
        if (level == null || tablePos == null) {
            return null;
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        List<TomeCandidate> candidates = new ArrayList<>();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                if (enchants.isEmpty()) continue;
                int count = enchants.size();
                double relSum = 0.0;
                double raritySum = 0.0;
                for (var entry : enchants.entrySet()) {
                    int maxLvl = Math.max(1, entry.getKey().value().getMaxLevel());
                    relSum += Mth.clamp((double) entry.getIntValue() / maxLvl, 0.05, 1.0);
                    int w = Math.max(1, entry.getKey().value().definition().weight());
                    raritySum += 1.0 / Math.min(16.0, w);
                }
                candidates.add(new TomeCandidate(
                    p.immutable(),
                    slot,
                    count,
                    relSum / count,
                    raritySum / count,
                    MasterTomeWear.hasInfinityImmunity(book)
                ));
            }
        }
        TomeCandidate victim = selectDegradationVictim(candidates);
        if (victim == null) {
            return null;
        }
        if (!(level.getBlockEntity(victim.shelfPos()) instanceof ChiseledBookShelfBlockEntity targetShelf)) {
            return null;
        }
        ItemStack destroyedCopy = targetShelf.getItem(victim.slot()).copy();
        targetShelf.setItem(victim.slot(), new ItemStack(BlockRegistry.DEPLETED_TOME, 1));
        targetShelf.setChanged();
        BlockState shelfState = level.getBlockState(victim.shelfPos());
        if (shelfState.is(Blocks.CHISELED_BOOKSHELF)
            && victim.slot() >= 0
            && victim.slot() < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size()) {
            var occProp = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(victim.slot());
            if (shelfState.hasProperty(occProp) && !shelfState.getValue(occProp)) {
                level.setBlock(victim.shelfPos(), shelfState.setValue(occProp, true), Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(victim.shelfPos(), shelfState, shelfState, Block.UPDATE_ALL);
            }
        }
        level.playSound(
            null,
            victim.shelfPos(),
            SoundEvents.AMETHYST_CLUSTER_BREAK,
            SoundSource.BLOCKS,
            1.0F,
            0.75F
        );
        return destroyedCopy;
    }

    /** Formats seconds cleanly as game days ({@code 10.0d}) or minutes/seconds. */
    public static String formatRegenGameDays(int seconds) {
        double days = seconds / (double) SECONDS_PER_GAME_DAY;
        if (Math.abs(days - Math.round(days)) < 0.005) {
            return Math.round(days) + "d (" + seconds + "s)";
        }
        return String.format(java.util.Locale.ROOT, "%.1fd (%ds)", days, seconds);
    }
}
