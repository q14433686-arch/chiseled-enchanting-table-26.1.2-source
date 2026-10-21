package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
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
 * Server → client cost table: one row per (enchantment, level), already resolved with the receiving
 * player's UUID salt.
 *
 * <p>This is what makes the recipe-viewer cost display truthful on a dedicated server. See
 * {@link chiseled_enchanting_table.compat.SyncedEnchantmentCosts} for the client-side cache and the
 * rationale.</p>
 *
 * <p>Wire format is hand-rolled (VarInt counts + {@code Identifier}, VarInt level/xp/count per cost
 * stack) instead of {@code ItemStack.STREAM_CODEC}: the values are always plain
 * {@code item + count} stacks produced by the data tables, so this keeps the payload small and free
 * of component/registry codec churn.</p>
 */
public record EnchantCostTablePayload(List<CostEntry> costs) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = ChiseledEnchantingTable.identifier("enchant_cost_table");
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> TYPE =
        new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantCostTablePayload> CODEC =
        CustomPacketPayload.codec(EnchantCostTablePayload::write, EnchantCostTablePayload::read);

    /**
     * One resolved row.
     *
     * @param enchantmentId enchantment id
     * @param level         level this row describes
     * @param xpLevelCost   XP levels the menu charges for this row
     * @param itemCosts     material stacks the menu charges, primary first (may be empty)
     */
    public record CostEntry(Identifier enchantmentId, int level, int xpLevelCost, List<ItemStack> itemCosts) {}

    /**
     * Resolves the whole catalogue for {@code player}: exactly the numbers
     * {@code ChiseledEnchantingTableScreenHandler} would charge them, because it calls the same data
     * helpers with the same UUID salt.
     */
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
                    id,
                    level,
                    ChiseledEnchantingTableData.xpCost(key, maxLevel),
                    List.copyOf(ChiseledEnchantingTableData.itemCosts(key, salt, maxLevel))
                ));
            }
        }
        return new EnchantCostTablePayload(List.copyOf(entries));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(costs.size());
        for (CostEntry entry : costs) {
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
        int rowCount = buf.readVarInt();
        List<CostEntry> entries = new ArrayList<>(Math.max(16, rowCount));
        for (int i = 0; i < rowCount; i++) {
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
            entries.add(new CostEntry(id, level, xpLevelCost, List.copyOf(itemCosts)));
        }
        return new EnchantCostTablePayload(List.copyOf(entries));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
