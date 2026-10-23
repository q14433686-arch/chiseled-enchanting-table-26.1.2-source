package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
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
 * {@link OptionType#token()} wire framing, {@link BookshelfProgression.Config}, and {@link LibraryResonance.Config}.
 */
public record EnchantCostTablePayload(
    boolean combineEnabled,
    boolean researchEnabled,
    boolean disenchantEnabled,
    boolean disenchantAllowCursePurge,
    double disenchantRefundScale,
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
) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = ChiseledEnchantingTable.identifier("enchant_cost_table_" + EnchantingProtocol.REVISION);
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> TYPE =
        new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantCostTablePayload> CODEC =
        CustomPacketPayload.codec(EnchantCostTablePayload::write, EnchantCostTablePayload::read);

    public record CostEntry(
        OptionType optionType,
        Identifier enchantmentId,
        int level,
        int xpLevelCost,
        List<List<ItemStack>> costOptions
    ) {}

    public static EnchantCostTablePayload build(Player player) {
        List<CostEntry> entries = new ArrayList<>();
        var registryAccess = player.level().registryAccess();
        var enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        for (Holder.Reference<Enchantment> holder : enchantments.listElements().toList()) {
            Identifier id = holder.key().identifier();
            int maxLevel = Math.max(1, holder.value().getMaxLevel());
            for (int level = 1; level <= maxLevel; level++) {
                entries.add(new CostEntry(
                    OptionType.TREASURE_EPIPHANY,
                    id,
                    level,
                    ChiseledEnchantingTableData.treasureEpiphanyXpCost(id, level, maxLevel),
                    ChiseledEnchantingTableData.treasureEpiphanyCostOptions(id, level, maxLevel)
                ));
                EnchantmentWithLevel key = new EnchantmentWithLevel(id, level);
                entries.add(new CostEntry(
                    OptionType.ENCHANTMENT,
                    id,
                    level,
                    ChiseledEnchantingTableData.xpCost(key, maxLevel),
                    ChiseledEnchantingTableData.itemCostOptions(key, maxLevel)
                ));
                entries.add(new CostEntry(
                    OptionType.STRIP_SINGLE_ENCHANTMENT,
                    id,
                    level,
                    ChiseledEnchantingTableData.targetedStripXpCost(id, level, maxLevel),
                    ChiseledEnchantingTableData.targetedStripCostOptions(id, level, maxLevel)
                ));
                entries.add(new CostEntry(
                    OptionType.PURGE_CURSE,
                    id,
                    level,
                    ChiseledEnchantingTableData.cursePurgeXpCost(),
                    ChiseledEnchantingTableData.cursePurgeCostOptions(id)
                ));
                if (level >= 2) {
                    entries.add(new CostEntry(
                        OptionType.BOOK_COMBINE_UPGRADE,
                        id,
                        level,
                        ChiseledEnchantingTableData.bookCombineXpCost(key, maxLevel),
                        List.of()
                    ));
                    entries.add(new CostEntry(
                        OptionType.BOOK_RESEARCH_UPGRADE,
                        id,
                        level,
                        ChiseledEnchantingTableData.bookResearchXpCost(key, maxLevel),
                        ChiseledEnchantingTableData.bookResearchCostOptions(key, maxLevel)
                    ));
                }
            }
        }
        for (Identifier transId : ChiseledEnchantingTableData.transformationIds()) {
            ChiseledEnchantingTableData.itemTransformation(transId).ifPresent(trans -> entries.add(new CostEntry(
                OptionType.TRANSFORMATION,
                transId,
                trans.level(),
                ChiseledEnchantingTableData.transformationXpCost(transId),
                ChiseledEnchantingTableData.transformationCostOptions(transId)
            )));
        }
        return new EnchantCostTablePayload(
            ChiseledEnchantingTableData.bookCombineEnabled(),
            ChiseledEnchantingTableData.bookResearchEnabled(),
            ChiseledEnchantingTableData.disenchantEnabled(),
            ChiseledEnchantingTableData.disenchantAllowCursePurge(),
            ChiseledEnchantingTableData.disenchantRefundScale(),
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
            List.copyOf(entries),
            ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload.buildTransformationDisplays()
        );
    }

    private static void writeStacks(RegistryFriendlyByteBuf buf, List<ItemStack> stacks) {
        buf.writeVarInt(stacks.size());
        for (ItemStack s : stacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
        }
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf) {
        int n = PayloadLimits.readCount(buf);
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
        writeStacks(buf, epiphanyFreezeCosts);
        buf.writeVarInt(epiphanyRerollXpLevels);
        writeStacks(buf, epiphanyRerollCosts);
        buf.writeVarInt(explicitTreasureIds.size());
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
        buf.writeVarInt(sg.overrides().size());
        for (var ov : sg.overrides().entrySet()) {
            buf.writeIdentifier(ov.getKey());
            buf.writeVarInt(ov.getValue().size());
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

        buf.writeVarInt(costs.size());
        for (CostEntry entry : costs) {
            buf.writeUtf(entry.optionType().token());
            buf.writeIdentifier(entry.enchantmentId());
            buf.writeVarInt(entry.level());
            buf.writeVarInt(Math.max(0, entry.xpLevelCost()));
            buf.writeVarInt(entry.costOptions().size());
            for (List<ItemStack> bundle : entry.costOptions()) {
                writeStacks(buf, bundle);
            }
        }
        buf.writeVarInt(transformations.size());
        for (var display : transformations) {
            ChiseledEnchantingMenuPayloads.TransformationDisplay.write(buf, display);
        }
    }

    private static EnchantCostTablePayload read(RegistryFriendlyByteBuf buf) {
        boolean combineEnabled = buf.readBoolean();
        boolean researchEnabled = buf.readBoolean();
        boolean disenchantEnabled = buf.readBoolean();
        boolean disenchantAllowCursePurge = buf.readBoolean();
        double disenchantRefundScale = buf.readDouble();
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
        List<ItemStack> freezeCosts = readStacks(buf);
        int rerollXp = buf.readVarInt();
        List<ItemStack> rerollCosts = readStacks(buf);
        int treasureIdCount = PayloadLimits.readCount(buf);
        Set<Identifier> treasureIds = new HashSet<>(treasureIdCount * 2);
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
        var sgConfig = new BookshelfProgression.Config(
            sgEnabled, sgMax, sgGamma, sgBlankMode, sgHScan, sgVScan,
            sgOverrides, sgT1, sgT2, sgMult, sgT3, sgBaseBatch, sgTier3Batch
        );

        var lrConfig = new LibraryResonance.Config(
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );

        int rowCount = PayloadLimits.readCount(buf);
        List<CostEntry> entries = new ArrayList<>(Math.max(16, rowCount));
        for (int i = 0; i < rowCount; i++) {
            OptionType optionType = OptionType.fromToken(buf.readUtf());
            Identifier id = buf.readIdentifier();
            int level = buf.readVarInt();
            int xpLevelCost = buf.readVarInt();
            int optionCount = PayloadLimits.readCount(buf);
            List<List<ItemStack>> options = new ArrayList<>(optionCount);
            for (int o = 0; o < optionCount; o++) {
                List<ItemStack> bundle = readStacks(buf);
                if (!bundle.isEmpty()) {
                    options.add(bundle);
                }
            }
            entries.add(new CostEntry(optionType, id, level, xpLevelCost, List.copyOf(options)));
        }
        int transformationCount = PayloadLimits.readCount(buf);
        List<ChiseledEnchantingMenuPayloads.TransformationDisplay> transformations = new ArrayList<>();
        for (int i = 0; i < transformationCount; i++) {
            transformations.add(ChiseledEnchantingMenuPayloads.TransformationDisplay.read(buf));
        }
        return new EnchantCostTablePayload(
            combineEnabled, researchEnabled,
            disenchantEnabled, disenchantAllowCursePurge, disenchantRefundScale,
            minShelves, baseChance, perShelfChance, perTomeChance, maxChance,
            maxSlotsCap, scorePerSlot, cycleTicks, freezeDurationTicks, maxFreezeTicks,
            freezeXpLevels, freezeCosts, rerollXp, rerollCosts,
            Set.copyOf(treasureIds), sgConfig, lrConfig, List.copyOf(entries), List.copyOf(transformations)
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
