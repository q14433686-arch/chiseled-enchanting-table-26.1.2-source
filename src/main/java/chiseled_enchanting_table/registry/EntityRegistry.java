package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Legacy compatibility bridge for {@link BlockEntityRegistry} (Audit API-03).
 *
 * @deprecated Prefer {@link BlockEntityRegistry}; scheduled for removal in 2.0.0.
 */
@Deprecated(forRemoval = false)
public class EntityRegistry {

    public static final BlockEntityType<ChiseledEnchantingTableBlockEntity> CHISELED_ENCHANTING_TABLE_ENTITY_TYPE =
        BlockEntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE;

    public static void init() {
        BlockEntityRegistry.init();
    }
}
