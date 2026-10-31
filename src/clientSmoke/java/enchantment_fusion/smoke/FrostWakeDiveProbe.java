package enchantment_fusion.smoke;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.signatures.FrostWakeDiveSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Prepared carrier and equipped-selection fixtures; movement/ice effects still need a physical game test. */
final class FrostWakeDiveProbe {
    private static final FusedPair WAKE = FusedPair.clean("minecraft:frost_walker", 2, "minecraft:depth_strider", 3);
    private FrostWakeDiveProbe() {}

    static void exercise(ServerPlayer player) {
        ServerLevel level = player.level();
        Holder<Enchantment> frost = enchant(level, "frost_walker"), depth = enchant(level, "depth_strider");
        for (FusedPair pair : List.of(WAKE, WAKE.flipped())) {
            ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS), shield = new ItemStack(Items.SHIELD);
            require(DirectionalHostRules.validateNewOrChanged(boots, pair) == RejectReason.NONE,
                "Selected ice path and dive should accept foot armor");
            require(DirectionalHostRules.validateNewOrChanged(shield, pair) == RejectReason.UNSUPPORTED_DIRECTION_HOST,
                "Selected ice path and dive should reject shields");
            var socket = new SimpleContainer(4);
            socket.setItem(FusionStationOperations.SLOT_TARGET, shield);
            socket.setItem(FusionStationOperations.SLOT_PRIMARY, FusedEnchantedBookItem.createForPair(pair));
            require(FusionStationOperations.inspect(socket, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1)
                .rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Fused books must reject shields");
            socket.setItem(FusionStationOperations.SLOT_TARGET, boots);
            require(FusionStationOperations.inspect(socket, level.registryAccess(), FusionStationOperations.TAB_SOCKET, -1, -1)
                .executable(), "Fused books must accept boots");
            var direct = new SimpleContainer(4);
            direct.setItem(FusionStationOperations.SLOT_TARGET, shield.copy());
            direct.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(
                FrostWakeDiveSkills.isWakePair(pair) ? frost : depth, pair.primaryLevel()));
            direct.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(
                FrostWakeDiveSkills.isWakePair(pair) ? depth : frost, pair.secondaryLevel()));
            require(FusionStationOperations.inspect(direct, level.registryAccess(), FusionStationOperations.TAB_SOCKET, 0, 1)
                .rejectReason() == RejectReason.UNSUPPORTED_DIRECTION_HOST, "Direct fusion must reject shields");
            direct.setItem(FusionStationOperations.SLOT_TARGET, boots.copy());
            require(FusionStationOperations.inspect(direct, level.registryAccess(), FusionStationOperations.TAB_SOCKET, 0, 1)
                .executable(), "Direct fusion must accept boots");
        }

        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] saved = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) saved[i] = player.getItemBySlot(slots[i]);
        try {
            for (EquipmentSlot slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
            ItemStack heldBoots = new ItemStack(Items.DIAMOND_BOOTS);
            heldBoots.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(WAKE));
            player.setItemSlot(EquipmentSlot.MAINHAND, heldBoots);
            require(FrostWakeDiveSkills.select(player, true) == null, "A held boot is not a worn boot");
            ItemStack oldShield = new ItemStack(Items.SHIELD);
            oldShield.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(WAKE));
            player.setItemSlot(EquipmentSlot.OFFHAND, oldShield);
            require(FrostWakeDiveSkills.select(player, true).stack() == oldShield,
                "An old unsupported carrier keeps its existing runtime path");
            ItemStack wornBoots = heldBoots.copy();
            player.setItemSlot(EquipmentSlot.FEET, wornBoots);
            var chosen = FrostWakeDiveSkills.select(player, true);
            require(chosen != null && chosen.slot() == EquipmentSlot.FEET && chosen.stack() == wornBoots,
                "Actually worn selected boots take priority over an old held carrier");
        } finally {
            for (int i = 0; i < slots.length; i++) player.setItemSlot(slots[i], saved[i]);
        }
    }

    private static Holder<Enchantment> enchant(ServerLevel level, String path) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path)));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
