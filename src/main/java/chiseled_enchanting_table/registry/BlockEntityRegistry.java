package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Registers the mod's {@link BlockEntityType} entries during {@code ModInitializer#onInitialize}.
 */
public final class BlockEntityRegistry {
    public static final BlockEntityType<ChiseledEnchantingTableBlockEntity> CHISELED_ENCHANTING_TABLE_ENTITY_TYPE =
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ChiseledEnchantingTable.identifier("chiseled_enchanting_table"),
            FabricBlockEntityTypeBuilder.create(
                ChiseledEnchantingTableBlockEntity::new,
                BlockRegistry.CHISELED_ENCHANTING_TABLE
            ).build()
        );

    private BlockEntityRegistry() {}

    /**
     * Explicit class-initialization trigger called from {@link ChiseledEnchantingTable#onInitialize}
     * so registration happens deterministically during mod boot rather than on first lazy reference.
     */
    public static void init() {
        // Intentional: triggers static field registration above in deterministic boot order.
    }
}
