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
import net.minecraft.world.item.component.DyedItemColor;
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
        output.putInt("color", color);
        output.storeNullable("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC, customName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int newColor = input.getIntOr("color", -1);
        boolean colorChanged = this.color != newColor;
        this.color = newColor;
        this.customName = parseCustomNameSafe(input, "CustomName");
        if (colorChanged && level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE);
        }
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
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
        output.discard("color");
    }

    public DataComponentMap getAllComponents() {
        return this.collectComponents();
    }

    public int colorOrDefault() {
        return color == -1 ? DEFAULT_COLOR : (0xFF000000 | (color & 0x00FFFFFF));
    }
}
