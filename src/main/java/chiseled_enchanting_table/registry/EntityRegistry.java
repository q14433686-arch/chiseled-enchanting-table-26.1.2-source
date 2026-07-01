package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class EntityRegistry {

    public static final BlockEntityType<ChiseledEnchantingTableBlockEntity> CHISELED_ENCHANTING_TABLE_ENTITY_TYPE =
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ChiseledEnchantingTable.identifier("chiseled_enchanting_table"),
            FabricBlockEntityTypeBuilder.create(
                ChiseledEnchantingTableBlockEntity::new,
                BlockRegistry.CHISELED_ENCHANTING_TABLE
            ).build()
        );

    public static void init() {
    }
}
