package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Server -> client cost table carrying ALL player-selectable material alternatives ({@code costOptions})
 * with full DataComponents preserved via {@link ItemStack#OPTIONAL_STREAM_CODEC}, stable string
 * {@link OptionType#token()} wire framing, {@link BookshelfProgression.Config}, {@link LibraryResonance.Config},
 * and {@link InscriptionRitualMechanics.Config}, with symmetric inbound/outbound {@link PayloadLimits} enforcement.
 */
public record EnchantCostTablePayload(
    boolean combineEnabled,
    boolean researchEnabled,
    boolean disenchantEnabled,
    boolean disenchantAllowCursePurge,
    double disenchantRefundScale,
    double disenchantExternalRefundScale,
    int epiphanyMinBookshelves,
    double epiphanyBaseChance,
    double epiphanyPerShelfChance,
    double epiphanyPerTomeChance,
    double epiphanyMaxChance,
    int epiphanyMaxSlotsCap,
    int epiphanyScorePerSlot,
    int epiphanyCycleTicks,
    int epiphanyFreezeDurationTicks,
    int epiphanyMaxFreezeTicks,
    int epiphanyFreezeXpLevels,
    List<ItemStack> epiphanyFreezeCosts,
    int epiphanyRerollXpLevels,
    List<ItemStack> epiphanyRerollCosts,
    Set<Identifier> explicitTreasureIds,
    BookshelfProgression.Config shelfGatesConfig,
    LibraryResonance.Config libraryResonanceConfig,
    InscriptionRitualMechanics.Config ritualConfig,
    List<CostEntry> costs,
    List<ChiseledEnchantingMenuPayloads.TransformationDisplay> transformations,
    /** False when wire-safety budgets truncated rows; clients must then fail closed on missing rows (audit R-01). */
    boolean costTableComplete
) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = ChiseledEnchantingTable.identifier("enchant_cost_table_" + EnchantingProtocol.REVISION);
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> TYPE =
        new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantCostTablePayload> CODEC =
        CustomPacketPayload.codec(EnchantCostTablePayload::write, EnchantCostTablePayload::read);

    public EnchantCostTablePayload(
        boolean combineEnabled,
        boolean researchEnabled,
        boolean disenchantEnabled,
        boolean disenchantAllowCursePurge,
        double disenchantRefundScale,
        double disenchantExternalRefundScale,
        int epiphanyMinBookshelves,
        double epiphanyBaseChance,
        double epiphanyPerShelfChance,
        double epiphanyPerTomeChance,
        double epiphanyMaxChance,
        int epiphanyMaxSlotsCap,
        int epiphanyScorePerSlot,
        int epiphanyCycleTicks,
        int epiphanyFreezeDurationTicks,
        int epiphanyMaxFreezeTicks,
        int epiphanyFreezeXpLevels,
        List<ItemStack> epiphanyFreezeCosts,
        int epiphanyRerollXpLevels,
        List<ItemStack> epiphanyRerollCosts,
        Set<Identifier> explicitTreasureIds,
        BookshelfProgression.Config shelfGatesConfig,
        LibraryResonance.Config libraryResonanceConfig,
        List<CostEntry> costs,
        List<ChiseledEnchantingMenuPayloads.TransformationDisplay> transformations
    ) {
        this(
            combineEnabled,
            researchEnabled,
            disenchantEnabled,
            disenchantAllowCursePurge,
            disenchantRefundScale,
            disenchantExternalRefundScale,
            epiphanyMinBookshelves,
            epiphanyBaseChance,
            epiphanyPerShelfChance,
            epiphanyPerTomeChance,
            epiphanyMaxChance,
            epiphanyMaxSlotsCap,
            epiphanyScorePerSlot,
            epiphanyCycleTicks,
            epiphanyFreezeDurationTicks,
            epiphanyMaxFreezeTicks,
            epiphanyFreezeXpLevels,
            epiphanyFreezeCosts,
            epiphanyRerollXpLevels,
            epiphanyRerollCosts,
            explicitTreasureIds,
            shelfGatesConfig,
            libraryResonanceConfig,
            InscriptionRitualMechanics.Config.DEFAULT,
            costs,
            transformations,
            true
        );
    }

    public record CostEntry(
        OptionType optionType,
        Identifier enchantmentId,
        int level,
        int xpLevelCost,
        List<List<ItemStack>> costOptions
    ) {}

    private static int countEntryStacks(List<List<ItemStack>> options) {
        int sum = 0;
        for (List<ItemStack> bundle : options) {
            sum += bundle.size();
        }
        return sum;
    }

    private static List<List<ItemStack>> sanitizeCostOptions(List<List<ItemStack>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(raw.size(), PayloadLimits.MAX_COST_OPTIONS);
        List<List<ItemStack>> out = new ArrayList<>(limit);
        boolean keptEmptyBundle = false;
        for (int i = 0; i < limit; i++) {
            List<ItemStack> b = raw.get(i);
            if (b == null) continue;
            if (b.isEmpty()) {
                // Audit H-02: an explicitly-empty bundle means "material-free by datapack
                // declaration" and must survive sync, or the client would price the row
                // differently from the server. Keep exactly one marker.
                if (!keptEmptyBundle) {
                    out.add(List.of());
                    keptEmptyBundle = true;
                }
                continue;
            }
            if (b.size() > PayloadLimits.MAX_BUNDLE_STACKS) {
                out.add(List.copyOf(b.subList(0, PayloadLimits.MAX_BUNDLE_STACKS)));
            } else {
                out.add(b);
            }
        }
        return List.copyOf(out);
    }

    private static boolean tryAppendEntry(
        List<CostEntry> entries,
        int[] totalStacks,
        int[] estimatedBytes,
        OptionType type,
        Identifier id,
        int level,
        int xpCost,
        List<List<ItemStack>> rawOptions
    ) {
        if (entries.size() >= PayloadLimits.MAX_COLLECTION_ENTRIES) {
            return false;
        }
        List<List<ItemStack>> opts = sanitizeCostOptions(rawOptions);
        int stackDelta = countEntryStacks(opts);
        if (totalStacks[0] + stackDelta > PayloadLimits.MAX_TOTAL_COST_STACKS) {
            return false;
        }
        // Audit R-01: counts alone don't bound encoded size; enforce a byte budget too.
        int byteDelta = PayloadLimits.ESTIMATED_BYTES_PER_ROW
            + stackDelta * PayloadLimits.ESTIMATED_BYTES_PER_STACK;
        if (estimatedBytes[0] + byteDelta > PayloadLimits.MAX_ESTIMATED_PAYLOAD_BYTES) {
            return false;
        }
        totalStacks[0] += stackDelta;
        estimatedBytes[0] += byteDelta;
        entries.add(new CostEntry(type, id, level, xpCost, opts));
        return true;
    }

    public static EnchantCostTablePayload build(Player player) {
        List<CostEntry> entries = new ArrayList<>();
        int[] totalStacks = new int[] { 0 };
        int[] estimatedBytes = new int[] { PayloadLimits.ESTIMATED_HEADER_BYTES };
        boolean truncated = false;

        // 1. Emit active transformation rows first so transformations are never truncated by over-leveled enchantments.
        for (Identifier transId : ChiseledEnchantingTableData.transformationIds()) {
            var opt = ChiseledEnchantingTableData.itemTransformation(transId);
            if (opt.isPresent()) {
                var trans = opt.get();
                if (!tryAppendEntry(
                    entries,
                    totalStacks,
                    estimatedBytes,
                    OptionType.TRANSFORMATION,
                    transId,
                    trans.level(),
                    ChiseledEnchantingTableData.transformationXpCost(transId),
                    ChiseledEnchantingTableData.transformationCostOptions(transId)
                )) {
                    truncated = true;
                    break;
                }
            }
        }

        // 2. Emit enchantment cost rows in level-interleaved order (lvl 1 across all enchantments, then lvl 2, ...)
        // so extreme max_level=255 datapacks stay strictly within PayloadLimits while keeping lower levels intact.
        var registryAccess = player.level().registryAccess();
        var enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder.Reference<Enchantment>> allHolders = enchantments.listElements().toList();
        int globalMaxLevel = 1;
        for (Holder.Reference<Enchantment> holder : allHolders) {
            globalMaxLevel = Math.max(globalMaxLevel, Math.min(255, Math.max(1, holder.value().getMaxLevel())));
        }

        outer:
        for (int level = 1; level <= globalMaxLevel; level++) {
            for (Holder.Reference<Enchantment> holder : allHolders) {
                int maxLevel = Math.min(255, Math.max(1, holder.value().getMaxLevel()));
                if (level > maxLevel) {
                    continue;
                }
                Identifier id = holder.key().identifier();
                EnchantmentWithLevel key = new EnchantmentWithLevel(id, level);
                if (!tryAppendEntry(entries, totalStacks, estimatedBytes, OptionType.TREASURE_EPIPHANY, id, level,
                        ChiseledEnchantingTableData.treasureEpiphanyXpCost(id, level, maxLevel),
                        ChiseledEnchantingTableData.treasureEpiphanyCostOptions(id, level, maxLevel))
                    || !tryAppendEntry(entries, totalStacks, estimatedBytes, OptionType.ENCHANTMENT, id, level,
                        ChiseledEnchantingTableData.xpCost(key, maxLevel),
                        ChiseledEnchantingTableData.itemCostOptions(key, maxLevel))
                    || !tryAppendEntry(entries, totalStacks, estimatedBytes, OptionType.STRIP_SINGLE_ENCHANTMENT, id, level,
                        ChiseledEnchantingTableData.targetedStripXpCost(id, level, maxLevel),
                        ChiseledEnchantingTableData.targetedStripCostOptions(id, level, maxLevel))
                    || !tryAppendEntry(entries, totalStacks, estimatedBytes, OptionType.PURGE_CURSE, id, level,
                        ChiseledEnchantingTableData.cursePurgeXpCost(),
                        ChiseledEnchantingTableData.cursePurgeCostOptions(id))) {
                    truncated = true;
                    break outer;
                }
                if (level >= 2) {
                    if (!tryAppendEntry(entries, totalStacks, estimatedBytes, OptionType.BOOK_COMBINE_UPGRADE, id, level,
                            ChiseledEnchantingTableData.bookCombineXpCost(key, maxLevel),
                            List.of())
                        || !tryAppendEntry(entries, totalStacks, estimatedBytes, OptionType.BOOK_RESEARCH_UPGRADE, id, level,
                            ChiseledEnchantingTableData.bookResearchXpCost(key, maxLevel),
                            ChiseledEnchantingTableData.bookResearchCostOptions(key, maxLevel))) {
                        truncated = true;
                        break outer;
                    }
                }
            }
        }
        if (truncated) {
            ChiseledEnchantingTable.LOGGER.warn(
                "Capped EnchantCostTablePayload at {} rows / {} cost stacks / ~{} estimated bytes to honor wire safety limits; "
                    + "clients will fail closed on missing rows (costTableComplete=false)",
                entries.size(),
                totalStacks[0],
                estimatedBytes[0]
            );
        }

        List<ChiseledEnchantingMenuPayloads.TransformationDisplay> displays =
            ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload.buildTransformationDisplays(player);
        if (displays.size() > PayloadLimits.MAX_COLLECTION_ENTRIES) {
            displays = List.copyOf(displays.subList(0, PayloadLimits.MAX_COLLECTION_ENTRIES));
            truncated = true;
        }

        return new EnchantCostTablePayload(
            ChiseledEnchantingTableData.bookCombineEnabled(),
            ChiseledEnchantingTableData.bookResearchEnabled(),
            ChiseledEnchantingTableData.disenchantEnabled(),
            ChiseledEnchantingTableData.disenchantAllowCursePurge(),
            ChiseledEnchantingTableData.disenchantRefundScale(),
            ChiseledEnchantingTableData.disenchantExternalRefundScale(),
            ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves(),
            ChiseledEnchantingTableData.treasureEpiphanyBaseChance(),
            ChiseledEnchantingTableData.treasureEpiphanyPerShelfChance(),
            ChiseledEnchantingTableData.treasureEpiphanyPerTomeChance(),
            ChiseledEnchantingTableData.treasureEpiphanyMaxChance(),
            ChiseledEnchantingTableData.treasureEpiphanyMaxSlotsCap(),
            ChiseledEnchantingTableData.treasureEpiphanyScorePerSlot(),
            ChiseledEnchantingTableData.treasureEpiphanyCycleTicks(),
            ChiseledEnchantingTableData.treasureEpiphanyFreezeDurationTicks(),
            ChiseledEnchantingTableData.treasureEpiphanyMaxFreezeTicks(),
            ChiseledEnchantingTableData.treasureEpiphanyFreezeXpLevels(),
            ChiseledEnchantingTableData.treasureEpiphanyFreezeCostOptions(),
            ChiseledEnchantingTableData.treasureEpiphanyRerollXpLevels(),
            ChiseledEnchantingTableData.treasureEpiphanyRerollCostOptions(),
            ChiseledEnchantingTableData.treasureEpiphanyExplicitIds(registryAccess),
            ChiseledEnchantingTableData.shelfGatesConfig(),
            ChiseledEnchantingTableData.libraryResonanceConfig(),
            ChiseledEnchantingTableData.ritualConfig(),
            List.copyOf(entries),
            displays,
            !truncated
        );
    }

    private static void writeStacks(RegistryFriendlyByteBuf buf, List<ItemStack> stacks, int maxAllowed) {
        PayloadLimits.writeBoundedCount(buf, stacks.size(), maxAllowed);
        for (ItemStack s : stacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
        }
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf, int maxAllowed) {
        int n = PayloadLimits.readBoundedCount(buf, maxAllowed);
        List<ItemStack> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ItemStack decoded = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            if (!decoded.isEmpty()) {
                list.add(decoded);
            }
        }
        return List.copyOf(list);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(combineEnabled);
        buf.writeBoolean(researchEnabled);
        buf.writeBoolean(disenchantEnabled);
        buf.writeBoolean(disenchantAllowCursePurge);
        buf.writeDouble(disenchantRefundScale);
        buf.writeDouble(disenchantExternalRefundScale);
        buf.writeVarInt(epiphanyMinBookshelves);
        buf.writeDouble(epiphanyBaseChance);
        buf.writeDouble(epiphanyPerShelfChance);
        buf.writeDouble(epiphanyPerTomeChance);
        buf.writeDouble(epiphanyMaxChance);
        buf.writeVarInt(epiphanyMaxSlotsCap);
        buf.writeVarInt(epiphanyScorePerSlot);
        buf.writeVarInt(epiphanyCycleTicks);
        buf.writeVarInt(epiphanyFreezeDurationTicks);
        buf.writeVarInt(epiphanyMaxFreezeTicks);
        buf.writeVarInt(epiphanyFreezeXpLevels);
        writeStacks(buf, epiphanyFreezeCosts, PayloadLimits.MAX_COST_OPTIONS);
        buf.writeVarInt(epiphanyRerollXpLevels);
        writeStacks(buf, epiphanyRerollCosts, PayloadLimits.MAX_COST_OPTIONS);
        PayloadLimits.writeBoundedCount(buf, explicitTreasureIds.size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
        for (Identifier id : explicitTreasureIds) {
            buf.writeIdentifier(id);
        }
        var sg = shelfGatesConfig == null ? BookshelfProgression.Config.DEFAULT : shelfGatesConfig;
        buf.writeBoolean(sg.directGatesEnabled());
        buf.writeVarInt(sg.maxDirectBookshelves());
        buf.writeDouble(sg.gamma());
        buf.writeUtf(sg.blankMaxTierMode().token());
        buf.writeVarInt(sg.horizontalScanRadius());
        buf.writeVarInt(sg.verticalScanRadius());
        PayloadLimits.writeBoundedCount(buf, sg.overrides().size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
        for (var ov : sg.overrides().entrySet()) {
            buf.writeIdentifier(ov.getKey());
            PayloadLimits.writeBoundedCount(buf, ov.getValue().size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
            for (int v : ov.getValue()) {
                buf.writeVarInt(Math.max(0, v));
            }
        }
        buf.writeVarInt(sg.tier1Bookshelves());
        buf.writeVarInt(sg.tier2Bookshelves());
        buf.writeDouble(sg.tier2MaterialMultiplier());
        buf.writeVarInt(sg.tier3QualifyingEnchantments());
        buf.writeVarInt(sg.baseBatchSize());
        buf.writeVarInt(sg.tier3BatchSize());
        buf.writeUtf(sg.vanillaTableMode().token());
        buf.writeBoolean(sg.requireBreathingRoom());
        buf.writeVarInt(sg.minOpenAdjacentBlocks());
        buf.writeBoolean(sg.requireLineOfSight());

        var lr = libraryResonanceConfig == null ? LibraryResonance.Config.DEFAULT : libraryResonanceConfig;
        buf.writeVarInt(lr.minDiscountBookshelves());
        buf.writeVarInt(lr.maxDiscountBookshelves());
        buf.writeVarInt(lr.maxWoodenDiscountPercent());
        buf.writeVarInt(lr.maxGlobalMasteryDiscountPercent());
        buf.writeVarInt(lr.maxBaseXpDiscountPercent());
        buf.writeVarInt(lr.maxSpecificEnchantBonusPercent());
        buf.writeDouble(lr.specificEnchantExponent());
        buf.writeVarInt(lr.maxTotalXpDiscountPercent());
        buf.writeDouble(lr.masteryUnitExponent());
        buf.writeDouble(lr.globalMasteryMultiplier());
        buf.writeDouble(lr.masterTomeUpgradeMaterialMultiplier());
        buf.writeDouble(lr.shelfUpgradeMaterialMultiplier());
        buf.writeDouble(lr.epiphanyProbabilityGlobalWeight());
        buf.writeDouble(lr.epiphanyProbabilityAffinityWeight());
        buf.writeDouble(lr.blankBookAffinityWeight());
        buf.writeDouble(lr.epiphanyShelfScoreFactor());
        buf.writeDouble(lr.epiphanyGlobalMasteryScoreFactor());
        buf.writeDouble(lr.epiphanyAffinityMasteryScoreFactor());
        buf.writeDouble(lr.epiphanyTier2MaxProb());
        buf.writeDouble(lr.epiphanyTier2ScoreDivisor());
        buf.writeDouble(lr.epiphanyTier2MasteryBonus());
        buf.writeDouble(lr.epiphanyTierPlusMaxProb());
        buf.writeDouble(lr.epiphanyTierPlusScoreDivisor());
        buf.writeDouble(lr.epiphanyTierPlusMasteryBonus());
        buf.writeDouble(lr.incrementalStepCreditFactor());
        buf.writeUtf(lr.tomeConsumptionMode().token());
        buf.writeVarInt(lr.maxTomeUses());
        buf.writeVarInt(lr.bookCopyWearCost());
        buf.writeUtf(lr.tomeDepleteBehavior().token());
        buf.writeBoolean(lr.tomeRegenEnabled());
        buf.writeVarInt(lr.tomeRegenBaseSeconds());
        buf.writeVarInt(lr.tomeRegenTier1Shelves());
        buf.writeVarInt(lr.tomeRegenTier1Seconds());
        buf.writeVarInt(lr.tomeRegenTier2Shelves());
        buf.writeVarInt(lr.tomeRegenTier2Seconds());
        buf.writeDouble(lr.unbreakingWaiverPerLevel());
        buf.writeDouble(lr.unbreakingWaiverMaxChance());
        buf.writeBoolean(lr.wildBookWearEnabled());
        PayloadLimits.writeBoundedCount(buf, lr.wildBookRemainingWeights().size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
        for (int w : lr.wildBookRemainingWeights()) {
            buf.writeVarInt(Math.max(0, w));
        }
        buf.writeUtf(lr.scapegoatPricing().token());
        buf.writeBoolean(lr.lockedShelfBreakable());
        buf.writeBoolean(lr.lockedBookExtractionBlocked());
        buf.writeVarInt(lr.earlyUnlockXpLevels());
        buf.writeVarInt(lr.earlyUnlockGritCount());
        buf.writeVarInt(lr.sacrificeRequired());
        buf.writeVarInt(lr.tomePageRequiredShelves());
        buf.writeDouble(lr.maxMasteryUnitsPerSlot());
        buf.writeVarInt(lr.wearImmuneMaxGeneration());
        buf.writeVarInt(lr.wearImmuneFullWaiverMaxEnchants());
        buf.writeDouble(lr.wearImmuneOverloadWaiverDecay());

        var rc = ritualConfig == null ? InscriptionRitualMechanics.Config.DEFAULT : ritualConfig;
        buf.writeVarInt(rc.maxSingleWaitSeconds());
        buf.writeVarInt(rc.minTomeEnchantsForWait());
        buf.writeVarInt(rc.maxSacrificeXpLevels());
        buf.writeDouble(rc.maxRushDegradeNormal());
        buf.writeDouble(rc.maxRushDegradeInfinity());
        buf.writeDouble(rc.maxTranscriptionSlipChance());
        buf.writeDouble(rc.shelfLoadReference());
        buf.writeDouble(rc.tomeLoadReference());
        buf.writeVarInt(rc.sacrificeExtraXpPerQueuedStep());
        buf.writeDouble(rc.sacrificeXpTimeConstantSeconds());
        buf.writeDouble(rc.rushDegradeTimeConstantSeconds());
        var cs = rc.crosslineSlip();
        buf.writeDouble(cs.minLoadThreshold());
        buf.writeDouble(cs.perLoadSlope());
        buf.writeDouble(cs.maxSlipChance());
        buf.writeVarInt(cs.shelfTier1Count());
        buf.writeDouble(cs.shelfTier1Bonus());
        buf.writeVarInt(cs.shelfTier2Count());
        buf.writeDouble(cs.shelfTier2Bonus());
        buf.writeVarInt(cs.shelfTier3Count());
        buf.writeDouble(cs.shelfTier3Bonus());
        buf.writeDouble(cs.treasureScore());
        buf.writeDouble(cs.singleTreasureMinLoad());
        buf.writeVarInt(cs.highRarityMaxWeight());
        buf.writeDouble(cs.highRarityScore());
        buf.writeVarInt(cs.midRarityMaxWeight());
        buf.writeDouble(cs.midRarityScore());
        buf.writeVarInt(cs.broadApplicabilityMinItems());
        buf.writeDouble(cs.broadApplicabilityScore());
        buf.writeDouble(cs.relativeLevelMinRatio());
        buf.writeDouble(cs.relativeLevelScore());
        buf.writeVarInt(cs.unbreakingHighMinLevel());
        buf.writeDouble(cs.unbreakingHighScore());
        buf.writeDouble(cs.tierDropShare());
        buf.writeDouble(cs.junkCrosslineShare());
        buf.writeDouble(cs.curseCrosslineShare());
        buf.writeDouble(cs.pollutionRefundRatio());
        var wc = rc.waitCurve();
        buf.writeDouble(wc.shelfWeight());
        buf.writeDouble(wc.tomeWeight());
        buf.writeDouble(wc.levelFloor());
        buf.writeDouble(wc.levelSpan());
        buf.writeDouble(wc.levelExponent());
        buf.writeDouble(wc.saturation());
        buf.writeVarInt(wc.minTicks());

        PayloadLimits.writeBoundedCount(buf, costs.size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
        int writtenStacks = 0;
        for (CostEntry entry : costs) {
            if (entry.costOptions().size() > PayloadLimits.MAX_COST_OPTIONS) {
                throw new IllegalArgumentException(
                    "Outbound CostEntry option count exceeds budget: "
                    + entry.costOptions().size() + " > " + PayloadLimits.MAX_COST_OPTIONS
                );
            }
            for (List<ItemStack> bundle : entry.costOptions()) {
                writtenStacks += bundle.size();
                if (writtenStacks > PayloadLimits.MAX_TOTAL_COST_STACKS) {
                    throw new IllegalArgumentException(
                        "Outbound EnchantCostTablePayload total cost stacks exceed budget: "
                        + writtenStacks + " > " + PayloadLimits.MAX_TOTAL_COST_STACKS
                    );
                }
            }
            buf.writeUtf(entry.optionType().token());
            buf.writeIdentifier(entry.enchantmentId());
            buf.writeVarInt(entry.level());
            buf.writeVarInt(Math.max(0, entry.xpLevelCost()));
            PayloadLimits.writeBoundedCount(buf, entry.costOptions().size(), PayloadLimits.MAX_COST_OPTIONS);
            for (List<ItemStack> bundle : entry.costOptions()) {
                writeStacks(buf, bundle, PayloadLimits.MAX_BUNDLE_STACKS);
            }
        }
        PayloadLimits.writeBoundedCount(buf, transformations.size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
        for (var display : transformations) {
            ChiseledEnchantingMenuPayloads.TransformationDisplay.write(buf, display);
        }
        buf.writeBoolean(costTableComplete);
    }

    private static EnchantCostTablePayload read(RegistryFriendlyByteBuf buf) {
        boolean combineEnabled = buf.readBoolean();
        boolean researchEnabled = buf.readBoolean();
        boolean disenchantEnabled = buf.readBoolean();
        boolean disenchantAllowCursePurge = buf.readBoolean();
        double disenchantRefundScale = buf.readDouble();
        double disenchantExternalRefundScale = buf.readDouble();
        int minShelves = buf.readVarInt();
        double baseChance = buf.readDouble();
        double perShelfChance = buf.readDouble();
        double perTomeChance = buf.readDouble();
        double maxChance = buf.readDouble();
        int maxSlotsCap = buf.readVarInt();
        int scorePerSlot = buf.readVarInt();
        int cycleTicks = buf.readVarInt();
        int freezeDurationTicks = buf.readVarInt();
        int maxFreezeTicks = buf.readVarInt();
        int freezeXpLevels = buf.readVarInt();
        List<ItemStack> freezeCosts = readStacks(buf, PayloadLimits.MAX_COST_OPTIONS);
        int rerollXp = buf.readVarInt();
        List<ItemStack> rerollCosts = readStacks(buf, PayloadLimits.MAX_COST_OPTIONS);
        int treasureIdCount = PayloadLimits.readCount(buf);
        Set<Identifier> treasureIds = new HashSet<>(Math.min(1024, treasureIdCount * 2));
        for (int i = 0; i < treasureIdCount; i++) {
            treasureIds.add(buf.readIdentifier());
        }
        boolean sgEnabled = buf.readBoolean();
        int sgMax = buf.readVarInt();
        double sgGamma = buf.readDouble();
        var sgBlankMode = BookshelfProgression.BlankMaxTierMode.fromToken(buf.readUtf());
        int sgHScan = buf.readVarInt();
        int sgVScan = buf.readVarInt();
        int ovCount = PayloadLimits.readCount(buf);
        java.util.Map<Identifier, List<Integer>> sgOverrides = new java.util.HashMap<>();
        for (int i = 0; i < ovCount; i++) {
            Identifier ovId = buf.readIdentifier();
            int len = PayloadLimits.readCount(buf);
            List<Integer> ladder = new ArrayList<>(len);
            for (int j = 0; j < len; j++) {
                ladder.add(Math.max(0, buf.readVarInt()));
            }
            if (!ladder.isEmpty()) {
                sgOverrides.put(ovId, List.copyOf(ladder));
            }
        }
        int sgT1 = buf.readVarInt();
        int sgT2 = buf.readVarInt();
        double sgMult = buf.readDouble();
        int sgT3 = buf.readVarInt();
        int sgBaseBatch = buf.readVarInt();
        int sgTier3Batch = buf.readVarInt();
        var sgVanillaMode = BookshelfProgression.VanillaTableMode.fromToken(buf.readUtf());
        boolean sgBreathing = buf.readBoolean();
        int sgMinOpen = buf.readVarInt();
        boolean sgLos = buf.readBoolean();
        var sgConfig = new BookshelfProgression.Config(
            sgEnabled, sgMax, sgGamma, sgBlankMode, sgHScan, sgVScan,
            sgOverrides, sgT1, sgT2, sgMult, sgT3, sgBaseBatch, sgTier3Batch,
            sgVanillaMode, sgBreathing, sgMinOpen, sgLos
        );

        int lr0 = buf.readVarInt();
        int lr1 = buf.readVarInt();
        int lr2 = buf.readVarInt();
        int lr3 = buf.readVarInt();
        int lr4 = buf.readVarInt();
        int lr5 = buf.readVarInt();
        double lr6 = buf.readDouble();
        int lr7 = buf.readVarInt();
        double lr8 = buf.readDouble();
        double lr9 = buf.readDouble();
        double lr10 = buf.readDouble();
        double lr11 = buf.readDouble();
        double lr12 = buf.readDouble();
        double lr13 = buf.readDouble();
        double lr14 = buf.readDouble();
        double lr15 = buf.readDouble();
        double lr16 = buf.readDouble();
        double lr17 = buf.readDouble();
        double lr18 = buf.readDouble();
        double lr19 = buf.readDouble();
        double lr20 = buf.readDouble();
        double lr21 = buf.readDouble();
        double lr22 = buf.readDouble();
        double lr23 = buf.readDouble();
        double lr24 = buf.readDouble();
        var lrMode = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.fromToken(buf.readUtf());
        int lrMtu = buf.readVarInt();
        int lrBcwc = buf.readVarInt();
        var lrDep = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.DepleteBehavior.fromToken(buf.readUtf());
        boolean lrRegenEn = buf.readBoolean();
        int lrRegenBase = buf.readVarInt();
        int lrRegenT1S = buf.readVarInt();
        int lrRegenT1Sec = buf.readVarInt();
        int lrRegenT2S = buf.readVarInt();
        int lrRegenT2Sec = buf.readVarInt();
        double lrUnbPerLvl = buf.readDouble();
        double lrUnbMax = buf.readDouble();
        boolean lrWildEn = buf.readBoolean();
        int wildWeightLen = PayloadLimits.readBoundedCount(buf, 256);
        List<Integer> lrWildWeights = new ArrayList<>(wildWeightLen);
        for (int i = 0; i < wildWeightLen; i++) {
            lrWildWeights.add(Math.max(0, buf.readVarInt()));
        }
        var lrScapegoat = chiseled_enchanting_table.mechanics.resonance.TomeWearData.ScapegoatPricing.fromToken(buf.readUtf());
        boolean lrShelfBreakable = buf.readBoolean();
        boolean lrExtractionBlocked = buf.readBoolean();
        int lrEarlyXp = buf.readVarInt();
        int lrEarlyGrit = buf.readVarInt();
        int lrSacrificeReq = buf.readVarInt();
        int lrTomeShelves = buf.readVarInt();
        double lrMaxMasterySlot = buf.readDouble();
        int lrWearMaxGen = buf.readVarInt();
        int lrWearFullEnch = buf.readVarInt();
        double lrWearDecay = buf.readDouble();

        var lrConfig = new LibraryResonance.Config(
            lr0, lr1, lr2, lr3, lr4, lr5, lr6, lr7, lr8, lr9, lr10, lr11, lr12,
            lr13, lr14, lr15, lr16, lr17, lr18, lr19, lr20, lr21, lr22, lr23, lr24,
            lrMode, lrMtu, lrBcwc, lrDep, lrRegenEn, lrRegenBase, lrRegenT1S, lrRegenT1Sec, lrRegenT2S, lrRegenT2Sec,
            lrUnbPerLvl, lrUnbMax, lrWildEn, lrWildWeights, lrScapegoat,
            lrShelfBreakable, lrExtractionBlocked, lrEarlyXp, lrEarlyGrit, lrSacrificeReq, lrTomeShelves,
            lrMaxMasterySlot, lrWearMaxGen, lrWearFullEnch, lrWearDecay
        );

        int rcMaxWait = buf.readVarInt();
        int rcMinTome = buf.readVarInt();
        int rcMaxSac = buf.readVarInt();
        double rcRushNorm = buf.readDouble();
        double rcRushInf = buf.readDouble();
        double rcSlipCap = buf.readDouble();
        double rcShelfRef = buf.readDouble();
        double rcTomeRef = buf.readDouble();
        int rcExtraSacPerStep = buf.readVarInt();
        double rcSacTau = buf.readDouble();
        double rcRushTau = buf.readDouble();
        var csConfig = new InscriptionRitualMechanics.CrosslineSlipConfig(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );
        var wcConfig = new InscriptionRitualMechanics.WaitCurve(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt()
        );
        var rcConfig = new InscriptionRitualMechanics.Config(
            rcMaxWait, rcMinTome, rcMaxSac, rcRushNorm, rcRushInf, rcSlipCap, rcShelfRef, rcTomeRef, csConfig, wcConfig,
            rcExtraSacPerStep, rcSacTau, rcRushTau
        );

        int rowCount = PayloadLimits.readCount(buf);
        List<CostEntry> entries = new ArrayList<>(Math.min(4096, Math.max(16, rowCount)));
        int decodedStacks = 0;
        for (int i = 0; i < rowCount; i++) {
            OptionType optionType = OptionType.fromToken(buf.readUtf(PayloadLimits.MAX_OPTION_TOKEN_LENGTH));
            Identifier id = buf.readIdentifier();
            int level = buf.readVarInt();
            int xpLevelCost = buf.readVarInt();
            int optionCount = PayloadLimits.readBoundedCount(buf, PayloadLimits.MAX_COST_OPTIONS);
            List<List<ItemStack>> options = new ArrayList<>(optionCount);
            for (int o = 0; o < optionCount; o++) {
                List<ItemStack> bundle = readStacks(buf, PayloadLimits.MAX_BUNDLE_STACKS);
                decodedStacks += bundle.size();
                if (decodedStacks > PayloadLimits.MAX_TOTAL_COST_STACKS) {
                    throw new IllegalArgumentException(
                        "Inbound EnchantCostTablePayload total cost stacks exceed budget: "
                        + decodedStacks + " > " + PayloadLimits.MAX_TOTAL_COST_STACKS
                    );
                }
                // Audit H-02: preserve explicitly-empty (material-free) bundles verbatim.
                options.add(bundle);
            }
            entries.add(new CostEntry(optionType, id, level, xpLevelCost, List.copyOf(options)));
        }
        int transformationCount = PayloadLimits.readCount(buf);
        List<ChiseledEnchantingMenuPayloads.TransformationDisplay> transformations = new ArrayList<>();
        for (int i = 0; i < transformationCount; i++) {
            transformations.add(ChiseledEnchantingMenuPayloads.TransformationDisplay.read(buf));
        }
        boolean costTableComplete = buf.readBoolean();
        return new EnchantCostTablePayload(
            combineEnabled, researchEnabled,
            disenchantEnabled, disenchantAllowCursePurge, disenchantRefundScale, disenchantExternalRefundScale,
            minShelves, baseChance, perShelfChance, perTomeChance, maxChance,
            maxSlotsCap, scorePerSlot, cycleTicks, freezeDurationTicks, maxFreezeTicks,
            freezeXpLevels, freezeCosts, rerollXp, rerollCosts,
            Set.copyOf(treasureIds), sgConfig, lrConfig, rcConfig, List.copyOf(entries), List.copyOf(transformations),
            costTableComplete
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
