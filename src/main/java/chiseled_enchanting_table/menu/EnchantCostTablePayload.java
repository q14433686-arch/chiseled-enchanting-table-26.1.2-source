package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Server -> client cost table carrying ALL player-selectable material alternatives (`costOptions`),
 * book-upgrade flags, and Grand Library Treasure Epiphany settings & costs.
 */
public record EnchantCostTablePayload(
    boolean combineEnabled,
    boolean researchEnabled,
    int epiphanyMinBookshelves,
    double epiphanyBaseChance,
    double epiphanyPerShelfChance,
    double epiphanyMaxChance,
    int epiphanyRerollXpLevels,
    List<ItemStack> epiphanyRerollCosts,
    List<CostEntry> costs
) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = ChiseledEnchantingTable.identifier("enchant_cost_table");
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
        var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (Holder.Reference<Enchantment> holder : enchantments.listElements().toList()) {
            Identifier id = holder.key().identifier();
            int maxLevel = Math.max(1, holder.value().getMaxLevel());
            entries.add(new CostEntry(
                OptionType.TREASURE_EPIPHANY,
                id,
                1,
                ChiseledEnchantingTableData.treasureEpiphanyXpCost(id),
                ChiseledEnchantingTableData.treasureEpiphanyCostOptions(id)
            ));
            for (int level = 1; level <= maxLevel; level++) {
                EnchantmentWithLevel key = new EnchantmentWithLevel(id, level);
                entries.add(new CostEntry(
                    OptionType.ENCHANTMENT,
                    id,
                    level,
                    ChiseledEnchantingTableData.xpCost(key, maxLevel),
                    ChiseledEnchantingTableData.itemCostOptions(key, maxLevel)
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
            ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves(),
            ChiseledEnchantingTableData.treasureEpiphanyBaseChance(),
            ChiseledEnchantingTableData.treasureEpiphanyPerShelfChance(),
            ChiseledEnchantingTableData.treasureEpiphanyMaxChance(),
            ChiseledEnchantingTableData.treasureEpiphanyRerollXpLevels(),
            ChiseledEnchantingTableData.treasureEpiphanyRerollCostOptions(),
            List.copyOf(entries)
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(combineEnabled);
        buf.writeBoolean(researchEnabled);
        buf.writeVarInt(epiphanyMinBookshelves);
        buf.writeDouble(epiphanyBaseChance);
        buf.writeDouble(epiphanyPerShelfChance);
        buf.writeDouble(epiphanyMaxChance);
        buf.writeVarInt(epiphanyRerollXpLevels);
        buf.writeVarInt(epiphanyRerollCosts.size());
        for (ItemStack rerollStack : epiphanyRerollCosts) {
            buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(rerollStack.getItem()));
            buf.writeVarInt(rerollStack.getCount());
        }
        buf.writeVarInt(costs.size());
        for (CostEntry entry : costs) {
            buf.writeVarInt(entry.optionType().ordinal());
            buf.writeIdentifier(entry.enchantmentId());
            buf.writeVarInt(entry.level());
            buf.writeVarInt(Math.max(0, entry.xpLevelCost()));
            buf.writeVarInt(entry.costOptions().size());
            for (List<ItemStack> bundle : entry.costOptions()) {
                buf.writeVarInt(bundle.size());
                for (ItemStack stack : bundle) {
                    buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    buf.writeVarInt(stack.getCount());
                }
            }
        }
    }

    private static EnchantCostTablePayload read(RegistryFriendlyByteBuf buf) {
        boolean combineEnabled = buf.readBoolean();
        boolean researchEnabled = buf.readBoolean();
        int minShelves = buf.readVarInt();
        double baseChance = buf.readDouble();
        double perShelfChance = buf.readDouble();
        double maxChance = buf.readDouble();
        int rerollXp = buf.readVarInt();
        int rerollCount = buf.readVarInt();
        List<ItemStack> rerollCosts = new ArrayList<>(rerollCount);
        for (int i = 0; i < rerollCount; i++) {
            Identifier itemId = buf.readIdentifier();
            int count = buf.readVarInt();
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            if (item != null) {
                rerollCosts.add(new ItemStack(item, Math.max(1, count)));
            }
        }
        OptionType[] optionValues = OptionType.values();
        int rowCount = buf.readVarInt();
        List<CostEntry> entries = new ArrayList<>(Math.max(16, rowCount));
        for (int i = 0; i < rowCount; i++) {
            int ord = buf.readVarInt();
            OptionType optionType = (ord >= 0 && ord < optionValues.length) ? optionValues[ord] : OptionType.ENCHANTMENT;
            Identifier id = buf.readIdentifier();
            int level = buf.readVarInt();
            int xpLevelCost = buf.readVarInt();
            int optionCount = buf.readVarInt();
            List<List<ItemStack>> options = new ArrayList<>(optionCount);
            for (int o = 0; o < optionCount; o++) {
                int stackCount = buf.readVarInt();
                List<ItemStack> bundle = new ArrayList<>(stackCount);
                for (int c = 0; c < stackCount; c++) {
                    Identifier itemId = buf.readIdentifier();
                    int count = buf.readVarInt();
                    Item item = BuiltInRegistries.ITEM.getValue(itemId);
                    if (item != null) {
                        bundle.add(new ItemStack(item, Math.max(1, count)));
                    }
                }
                if (!bundle.isEmpty()) {
                    options.add(List.copyOf(bundle));
                }
            }
            entries.add(new CostEntry(optionType, id, level, xpLevelCost, List.copyOf(options)));
        }
        return new EnchantCostTablePayload(
            combineEnabled, researchEnabled,
            minShelves, baseChance, perShelfChance, maxChance, rerollXp, List.copyOf(rerollCosts),
            List.copyOf(entries)
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
