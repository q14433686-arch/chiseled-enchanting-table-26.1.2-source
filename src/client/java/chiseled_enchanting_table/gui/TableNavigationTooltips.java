package chiseled_enchanting_table.gui;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import chiseled_enchanting_table.compat.CetFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import chiseled_enchanting_table.gui.TableTooltipContext.Region;

/** Read-only tooltip family; action dispatch remains at the screen boundary. */
final class TableNavigationTooltips {
    private TableNavigationTooltips() {}
    static boolean render(TableTooltipContext state, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {

        if (state.hovered(Region.isOverConfigButton)) {
            List<Component> tip = List.of(
                Component.translatable("config.chiseled_enchanting_table.open_tip.title").withStyle(CetFormatting.GOLD),
                Component.translatable("config.chiseled_enchanting_table.open_tip.sub").withStyle(CetFormatting.GRAY)
            );
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverGrimoireButton)) {
            List<Component> tip = List.of(
                Component.translatable("gui.chiseled_enchanting_table.grimoire.open_btn.title").withStyle(CetFormatting.GOLD),
                Component.translatable("gui.chiseled_enchanting_table.grimoire.open_btn.desc").withStyle(CetFormatting.GRAY)
            );
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverShelfHudBadge)) {
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx,
                state.font(),
                state.resonance().get(),
                Optional.empty(),
                mouseX,
                mouseY,
                (Identifier) null
            );
            return true;
        }
        if (state.hovered(Region.isOverInscribeTab)) {
            List<Component> tip = List.of(
                Component.translatable("gui.chiseled_enchanting_table.tab.inscribe.title").withStyle(CetFormatting.GOLD),
                Component.translatable(state.menu().usesClassicShelfBoost()
                    ? "gui.chiseled_enchanting_table.tab.inscribe.desc_classic"
                    : "gui.chiseled_enchanting_table.tab.inscribe.desc").withStyle(CetFormatting.GRAY)
            );
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverCleanseTab) && !state.menu().isCleansePageUnlocked()) {
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), List.of(Component.translatable("gui.chiseled_enchanting_table.tab.locked_tooltip").withStyle(CetFormatting.RED)), Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverTomeTab)) {
            List<Component> tip = state.menu().isTomePageUnlocked()
                ? List.of(Component.translatable("gui.chiseled_enchanting_table.tab.tomes.title").withStyle(CetFormatting.AQUA), Component.translatable("gui.chiseled_enchanting_table.tab.tomes.desc").withStyle(CetFormatting.GRAY))
                : List.of(Component.translatable("gui.chiseled_enchanting_table.tab.locked_tooltip").withStyle(CetFormatting.RED));
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverSacrificeButton)) {
            List<Component> tip = List.of(
                Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_title").withStyle(CetFormatting.GOLD),
                Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_desc", state.menu().sacrificeProgress, state.menu().sacrificeRequired).withStyle(CetFormatting.GRAY)
            );
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        int tomeBtn = state.tomeButton();
        if (tomeBtn >= 0) {
            String key = switch (tomeBtn) {
                case 0 -> "gui.chiseled_enchanting_table.tomes.tip_next";
                case 1 -> "gui.chiseled_enchanting_table.tomes.tip_full";
                case 2 -> "gui.chiseled_enchanting_table.tomes.tip_permanent";
                case 3 -> "gui.chiseled_enchanting_table.tomes.tip_unlock";
                default -> "gui.chiseled_enchanting_table.catalog.batch_help";
            };
            var lrCfg = state.menu().libraryResonanceConfig();
            var tipText = tomeBtn == 3
                ? Component.translatable(key,
                    lrCfg.earlyUnlockXpLevels(),
                    lrCfg.earlyUnlockGritCount())
                : Component.translatable(key);
            String titleKey = switch(tomeBtn) {
                case 0 -> "gui.chiseled_enchanting_table.tomes.btn_next";
                case 1 -> "gui.chiseled_enchanting_table.tomes.btn_full";
                case 2 -> "gui.chiseled_enchanting_table.tomes.btn_permanent";
                case 3 -> "gui.chiseled_enchanting_table.tomes.btn_unlock";
                default -> "gui.chiseled_enchanting_table.catalog.batch_full";
            };
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable(titleKey).withStyle(CetFormatting.AQUA));
            lines.add(tipText.withStyle(CetFormatting.GRAY));
            if(tomeBtn!=4) lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.batch_help").withStyle(CetFormatting.GOLD));
            TooltipPresentation.show(ctx,state.font(),lines,Optional.empty(),mouseX,mouseY,null);
            return true;
        }
        var hoveredTome = state.list().hoveredTomeEntry(mouseX, mouseY);
        if (hoveredTome != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(TomeDisplayNames.tomeTitle(hoveredTome, state.menu().player)).withStyle(CetFormatting.GOLD));
            if (TooltipPresentation.detailsRequested()) {
                TomeEnchantmentDetails.render(ctx, state.font(), hoveredTome, state.menu().player, mouseX, mouseY);
                return true;
            }
            tip.add(Component.translatable("gui.chiseled_enchanting_table.catalog.shift_details").withStyle(CetFormatting.GRAY));
            var wearImmuneEntry = hoveredTome.enchantments().stream()
                .filter(e -> EnchantmentDisplayNames.isWearImmuneEnchantmentId(e.enchantment_id(), state.menu().player))
                .findFirst();
            boolean purelyImmune = hoveredTome.wear_immune()
                && hoveredTome.wear_used() <= 0
                && hoveredTome.remaining_charges() >= hoveredTome.effective_max_uses();
            if (wearImmuneEntry.isPresent() && hoveredTome.wear_immune()) {
                var immuneId = wearImmuneEntry.get().enchantment_id();
                EnchantmentDisplayNames.findEnchantment(immuneId, state.menu().player).ifPresent(h -> {
                    tip.add(net.minecraft.world.item.enchantment.Enchantment.getFullname(h, wearImmuneEntry.get().enchantment_level())
                        .copy().withStyle(CetFormatting.GRAY));
                    String descKey = "enchantment." + immuneId.getNamespace() + "." + immuneId.getPath() + ".desc";
                    if (net.minecraft.locale.Language.getInstance().has(descKey)) {
                        tip.add(Component.translatable(descKey).withStyle(CetFormatting.DARK_GRAY));
                    }
                });
                tip.add(Component.translatable("item.chiseled_enchanting_table.master_tome_infinite")
                    .withStyle(CetFormatting.LIGHT_PURPLE));
            }
            if (!purelyImmune) {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.tomes.charges",
                    hoveredTome.remaining_charges(),
                    hoveredTome.effective_max_uses()
                ).withStyle(CetFormatting.AQUA));
            }
            if (!hoveredTome.last_used_enchant().isBlank() && hoveredTome.last_used_level() > 0) {
                var lastId = Identifier.tryParse(hoveredTome.last_used_enchant());
                String lastName = lastId == null ? hoveredTome.last_used_enchant()
                    : EnchantmentDisplayNames.findEnchantment(lastId, state.menu().player)
                        .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h, hoveredTome.last_used_level()).getString())
                        .orElse(hoveredTome.last_used_enchant());
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.last_used", lastName).withStyle(CetFormatting.LIGHT_PURPLE));
            }
            if (hoveredTome.deplete_outcome() > 0 && !hoveredTome.deplete_enchant().isBlank()) {
                var depId = Identifier.tryParse(hoveredTome.deplete_enchant());
                java.util.function.IntFunction<String> depName = lvl -> depId == null
                    ? hoveredTome.deplete_enchant()
                    : EnchantmentDisplayNames.findEnchantment(depId, state.menu().player)
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
                    tip.add(depleteLine.withStyle(CetFormatting.GOLD));
                }
            }
            if (hoveredTome.locked()) {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.locked", Component.translatable("gui.chiseled_enchanting_table.tomes.lock_mode." + hoveredTome.lock_mode()).getString(), hoveredTome.lock_owner_name()).withStyle(CetFormatting.RED));
                int lockEta = hoveredTome.lock_mode() == 0 ? hoveredTome.eta_next_ticks()
                    : hoveredTome.lock_mode() == 1 ? hoveredTome.eta_full_ticks() : -1;
                if (lockEta >= 0) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_lock_remaining", TomeDisplayNames.formatTomeEta(lockEta)).withStyle(CetFormatting.GOLD));
                }
            }
            if (hoveredTome.charging()) {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_eta_next", TomeDisplayNames.formatTomeEta(hoveredTome.eta_next_ticks())).withStyle(CetFormatting.GREEN));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_eta_full", TomeDisplayNames.formatTomeEta(hoveredTome.eta_full_ticks())).withStyle(CetFormatting.GREEN));
            }
            tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.select_hint").withStyle(CetFormatting.DARK_GRAY));
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverSacrificeSkipButton)) {
            int lvls = state.menu().currentSacrificeSkipLevels();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_title")
                .withStyle(CetFormatting.GOLD));
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.ritual.tip_sacrifice_cost",
                lvls,
                state.menu().player.experienceLevel
            ).withStyle(state.menu().player.experienceLevel >= lvls ? CetFormatting.GREEN : CetFormatting.RED));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_safe")
                .withStyle(CetFormatting.AQUA));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_currency")
                .withStyle(CetFormatting.DARK_GRAY));
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverRushSkipButton)) {
            double riskPct = state.menu().currentRushSkipDegradeChance() * 100.0;
            var rCfg = state.menu().ritualConfig();
            int capPct = (int) Math.round(100.0 * (state.menu().ritualUsedInfinityTome()
                ? rCfg.maxRushDegradeInfinity()
                : rCfg.maxRushDegradeNormal()));
            int infCapPct = (int) Math.round(100.0 * rCfg.maxRushDegradeInfinity());
            boolean hasTome = state.menu().hasAnyMasterTomeInRange();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_title")
                .withStyle(CetFormatting.RED));
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.ritual.tip_rush_risk",
                String.format(java.util.Locale.ROOT, "%.1f", riskPct),
                capPct
            ).withStyle(CetFormatting.GOLD));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_priority")
                .withStyle(CetFormatting.GRAY));
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.ritual.tip_rush_currency",
                infCapPct
            ).withStyle(CetFormatting.DARK_GRAY));
            if (!hasTome) {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.no_tome_cannot_skip")
                    .withStyle(CetFormatting.RED));
            }
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverCleanseTab)) {
            List<Component> tip = List.of(
                Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.title").withStyle(CetFormatting.AQUA),
                Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.desc").withStyle(CetFormatting.GRAY)
            );
            chiseled_enchanting_table.gui.TooltipPresentation.show(ctx, state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }

        return false;
    }
}
