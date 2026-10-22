package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Universal, mod-compatible disenchanting, step-down downgrading, targeted trait removal,
 * and curse purification engine for the Chiseled Enchanting Table.
 *
 * <p>Uses strictly data-driven vanilla registry attributes ({@link Enchantment#getMaxLevel()},
 * {@link Enchantment#getAnvilCost()}, {@code #minecraft:treasure}, and {@code #minecraft:curse})
 * with conservative telescoping potential {@code V(E, L)} so step-by-step downgrading yields the
 * exact same total XP as a single full wipe and strictly less XP than Master Tome creation.</p>
 */
public final class DisenchantmentApplication {
    public static final Identifier DISENCHANT_ALL_ID = ChiseledEnchantingTable.identifier("disenchant_all");
    public static final Identifier STRIP_SINGLE_ID = ChiseledEnchantingTable.identifier("strip_single_enchantment");
    public static final Identifier PURGE_CURSE_ID = ChiseledEnchantingTable.identifier("purge_curse");

    private static final TagKey<Enchantment> TREASURE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("treasure"));
    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    private DisenchantmentApplication() {}

    public static boolean isCurse(Holder<Enchantment> holder) {
        return holder != null && holder.is(CURSE_TAG);
    }

    public static boolean isTreasure(Holder<Enchantment> holder) {
        return holder != null && !holder.is(CURSE_TAG) && holder.is(TREASURE_TAG);
    }

    /**
     * Conservative stored-essence XP potential {@code V(E, level)} for any vanilla or modded
     * enchantment at {@code level >= 0}:
     * <ul>
     *   <li>{@code V(E, 0) = 0}</li>
     *   <li>{@code r = clamp(level / maxLevel, 0, 1)} handles arbitrary modded max levels {@code 1..255}</li>
     *   <li>{@code anvilCost} scales logarithmically in {@code [1.0, 1.45]} so extreme modded values cannot overflow</li>
     *   <li>Guaranteed strictly below minimum Master Tome inscription XP cost at every tier</li>
     * </ul>
     */
    public static int essencePotential(Holder<Enchantment> holder, int level, double scale) {
        if (holder == null || level <= 0 || isCurse(holder) || scale <= 0.0) {
            return 0;
        }
        Enchantment ench = holder.value();
        int maxLevel = Math.max(1, ench.getMaxLevel());
        int clampedLevel = Math.min(level, maxLevel);
        double r = Math.clamp((double) clampedLevel / (double) maxLevel, 0.0, 1.0);
        int anvilCost = Math.max(1, ench.getAnvilCost());
        double rarityFactor = Math.clamp(1.0 + 0.18 * Math.log(anvilCost), 1.0, 1.45)
            * (isTreasure(holder) ? 1.15 : 1.0);
        double baseMax = maxLevel == 1 ? 34.0 : 48.0;
        int raw = (int) Math.round(scale * baseMax * rarityFactor * Math.pow(r, 1.65));
        return Math.max(clampedLevel, raw);
    }

    /**
     * Exact telescoping XP refund for stepping {@code currentLevel -> targetLevel} ({@code 0 <= targetLevel < currentLevel}).
     */
    public static int refundForStepDown(Holder<Enchantment> holder, int currentLevel, int targetLevel, double scale) {
        if (holder == null || isCurse(holder) || currentLevel <= targetLevel || currentLevel <= 0) {
            return 0;
        }
        int curV = essencePotential(holder, currentLevel, scale);
        int nextV = targetLevel <= 0 ? 0 : essencePotential(holder, targetLevel, scale);
        return Math.max(1, curV - nextV);
    }

    /**
     * Total XP points granted when wiping all non-curse enchantments off {@code stack}.
     */
    public static int totalFullDisenchantRefund(ItemStack stack, double scale) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (current.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (var entry : current.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int lvl = entry.getIntValue();
            if (!isCurse(holder) && lvl > 0) {
                sum += essencePotential(holder, lvl, scale);
            }
        }
        return sum;
    }

    public static int countNonCurseEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int count = 0;
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            if (!isCurse(entry.getKey()) && entry.getIntValue() > 0) {
                count++;
            }
        }
        return count;
    }

    public static boolean apply(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        if (!handler.disenchantEnabled()) {
            return false;
        }
        return switch (payload.option_type()) {
            case DISENCHANT_ALL -> applyDisenchantAll(handler);
            case DOWNGRADE_ENCHANTMENT -> applyDowngrade(handler, payload);
            case STRIP_SINGLE_ENCHANTMENT -> applyStripSingle(handler, payload, false);
            case PURGE_CURSE -> applyStripSingle(handler, payload, true);
            default -> false;
        };
    }

    private static boolean applyDisenchantAll(ChiseledEnchantingTableScreenHandler handler) {
        ItemStack target = handler.get_enchantable_item();
        if (target.isEmpty()) return false;

        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        int removable = countNonCurseEnchantments(target);
        if (removable <= 0) {
            return false;
        }

        int refundPoints = totalFullDisenchantRefund(target, handler.disenchantRefundScale());
        ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (isCurse(entry.getKey()) && entry.getIntValue() > 0) {
                remaining.set(entry.getKey(), entry.getIntValue());
            }
        }

        applyResultingEnchantmentsOrDepletedTome(handler, target, remaining);
        grantRefundExperience(handler, refundPoints);
        playCleanseSounds(handler, false, true);
        return true;
    }

    private static boolean applyDowngrade(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        ItemStack target = handler.get_enchantable_item();
        if (target.isEmpty()) return false;

        Holder<Enchantment> ench = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), handler.world());
        if (isCurse(ench)) return false;

        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        int currentLevel = current.getLevel(ench);
        int targetLevel = payload.enchantment_level();
        if (currentLevel < 2 || targetLevel < 1 || targetLevel >= currentLevel) {
            return false;
        }

        int refundPoints = refundForStepDown(ench, currentLevel, targetLevel, handler.disenchantRefundScale());
        ItemEnchantments.Mutable updated = new ItemEnchantments.Mutable(current);
        updated.set(ench, targetLevel);

        applyResultingEnchantmentsOrDepletedTome(handler, target, updated);
        grantRefundExperience(handler, refundPoints);
        playCleanseSounds(handler, false, true);
        return true;
    }

    private static boolean applyStripSingle(
        ChiseledEnchantingTableScreenHandler handler,
        ApplyEnchantmentPayload payload,
        boolean expectCurse
    ) {
        ItemStack target = handler.get_enchantable_item();
        if (target.isEmpty()) return false;

        Holder<Enchantment> ench = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), handler.world());
        boolean curse = isCurse(ench);
        if (curse != expectCurse) {
            return false;
        }
        if (curse && !handler.disenchantAllowCursePurge()) {
            return false;
        }

        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        int currentLevel = current.getLevel(ench);
        if (currentLevel <= 0) {
            return false;
        }

        // If this non-curse enchantment is the ONLY enchantment on the item, delegate to free full disenchant + XP refund!
        if (!curse && current.size() <= 1) {
            return applyDisenchantAll(handler);
        }

        OptionType optType = curse ? OptionType.PURGE_CURSE : OptionType.STRIP_SINGLE_ENCHANTMENT;
        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), currentLevel);
        var xpReq = handler.resolveEffectiveXpRequirement(ewl, optType);
        List<ItemStack> matchedBundle = handler.findMatchingAffordableCostBundle(ewl, optType);
        if (!xpReq.isMetBy(handler.player) || (!handler.player.getAbilities().instabuild && matchedBundle == null)) {
            return false;
        }

        if (!handler.player.getAbilities().instabuild) {
            XpCostConversion.deductXp(handler.player, xpReq);
            handler.deductMatchedBundle(matchedBundle);
        }

        ItemEnchantments.Mutable updated = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (!entry.getKey().equals(ench) && entry.getIntValue() > 0) {
                updated.set(entry.getKey(), entry.getIntValue());
            }
        }

        applyResultingEnchantmentsOrDepletedTome(handler, target, updated);
        playCleanseSounds(handler, curse, false);
        Advancement.give(handler.player, "cleanse_enchantment");
        if (curse) {
            Advancement.give(handler.player, "purge_curse");
        }
        return true;
    }

    private static void grantRefundExperience(ChiseledEnchantingTableScreenHandler handler, int refundPoints) {
        if (refundPoints <= 0) {
            return;
        }
        BlockPos tablePos = handler.tableBlockPos();
        if (handler.world() instanceof ServerLevel serverLevel && tablePos != null) {
            Vec3 spawnPos = serverLevel.getBlockState(tablePos.above()).isAir()
                ? Vec3.atBottomCenterOf(tablePos).add(0.0, 1.05, 0.0)
                : handler.player.position().add(0.0, 0.5, 0.0);
            ExperienceOrb.award(serverLevel, spawnPos, refundPoints);
        } else {
            handler.player.giveExperiencePoints(refundPoints);
        }
    }

    private static void applyResultingEnchantmentsOrDepletedTome(
        ChiseledEnchantingTableScreenHandler handler,
        ItemStack target,
        ItemEnchantments.Mutable updated
    ) {
        if (target.is(Items.ENCHANTED_BOOK) && updated.keySet().isEmpty()) {
            ItemStack depleted = new ItemStack(BlockRegistry.DEPLETED_TOME, 1);
            var customName = target.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                depleted.set(DataComponents.CUSTOM_NAME, customName);
            }
            handler.setEnchantableSlot(depleted);
            handler.markInventoryChanged();
            return;
        }
        EnchantmentHelper.updateEnchantments(target, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var k : keys) {
                m.set(k, 0);
            }
            for (var key : updated.keySet()) {
                m.set(key, updated.getLevel(key));
            }
        });
        // Reset anvil repair penalty cleanly if all non-curse enchantments were wiped off equipment.
        if (updated.keySet().isEmpty()) {
            target.set(DataComponents.REPAIR_COST, 0);
        }
        handler.markInventoryChanged();
    }

    private static void playCleanseSounds(ChiseledEnchantingTableScreenHandler handler, boolean cursePurged, boolean refundedXp) {
        var world = handler.world();
        var pos = handler.player.blockPosition();
        world.playSound(
            null,
            pos,
            SoundEvents.GRINDSTONE_USE,
            SoundSource.BLOCKS,
            0.95F,
            cursePurged ? 1.25F : 1.05F
        );
        if (cursePurged) {
            world.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.15F, 1.3F);
        } else if (refundedXp) {
            world.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.15F);
        } else {
            world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.15F);
        }
    }
}
