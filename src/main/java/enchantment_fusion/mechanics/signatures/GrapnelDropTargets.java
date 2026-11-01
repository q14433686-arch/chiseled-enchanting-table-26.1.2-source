package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.mechanics.transmute.DroppedItemOwnerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Revalidate the exact captured item and ordinary pickup conditions at recall, not a nearby scan. */
final class GrapnelDropTargets {
    private GrapnelDropTargets() {}
    static boolean unchanged(ServerPlayer owner, ItemEntity entity, ItemStack stack, ItemStack snapshot) {
        if (!entity.isAlive() || entity.level() != owner.level() || owner.level().getEntity(entity.getUUID()) != entity
            || entity.getItem() != stack || !ItemStack.matches(stack, snapshot)) return false;
        DroppedItemOwnerAccess metadata = (DroppedItemOwnerAccess) (Object) entity;
        var thrower = metadata.ef$throwerReference();
        return HarvestDropCapture.ownerCompatible(owner.getUUID(), thrower == null ? null : thrower.getUUID(), metadata.ef$pickupTarget());
    }
    static boolean reachable(ServerPlayer owner, BlockPos origin, ItemEntity entity, ItemStack stack, ItemStack snapshot) {
        if (!unchanged(owner, entity, stack, snapshot) || entity.hasPickUpDelay() || !owner.level().hasChunkAt(entity.blockPosition())
            || !owner.level().mayInteract(owner, origin) || !owner.level().mayInteract(owner, entity.blockPosition())) return false;
        Vec3 from = owner.getEyePosition(), to = entity.getBoundingBox().getCenter();
        return from.distanceToSqr(to) <= 12.0D * 12.0D && VirtualCombatSight.clear(owner.level(), owner, from, to);
    }
}
