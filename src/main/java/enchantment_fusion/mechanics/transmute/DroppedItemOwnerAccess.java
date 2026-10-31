package enchantment_fusion.mechanics.transmute;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import org.jetbrains.annotations.Nullable;

/** Read vanilla's pickup restriction without rewriting ownership metadata. */
public interface DroppedItemOwnerAccess {
    @Nullable UUID ef$pickupTarget();
    @Nullable EntityReference<Entity> ef$throwerReference();
}
