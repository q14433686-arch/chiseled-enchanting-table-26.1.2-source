package chiseled_enchanting_table.render;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class ChiseledEnchantingTableBlockEntityRenderer implements BlockEntityRenderer<ChiseledEnchantingTableBlockEntity, ChiseledEnchantingTableBlockEntityRenderer.State> {
    public static final class State extends EnchantTableRenderState {
        public boolean rainbow;
        public int clothColor;
        public boolean ritualActive;
        public final ItemStackRenderState floatingItem = new ItemStackRenderState();
        public List<TableCustomParticles.QuadSnapshot> customParticles = List.of();
    }

    public static int clothColor(long ticks, float partialTick) {
        return net.minecraft.client.color.ColorLerper.getLerpedColor(net.minecraft.client.color.ColorLerper.Type.SHEEP,
            chiseled_enchanting_table.cosmetic.RainbowCloth.phase(ticks, partialTick));
    }

    public static final SpriteId BOOK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.defaultNamespaceApply("enchantment/enchanting_table_book");
    private final SpriteGetter sprites;
    private final BookModel bookModel;

    public ChiseledEnchantingTableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChiseledEnchantingTableBlockEntity blockEntity, State renderState, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
        renderState.rainbow = blockEntity.getBlockState().getValue(chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.RAINBOW_CLOTH);
        renderState.clothColor = renderState.rainbow ? clothColor(
            blockEntity.getLevel() == null ? 0 : blockEntity.getLevel().getGameTime(), partialTick) : blockEntity.colorOrDefault();
        renderState.ritualActive = blockEntity.hasActiveRitual();
        renderState.flip = Mth.lerp(partialTick, blockEntity.oFlip, blockEntity.flip);
        renderState.open = Mth.lerp(partialTick, blockEntity.oOpen, blockEntity.open);
        renderState.time = (float) blockEntity.time + partialTick;

        float rotationDelta = blockEntity.rot - blockEntity.oRot;
        while (rotationDelta >= Mth.PI) rotationDelta -= Mth.TWO_PI;
        while (rotationDelta < -Mth.PI) rotationDelta += Mth.TWO_PI;
        renderState.yRot = blockEntity.oRot + rotationDelta * partialTick;

        ItemStack displayStack = blockEntity.visualDisplayStack();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getItemModelResolver() != null) {
            mc.getItemModelResolver().updateForTopItem(
                renderState.floatingItem,
                displayStack,
                ItemDisplayContext.GROUND,
                blockEntity.getLevel(),
                null,
                (int) blockEntity.getBlockPos().asLong()
            );
        }
        renderState.customParticles = TableCustomParticles.extractForTable(
            blockEntity.getBlockPos(), partialTick, cameraPos
        );
    }

    @Override
    public void submit(State renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.rainbow) RainbowClothRenderer.submit(poseStack, submitNodeCollector, renderState.clothColor, renderState.lightCoords);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.75F, 0.5F);
        poseStack.translate(0.0F, 0.1F + Mth.sin((double) (renderState.time * (renderState.ritualActive ? 0.18F : 0.1F))) * (renderState.ritualActive ? 0.02F : 0.01F), 0.0F);
        poseStack.mulPose(Axis.YP.rotation(-renderState.yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));

        float leftPage = Mth.frac(renderState.flip + 0.25F) * 1.6F - 0.3F;
        float rightPage = Mth.frac(renderState.flip + 0.75F) * 1.6F - 0.3F;
        BookModel.State bookState = BookModel.State.forAnimation(
            renderState.time,
            Mth.clamp(leftPage, 0.0F, 1.0F),
            Mth.clamp(rightPage, 0.0F, 1.0F),
            renderState.open
        );
        submitNodeCollector.submitModel(
            this.bookModel,
            bookState,
            poseStack,
            renderState.lightCoords,
            OverlayTexture.NO_OVERLAY,
            -1,
            BOOK_TEXTURE,
            this.sprites,
            0,
            renderState.breakProgress
        );
        poseStack.popPose();

        if (!renderState.floatingItem.isEmpty()) {
            poseStack.pushPose();
            float hoverY = (renderState.ritualActive ? 1.25F : 1.17F)
                + Mth.sin(renderState.time * (renderState.ritualActive ? 0.18F : 0.08F)) * 0.035F;
            float spin = renderState.time * (renderState.ritualActive ? 0.14F : 0.055F);
            poseStack.translate(0.5F, hoverY, 0.5F);
            poseStack.mulPose(Axis.YP.rotation(spin));
            poseStack.scale(0.84F, 0.84F, 0.84F);
            renderState.floatingItem.submit(
                poseStack,
                submitNodeCollector,
                renderState.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
            );
            poseStack.popPose();
        }

        TableCustomParticles.submitQuads(poseStack, submitNodeCollector, renderState.customParticles);
    }
}
