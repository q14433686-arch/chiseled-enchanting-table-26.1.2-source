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
 * Pure PNG sprite micro-animation &amp; visual feedback engine tuned for the compact
 * {@code 304 x 218} Split-Wing Live-Stage Fusion Altar, featuring bespoke particle &amp; sound
 * choreography per station operation.
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
            spawnSparkBurst(stationOriginX + 288, stationOriginY + 16, 8, 52.0F, 520L);
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

    /** Instant UI &amp; stage feedback when the player triggers Flip / Swap Order / Swap Slots. */
    public static void triggerFlipFeedback(int originX, int originY, boolean physicalSlotSwap) {
        FusionLiveStageRenderer.triggerInstantFlip();
        int swapX = originX + 48;
        int swapY = originY + 39;
        int stageX = originX + 140;
        int stageY = originY + 48;
        spawnShockwave(swapX, swapY, 10.0F, 34.0F, 280L, physicalSlotSwap ? -4.5F : 4.5F);
        spawnSparkBurst(stageX, stageY, 7, 46.0F, 340L);
        playUiSound(
            physicalSlotSwap ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.AMETHYST_CLUSTER_STEP,
            physicalSlotSwap ? 1.18F : 1.34F,
            0.72F
        );
    }

    /** Instant UI &amp; stage feedback when the player splits a Fused Book or unsockets a Vessel gem. */
    public static void triggerSplitFeedback(int originX, int originY, net.minecraft.world.item.ItemStack a, net.minecraft.world.item.ItemStack b) {
        FusionLiveStageRenderer.triggerInstantSplit(a, b);
        int stageX = originX + 140;
        int stageY = originY + 48;
        spawnShockwave(stageX, stageY, 8.0F, 48.0F, 360L, -3.2F);
        spawnSparkBurst(stageX - 14, stageY, 6, 54.0F, 420L);
        spawnSparkBurst(stageX + 14, stageY, 6, 54.0F, 420L);
        playUiSound(SoundEvents.SHEEP_SHEAR, 1.38F, 0.48F);
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.26F, 0.68F);
    }

    public static void triggerStationFeedback(int eventKind, int originX, int originY) {
        if (!(Minecraft.getInstance().screen instanceof EnchantmentFusionScreen)) {
            return;
        }
        int outX = originX + 72;
        int outY = originY + 86;
        int stageX = originX + 140;
        int stageY = originY + 50;

        switch (eventKind) {
            case FusionNetworking.VISUAL_FUSE_SUCCESS -> {
                spawnShockwave(stageX, stageY, 16.0F, 76.0F, 500L, 2.6F);
                spawnShockwave(outX, outY, 12.0F, 52.0F, 420L, -2.5F);
                spawnSparkBurst(stageX, stageY, 16, 86.0F, 620L);
                spawnSparkBurst(outX, outY, 10, 64.0F, 480L);
                triggerShake(2.0F, 180L);
            }
            case FusionNetworking.VISUAL_FUSE_ABERRANT -> {
                spawnShockwave(stageX, stageY, 18.0F, 88.0F, 600L, -4.4F);
                spawnShockwave(outX, outY, 14.0F, 64.0F, 500L, 4.0F);
                spawnSparkBurst(stageX, stageY, 22, 110.0F, 740L);
                triggerShake(4.2F, 300L);
            }
            case FusionNetworking.VISUAL_FUSE_FLUCTUATED -> {
                spawnShockwave(stageX, stageY, 14.0F, 50.0F, 360L, -2.0F);
                spawnSparkBurst(stageX, stageY, 8, 52.0F, 420L);
                triggerShake(2.4F, 210L);
            }
            case FusionNetworking.VISUAL_UTILITY_DONE -> {
                spawnShockwave(stageX, stageY, 12.0F, 48.0F, 340L, 2.4F);
                spawnSparkBurst(outX, outY, 8, 54.0F, 400L);
            }
            default -> {}
        }
    }

    public static void renderPedestalResonanceBackdrop(
        GuiGraphicsExtractor ctx,
        int leftPos,
        int topPos,
        boolean executable,
        boolean highRisk,
        float channelProgress
    ) {
        long now = System.currentTimeMillis();
        float tSec = (now % 60000L) / 1000.0F;

        int pedCenterX = leftPos + 48;
        int pedCenterY = topPos + 62;
        int stageCenterX = leftPos + 140;
        int stageCenterY = topPos + 50;

        float spinSpeed = channelProgress > 0.0F
            ? 2.8F + channelProgress * 4.5F
            : (executable ? (highRisk ? 1.55F : 0.80F) : 0.24F);
        float pulse = executable ? (1.0F + 0.06F * Mth.sin(tSec * (highRisk ? 6.5F : 2.8F))) : 0.90F;
        float size = (executable ? 46.0F : 36.0F) * pulse;

        drawRotatedSprite(ctx, FusionGuiSkin.VFX_RUNE_HALO, pedCenterX, pedCenterY, size, tSec * spinSpeed);

        long emitInterval = channelProgress > 0.0F ? 55L : (highRisk ? 130L : 260L);
        if ((executable || channelProgress > 0.0F) && now - lastAmbientMoteMs > emitInterval && PARTICLES.size() < 64) {
            lastAmbientMoteMs = now;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            float ang = rng.nextFloat() * Mth.TWO_PI;
            float rad = 18.0F + rng.nextFloat() * 14.0F;
            float sx = stageCenterX + Mth.cos(ang) * rad;
            float sy = stageCenterY + Mth.sin(ang) * rad * 0.75F;
            PARTICLES.add(new SpriteParticle(
                FusionGuiSkin.VFX_ARCANE_SPARK,
                sx, sy,
                (stageCenterX - sx) * (0.8F + channelProgress * 2.2F),
                (stageCenterY - sy) * (0.8F + channelProgress * 2.2F) - 8.0F,
                channelProgress > 0.0F ? 0.0F : -10.0F,
                8.0F + channelProgress * 3.5F,
                2.0F,
                rng.nextFloat() * Mth.TWO_PI,
                (rng.nextBoolean() ? 1.0F : -1.0F) * 2.4F,
                now,
                channelProgress > 0.0F ? 360L : 650L
            ));
        }

        if (now < grimoireBadgeGlowUntilMs) {
            float glowPulse = 1.0F + 0.14F * Mth.sin(tSec * 7.0F);
            drawRotatedSprite(
                ctx, FusionGuiSkin.VFX_RUNE_HALO,
                leftPos + 288, topPos + 16,
                20.0F * glowPulse, -tSec * 2.0F
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
        FusionGuiSkin.drawRotatedSprite(ctx, sprite, centerX, centerY, size, size, angleRad);
    }

    public static void playUiSound(SoundEvent sound, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    public static void playUiSound(net.minecraft.core.Holder<SoundEvent> soundHolder, float pitch, float volume) {
        if (soundHolder != null) {
            playUiSound(soundHolder.value(), pitch, volume);
        }
    }
}
