package chiseled_enchanting_table.gui;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;


/** Read-only tooltip family; action dispatch remains at the screen boundary. */
final class TableOptionTooltips {
    private TableOptionTooltips() {}
    static boolean render(TableTooltipContext state, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        var specialEntry = state.list().hoveredSpecialRowEntry(mouseX, mouseY);
        if (specialEntry != null) {
            if (specialEntry.discovery_locked) {
                String lockedOut = state.menu().syncedTransformationOutputName(specialEntry.enchantment_id);
                List<Component> discTip = new ArrayList<>();
                discTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.discovery_locked.tip_title",
                    lockedOut
                ).withStyle(ChatFormatting.GOLD));
                discTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.discovery_locked.tip_desc",
                    lockedOut
                ).withStyle(ChatFormatting.YELLOW));
                ctx.setTooltipForNextFrame(state.font(), discTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return true;
            }
            if (specialEntry.shelf_locked && specialEntry.enchantment != null) {
                String lockedName = net.minecraft.world.item.enchantment.Enchantment
                    .getFullname(specialEntry.enchantment, specialEntry.enchant_level)
                    .getString();
                List<Component> lockTip = new ArrayList<>();
                lockTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.shelf_locked.tip_title",
                    lockedName
                ).withStyle(ChatFormatting.GOLD));
                if (state.menu().regularBookshelves < specialEntry.required_bookshelves) {
                    lockTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.shelf_locked.tip_req",
                        state.menu().regularBookshelves,
                        specialEntry.required_bookshelves
                    ).withStyle(ChatFormatting.RED));
                } else {
                    lockTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.shelf_locked.tip_blank_cap"
                    ).withStyle(ChatFormatting.YELLOW));
                }
                lockTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.shelf_locked.tip_bypass"
                ).withStyle(ChatFormatting.GRAY));
                ctx.setTooltipForNextFrame(state.font(), lockTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return true;
            }
            if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(specialEntry.enchantment_id)) {
                var dtTier = state.menu().depletedTomeTier();
                var sg = state.menu().shelfGatesConfig();
                int shelves = state.menu().regularBookshelves;
                int qualifyingEnchants = state.menu().qualifyingChiseledEnchantments();
                List<Component> dtTip = new ArrayList<>();
                dtTip.add(Component.translatable("gui.chiseled_enchanting_table.depleted_tome.tip_title")
                    .withStyle(ChatFormatting.GOLD));
                dtTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.depleted_tome.tip_g1",
                    shelves,
                    sg.tier1Bookshelves()
                ).withStyle(dtTier.ordinal() >= 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                dtTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.depleted_tome.tip_g2",
                    shelves,
                    sg.tier2Bookshelves()
                ).withStyle(dtTier.ordinal() >= 2 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                dtTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.depleted_tome.tip_g3",
                    shelves,
                    sg.tier2Bookshelves(),
                    qualifyingEnchants,
                    sg.tier3QualifyingEnchantments()
                ).withStyle(dtTier == chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.DepletedTomeTier.TIER_3_FREE
                    ? ChatFormatting.LIGHT_PURPLE
                    : ChatFormatting.GRAY));
                ctx.setTooltipForNextFrame(state.font(), dtTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return true;
            }
            if (specialEntry.is_category_header && !specialEntry.cleanse_theme) {
                int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(
                    state.level(),
                    specialEntry.enchantment_id
                );
                var sg = state.menu().shelfGatesConfig();
                int shelves = state.menu().regularBookshelves;
                int storedLvl = state.menu().storedMasterLevel(specialEntry.enchantment_id);
                int existingLvl = state.menu().currentTargetEnchantLevel(specialEntry.enchantment_id);
                int directCap = state.menu().maxUnlockedDirectTier(specialEntry.enchantment_id, maxLvl);
                int baseCap = existingLvl >= 1
                    ? directCap
                    : state.menu().maxBlankWoodenTier(specialEntry.enchantment_id, maxLvl);
                int effectiveCap = Math.min(maxLvl, Math.max(storedLvl, baseCap));
                List<Component> catTip = new ArrayList<>();
                catTip.add(Component.literal(specialEntry.category_title + "  " + specialEntry.category_badge)
                    .withStyle(ChatFormatting.GOLD));
                catTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.category.tip_cap",
                    effectiveCap,
                    maxLvl,
                    shelves,
                    sg.maxDirectBookshelves()
                ).withStyle(effectiveCap >= maxLvl ? ChatFormatting.GREEN : ChatFormatting.AQUA));
                if (effectiveCap < maxLvl) {
                    int nextTier = effectiveCap + 1;
                    int reqShelves = state.menu().requiredBookshelvesForDirectTier(
                        specialEntry.enchantment_id,
                        nextTier,
                        maxLvl
                    );
                    if (shelves < reqShelves) {
                        catTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.category.tip_next",
                            nextTier,
                            reqShelves,
                            sg.maxDirectBookshelves()
                        ).withStyle(ChatFormatting.YELLOW));
                    } else {
                        catTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.shelf_locked.tip_blank_cap"
                        ).withStyle(ChatFormatting.YELLOW));
                    }
                }
                catTip.add(Component.translatable("gui.chiseled_enchanting_table.category.tip_master")
                    .withStyle(ChatFormatting.DARK_GRAY));
                ctx.setTooltipForNextFrame(state.font(), catTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return true;
            }
        }
        var hoveredRow = state.list().hoveredActionRowEntry(mouseX, mouseY);
        if (hoveredRow != null) {
            List<Component> rowTip = new ArrayList<>();
            AvailableOption hoverOpt = new AvailableOption(
                hoveredRow.option_type,
                hoveredRow.enchantment_id,
                hoveredRow.enchant_level
            );
            rowTip.add(Component.literal(state.list().displayName(hoverOpt)).withStyle(ChatFormatting.GOLD));
            boolean fromBook = state.menu().get_cost_item().is(net.minecraft.world.item.Items.ENCHANTED_BOOK)
                && hoveredRow.option_type == OptionType.ENCHANTMENT;
            var lrCfg = state.menu().libraryResonanceConfig();
            if (fromBook) {
                rowTip.add(Component.translatable(state.menu().returnsPlainDrainedBook()
                    ? "gui.chiseled_enchanting_table.pricing.book_transfer_classic"
                    : "gui.chiseled_enchanting_table.pricing.book_transfer")
                    .withStyle(ChatFormatting.GREEN));
            } else if (state.menu().usesClassicFullPointXp()
                && !state.menu().usesIncrementalCredit()
                && !hoveredRow.is_transformation
                && hoveredRow.option_type != OptionType.TREASURE_EPIPHANY
                && hoveredRow.option_type != OptionType.DISENCHANT_ALL
                && hoveredRow.option_type != OptionType.DOWNGRADE_ENCHANTMENT
                && hoveredRow.option_type != OptionType.STRIP_SINGLE_ENCHANTMENT
                && hoveredRow.option_type != OptionType.PURGE_CURSE) {
                // 1.0.3 Classic pricing source: full target-level XP points +
                // full materials, whether or not a stored book unlocked the
                // level. Previously this branch said "Stored Template Book
                // (Vanilla 1-3L Mode)" / "Step Upgrade (difference only)",
                // contradicting the XP badge's "Full XP Level Cost" rule.
                rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.classic_1_0_3")
                    .withStyle(ChatFormatting.GREEN));
            } else if (hoveredRow.is_transformation) {
                rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.transformation")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                if (hoveredRow.enchantable_item != null && hoveredRow.enchantable_item.getCount() > 1) {
                    int batch = state.menu().resolveTransformationBatchCount(hoveredRow.enchantment_id);
                    int inSlot = hoveredRow.enchantable_item.getCount();
                    rowTip.add(Component.translatable(
                        state.menu().player.getAbilities().instabuild
                            ? "gui.chiseled_enchanting_table.transformation.batch_tip_creative"
                            : "gui.chiseled_enchanting_table.transformation.batch_tip_survival",
                        batch,
                        inSlot
                    ).withStyle(ChatFormatting.AQUA));
                }
            } else if (hoveredRow.option_type == OptionType.TREASURE_EPIPHANY) {
                rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.epiphany")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (hoveredRow.option_type == OptionType.DISENCHANT_ALL
                || hoveredRow.option_type == OptionType.DOWNGRADE_ENCHANTMENT) {
                int extPct = (int) Math.round(state.menu().disenchantExternalRefundScale() * 100.0);
                rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.cleanse_provenance", extPct)
                    .withStyle(ChatFormatting.AQUA));
            } else if (hoveredRow.option_type == OptionType.STRIP_SINGLE_ENCHANTMENT
                || hoveredRow.option_type == OptionType.PURGE_CURSE) {
                rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.cleanse_targeted")
                    .withStyle(ChatFormatting.AQUA));
            } else {
                boolean masterBacked = state.menu().storedMasterLevel(hoveredRow.enchantment_id) >= hoveredRow.enchant_level;
                if (masterBacked) {
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.master_tome")
                        .withStyle(ChatFormatting.GREEN));
                    boolean targetIsBook = hoveredRow.enchantable_item.is(net.minecraft.world.item.Items.BOOK)
                        || hoveredRow.enchantable_item.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);
                    int wearCost = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.wearCostForTarget(lrCfg, targetIsBook);
                    if (wearCost > 0) {
                        int liveRemaining = state.menu().masterTomeRemainingCharges(hoveredRow.enchantment_id, hoveredRow.enchant_level);
                        if (liveRemaining == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.IMMORTAL_CHARGES) {
                            rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.tome_wear_infinite")
                                .withStyle(ChatFormatting.LIGHT_PURPLE));
                        } else {
                            int maxUses = Math.max(1, lrCfg.maxTomeUses());
                            float ratio = (float) liveRemaining / (float) maxUses;
                            ChatFormatting chargeColor = ratio > 0.5f
                                ? ChatFormatting.AQUA
                                : (ratio > 0.25f ? ChatFormatting.GOLD : ChatFormatting.RED);
                            rowTip.add(Component.translatable(
                                "gui.chiseled_enchanting_table.pricing.tome_wear_live",
                                liveRemaining,
                                maxUses,
                                wearCost
                            ).withStyle(chargeColor));
                            if (lrCfg.tomeRegenEnabled()) {
                                int nearShelves = state.menu().totalLibraryBookshelves();
                                double regenSec = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                                    .computeRegenSeconds(lrCfg, nearShelves);
                                int regenTier = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                                    .regenTierIndex(lrCfg, nearShelves);
                                Component tierBadge = switch (regenTier) {
                                    case 2 -> Component.translatable(
                                        "gui.chiseled_enchanting_table.pricing.tome_regen_tier2",
                                        lrCfg.tomeRegenTier2Shelves()
                                    );
                                    case 1 -> Component.translatable(
                                        "gui.chiseled_enchanting_table.pricing.tome_regen_tier1",
                                        lrCfg.tomeRegenTier1Shelves()
                                    );
                                    default -> Component.translatable(
                                        "gui.chiseled_enchanting_table.pricing.tome_regen_tier0",
                                        lrCfg.tomeRegenTier1Shelves()
                                    );
                                };
                                rowTip.add(Component.translatable(
                                    "gui.chiseled_enchanting_table.pricing.tome_regen_rate",
                                    String.format(java.util.Locale.ROOT, "%.1f", regenSec / 1200.0),
                                    nearShelves,
                                    tierBadge
                                ).withStyle(regenTier >= 2
                                    ? ChatFormatting.GOLD
                                    : (regenTier == 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
                            }
                        }
                    }
                } else if (hoveredRow.existingSameLevel.isPresent() && hoveredRow.existingSameLevel.getAsInt() > 0) {
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.incremental")
                        .withStyle(ChatFormatting.AQUA));
                } else {
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.full_ritual")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            if (hoveredRow.conflict_refund_points > 0) {
                rowTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.conflict_refund",
                    hoveredRow.conflict_refund_points
                ).withStyle(ChatFormatting.GREEN));
            }
            int rowDisc = state.menu().effectiveXpDiscountPercent(hoveredRow.enchantment_id, hoveredRow.option_type);
            if (rowDisc > 0 && !hoveredRow.xp_requirement.isRefund() && !fromBook) {
                rowTip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.resonance_discount",
                    rowDisc
                ).withStyle(ChatFormatting.DARK_AQUA));
            }
            ctx.setTooltipForNextFrame(state.font(), rowTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            return true;
        }
        return false;
    }
}
