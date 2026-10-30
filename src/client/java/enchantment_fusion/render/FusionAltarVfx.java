package enchantment_fusion.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import enchantment_fusion.EnchantmentFusion;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only 3D VFX choreographer for the Enchantment Fusion Table on Minecraft 26.1.2:
 * <ul>
 *   <li>Continuous, gap-free camera-aligned Gold &amp; Cyan quad-strip ribbons along the twin Catmull-Rom trajectories</li>
 *   <li>Drifting star motes detaching from both ribbons</li>
 *   <li>Four corner brazier smoldering idle embers, 0.75s ignition shockwave bursts, and rising purple flames</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class FusionAltarVfx {

    public static final Identifier RIBBON_TEXTURE =
        EnchantmentFusion.id("textures/particle/arcane_ribbon.png");
    public static final Identifier STREAM_TEXTURE =
        EnchantmentFusion.id("textures/particle/arcane_stream.png");
    public static final Identifier MOTE_TEXTURE =
        EnchantmentFusion.id("textures/particle/arcane_mote.png");
    public static final Identifier FLAME_TEXTURE =
        EnchantmentFusion.id("textures/particle/arcane_flame.png");

    public static final int FULLBRIGHT_LIGHT = 0xF000F0;

    private static final float[][] GOLD_TRAJECTORY = new float[][]{
        { 0.5484F, 0.9750F, 0.5880F},
        { 0.4580F, 1.0719F, 0.6657F},
        { 0.3508F, 1.1719F, 0.7267F},
        { 0.2937F, 1.3125F, 0.7223F},
        { 0.3232F, 1.4594F, 0.6370F},
        { 0.4602F, 1.5688F, 0.5972F},
        { 0.4050F, 1.6656F, 0.6657F},
        { 0.2657F, 1.7469F, 0.7386F},
        { 0.1751F, 1.8531F, 0.7851F},
        { 0.1818F, 1.9719F, 0.7519F}
    };

    private static final float[][] CYAN_TRAJECTORY = new float[][]{
        { 0.6436F, 0.8344F, 0.4713F},
        { 0.7086F, 0.8875F, 0.3904F},
        { 0.7267F, 0.9688F, 0.3572F},
        { 0.6768F, 1.0469F, 0.4514F},
        { 0.6335F, 1.1250F, 0.5186F},
        { 0.6865F, 1.1969F, 0.4302F},
        { 0.7373F, 1.2906F, 0.3175F},
        { 0.7166F, 1.4375F, 0.2817F},
        { 0.6215F, 1.5656F, 0.3299F},
        { 0.5278F, 1.6469F, 0.3997F}
    };

    private static final float[][] BRAZIER_CENTERS = new float[][]{
        { 2.75F / 16.0F, 13.05F / 16.0F,  2.75F / 16.0F},
        {13.25F / 16.0F, 13.05F / 16.0F,  2.75F / 16.0F},
        { 2.75F / 16.0F, 13.05F / 16.0F, 13.25F / 16.0F},
        {13.25F / 16.0F, 13.05F / 16.0F, 13.25F / 16.0F}
    };

    public enum SpriteSheet {
        MOTE,
        FLAME
    }

    public record BillboardQuad(
        SpriteSheet sheet,
        float lx, float ly, float lz,
        float rx, float ry, float rz,
        float ux, float uy, float uz,
        int argb
    ) {}

    /**
     * Connected camera-aligned quad segment between node {@code i} and node {@code i + 1} of a continuous 3D ribbon.
     * Adjacent segments share exact {@code (l1 <-> l0, r1 <-> r0)} edge coordinates and vertex colors so the ribbon
     * forms an unbroken silk band rather than discrete particles.
     */
    public record RibbonSegment(
        float l0x, float l0y, float l0z,
        float r0x, float r0y, float r0z,
        float l1x, float l1y, float l1z,
        float r1x, float r1y, float r1z,
        int argb0,
        int argb1
    ) {}

    public record AltarVfxBatch(
        List<RibbonSegment> ribbons,
        List<BillboardQuad> billboards
    ) {
        public static final AltarVfxBatch EMPTY = new AltarVfxBatch(List.of(), List.of());
    }

    private FusionAltarVfx() {}

    public static AltarVfxBatch extractBatch(
        BlockPos tablePos,
        float hoverTicks,
        float activeBlend,
        float ignitionProgress,
        Vec3 cameraPos
    ) {
        List<RibbonSegment> ribbons = new ArrayList<>(200);
        List<BillboardQuad> billboards = new ArrayList<>(80);

        float speedMul = 1.0F + activeBlend * 0.95F;
        float flowPhase = (hoverTicks * 0.024F * speedMul) % 1.0F;
        float orbitYawRad = activeBlend * ((hoverTicks * 0.045F) % Mth.TWO_PI);

        // 1. Continuous connected Gold & Cyan quad-strip ribbons along the preserved Catmull-Rom trajectories
        int ribbonNodes = 49;
        appendContinuousRibbon(ribbons, tablePos, cameraPos, GOLD_TRAJECTORY, true, ribbonNodes, flowPhase, orbitYawRad, activeBlend);
        appendContinuousRibbon(ribbons, tablePos, cameraPos, CYAN_TRAJECTORY, false, ribbonNodes, flowPhase, orbitYawRad, activeBlend);

        // 2. Subtle drifting star motes detaching upward from both ribbons
        int moteCount = 10;
        for (int i = 0; i < moteCount; i++) {
            boolean isGold = (i % 2 == 0);
            float seed = i * 0.6180339F;
            float lifeU = ((hoverTicks * 0.028F * speedMul) + seed) % 1.0F;
            float spawnT = 0.18F + ((i * 0.273F) % 0.72F);
            float[] base = sampleCatmullRom(isGold ? GOLD_TRAJECTORY : CYAN_TRAJECTORY, spawnT, orbitYawRad);
            float jx = Mth.sin(i * 4.1F) * 0.065F * (0.4F + lifeU);
            float jz = Mth.cos(i * 3.7F) * 0.065F * (0.4F + lifeU);
            float ly = base[1] + lifeU * (0.14F + (i % 3) * 0.045F);
            float alpha = (1.0F - lifeU * lifeU) * 0.85F;
            float halfSize = 0.024F * (1.0F - 0.35F * lifeU);
            int argb = isGold
                ? packArgb(alpha, 1.0F, 0.86F, 0.38F)
                : packArgb(alpha, 0.45F, 0.94F, 1.0F);
            addBillboard(billboards, SpriteSheet.MOTE, tablePos, cameraPos, base[0] + jx, ly, base[2] + jz, halfSize, argb);
        }

        // 3. Four Corner Braziers: Idle Smoldering Embers vs Ignition Burst + Active Purple Flames
        if (activeBlend < 0.08F && ignitionProgress <= 0.0F) {
            for (int b = 0; b < 4; b++) {
                float u = ((hoverTicks * 0.018F) + b * 0.27F) % 1.0F;
                if (u < 0.55F) {
                    float un = u / 0.55F;
                    float[] bc = BRAZIER_CENTERS[b];
                    float lx = bc[0] + Mth.sin(un * 5.0F + b) * 0.015F;
                    float ly = bc[1] + un * 0.11F;
                    float lz = bc[2] + Mth.cos(un * 5.0F + b) * 0.015F;
                    int argb = packArgb((1.0F - un) * 0.80F, 0.82F, 0.38F, 1.0F);
                    addBillboard(billboards, SpriteSheet.MOTE, tablePos, cameraPos, lx, ly, lz, 0.018F, argb);
                }
            }
        } else {
            for (int b = 0; b < 4; b++) {
                float[] bc = BRAZIER_CENTERS[b];
                for (int k = 0; k < 5; k++) {
                    float u = ((hoverTicks * 0.048F) + b * 0.19F + k * 0.21F) % 1.0F;
                    float spread = 0.032F * (0.4F + 0.6F * u);
                    float lx = bc[0] + Mth.sin(k * 2.5F + b) * spread;
                    float ly = bc[1] + 0.025F + u * 0.24F * activeBlend;
                    float lz = bc[2] + Mth.cos(k * 2.9F + b) * spread;
                    boolean spark = (k == 4);
                    float halfSize = (spark ? 0.020F : 0.052F * (1.0F - 0.45F * u)) * activeBlend;
                    float r = Mth.lerp(u, 1.00F, 0.58F);
                    float g = Mth.lerp(u, 0.78F, 0.18F);
                    float bl = Mth.lerp(u, 1.00F, 0.88F);
                    int argb = packArgb((1.0F - u) * 0.88F * activeBlend, r, g, bl);
                    addBillboard(billboards, spark ? SpriteSheet.MOTE : SpriteSheet.FLAME, tablePos, cameraPos, lx, ly, lz, halfSize, argb);
                }
            }

            if (ignitionProgress > 0.0F && ignitionProgress < 1.0F) {
                float ease = 1.0F - (1.0F - ignitionProgress) * (1.0F - ignitionProgress);
                float fade = (1.0F - ignitionProgress);
                for (int b = 0; b < 4; b++) {
                    float[] bc = BRAZIER_CENTERS[b];
                    for (int s = 0; s < 7; s++) {
                        float ang = s * (Mth.TWO_PI / 7.0F) + b * 0.6F;
                        float rad = (0.04F + (s % 3) * 0.035F) * ease;
                        float rise = (0.14F + (s % 4) * 0.085F) * ease;
                        boolean isSpark = (s % 2 == 1);
                        float halfSize = isSpark ? 0.026F * fade : 0.068F * (1.0F - 0.5F * ignitionProgress);
                        int argb = packArgb(fade * 0.95F, 1.0F, 0.72F, 1.0F);
                        addBillboard(
                            billboards,
                            isSpark ? SpriteSheet.MOTE : SpriteSheet.FLAME,
                            tablePos,
                            cameraPos,
                            bc[0] + Mth.cos(ang) * rad,
                            bc[1] + rise,
                            bc[2] + Mth.sin(ang) * rad,
                            halfSize,
                            argb
                        );
                    }
                }
            }
        }

        return new AltarVfxBatch(ribbons, billboards);
    }

    private static void appendContinuousRibbon(
        List<RibbonSegment> out,
        BlockPos tablePos,
        Vec3 cameraPos,
        float[][] pts,
        boolean isGold,
        int nodeCount,
        float flowPhase,
        float yawRad,
        float activeBlend
    ) {
        float camLocalX = (float) (cameraPos.x - tablePos.getX());
        float camLocalY = (float) (cameraPos.y - tablePos.getY());
        float camLocalZ = (float) (cameraPos.z - tablePos.getZ());

        float baseHalf = (isGold ? 0.046F : 0.042F) * (0.92F + 0.24F * activeBlend);
        float[][] pList = new float[nodeCount][3];
        float[][] lOuter = new float[nodeCount][3];
        float[][] rOuter = new float[nodeCount][3];
        float[][] lCore = new float[nodeCount][3];
        float[][] rCore = new float[nodeCount][3];
        int[] outerColors = new int[nodeCount];
        int[] coreColors = new int[nodeCount];

        for (int i = 0; i < nodeCount; i++) {
            float t = i / (float) (nodeCount - 1);
            pList[i] = sampleCatmullRom(pts, t, yawRad);
        }

        float prevSx = 0.0F, prevSy = 0.0F, prevSz = 0.0F;
        for (int i = 0; i < nodeCount; i++) {
            float t = i / (float) (nodeCount - 1);
            float[] p = pList[i];
            float[] pPrev = pList[Math.max(0, i - 1)];
            float[] pNext = pList[Math.min(nodeCount - 1, i + 1)];

            float tx = pNext[0] - pPrev[0];
            float ty = pNext[1] - pPrev[1];
            float tz = pNext[2] - pPrev[2];
            float tLen = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            if (tLen < 1.0E-5F) {
                tx = 0.0F; ty = 1.0F; tz = 0.0F;
            } else {
                tx /= tLen; ty /= tLen; tz /= tLen;
            }

            float vx = camLocalX - p[0];
            float vy = camLocalY - p[1];
            float vz = camLocalZ - p[2];
            float vLen = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            if (vLen < 1.0E-4F) {
                vx = 0.0F; vy = 0.0F; vz = 1.0F;
            } else {
                vx /= vLen; vy /= vLen; vz /= vLen;
            }

            // Cross-section wing perpendicular to both ribbon tangent and view ray
            float sx = ty * vz - tz * vy;
            float sy = tz * vx - tx * vz;
            float sz = tx * vy - ty * vx;
            float sLen = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sLen < 1.0E-4F) {
                // Fallback when looking nearly parallel to tangent
                sx = -tz; sy = 0.0F; sz = tx;
                sLen = Mth.sqrt(sx * sx + sz * sz);
                if (sLen < 1.0E-4F) {
                    sx = 1.0F; sy = 0.0F; sz = 0.0F;
                } else {
                    sx /= sLen; sz /= sLen;
                }
            } else {
                sx /= sLen; sy /= sLen; sz /= sLen;
            }

            // Preserve smooth ribbon surface orientation so consecutive nodes never twist 180 deg
            if (i > 0 && (sx * prevSx + sy * prevSy + sz * prevSz) < 0.0F) {
                sx = -sx; sy = -sy; sz = -sz;
            }
            prevSx = sx; prevSy = sy; prevSz = sz;

            float env = (float) Math.pow(Math.max(0.0F, Mth.sin(t * Mth.PI)), 0.58D);
            float wave = 0.88F + 0.16F * Mth.sin(t * 10.0F - flowPhase * Mth.TWO_PI * 2.0F);
            float halfW = baseHalf * (0.16F + 0.92F * env) * wave;
            float coreW = halfW * 0.38F;

            lOuter[i][0] = p[0] - sx * halfW;
            lOuter[i][1] = p[1] - sy * halfW;
            lOuter[i][2] = p[2] - sz * halfW;
            rOuter[i][0] = p[0] + sx * halfW;
            rOuter[i][1] = p[1] + sy * halfW;
            rOuter[i][2] = p[2] + sz * halfW;

            lCore[i][0] = p[0] - sx * coreW;
            lCore[i][1] = p[1] - sy * coreW;
            lCore[i][2] = p[2] - sz * coreW;
            rCore[i][0] = p[0] + sx * coreW;
            rCore[i][1] = p[1] + sy * coreW;
            rCore[i][2] = p[2] + sz * coreW;

            float fade = t < 0.09F
                ? (t / 0.09F)
                : (t > 0.84F ? Math.max(0.0F, 1.0F - (t - 0.84F) / 0.16F) : 1.0F);
            float crest = 0.80F + 0.20F * Mth.sin(t * 8.0F - flowPhase * Mth.TWO_PI * 3.0F);
            float alpha = fade * (0.76F + 0.18F * activeBlend) * crest;

            if (isGold) {
                float r = 1.00F;
                float g = Mth.lerp(t, 0.90F, 0.72F);
                float b = Mth.lerp(t, 0.48F, 0.16F);
                outerColors[i] = packArgb(alpha, r, g, b);
                coreColors[i] = packArgb(Math.min(1.0F, alpha * 1.12F), 1.00F, 0.97F, 0.80F);
            } else {
                float r = Mth.lerp(t, 0.56F, 0.14F);
                float g = Mth.lerp(t, 0.96F, 0.82F);
                float b = 1.00F;
                outerColors[i] = packArgb(alpha, r, g, b);
                coreColors[i] = packArgb(Math.min(1.0F, alpha * 1.12F), 0.84F, 0.99F, 1.00F);
            }
        }

        for (int i = 0; i < nodeCount - 1; i++) {
            emitCameraFacingSegment(out, lOuter[i], rOuter[i], lOuter[i + 1], rOuter[i + 1], outerColors[i], outerColors[i + 1], camLocalX, camLocalY, camLocalZ);
            emitCameraFacingSegment(out, lCore[i], rCore[i], lCore[i + 1], rCore[i + 1], coreColors[i], coreColors[i + 1], camLocalX, camLocalY, camLocalZ);
        }
    }

    private static void emitCameraFacingSegment(
        List<RibbonSegment> out,
        float[] l0, float[] r0,
        float[] l1, float[] r1,
        int argb0, int argb1,
        float camX, float camY, float camZ
    ) {
        if (((argb0 >>> 24) & 0xFF) <= 3 && ((argb1 >>> 24) & 0xFF) <= 3) {
            return;
        }
        // Check quad geometric normal vs camera ray so front-face winding is always guaranteed without double-blending
        float ex = r0[0] - l0[0];
        float ey = r0[1] - l0[1];
        float ez = r0[2] - l0[2];
        float fx = l1[0] - l0[0];
        float fy = l1[1] - l0[1];
        float fz = l1[2] - l0[2];
        float nx = ey * fz - ez * fy;
        float ny = ez * fx - ex * fz;
        float nz = ex * fy - ey * fx;
        float mx = 0.5F * (l0[0] + r1[0]);
        float my = 0.5F * (l0[1] + r1[1]);
        float mz = 0.5F * (l0[2] + r1[2]);
        float dot = nx * (camX - mx) + ny * (camY - my) + nz * (camZ - mz);
        if (dot >= 0.0F) {
            out.add(new RibbonSegment(
                l0[0], l0[1], l0[2], r0[0], r0[1], r0[2],
                l1[0], l1[1], l1[2], r1[0], r1[1], r1[2],
                argb0, argb1
            ));
        } else {
            out.add(new RibbonSegment(
                r0[0], r0[1], r0[2], l0[0], l0[1], l0[2],
                r1[0], r1[1], r1[2], l1[0], l1[1], l1[2],
                argb0, argb1
            ));
        }
    }

    private static float[] sampleCatmullRom(float[][] pts, float t, float yawRad) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float scaled = clamped * (pts.length - 1);
        int i1 = Math.min((int) Math.floor(scaled), pts.length - 2);
        float u = scaled - i1;
        float[] p0 = pts[Math.max(0, i1 - 1)];
        float[] p1 = pts[i1];
        float[] p2 = pts[i1 + 1];
        float[] p3 = pts[Math.min(pts.length - 1, i1 + 2)];
        float u2 = u * u;
        float u3 = u2 * u;

        float x = 0.5F * ((2.0F * p1[0]) + (-p0[0] + p2[0]) * u
            + (2.0F * p0[0] - 5.0F * p1[0] + 4.0F * p2[0] - p3[0]) * u2
            + (-p0[0] + 3.0F * p1[0] - 3.0F * p2[0] + p3[0]) * u3);
        float y = 0.5F * ((2.0F * p1[1]) + (-p0[1] + p2[1]) * u
            + (2.0F * p0[1] - 5.0F * p1[1] + 4.0F * p2[1] - p3[1]) * u2
            + (-p0[1] + 3.0F * p1[1] - 3.0F * p2[1] + p3[1]) * u3);
        float z = 0.5F * ((2.0F * p1[2]) + (-p0[2] + p2[2]) * u
            + (2.0F * p0[2] - 5.0F * p1[2] + 4.0F * p2[2] - p3[2]) * u2
            + (-p0[2] + 3.0F * p1[2] - 3.0F * p2[2] + p3[2]) * u3);

        if (Math.abs(yawRad) > 1.0E-4F) {
            float dx = x - 0.5F;
            float dz = z - 0.5F;
            float c = Mth.cos(yawRad);
            float s = Mth.sin(yawRad);
            x = 0.5F + dx * c - dz * s;
            z = 0.5F + dx * s + dz * c;
        }
        return new float[]{x, y, z};
    }

    private static void addBillboard(
        List<BillboardQuad> out,
        SpriteSheet sheet,
        BlockPos tablePos,
        Vec3 cameraPos,
        float lx, float ly, float lz,
        float halfSize,
        int argb
    ) {
        if (halfSize <= 0.002F || ((argb >>> 24) & 0xFF) <= 4) {
            return;
        }
        double wx = tablePos.getX() + lx;
        double wy = tablePos.getY() + ly;
        double wz = tablePos.getZ() + lz;
        double fx = cameraPos.x - wx;
        double fy = cameraPos.y - wy;
        double fz = cameraPos.z - wz;
        double flen = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (flen < 1.0E-4D) {
            fx = 0.0D; fy = 0.0D; fz = 1.0D;
        } else {
            fx /= flen; fy /= flen; fz /= flen;
        }
        double rx = fz;
        double ry = 0.0D;
        double rz = -fx;
        double rlen = Math.sqrt(rx * rx + rz * rz);
        if (rlen < 1.0E-4D) {
            rx = 1.0D; rz = 0.0D;
        } else {
            rx /= rlen; rz /= rlen;
        }
        double ux = fy * rz - fz * ry;
        double uy = fz * rx - fx * rz;
        double uz = fx * ry - fy * rx;

        out.add(new BillboardQuad(
            sheet,
            lx, ly, lz,
            (float) (rx * halfSize), (float) (ry * halfSize), (float) (rz * halfSize),
            (float) (ux * halfSize), (float) (uy * halfSize), (float) (uz * halfSize),
            argb
        ));
    }

    private static int packArgb(float a, float r, float g, float b) {
        int ai = Mth.clamp(Math.round(a * 255.0F), 0, 255);
        int ri = Mth.clamp(Math.round(r * 255.0F), 0, 255);
        int gi = Mth.clamp(Math.round(g * 255.0F), 0, 255);
        int bi = Mth.clamp(Math.round(b * 255.0F), 0, 255);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public static void submitBatch(PoseStack poseStack, SubmitNodeCollector collector, AltarVfxBatch batch) {
        if (batch == null) {
            return;
        }
        List<RibbonSegment> ribbons = batch.ribbons();
        if (!ribbons.isEmpty()) {
            collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(RIBBON_TEXTURE),
                (pose, vc) -> {
                    for (RibbonSegment seg : ribbons) {
                        vc.addVertex(pose, seg.l0x, seg.l0y, seg.l0z)
                            .setColor(seg.argb0).setUv(0.25F, 0.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
                        vc.addVertex(pose, seg.r0x, seg.r0y, seg.r0z)
                            .setColor(seg.argb0).setUv(0.25F, 1.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
                        vc.addVertex(pose, seg.r1x, seg.r1y, seg.r1z)
                            .setColor(seg.argb1).setUv(0.75F, 1.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
                        vc.addVertex(pose, seg.l1x, seg.l1y, seg.l1z)
                            .setColor(seg.argb1).setUv(0.75F, 0.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
                    }
                }
            );
        }

        List<BillboardQuad> quads = batch.billboards();
        if (!quads.isEmpty()) {
            submitSheet(poseStack, collector, quads, SpriteSheet.FLAME, FLAME_TEXTURE);
            submitSheet(poseStack, collector, quads, SpriteSheet.MOTE, MOTE_TEXTURE);
        }
    }

    private static void submitSheet(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        List<BillboardQuad> quads,
        SpriteSheet targetSheet,
        Identifier texture
    ) {
        boolean any = false;
        for (BillboardQuad q : quads) {
            if (q.sheet == targetSheet) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        collector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucent(texture),
            (pose, vc) -> {
                for (BillboardQuad q : quads) {
                    if (q.sheet == targetSheet) {
                        emitQuad(pose, vc, q);
                    }
                }
            }
        );
    }

    private static void emitQuad(PoseStack.Pose pose, VertexConsumer vc, BillboardQuad q) {
        vc.addVertex(pose, q.lx - q.rx - q.ux, q.ly - q.ry - q.uy, q.lz - q.rz - q.uz)
            .setColor(q.argb).setUv(0.0F, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vc.addVertex(pose, q.lx + q.rx - q.ux, q.ly + q.ry - q.uy, q.lz + q.rz - q.uz)
            .setColor(q.argb).setUv(1.0F, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vc.addVertex(pose, q.lx + q.rx + q.ux, q.ly + q.ry + q.uy, q.lz + q.rz + q.uz)
            .setColor(q.argb).setUv(1.0F, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vc.addVertex(pose, q.lx - q.rx + q.ux, q.ly - q.ry + q.uy, q.lz - q.rz + q.uz)
            .setColor(q.argb).setUv(0.0F, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
