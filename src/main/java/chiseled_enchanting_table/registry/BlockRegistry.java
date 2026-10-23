package chiseled_enchanting_table.registry;

import java.util.List;
import java.util.function.Function;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.item.ArcaneGrimoireItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public class BlockRegistry {

    public static final int DEFAULT_CLOTH_RGB = 0xA020F0;

    public static final Block CHISELED_ENCHANTING_TABLE = register(
        "chiseled_enchanting_table",
        ChiseledEnchantingTableBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .lightLevel(state -> 7)
            .strength(5.0F, 1200.0F),
        true
    );

    public static final Item ARCANE_CATALYST = registerItem(
        "arcane_catalyst",
        settings -> new Item(
            settings
                .rarity(Rarity.RARE)
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        )
    );

    public static final Item DEPLETED_TOME = registerItem(
        "depleted_tome",
        settings -> new Item(
            settings
                .rarity(Rarity.UNCOMMON)
                .component(
                    DataComponents.LORE,
                    new ItemLore(List.of(
                        Component.translatable("item.chiseled_enchanting_table.depleted_tome.lore")
                            .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE))
                    ))
                )
        )
    );

    public static final Item CLEANSING_GRIT = registerItem(
        "cleansing_grit",
        settings -> new Item(
            settings
                .rarity(Rarity.UNCOMMON)
                .component(
                    DataComponents.LORE,
                    new ItemLore(List.of(
                        Component.translatable("item.chiseled_enchanting_table.cleansing_grit.lore")
                            .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.AQUA))
                    ))
                )
        )
    );

    public static final Item ARCANE_GRIMOIRE = registerItem(
        "arcane_grimoire",
        settings -> new ArcaneGrimoireItem(
            settings
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .component(
                    DataComponents.LORE,
                    new ItemLore(List.of(
                        Component.translatable("item.chiseled_enchanting_table.arcane_grimoire.lore")
                            .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD))
                    ))
                )
        )
    );

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties settings, boolean shouldRegisterItem) {
        ResourceKey<Block> blockKey = keyOfBlock(name);
        Block block = blockFactory.apply(settings.setId(blockKey));

        if (shouldRegisterItem) {
            ResourceKey<Item> itemKey = keyOfItem(name);
            BlockItem blockItem = new BlockItem(
                block,
                new Item.Properties()
                    .setId(itemKey)
                    .component(DataComponents.DYED_COLOR, new DyedItemColor(DEFAULT_CLOTH_RGB))
            );
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
        }

        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> itemFactory) {
        ResourceKey<Item> itemKey = keyOfItem(name);
        Item item = itemFactory.apply(new Item.Properties().setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static ResourceKey<Block> keyOfBlock(String name) {
        return ResourceKey.create(Registries.BLOCK, ChiseledEnchantingTable.identifier(name));
    }

    private static ResourceKey<Item> keyOfItem(String name) {
        return ResourceKey.create(Registries.ITEM, ChiseledEnchantingTable.identifier(name));
    }

    public static void init() {
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
            output.accept(CHISELED_ENCHANTING_TABLE.asItem());
            output.accept(ARCANE_GRIMOIRE);
            output.accept(ARCANE_CATALYST);
            output.accept(CLEANSING_GRIT);
            output.accept(DEPLETED_TOME);
        });
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(ARCANE_GRIMOIRE);
        });
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
            output.accept(ARCANE_GRIMOIRE);
            output.accept(ARCANE_CATALYST);
            output.accept(CLEANSING_GRIT);
            output.accept(DEPLETED_TOME);
        });
    }
}
