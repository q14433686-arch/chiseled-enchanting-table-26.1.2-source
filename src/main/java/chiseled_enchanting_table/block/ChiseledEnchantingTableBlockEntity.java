package chiseled_enchanting_table.block;

import chiseled_enchanting_table.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class ChiseledEnchantingTableBlockEntity extends BlockEntity implements Nameable {
    private static final RandomSource RANDOM = RandomSource.create();

    @Nullable
    private Component customName;
    public int color = -1;
    public final int DEFAULT_COLOR = 0xA020F0;

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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (color != -1) {
            output.putInt("color", color);
        }
        output.storeNullable("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC, customName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        color = input.getIntOr("color", -1);
        customName = parseCustomNameSafe(input, "CustomName");
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
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
        if (dyedColor != null) {
            this.color = dyedColor.rgb();
        }
        this.customName = components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        if (this.customName != null) {
            componentMapBuilder.set(DataComponents.CUSTOM_NAME, this.customName);
        }
        if (this.color != -1) {
            componentMapBuilder.set(DataComponents.DYED_COLOR, new DyedItemColor(this.color));
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
    }

    public DataComponentMap getAllComponents() {
        return this.collectComponents();
    }

    public int colorOrDefault() {
        return color == -1 ? DEFAULT_COLOR : color;
    }
}
