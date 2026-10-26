package chiseled_enchanting_table.gui;


import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;


/** Registry lookup and localized names; no list, navigation, selection or row mutation. */
final class EnchantmentDisplayNames {
    private EnchantmentDisplayNames() {}
    static boolean isWearImmuneEnchantmentId(Identifier id, @Nullable Player player) {
        if (id == null) {
            return false;
        }
        return findEnchantment(id, player)
            .map(chiseled_enchanting_table.mechanics.resonance.TomeWearData::isWearImmuneEnchantment)
            .orElseGet(() -> chiseled_enchanting_table.mechanics.resonance.TomeWearData.INFINITY_ENCHANTMENT_ID.equals(id));
    }

    static java.util.Optional<Holder.Reference<Enchantment>> findEnchantment(Identifier id, Player player) {
        if (id == null || player == null) {
            return java.util.Optional.empty();
        }
        return player.level().registryAccess()
            .lookup(Registries.ENCHANTMENT)
            .flatMap(reg -> reg.get(ResourceKey.create(Registries.ENCHANTMENT, id)));
    }

    static Holder<Enchantment> identifierToEnchantment(Identifier id, Player player) {
        return findEnchantment(id, player)
            .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment: " + id));
    }

    static Identifier enchantmentToIdentifier(Holder<Enchantment> enchant) {
        return enchant.unwrapKey().orElseThrow().identifier();
    }

    static String baseEnchantmentName(Holder<Enchantment> ench) {
        return ench.value().description().getString();
    }

    static String romanNumeral(int level) {
        if (level >= 1 && level <= 10) {
            return Component.translatable("enchantment.level." + level).getString();
        }
        return String.valueOf(level);
    }

    static String displayName(ChiseledEnchantingTableScreenHandler handler, Player player, AvailableOption option) {
        if (option.isDisenchantAll()) {
            return I18n.get("gui.chiseled_enchanting_table.cleanse.all_row");
        }
        if (option.isTransformation()) {
            String out = handler.syncedTransformationOutputName(option.id());
            if (option.id().equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID)) {
                return I18n.get("gui.chiseled_enchanting_table.blank_overwrite", out);
            }
            return out;
        }
        Holder<Enchantment> ench = identifierToEnchantment(option.id(), player);
        String name = Enchantment.getFullname(ench, option.level()).getString();
        if (option.isDowngrade()) {
            int curLvl = handler.currentTargetEnchantLevel(option.id());
            String curName = Enchantment.getFullname(ench, Math.max(option.level() + 1, curLvl)).getString();
            return I18n.get("gui.chiseled_enchanting_table.cleanse.downgrade_row", curName, name);
        }
        if (option.isStripSingle()) {
            return I18n.get("gui.chiseled_enchanting_table.cleanse.strip_row", name);
        }
        if (option.isPurgeCurse()) {
            return I18n.get("gui.chiseled_enchanting_table.cleanse.curse_row", name);
        }
        if (option.isTreasureEpiphany()) return I18n.get("gui.chiseled_enchanting_table.treasure_epiphany", name);
        if (option.isBookCombineUpgrade()) return I18n.get("gui.chiseled_enchanting_table.book_upgrade.combine", name);
        if (option.isBookResearchUpgrade()) return I18n.get("gui.chiseled_enchanting_table.book_upgrade.research", name);
        return name;
    }
}
