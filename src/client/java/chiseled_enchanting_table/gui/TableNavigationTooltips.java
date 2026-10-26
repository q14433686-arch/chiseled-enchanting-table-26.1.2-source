package chiseled_enchanting_table.gui;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
                Component.translatable("config.chiseled_enchanting_table.open_tip.title").withStyle(ChatFormatting.GOLD),
                Component.translatable("config.chiseled_enchanting_table.open_tip.sub").withStyle(ChatFormatting.GRAY)
            );
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverGrimoireButton)) {
            List<Component> tip = List.of(
                Component.translatable("gui.chiseled_enchanting_table.grimoire.open_btn.title").withStyle(ChatFormatting.GOLD),
                Component.translatable("gui.chiseled_enchanting_table.grimoire.open_btn.desc").withStyle(ChatFormatting.GRAY)
            );
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverShelfHudBadge)) {
            ctx.setTooltipForNextFrame(
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
                Component.translatable("gui.chiseled_enchanting_table.tab.inscribe.title").withStyle(ChatFormatting.GOLD),
                Component.translatable(state.menu().usesClassicShelfBoost()
                    ? "gui.chiseled_enchanting_table.tab.inscribe.desc_classic"
                    : "gui.chiseled_enchanting_table.tab.inscribe.desc").withStyle(ChatFormatting.GRAY)
            );
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverCleanseTab) && !state.menu().isCleansePageUnlocked()) {
            ctx.setTooltipForNextFrame(state.font(), List.of(Component.translatable("gui.chiseled_enchanting_table.tab.locked_tooltip").withStyle(ChatFormatting.RED)), Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverTomeTab)) {
            List<Component> tip = state.menu().isTomePageUnlocked()
                ? List.of(Component.translatable("gui.chiseled_enchanting_table.tab.tomes.title").withStyle(ChatFormatting.AQUA), Component.translatable("gui.chiseled_enchanting_table.tab.tomes.desc").withStyle(ChatFormatting.GRAY))
                : List.of(Component.translatable("gui.chiseled_enchanting_table.tab.locked_tooltip").withStyle(ChatFormatting.RED));
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverSacrificeButton)) {
            List<Component> tip = List.of(
                Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_title").withStyle(ChatFormatting.GOLD),
                Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_desc", state.menu().sacrificeProgress, state.menu().sacrificeRequired).withStyle(ChatFormatting.GRAY)
            );
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        int tomeBtn = state.tomeButton();
        if (tomeBtn >= 0) {
            String key = switch (tomeBtn) {
                case 0 -> "gui.chiseled_enchanting_table.tomes.tip_next";
                case 1 -> "gui.chiseled_enchanting_table.tomes.tip_full";
                case 2 -> "gui.chiseled_enchanting_table.tomes.tip_permanent";
                case 3 -> "gui.chiseled_enchanting_table.tomes.tip_unlock";
                default -> "gui.chiseled_enchanting_table.tomes.tip_lock_all";
            };
            var lrCfg = state.menu().libraryResonanceConfig();
            var tipText = tomeBtn == 3
                ? Component.translatable(key,
                    lrCfg.earlyUnlockXpLevels(),
                    lrCfg.earlyUnlockGritCount())
                : Component.translatable(key);
            ctx.setTooltipForNextFrame(state.font(), List.of(tipText.withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        var hoveredTome = state.list().hoveredTomeEntry(mouseX, mouseY);
        if (hoveredTome != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(EnchantementListWidget.tomeTitle(hoveredTome, state.menu().player)).withStyle(ChatFormatting.GOLD));
            var wearImmuneEntry = hoveredTome.enchantments().stream()
                .filter(e -> EnchantementListWidget.isWearImmuneEnchantmentId(e.enchantment_id(), state.menu().player))
                .findFirst();
            boolean purelyImmune = hoveredTome.wear_immune()
                && hoveredTome.wear_used() <= 0
                && hoveredTome.remaining_charges() >= hoveredTome.effective_max_uses();
            if (wearImmuneEntry.isPresent() && hoveredTome.wear_immune()) {
                var immuneId = wearImmuneEntry.get().enchantment_id();
                EnchantementListWidget.findEnchantment(immuneId, state.menu().player).ifPresent(h -> {
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
                    : EnchantementListWidget.findEnchantment(lastId, state.menu().player)
                        .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h, hoveredTome.last_used_level()).getString())
                        .orElse(hoveredTome.last_used_enchant());
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.last_used", lastName).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (hoveredTome.deplete_outcome() > 0 && !hoveredTome.deplete_enchant().isBlank()) {
                var depId = Identifier.tryParse(hoveredTome.deplete_enchant());
                java.util.function.IntFunction<String> depName = lvl -> depId == null
                    ? hoveredTome.deplete_enchant()
                    : EnchantementListWidget.findEnchantment(depId, state.menu().player)
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
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverSacrificeSkipButton)) {
            int lvls = state.menu().currentSacrificeSkipLevels();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_title")
                .withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.ritual.tip_sacrifice_cost",
                lvls,
                state.menu().player.experienceLevel
            ).withStyle(state.menu().player.experienceLevel >= lvls ? ChatFormatting.GREEN : ChatFormatting.RED));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_safe")
                .withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_currency")
                .withStyle(ChatFormatting.DARK_GRAY));
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
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
                .withStyle(ChatFormatting.RED));
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.ritual.tip_rush_risk",
                String.format(java.util.Locale.ROOT, "%.1f", riskPct),
                capPct
            ).withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_priority")
                .withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.ritual.tip_rush_currency",
                infCapPct
            ).withStyle(ChatFormatting.DARK_GRAY));
            if (!hasTome) {
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.no_tome_cannot_skip")
                    .withStyle(ChatFormatting.RED));
            }
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        if (state.hovered(Region.isOverCleanseTab)) {
            List<Component> tip = List.of(
                Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.title").withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.desc").withStyle(ChatFormatting.GRAY)
            );
            ctx.setTooltipForNextFrame(state.font(), tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }

        return false;
    }
}
