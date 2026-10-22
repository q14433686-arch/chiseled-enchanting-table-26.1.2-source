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
 * Server -> client cost table: resolved rows across all {@link OptionType}s (direct enchantments,
 * book combine upgrades, book research upgrades, and item transformations) with the receiving
 * player's UUID salt, plus server-side {@code book_upgrades.json} enablement flags.
 */
public record EnchantCostTablePayload(
    boolean combineEnabled,
    boolean researchEnabled,
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
        List<ItemStack> itemCosts
    ) {}

    public static EnchantCostTablePayload build(Player player) {
        String salt = player.getStringUUID();
        List<CostEntry> entries = new ArrayList<>();
        var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (Holder.Reference<Enchantment> holder : enchantments.listElements().toList()) {
            Identifier id = holder.key().identifier();
            int maxLevel = Math.max(1, holder.value().getMaxLevel());
            for (int level = 1; level <= maxLevel; level++) {
                EnchantmentWithLevel key = new EnchantmentWithLevel(id, level);
                entries.add(new CostEntry(
                    OptionType.ENCHANTMENT,
                    id,
                    level,
                    ChiseledEnchantingTableData.xpCost(key, maxLevel),
                    List.copyOf(ChiseledEnchantingTableData.itemCosts(key, salt, maxLevel))
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
                        List.copyOf(ChiseledEnchantingTableData.bookResearchCosts(key, salt, maxLevel))
                    ));
                }
            }
        }
        for (Identifier transId : ChiseledEnchantingTableData.transformationIds()) {
            ChiseledEnchantingTableData.itemTransformation(transId).ifPresent(trans -> {
                List<ItemStack> costs = new ArrayList<>();
                costs.add(ChiseledEnchantingTableData.transformationPrimaryCost(transId, salt));
                costs.addAll(ChiseledEnchantingTableData.transformationAdditionalCosts(transId, salt));
                entries.add(new CostEntry(
                    OptionType.TRANSFORMATION,
                    transId,
                    trans.level(),
                    ChiseledEnchantingTableData.transformationXpCost(transId),
                    List.copyOf(costs)
                ));
            });
        }
        return new EnchantCostTablePayload(
            ChiseledEnchantingTableData.bookCombineEnabled(),
            ChiseledEnchantingTableData.bookResearchEnabled(),
            List.copyOf(entries)
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(combineEnabled);
        buf.writeBoolean(researchEnabled);
        buf.writeVarInt(costs.size());
        for (CostEntry entry : costs) {
            buf.writeVarInt(entry.optionType().ordinal());
            buf.writeIdentifier(entry.enchantmentId());
            buf.writeVarInt(entry.level());
            buf.writeVarInt(Math.max(0, entry.xpLevelCost()));
            buf.writeVarInt(entry.itemCosts().size());
            for (ItemStack stack : entry.itemCosts()) {
                buf.writeIdentifier(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                buf.writeVarInt(stack.getCount());
            }
        }
    }

    private static EnchantCostTablePayload read(RegistryFriendlyByteBuf buf) {
        boolean combineEnabled = buf.readBoolean();
        boolean researchEnabled = buf.readBoolean();
        OptionType[] optionValues = OptionType.values();
        int rowCount = buf.readVarInt();
        List<CostEntry> entries = new ArrayList<>(Math.max(16, rowCount));
        for (int i = 0; i < rowCount; i++) {
            int ord = buf.readVarInt();
            OptionType optionType = (ord >= 0 && ord < optionValues.length) ? optionValues[ord] : OptionType.ENCHANTMENT;
            Identifier id = buf.readIdentifier();
            int level = buf.readVarInt();
            int xpLevelCost = buf.readVarInt();
            int costCount = buf.readVarInt();
            List<ItemStack> itemCosts = new ArrayList<>(costCount);
            for (int c = 0; c < costCount; c++) {
                Identifier itemId = buf.readIdentifier();
                int count = buf.readVarInt();
                Item item = BuiltInRegistries.ITEM.getValue(itemId);
                if (item != null) {
                    itemCosts.add(new ItemStack(item, Math.max(1, count)));
                }
            }
            entries.add(new CostEntry(optionType, id, level, xpLevelCost, List.copyOf(itemCosts)));
        }
        return new EnchantCostTablePayload(combineEnabled, researchEnabled, List.copyOf(entries));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
