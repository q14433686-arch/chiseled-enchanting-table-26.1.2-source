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

    private static int estimateEntryStackBytes(List<List<ItemStack>> options) {
        int bytes = 0;
        for (List<ItemStack> bundle : options) {
            bytes += 8;
            for (ItemStack stack : bundle) {
                bytes += PayloadLimits.estimateStackBytes(stack);
            }
        }
        return bytes;
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
        // Audit R-01 / NET-04: account for custom component payloads on datapack cost stacks too.
        int byteDelta = PayloadLimits.ESTIMATED_BYTES_PER_ROW
            + estimateEntryStackBytes(opts);
        if (estimatedBytes[0] + byteDelta > PayloadLimits.MAX_ESTIMATED_PAYLOAD_BYTES) {
            return false;
        }
        totalStacks[0] += stackDelta;
        estimatedBytes[0] += byteDelta;
        entries.add(new CostEntry(type, id, level, xpCost, opts));
        return true;
    }

    public static EnchantCostTablePayload build(Player player) {
        boolean saltedSingle = ChiseledEnchantingTableData.runtimeConfiguration().materialSelectionMode()
            == chiseled_enchanting_table.config.ModRuntimeConfig.MaterialSelectionMode.PLAYER_SALTED_SINGLE;
        String salt = player.getStringUUID();
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
                    saltedSingle
                        ? List.of(ChiseledEnchantingTableData.classicTransformationCosts(transId, salt))
                        : ChiseledEnchantingTableData.transformationCostOptions(transId)
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
                        saltedSingle
                            ? List.of(ChiseledEnchantingTableData.classicSaltedItemCosts(key, maxLevel, salt))
                            : ChiseledEnchantingTableData.itemCostOptions(key, maxLevel))
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
                            saltedSingle
                                ? List.of(ChiseledEnchantingTableData.classicSaltedResearchCosts(key, maxLevel, salt))
                                : ChiseledEnchantingTableData.bookResearchCostOptions(key, maxLevel))) {
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

        return enforceWireByteBudget(
            new EnchantCostTablePayload(
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
            ),
            registryAccess
        );
    }

    /**
     * Measures actual Netty-encoded bytes via {@link io.netty.buffer.Unpooled#buffer()} and trims
     * tail cost rows if custom DataComponents push the real wire payload beyond
     * {@link PayloadLimits#MAX_SAFE_ENCODED_PAYLOAD_BYTES}.
     */
    public static EnchantCostTablePayload enforceWireByteBudget(
        EnchantCostTablePayload candidate,
        net.minecraft.core.RegistryAccess registryAccess
    ) {
        return trimToWireBudget(
            candidate,
            PayloadLimits.MAX_SAFE_ENCODED_PAYLOAD_BYTES,
            payload -> measureEncodedBytes(payload, registryAccess)
        );
    }

    public static int measureEncodedBytes(
        EnchantCostTablePayload payload,
        net.minecraft.core.RegistryAccess registryAccess
    ) {
        var raw = io.netty.buffer.Unpooled.buffer();
        try {
            var buf = new RegistryFriendlyByteBuf(raw, registryAccess);
            CODEC.encode(buf, payload);
            return raw.readableBytes();
        } finally {
            raw.release();
        }
    }

    public static EnchantCostTablePayload trimToWireBudget(
        EnchantCostTablePayload candidate,
        int maxSafeBytes,
        java.util.function.ToIntFunction<EnchantCostTablePayload> encodedByteMeasurer
    ) {
        int actualBytes = encodedByteMeasurer.applyAsInt(candidate);
        if (actualBytes <= maxSafeBytes) {
            return candidate;
        }
        EnchantCostTablePayload cur = candidate;
        while (actualBytes > maxSafeBytes && !cur.costs().isEmpty()) {
            int keep = Math.min(
                cur.costs().size() - 1,
                Math.max(0, (int) ((long) cur.costs().size() * (long) (maxSafeBytes - 16_384) / Math.max(1L, actualBytes)))
            );
            cur = new EnchantCostTablePayload(
                cur.combineEnabled(),
                cur.researchEnabled(),
                cur.disenchantEnabled(),
                cur.disenchantAllowCursePurge(),
                cur.disenchantRefundScale(),
                cur.disenchantExternalRefundScale(),
                cur.epiphanyMinBookshelves(),
                cur.epiphanyBaseChance(),
                cur.epiphanyPerShelfChance(),
                cur.epiphanyPerTomeChance(),
                cur.epiphanyMaxChance(),
                cur.epiphanyMaxSlotsCap(),
                cur.epiphanyScorePerSlot(),
                cur.epiphanyCycleTicks(),
                cur.epiphanyFreezeDurationTicks(),
                cur.epiphanyMaxFreezeTicks(),
                cur.epiphanyFreezeXpLevels(),
                cur.epiphanyFreezeCosts(),
                cur.epiphanyRerollXpLevels(),
                cur.epiphanyRerollCosts(),
                cur.explicitTreasureIds(),
                cur.shelfGatesConfig(),
                cur.libraryResonanceConfig(),
                cur.ritualConfig(),
                List.copyOf(cur.costs().subList(0, keep)),
                cur.transformations(),
                false
            );
            actualBytes = encodedByteMeasurer.applyAsInt(cur);
        }
        if (actualBytes > maxSafeBytes) {
            // Header/config/transformation data cannot be discarded without lying about rules.
            // Never emit an oversized packet, even when there are zero cost rows left to trim.
            throw new IllegalArgumentException("Enchanting configuration/header exceeds wire budget: "
                + actualBytes + " > " + maxSafeBytes);
        }
        ChiseledEnchantingTable.LOGGER.warn(
            "Trimmed EnchantCostTablePayload from {} to {} rows ({} actual encoded bytes <= {} budget); costTableComplete=false",
            candidate.costs().size(),
            cur.costs().size(),
            actualBytes,
            maxSafeBytes
        );
        return cur;
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
        BookshelfProgression.writeWire(buf, shelfGatesConfig == null ? BookshelfProgression.Config.DEFAULT : shelfGatesConfig);

        LibraryResonance.writeWire(buf, libraryResonanceConfig == null ? LibraryResonance.Config.DEFAULT : libraryResonanceConfig);

        InscriptionRitualMechanics.writeWire(buf, ritualConfig == null ? InscriptionRitualMechanics.Config.DEFAULT : ritualConfig);

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
        var sgConfig = BookshelfProgression.readWire(buf);

        var lrConfig = LibraryResonance.readWire(buf);

        var rcConfig = InscriptionRitualMechanics.readWire(buf);

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
