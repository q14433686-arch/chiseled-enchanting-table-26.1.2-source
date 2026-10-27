package chiseled_enchanting_table.gui;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;


/** Read-only tooltip family; action dispatch remains at the screen boundary. */
final class TableCostTooltips {
    private TableCostTooltips() {}
    static boolean render(TableTooltipContext state, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ItemStack hoveredCost = state.list().hoveredCostItem(mouseX, mouseY);
        if (hoveredCost != null && !hoveredCost.isEmpty()) {
            List<Component> lines = new ArrayList<>(state.itemTooltip().apply(hoveredCost));
            List<List<ItemStack>> options = state.list().hoveredCostOptions(mouseX, mouseY);
            if (options != null && !options.isEmpty()) {
                boolean hasMultiBundle = options.stream().anyMatch(b -> b != null && b.size() > 1);
                lines.add(Component.translatable(hasMultiBundle
                    ? "gui.chiseled_enchanting_table.cost_bundle.header"
                    : "gui.chiseled_enchanting_table.cost_alternatives.header")
                    .withStyle(ChatFormatting.GOLD));
                if (options.size() > 1) {
                    lines.add(Component.translatable("gui.chiseled_enchanting_table.cost_bundle.click_to_cycle")
                        .withStyle(ChatFormatting.YELLOW));
                }
                boolean instabuild = state.menu().player.getAbilities().instabuild;
                ItemStack slotCatalyst = state.menu().get_cost_item();
                for (int optIdx = 0; optIdx < options.size(); optIdx++) {
                    List<ItemStack> bundle = options.get(optIdx);
                    if (bundle == null || bundle.isEmpty()) continue;
                    if (bundle.size() == 1) {
                        ItemStack st = bundle.getFirst();
                        int inSlot = (!slotCatalyst.isEmpty() && ItemStack.isSameItemSameComponents(slotCatalyst, st))
                            ? slotCatalyst.getCount()
                            : 0;
                        boolean slotMet = instabuild || inSlot >= st.getCount();
                        MutableComponent bullet = Component.literal(" \u2022 ")
                            .withStyle(slotMet ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                            .append(Component.literal(st.getCount() + "\u00d7 ").append(st.getHoverName())
                                .withStyle(options.size() > 1 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
                        if (inSlot > 0 || slotMet) {
                            bullet.append(Component.literal(" (" + (instabuild ? st.getCount() : inSlot) + "/" + st.getCount() + ")")
                                .withStyle(slotMet ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                        }
                        lines.add(bullet);
                    } else {
                        if (options.size() > 1) {
                            lines.add(Component.translatable(
                                "gui.chiseled_enchanting_table.cost_bundle.option_label",
                                optIdx + 1
                            ).withStyle(ChatFormatting.LIGHT_PURPLE));
                        }
                        ItemStack primary = bundle.getFirst();
                        int slotHave = (!slotCatalyst.isEmpty() && ItemStack.isSameItemSameComponents(slotCatalyst, primary))
                            ? slotCatalyst.getCount()
                            : 0;
                        boolean primaryMet = instabuild || slotHave >= primary.getCount();
                        lines.add(Component.translatable(
                            "gui.chiseled_enchanting_table.cost_bundle.slot_item",
                            primary.getCount(),
                            primary.getHoverName(),
                            instabuild ? primary.getCount() : slotHave,
                            primary.getCount(),
                            primaryMet ? "\u2714" : "\u2716"
                        ).withStyle(primaryMet ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                        for (int idx = 1; idx < bundle.size(); idx++) {
                            ItemStack extra = bundle.get(idx);
                            int invHave = state.countMatching(extra);
                            boolean extraMet = instabuild || invHave >= extra.getCount();
                            lines.add(Component.translatable(
                                "gui.chiseled_enchanting_table.cost_bundle.inventory_item",
                                extra.getCount(),
                                extra.getHoverName(),
                                instabuild ? extra.getCount() : invHave,
                                extra.getCount(),
                                extraMet ? "\u2714" : "\u2716"
                            ).withStyle(extraMet ? ChatFormatting.AQUA : ChatFormatting.RED));
                        }
                    }
                }
            }
            Identifier hoveredEnchId = state.list().hoveredEnchantmentId(mouseX, mouseY);
            var hoveredOptType = state.list().hoveredOptionType(mouseX, mouseY);
            if (hoveredEnchId != null && hoveredOptType != null) {
                int storedLvl = state.menu().storedMasterLevel(hoveredEnchId);
                int specBonus = state.menu().specificEnchantDiscountBonus(hoveredEnchId);
                int totalDisc = state.menu().effectiveXpDiscountPercent(hoveredEnchId, hoveredOptType);
                if (state.menu().isLibraryDiscountEnabled() && storedLvl > 0 && specBonus > 0) {
                    int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(
                        state.level(),
                        hoveredEnchId
                    );
                    lines.add(Component.translatable(
                        "gui.chiseled_enchanting_table.resonance.specific_line",
                        storedLvl,
                        maxLvl,
                        specBonus,
                        totalDisc
                    ).withStyle(ChatFormatting.AQUA));
                }
            }
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx,
                state.font(),
                lines,
                hoveredCost.getTooltipImage(),
                mouseX,
                mouseY,
                hoveredCost.get(DataComponents.TOOLTIP_STYLE)
            );
            return true;
        }
        var hoveredXpEntry = state.list().hoveredXpBadgeEntry(mouseX, mouseY);
        if (hoveredXpEntry != null) {
            var req = hoveredXpEntry.xp_requirement();
            int playerLvl = state.menu().player.experienceLevel;
            boolean met = hoveredXpEntry.xpConditionMet();
            List<Component> xpTip = new ArrayList<>();
            if (req.isRefund()) {
                xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.title")
                    .withStyle(ChatFormatting.GREEN));
                xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.amount", req.refundPoints())
                    .withStyle(ChatFormatting.AQUA));
                xpTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.xp_mode.refund.hint",
                    (int) Math.round(state.menu().disenchantExternalRefundScale() * 100.0)
                ).withStyle(ChatFormatting.GRAY));
            } else if (req.requiredLevel() <= 0 && req.deductedLevels() <= 0) {
                if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(hoveredXpEntry.enchantment_id())) {
                    xpTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.xp_mode.free_tome",
                        state.menu().shelfGatesConfig().tier1Bookshelves()
                    ).withStyle(ChatFormatting.GREEN));
                } else {
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.free")
                        .withStyle(ChatFormatting.GREEN));
                }
            } else if (req.vanillaTierMode()) {
                xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.vanilla.title")
                    .withStyle(ChatFormatting.GREEN));
                xpTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.xp_mode.vanilla.threshold",
                    req.requiredLevel(),
                    playerLvl
                ).withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
                xpTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.xp_mode.vanilla.consume",
                    req.deductedLevels()
                ).withStyle(ChatFormatting.GOLD));
                if (req.discountPercent() > 0) {
                    xpTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.xp_mode.discount",
                        req.discountPercent(),
                        req.rawRequiredLevel()
                    ).withStyle(ChatFormatting.AQUA));
                }
            } else {
                xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.full.title")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                xpTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.xp_mode.full.consume",
                    req.deductedLevels(),
                    playerLvl
                ).withStyle(met ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED));
                if (req.discountPercent() > 0) {
                    xpTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.xp_mode.discount",
                        req.discountPercent(),
                        req.rawRequiredLevel()
                    ).withStyle(ChatFormatting.AQUA));
                }
                // 1.0.3 Classic: storing the book unlocks the level but does NOT
                // switch to 1-3 level mode, so the hint would contradict the
                // "Full XP Level Cost" title directly above it.
                if (hoveredXpEntry.option_type() == OptionType.ENCHANTMENT && !state.menu().usesClassicFullPointXp()) {
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.full.master_hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), xpTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        return false;
    }
}
