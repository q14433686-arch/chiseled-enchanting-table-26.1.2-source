package enchantment_fusion.menu;

import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.ActionResult;
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
 * Syncs {@code uiState[0..3]} ({@code activeTab}, {@code selectedPrimaryA}, {@code selectedSecondaryB},
 * and independent {@code singleOnlyMode}) without sentinel overloading (GUI-3 / GUI-4).
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

    public static final int NO_SELECTION = -1;
    public static final int DATA_ACTIVE_TAB = 0;
    public static final int DATA_PRIMARY_IDX = 1;
    public static final int DATA_SECONDARY_IDX = 2;
    public static final int DATA_SINGLE_MODE = 3;
    public static final int UI_DATA_COUNT = 4;

    private final Container stationInv;
    private final ContainerLevelAccess access;
    private final Player player;
    private final BlockPos tablePos;
    private final ContainerData uiState = new SimpleContainerData(UI_DATA_COUNT);

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
        this.uiState.set(
            DATA_ACTIVE_TAB,
            CrossDomainTransmutations.isEligibleCrossDomainHost(stationInv.getItem(FusionStationOperations.SLOT_TARGET))
                ? FusionStationOperations.TAB_SOCKET
                : FusionStationOperations.TAB_FUSE
        );
        this.uiState.set(DATA_PRIMARY_IDX, NO_SELECTION);
        this.uiState.set(DATA_SECONDARY_IDX, NO_SELECTION);
        this.uiState.set(DATA_SINGLE_MODE, 0);
        this.addDataSlots(this.uiState);
        if (!playerInventory.player.level().isClientSide()) {
            stationInv.startOpen(playerInventory.player);
        }

        // 0..3: Workstation slots (prune stale candidate indices whenever input/target slots mutate, GUI-5)
        this.addSlot(createStationSlot(FusionStationOperations.SLOT_PRIMARY, SLOT_PRIMARY_X, SLOT_PRIMARY_Y, true));
        this.addSlot(createStationSlot(FusionStationOperations.SLOT_SECONDARY, SLOT_SECONDARY_X, SLOT_SECONDARY_Y, true));
        this.addSlot(createStationSlot(FusionStationOperations.SLOT_CATALYST, SLOT_CATALYST_X, SLOT_CATALYST_Y, false));
        this.addSlot(createStationSlot(FusionStationOperations.SLOT_TARGET, SLOT_TARGET_X, SLOT_TARGET_Y, true));

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

    private Slot createStationSlot(int slotIndex, int x, int y, boolean affectsCandidates) {
        return new Slot(this.stationInv, slotIndex, x, y) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (affectsCandidates && !EnchantmentFusionMenu.this.player.level().isClientSide()) {
                    EnchantmentFusionMenu.this.pruneStaleSelectionIndices();
                }
            }
        };
    }

    private void pruneStaleSelectionIndices() {
        List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(this.stationInv, activeTab());
        int n = cands.size();
        int curA = uiState.get(DATA_PRIMARY_IDX);
        int curB = uiState.get(DATA_SECONDARY_IDX);
        if (curA >= n) {
            uiState.set(DATA_PRIMARY_IDX, NO_SELECTION);
        }
        if (curB >= n) {
            uiState.set(DATA_SECONDARY_IDX, NO_SELECTION);
        }
    }

    public BlockPos tableBlockPos() {
        return tablePos;
    }

    public Container stationContainer() {
        return stationInv;
    }

    public int activeTab() {
        return Math.clamp(uiState.get(DATA_ACTIVE_TAB), 0, 1);
    }

    public int preferredPrimaryIndex() {
        return uiState.get(DATA_PRIMARY_IDX);
    }

    public int preferredSecondaryIndex() {
        return uiState.get(DATA_SECONDARY_IDX);
    }

    public boolean isSingleModeToggled() {
        return uiState.get(DATA_SINGLE_MODE) != 0;
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
            return NO_SELECTION;
        }
        return (fallbackIndex >= 0 && fallbackIndex < cands.size()) ? fallbackIndex : NO_SELECTION;
    }

    public StationPreview computePreview() {
        HolderLookup.Provider registries = player.level().registryAccess();
        FusionMatrixRegistry.Snapshot snap = FusionMatrixRegistry.snapshotFor(player.level().isClientSide());
        return FusionStationOperations.inspect(
            stationInv,
            registries,
            activeTab(),
            preferredPrimaryIndex(),
            preferredSecondaryIndex(),
            singleOnlyMode(),
            snap
        );
    }

    public boolean performAction(Player actor, StationAction action, int paramA, int paramB, String expectedToken) {
        return performActionWithResult(actor, action, paramA, paramB, expectedToken).success();
    }

    public ActionResult performActionWithResult(
        Player actor,
        StationAction action,
        int paramA,
        int paramB,
        String expectedToken
    ) {
        HolderLookup.Provider registries = actor.level().registryAccess();
        boolean changed = false;
        RejectReason failReason = RejectReason.NONE;

        switch (action) {
            case SELECT_TAB -> {
                int clamped = Math.clamp(paramA, 0, 1);
                uiState.set(DATA_ACTIVE_TAB, clamped);
                uiState.set(DATA_PRIMARY_IDX, NO_SELECTION);
                uiState.set(DATA_SECONDARY_IDX, NO_SELECTION);
                changed = true;
            }
            case PICK_PRIMARY_A -> {
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                int resolvedIdx = resolveCandidateIndex(cands, paramA, expectedToken);
                if (resolvedIdx >= 0) {
                    ResolvedSelection cur = FusionStationOperations.resolveSelection(
                        cands, preferredPrimaryIndex(), preferredSecondaryIndex(), singleOnlyMode()
                    );
                    if (resolvedIdx == cur.secondaryIndex()) {
                        uiState.set(DATA_SECONDARY_IDX, cur.primaryIndex());
                    }
                    uiState.set(DATA_PRIMARY_IDX, resolvedIdx);
                    changed = true;
                } else {
                    failReason = RejectReason.MISSING_INPUT;
                }
            }
            case PICK_SECONDARY_B -> {
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                int resolvedIdx = resolveCandidateIndex(cands, paramA, expectedToken);
                if (resolvedIdx >= 0) {
                    ResolvedSelection cur = FusionStationOperations.resolveSelection(
                        cands, preferredPrimaryIndex(), preferredSecondaryIndex(), singleOnlyMode()
                    );
                    if (!singleOnlyMode() && resolvedIdx == cur.secondaryIndex()) {
                        uiState.set(DATA_SINGLE_MODE, 1);
                    } else {
                        uiState.set(DATA_SINGLE_MODE, 0);
                        if (resolvedIdx == cur.primaryIndex()) {
                            uiState.set(DATA_PRIMARY_IDX, cur.secondaryIndex() >= 0 ? cur.secondaryIndex() : 0);
                        }
                        uiState.set(DATA_SECONDARY_IDX, resolvedIdx);
                    }
                    changed = true;
                } else {
                    failReason = RejectReason.MISSING_INPUT;
                }
            }
            case TOGGLE_SINGLE_MODE -> {
                int next = singleOnlyMode() ? 0 : 1;
                uiState.set(DATA_SINGLE_MODE, next);
                changed = true;
            }
            case SWAP_SLOTS -> {
                // Dedicated physical slot swapper: Slot 0 <-> Slot 1 (GUI-1)
                changed = FusionStationOperations.swapInputSlots(stationInv);
                if (changed) {
                    uiState.set(DATA_PRIMARY_IDX, NO_SELECTION);
                    uiState.set(DATA_SECONDARY_IDX, NO_SELECTION);
                } else {
                    failReason = RejectReason.MISSING_INPUT;
                }
            }
            case SWAP_ORDER -> {
                // Dedicated [A ▷ B] <-> [B ▷ A] selection order swapper without moving physical items (GUI-1)
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                ResolvedSelection cur = FusionStationOperations.resolveSelection(
                    cands, preferredPrimaryIndex(), preferredSecondaryIndex(), false
                );
                if (cur.primaryIndex() >= 0 && cur.secondaryIndex() >= 0) {
                    uiState.set(DATA_SINGLE_MODE, 0);
                    uiState.set(DATA_PRIMARY_IDX, cur.secondaryIndex());
                    uiState.set(DATA_SECONDARY_IDX, cur.primaryIndex());
                    changed = true;
                } else {
                    failReason = RejectReason.MISSING_INPUT;
                }
            }
            case EXECUTE_PRIMARY -> {
                ActionResult exec = FusionStationOperations.executePrimaryWithReason(
                    stationInv,
                    actor,
                    registries,
                    activeTab(),
                    preferredPrimaryIndex(),
                    preferredSecondaryIndex(),
                    singleOnlyMode(),
                    expectedToken
                );
                changed = exec.success();
                failReason = exec.reason();
                if (changed) {
                    uiState.set(DATA_PRIMARY_IDX, NO_SELECTION);
                    uiState.set(DATA_SECONDARY_IDX, NO_SELECTION);
                }
            }
            case FLIP_SOCKET -> {
                ActionResult flip = FusionStationOperations.executeFlipSocketWithReason(
                    stationInv, actor, registries, paramA, expectedToken
                );
                changed = flip.success();
                failReason = flip.reason();
            }
            case UNSOCKET_OR_SPLIT -> {
                ActionResult split = FusionStationOperations.executeUnsocketOrSplitWithReason(
                    stationInv, actor, registries, paramA, expectedToken
                );
                changed = split.success();
                failReason = split.reason();
            }
        }

        if (changed) {
            this.broadcastChanges();
            return ActionResult.ok();
        }
        return ActionResult.failed(failReason);
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
                uiState.set(DATA_ACTIVE_TAB, FusionStationOperations.TAB_SOCKET);
            } else if (CrossDomainTransmutations.isSpecialNonEquipmentHost(source)) {
                Slot targetSlot = this.slots.get(FusionStationOperations.SLOT_TARGET);
                if (targetSlot.hasItem()) {
                    return ItemStack.EMPTY;
                }
                targetSlot.setByPlayer(source.split(1));
                uiState.set(DATA_ACTIVE_TAB, FusionStationOperations.TAB_SOCKET);
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
