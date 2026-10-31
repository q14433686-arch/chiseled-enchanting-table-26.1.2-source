package enchantment_fusion.smoke;

import com.google.gson.JsonParser;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Prepared carrier-policy fixture; the synthetic ID is not a live third-party mod. */
final class ThirdPartyCarrierProbe {
    private ThirdPartyCarrierProbe() {}

    static void exercise(ServerPlayer player) {
        FusedPair pair = FusedPair.clean("example:storm_edge", 1, "minecraft:sharpness", 1);
        var base = FusionMatrixRegistry.createBuiltInSnapshot();
        var configured = FusionMatrixRegistry.parseFromJson(JsonParser.parseString(
            "{\"profiles\":{\"example:storm_edge\":{\"trigger\":\"ON_MELEE_STRIKE\",\"payload\":\"VOLT\",\"fusion_hosts\":[\"SWORD\"]}}}"
        ).getAsJsonObject(), null);
        var inventory = new SimpleContainer(4);
        inventory.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
        inventory.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_SWORD));
        var unclassified = FusionStationOperations.inspect(inventory, player.level().registryAccess(),
            FusionStationOperations.TAB_SOCKET, -1, -1, false, base);
        require(!unclassified.executable() && unclassified.rejectReason() == RejectReason.UNCLASSIFIED_ENCHANTMENT,
            "Unclassified third-party book must be rejected on equipment");
        var allowed = FusionStationOperations.inspect(inventory, player.level().registryAccess(),
            FusionStationOperations.TAB_SOCKET, -1, -1, false, configured);
        require(allowed.executable(), "Declared sword fusion carrier must be accepted");
        inventory.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.DIAMOND_PICKAXE));
        var wrongHost = FusionStationOperations.inspect(inventory, player.level().registryAccess(),
            FusionStationOperations.TAB_SOCKET, -1, -1, false, configured);
        require(!wrongHost.executable() && wrongHost.rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST,
            "A non-declared carrier must be rejected");

        ItemStack legacy = new ItemStack(Items.DIAMOND_PICKAXE);
        legacy.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(pair));
        inventory.setItem(FusionStationOperations.SLOT_PRIMARY, ItemStack.EMPTY);
        inventory.setItem(FusionStationOperations.SLOT_TARGET, legacy);
        FusionStationOperations.inspect(inventory, player.level().registryAccess(),
            FusionStationOperations.TAB_SOCKET, -1, -1, false, base);
        require(legacy.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().equals(pair),
            "Inspecting unclassified legacy equipment must not rewrite or remove its pair");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
