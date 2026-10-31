package enchantment_fusion.smoke;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking.StationAction;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Selection/preview fixtures prepared for the eventual game-process acceptance. */
final class StationSelectionProbe {
    private StationSelectionProbe() {}

    static void exercise(ServerPlayer player) {
        var registries = player.level().registryAccess();
        Holder<Enchantment> fire = enchant(registries, "fire_aspect");
        Holder<Enchantment> loyalty = enchant(registries, "loyalty");
        Holder<Enchantment> mending = enchant(registries, "mending");
        Holder<Enchantment> unbreaking = enchant(registries, "unbreaking");
        Holder<Enchantment> efficiency = enchant(registries, "efficiency");
        boolean savedCreative = player.getAbilities().instabuild;
        try {
            player.getAbilities().instabuild = true;
            var slots = new SimpleContainer(4);
            ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
            FusedPair existing = FusedPair.clean("minecraft:efficiency", 1, "minecraft:silk_touch", 1);
            pick.set(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY.append(existing));
            slots.setItem(FusionStationOperations.SLOT_TARGET, pick);
            slots.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(fire, 1));
            slots.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(loyalty, 1));
            var pair = inspect(slots, player, -1, false);
            require(!pair.executable() && pair.rejectReason() == RejectReason.VESSEL_CAPACITY_FULL,
                "A rejected pair must not silently become a single transmutation");
            require(pick.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().getFirst().equals(existing),
                "A rejected pair must not mutate the host");
            var single = inspect(slots, player, 0, true);
            require(single.executable() && single.kind() == OperationKind.INSCRIBE_SINGLE_CROSS,
                "The same individual inscription remains available in explicit single mode");

            slots.setItem(FusionStationOperations.SLOT_TARGET, new ItemStack(Items.APPLE));
            slots.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(mending, 1));
            slots.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(unbreaking, 1));
            var consumablePair = inspect(slots, player, -1, false);
            require(!consumablePair.executable() && consumablePair.rejectReason() == RejectReason.SELECT_SINGLE_FOR_CONSUMABLE,
                "A consumable with two selected enchantments must ask for single mode");
            require(inspect(slots, player, 0, true).kind() == OperationKind.INSCRIBE_SINGLE_CROSS,
                "Explicit single mode must preserve consumable inscription");

            slots.setItem(FusionStationOperations.SLOT_TARGET, pick);
            slots.setItem(FusionStationOperations.SLOT_PRIMARY, FusionStationOperations.createSingleEnchantedBook(mending, 1));
            slots.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(efficiency, 5));
            require(inspect(slots, player, -1, false).kind() != OperationKind.TOP_UP_LEVEL,
                "A selected pair must not be redirected to an unselected top-up");
            var topUp = inspect(slots, player, 1, true);
            require(topUp.executable() && topUp.kind() == OperationKind.TOP_UP_LEVEL,
                "An explicitly selected matching book can top up");
            var result = FusionStationOperations.executePrimaryWithReason(slots, player, registries,
                FusionStationOperations.TAB_SOCKET, 1, -1, true, topUp.expectedExecuteToken());
            require(result.success() && !slots.getItem(FusionStationOperations.SLOT_PRIMARY).isEmpty()
                && slots.getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty(),
                "Top-up must consume the selected book, not the first unrelated donor");
            require(pick.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
                .pairs().getFirst().primaryLevel() == 5, "Selected top-up level must be applied");

            slots.setItem(FusionStationOperations.SLOT_TARGET, FusedEnchantedBookItem.createForPair(existing));
            slots.setItem(FusionStationOperations.SLOT_SECONDARY, FusionStationOperations.createSingleEnchantedBook(efficiency, 5));
            var bookPair = FusionStationOperations.inspect(slots, registries, FusionStationOperations.TAB_FUSE,
                -1, -1, false, FusionMatrixRegistry.serverSnapshot());
            require(!bookPair.executable() && bookPair.rejectReason() == RejectReason.OUTPUT_OCCUPIED,
                "A pair selection with an occupied fused-book output must not become top-up");
            var bookSingle = FusionStationOperations.inspect(slots, registries, FusionStationOperations.TAB_FUSE,
                1, -1, true, FusionMatrixRegistry.serverSnapshot());
            require(bookSingle.executable() && bookSingle.kind() == OperationKind.TOP_UP_LEVEL,
                "Explicitly selecting the book's matching donor must enable top-up");

            EnchantmentFusionMenu menu = new EnchantmentFusionMenu(0, player.getInventory(), slots,
                ContainerLevelAccess.NULL, BlockPos.ZERO);
            try {
                var candidates = FusionStationOperations.collectCandidates(slots, FusionStationOperations.TAB_FUSE);
                require(candidates.size() == 2, "Selection fixture needs exactly two candidates");
                String firstToken = candidates.get(0).selectionToken();
                String secondToken = candidates.get(1).selectionToken();
                require(menu.performAction(player, StationAction.PICK_SINGLE_A, 0, 0, firstToken)
                    && menu.singleOnlyMode() && menu.computePreview().selection().secondary() == null,
                    "Shift selection must atomically enter single mode");
                require(menu.performAction(player, StationAction.PICK_SECONDARY_B, 1, 0, secondToken)
                    && !menu.singleOnlyMode() && menu.computePreview().selection().secondaryIndex() == 1,
                    "Right-clicking the default B in single mode must restore pair mode");
                require(menu.performAction(player, StationAction.PICK_SECONDARY_B, 1, 0, secondToken)
                    && !menu.singleOnlyMode() && menu.computePreview().selection().secondaryIndex() == 1,
                    "Right-clicking the already-selected B must not silently enter single mode");
                require(menu.performAction(player, StationAction.PICK_SINGLE_A, 0, 0, firstToken)
                    && menu.performAction(player, StationAction.PICK_SECONDARY_B, 0, 0, firstToken)
                    && !menu.singleOnlyMode() && menu.computePreview().selection().secondaryIndex() == 0
                    && menu.computePreview().selection().primaryIndex() == 1,
                    "Right-clicking current A in single mode must make it B and select the other A");
                require(!menu.performAction(player, StationAction.PICK_SINGLE_A, 0, 0, "stale:token")
                    && !menu.singleOnlyMode(), "A stale candidate token must not change selection");
            } finally {
                menu.removed(player);
            }
        } finally {
            player.getAbilities().instabuild = savedCreative;
        }
    }

    private static FusionStationOperations.StationPreview inspect(SimpleContainer slots, ServerPlayer player,
                                                                   int selected, boolean single) {
        return FusionStationOperations.inspect(slots, player.level().registryAccess(), FusionStationOperations.TAB_SOCKET,
            selected, -1, single, FusionMatrixRegistry.serverSnapshot());
    }

    private static Holder<Enchantment> enchant(net.minecraft.core.HolderLookup.Provider registries, String path) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
