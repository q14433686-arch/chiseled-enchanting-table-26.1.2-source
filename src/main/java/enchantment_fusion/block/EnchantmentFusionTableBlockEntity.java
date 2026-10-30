package enchantment_fusion.block;

import enchantment_fusion.config.ModRuntimeConfig;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.ActionResult;
import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionBlockEntities;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * Persistent 4-slot container BlockEntity for the Enchantment Fusion Table, also hosting the
 * server-authoritative background ritual ticker so closing the UI screen lets the altar finish
 * channeling in world space and leave the crafted result on the pedestal.
 */
public final class EnchantmentFusionTableBlockEntity extends BlockEntity implements Container, Nameable {
    public static final int CONTAINER_SIZE = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private @Nullable Component customName;
    private long lastSyncedVisualHash = Long.MIN_VALUE;
    public int animTicks;
    public int surgeTicks;
    public int surgeKind;

    // Server-authoritative background ritual state (synced at start/finish for 3D altar rendering)
    private @Nullable UUID ritualActorUuid = null;
    private int ritualTab = 0;
    private int ritualPrimaryIdx = -1;
    private int ritualSecondaryIdx = -1;
    private boolean ritualSingleMode = false;
    private String ritualToken = "";
    private int ritualTicks = 0;
    private int ritualDurationTicks = 0;
    private int ritualOpOrdinal = 0;
    private boolean ritualHighRisk = false;
    private int lastWorldChimeStage = 0;

    public EnchantmentFusionTableBlockEntity(BlockPos pos, BlockState state) {
        super(FusionBlockEntities.ENCHANTMENT_FUSION_TABLE_BE, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, EnchantmentFusionTableBlockEntity be) {
        be.animTicks++;
        if (be.surgeTicks > 0) {
            be.surgeTicks--;
        }
        if (be.ritualDurationTicks > 0 && be.ritualTicks < be.ritualDurationTicks) {
            be.ritualTicks++;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnchantmentFusionTableBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || !be.isRitualActive()) {
            return;
        }
        ServerPlayer actor = be.ritualActorUuid != null
            ? serverLevel.getServer().getPlayerList().getPlayer(be.ritualActorUuid)
            : null;
        if (actor == null || !actor.isAlive()) {
            be.cancelRitual();
            return;
        }
        StationPreview live = FusionStationOperations.inspect(
            be,
            serverLevel.registryAccess(),
            be.ritualTab,
            be.ritualPrimaryIdx,
            be.ritualSecondaryIdx,
            be.ritualSingleMode,
            FusionMatrixRegistry.serverSnapshot()
        );
        if (!live.executable() || !be.ritualToken.equals(live.expectedExecuteToken())) {
            be.cancelRitual();
            return;
        }

        be.ritualTicks++;
        int stage = (be.ritualTicks * 4) / Math.max(1, be.ritualDurationTicks);
        if (stage > be.lastWorldChimeStage && stage < 4) {
            be.lastWorldChimeStage = stage;
            float pitch = 0.96F + stage * 0.18F;
            serverLevel.playSound(
                null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.55F, pitch
            );
            serverLevel.sendParticles(
                be.ritualHighRisk ? ParticleTypes.WITCH : ParticleTypes.ENCHANT,
                pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                6 + stage * 2, 0.25D, 0.18D, 0.25D, 0.08D
            );
        }

        if (be.ritualTicks >= be.ritualDurationTicks) {
            be.completeRitualNow(serverLevel, actor);
        }
    }

    public boolean isRitualActive() {
        return this.ritualDurationTicks > 0 && this.ritualTicks < this.ritualDurationTicks;
    }

    public int ritualTicks() {
        return this.ritualTicks;
    }

    public int ritualDurationTicks() {
        return this.ritualDurationTicks;
    }

    public int ritualOpOrdinal() {
        return this.ritualOpOrdinal;
    }

    public boolean isRitualHighRisk() {
        return this.ritualHighRisk;
    }

    public boolean matchesRitualToken(String token) {
        return isRitualActive() && token != null && token.equals(this.ritualToken);
    }

    public float clientRitualProgress(float partialTick) {
        if (this.ritualDurationTicks <= 0) {
            return 0.0F;
        }
        return Mth.clamp((this.ritualTicks + partialTick) / (float) this.ritualDurationTicks, 0.0F, 1.0F);
    }

    public void beginRitual(
        ServerPlayer actor,
        int tab,
        int primaryIdx,
        int secondaryIdx,
        boolean singleMode,
        String expectedToken,
        StationPreview preview
    ) {
        ModRuntimeConfig.Snapshot cfg = ModRuntimeConfig.localSnapshot();
        boolean highRisk = preview.economyQuote() != null && preview.economyQuote().highRiskTier();
        int baseTicks = highRisk ? cfg.ritualHighRiskTicks() : cfg.ritualNormalTicks();
        OperationKind kind = preview.kind() != null ? preview.kind() : OperationKind.FUSE_BOOKS;
        int dur = switch (kind) {
            case EXPAND_VESSEL -> Math.max(6, Math.round(baseTicks * 0.80F));
            case INSCRIBE_SINGLE_CROSS -> Math.max(6, Math.round(baseTicks * 0.85F));
            default -> baseTicks;
        };

        this.ritualActorUuid = actor.getUUID();
        this.ritualTab = tab;
        this.ritualPrimaryIdx = primaryIdx;
        this.ritualSecondaryIdx = secondaryIdx;
        this.ritualSingleMode = singleMode;
        this.ritualToken = expectedToken == null ? "" : expectedToken;
        this.ritualTicks = 0;
        this.ritualDurationTicks = dur;
        this.ritualOpOrdinal = kind.ordinal();
        this.ritualHighRisk = highRisk;
        this.lastWorldChimeStage = 0;

        if (this.level instanceof ServerLevel sl) {
            sl.playSound(null, this.worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.35F, 1.16F);
        }
        syncRitualStateToClients();
    }

    public ActionResult completeRitualNow(ServerLevel serverLevel, @Nullable ServerPlayer fallbackActor) {
        ServerPlayer actor = this.ritualActorUuid != null
            ? serverLevel.getServer().getPlayerList().getPlayer(this.ritualActorUuid)
            : null;
        if (actor == null) {
            actor = fallbackActor;
        }
        int tab = this.ritualTab;
        int pIdx = this.ritualPrimaryIdx;
        int sIdx = this.ritualSecondaryIdx;
        boolean single = this.ritualSingleMode;
        String tok = this.ritualToken;
        boolean highRisk = this.ritualHighRisk;
        clearRitualFields();

        if (actor == null) {
            syncRitualStateToClients();
            return ActionResult.failed(enchantment_fusion.mechanics.FusionRuleEngine.RejectReason.MISSING_INPUT);
        }

        ActionResult res = FusionStationOperations.executePrimaryWithReason(
            this, actor, serverLevel.registryAccess(), tab, pIdx, sIdx, single, tok
        );
        if (res.success()) {
            serverLevel.playSound(
                null, this.worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                0.75F, highRisk ? 0.82F : 1.14F
            );
            serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 1.10D, this.worldPosition.getZ() + 0.5D,
                14, 0.28D, 0.22D, 0.28D, 0.06D
            );
            for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
                if (sp.containerMenu instanceof EnchantmentFusionMenu openMenu && openMenu.stationContainer() == this) {
                    openMenu.onBackgroundRitualFinished();
                }
            }
        }
        syncRitualStateToClients();
        return res;
    }

    public void cancelRitual() {
        if (!isRitualActive() && this.ritualDurationTicks == 0) {
            return;
        }
        clearRitualFields();
        syncRitualStateToClients();
    }

    private void clearRitualFields() {
        this.ritualActorUuid = null;
        this.ritualToken = "";
        this.ritualTicks = 0;
        this.ritualDurationTicks = 0;
        this.ritualOpOrdinal = 0;
        this.ritualHighRisk = false;
        this.lastWorldChimeStage = 0;
    }

    private void syncRitualStateToClients() {
        super.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.lastSyncedVisualHash = computeVisualHash();
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
            var fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            if (fused.hasAberration()) {
                return true;
            }
        }
        return false;
    }

    private long computeVisualHash() {
        long h = 1469598103934665603L;
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
        h = (h ^ this.ritualDurationTicks) * 1099511628211L;
        h = (h ^ this.ritualOpOrdinal) * 1099511628211L;
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
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            items.set(slot, stack);
            stack.limitSize(getMaxStackSize(stack));
            setChanged();
        }
    }

    @Override
    public void setChanged() {
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
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        clearRitualFields();
        items.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        if (this.customName != null) {
            output.store("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC, this.customName);
        }
        output.putInt("RitualTicks", this.ritualTicks);
        output.putInt("RitualDuration", this.ritualDurationTicks);
        output.putInt("RitualOp", this.ritualOpOrdinal);
        output.putBoolean("RitualHighRisk", this.ritualHighRisk);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.customName = input.read("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
        this.ritualTicks = input.getIntOr("RitualTicks", 0);
        this.ritualDurationTicks = input.getIntOr("RitualDuration", 0);
        this.ritualOpOrdinal = input.getIntOr("RitualOp", 0);
        this.ritualHighRisk = input.getBooleanOr("RitualHighRisk", false);
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
