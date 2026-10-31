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
import net.minecraft.server.level.ServerPlayer;
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
 * Container menu for the Compact Split-Wing Live-Stage Enchantment Fusion Altar ({@code 304 x 218}):
 * <ul>
 *   <li>Left Wing ({@code 4..92}): Slot 0 Primary [A], [⇄] Medallion, Slot 1 Secondary [B],
 *       Slot 2 Catalyst, Slot 3 Host Gear / Output Book</li>
 *   <li>Center Live Stage ({@code 96..184}): Scissored Real-Time 3D Fusion Altar &amp; 8-Scene Stage Renderer</li>
 *   <li>Right Wing ({@code 188..300}): Auto-Morphing Resonance Panel + In-Panel Synergy Codex Popover</li>
 *   <li>Bottom Pedestal ({@code 63..241}): Player 3x9 Inventory + Hotbar</li>
 * </ul>
 * Syncs {@code uiState[0..3]} ({@code activeTab}, {@code selectedPrimaryA}, {@code selectedSecondaryB},
 * and independent {@code singleOnlyMode}) without sentinel overloading (GUI-3 / GUI-4).
 */
public final class EnchantmentFusionMenu extends AbstractContainerMenu {
    public static final int GUI_WIDTH = 304;
    public static final int GUI_HEIGHT = 218;
    public static final int TAB_BAR_H = 0;

    public static final int SLOT_PRIMARY_X = 14;
    public static final int SLOT_PRIMARY_Y = 32;
    public static final int SLOT_SECONDARY_X = 64;
    public static final int SLOT_SECONDARY_Y = 32;
    public static final int SLOT_CATALYST_X = 14;
    public static final int SLOT_CATALYST_Y = 78;
    public static final int SLOT_TARGET_X = 64;
    public static final int SLOT_TARGET_Y = 78;

    public static final int PLAYER_INV_X = 71;
    public static final int PLAYER_INV_Y = 136;

    public static final int NO_SELECTION = -1;
    public static final int DATA_ACTIVE_TAB = 0;
    public static final int DATA_PRIMARY_IDX = 1;
    public static final int DATA_SECONDARY_IDX = 2;
    public static final int DATA_SINGLE_MODE = 3;
    public static final int DATA_RITUAL_REMAINING = 4;
    public static final int DATA_RITUAL_TOTAL = 5;
    public static final int DATA_RITUAL_OWNER = 6;
    public static final int UI_DATA_COUNT = 7;

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
            public boolean mayPlace(ItemStack stack) { return !isRitualRunning(); }

            @Override
            public boolean mayPickup(Player player) { return !isRitualRunning(); }

            @Override
            public void setChanged() {
                super.setChanged();
                if (slotIndex == FusionStationOperations.SLOT_TARGET) {
                    EnchantmentFusionMenu.this.syncAutoMorphMode();
                }
                if (affectsCandidates && !EnchantmentFusionMenu.this.player.level().isClientSide()) {
                    EnchantmentFusionMenu.this.pruneStaleSelectionIndices();
                }
            }
        };
    }

    private void syncAutoMorphMode() {
        if (this.player.level().isClientSide()) {
            return;
        }
        ItemStack host = this.stationInv.getItem(FusionStationOperations.SLOT_TARGET);
        int desired = CrossDomainTransmutations.isEligibleCrossDomainHost(host)
            ? FusionStationOperations.TAB_SOCKET
            : FusionStationOperations.TAB_FUSE;
        if (this.uiState.get(DATA_ACTIVE_TAB) != desired) {
            this.uiState.set(DATA_ACTIVE_TAB, desired);
            this.uiState.set(DATA_PRIMARY_IDX, NO_SELECTION);
            this.uiState.set(DATA_SECONDARY_IDX, NO_SELECTION);
        }
        // Book synthesis has no single-mode control. Do not leave it stuck in a hidden
        // mode after a fused book was removed or after switching away from equipment.
        if (desired == FusionStationOperations.TAB_FUSE && !host.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            this.uiState.set(DATA_SINGLE_MODE, 0);
        }
    }

    @Override
    public void broadcastChanges() {
        syncAutoMorphMode();
        if (!this.player.level().isClientSide() && this.stationInv instanceof EnchantmentFusionTableBlockEntity be) {
            uiState.set(DATA_RITUAL_REMAINING, be.ritualRemainingTicks());
            uiState.set(DATA_RITUAL_TOTAL, be.ritualTotalTicks());
            uiState.set(DATA_RITUAL_OWNER, be.ownsRitual(this.player.getUUID()) ? 1 : 0);
        }
        super.broadcastChanges();
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

    public boolean isRitualRunning() {
        if (!player.level().isClientSide() && stationInv instanceof EnchantmentFusionTableBlockEntity be) {
            return be.hasActiveRitual();
        }
        return uiState.get(DATA_RITUAL_REMAINING) > 0;
    }

    public int ritualRemainingTicks() { return Math.max(0, uiState.get(DATA_RITUAL_REMAINING)); }
    public int ritualTotalTicks() { return Math.max(0, uiState.get(DATA_RITUAL_TOTAL)); }
    public boolean isRitualOwner() { return uiState.get(DATA_RITUAL_OWNER) != 0; }

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
        ItemStack host = stationInv.getItem(FusionStationOperations.SLOT_TARGET);
        int effectiveTab = CrossDomainTransmutations.isEligibleCrossDomainHost(host)
            ? FusionStationOperations.TAB_SOCKET
            : activeTab();
        return FusionStationOperations.inspect(
            stationInv,
            registries,
            effectiveTab,
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
        syncAutoMorphMode();
        if (isRitualRunning() && action != StationAction.SKIP_RITUAL && action != StationAction.CANCEL_RITUAL) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
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
            case PICK_PRIMARY_A, PICK_SINGLE_A -> {
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                int resolvedIdx = resolveCandidateIndex(cands, paramA, expectedToken);
                if (resolvedIdx >= 0) {
                    ResolvedSelection cur = FusionStationOperations.resolveSelection(
                        cands, preferredPrimaryIndex(), preferredSecondaryIndex(), singleOnlyMode()
                    );
                    if (action == StationAction.PICK_PRIMARY_A && resolvedIdx == cur.secondaryIndex()) {
                        uiState.set(DATA_SECONDARY_IDX, cur.primaryIndex());
                    }
                    uiState.set(DATA_PRIMARY_IDX, resolvedIdx);
                    if (action == StationAction.PICK_SINGLE_A) uiState.set(DATA_SINGLE_MODE, 1);
                    changed = true;
                } else {
                    failReason = RejectReason.MISSING_INPUT;
                }
            }
            case PICK_SECONDARY_B -> {
                List<EnchantCandidate> cands = FusionStationOperations.collectCandidates(stationInv, activeTab());
                int resolvedIdx = resolveCandidateIndex(cands, paramA, expectedToken);
                if (resolvedIdx >= 0) {
                    // Right-click is an explicit pair selection, including when this row
                    // happened to be the default B. Do it in one server action so a stale
                    // client toggle cannot silently switch the result back to single mode.
                    ResolvedSelection paired = FusionStationOperations.resolveSelection(
                        cands, preferredPrimaryIndex(), preferredSecondaryIndex(), false
                    );
                    if (resolvedIdx == paired.primaryIndex() && paired.secondaryIndex() < 0) {
                        failReason = RejectReason.MISSING_INPUT;
                    } else {
                        if (resolvedIdx == paired.primaryIndex()) {
                            uiState.set(DATA_PRIMARY_IDX, paired.secondaryIndex());
                        }
                        uiState.set(DATA_SECONDARY_IDX, resolvedIdx);
                        uiState.set(DATA_SINGLE_MODE, 0);
                        changed = true;
                    }
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
                ActionResult exec;
                if (stationInv instanceof EnchantmentFusionTableBlockEntity be && actor instanceof ServerPlayer serverPlayer) {
                    exec = be.startRitual(serverPlayer, activeTab(), preferredPrimaryIndex(),
                        preferredSecondaryIndex(), singleOnlyMode(), expectedToken);
                } else {
                    // Retain the standalone container operation for pure rule/integration tests.
                    exec = FusionStationOperations.executePrimaryWithReason(stationInv, actor, registries,
                        activeTab(), preferredPrimaryIndex(), preferredSecondaryIndex(), singleOnlyMode(), expectedToken);
                }
                changed = exec.success();
                failReason = exec.reason();
            }
            case SKIP_RITUAL -> {
                ActionResult skip = stationInv instanceof EnchantmentFusionTableBlockEntity be && actor instanceof ServerPlayer sp
                    ? be.skipRitual(sp) : ActionResult.failed(RejectReason.MISSING_INPUT);
                changed = skip.success();
                failReason = skip.reason();
                if (changed) {
                    uiState.set(DATA_PRIMARY_IDX, NO_SELECTION);
                    uiState.set(DATA_SECONDARY_IDX, NO_SELECTION);
                }
            }
            case CANCEL_RITUAL -> {
                changed = stationInv instanceof EnchantmentFusionTableBlockEntity be && be.cancelRitual(actor.getUUID());
                if (!changed) failReason = RejectReason.MISSING_INPUT;
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
        if (isRitualRunning()) return ItemStack.EMPTY;
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
        syncAutoMorphMode();
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
