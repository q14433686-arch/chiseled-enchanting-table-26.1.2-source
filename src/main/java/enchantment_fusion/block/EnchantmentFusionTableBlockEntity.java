package enchantment_fusion.block;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.registry.FusionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
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
 */
public final class EnchantmentFusionTableBlockEntity extends BlockEntity implements Container, Nameable {
    public static final int CONTAINER_SIZE = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private @Nullable Component customName;
    public int animTicks;
    public int surgeTicks;
    public int surgeKind;

    public EnchantmentFusionTableBlockEntity(BlockPos pos, BlockState state) {
        super(FusionBlockEntities.ENCHANTMENT_FUSION_TABLE_BE, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, EnchantmentFusionTableBlockEntity be) {
        be.animTicks++;
        if (be.surgeTicks > 0) {
            be.surgeTicks--;
        }
    }

    public void triggerClientSurge(int kind) {
        this.surgeKind = kind;
        this.surgeTicks = (kind == 2) ? 36 : 28;
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
                enchantment_fusion.registry.FusionDataComponents.FUSED_PAIRS,
                enchantment_fusion.model.FusedEnchantmentList.EMPTY
            );
            for (var p : fused.pairs()) {
                if (p.isAberrant()) {
                    return true;
                }
            }
        }
        return false;
    }

    public ItemStack displayStack() {
        ItemStack target = items.get(FusionStationOperations.SLOT_TARGET);
        if (!target.isEmpty()) {
            return target;
        }
        ItemStack primary = items.get(FusionStationOperations.SLOT_PRIMARY);
        return primary;
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
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.customName = input.read("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
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
