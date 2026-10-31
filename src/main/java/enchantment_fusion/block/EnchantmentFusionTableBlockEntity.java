package enchantment_fusion.block;

import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.FusionEconomyRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.StationTargetRevision;
import enchantment_fusion.mechanics.FusionStationOperations.ActionResult;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.data.FusionMatrixRegistry;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionBlockEntities;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent 4-slot container BlockEntity for the Enchantment Fusion Table.
 * Deduplicates {@code sendBlockUpdated} via a 4-slot visual render signature so stack-count changes
 * do not broadcast redundant chunk BE packets while keeping 3D orbital rendering &amp; ambient particles live (NET-02).
 */
public final class EnchantmentFusionTableBlockEntity extends BlockEntity implements Container, Nameable {
    public static final int CONTAINER_SIZE = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final StationTargetRevision targetRevision = new StationTargetRevision();
    private @Nullable Component customName;
    private long lastSyncedVisualHash = Long.MIN_VALUE;
    public int animTicks;
    public int surgeTicks;
    public int surgeKind;

    /** A server-owned transaction; inputs are locked and rechecked at completion. */
    private static final class PendingRitual {
        final UUID owner;
        final int tab, primary, secondary, totalTicks;
        final boolean single;
        final String token, rulesFingerprint;
        final NonNullList<ItemStack> inputs;
        int remainingTicks;

        PendingRitual(UUID owner, int tab, int primary, int secondary, boolean single, String token,
                      String rulesFingerprint, int totalTicks, int remainingTicks, NonNullList<ItemStack> inputs) {
            this.owner = owner;
            this.tab = tab;
            this.primary = primary;
            this.secondary = secondary;
            this.single = single;
            this.token = token;
            this.rulesFingerprint = rulesFingerprint;
            this.totalTicks = totalTicks;
            this.remainingTicks = remainingTicks;
            this.inputs = inputs;
        }
    }

    private @Nullable PendingRitual ritual;

    public EnchantmentFusionTableBlockEntity(BlockPos pos, BlockState state) {
        super(FusionBlockEntities.ENCHANTMENT_FUSION_TABLE_BE, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, EnchantmentFusionTableBlockEntity be) {
        be.animTicks++;
        if (be.ritual != null && be.ritual.remainingTicks > 1) {
            be.ritual.remainingTicks--;
        }
        if (be.surgeTicks > 0) {
            be.surgeTicks--;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnchantmentFusionTableBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || be.ritual == null) return;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(be.ritual.owner);
        // No resources have been paid yet. If the owner logs out, release the table safely.
        if (owner == null || !owner.isAlive()) {
            be.cancelRitual();
            return;
        }
        if (--be.ritual.remainingTicks <= 0) {
            be.finishRitual(owner);
        } else {
            be.setChanged(); // persist progress; visual packets are deduplicated
        }
    }

    public boolean hasActiveRitual() { return ritual != null; }
    public int ritualRemainingTicks() { return ritual == null ? 0 : ritual.remainingTicks; }
    public int ritualTotalTicks() { return ritual == null ? 0 : ritual.totalTicks; }
    public boolean ownsRitual(UUID playerId) { return ritual != null && ritual.owner.equals(playerId); }

    public float ritualProgress() {
        return ritual == null ? 0.0F : Math.clamp(1.0F - (float) ritual.remainingTicks / ritual.totalTicks, 0.0F, 1.0F);
    }

    public ActionResult startRitual(ServerPlayer owner, int tab, int primary, int secondary,
                                    boolean single, String expectedToken) {
        if (ritual != null || expectedToken == null || expectedToken.isEmpty()) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        StationPreview preview = FusionStationOperations.inspect(this, owner.level().registryAccess(),
            tab, primary, secondary, single, FusionMatrixRegistry.serverSnapshot());
        if (!preview.executable() || !expectedToken.equals(preview.expectedExecuteToken())) {
            return ActionResult.failed(preview.executable() ? RejectReason.MISSING_INPUT : preview.rejectReason());
        }
        if (!owner.getAbilities().instabuild) {
            if (owner.experienceLevel < preview.xpCost()) return ActionResult.failed(RejectReason.INSUFFICIENT_XP);
            if (preview.economyQuote() != null && !FusionEconomyRules.canAfford(this, owner, preview.economyQuote())) {
                return ActionResult.failed(RejectReason.INSUFFICIENT_REAGENTS);
            }
        }
        int duration = switch (preview.kind()) {
            case EXPAND_VESSEL -> 29;
            case INSCRIBE_SINGLE_CROSS -> 31;
            case PURIFY_ABERRANT -> 39;
            default -> 37;
        };
        NonNullList<ItemStack> snapshot = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < CONTAINER_SIZE; i++) snapshot.set(i, items.get(i).copy());
        ritual = new PendingRitual(owner.getUUID(), tab, primary, secondary, single, expectedToken,
            FusionConfigStore.rulesFingerprint(), duration, duration, snapshot);
        setChanged();
        return ActionResult.ok();
    }

    /** Only the initiator can skip; completion uses the same validation as the timer. */
    public ActionResult skipRitual(ServerPlayer actor) {
        if (!ownsRitual(actor.getUUID())) return ActionResult.failed(RejectReason.MISSING_INPUT);
        return finishRitual(actor);
    }

    public boolean cancelRitual(UUID actor) {
        if (!ownsRitual(actor)) return false;
        cancelRitual();
        return true;
    }

    private void cancelRitual() {
        ritual = null;
        setChanged();
    }

    private ActionResult finishRitual(ServerPlayer owner) {
        PendingRitual pending = ritual;
        if (pending == null || !pending.owner.equals(owner.getUUID())) {
            return ActionResult.failed(RejectReason.MISSING_INPUT);
        }
        // Unlock before modifying the inventory. A changed slot, cost or selection aborts
        // without consuming anything, including when another mod bypassed the slot lock.
        ritual = null;
        setChanged();
        if (!pending.rulesFingerprint.equals(FusionConfigStore.rulesFingerprint())) {
            return ritualFailed(owner, RejectReason.MISSING_INPUT);
        }
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (!ItemStack.matches(items.get(i), pending.inputs.get(i))) {
                return ritualFailed(owner, RejectReason.MISSING_INPUT);
            }
        }
        ActionResult result = FusionStationOperations.executePrimaryWithReason(this, owner, owner.level().registryAccess(),
            pending.tab, pending.primary, pending.secondary, pending.single, pending.token);
        if (!result.success()) return ritualFailed(owner, result.reason());
        return result;
    }

    private ActionResult ritualFailed(ServerPlayer owner, RejectReason reason) {
        String key = reason == null || reason.langKey().isEmpty()
            ? "message.enchantment_fusion.action_rejected" : reason.langKey();
        owner.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        return ActionResult.failed(reason);
    }

    public void triggerClientSurge(int kind) {
        this.surgeKind = kind;
        this.surgeTicks = FusionNetworking.surgeTicksFor(kind);
    }

    public int occupiedSlotCount() {
        int count = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public boolean hasAberrantItem() {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            var fused = stack.getOrDefault(
                FusionDataComponents.FUSED_PAIRS,
                FusedEnchantmentList.EMPTY
            );
            if (fused.hasAberration()) {
                return true;
            }
        }
        return false;
    }

    private long computeVisualHash() {
        long h = 1469598103934665603L;
        h = (h ^ (hasActiveRitual() ? 1L : 0L)) * 1099511628211L;
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            ItemStack st = items.get(i);
            h = (h ^ i) * 1099511628211L;
            if (st.isEmpty()) {
                h = (h ^ 0xFFL) * 1099511628211L;
            } else {
                int itemId = BuiltInRegistries.ITEM.getId(st.getItem());
                boolean foil = st.hasFoil();
                boolean aberrant = st.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).hasAberration();
                long packed = ((long) itemId << 2) | (foil ? 2L : 0L) | (aberrant ? 1L : 0L);
                h = (h ^ packed) * 1099511628211L;
            }
        }
        return h;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return (slot >= 0 && slot < items.size()) ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (hasActiveRitual()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return hasActiveRitual() ? ItemStack.EMPTY : ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!hasActiveRitual() && slot >= 0 && slot < items.size()) {
            items.set(slot, stack);
            stack.limitSize(getMaxStackSize(stack));
            setChanged();
        }
    }

    /** Shared by every viewer; re-observing also catches in-place changes before a request. */
    public long targetRevision() {
        return targetRevision.observe(items.get(FusionStationOperations.SLOT_TARGET));
    }

    @Override
    public void setChanged() {
        targetRevision();
        super.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            long nextVisual = computeVisualHash();
            if (nextVisual != this.lastSyncedVisualHash) {
                this.lastSyncedVisualHash = nextVisual;
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { return !hasActiveRitual(); }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) { return !hasActiveRitual(); }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        ritual = null;
        items.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        if (ritual != null) {
            output.putString("RitualOwner", ritual.owner.toString());
            output.putString("RitualToken", ritual.token);
            output.putString("RitualRules", ritual.rulesFingerprint);
            output.putInt("RitualTab", ritual.tab);
            output.putInt("RitualPrimary", ritual.primary);
            output.putInt("RitualSecondary", ritual.secondary);
            output.putBoolean("RitualSingle", ritual.single);
            output.putInt("RitualTotal", ritual.totalTicks);
            output.putInt("RitualRemaining", ritual.remainingTicks);
            ContainerHelper.saveAllItems(output.child("RitualInputs"), ritual.inputs, true);
        }
        if (this.customName != null) {
            output.store("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC, this.customName);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.ritual = null;
        String ownerId = input.getStringOr("RitualOwner", "");
        String token = input.getStringOr("RitualToken", "");
        String rules = input.getStringOr("RitualRules", "");
        int total = input.getIntOr("RitualTotal", 0);
        int remaining = input.getIntOr("RitualRemaining", 0);
        if (!ownerId.isEmpty() && !token.isEmpty() && token.length() <= 512 && rules.length() == 64
            && total > 0 && total <= 100 && remaining > 0 && remaining <= total
            && input.child("RitualInputs").isPresent()) {
            try {
                NonNullList<ItemStack> snapshot = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(input.childOrEmpty("RitualInputs"), snapshot);
                this.ritual = new PendingRitual(UUID.fromString(ownerId), input.getIntOr("RitualTab", 0),
                    input.getIntOr("RitualPrimary", -1), input.getIntOr("RitualSecondary", -1),
                    input.getBooleanOr("RitualSingle", false), token, rules, total, remaining, snapshot);
            } catch (IllegalArgumentException ignored) {
                // Invalid saved transactions are abandoned, never executed.
            }
        }
        this.customName = input.read("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
        this.lastSyncedVisualHash = computeVisualHash();
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.customName = components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (this.customName != null) {
            builder.set(DataComponents.CUSTOM_NAME, this.customName);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getName() {
        return this.customName != null
            ? this.customName
            : Component.translatable("block.enchantment_fusion.enchantment_fusion_table");
    }

    @Override
    public @Nullable Component getCustomName() {
        return this.customName;
    }
}
