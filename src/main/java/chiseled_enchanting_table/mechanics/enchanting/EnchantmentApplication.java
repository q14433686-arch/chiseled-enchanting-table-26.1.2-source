package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.HashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * Real-enchantment gameplay mechanic for the chiseled enchanting menu: applying unlocked
 * enchantments (with Master Tome wear, provenance recording, and conflict overwrite XP conservation
 * refunds) and transferring an enchantment from an enchanted book without provenance laundering.
 */
public final class EnchantmentApplication {
    private EnchantmentApplication() {}

    /** Apply one of the menu's unlocked enchantments (or an active Grand Library Treasure Epiphany) to the enchantable item. */
    public static boolean apply(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        Level world = handler.world();
        ItemStack enchantable_item = handler.get_enchantable_item();
        if (enchantable_item.isEmpty()
            || enchantable_item.getCount() != 1
            || ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            return false;
        }
        ItemStack planningStack = handler.getEffectiveEnchantableItem();
        int enchantment_level = payload.enchantment_level();
        var optEnchantment = ChiseledEnchantingTableScreenHandler.findEnchantment(payload.enchantment_id(), world);
        if (optEnchantment.isEmpty()) {
            return false;
        }
        Holder<Enchantment> enchantment = optEnchantment.get();
        // Audit H-06: validate the requested level against the live definition maximum
        // BEFORE any pricing, receipt or wear side effect can run.
        if (enchantment_level < 1 || enchantment_level > Math.max(1, enchantment.value().getMaxLevel())) {
            return false;
        }
        if (!(planningStack.is(Items.BOOK) || planningStack.is(Items.ENCHANTED_BOOK) || enchantment.value().canEnchant(planningStack))) {
            return false;
        }
        ItemEnchantments preEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(planningStack);
        int existingSameLevel = preEnchantments.getLevel(enchantment);
        // Never allow accidental same-level or downgrade via direct enchant (downgrades are free & grant XP in Cleanse mode).
        if (existingSameLevel >= enchantment_level) {
            return false;
        }

        boolean exist = false;
        if (payload.option_type() == OptionType.TREASURE_EPIPHANY) {
            exist = handler.matchesActiveTreasureEpiphany(payload);
        } else {
            for (EnchantmentWithLevel e : handler.unlocked_enchantements) {
                if (e.enchantment_id().equals(payload.enchantment_id()) && e.enchantment_level() == enchantment_level) {
                    exist = true;
                    break;
                }
            }
            if (!exist && existingSameLevel >= 1 && handler.allowsDirectTierUpgrade()) {
                exist = handler.canDirectUpgradeExistingEnchantment(enchantment, payload.enchantment_id(), existingSameLevel, enchantment_level);
            }
        }
        if (!exist) {
            return false;
        }

        boolean masterTomeUsed = payload.option_type() == OptionType.ENCHANTMENT
            && handler.storedMasterLevel(payload.enchantment_id()) >= enchantment_level;
        boolean targetWasBook = enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK);

        int conflictRefund = computeConflictOverwriteRefund(handler, planningStack, preEnchantments, enchantment);
        var admissionPos = handler.tableBlockPos();
        if (admissionPos == null || !(world.getBlockEntity(admissionPos) instanceof
            chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity admissionTable)
            || !admissionTable.canQueue(handler.player.getUUID(), conflictRefund)) return false;
        var receipt = handler.applyEnchantementConditionWithReceipt(payload);
        if (!receipt.accepted()) {
            return false;
        }

        MasterTomeWear.ResolvedTomeUse resolvedWear = MasterTomeWear.ResolvedTomeUse.NONE;
        if (masterTomeUsed) {
            resolvedWear = MasterTomeWear.applyShelfWearDetailed(
                world,
                handler.tableBlockPos(),
                handler.shelfGatesConfig(),
                handler.libraryResonanceConfig(),
                payload.enchantment_id(),
                enchantment_level,
                targetWasBook
            );
            var outcome = resolvedWear.outcome();
            if (handler.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                if (outcome == MasterTomeWear.WearOutcome.STEPPED_DOWN) {
                    serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        Component.translatable(
                            "gui.chiseled_enchanting_table.tome_wear.stepped_down",
                            enchantment.value().description(),
                            Math.max(1, enchantment_level - 1)
                        ).withStyle(ChatFormatting.GOLD)
                    ));
                } else if (outcome == MasterTomeWear.WearOutcome.DEPLETED) {
                    serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        Component.translatable(
                            "gui.chiseled_enchanting_table.tome_wear.depleted",
                            enchantment.value().description()
                        ).withStyle(ChatFormatting.YELLOW)
                    ));
                } else if (outcome == MasterTomeWear.WearOutcome.WAIVED_BY_UNBREAKING) {
                    serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        Component.translatable(
                            "gui.chiseled_enchanting_table.tome_wear.unbreaking_saved",
                            enchantment.value().description()
                        ).withStyle(ChatFormatting.AQUA)
                    ));
                }
            }
        }

        boolean consumedEpiphany = payload.option_type() == OptionType.TREASURE_EPIPHANY;
        handler.advanceTableEpiphanySeed(consumedEpiphany);

        var tablePos = handler.tableBlockPos();
        var shelfCfg = handler.shelfGatesConfig();
        var ritualScan = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.scanLibraryRitualContext(
            world,
            tablePos,
            shelfCfg
        );
        // Iron Rule #2: bind wear-immunity flag AND qualification load L (max of chosen tome load
        // and library-wide Sigma L across active wear-immune Master Tomes) to this step!
        boolean usedInfinityTome = masterTomeUsed && resolvedWear.usedInfinityTome();
        double singleTomeLoad = usedInfinityTome
            ? chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeInfinityTomeQualificationLoad(
                handler.ritualConfig(),
                handler.libraryResonanceConfig(),
                resolvedWear.chosenTomeSnapshot()
            )
            : 0.0;
        double infinityTomeLoad = usedInfinityTome
            ? Math.max(singleTomeLoad, ritualScan.totalWearImmuneLoad())
            : 0.0;
        int maxLvl = Math.max(1, enchantment.value().getMaxLevel());
        double appFactor = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeApplicabilityFactor(
            handler.ritualConfig(),
            enchantment
        );
        boolean ritualQueueEnabled = handler.isRitualQueueEnabled();
        boolean provenanceEnabled = handler.isProvenanceEnabled();
        boolean tableAlreadyActive = tablePos != null
            && world.getBlockEntity(tablePos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity existingBe
            && existingBe.hasActiveRitual();
        boolean bypassWait = chiseled_enchanting_table.mechanics.ritual.RitualActionService.shouldBypassRitualWait(
            ritualQueueEnabled,
            tableAlreadyActive,
            masterTomeUsed,
            payload.option_type(),
            enchantment_level,
            maxLvl
        );
        int waitTicks = bypassWait ? 0 : chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeSingleEnchantWaitTicks(
            handler.ritualConfig(),
            ritualScan.totalDetectedBookshelves(),
            chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.medianShelfGate(shelfCfg),
            ritualScan.totalMasterTomeEnchantments(),
            enchantment_level,
            maxLvl,
            appFactor
        );

        int inheritedGeneration = (provenanceEnabled && masterTomeUsed && targetWasBook
            && resolvedWear.chosenTomeSnapshot() != null
            && !resolvedWear.chosenTomeSnapshot().isEmpty())
            ? chiseled_enchanting_table.mechanics.resonance.TomeWearData.childGeneration(
                chiseled_enchanting_table.mechanics.resonance.TomeWearData.getGeneration(resolvedWear.chosenTomeSnapshot()))
            : -1;
        var queuedStep = new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription(
            payload.enchantment_id(),
            enchantment_level,
            existingSameLevel,
            masterTomeUsed,
            usedInfinityTome,
            conflictRefund,
            consumedEpiphany,
            infinityTomeLoad,
            receipt.paidXpPoints(),
            chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription.fromStacks(
                receipt.paidMaterials()
            ),
            inheritedGeneration
        );

        if (tablePos != null
            && resolvedWear.shelfPos() != null
            && resolvedWear.shelfSlot() >= 0) {
            chiseled_enchanting_table.menu.TableVisualEvents.onTomeTether(
                world,
                tablePos,
                resolvedWear.shelfPos(),
                resolvedWear.shelfSlot(),
                chiseled_enchanting_table.menu.TableVisualEvents.COLOR_ARCANE_VIOLET
            );
        }

        if (tablePos != null
            && world.getBlockEntity(tablePos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity tableBe) {
            if (waitTicks > 0 || tableBe.hasActiveRitual()) {
                int effectiveAddTicks = Math.max(20, waitTicks);
                tableBe.enqueueInscription(queuedStep, effectiveAddTicks, handler.player.getUUID());
                if (resolvedWear.hasShelfUndo()) {
                    tableBe.recordTomeWearUndo(new chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.TomeWearUndo(
                        resolvedWear.shelfPos(),
                        resolvedWear.shelfSlot(),
                        resolvedWear.preWearSnapshot(),
                        resolvedWear.postWearSnapshot()
                    ));
                }
                handler.player.awardStat(Stats.ENCHANT_ITEM);
                world.playSound(
                    null,
                    tablePos,
                    SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.BLOCKS,
                    1.2F,
                    1.05F
                );
                world.playSound(
                    null,
                    tablePos,
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.15F
                );
                handler.markInventoryChanged();
                handler.refreshLiveShelfState();
                handler.pushRitualStateToViewer();
                return true;
            }
            // Instant mode: route through the exact same deterministic completion path!
            tableBe.enqueueInscription(queuedStep, 1, handler.player.getUUID());
            tableBe.completeActiveRitual(
                world,
                tablePos,
                handler.player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity.RitualCompletionKind.NATURAL
            );
            finish(handler);
            return true;
        }

        // Headless / synthetic non-BE fallback path
        // 1.0.3 Classic: transcription slip («抄错») is a post-1.0.3 ritual mechanic;
        // classic inscriptions always land at the exact paid level.
        double slipChance = !ritualQueueEnabled ? 0.0 : chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeTranscriptionSlipChance(
            handler.ritualConfig(),
            ritualScan.maxOverloadedInfinityTomeEnchants(),
            ritualScan.totalDetectedBookshelves()
        );
        int finalLevel = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.resolveTranscribedLevel(
            enchantment_level,
            ritualScan.maxOverloadedInfinityTomeEnchants(),
            slipChance,
            world.getRandom().nextDouble(),
            world.getRandom().nextDouble()
        );

        ItemEnchantments.Mutable newEnchantmentsComponent = new ItemEnchantments.Mutable(preEnchantments);
        if (!targetWasBook) {
            var keys = new HashSet<>(newEnchantmentsComponent.keySet());
            for (var key : keys) {
                if (!Enchantment.areCompatible(key, enchantment)) {
                    newEnchantmentsComponent.set(key, 0);
                }
            }
        }
        newEnchantmentsComponent.set(enchantment, finalLevel);

        if (enchantable_item.is(Items.BOOK)) {
            handler.setEnchantableSlot(BookConversion.toEnchantedBook(enchantable_item));
            enchantable_item = handler.get_enchantable_item();
        }

        ItemStack target = enchantable_item;
        EnchantmentHelper.updateEnchantments(target, m -> {
            var mKeys = new HashSet<>(m.keySet());
            for (var k : mKeys) m.set(k, 0);
            for (var key : newEnchantmentsComponent.keySet()) {
                m.set(key, newEnchantmentsComponent.getLevel(key));
            }
        });

        if (provenanceEnabled) {
            EnchantmentProvenance.recordTablePayment(target, payload.enchantment_id(), existingSameLevel, finalLevel);
            EnchantmentProvenance.pruneToCurrentEnchantments(target, EnchantmentHelper.getEnchantmentsForCrafting(target));
            if (inheritedGeneration >= 0 && target.is(Items.ENCHANTED_BOOK)) {
                chiseled_enchanting_table.mechanics.resonance.TomeWearData.stampInheritedGeneration(target, inheritedGeneration);
            }
        } else {
            EnchantmentProvenance.clearAll(target);
        }

        if (conflictRefund > 0) {
            DisenchantmentApplication.awardRefundOrbs(handler, conflictRefund);
        }

        finish(handler);
        if (masterTomeUsed
            && (MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(payload.enchantment_id())
                || chiseled_enchanting_table.mechanics.resonance.TomeWearData.isWearImmuneEnchantment(enchantment))) {
            Advancement.give(handler.player, "eternal_codex");
        }
        if (consumedEpiphany) {
            Advancement.give(handler.player, "inscribe_epiphany");
        }
        if (target.is(Items.ENCHANTED_BOOK)) {
            Advancement.give(handler.player, "enchant_to_book");
            Advancement.checkBookMilestones(handler.player, world, target);
        }
        return true;
    }

    /** Transfer an enchantment from the enchanted book in the cost slot to the enchantable item. */
    public static boolean applyFromBook(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        Level world = handler.world();
        ItemStack enchanted_book = handler.get_cost_item();
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchanted_book);
        boolean exists = false;
        for (var entry : enchantments.entrySet()) {
            if (ChiseledEnchantingTableScreenHandler.EnchantmentToIdentifier(entry.getKey()).equals(payload.enchantment_id())
                && entry.getIntValue() == payload.enchantment_level()) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            return false;
        }

        ItemStack enchantable_item = handler.get_enchantable_item();
        if (enchantable_item.isEmpty()
            || enchantable_item.getCount() != 1
            || ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            return false;
        }
        int enchantment_level = payload.enchantment_level();
        var optEnchantment = ChiseledEnchantingTableScreenHandler.findEnchantment(payload.enchantment_id(), world);
        if (optEnchantment.isEmpty()) {
            return false;
        }
        Holder<Enchantment> enchantment = optEnchantment.get();
        boolean targetWasBook = enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK);
        if (!(targetWasBook || enchantment.value().canEnchant(enchantable_item))) {
            return false;
        }

        ItemStack sourceBookSnapshot = enchanted_book.copyWithCount(1);
        ItemEnchantments preTargetEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);
        // Audit M-03: transferring a same-or-lower level must not consume the source book
        // entry for zero gain. Reject before any slot mutation.
        if (preTargetEnchantments.getLevel(enchantment) >= enchantment_level) {
            return false;
        }
        int conflictRefund = computeConflictOverwriteRefund(handler, enchantable_item, preTargetEnchantments, enchantment);

        ItemEnchantments.Mutable new_enchanted_book_enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (!ChiseledEnchantingTableScreenHandler.EnchantmentToIdentifier(entry.getKey()).equals(payload.enchantment_id())) {
                new_enchanted_book_enchants.set(entry.getKey(), entry.getIntValue());
            }
        }
        ItemStack updatedSingleBook = sourceBookSnapshot.copy();
        EnchantmentHelper.updateEnchantments(updatedSingleBook, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0);
            for (var entry : new_enchanted_book_enchants.keySet()) m.set(entry, new_enchanted_book_enchants.getLevel(entry));
        });

        ItemStack remainderBook;
        if (EnchantmentHelper.getEnchantmentsForCrafting(updatedSingleBook).isEmpty()) {
            remainderBook = new ItemStack(
                handler.returnsPlainDrainedBook() ? Items.BOOK : chiseled_enchanting_table.registry.BlockRegistry.DEPLETED_TOME,
                1
            );
        } else {
            if (handler.isProvenanceEnabled()) {
                EnchantmentProvenance.pruneToCurrentEnchantments(
                    updatedSingleBook,
                    EnchantmentHelper.getEnchantmentsForCrafting(updatedSingleBook)
                );
            }
            remainderBook = updatedSingleBook;
        }
        if (enchanted_book.getCount() <= 1) {
            handler.setCostSlot(remainderBook);
        } else {
            ItemStack reduced = enchanted_book.copyWithCount(enchanted_book.getCount() - 1);
            handler.setCostSlot(reduced);
            chiseled_enchanting_table.utils.InventoryUtils.insertItem(handler.player, remainderBook, java.util.Optional.empty());
        }

        ItemEnchantments.Mutable newEnchantmentsComponent = new ItemEnchantments.Mutable(preTargetEnchantments);
        if (!targetWasBook) {
            var keys = new HashSet<>(newEnchantmentsComponent.keySet());
            for (var key : keys) {
                if (!Enchantment.areCompatible(key, enchantment)) {
                    newEnchantmentsComponent.set(key, 0);
                }
            }
        }
        newEnchantmentsComponent.set(enchantment, enchantment_level);

        if (enchantable_item.is(Items.BOOK)) {
            handler.setEnchantableSlot(BookConversion.toEnchantedBook(enchantable_item));
            enchantable_item = handler.get_enchantable_item();
        }

        ItemStack target = enchantable_item;
        EnchantmentHelper.updateEnchantments(target, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var key : keys) m.set(key, 0);
            for (var key : newEnchantmentsComponent.keySet()) {
                m.set(key, newEnchantmentsComponent.getLevel(key));
            }
        });

        if (ChiseledEnchantingTableData.disenchantEnabled()) {
            EnchantmentProvenance.recordBookTransfer(sourceBookSnapshot, target, payload.enchantment_id(), enchantment_level);
            EnchantmentProvenance.pruneToCurrentEnchantments(target, EnchantmentHelper.getEnchantmentsForCrafting(target));
        } else {
            EnchantmentProvenance.clearAll(target);
        }
        // Transfers move (never duplicate) an entry, so when template tome wear is active,
        // the result keeps the more faded parent's generation instead of advancing it.
        if (target.is(Items.ENCHANTED_BOOK)
            && ChiseledEnchantingTableData.libraryResonanceConfig().tomeConsumptionMode()
                == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR) {
            chiseled_enchanting_table.mechanics.resonance.TomeWearData.stampInheritedGeneration(
                target,
                chiseled_enchanting_table.mechanics.resonance.TomeWearData.getGeneration(sourceBookSnapshot));
        }

        if (conflictRefund > 0) {
            DisenchantmentApplication.awardRefundOrbs(handler, conflictRefund);
        }

        finish(handler);
        Advancement.give(handler.player, "enchant_from_book");
        if (target.is(Items.ENCHANTED_BOOK)) {
            Advancement.give(handler.player, "transfer_enchanted_book");
            Advancement.checkBookMilestones(handler.player, world, target);
        }
        return true;
    }

    public static int computeConflictOverwriteRefund(
        ChiseledEnchantingTableScreenHandler handler,
        ItemStack target,
        ItemEnchantments currentEnchantments,
        Holder<Enchantment> incoming
    ) {
        if (target == null || target.isEmpty() || target.is(Items.BOOK) || target.is(Items.ENCHANTED_BOOK)) {
            return 0;
        }
        if (!handler.disenchantEnabled()) {
            return 0;
        }
        long total = 0;
        var cfg = handler.libraryResonanceConfig();
        double tableScale = handler.disenchantRefundScale();
        double externalScale = handler.disenchantExternalRefundScale();
        for (var entry : currentEnchantments.entrySet()) {
            Holder<Enchantment> existingHolder = entry.getKey();
            if (!existingHolder.equals(incoming)
                && !Enchantment.areCompatible(existingHolder, incoming)
                && !DisenchantmentApplication.isCurse(existingHolder)) {
                total += DisenchantmentApplication.provenanceEssencePotential(
                    cfg,
                    target,
                    existingHolder,
                    entry.getIntValue(),
                    tableScale,
                    externalScale
                );
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static void finish(ChiseledEnchantingTableScreenHandler handler) {
        Player player = handler.player;
        Level world = handler.world();
        handler.markInventoryChanged();
        handler.refreshLiveShelfState();
        player.awardStat(Stats.ENCHANT_ITEM);
        world.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.95F, world.getRandom().nextFloat() * 0.1F + 0.95F);
        chiseled_enchanting_table.menu.TableVisualEvents.onRitualPulse(
            world, handler.tableBlockPos(), chiseled_enchanting_table.menu.TableVisualEvents.COLOR_ARCANE_VIOLET
        );
    }
}
