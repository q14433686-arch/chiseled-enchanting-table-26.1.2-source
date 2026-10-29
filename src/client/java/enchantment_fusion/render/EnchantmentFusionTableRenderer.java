package enchantment_fusion.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.menu.FusionNetworking;
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
 * Multi-item orbital 3D renderer for the Enchantment Fusion Table using the 26.1.2
 * {@link BlockEntityRenderState} pipeline:
 * <ul>
 *   <li>Central levitating host gear or tome above the amethyst basin</li>
 *   <li>Up to 3 orbiting satellite reagents/books (Primary, Secondary, Catalyst)</li>
 *   <li>Ritual Surge elevation, vortex acceleration, and inward orbital convergence</li>
 * </ul>
 */
public final class EnchantmentFusionTableRenderer
    implements BlockEntityRenderer<EnchantmentFusionTableBlockEntity, EnchantmentFusionTableRenderer.FusionTableRenderState> {

    public static final class FusionTableRenderState extends BlockEntityRenderState {
        public final ItemStackRenderState centerItem = new ItemStackRenderState();
        public final ItemStackRenderState[] satellites = new ItemStackRenderState[]{
            new ItemStackRenderState(),
            new ItemStackRenderState(),
            new ItemStackRenderState()
        };
        public int satelliteCount;
        public float hoverTicks;
        public float surgeProgress;
        public int surgeKind;
        public boolean hasCenter;
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
        state.surgeKind = blockEntity.surgeKind;
        float maxSurge = (float) FusionNetworking.surgeTicksFor(blockEntity.surgeKind);
        state.surgeProgress = blockEntity.surgeTicks > 0
            ? Mth.clamp((blockEntity.surgeTicks - partialTick) / maxSurge, 0.0F, 1.0F)
            : 0.0F;

        state.centerItem.clear();
        for (ItemStackRenderState sat : state.satellites) {
            sat.clear();
        }
        state.satelliteCount = 0;

        Minecraft mc = Minecraft.getInstance();
        int seedBase = (int) blockEntity.getBlockPos().asLong();

        ItemStack target = blockEntity.getItem(FusionStationOperations.SLOT_TARGET);
        ItemStack primary = blockEntity.getItem(FusionStationOperations.SLOT_PRIMARY);
        ItemStack secondary = blockEntity.getItem(FusionStationOperations.SLOT_SECONDARY);
        ItemStack catalyst = blockEntity.getItem(FusionStationOperations.SLOT_CATALYST);

        boolean primaryIsCenter = target.isEmpty() && !primary.isEmpty();
        ItemStack centerStack = !target.isEmpty() ? target : primary;
        state.hasCenter = !centerStack.isEmpty();
        if (state.hasCenter) {
            mc.getItemModelResolver().updateForTopItem(
                state.centerItem,
                centerStack,
                ItemDisplayContext.FIXED,
                blockEntity.getLevel(),
                null,
                seedBase
            );
        }

        ItemStack[] candidates = new ItemStack[]{
            primaryIsCenter ? ItemStack.EMPTY : primary,
            secondary,
            catalyst
        };
        for (int i = 0; i < candidates.length; i++) {
            ItemStack st = candidates[i];
            if (!st.isEmpty() && state.satelliteCount < state.satellites.length) {
                mc.getItemModelResolver().updateForTopItem(
                    state.satellites[state.satelliteCount],
                    st,
                    ItemDisplayContext.FIXED,
                    blockEntity.getLevel(),
                    null,
                    seedBase + i + 1
                );
                state.satelliteCount++;
            }
        }
    }

    @Override
    public void submit(
        FusionTableRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState cameraState
    ) {
        float surgeArc = Mth.sin(state.surgeProgress * Mth.PI);

        // 1. Central levitating host gear / output book
        if (state.hasCenter && !state.centerItem.isEmpty()) {
            poseStack.pushPose();
            float bob = Mth.sin(state.hoverTicks * 0.08F) * 0.045F + surgeArc * 0.18F;
            poseStack.translate(0.5F, 1.04F + bob, 0.5F);
            float spinDeg = (state.hoverTicks * 2.4F + surgeArc * 260.0F) % 360.0F;
            poseStack.mulPose(Axis.YP.rotationDegrees(spinDeg));
            if (state.surgeKind == 2 && surgeArc > 0.01F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.hoverTicks * 0.9F) * 14.0F * surgeArc));
            }
            float centerScale = 0.58F + surgeArc * 0.10F;
            poseStack.scale(centerScale, centerScale, centerScale);
            state.centerItem.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // 2. Orbiting satellite reagents & donor books
        if (state.satelliteCount > 0) {
            float baseRadius = 0.40F * (1.0F - 0.52F * surgeArc);
            float baseAngle = state.hoverTicks * 0.065F + surgeArc * 3.2F;
            for (int i = 0; i < state.satelliteCount; i++) {
                ItemStackRenderState sat = state.satellites[i];
                if (sat.isEmpty()) continue;

                float phase = baseAngle + (Mth.TWO_PI * i) / state.satelliteCount;
                float ox = Mth.cos(phase) * baseRadius;
                float oz = Mth.sin(phase) * baseRadius;
                float oy = 0.98F + Mth.sin(state.hoverTicks * 0.11F + i * 2.1F) * 0.04F + surgeArc * 0.12F;

                poseStack.pushPose();
                poseStack.translate(0.5F + ox, oy, 0.5F + oz);
                poseStack.mulPose(Axis.YP.rotationDegrees((-phase * Mth.RAD_TO_DEG + 90.0F) % 360.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(16.0F));
                poseStack.scale(0.34F, 0.34F, 0.34F);
                sat.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }
}
