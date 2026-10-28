package enchantment_fusion.menu;

import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.EnchantCandidate;
import enchantment_fusion.mechanics.FusionStationOperations.ResolvedSelection;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.menu.FusionNetworking.OpenStationPayload;
import enchantment_fusion.menu.FusionNetworking.StationAction;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionItems;
import enchantment_fusion.registry.FusionMenus;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Container menu for the 2-Tab Enchantment Fusion Table (308 x 214 layout + top-right Grimoire button):
 * <ul>
 *   <li>Slot 0 ({@code 18, 52}): A · Primary Book / Material Slot</li>
 *   <li>Slot 1 ({@code 78, 52}): B · Secondary Book / Material Slot</li>
 *   <li>Slot 2 ({@code 48, 80}): Optional Resonance Stabilizer / Reagent / Split Catalyst</li>
 *   <li>Slot 3 ({@code 48, 108}): Output Fused Book (Tab 0) / Host Equipment or Special Relic (Tab 1)</li>
 * </ul>
 */
public final class EnchantmentFusionMenu extends AbstractContainerMenu {
    public static final int GUI_WIDTH = 308;
    public static final int GUI_HEIGHT = 214;
    public static final int TAB_BAR_H = 20;

    public static final int SLOT_PRIMARY_X = 18;
    public static final int SLOT_PRIMARY_Y = 52;
    public static final int SLOT_SECONDARY_X = 78;
    public static final int SLOT_SECONDARY_Y = 52;
    public static final int SLOT_CATALYST_X = 48;
    public static final int SLOT_CATALYST_Y = 80;
    public static final int SLOT_TARGET_X = 48;
    public static final int SLOT_TARGET_Y = 108;

    public static final int PLAYER_INV_X = 74;
    public static final int PLAYER_INV_Y = 132;

    private final Container stationInv;
    private final ContainerLevelAccess access;
    private final Player player;
    private final BlockPos tablePos;
    // Syncs [0]=activeTab, [1]=selectedPrimaryA, [2]=selectedSecondaryB between server & client
    private final ContainerData uiState = new SimpleContainerData(3);

    public EnchantmentFusionMenu(int syncId, Inventory playerInventory, OpenStationPayload payload) {
        // NET-04: Client menu uses an isolated SimpleContainer(4) synced strictly via menu slots!
        this(
            syncId,
            playerInventory,
            new SimpleContainer(EnchantmentFusionTableBlockEntity.CONTAINER_SIZE),
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
        this.uiState.set(0, CrossDomainTransmutations.isEligibleCrossDomainHost(stationInv.getItem(FusionStationOperations.SLOT_TARGET))
            ? FusionStationOperations.TAB_SOCKET
            : FusionStationOperations.TAB_FUSE);
        this.uiState.set(1, -1);
        this.uiState.set(2, -1);
        this.addDataSlots(this.uiState);
        if (!playerInventory.player.level().isClientSide()) {
            stationInv.startOpen(playerInventory.player);
        }

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

    public BlockPos tableBlockPos() {
        return tablePos;
    }

    public Container stationContainer() {
        return stationInv;
    }

    public Player viewer() {
        return player;
    }

    public int activeTab() {
        return Math.clamp(uiState.get(0), 0, 1);
    }

    public int preferredPrimaryIndex() {
        return uiState.get(1);
    }

    public int preferredSecondaryIndex() {
        return uiState.get(2);
    }

    public boolean isSingleModeToggled() {
        return uiState.get(2) == -2;
    }

    public boolean singleOnlyMode() {
        return isSingleModeToggled();
    }

    private static int resolveCandidateIndex(List<EnchantCandidate> cands, int fallbackIndex, String expectedToken) {
        if (expectedToken != null && !expectedToken.isEmpty()) {
            for (int i = 0; i < cands.size(); i++) {
                if (expectedToken.equals(cands.get(i).selectionToken())) {
                    return i;
                }
            }
            return -1;
        }
        return (fallbackIndex >= 0 && fallbackIndex < cands.size()) ? fallbackIndex : -1;
    }

    public StationPreview computePreview() {
        HolderLookup.Provider registries = player.level().registryAccess();
        return FusionStationOperations.inspect(
            stationInv,
            registries,
            activeTab(),
            preferredPrimaryIndex(),
            preferredSecondaryIndex()
        );
    }

    public boolean performAction(Player actor, StationAction action, int paramA, int paramB) {
        return performAction(actor, action, paramA, paramB, "");
    }

    public boolean performAction(Player actor, StationAction action, int paramA, int paramB, String expectedToken) {
        HolderLookup.Provider registries = actor.level().registryAccess();
        boolean changed = false;

        switch (action) {
            case SELECT_TAB -> {
                int clamped = Math.clamp(paramA, 0, 1);
                uiState.set(0, clamped);
                uiState.set(1, -1);
                uiState.set(2, -1);
                changed = true;
            }
            case PICK_PRIMARY_A -> {
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                int resolvedIdx = resolveCandidateIndex(cands, paramA, expectedToken);
                if (resolvedIdx >= 0) {
                    ResolvedSelection cur = FusionStationOperations.resolveSelection(cands, preferredPrimaryIndex(), preferredSecondaryIndex());
                    if (resolvedIdx == cur.secondaryIndex()) {
                        uiState.set(2, cur.primaryIndex());
                    }
                    uiState.set(1, resolvedIdx);
                    changed = true;
                }
            }
            case PICK_SECONDARY_B -> {
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                int resolvedIdx = resolveCandidateIndex(cands, paramA, expectedToken);
                if (resolvedIdx >= 0) {
                    ResolvedSelection cur = FusionStationOperations.resolveSelection(cands, preferredPrimaryIndex(), preferredSecondaryIndex());
                    if (resolvedIdx == cur.secondaryIndex()) {
                        uiState.set(2, -2);
                    } else {
                        if (resolvedIdx == cur.primaryIndex()) {
                            uiState.set(1, cur.secondaryIndex() >= 0 ? cur.secondaryIndex() : 0);
                        }
                        uiState.set(2, resolvedIdx);
                    }
                    changed = true;
                }
            }
            case TOGGLE_SINGLE_MODE -> {
                if (uiState.get(2) == -2) {
                    uiState.set(2, -1);
                } else {
                    uiState.set(2, -2);
                }
                changed = true;
            }
            case SWAP_ORDER -> {
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                ResolvedSelection cur = FusionStationOperations.resolveSelection(cands, preferredPrimaryIndex(), preferredSecondaryIndex());
                if (cur.primaryIndex() >= 0 && cur.secondaryIndex() >= 0) {
                    EnchantCandidate p = cur.primary();
                    EnchantCandidate s = cur.secondary();
                    if (activeTab() == FusionStationOperations.TAB_FUSE
                        && p != null && s != null
                        && p.sourceSlot() != s.sourceSlot()
                        && cands.size() == 2) {
                        changed = FusionStationOperations.swapInputSlots(stationInv);
                        uiState.set(1, -1);
                        uiState.set(2, -1);
                    } else {
                        uiState.set(1, cur.secondaryIndex());
                        uiState.set(2, cur.primaryIndex());
                        changed = true;
                    }
                } else {
                    changed = FusionStationOperations.swapInputSlots(stationInv);
                }
            }
            case EXECUTE_PRIMARY -> {
                changed = FusionStationOperations.executePrimaryOperation(
                    stationInv, actor, registries, activeTab(), preferredPrimaryIndex(), preferredSecondaryIndex(), expectedToken
                );
                if (changed) {
                    uiState.set(1, -1);
                    uiState.set(2, -1);
                }
            }
            case FLIP_SOCKET -> {
                changed = FusionStationOperations.executeFlipSocket(stationInv, actor, registries, paramA);
            }
            case UNSOCKET_OR_SPLIT -> {
                changed = FusionStationOperations.executeUnsocketOrSplit(stationInv, actor, registries, paramA);
            }
        }

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
            if (CetSoftBridge.isStabilizerCatalyst(source) || CetSoftBridge.isSplitOrFlipCatalyst(source)
                || source.is(Items.LAPIS_LAZULI) || source.is(Items.AMETHYST_SHARD) || source.is(Items.EMERALD)) {
                if (!this.moveItemStackTo(source, 2, 3, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (VesselCapacityRules.isPotentialEquipmentHost(source)) {
                if (!this.moveItemStackTo(source, 3, 4, false)) {
                    return ItemStack.EMPTY;
                }
                uiState.set(0, FusionStationOperations.TAB_SOCKET);
            } else if (CrossDomainTransmutations.isSpecialNonEquipmentHost(source)) {
                Slot targetSlot = this.slots.get(FusionStationOperations.SLOT_TARGET);
                if (targetSlot.hasItem()) {
                    return ItemStack.EMPTY;
                }
                targetSlot.setByPlayer(source.split(1));
                uiState.set(0, FusionStationOperations.TAB_SOCKET);
            } else if (source.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
                if (activeTab() == FusionStationOperations.TAB_FUSE) {
                    if (!this.moveItemStackTo(source, 3, 4, false) && !this.moveItemStackTo(source, 0, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!this.moveItemStackTo(source, 0, 2, false) && !this.moveItemStackTo(source, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (source.is(Items.ENCHANTED_BOOK) || source.is(Items.ECHO_SHARD) || source.is(Items.AMETHYST_CLUSTER)) {
                if (!this.moveItemStackTo(source, 0, 2, false)) {
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
        if (!player.level().isClientSide()) {
            this.stationInv.stopOpen(player);
        }
    }
}
