package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;

/**
 * Centralized single source of truth for all mod and shared {@link TagKey} constants
 * (audit {@code API-02}: eliminates duplicated {@code TagKey.create(...)} declarations
 * across blocks, resonance, rituals, loot, menus, and mixins).
 */
public final class CetTags {
    private CetTags() {}

    public static final class Blocks {
        private Blocks() {}

        /** Modded or vanilla wooden bookshelves supplying enchanting power to the table. */
        public static final TagKey<Block> REGULAR_BOOKSHELVES = TagKey.create(
            Registries.BLOCK,
            ChiseledEnchantingTable.identifier("regular_bookshelves")
        );
    }

    public static final class Enchantments {
        private Enchantments() {}

        /** Datapack-extensible Master Tome wear immunity (ships with {@code minecraft:infinity}). */
        public static final TagKey<Enchantment> WEAR_IMMUNE = TagKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(ChiseledEnchantingTable.MOD_ID, "wear_immune")
        );

        /** Enchantments reserved exclusively for Treasure Epiphany slots. */
        public static final TagKey<Enchantment> EPIPHANY_EXCLUSIVE = TagKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(ChiseledEnchantingTable.MOD_ID, "epiphany_exclusive")
        );

        /** Enchantments excluded from crossline slip mutation rolls. */
        public static final TagKey<Enchantment> CROSSLINE_EXCLUDED = TagKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(ChiseledEnchantingTable.MOD_ID, "crossline_excluded")
        );

        /** Broadly applicable utility/curse enchantments receiving the broad applicability multiplier. */
        public static final TagKey<Enchantment> BROAD_APPLICABILITY = TagKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(ChiseledEnchantingTable.MOD_ID, "broad_applicability")
        );

        /** Tier-1 keystone enchantments receiving the major applicability multiplier (ships with infinity &amp; mending). */
        public static final TagKey<Enchantment> KEYSTONE_MAJOR = TagKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(ChiseledEnchantingTable.MOD_ID, "keystone_major")
        );

        /** Tier-2 keystone enchantments receiving the minor applicability multiplier (ships with unbreaking). */
        public static final TagKey<Enchantment> KEYSTONE_MINOR = TagKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(ChiseledEnchantingTable.MOD_ID, "keystone_minor")
        );

        /** Vanilla curse tag reference ({@link EnchantmentTags#CURSE}). */
        public static final TagKey<Enchantment> CURSE = EnchantmentTags.CURSE;

        /** Vanilla treasure tag reference ({@link EnchantmentTags#TREASURE}). */
        public static final TagKey<Enchantment> TREASURE = EnchantmentTags.TREASURE;
    }
}
