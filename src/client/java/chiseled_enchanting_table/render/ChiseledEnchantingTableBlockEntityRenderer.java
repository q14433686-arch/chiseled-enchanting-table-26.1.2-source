package chiseled_enchanting_table.render;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class ChiseledEnchantingTableBlockEntityRenderer implements BlockEntityRenderer<ChiseledEnchantingTableBlockEntity, EnchantTableRenderState> {
    public static final SpriteId BOOK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.defaultNamespaceApply("enchantment/enchanting_table_book");
    private final SpriteGetter sprites;
    private final BookModel bookModel;

    public ChiseledEnchantingTableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public EnchantTableRenderState createRenderState() {
        return new EnchantTableRenderState();
    }

    @Override
    public void extractRenderState(ChiseledEnchantingTableBlockEntity blockEntity, EnchantTableRenderState renderState, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
        renderState.flip = Mth.lerp(partialTick, blockEntity.oFlip, blockEntity.flip);
        renderState.open = Mth.lerp(partialTick, blockEntity.oOpen, blockEntity.open);
        renderState.time = (float)blockEntity.time + partialTick;

        float rotationDelta = blockEntity.rot - blockEntity.oRot;
        while (rotationDelta >= Mth.PI) rotationDelta -= Mth.TWO_PI;
        while (rotationDelta < -Mth.PI) rotationDelta += Mth.TWO_PI;
        renderState.yRot = blockEntity.oRot + rotationDelta * partialTick;
    }

    @Override
    public void submit(EnchantTableRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.75F, 0.5F);
        poseStack.translate(0.0F, 0.1F + Mth.sin((double)(renderState.time * 0.1F)) * 0.01F, 0.0F);
        // 26.3: PoseStack#mulPose(Quaternionf) is gone; vanilla's own EnchantTableRenderer now calls
        // rotate(Axis, radians) / rotateDegrees(Axis, degrees) directly.
        poseStack.rotate(Axis.YP, -renderState.yRot);
        poseStack.rotateDegrees(Axis.ZP, 80.0F);

        float leftPage = Mth.frac(renderState.flip + 0.25F) * 1.6F - 0.3F;
        float rightPage = Mth.frac(renderState.flip + 0.75F) * 1.6F - 0.3F;
        BookModel.State bookState = BookModel.State.forAnimation(
            renderState.time,
            Mth.clamp(leftPage, 0.0F, 1.0F),
            Mth.clamp(rightPage, 0.0F, 1.0F),
            renderState.open
        );
        // 26.3: submitModel lost its trailing breakProgress parameter (9 args now); the crumbling
        // overlay is submitted separately through order(1), exactly like vanilla does.
        submitNodeCollector.submitModel(
            this.bookModel,
            bookState,
            poseStack,
            renderState.lightCoords,
            OverlayTexture.NO_OVERLAY,
            -1,
            BOOK_TEXTURE,
            this.sprites,
            0
        );
        if (renderState.breakProgress != null) {
            submitNodeCollector.order(1).submitCrumblingOverlay(
                this.bookModel,
                bookState,
                poseStack,
                BOOK_TEXTURE.renderType(this.bookModel.renderType()),
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                -1,
                renderState.breakProgress
            );
        }
        poseStack.popPose();
    }
}
