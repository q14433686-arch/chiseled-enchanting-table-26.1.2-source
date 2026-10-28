package enchantment_fusion.menu;

import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.menu.FusionNetworking.OpenStationPayload;
import enchantment_fusion.menu.FusionNetworking.StationAction;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionItems;
import enchantment_fusion.registry.FusionMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Container menu for the Enchantment Fusion Table (276 x 198 layout):
 * <ul>
 *   <li>Slot 0 ({@code 22, 34}): Primary Carrier {@code A}</li>
 *   <li>Slot 1 ({@code 86, 34}): Secondary Graft {@code B}</li>
 *   <li>Slot 2 ({@code 54, 62}): Catalyst / Stabilizer</li>
 *   <li>Slot 3 ({@code 54, 92}): Host Equipment / Target Book</li>
 * </ul>
 */
public final class EnchantmentFusionMenu extends AbstractContainerMenu {
    public static final int GUI_WIDTH = 276;
    public static final int GUI_HEIGHT = 198;

    public static final int SLOT_PRIMARY_X = 22;
    public static final int SLOT_PRIMARY_Y = 34;
    public static final int SLOT_SECONDARY_X = 86;
    public static final int SLOT_SECONDARY_Y = 34;
    public static final int SLOT_CATALYST_X = 54;
    public static final int SLOT_CATALYST_Y = 62;
    public static final int SLOT_TARGET_X = 54;
    public static final int SLOT_TARGET_Y = 92;

    public static final int PLAYER_INV_X = 58;
    public static final int PLAYER_INV_Y = 116;

    private final Container stationInv;
    private final ContainerLevelAccess access;
    private final Player player;
    private final BlockPos tablePos;

    public EnchantmentFusionMenu(int syncId, Inventory playerInventory, OpenStationPayload payload) {
        this(
            syncId,
            playerInventory,
            resolveClientContainer(playerInventory, payload.blockPos()),
            ContainerLevelAccess.NULL,
            payload.blockPos()
        );
    }

    public EnchantmentFusionMenu(
        int syncId,
        Inventory playerInventory,
        Container stationInv,
        ContainerLevelAccess access,
        BlockPos tablePos
    ) {
        super(FusionMenus.ENCHANTMENT_FUSION_MENU, syncId);
        checkContainerSize(stationInv, EnchantmentFusionTableBlockEntity.CONTAINER_SIZE);
        this.stationInv = stationInv;
        this.access = access;
        this.player = playerInventory.player;
        this.tablePos = tablePos;
        stationInv.startOpen(playerInventory.player);

        // 0..3: Workstation slots
        this.addSlot(new Slot(stationInv, FusionStationOperations.SLOT_PRIMARY, SLOT_PRIMARY_X, SLOT_PRIMARY_Y));
        this.addSlot(new Slot(stationInv, FusionStationOperations.SLOT_SECONDARY, SLOT_SECONDARY_X, SLOT_SECONDARY_Y));
        this.addSlot(new Slot(stationInv, FusionStationOperations.SLOT_CATALYST, SLOT_CATALYST_X, SLOT_CATALYST_Y));
        this.addSlot(new Slot(stationInv, FusionStationOperations.SLOT_TARGET, SLOT_TARGET_X, SLOT_TARGET_Y));

        // 4..30: Player main inventory (3 rows x 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        // 31..39: Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    private static Container resolveClientContainer(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof EnchantmentFusionTableBlockEntity be) {
            return be;
        }
        return new SimpleContainer(EnchantmentFusionTableBlockEntity.CONTAINER_SIZE);
    }

    public BlockPos tableBlockPos() {
        return tablePos;
    }

    public Container stationContainer() {
        return stationInv;
    }

    public StationPreview computePreview() {
        HolderLookup.Provider registries = player.level().registryAccess();
        return FusionStationOperations.inspect(stationInv, registries);
    }

    public boolean performAction(Player actor, StationAction action) {
        HolderLookup.Provider registries = actor.level().registryAccess();
        boolean changed = switch (action) {
            case SWAP_ORDER -> FusionStationOperations.swapInputSlots(stationInv);
            case EXECUTE_PRIMARY -> FusionStationOperations.executePrimaryOperation(stationInv, actor, registries);
            case EXECUTE_FLIP -> FusionStationOperations.executeFlipOperation(stationInv, actor, registries);
            case EXECUTE_SPLIT -> FusionStationOperations.executeSplitOperation(stationInv, actor, registries);
        };
        if (changed) {
            this.broadcastChanges();
        }
        return changed;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();

        if (index < 4) {
            if (!this.moveItemStackTo(source, 4, 40, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (CetSoftBridge.isStabilizerCatalyst(source) || CetSoftBridge.isSplitOrFlipCatalyst(source)) {
                if (!this.moveItemStackTo(source, 2, 3, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (VesselCapacityRules.isPotentialEquipmentHost(source) || source.is(Items.BOOK)) {
                if (!this.moveItemStackTo(source, 3, 4, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (source.is(Items.ENCHANTED_BOOK) || source.is(FusionItems.FUSED_ENCHANTED_BOOK)
                || source.is(Items.ECHO_SHARD) || source.is(Items.AMETHYST_CLUSTER)) {
                if (!this.moveItemStackTo(source, 0, 2, false)
                    && !(source.is(FusionItems.FUSED_ENCHANTED_BOOK) && this.moveItemStackTo(source, 3, 4, false))) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, FusionBlocks.ENCHANTMENT_FUSION_TABLE)
            || this.stationInv.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Persistent chest-like table inventory: items stay safely inside EnchantmentFusionTableBlockEntity!
        this.stationInv.stopOpen(player);
    }
}
