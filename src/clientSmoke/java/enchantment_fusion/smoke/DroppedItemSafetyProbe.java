package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.transmute.DroppedItemOwnerAccess;
import enchantment_fusion.mechanics.transmute.RelicWorldItemTransmutes;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Prepared item-hazard and ownership fixture; it does not simulate a second client. */
final class DroppedItemSafetyProbe {
    private DroppedItemSafetyProbe() {}

    static void exercise(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Inventory inv = player.getInventory();
        List<ItemStack> saved = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) saved.add(inv.getItem(i).copy());
        List<ItemEntity> spawned = new ArrayList<>();
        try {
            ItemEntity fire = drop(level, player, "minecraft:fire_protection", spawned);
            require(!fire.hurtServer(level, level.damageSources().lava(), 1), "Fire protection must block lava damage");
            require(fire.hurtServer(level, level.damageSources().explosion(null, null), 1), "Fire protection must not block explosion damage");
            require(fire.hurtServer(level, level.damageSources().generic(), 1), "Fire protection must not grant global immunity");
            ItemEntity blast = drop(level, player, "minecraft:blast_protection", spawned);
            require(!blast.hurtServer(level, level.damageSources().explosion(null, null), 1), "Blast protection must block explosion damage");
            require(blast.hurtServer(level, level.damageSources().lava(), 1), "Blast protection must not block lava damage");

            for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            ItemStack stack = loyaltyStack(5);
            ItemStack almostFull = stack.copyWithCount(62);
            inv.setItem(0, almostFull);
            ItemEntity owned = new ItemEntity(level, player.getX(), level.getMinY() + 5, player.getZ(), stack.copy());
            owned.setThrower(player);
            level.addFreshEntity(owned); spawned.add(owned);
            RelicWorldItemTransmutes.processWorldItem(level, owned);
            require(inv.getItem(0).getCount() == 64 && owned.isAlive() && owned.getItem().getCount() == 3,
                "A partial inventory transfer must leave the exact remainder in the world");
            require(player.getUUID().equals(((DroppedItemOwnerAccess) (Object) owned).ef$pickupTarget()),
                "A rescued remainder must remain restricted to its owner");

            ItemEntity unknown = new ItemEntity(level, player.getX(), level.getMinY() + 5, player.getZ(), stack.copy());
            level.addFreshEntity(unknown); spawned.add(unknown);
            RelicWorldItemTransmutes.processWorldItem(level, unknown);
            require(unknown.isAlive() && unknown.getItem().getCount() == 5 && inv.getItem(0).getCount() == 64,
                "Unknown ownership must not transfer items to a nearby player");
            ItemEntity reserved = new ItemEntity(level, player.getX(), level.getMinY() + 5, player.getZ(), stack.copy());
            reserved.setThrower(player); reserved.setTarget(UUID.randomUUID());
            level.addFreshEntity(reserved); spawned.add(reserved);
            RelicWorldItemTransmutes.processWorldItem(level, reserved);
            require(reserved.isAlive() && reserved.getItem().getCount() == 5,
                "A different vanilla pickup target must prevent recovery by the thrower");
        } finally {
            for (ItemEntity item : spawned) item.discard();
            for (int i = 0; i < saved.size(); i++) inv.setItem(i, saved.get(i));
        }
    }

    private static ItemEntity drop(ServerLevel level, ServerPlayer player, String id, List<ItemEntity> out) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of(id, 1));
        ItemEntity item = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
        level.addFreshEntity(item); out.add(item);
        return item;
    }

    private static ItemStack loyaltyStack(int count) {
        ItemStack stack = new ItemStack(Items.SHEARS, count);
        stack.remove(DataComponents.MAX_DAMAGE);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        stack.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of("minecraft:loyalty", 1));
        return stack;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
