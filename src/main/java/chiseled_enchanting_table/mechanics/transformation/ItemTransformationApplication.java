package chiseled_enchanting_table.mechanics.transformation;

import chiseled_enchanting_table.compat.CetFormatting;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.data.ItemTransformation;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.InventoryUtils;
import java.util.Optional;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item transformation gameplay mechanic with prior-discovery gating ({@code D5}) and universal
 * ungated stack batching across ALL transformations:
 * <ul>
 *   <li>Exploration transmutations carrying {@code require_prior_discovery = true} or
 *       {@code required_advancement} verify server-side unlock status before accepting rituals.</li>
 *   <li>Every unlocked transformation automatically resolves {@code batchCount >= 1} up to the
 *       player's full affordable stack without any bookshelf threshold requirement, scaling both
 *       reagents and XP identically to {@code batchCount} sequential rituals.</li>
 *   <li>Produced stacks strictly respect {@link ItemStack#getMaxStackSize()} (including unstackable
 *       outputs such as Totems of Undying and Tridents).</li>
 * </ul>
 */
public final class ItemTransformationApplication {
    private ItemTransformationApplication() {}

    public static boolean apply(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        Optional<ItemTransformation> match = ChiseledEnchantingTableData.itemTransformation(payload.enchantment_id());
        if (match.isEmpty()) {
            return false;
        }
        ItemTransformation rule = match.get();
        ItemStack input = handler.get_enchantable_item();
        if (!rule.matchesInput(input)) {
            return false;
        }
        if (!rule.isUnlockedFor(handler.player)) {
            return tryDecipherWithCatalyst(handler, rule);
        }

        int batchCount = Math.max(1, Math.min(input.getCount(), handler.resolveTransformationBatchCount(rule.id())));

        ApplyEnchantmentPayload canonical = new ApplyEnchantmentPayload(
            handler.containerId,
            OptionType.TRANSFORMATION,
            rule.id(),
            rule.level(),
            payload.cost_option_index()
        );
        if (!handler.applyEnchantementCondition(canonical)) {
            return false;
        }

        int totalProduced = batchCount * Math.max(1, rule.level());
        ItemStack unitSample = rule.outputStack(1);
        int outMaxStack = Math.max(1, unitSample.getMaxStackSize());
        int targetSlotCap = Math.min(
            outMaxStack,
            Math.max(1, handler.getSlot(ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT).getMaxStackSize(unitSample))
        );

        if (input.getCount() <= batchCount) {
            int inSlot = Math.min(totalProduced, targetSlotCap);
            handler.setEnchantableSlot(rule.outputStack(inSlot));
            int rem = totalProduced - inSlot;
            if (rem > 0) {
                InventoryUtils.insertItem(handler.player, rule.outputStack(rem), Optional.empty());
            }
        } else {
            input.shrink(batchCount);
            InventoryUtils.insertItem(handler.player, rule.outputStack(totalProduced), Optional.empty());
        }
        handler.advanceTableEpiphanySeed(false);

        Player player = handler.player;
        handler.markInventoryChanged();
        player.getInventory().setChanged();
        handler.world().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.85F, 1.05F);
        chiseled_enchanting_table.menu.TableVisualEvents.onArcanePulse(
            handler.world(), handler.tableBlockPos(), chiseled_enchanting_table.menu.TableVisualEvents.COLOR_SACRED_GOLD
        );
        Advancement.give(player, "transmute_item");
        if (rule.id().equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID)) {
            Advancement.give(player, "blank_overwrite");
        }
        if (unitSample.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            Advancement.give(player, "enchanted_golden_apple");
        }
        return true;
    }

    /**
     * Arcane Catalyst breakthrough: placing the base input in slot 0 and an Arcane Catalyst in the
     * reagent slot permanently deciphers and unlocks a discovery-locked transformation for that player.
     */
    private static boolean tryDecipherWithCatalyst(ChiseledEnchantingTableScreenHandler handler, ItemTransformation rule) {
        ItemStack costSlot = handler.get_cost_item();
        if (costSlot.isEmpty() || !costSlot.is(chiseled_enchanting_table.registry.BlockRegistry.ARCANE_CATALYST)) {
            return false;
        }
        if (!(handler.player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return false;
        }
        var outOpt = rule.resolveOutputItem();
        if (outOpt.isEmpty()) {
            return false;
        }
        if (!sp.getAbilities().instabuild) {
            costSlot.shrink(1);
            handler.markInventoryChanged();
        }
        var outItem = outOpt.get();
        sp.awardStat(net.minecraft.stats.Stats.ITEM_CRAFTED.get(outItem), 1);
        if (rule.requiredAdvancement() != null && sp.level().getServer() != null) {
            var advHolder = sp.level().getServer().getAdvancements().get(rule.requiredAdvancement());
            if (advHolder != null) {
                var progress = sp.getAdvancements().getOrStartProgress(advHolder);
                for (String criterion : progress.getRemainingCriteria()) {
                    sp.getAdvancements().award(advHolder, criterion);
                }
            }
        }
        chiseled_enchanting_table.menu.EnchantCostSync.syncPlayerTransformationsNow(sp);
        handler.world().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.85F, 1.25F);
        handler.world().playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.85F, 1.18F);
        chiseled_enchanting_table.menu.TableVisualEvents.onArcanePulse(
            handler.world(), handler.tableBlockPos(), chiseled_enchanting_table.menu.TableVisualEvents.COLOR_ARCANE_VIOLET
        );
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
            net.minecraft.network.chat.Component.translatable(
                "gui.chiseled_enchanting_table.discovery_locked.deciphered",
                new ItemStack(outItem).getHoverName()
            ).withStyle(net.minecraft.CetFormatting.LIGHT_PURPLE)
        ));
        handler.broadcastChanges();
        return true;
    }
}
