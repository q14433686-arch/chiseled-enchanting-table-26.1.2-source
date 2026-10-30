package enchantment_fusion.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.gui.FusionRitualDirector;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.menu.FusionNetworking;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Full 3D animated altar renderer synchronized in real time with {@link FusionRitualDirector}:
 * <ul>
 *   <li>Central levitating amethyst crystal cluster + counter-rotating floating shards
 *       (accelerates smoothly up to 720 deg/s during the 2.0s UI channeling ritual!)</li>
 *   <li>Four corner braziers: idle smoldering ember coals -&gt; simultaneous 1.50x purple flame eruption</li>
 *   <li>Bottom-to-top golden and cyan swirling trajectory particle ribbons + rising purple flame particles</li>
 *   <li>Orbiting host gear and donor books that spiral tightly inward into the crystal core during channeling</li>
 * </ul>
 */
public final class EnchantmentFusionTableRenderer
    implements BlockEntityRenderer<EnchantmentFusionTableBlockEntity, EnchantmentFusionTableRenderer.FusionTableRenderState> {

    private static final int FULLBRIGHT_LIGHT = 0xF000F0;
    private static final float IGNITION_DURATION_TICKS = 15.0F;

    private static final class RitualTracker {
        boolean wasActive;
        float activeBlend;
        int lastSeenAnimTick = -1;
        int ignitionStartTick = -1000;
    }

    private final Map<Long, RitualTracker> trackers = new HashMap<>();
    private @Nullable ItemStack crystalCoreProxy;
    private @Nullable ItemStack crystalShardsProxy;
    private @Nullable ItemStack brazierFlamesProxy;

    public static final class FusionTableRenderState extends BlockEntityRenderState {
        public final ItemStackRenderState crystalCore = new ItemStackRenderState();
        public final ItemStackRenderState crystalShards = new ItemStackRenderState();
        public final ItemStackRenderState brazierFlames = new ItemStackRenderState();
        public final ItemStackRenderState centerItem = new ItemStackRenderState();
        public final ItemStackRenderState[] satellites = new ItemStackRenderState[]{
            new ItemStackRenderState(),
            new ItemStackRenderState(),
            new ItemStackRenderState()
        };
        public FusionAltarVfx.AltarVfxBatch vfxBatch = FusionAltarVfx.AltarVfxBatch.EMPTY;
        public int satelliteCount;
        public float hoverTicks;
        public float surgeProgress;
        public float channelProgress;
        public float activeBlend;
        public float ignitionProgress;
        public int surgeKind;
        public boolean hasCenter;
    }

    public EnchantmentFusionTableRenderer(BlockEntityRendererProvider.Context context) {}

    private ItemStack ensureProxy(@Nullable ItemStack cached, String modelId) {
        if (cached != null) {
            return cached;
        }
        ItemStack stack = new ItemStack(Items.AMETHYST_SHARD);
        stack.set(DataComponents.ITEM_MODEL, EnchantmentFusion.id(modelId));
        return stack;
    }

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
        state.channelProgress = Math.max(blockEntity.ritualProgress(),
            FusionRitualDirector.activeChannelProgressForTable(blockEntity.getBlockPos()));

        boolean nowActive = blockEntity.occupiedSlotCount() > 0 || blockEntity.surgeTicks > 0 || state.channelProgress > 0.0F;
        long posKey = blockEntity.getBlockPos().asLong();
        if (this.trackers.size() > 256) {
            this.trackers.clear();
        }
        RitualTracker tracker = this.trackers.computeIfAbsent(posKey, k -> new RitualTracker());
        if (tracker.lastSeenAnimTick != blockEntity.animTicks) {
            tracker.lastSeenAnimTick = blockEntity.animTicks;
            if (nowActive && !tracker.wasActive) {
                tracker.ignitionStartTick = blockEntity.animTicks;
                if (blockEntity.getLevel() != null) {
                    double sx = blockEntity.getBlockPos().getX() + 0.5D;
                    double sy = blockEntity.getBlockPos().getY() + 0.85D;
                    double sz = blockEntity.getBlockPos().getZ() + 0.5D;
                    blockEntity.getLevel().playLocalSound(
                        sx, sy, sz, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.32F, 1.18F, false
                    );
                    blockEntity.getLevel().playLocalSound(
                        sx, sy, sz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.45F, 0.92F, false
                    );
                }
            }
            tracker.wasActive = nowActive;
            float target = nowActive ? 1.0F : 0.0F;
            float delta = target - tracker.activeBlend;
            tracker.activeBlend = Mth.clamp(
                tracker.activeBlend + Mth.clamp(delta, -0.08F, 0.08F),
                0.0F,
                1.0F
            );
        }
        state.activeBlend = Math.max(tracker.activeBlend, state.channelProgress);
        float elapsedIgnite = state.hoverTicks - tracker.ignitionStartTick;
        state.ignitionProgress = (nowActive && elapsedIgnite >= 0.0F && elapsedIgnite <= IGNITION_DURATION_TICKS)
            ? (elapsedIgnite / IGNITION_DURATION_TICKS)
            : 0.0F;

        state.crystalCore.clear();
        state.crystalShards.clear();
        state.brazierFlames.clear();
        state.centerItem.clear();
        for (ItemStackRenderState sat : state.satellites) {
            sat.clear();
        }
        state.satelliteCount = 0;

        Minecraft mc = Minecraft.getInstance();
        int seedBase = (int) posKey;

        if (blockEntity.getLevel() != null) {
            this.crystalCoreProxy = ensureProxy(this.crystalCoreProxy, "fusion_crystal_core");
            this.crystalShardsProxy = ensureProxy(this.crystalShardsProxy, "fusion_crystal_shards");
            this.brazierFlamesProxy = ensureProxy(this.brazierFlamesProxy, "fusion_brazier_flames");

            mc.getItemModelResolver().updateForTopItem(
                state.crystalCore, this.crystalCoreProxy, ItemDisplayContext.FIXED, blockEntity.getLevel(), null, seedBase + 11
            );
            mc.getItemModelResolver().updateForTopItem(
                state.crystalShards, this.crystalShardsProxy, ItemDisplayContext.FIXED, blockEntity.getLevel(), null, seedBase + 12
            );
            if (state.activeBlend > 0.01F || state.ignitionProgress > 0.0F) {
                mc.getItemModelResolver().updateForTopItem(
                    state.brazierFlames, this.brazierFlamesProxy, ItemDisplayContext.FIXED, blockEntity.getLevel(), null, seedBase + 13
                );
            }
        }

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

        state.vfxBatch = FusionAltarVfx.extractBatch(
            blockEntity.getBlockPos(),
            state.hoverTicks,
            state.activeBlend,
            Math.max(state.ignitionProgress, state.channelProgress),
            cameraPos
        );
    }

    @Override
    public void submit(
        FusionTableRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState cameraState
    ) {
        float timeSec = state.hoverTicks / 20.0F;
        float surgeArc = Mth.sin(state.surgeProgress * Mth.PI);
        float ch = state.channelProgress;
        float act = Mth.clamp(state.activeBlend + surgeArc * 0.35F + ch * 0.5F, 0.0F, 1.0F);

        // 1. Central Floating Amethyst Crystal Cluster + Counter-Rotating Bottom Shards
        float idleBob = Mth.sin(timeSec * (Mth.TWO_PI / 4.0F)) * (0.72F / 16.0F);
        float activeBob = (1.05F + 0.42F * Mth.sin(timeSec * Mth.PI)) / 16.0F + surgeArc * 0.08F + ch * 0.085F;
        float crystalY = (22.0F / 16.0F) + Mth.lerp(act, idleBob, activeBob);

        float idleYaw = 11.0F * Mth.sin(timeSec * (Mth.TWO_PI / 4.0F) - 0.4F);
        float activeYaw = (timeSec * 180.0F + surgeArc * 180.0F + ch * ch * 540.0F) % 360.0F;
        float crystalYaw = Mth.lerp(act, idleYaw, activeYaw);
        float crystalTilt = Mth.lerp(act, 2.2F, 3.4F) * Mth.sin(timeSec * (2.0F + ch * 4.0F));

        if (!state.crystalCore.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5F, crystalY, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(crystalYaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(crystalTilt));
            state.crystalCore.submit(poseStack, collector, FULLBRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (!state.crystalShards.isEmpty()) {
            float shardY = (22.0F / 16.0F) + Mth.lerp(
                act,
                -idleBob * 0.65F,
                (0.85F - 0.35F * Mth.cos(timeSec * Mth.PI)) / 16.0F + ch * 0.05F
            );
            float shardYaw = Mth.lerp(act, -idleYaw * 1.6F, -(timeSec * 180.0F + ch * ch * 720.0F) % 360.0F);
            poseStack.pushPose();
            poseStack.translate(0.5F, shardY, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(shardYaw));
            state.crystalShards.submit(poseStack, collector, FULLBRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // 2. Four Corner Braziers: 45-Degree Crossed Cutout Purple Fire Cores + 1.52x Ritual Flare!
        if (!state.brazierFlames.isEmpty() && (state.activeBlend > 0.01F || state.ignitionProgress > 0.0F)) {
            float flameScaleY;
            if (ch > 0.0F) {
                flameScaleY = Mth.lerp(ch, 1.05F, 1.52F) + 0.09F * Mth.sin(timeSec * 16.0F);
            } else if (state.ignitionProgress > 0.0F) {
                float u = state.ignitionProgress;
                flameScaleY = (u < 0.35F)
                    ? Mth.lerp(u / 0.35F, 0.05F, 1.48F)
                    : Mth.lerp((u - 0.35F) / 0.65F, 1.48F, 1.0F);
            } else {
                flameScaleY = state.activeBlend * (1.0F + 0.07F * Mth.sin(timeSec * 9.0F));
            }
            poseStack.pushPose();
            poseStack.translate(0.5F, 12.85F / 16.0F, 0.5F);
            poseStack.scale(1.0F, Math.max(0.02F, flameScaleY), 1.0F);
            state.brazierFlames.submit(poseStack, collector, FULLBRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // 3. Orbiting Host Gear & Donor Books (spiral tightly inward from 0.56 -> 0.10 during 2.0s channeling!)
        int totalOrbiters = (state.hasCenter && !state.centerItem.isEmpty() ? 1 : 0) + state.satelliteCount;
        if (totalOrbiters > 0) {
            float inwardPull = 1.0F - 0.80F * (ch * ch) - 0.25F * surgeArc;
            float baseRadius = 0.56F * Math.max(0.14F, inwardPull);
            float baseAngle = state.hoverTicks * 0.065F + surgeArc * 3.2F + ch * ch * 5.2F;
            int slotIdx = 0;

            if (state.hasCenter && !state.centerItem.isEmpty()) {
                float phase = baseAngle;
                float ox = Mth.cos(phase) * baseRadius;
                float oz = Mth.sin(phase) * baseRadius;
                float oy = 1.24F + Mth.sin(state.hoverTicks * 0.08F) * 0.045F + surgeArc * 0.14F + ch * 0.14F;

                poseStack.pushPose();
                poseStack.translate(0.5F + ox, oy, 0.5F + oz);
                poseStack.mulPose(Axis.YP.rotationDegrees((-phase * Mth.RAD_TO_DEG + 90.0F) % 360.0F));
                if (state.surgeKind == 2 && surgeArc > 0.01F) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.hoverTicks * 0.9F) * 14.0F * surgeArc));
                }
                float centerScale = 0.46F + surgeArc * 0.08F;
                poseStack.scale(centerScale, centerScale, centerScale);
                state.centerItem.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
                slotIdx++;
            }

            for (int i = 0; i < state.satelliteCount; i++) {
                ItemStackRenderState sat = state.satellites[i];
                if (sat.isEmpty()) continue;

                float phase = baseAngle + (Mth.TWO_PI * slotIdx) / totalOrbiters;
                float ox = Mth.cos(phase) * baseRadius;
                float oz = Mth.sin(phase) * baseRadius;
                float oy = 1.16F + Mth.sin(state.hoverTicks * 0.11F + slotIdx * 2.1F) * 0.04F + surgeArc * 0.12F + ch * 0.18F;

                poseStack.pushPose();
                poseStack.translate(0.5F + ox, oy, 0.5F + oz);
                poseStack.mulPose(Axis.YP.rotationDegrees((-phase * Mth.RAD_TO_DEG + 90.0F) % 360.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(16.0F));
                poseStack.scale(0.34F, 0.34F, 0.34F);
                sat.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
                slotIdx++;
            }
        }

        // 4. Continuous Rising Gold/Cyan Ribbon Mesh & Brazier Flame/Spark Billboards
        FusionAltarVfx.submitBatch(poseStack, collector, state.vfxBatch);
    }
}
