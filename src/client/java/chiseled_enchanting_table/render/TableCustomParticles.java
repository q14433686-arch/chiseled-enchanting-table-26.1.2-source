package chiseled_enchanting_table.render;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.cosmetic.RainbowCloth;
import chiseled_enchanting_table.mechanics.resonance.TomeParticleEffects;
import chiseled_enchanting_table.menu.TableVisualEventPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side 3D pixel-art billboard particle choreographer anchored to Chiseled Enchanting Tables.
 *
 * <p>Renders two dedicated {@code 32x32} custom sprites ({@code scorched_page.png} and
 * {@code arcane_rune_spark.png}) via {@link SubmitNodeCollector#submitCustomGeometry} without
 * mutating vanilla {@code BuiltInRegistries.PARTICLE_TYPE} IDs.</p>
 */
public final class TableCustomParticles {

    public static final Identifier SCORCHED_PAGE_TEXTURE =
        ChiseledEnchantingTable.identifier("textures/particle/scorched_page.png");
    public static final Identifier ARCANE_RUNE_SPARK_TEXTURE =
        ChiseledEnchantingTable.identifier("textures/particle/arcane_rune_spark.png");

    private static final int MAX_PARTICLES = 320;
    private static final int MAX_EMITTERS = 64;
    private static final int FULLBRIGHT_LIGHT = 0xF000F0;

    public enum SpriteKind {
        SCORCHED_PAGE,
        ARCANE_RUNE_SPARK
    }

    public record QuadSnapshot(
        SpriteKind sprite,
        float lx,
        float ly,
        float lz,
        float rx,
        float ry,
        float rz,
        float ux,
        float uy,
        float uz,
        int argb
    ) {}

    private static final class Particle {
        final BlockPos anchorTable;
        final SpriteKind sprite;
        double ox, oy, oz;
        double x, y, z;
        double vx, vy, vz;
        final boolean curvedPath;
        final double startX, startY, startZ;
        final double endX, endY, endZ;
        final double arcHeight;
        int age;
        final int maxAge;
        final float baseSize;
        float oRoll;
        float roll;
        final float rollVel;
        final float gravity;
        final int argb;

        Particle(
            BlockPos anchorTable,
            SpriteKind sprite,
            double x, double y, double z,
            double vx, double vy, double vz,
            int maxAge,
            float baseSize,
            float roll,
            float rollVel,
            float gravity,
            int argb
        ) {
            this.anchorTable = anchorTable.immutable();
            this.sprite = sprite;
            this.ox = this.x = x;
            this.oy = this.y = y;
            this.oz = this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.curvedPath = false;
            this.startX = this.startY = this.startZ = 0.0D;
            this.endX = this.endY = this.endZ = 0.0D;
            this.arcHeight = 0.0D;
            this.age = 0;
            this.maxAge = Math.max(4, maxAge);
            this.baseSize = baseSize;
            this.oRoll = this.roll = roll;
            this.rollVel = rollVel;
            this.gravity = gravity;
            this.argb = argb | 0xFF000000;
        }

        Particle(
            BlockPos anchorTable,
            SpriteKind sprite,
            double sx, double sy, double sz,
            double ex, double ey, double ez,
            double arcHeight,
            int delayAgeOffset,
            int maxAge,
            float baseSize,
            float rollVel,
            int argb
        ) {
            this.anchorTable = anchorTable.immutable();
            this.sprite = sprite;
            this.curvedPath = true;
            this.startX = sx;
            this.startY = sy;
            this.startZ = sz;
            this.endX = ex;
            this.endY = ey;
            this.endZ = ez;
            this.arcHeight = arcHeight;
            this.age = -Math.max(0, delayAgeOffset);
            this.maxAge = Math.max(6, maxAge);
            this.ox = this.x = sx;
            this.oy = this.y = sy;
            this.oz = this.z = sz;
            this.baseSize = baseSize;
            this.oRoll = this.roll = 0.0F;
            this.rollVel = rollVel;
            this.gravity = 0.0F;
            this.argb = argb | 0xFF000000;
        }
    }

    private static final class BacklashSlotEmitter {
        final BlockPos anchorTable;
        final double sx, sy, sz;
        final boolean locateBeacon;
        int ticksRemaining;

        BacklashSlotEmitter(BlockPos anchorTable, double sx, double sy, double sz, int ticksRemaining, boolean locateBeacon) {
            this.anchorTable = anchorTable.immutable();
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.locateBeacon = locateBeacon;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final Deque<Particle> PARTICLES = new ArrayDeque<>();
    private static final Deque<BacklashSlotEmitter> EMITTERS = new ArrayDeque<>();

    private TableCustomParticles() {}

    public static synchronized void clear() {
        PARTICLES.clear();
        EMITTERS.clear();
    }

    public static synchronized int activeParticleCount() {
        return PARTICLES.size();
    }

    public static synchronized int activeEmitterCount() {
        return EMITTERS.size();
    }

    private static void push(Particle p) {
        while (PARTICLES.size() >= MAX_PARTICLES) {
            PARTICLES.pollFirst();
        }
        PARTICLES.addLast(p);
    }

    public static synchronized void clientTick() {
        if (!EMITTERS.isEmpty()) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Iterator<BacklashSlotEmitter> eit = EMITTERS.iterator();
            while (eit.hasNext()) {
                BacklashSlotEmitter em = eit.next();
                if (em.ticksRemaining % 6 == 0) {
                    if (em.locateBeacon) {
                        push(new Particle(
                            em.anchorTable,
                            SpriteKind.ARCANE_RUNE_SPARK,
                            em.sx + (rnd.nextDouble() - 0.5D) * 0.11D,
                            em.sy + (rnd.nextDouble() - 0.5D) * 0.10D,
                            em.sz + (rnd.nextDouble() - 0.5D) * 0.11D,
                            (rnd.nextDouble() - 0.5D) * 0.010D,
                            0.012D + rnd.nextDouble() * 0.014D,
                            (rnd.nextDouble() - 0.5D) * 0.010D,
                            20 + rnd.nextInt(8),
                            0.078F,
                            0.0F,
                            0.18F,
                            -0.0004F,
                            rnd.nextBoolean() ? 0xFF74F3FF : 0xFFFFE48F
                        ));
                    } else {
                        push(new Particle(
                            em.anchorTable,
                            SpriteKind.SCORCHED_PAGE,
                            em.sx + (rnd.nextDouble() - 0.5D) * 0.16D,
                            em.sy + (rnd.nextDouble() - 0.5D) * 0.14D,
                            em.sz + (rnd.nextDouble() - 0.5D) * 0.16D,
                            (rnd.nextDouble() - 0.5D) * 0.018D,
                            0.008D + rnd.nextDouble() * 0.012D,
                            (rnd.nextDouble() - 0.5D) * 0.018D,
                            26 + rnd.nextInt(10),
                            0.095F,
                            (float) (rnd.nextDouble() * Math.PI * 2.0D),
                            (rnd.nextFloat() - 0.5F) * 0.22F,
                            0.0022F,
                            0xFFFFFFFF
                        ));
                        push(new Particle(
                            em.anchorTable,
                            SpriteKind.ARCANE_RUNE_SPARK,
                            em.sx + (rnd.nextDouble() - 0.5D) * 0.14D,
                            em.sy + (rnd.nextDouble() - 0.5D) * 0.12D,
                            em.sz + (rnd.nextDouble() - 0.5D) * 0.14D,
                            (rnd.nextDouble() - 0.5D) * 0.014D,
                            0.018D + rnd.nextDouble() * 0.016D,
                            (rnd.nextDouble() - 0.5D) * 0.014D,
                            18 + rnd.nextInt(8),
                            0.068F,
                            0.0F,
                            0.16F,
                            -0.0005F,
                            rnd.nextBoolean() ? 0xFFFF4858 : 0xFFFF9E44
                        ));
                    }
                }
                if (--em.ticksRemaining <= 0) {
                    eit.remove();
                }
            }
        }
        if (PARTICLES.isEmpty()) {
            return;
        }
        Iterator<Particle> it = PARTICLES.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.ox = p.x;
            p.oy = p.y;
            p.oz = p.z;
            p.oRoll = p.roll;
            p.age++;
            if (p.age < 0) {
                continue;
            }
            if (p.age >= p.maxAge) {
                it.remove();
                continue;
            }
            p.roll += p.rollVel;
            if (p.curvedPath) {
                double t = (double) p.age / (double) p.maxAge;
                double smooth = t * t * (3.0D - 2.0D * t);
                p.x = Mth.lerp(smooth, p.startX, p.endX);
                p.y = Mth.lerp(smooth, p.startY, p.endY) + Math.sin(t * Math.PI) * p.arcHeight;
                p.z = Mth.lerp(smooth, p.startZ, p.endZ);
            } else {
                p.vy -= p.gravity;
                p.x += p.vx;
                p.y += p.vy;
                p.z += p.vz;
                p.vx *= 0.94D;
                p.vz *= 0.94D;
            }
        }
    }

    public static synchronized void handleVisualEvent(TableVisualEventPayload payload) {
        if (payload == null || payload.tablePos() == null) {
            return;
        }
        BlockPos tablePos = payload.tablePos();
        double tx = tablePos.getX() + 0.5D;
        double ty = tablePos.getY() + 1.08D;
        double tz = tablePos.getZ() + 0.5D;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        switch (payload.kind()) {
            case RUSH_BACKLASH -> {
                double[] slotCoords = TomeParticleEffects.slotWorldCoordinates(
                    payload.targetPos(), payload.shelfFacingOrSouth(), payload.slot()
                );
                for (int i = 0; i < 12; i++) {
                    push(new Particle(
                        tablePos,
                        SpriteKind.ARCANE_RUNE_SPARK,
                        tx, ty, tz,
                        slotCoords[0], slotCoords[1], slotCoords[2],
                        0.22D,
                        i,
                        12,
                        0.085F,
                        0.28F,
                        (i % 3 == 0) ? 0xFFFF9E44 : 0xFFFF3B52
                    ));
                }
                for (int i = 0; i < 6; i++) {
                    push(new Particle(
                        tablePos,
                        SpriteKind.SCORCHED_PAGE,
                        slotCoords[0] + (rnd.nextDouble() - 0.5D) * 0.18D,
                        slotCoords[1] + (rnd.nextDouble() - 0.5D) * 0.16D,
                        slotCoords[2] + (rnd.nextDouble() - 0.5D) * 0.18D,
                        (rnd.nextDouble() - 0.5D) * 0.04D,
                        0.025D + rnd.nextDouble() * 0.025D,
                        (rnd.nextDouble() - 0.5D) * 0.04D,
                        28 + rnd.nextInt(12),
                        0.105F,
                        (float) (rnd.nextDouble() * Math.PI * 2.0D),
                        (rnd.nextFloat() - 0.5F) * 0.26F,
                        0.0024F,
                        0xFFFFFFFF
                    ));
                }
                while (EMITTERS.size() >= MAX_EMITTERS) {
                    EMITTERS.pollFirst();
                }
                EMITTERS.addLast(new BacklashSlotEmitter(tablePos, slotCoords[0], slotCoords[1], slotCoords[2], 120, false));
            }
            case TOME_LOCATE -> {
                double[] slotCoords = TomeParticleEffects.slotWorldCoordinates(
                    payload.targetPos(), payload.shelfFacingOrSouth(), payload.slot()
                );
                for (int i = 0; i < 8; i++) {
                    push(new Particle(
                        tablePos,
                        SpriteKind.ARCANE_RUNE_SPARK,
                        tx, ty + 0.04D, tz,
                        slotCoords[0], slotCoords[1], slotCoords[2],
                        0.22D,
                        i * 2,
                        16,
                        0.082F,
                        0.22F,
                        (i % 2 == 0) ? payload.argbColor() : 0xFFFFE48F
                    ));
                }
                while (EMITTERS.size() >= MAX_EMITTERS) {
                    EMITTERS.pollFirst();
                }
                EMITTERS.addLast(new BacklashSlotEmitter(tablePos, slotCoords[0], slotCoords[1], slotCoords[2], 120, true));
            }
            case TOME_TETHER -> {
                double[] slotCoords = TomeParticleEffects.slotWorldCoordinates(
                    payload.targetPos(), payload.shelfFacingOrSouth(), payload.slot()
                );
                for (int i = 0; i < 5; i++) {
                    push(new Particle(
                        tablePos,
                        SpriteKind.ARCANE_RUNE_SPARK,
                        slotCoords[0], slotCoords[1], slotCoords[2],
                        tx, ty + 0.06D, tz,
                        0.18D,
                        i * 3,
                        18,
                        0.072F,
                        0.18F,
                        (i % 2 == 0) ? payload.argbColor() : 0xFFFFE28A
                    ));
                }
            }
            case TOME_RESTORE -> {
                double[] slotCoords = TomeParticleEffects.slotWorldCoordinates(
                    payload.targetPos(), payload.shelfFacingOrSouth(), payload.slot()
                );
                for (int i = 0; i < 7; i++) {
                    push(new Particle(
                        tablePos,
                        SpriteKind.ARCANE_RUNE_SPARK,
                        tx, ty, tz,
                        slotCoords[0], slotCoords[1], slotCoords[2],
                        0.20D,
                        i * 2,
                        16,
                        0.078F,
                        -0.20F,
                        payload.argbColor()
                    ));
                }
            }
            case SACRIFICE_PAGES -> {
                for (int i = 0; i < 7; i++) {
                    double ang = (Math.PI * 2.0D * i) / 7.0D + rnd.nextDouble() * 0.3D;
                    push(new Particle(
                        tablePos,
                        SpriteKind.SCORCHED_PAGE,
                        tx + Math.cos(ang) * 0.22D,
                        ty - 0.08D,
                        tz + Math.sin(ang) * 0.22D,
                        Math.cos(ang) * 0.015D,
                        0.028D + rnd.nextDouble() * 0.018D,
                        Math.sin(ang) * 0.015D,
                        24 + rnd.nextInt(10),
                        0.095F,
                        (float) ang,
                        0.18F,
                        0.0006F,
                        0xFFFFFFFF
                    ));
                }
                spawnRing(tablePos, tx, ty, tz, 8, 0.24D, 0.022D, payload.argbColor());
            }
            case CLEANSE_PURGE_CURSE -> {
                for (int i = 0; i < 14; i++) {
                    double ang = (Math.PI * 2.0D * i) / 14.0D;
                    double rad = 0.20D + (i % 2) * 0.14D;
                    push(new Particle(
                        tablePos,
                        SpriteKind.ARCANE_RUNE_SPARK,
                        tx + Math.cos(ang) * rad,
                        ty - 0.12D + (i % 3) * 0.06D,
                        tz + Math.sin(ang) * rad,
                        Math.cos(ang) * 0.008D,
                        0.032D + (i % 4) * 0.006D,
                        Math.sin(ang) * 0.008D,
                        22 + (i % 6),
                        0.082F,
                        (float) ang,
                        0.16F,
                        -0.0004F,
                        (i % 3 == 0) ? 0xFFFFFFFF : payload.argbColor()
                    ));
                }
            }
            case CLEANSE_EXTRACT_BOOK -> {
                for (int i = 0; i < 12; i++) {
                    double ang = (Math.PI * 2.0D * i) / 12.0D;
                    double sx = tx + Math.cos(ang) * 0.48D;
                    double sz = tz + Math.sin(ang) * 0.48D;
                    push(new Particle(
                        tablePos,
                        SpriteKind.ARCANE_RUNE_SPARK,
                        sx, ty + 0.04D, sz,
                        tx, ty + 0.16D, tz,
                        0.10D,
                        i,
                        15,
                        0.076F,
                        0.22F,
                        payload.argbColor()
                    ));
                }
            }
            case MENDING_REPAIR -> {
                BlockPos pPos = payload.targetPos() != null ? payload.targetPos() : tablePos;
                double px = pPos.getX() + 0.5D;
                double py = pPos.getY() + 1.0D;
                double pz = pPos.getZ() + 0.5D;
                spawnRing(tablePos, px, py, pz, 10, 0.36D, 0.025D, payload.argbColor());
            }
            case EPIPHANY_FREEZE -> spawnRing(tablePos, tx, ty - 0.04D, tz, 12, 0.38D, 0.010D, payload.argbColor());
            case RITUAL_COMPLETE, ARCANE_PULSE -> spawnRing(tablePos, tx, ty + 0.05D, tz, 12, 0.30D, 0.024D, payload.argbColor());
            case CLOTH_DYE_OR_WASH -> {
                boolean rainbow = payload.argbColor() == 0 && payload.slot() == 0;
                for (int i = 0; i < 14; i++) {
                    double ang = (Math.PI * 2.0D * i) / 14.0D;
                    int col = rainbow
                        ? ChiseledEnchantingTableBlockEntityRenderer.clothColor((long) i * RainbowCloth.PERIOD / 14L, 0.0F)
                        : payload.argbColor();
                    push(new Particle(
                        tablePos,
                        SpriteKind.ARCANE_RUNE_SPARK,
                        tx + Math.cos(ang) * 0.46D,
                        tablePos.getY() + 0.76D,
                        tz + Math.sin(ang) * 0.46D,
                        Math.cos(ang) * 0.014D,
                        0.020D + (i % 3) * 0.005D,
                        Math.sin(ang) * 0.014D,
                        18 + (i % 6),
                        0.074F,
                        0.0F,
                        0.18F,
                        0.0005F,
                        col
                    ));
                }
            }
        }
    }

    private static void spawnRing(
        BlockPos anchorTable,
        double cx, double cy, double cz,
        int count, double radius, double vy, int argb
    ) {
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0D * i) / count;
            push(new Particle(
                anchorTable,
                SpriteKind.ARCANE_RUNE_SPARK,
                cx + Math.cos(ang) * radius,
                cy,
                cz + Math.sin(ang) * radius,
                Math.cos(ang) * 0.015D,
                vy,
                Math.sin(ang) * 0.015D,
                20 + (i % 5),
                0.078F,
                (float) ang,
                0.20F,
                0.0F,
                argb
            ));
        }
    }

    public static synchronized List<QuadSnapshot> extractForTable(
        BlockPos tablePos,
        float partialTick,
        Vec3 cameraPos
    ) {
        if (PARTICLES.isEmpty() || tablePos == null || cameraPos == null) {
            return List.of();
        }
        List<QuadSnapshot> out = null;
        for (Particle p : PARTICLES) {
            if (p.age < 0 || !p.anchorTable.equals(tablePos)) {
                continue;
            }
            float lifeFrac = ((float) p.age + partialTick) / (float) p.maxAge;
            if (lifeFrac < 0.0F || lifeFrac >= 1.0F) {
                continue;
            }
            float envelope = lifeFrac < 0.15F
                ? (lifeFrac / 0.15F)
                : (lifeFrac > 0.72F ? Math.max(0.0F, (1.0F - lifeFrac) / 0.28F) : 1.0F);
            float halfSize = p.baseSize * envelope;
            if (halfSize <= 0.003F) {
                continue;
            }
            double wx = Mth.lerp(partialTick, p.ox, p.x);
            double wy = Mth.lerp(partialTick, p.oy, p.y);
            double wz = Mth.lerp(partialTick, p.oz, p.z);

            double fx = cameraPos.x - wx;
            double fy = cameraPos.y - wy;
            double fz = cameraPos.z - wz;
            double flen = Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (flen < 1.0E-4D) {
                fx = 0.0D;
                fy = 0.0D;
                fz = 1.0D;
            } else {
                fx /= flen;
                fy /= flen;
                fz /= flen;
            }
            // right = normalize(worldUp x forward)
            double rx = fz;
            double ry = 0.0D;
            double rz = -fx;
            double rlen = Math.sqrt(rx * rx + rz * rz);
            if (rlen < 1.0E-4D) {
                rx = 1.0D;
                rz = 0.0D;
            } else {
                rx /= rlen;
                rz /= rlen;
            }
            // up = forward x right
            double ux = fy * rz - fz * ry;
            double uy = fz * rx - fx * rz;
            double uz = fx * ry - fy * rx;

            float roll = Mth.lerp(partialTick, p.oRoll, p.roll);
            float c = Mth.cos(roll) * halfSize;
            float s = Mth.sin(roll) * halfSize;
            float brx = (float) (rx * c + ux * s);
            float bry = (float) (ry * c + uy * s);
            float brz = (float) (rz * c + uz * s);
            float bux = (float) (-rx * s + ux * c);
            float buy = (float) (-ry * s + uy * c);
            float buz = (float) (-rz * s + uz * c);

            if (out == null) {
                out = new ArrayList<>(16);
            }
            out.add(new QuadSnapshot(
                p.sprite,
                (float) (wx - tablePos.getX()),
                (float) (wy - tablePos.getY()),
                (float) (wz - tablePos.getZ()),
                brx, bry, brz,
                bux, buy, buz,
                p.argb
            ));
        }
        return out != null ? out : List.of();
    }

    public static void submitQuads(PoseStack poseStack, SubmitNodeCollector collector, List<QuadSnapshot> quads) {
        if (quads == null || quads.isEmpty()) {
            return;
        }
        boolean hasPages = false;
        boolean hasSparks = false;
        for (QuadSnapshot q : quads) {
            if (q.sprite == SpriteKind.SCORCHED_PAGE) hasPages = true;
            else hasSparks = true;
        }
        if (hasPages) {
            collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(SCORCHED_PAGE_TEXTURE),
                (pose, vc) -> {
                    for (QuadSnapshot q : quads) {
                        if (q.sprite == SpriteKind.SCORCHED_PAGE) {
                            emitQuad(pose, vc, q);
                        }
                    }
                }
            );
        }
        if (hasSparks) {
            collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutout(ARCANE_RUNE_SPARK_TEXTURE),
                (pose, vc) -> {
                    for (QuadSnapshot q : quads) {
                        if (q.sprite == SpriteKind.ARCANE_RUNE_SPARK) {
                            emitQuad(pose, vc, q);
                        }
                    }
                }
            );
        }
    }

    private static void emitQuad(PoseStack.Pose pose, VertexConsumer vc, QuadSnapshot q) {
        float x00 = q.lx - q.rx - q.ux;
        float y00 = q.ly - q.ry - q.uy;
        float z00 = q.lz - q.rz - q.uz;

        float x10 = q.lx + q.rx - q.ux;
        float y10 = q.ly + q.ry - q.uy;
        float z10 = q.lz + q.rz - q.uz;

        float x11 = q.lx + q.rx + q.ux;
        float y11 = q.ly + q.ry + q.uy;
        float z11 = q.lz + q.rz + q.uz;

        float x01 = q.lx - q.rx + q.ux;
        float y01 = q.ly - q.ry + q.uy;
        float z01 = q.lz - q.rz + q.uz;

        vc.addVertex(pose, x00, y00, z00).setColor(q.argb).setUv(0.0F, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vc.addVertex(pose, x10, y10, z10).setColor(q.argb).setUv(1.0F, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vc.addVertex(pose, x11, y11, z11).setColor(q.argb).setUv(1.0F, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vc.addVertex(pose, x01, y01, z01).setColor(q.argb).setUv(0.0F, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT_LIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
