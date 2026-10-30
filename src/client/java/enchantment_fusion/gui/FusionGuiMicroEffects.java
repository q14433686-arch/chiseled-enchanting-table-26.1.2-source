package enchantment_fusion.gui;

import enchantment_fusion.menu.FusionNetworking;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Pure PNG sprite micro-animation &amp; visual feedback engine tuned for the
 * {@code 384 x 218} Cutaway Split-Wing Fusion Altar:
 * <ul>
 *   <li>Counter-rotating rune halos behind Left Wing Pedestal &amp; Center Cutaway Viewport</li>
 *   <li>Converging gold/cyan arcane sparks during the 2.0s channeling sequence</li>
 *   <li>Climax shockwave rings bursting from the Center 3D Altar Arch &amp; Output Pedestal</li>
 * </ul>
 */
public final class FusionGuiMicroEffects {
    private static final class SpriteParticle {
        final Identifier sprite;
        float x;
        float y;
        float vx;
        float vy;
        final float gravity;
        final float baseSize;
        final float endSize;
        float angle;
        final float spin;
        final long bornMs;
        final long durationMs;

        SpriteParticle(
            Identifier sprite,
            float x,
            float y,
            float vx,
            float vy,
            float gravity,
            float baseSize,
            float endSize,
            float angle,
            float spin,
            long bornMs,
            long durationMs
        ) {
            this.sprite = sprite;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.baseSize = baseSize;
            this.endSize = endSize;
            this.angle = angle;
            this.spin = spin;
            this.bornMs = bornMs;
            this.durationMs = durationMs;
        }
    }

    private static final List<SpriteParticle> PARTICLES = new ArrayList<>();
    private static long lastFrameMs = System.currentTimeMillis();
    private static long lastAmbientMoteMs = 0L;

    private static long shakeUntilMs = 0L;
    private static float shakeAmplitude = 0.0F;
    private static long grimoireBadgeGlowUntilMs = 0L;
    private static int stationOriginX = 0;
    private static int stationOriginY = 0;

    private FusionGuiMicroEffects() {}

    public static void updateStationOrigin(int leftPos, int topPos) {
        stationOriginX = leftPos;
        stationOriginY = topPos;
    }

    public static void notifyDiscoveryUnlocked() {
        grimoireBadgeGlowUntilMs = System.currentTimeMillis() + 6500L;
    }

    public static void triggerDiscoveryGlow() {
        notifyDiscoveryUnlocked();
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.42F, 0.75F);
        if (Minecraft.getInstance().screen instanceof EnchantmentFusionScreen) {
            spawnSparkBurst(stationOriginX + 369, stationOriginY + 16, 8, 52.0F, 520L);
        }
    }

    public static void onServerVisualEvent(int eventKind) {
        triggerStationFeedback(eventKind, stationOriginX, stationOriginY);
    }

    public static int currentShakeOffsetX() {
        long now = System.currentTimeMillis();
        if (now >= shakeUntilMs) {
            return 0;
        }
        float decay = (float) (shakeUntilMs - now) / 260.0F;
        return Math.round(Mth.sin(now * 0.095F) * shakeAmplitude * Mth.clamp(decay, 0.0F, 1.0F));
    }

    public static int currentShakeOffsetY() {
        long now = System.currentTimeMillis();
        if (now >= shakeUntilMs) {
            return 0;
        }
        float decay = (float) (shakeUntilMs - now) / 260.0F;
        return Math.round(Mth.cos(now * 0.125F) * shakeAmplitude * 0.75F * Mth.clamp(decay, 0.0F, 1.0F));
    }

    public static void triggerStationFeedback(int eventKind, int stationOriginX, int stationOriginY) {
        if (!(Minecraft.getInstance().screen instanceof EnchantmentFusionScreen)) {
            return;
        }
        int pedX = stationOriginX + 58;
        int pedY = stationOriginY + 82;
        int outY = stationOriginY + 110;
        int archCenterX = stationOriginX + 174;
        int archCenterY = stationOriginY + 65;

        switch (eventKind) {
            case FusionNetworking.VISUAL_FUSE_SUCCESS -> {
                spawnShockwave(archCenterX, archCenterY, 18.0F, 88.0F, 520L, 2.6F);
                spawnShockwave(pedX, outY, 14.0F, 64.0F, 440L, -2.5F);
                spawnSparkBurst(archCenterX, archCenterY, 18, 96.0F, 660L);
                spawnSparkBurst(pedX, outY, 12, 74.0F, 520L);
                triggerShake(2.2F, 190L);
            }
            case FusionNetworking.VISUAL_FUSE_ABERRANT -> {
                spawnShockwave(archCenterX, archCenterY, 20.0F, 98.0F, 620L, -4.4F);
                spawnShockwave(pedX, outY, 16.0F, 72.0F, 520L, 4.0F);
                spawnSparkBurst(archCenterX, archCenterY, 24, 112.0F, 740L);
                triggerShake(4.0F, 290L);
            }
            case FusionNetworking.VISUAL_FUSE_FAIL -> {
                spawnShockwave(pedX, pedY, 24.0F, 8.0F, 320L, 3.0F);
                spawnSparkBurst(pedX, pedY, 9, 48.0F, 420L);
                triggerShake(2.8F, 220L);
            }
            case FusionNetworking.VISUAL_FLIP_ORDER -> {
                spawnShockwave(stationOriginX + 58, stationOriginY + 52, 12.0F, 54.0F, 420L, 3.8F);
                spawnSparkBurst(stationOriginX + 27, stationOriginY + 52, 7, 50.0F, 440L);
                spawnSparkBurst(stationOriginX + 90, stationOriginY + 52, 7, 50.0F, 440L);
            }
            case FusionNetworking.VISUAL_SPLIT_EXTRACT -> {
                spawnShockwave(pedX, outY, 12.0F, 56.0F, 420L, -3.0F);
                spawnSparkBurst(pedX, outY, 12, 72.0F, 520L);
            }
            default -> {}
        }
    }

    public static void triggerSwapPulse(int centerX, int centerY) {
        spawnShockwave(centerX, centerY, 10.0F, 44.0F, 340L, 4.5F);
        spawnSparkBurst(centerX - 28, centerY, 5, 42.0F, 360L);
        spawnSparkBurst(centerX + 28, centerY, 5, 42.0F, 360L);
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.32F, 0.70F);
    }

    public static void triggerCandidatePickSpark(int rowCenterX, int rowCenterY, boolean isPrimary) {
        spawnSparkBurst(rowCenterX, rowCenterY, 5, 36.0F, 320L);
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, isPrimary ? 1.18F : 1.36F, 0.55F);
    }

    public static void triggerGrimoireSparkle(int centerX, int centerY) {
        spawnSparkBurst(centerX, centerY, 6, 40.0F, 420L);
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.28F, 0.50F);
    }

    public static void renderPedestalResonanceBackdrop(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        boolean executable,
        boolean highRiskOrChanneling
    ) {
        stationOriginX = left;
        stationOriginY = top;
        long now = System.currentTimeMillis();
        float tSec = (now % 60000L) / 1000.0F;
        boolean channeling = FusionRitualDirector.isChanneling();

        if (executable || channeling) {
            float speed = channeling ? 3.2F : (highRiskOrChanneling ? 1.65F : 0.78F);
            float angle = tSec * speed;
            float breath = 1.0F + 0.08F * Mth.sin(tSec * (channeling ? 8.5F : 2.8F));

            drawRotatedSprite(
                ctx, FusionGuiSkin.VFX_RUNE_HALO,
                left + 58.0F, top + 109.0F,
                36.0F * breath, angle
            );
            drawRotatedSprite(
                ctx, FusionGuiSkin.VFX_RUNE_HALO,
                left + 58.0F, top + 52.0F,
                26.0F * breath, -angle * 1.25F
            );

            if (channeling) {
                float p = FusionRitualDirector.channelProgress();
                drawRotatedSprite(
                    ctx, FusionGuiSkin.VFX_RUNE_HALO,
                    left + 174.0F, top + 115.0F,
                    (18.0F + 20.0F * p) * breath, angle * 1.6F
                );
            }

            long interval = channeling ? 55L : (highRiskOrChanneling ? 115L : 195L);
            if (now - lastAmbientMoteMs >= interval && PARTICLES.size() < 76) {
                lastAmbientMoteMs = now;
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                boolean fromLeft = rng.nextBoolean();
                float sx = left + (fromLeft ? 27.0F : 90.0F) + (rng.nextFloat() - 0.5F) * 6.0F;
                float sy = top + 52.0F + (rng.nextFloat() - 0.5F) * 6.0F;
                float tx = channeling ? (left + 174.0F) : (left + 58.0F);
                float ty = channeling ? (top + 65.0F) : (top + 109.0F);
                PARTICLES.add(new SpriteParticle(
                    FusionGuiSkin.VFX_ARCANE_SPARK,
                    sx, sy,
                    (tx - sx) * (channeling ? 2.4F : 1.85F),
                    (ty - sy) * (channeling ? 2.4F : 1.85F),
                    0.0F,
                    channeling ? 10.5F : 8.5F, 2.0F,
                    rng.nextFloat() * Mth.TWO_PI,
                    3.6F,
                    now,
                    channeling ? 420L : 520L
                ));
            }
        }

        if (now < grimoireBadgeGlowUntilMs) {
            float pulse = 1.0F + 0.14F * Mth.sin(tSec * 6.0F);
            drawRotatedSprite(
                ctx, FusionGuiSkin.VFX_RUNE_HALO,
                left + 369.0F, top + 16.0F,
                22.0F * pulse, tSec * 1.8F
            );
        }
    }

    public static void renderGrimoireMasteredSeal(GuiGraphicsExtractor ctx, float centerX, float centerY) {
        float tSec = (System.currentTimeMillis() % 60000L) / 1000.0F;
        float breath = 1.0F + 0.06F * Mth.sin(tSec * 2.4F);
        drawRotatedSprite(
            ctx, FusionGuiSkin.VFX_RUNE_HALO,
            centerX, centerY,
            24.0F * breath, tSec * 0.65F
        );
    }

    public static void stepAndRenderOverlayParticles(GuiGraphicsExtractor ctx) {
        long now = System.currentTimeMillis();
        float dt = Mth.clamp((now - lastFrameMs) / 1000.0F, 0.0F, 0.08F);
        lastFrameMs = now;

        if (PARTICLES.isEmpty()) {
            return;
        }
        Iterator<SpriteParticle> it = PARTICLES.iterator();
        while (it.hasNext()) {
            SpriteParticle p = it.next();
            long age = now - p.bornMs;
            if (age >= p.durationMs) {
                it.remove();
                continue;
            }
            float prog = (float) age / (float) p.durationMs;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += p.gravity * dt;
            p.angle += p.spin * dt;

            float size = Mth.lerp(prog, p.baseSize, p.endSize);
            if (size >= 1.2F) {
                drawRotatedSprite(ctx, p.sprite, p.x, p.y, size, p.angle);
            }
        }
    }

    private static void spawnShockwave(
        float x,
        float y,
        float startSize,
        float endSize,
        long durationMs,
        float spinSpeed
    ) {
        if (PARTICLES.size() > 96) {
            return;
        }
        PARTICLES.add(new SpriteParticle(
            FusionGuiSkin.VFX_SHOCKWAVE_RING,
            x, y,
            0.0F, 0.0F, 0.0F,
            startSize, endSize,
            0.0F, spinSpeed,
            System.currentTimeMillis(),
            durationMs
        ));
    }

    private static void spawnSparkBurst(float cx, float cy, int count, float maxSpeed, long durationMs) {
        long now = System.currentTimeMillis();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count && PARTICLES.size() < 110; i++) {
            float ang = (Mth.TWO_PI * i) / Math.max(1, count) + (rng.nextFloat() - 0.5F) * 0.45F;
            float spd = maxSpeed * (0.45F + rng.nextFloat() * 0.65F);
            PARTICLES.add(new SpriteParticle(
                FusionGuiSkin.VFX_ARCANE_SPARK,
                cx, cy,
                Mth.cos(ang) * spd,
                Mth.sin(ang) * spd - 14.0F,
                95.0F,
                11.0F + rng.nextFloat() * 4.0F,
                1.5F,
                rng.nextFloat() * Mth.TWO_PI,
                (rng.nextBoolean() ? 1.0F : -1.0F) * (3.0F + rng.nextFloat() * 4.0F),
                now,
                durationMs + rng.nextLong(120L)
            ));
        }
    }

    private static void triggerShake(float amplitude, long durationMs) {
        shakeAmplitude = amplitude;
        shakeUntilMs = System.currentTimeMillis() + durationMs;
    }

    private static void drawRotatedSprite(
        GuiGraphicsExtractor ctx,
        Identifier sprite,
        float centerX,
        float centerY,
        float size,
        float angleRad
    ) {
        int intSize = Math.max(2, Math.round(size));
        float half = intSize * 0.5F;
        ctx.pose().pushMatrix();
        ctx.pose().translate(centerX, centerY);
        if (Math.abs(angleRad) > 0.001F) {
            ctx.pose().rotate(angleRad);
        }
        ctx.pose().translate(-half, -half);
        FusionGuiSkin.drawSprite(ctx, sprite, 0, 0, intSize, intSize);
        ctx.pose().popMatrix();
    }

    public static void playUiSound(SoundEvent sound, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }
}
