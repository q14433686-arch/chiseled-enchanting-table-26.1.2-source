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
        ItemStack planningStack = handler.getEffectiveEnchantableItem();
        if (ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            return false;
        }
        int enchantment_level = payload.enchantment_level();
        Holder<Enchantment> enchantment = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), world);
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
            if (!exist && existingSameLevel >= 1) {
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
            || !admissionTable.canQueue(handler.player.getUUID(), conflictRefund) || enchantment_level < 1 || enchantment_level > 255 || payload.enchantment_id().toString().length() > 512) return false;
        if (!handler.applyEnchantementCondition(payload)) {
            return false;
        }

        if (masterTomeUsed) {
            var outcome = MasterTomeWear.applyShelfWear(
                world,
                handler.tableBlockPos(),
                handler.shelfGatesConfig(),
                handler.libraryResonanceConfig(),
                payload.enchantment_id(),
                enchantment_level,
                targetWasBook
            );
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
        boolean usedInfinityTome = masterTomeUsed
            && chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.doesMatchingMasterTomeHaveInfinity(
                world,
                tablePos,
                shelfCfg,
                payload.enchantment_id(),
                enchantment_level
            );
        int maxLvl = Math.max(1, enchantment.value().getMaxLevel());
        double appFactor = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeApplicabilityFactor(
            payload.enchantment_id(),
            enchantment
        );
        int waitTicks = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeSingleEnchantWaitTicks(
            ritualScan.totalDetectedBookshelves(),
            chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.medianShelfGate(shelfCfg),
            ritualScan.totalMasterTomeEnchantments(),
            enchantment_level,
            maxLvl,
            appFactor
        );

        var queuedStep = new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription(
            payload.enchantment_id(),
            enchantment_level,
            existingSameLevel,
            masterTomeUsed,
            usedInfinityTome,
            conflictRefund,
            consumedEpiphany
        );

        if (tablePos != null
            && world.getBlockEntity(tablePos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity tableBe) {
            if (waitTicks > 0 || tableBe.hasActiveRitual()) {
                int effectiveAddTicks = Math.max(20, waitTicks);
                tableBe.enqueueInscription(queuedStep, effectiveAddTicks, handler.player.getUUID());
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
        double slipChance = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.computeTranscriptionSlipChance(
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
            ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK, 1);
            var customName = enchantable_item.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (customName != null) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, customName);
            }
            handler.setEnchantableSlot(stack);
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

        EnchantmentProvenance.recordTablePayment(target, payload.enchantment_id(), existingSameLevel, finalLevel);
        EnchantmentProvenance.pruneToCurrentEnchantments(target, EnchantmentHelper.getEnchantmentsForCrafting(target));

        if (conflictRefund > 0) {
            DisenchantmentApplication.awardRefundOrbs(handler, conflictRefund);
        }

        finish(handler);
        if (masterTomeUsed && MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(payload.enchantment_id())) {
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
        if (ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            return false;
        }
        int enchantment_level = payload.enchantment_level();
        Holder<Enchantment> enchantment = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), world);
        boolean targetWasBook = enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK);
        if (!(targetWasBook || enchantment.value().canEnchant(enchantable_item))) {
            return false;
        }

        ItemStack sourceBookSnapshot = enchanted_book.copy();
        ItemEnchantments preTargetEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);
        int conflictRefund = computeConflictOverwriteRefund(handler, enchantable_item, preTargetEnchantments, enchantment);

        ItemEnchantments.Mutable new_enchanted_book_enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (!ChiseledEnchantingTableScreenHandler.EnchantmentToIdentifier(entry.getKey()).equals(payload.enchantment_id())) {
                new_enchanted_book_enchants.set(entry.getKey(), entry.getIntValue());
            }
        }
        EnchantmentHelper.updateEnchantments(enchanted_book, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0);
            for (var entry : new_enchanted_book_enchants.keySet()) m.set(entry, new_enchanted_book_enchants.getLevel(entry));
        });

        if (EnchantmentHelper.getEnchantmentsForCrafting(enchanted_book).isEmpty()) {
            handler.setCostSlot(new ItemStack(chiseled_enchanting_table.registry.BlockRegistry.DEPLETED_TOME, 1));
        } else {
            EnchantmentProvenance.pruneToCurrentEnchantments(
                enchanted_book,
                EnchantmentHelper.getEnchantmentsForCrafting(enchanted_book)
            );
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
            ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK, 1);
            var customName = enchantable_item.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (customName != null) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, customName);
            }
            handler.setEnchantableSlot(stack);
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

        EnchantmentProvenance.recordBookTransfer(sourceBookSnapshot, target, payload.enchantment_id(), enchantment_level);
        EnchantmentProvenance.pruneToCurrentEnchantments(target, EnchantmentHelper.getEnchantmentsForCrafting(target));

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
        world.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
