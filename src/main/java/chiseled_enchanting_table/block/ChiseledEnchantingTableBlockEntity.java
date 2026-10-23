package chiseled_enchanting_table.block;

import chiseled_enchanting_table.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class ChiseledEnchantingTableBlockEntity extends BlockEntity implements Nameable {
    private static final RandomSource RANDOM = RandomSource.create();
    public static final int DEFAULT_COLOR = 0xFFA020F0;

    @Nullable
    private Component customName;
    public volatile int color = -1;
    public volatile int epiphanySeed = 0;
    public volatile long epiphanyFreezeUntilGameTime = 0L;
    public volatile long pendingFreezeTicks = 0L;
    public volatile int frozenCycleIndex = 0;

    // Vanilla enchanting-table-style book animation state, copied as data fields so the 26.1 renderer can use it.
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;

    public ChiseledEnchantingTableBlockEntity(BlockPos pos, BlockState state) {
        super(EntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE, pos, state);
    }

    @Override
    public @Nullable Object getRenderData() {
        return colorOrDefault();
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.level != null) {
            resolvePendingFreeze(this.level.getGameTime());
        }
        output.putInt("color", color);
        output.putInt("epiphanySeed", epiphanySeed);
        output.putLong("epiphanyFreezeUntil", epiphanyFreezeUntilGameTime);
        output.putLong("pendingFreezeTicks", pendingFreezeTicks);
        output.putInt("frozenCycleIndex", frozenCycleIndex);
        output.storeNullable("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC, customName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int newColor = input.getIntOr("color", -1);
        this.epiphanySeed = input.getIntOr("epiphanySeed", 0);
        this.epiphanyFreezeUntilGameTime = input.getLongOr("epiphanyFreezeUntil", 0L);
        this.pendingFreezeTicks = input.getLongOr("pendingFreezeTicks", 0L);
        this.frozenCycleIndex = input.getIntOr("frozenCycleIndex", 0);
        boolean colorChanged = this.color != newColor;
        this.color = newColor;
        this.customName = parseCustomNameSafe(input, "CustomName");
        if (colorChanged && level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
        }
    }

    private void resolvePendingFreeze(long gameTime) {
        if (this.pendingFreezeTicks > 0L) {
            this.epiphanyFreezeUntilGameTime = gameTime + this.pendingFreezeTicks;
            this.pendingFreezeTicks = 0L;
            this.setChanged();
        }
    }

    public int getOrInitEpiphanySeed() {
        if (this.epiphanySeed == 0) {
            int init = (int) Mth.getSeed(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            if (init == 0) init = 1;
            this.epiphanySeed = init;
            this.setChanged();
        }
        return this.epiphanySeed;
    }

    public boolean isEpiphanyFrozen(long gameTime) {
        resolvePendingFreeze(gameTime);
        return this.epiphanyFreezeUntilGameTime > gameTime;
    }

    public long remainingFreezeTicks(long gameTime) {
        resolvePendingFreeze(gameTime);
        return Math.max(0L, this.epiphanyFreezeUntilGameTime - gameTime);
    }

    public int activeCycleIndex(long gameTime, int cycleTicks) {
        if (isEpiphanyFrozen(gameTime)) {
            return this.frozenCycleIndex & 0xFFFF;
        }
        return (int) ((gameTime / Math.max(200L, cycleTicks)) & 0xFFFFL);
    }

    public void addFreezeTicks(long gameTime, int cycleTicks, int addTicks, int maxTicks) {
        resolvePendingFreeze(gameTime);
        if (!isEpiphanyFrozen(gameTime)) {
            this.frozenCycleIndex = (int) ((gameTime / Math.max(200L, cycleTicks)) & 0xFFFFL);
            this.epiphanyFreezeUntilGameTime = gameTime + Math.min(maxTicks, addTicks);
        } else {
            this.epiphanyFreezeUntilGameTime = Math.min(gameTime + maxTicks, this.epiphanyFreezeUntilGameTime + addTicks);
        }
        this.setChanged();
    }

    public int clearFreezeAndAdvanceSeed() {
        this.epiphanyFreezeUntilGameTime = 0L;
        this.pendingFreezeTicks = 0L;
        return advanceEpiphanySeed();
    }

    public int advanceEpiphanySeed() {
        int cur = getOrInitEpiphanySeed();
        int next = cur * 1664525 + 1013904223;
        if (next == 0) next = 1;
        this.epiphanySeed = next;
        this.setChanged();
        return next;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChiseledEnchantingTableBlockEntity blockEntity) {
        bookAnimationTick(level, pos, state, blockEntity);
    }

    private static void bookAnimationTick(Level level, BlockPos pos, BlockState state, ChiseledEnchantingTableBlockEntity blockEntity) {
        blockEntity.oOpen = blockEntity.open;
        blockEntity.oRot = blockEntity.rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3.0D, false);
        if (player != null) {
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            blockEntity.tRot = (float)Mth.atan2(dz, dx);
            blockEntity.open += 0.1F;
            if (blockEntity.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float previousFlipTarget = blockEntity.flipT;
                do {
                    blockEntity.flipT += (float)(RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (previousFlipTarget == blockEntity.flipT);
            }
        } else {
            blockEntity.tRot += 0.02F;
            blockEntity.open -= 0.1F;
        }

        while (blockEntity.rot >= Mth.PI) blockEntity.rot -= Mth.TWO_PI;
        while (blockEntity.rot < -Mth.PI) blockEntity.rot += Mth.TWO_PI;
        while (blockEntity.tRot >= Mth.PI) blockEntity.tRot -= Mth.TWO_PI;
        while (blockEntity.tRot < -Mth.PI) blockEntity.tRot += Mth.TWO_PI;

        float rotationDelta = blockEntity.tRot - blockEntity.rot;
        while (rotationDelta >= Mth.PI) rotationDelta -= Mth.TWO_PI;
        while (rotationDelta < -Mth.PI) rotationDelta += Mth.TWO_PI;
        blockEntity.rot += rotationDelta * 0.4F;
        blockEntity.open = Mth.clamp(blockEntity.open, 0.0F, 1.0F);
        ++blockEntity.time;
        blockEntity.oFlip = blockEntity.flip;
        float flipVelocity = (blockEntity.flipT - blockEntity.flip) * 0.4F;
        flipVelocity = Mth.clamp(flipVelocity, -0.2F, 0.2F);
        blockEntity.flipA += (flipVelocity - blockEntity.flipA) * 0.9F;
        blockEntity.flip += blockEntity.flipA;
    }

    @Override
    public Component getName() {
        return this.customName != null ? this.customName : Component.translatable("container.chiseled_enchanting_table.chiseled_enchanting_table");
    }

    public void setCustomName(@Nullable Component customName) {
        this.customName = customName;
    }

    @Override
    @Nullable
    public Component getCustomName() {
        return this.customName;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        DyedItemColor dyedColor = components.get(DataComponents.DYED_COLOR);
        this.color = dyedColor != null ? (dyedColor.rgb() & 0x00FFFFFF) : -1;
        this.customName = components.get(DataComponents.CUSTOM_NAME);
        CustomData customData = components.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            this.epiphanySeed = tag.getIntOr("epiphanySeed", 0);
            this.pendingFreezeTicks = tag.getLongOr("pendingFreezeTicks", 0L);
            this.frozenCycleIndex = tag.getIntOr("frozenCycleIndex", 0);
            if (this.level != null) {
                resolvePendingFreeze(this.level.getGameTime());
            }
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        if (this.customName != null) {
            componentMapBuilder.set(DataComponents.CUSTOM_NAME, this.customName);
        }
        if (this.color != -1) {
            componentMapBuilder.set(DataComponents.DYED_COLOR, new DyedItemColor(this.color & 0x00FFFFFF));
        }
        long rem = this.pendingFreezeTicks > 0L
            ? this.pendingFreezeTicks
            : (this.level != null ? remainingFreezeTicks(this.level.getGameTime()) : 0L);
        if (rem > 0L) {
            CompoundTag tag = new CompoundTag();
            if (this.epiphanySeed != 0) {
                tag.putInt("epiphanySeed", this.epiphanySeed);
            }
            tag.putLong("pendingFreezeTicks", rem);
            tag.putInt("frozenCycleIndex", this.frozenCycleIndex);
            int totalSec = (int) ((rem + 19L) / 20L);
            String clock = String.format("%02d:%02d", totalSec / 60, totalSec % 60);
            componentMapBuilder.set(
                DataComponents.LORE,
                new ItemLore(java.util.List.of(
                    Component.translatable("item.chiseled_enchanting_table.frozen_lore", clock)
                        .withStyle(style -> style.withItalic(false).withColor(net.minecraft.ChatFormatting.AQUA))
                ))
            );
            componentMapBuilder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
        output.discard("color");
        output.discard("epiphanySeed");
        output.discard("epiphanyFreezeUntil");
        output.discard("pendingFreezeTicks");
        output.discard("frozenCycleIndex");
    }

    public DataComponentMap getAllComponents() {
        return this.collectComponents();
    }

    public int colorOrDefault() {
        return color == -1 ? DEFAULT_COLOR : (0xFF000000 | (color & 0x00FFFFFF));
    }
}
