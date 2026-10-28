package enchantment_fusion.registry;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.item.FusionGrimoireItem;
import enchantment_fusion.item.FusionPageItem;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
import net.minecraft.world.item.component.ItemLore;

public final class FusionItems {
    public static final Item ENCHANTMENT_FUSION_TABLE_ITEM = register(
        "enchantment_fusion_table",
        props -> new BlockItem(
            FusionBlocks.ENCHANTMENT_FUSION_TABLE,
            props.useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON)
        )
    );

    public static final Item FUSION_GRIMOIRE = register(
        "fusion_grimoire",
        props -> new FusionGrimoireItem(
            props
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .component(
                    DataComponents.LORE,
                    new ItemLore(List.of(
                        Component.translatable("item.enchantment_fusion.fusion_grimoire.lore")
                            .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD))
                    ))
                )
        )
    );

    public static final Item FUSION_TATTERED_PAGE = register(
        "fusion_tattered_page",
        props -> new FusionPageItem(
            props
                .stacksTo(16)
                .rarity(Rarity.UNCOMMON)
                .component(
                    DataComponents.LORE,
                    new ItemLore(List.of(
                        Component.translatable("item.enchantment_fusion.fusion_tattered_page.lore")
                            .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY))
                    ))
                )
        )
    );

    public static final Item FUSED_ENCHANTED_BOOK = register(
        "fused_enchanted_book",
        props -> new FusedEnchantedBookItem(
            props.stacksTo(1).rarity(Rarity.RARE)
        )
    );

    public static final Item RESONANCE_STABILIZER = register(
        "resonance_stabilizer",
        props -> new Item(
            props
                .stacksTo(16)
                .rarity(Rarity.UNCOMMON)
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                .component(
                    DataComponents.LORE,
                    new ItemLore(List.of(
                        Component.translatable("item.enchantment_fusion.resonance_stabilizer.lore")
                            .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA))
                    ))
                )
        )
    );

    private FusionItems() {}

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EnchantmentFusion.id(name));
        Item item = factory.apply(new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> {
            out.accept(ENCHANTMENT_FUSION_TABLE_ITEM);
            out.accept(FUSION_GRIMOIRE);
            out.accept(FUSION_TATTERED_PAGE);
            out.accept(FusionPageItem.createStack(1, 1));
            out.accept(FusionPageItem.createStack(2, 1));
            out.accept(RESONANCE_STABILIZER);
            out.accept(FUSED_ENCHANTED_BOOK);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(out -> {
            out.accept(FUSION_GRIMOIRE);
            out.accept(FUSION_TATTERED_PAGE);
            out.accept(FusionPageItem.createStack(1, 1));
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(out -> {
            out.accept(FUSION_TATTERED_PAGE);
            out.accept(FusionPageItem.createStack(1, 1));
            out.accept(FusionPageItem.createStack(2, 1));
            out.accept(RESONANCE_STABILIZER);
            out.accept(FUSED_ENCHANTED_BOOK);
        });
    }
}
