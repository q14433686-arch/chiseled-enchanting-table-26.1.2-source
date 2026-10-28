package enchantment_fusion.registry;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class FusionBlockEntities {
    public static final BlockEntityType<EnchantmentFusionTableBlockEntity> ENCHANTMENT_FUSION_TABLE_BE =
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            EnchantmentFusion.id("enchantment_fusion_table"),
            FabricBlockEntityTypeBuilder.create(
                EnchantmentFusionTableBlockEntity::new,
                FusionBlocks.ENCHANTMENT_FUSION_TABLE
            ).build()
        );

    private FusionBlockEntities() {}

    public static void init() {
        // Explicit bootstrap trigger
    }
}
