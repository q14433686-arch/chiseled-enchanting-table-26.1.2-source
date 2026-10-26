package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Tome directory entry tooltip composition. */
final class TablePanelTooltips {
    private TablePanelTooltips() {}

    static List<Component> tomeEntryTip(ChiseledEnchantingTableScreenHandler menu, TomeBookEntry hoveredTome) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(EnchantementListWidget.tomeTitle(hoveredTome, menu.player)).withStyle(ChatFormatting.GOLD));
                var wearImmuneEntry = hoveredTome.enchantments().stream()
                    .filter(e -> EnchantementListWidget.isWearImmuneEnchantmentId(e.enchantment_id(), menu.player))
                    .findFirst();
                boolean purelyImmune = hoveredTome.wear_immune()
                    && hoveredTome.wear_used() <= 0
                    && hoveredTome.remaining_charges() >= hoveredTome.effective_max_uses();
                if (wearImmuneEntry.isPresent() && hoveredTome.wear_immune()) {
                    var immuneId = wearImmuneEntry.get().enchantment_id();
                    EnchantementListWidget.findEnchantment(immuneId, menu.player).ifPresent(h -> {
                        tip.add(net.minecraft.world.item.enchantment.Enchantment.getFullname(h, wearImmuneEntry.get().enchantment_level())
                            .copy().withStyle(ChatFormatting.GRAY));
                        String descKey = "enchantment." + immuneId.getNamespace() + "." + immuneId.getPath() + ".desc";
                        if (net.minecraft.locale.Language.getInstance().has(descKey)) {
                            tip.add(Component.translatable(descKey).withStyle(ChatFormatting.DARK_GRAY));
                        }
                    });
                    tip.add(Component.translatable("item.chiseled_enchanting_table.master_tome_infinite")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                if (!purelyImmune) {
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.tomes.charges",
                        hoveredTome.remaining_charges(),
                        hoveredTome.effective_max_uses()
                    ).withStyle(ChatFormatting.AQUA));
                }
                if (!hoveredTome.last_used_enchant().isBlank() && hoveredTome.last_used_level() > 0) {
                    var lastId = Identifier.tryParse(hoveredTome.last_used_enchant());
                    String lastName = lastId == null ? hoveredTome.last_used_enchant()
                        : EnchantementListWidget.findEnchantment(lastId, menu.player)
                            .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h, hoveredTome.last_used_level()).getString())
                            .orElse(hoveredTome.last_used_enchant());
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.last_used", lastName).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                if (hoveredTome.deplete_outcome() > 0 && !hoveredTome.deplete_enchant().isBlank()) {
                    var depId = Identifier.tryParse(hoveredTome.deplete_enchant());
                    java.util.function.IntFunction<String> depName = lvl -> depId == null
                        ? hoveredTome.deplete_enchant()
                        : EnchantementListWidget.findEnchantment(depId, menu.player)
                            .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h, lvl).getString())
                            .orElse(hoveredTome.deplete_enchant());
                    int depLevel = hoveredTome.deplete_level();
                    var depleteLine = switch (hoveredTome.deplete_outcome()) {
                        case 1 -> Component.translatable("gui.chiseled_enchanting_table.tomes.deplete_step_down", depName.apply(Math.max(1, depLevel - 1)));
                        case 2 -> Component.translatable("gui.chiseled_enchanting_table.tomes.deplete_depleted", depName.apply(depLevel));
                        case 3 -> Component.translatable("gui.chiseled_enchanting_table.tomes.deplete_lose", depName.apply(depLevel));
                        case 4 -> Component.translatable("gui.chiseled_enchanting_table.tomes.consume_lose", depName.apply(depLevel));
                        case 5 -> Component.translatable("gui.chiseled_enchanting_table.tomes.consume_depleted", depName.apply(depLevel));
                        default -> null;
                    };
                    if (depleteLine != null) {
                        tip.add(depleteLine.withStyle(ChatFormatting.GOLD));
                    }
                }
                if (hoveredTome.locked()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.locked", Component.translatable("gui.chiseled_enchanting_table.tomes.lock_mode." + hoveredTome.lock_mode()).getString(), hoveredTome.lock_owner_name()).withStyle(ChatFormatting.RED));
                    int lockEta = hoveredTome.lock_mode() == 0 ? hoveredTome.eta_next_ticks()
                        : hoveredTome.lock_mode() == 1 ? hoveredTome.eta_full_ticks() : -1;
                    if (lockEta >= 0) {
                        tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_lock_remaining", EnchantementListWidget.formatTomeEta(lockEta)).withStyle(ChatFormatting.GOLD));
                    }
                }
                if (hoveredTome.charging()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_eta_next", EnchantementListWidget.formatTomeEta(hoveredTome.eta_next_ticks())).withStyle(ChatFormatting.GREEN));
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_eta_full", EnchantementListWidget.formatTomeEta(hoveredTome.eta_full_ticks())).withStyle(ChatFormatting.GREEN));
                }
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.select_hint").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }
}
