package enchantment_fusion.registry;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.block.EnchantmentFusionTableBlock;
import enchantment_fusion.block.TemporaryFrostBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public final class FusionBlocks {
    public static final Block ENCHANTMENT_FUSION_TABLE = register(
        "enchantment_fusion_table",
        EnchantmentFusionTableBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.DEEPSLATE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .lightLevel(state -> 7)
            .strength(4.5F, 1200.0F)
    );

    // Not an IceBlock: only the mod's ephemeral cages use this state. Vanilla Frost Walker
    // ice keeps its normal water-producing behaviour.
    public static final Block TEMPORARY_FROST = register(
        "temporary_frost", TemporaryFrostBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.FROSTED_ICE).noLootTable()
    );

    private FusionBlocks() {}

    private static Block register(
        String name,
        Function<BlockBehaviour.Properties, Block> factory,
        BlockBehaviour.Properties settings
    ) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, EnchantmentFusion.id(name));
        Block block = factory.apply(settings.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    public static void init() {
        // Explicit bootstrap trigger
    }
}
