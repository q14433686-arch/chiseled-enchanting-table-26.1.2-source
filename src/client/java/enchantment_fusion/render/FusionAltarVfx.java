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
 * Client-only 3D billboard VFX choreographer for the Enchantment Fusion Table on Minecraft 26.1.2:
 * <ul>
 *   <li>Bottom-to-top golden and cyan swirling trajectory energy streams + drifting star motes</li>
 *   <li>Four corner brazier smoldering idle embers, 0.75s ignition shockwave bursts, and rising purple flames</li>
 * </ul>
 * Uses {@link SubmitNodeCollector#submitCustomGeometry} with {@code FULLBRIGHT_LIGHT} so shader packs
 * automatically apply per-color HDR bloom without registering server-side particle IDs.
 */
@Environment(EnvType.CLIENT)
public final class FusionAltarVfx {

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
        STREAM,
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

    private FusionAltarVfx() {}

    public static List<BillboardQuad> extractQuads(
        BlockPos tablePos,
        float hoverTicks,
        float activeBlend,
        float ignitionProgress,
        Vec3 cameraPos
    ) {
        List<BillboardQuad> out = new ArrayList<>(140);
        float speedMul = 1.0F + activeBlend * 0.95F;
        float flowPhase = (hoverTicks * 0.022F * speedMul) % 1.0F;
        float orbitYawRad = activeBlend * ((hoverTicks * 0.045F) % Mth.TWO_PI);

        // 1. Bottom-to-top Gold & Cyan Ribbon Stream Billboards
        int streamSteps = activeBlend > 0.25F ? 36 : 26;
        appendTrajectoryStream(out, tablePos, cameraPos, GOLD_TRAJECTORY, true, streamSteps, flowPhase, orbitYawRad, activeBlend);
        appendTrajectoryStream(out, tablePos, cameraPos, CYAN_TRAJECTORY, false, streamSteps, flowPhase, orbitYawRad, activeBlend);

        // 2. Drifting Star Motes detaching upward from both ribbons
        int moteCount = 14;
        for (int i = 0; i < moteCount; i++) {
            boolean isGold = (i % 2 == 0);
            float seed = i * 0.6180339F;
            float lifeU = ((hoverTicks * 0.028F * speedMul) + seed) % 1.0F;
            float spawnT = 0.18F + ((i * 0.273F) % 0.72F);
            float[] base = sampleCatmullRom(isGold ? GOLD_TRAJECTORY : CYAN_TRAJECTORY, spawnT, orbitYawRad);
            float jx = Mth.sin(i * 4.1F) * 0.07F * (0.4F + lifeU);
            float jz = Mth.cos(i * 3.7F) * 0.07F * (0.4F + lifeU);
            float ly = base[1] + lifeU * (0.14F + (i % 3) * 0.05F);
            float alpha = (1.0F - lifeU * lifeU) * 0.90F;
            float halfSize = 0.028F * (1.0F - 0.35F * lifeU);
            int argb = isGold
                ? packArgb(alpha, 1.0F, 0.86F, 0.38F)
                : packArgb(alpha, 0.45F, 0.94F, 1.0F);
            addBillboard(out, SpriteSheet.MOTE, tablePos, cameraPos, base[0] + jx, ly, base[2] + jz, halfSize, argb);
        }

        // 3. Four Corner Braziers: Idle Smoldering Embers vs Ignition Burst + Active Purple Flames
        if (activeBlend < 0.08F && ignitionProgress <= 0.0F) {
            // Idle: subtle occasional smoldering purple ember sparks above the dark coal beds
            for (int b = 0; b < 4; b++) {
                float u = ((hoverTicks * 0.018F) + b * 0.27F) % 1.0F;
                if (u < 0.55F) {
                    float un = u / 0.55F;
                    float[] bc = BRAZIER_CENTERS[b];
                    float lx = bc[0] + Mth.sin(un * 5.0F + b) * 0.015F;
                    float ly = bc[1] + un * 0.11F;
                    float lz = bc[2] + Mth.cos(un * 5.0F + b) * 0.015F;
                    int argb = packArgb((1.0F - un) * 0.80F, 0.82F, 0.38F, 1.0F);
                    addBillboard(out, SpriteSheet.MOTE, tablePos, cameraPos, lx, ly, lz, 0.018F, argb);
                }
            }
        } else {
            // Active rising purple-pink flame wisps & sparks around the 45-degree crossed fire cores
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
                    addBillboard(out, spark ? SpriteSheet.MOTE : SpriteSheet.FLAME, tablePos, cameraPos, lx, ly, lz, halfSize, argb);
                }
            }

            // 0.75s Ignition Shockwave Burst ("轰！" eruption when table activates!)
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
                            out,
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

        return out;
    }

    private static void appendTrajectoryStream(
        List<BillboardQuad> out,
        BlockPos tablePos,
        Vec3 cameraPos,
        float[][] pts,
        boolean isGold,
        int steps,
        float flowPhase,
        float yawRad,
        float activeBlend
    ) {
        float baseHalf = (isGold ? 0.066F : 0.058F) * (0.90F + 0.16F * activeBlend);
        for (int i = 0; i < steps; i++) {
            float u = ((i / (float) steps) + flowPhase) % 1.0F;
            float[] pos = sampleCatmullRom(pts, u, yawRad);
            float env = Mth.sin(u * Mth.PI);
            float pulse = 0.78F + 0.26F * Mth.sin(u * 16.0F - flowPhase * Mth.TWO_PI * 3.0F);
            float halfSize = baseHalf * (0.32F + 0.82F * env) * pulse;
            float alpha = u < 0.10F
                ? (u / 0.10F)
                : (u > 0.74F ? Math.max(0.0F, 1.0F - (u - 0.74F) / 0.26F) : 0.88F);
            int argb;
            if (isGold) {
                float r = 1.00F;
                float g = Mth.lerp(u, 0.94F, 0.74F);
                float b = Mth.lerp(u, 0.62F, 0.18F);
                argb = packArgb(alpha * 0.85F, r, g, b);
            } else {
                float r = Mth.lerp(u, 0.72F, 0.16F);
                float g = Mth.lerp(u, 0.97F, 0.82F);
                float b = 1.00F;
                argb = packArgb(alpha * 0.85F, r, g, b);
            }
            addBillboard(out, SpriteSheet.STREAM, tablePos, cameraPos, pos[0], pos[1], pos[2], halfSize, argb);
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

    public static void submitQuads(PoseStack poseStack, SubmitNodeCollector collector, List<BillboardQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return;
        }
        submitSheet(poseStack, collector, quads, SpriteSheet.STREAM, STREAM_TEXTURE);
        submitSheet(poseStack, collector, quads, SpriteSheet.FLAME, FLAME_TEXTURE);
        submitSheet(poseStack, collector, quads, SpriteSheet.MOTE, MOTE_TEXTURE);
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
