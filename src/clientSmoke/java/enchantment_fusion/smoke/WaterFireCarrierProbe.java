package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Prepared 31.4 carrier checks; preserves the existing abilities and old equipment. */
final class WaterFireCarrierProbe {
    private static final FusedPair FIRE = FusedPair.clean("minecraft:fire_aspect", 1, "minecraft:aqua_affinity", 1);
    private WaterFireCarrierProbe() {}

    static void exercise(ServerPlayer player) {
        for (Item allowed : new Item[]{Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE}) {
            require(DirectionalHostRules.validateNewOrChanged(new ItemStack(allowed), FIRE) == RejectReason.NONE,
                "Fire-primary 31.4 must support each selected mining tool");
        }
        for (Item rejected : new Item[]{Items.DIAMOND_SWORD, Items.SHEARS, Items.BRUSH, Items.BOW}) {
            require(DirectionalHostRules.validateNewOrChanged(new ItemStack(rejected), FIRE) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Fire-primary 31.4 must reject unselected carriers");
        }
        FusedPair water = FIRE.flipped();
        for (Item allowed : new Item[]{Items.DIAMOND_SWORD, Items.DIAMOND_AXE}) {
            require(DirectionalHostRules.validateNewOrChanged(new ItemStack(allowed), water) == RejectReason.NONE,
                "Water-primary 31.4 must support sword and axe");
        }
        for (Item rejected : new Item[]{Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.BOW}) {
            require(DirectionalHostRules.validateNewOrChanged(new ItemStack(rejected), water) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Water-primary 31.4 must reject unselected carriers");
        }
        var socket = new SimpleContainer(4);
        socket.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_SWORD));
        socket.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(FIRE));
        var preview = FusionStationOperations.inspect(socket, player.level().registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1);
        require(!preview.executable() && preview.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
            "Book socketing must show the selected direction's carrier error");
        ItemStack legacy = new ItemStack(Items.DIAMOND_PICKAXE);
        legacy.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(FIRE));
        var flip = new SimpleContainer(4);
        flip.setItem(FusionStationOperations.SLOT_TARGET, legacy);
        var result = FusionStationOperations.executeFlipSocketWithReason(flip, player, player.level().registryAccess(), 0, "");
        require(!result.success() && result.reason() == RejectReason.UNSUPPORTED_DIRECTION_HOST
            && legacy.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().equals(FIRE),
            "An unsupported flip must leave the old pair intact");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
