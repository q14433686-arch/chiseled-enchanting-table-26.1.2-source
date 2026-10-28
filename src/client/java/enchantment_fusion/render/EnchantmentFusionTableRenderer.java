package enchantment_fusion.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the levitating host gear or enchanted tome above the Enchantment Fusion Table's
 * amethyst resonance basin using the 26.1.2 {@link BlockEntityRenderState} pipeline.
 */
public final class EnchantmentFusionTableRenderer
    implements BlockEntityRenderer<EnchantmentFusionTableBlockEntity, EnchantmentFusionTableRenderer.FusionTableRenderState> {

    public static final class FusionTableRenderState extends BlockEntityRenderState {
        public final ItemStackRenderState floatingItem = new ItemStackRenderState();
        public float hoverTicks;
        public boolean hasItem;
    }

    public EnchantmentFusionTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public FusionTableRenderState createRenderState() {
        return new FusionTableRenderState();
    }

    @Override
    public void extractRenderState(
        EnchantmentFusionTableBlockEntity blockEntity,
        FusionTableRenderState state,
        float partialTick,
        Vec3 cameraPos,
        @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPos, crumblingOverlay);
        state.hoverTicks = blockEntity.animTicks + partialTick;
        ItemStack display = blockEntity.displayStack();
        state.floatingItem.clear();
        state.hasItem = !display.isEmpty();
        if (state.hasItem) {
            Minecraft mc = Minecraft.getInstance();
            mc.getItemModelResolver().updateForTopItem(
                state.floatingItem,
                display,
                ItemDisplayContext.FIXED,
                blockEntity.getLevel(),
                null,
                (int) blockEntity.getBlockPos().asLong()
            );
        }
    }

    @Override
    public void submit(
        FusionTableRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState cameraState
    ) {
        if (!state.hasItem || state.floatingItem.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        float bob = Mth.sin(state.hoverTicks * 0.08F) * 0.05F;
        poseStack.translate(0.5F, 1.02F + bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees((state.hoverTicks * 2.2F) % 360.0F));
        poseStack.scale(0.58F, 0.58F, 0.58F);
        state.floatingItem.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
