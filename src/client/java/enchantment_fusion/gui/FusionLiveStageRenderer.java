package enchantment_fusion.gui;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Real-time scissored 3D Altar &amp; 8-Scene Live Fusion Stage Renderer inside the center archway
 * ({@code 96..184, 6..118}) of {@link EnchantmentFusionScreen}.
 * <p>
 * Directly renders the miniature 3D Fusion Altar, floating Amethyst Crystal Core &amp; counter-rotating
 * Shards, four corner braziers, continuous flowing Gold &amp; Cyan Bezier ribbons, and the live
 * {@link ItemStack}s in Slots 0..3 executing 8 distinct operation-specific scenes:
 * <ol>
 *   <li>{@code FUSE_NEW_BOOK}: Twin Gold/Cyan helical book orbits converging into the crystal core</li>
 *   <li>{@code SOCKET_GEAR}: Host gear hovering on throne while rune plate stamps down into ◇ clasps</li>
 *   <li>{@code INSCRIBE_SINGLE_CROSS}: Phantom silhouettes circling and reforging host item outline</li>
 *   <li>{@code INSTANT_MIRROR_FLIP}: 0.45s 180-deg Yin-Yang twin-arc mirror swap (Flip / Swap Order / Swap Slots)</li>
 *   <li>{@code INSTANT_BIFURCATE_SPLIT}: 0.55s central cleavage flash springing two books apart (Split / Unsocket)</li>
 *   <li>{@code EXPAND_VESSEL}: Sculk-cyan Echo sonic rings chiseling open a second ◇◇ gem socket</li>
 *   <li>{@code PURIFY_ABERRANT}: Spinning golden-white triad of Stabilizers extracting crimson chains</li>
 *   <li>{@code TRANSITION_MODE_MORPH}: 0.35s smooth throne &amp; right-wing slide transition on host change</li>
 * </ol>
 */
public final class FusionLiveStageRenderer {
    public enum InstantScene {
        NONE(0L),
        MIRROR_FLIP(460L),
        BIFURCATE_SPLIT(560L),
        MODE_MORPH(360L);

        final long durationMs;

        InstantScene(long durationMs) {
            this.durationMs = durationMs;
        }
    }

    private static InstantScene activeInstant = InstantScene.NONE;
    private static long instantStartMs = 0L;
    private static ItemStack cachedSplitLeft = ItemStack.EMPTY;
    private static ItemStack cachedSplitRight = ItemStack.EMPTY;

    private static @Nullable ItemStack crystalCoreProxy;
    private static @Nullable ItemStack crystalShardsProxy;
    private static @Nullable ItemStack fusedBookPreview;
    private static @Nullable ItemStack stabilizerPreview;

    private FusionLiveStageRenderer() {}

    public static void triggerInstantFlip() {
        activeInstant = InstantScene.MIRROR_FLIP;
        instantStartMs = System.currentTimeMillis();
    }

    public static void triggerInstantSplit(ItemStack leftSample, ItemStack rightSample) {
        activeInstant = InstantScene.BIFURCATE_SPLIT;
        instantStartMs = System.currentTimeMillis();
        cachedSplitLeft = (leftSample == null || leftSample.isEmpty())
            ? new ItemStack(Items.ENCHANTED_BOOK)
            : leftSample.copyWithCount(1);
        cachedSplitRight = (rightSample == null || rightSample.isEmpty())
            ? new ItemStack(Items.ENCHANTED_BOOK)
            : rightSample.copyWithCount(1);
    }

    public static void triggerModeMorph() {
        if (activeInstant == InstantScene.NONE) {
            activeInstant = InstantScene.MODE_MORPH;
            instantStartMs = System.currentTimeMillis();
        }
    }

    public static int currentRightWingMorphOffsetX(boolean gearMode) {
        if (activeInstant != InstantScene.MODE_MORPH) {
            return 0;
        }
        float p = instantProgress();
        if (p >= 1.0F) {
            return 0;
        }
        float easeOut = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
        float dir = gearMode ? 1.0F : -1.0F;
        return Math.round((1.0F - easeOut) * 8.0F * dir);
    }

    private static float instantProgress() {
        if (activeInstant == InstantScene.NONE || activeInstant.durationMs <= 0L) {
            return 1.0F;
        }
        long elapsed = System.currentTimeMillis() - instantStartMs;
        if (elapsed >= activeInstant.durationMs) {
            activeInstant = InstantScene.NONE;
            return 1.0F;
        }
        return Mth.clamp((float) elapsed / (float) activeInstant.durationMs, 0.0F, 1.0F);
    }

    private static ItemStack ensureModelProxy(@Nullable ItemStack cached, String modelName) {
        if (cached != null) {
            return cached;
        }
        ItemStack stack = new ItemStack(Items.AMETHYST_SHARD);
        stack.set(DataComponents.ITEM_MODEL, EnchantmentFusion.id(modelName));
        return stack;
    }

    private static ItemStack fusedBookDisplay() {
        if (fusedBookPreview == null) {
            fusedBookPreview = new ItemStack(FusionItems.FUSED_ENCHANTED_BOOK);
        }
        return fusedBookPreview;
    }

    private static ItemStack stabilizerDisplay() {
        if (stabilizerPreview == null) {
            stabilizerPreview = new ItemStack(FusionItems.RESONANCE_STABILIZER);
        }
        return stabilizerPreview;
    }

    public static void renderStage(
        GuiGraphicsExtractor ctx,
        int stageX,
        int stageY,
        int stageW,
        int stageH,
        EnchantmentFusionMenu menu,
        StationPreview preview,
        boolean gearMode
    ) {
        long nowMs = System.currentTimeMillis();
        float tSec = (nowMs % 120000L) / 1000.0F;
        Container inv = menu.stationContainer();
        ItemStack slotA = inv.getItem(FusionStationOperations.SLOT_PRIMARY);
        ItemStack slotB = inv.getItem(FusionStationOperations.SLOT_SECONDARY);
        ItemStack slotCat = inv.getItem(FusionStationOperations.SLOT_CATALYST);
        ItemStack slotTarget = inv.getItem(FusionStationOperations.SLOT_TARGET);

        boolean channeling = FusionRitualDirector.isChanneling();
        float ch = FusionRitualDirector.channelProgress();
        OperationKind activeOp = channeling ? FusionRitualDirector.activeOperationKind() : preview.kind();
        boolean highRisk = preview.economyQuote() != null && preview.economyQuote().highRiskTier();
        boolean anyOccupied = !slotA.isEmpty() || !slotB.isEmpty() || !slotCat.isEmpty() || !slotTarget.isEmpty();

        int cx = stageX + stageW / 2;
        int altarY = stageY + stageH - 34;
        int apexY = stageY + 42;

        // 1. Starry Obsidian Sanctum Backdrop (pure pixel-art PNG)
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.STAGE_BACKDROP, stageX, stageY, stageW, stageH);

        ctx.enableScissor(stageX + 3, stageY + 4, stageX + stageW - 3, stageY + stageH - 4);

        // 2. Ambient / Channeling Astrolabe Rune Halo behind the Crystal
        float haloSpin = tSec * (channeling ? 2.4F : (preview.executable() ? 0.85F : 0.28F));
        float haloSize = (preview.executable() || channeling ? 44.0F : 34.0F) + ch * 12.0F;
        if (anyOccupied || channeling) {
            FusionGuiSkin.drawRotatedSprite(ctx, FusionGuiSkin.VFX_RUNE_HALO, cx, apexY, haloSize, haloSize, haloSpin);
        }

        // 3. Continuous Bezier Silk Ribbons flowing from Left Wing Conduits into the Stage Crystal
        float ribbonActivity = channeling ? (0.45F + 0.55F * ch) : (preview.executable() ? 0.92F : (anyOccupied ? 0.55F : 0.0F));
        if (!slotA.isEmpty() || channeling) {
            float sway = Mth.sin(tSec * 2.8F) * 4.5F;
            FusionGuiSkin.drawContinuousBezierRibbon(
                ctx, FusionGuiSkin.CONDUIT_GOLD,
                stageX + 2.0F, stageY + 34.0F,
                cx - 18.0F, apexY - 12.0F + sway,
                cx, apexY,
                channeling ? 5.2F : 3.6F,
                ribbonActivity
            );
        }
        if (!slotB.isEmpty() || (channeling && activeOp == OperationKind.FUSE_BOOKS)) {
            float sway = Mth.cos(tSec * 2.6F) * 4.5F;
            FusionGuiSkin.drawContinuousBezierRibbon(
                ctx, FusionGuiSkin.CONDUIT_CYAN,
                stageX + 2.0F, stageY + 52.0F,
                cx + 18.0F, apexY + 10.0F + sway,
                cx, apexY,
                channeling ? 5.2F : 3.6F,
                ribbonActivity
            );
        }
        if (!slotCat.isEmpty() || (channeling && activeOp == OperationKind.PURIFY_ABERRANT)) {
            FusionGuiSkin.drawContinuousBezierRibbon(
                ctx, FusionGuiSkin.CONDUIT_AMETHYST,
                stageX + 2.0F, stageY + 78.0F,
                cx - 12.0F, altarY + 4.0F,
                cx, apexY + 4.0F,
                channeling ? 4.6F : 3.0F,
                ribbonActivity
            );
        }

        // 4. Isometric Altar Pedestal + 4 Corner Animated Purple Braziers + Floating 3D Amethyst Crystal Core
        int morphLift = (activeInstant == InstantScene.MODE_MORPH)
            ? Math.round(Mth.sin(instantProgress() * Mth.PI) * 3.0F)
            : 0;
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.STAGE_ALTAR_PEDESTAL, cx - 24, altarY + morphLift, 48, 32);

        boolean lit = anyOccupied || channeling;
        FusionGuiSkin.drawMiniBrazier(ctx, cx - 25, altarY - 3, lit, channeling);
        FusionGuiSkin.drawMiniBrazier(ctx, cx + 11, altarY - 3, lit, channeling);

        crystalCoreProxy = ensureModelProxy(crystalCoreProxy, "fusion_crystal_core");
        crystalShardsProxy = ensureModelProxy(crystalShardsProxy, "fusion_crystal_shards");
        float crystalBob = Mth.sin(tSec * (channeling ? 7.5F : 2.2F)) * (channeling ? 3.2F : 1.8F) - ch * 4.0F;
        float crystalScale = 1.05F + ch * 0.26F;
        drawScaledStageItem(ctx, crystalShardsProxy, cx, altarY - 8 + crystalBob * 0.6F, 0.92F, -tSec * (channeling ? 4.2F : 0.9F));
        drawScaledStageItem(ctx, crystalCoreProxy, cx, altarY - 12 + crystalBob, crystalScale, 0.0F);

        // 5. Choreograph the 8 Distinct Scenes
        float instProg = instantProgress();
        if (activeInstant == InstantScene.MIRROR_FLIP && instProg < 1.0F) {
            renderSceneMirrorFlip(ctx, cx, apexY, instProg, slotA, slotB, slotTarget);
        } else if (activeInstant == InstantScene.BIFURCATE_SPLIT && instProg < 1.0F) {
            renderSceneBifurcateSplit(ctx, cx, apexY, instProg);
        } else if (channeling) {
            renderChannelingScene(ctx, cx, apexY, altarY, tSec, ch, activeOp, highRisk, slotA, slotB, slotCat, slotTarget);
        } else if (FusionRitualDirector.hasActiveClimaxBanner()) {
            renderPostClimaxEcho(ctx, cx, apexY, tSec, slotTarget);
        } else {
            renderIdleLiveActors(ctx, cx, apexY, altarY, tSec, preview, gearMode, slotA, slotB, slotCat, slotTarget);
        }

        ctx.disableScissor();
    }

    // --- Scene 4: Instant 0.45s 180-deg Yin-Yang Mirror Flip (Flip / Swap Order / Swap Slots) ---
    private static void renderSceneMirrorFlip(
        GuiGraphicsExtractor ctx,
        int cx,
        int apexY,
        float p,
        ItemStack slotA,
        ItemStack slotB,
        ItemStack slotTarget
    ) {
        float ease = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
        float angle = ease * Mth.PI;
        FusionGuiSkin.drawRotatedSprite(ctx, FusionGuiSkin.STAGE_YINYANG_RING, cx, apexY, 48.0F, 48.0F, angle);

        ItemStack leftActor = !slotA.isEmpty() ? slotA : (!slotTarget.isEmpty() ? slotTarget : new ItemStack(Items.ENCHANTED_BOOK));
        ItemStack rightActor = !slotB.isEmpty() ? slotB : fusedBookDisplay();
        float rx = Mth.cos(angle) * 19.0F;
        float ry = Mth.sin(angle) * 9.0F;
        drawScaledStageItem(ctx, leftActor, cx - rx, apexY - ry, 0.86F, 0.0F);
        drawScaledStageItem(ctx, rightActor, cx + rx, apexY + ry, 0.86F, 0.0F);
    }

    // --- Scene 5: Instant 0.55s Bifurcating Cleavage Arc (Split Book / Unsocket) ---
    private static void renderSceneBifurcateSplit(GuiGraphicsExtractor ctx, int cx, int apexY, float p) {
        float ease = 1.0F - (1.0F - p) * (1.0F - p);
        float cleaveSize = 46.0F * (1.0F - 0.25F * p);
        FusionGuiSkin.drawRotatedSprite(ctx, FusionGuiSkin.STAGE_SPLIT_CLEAVE, cx, apexY, cleaveSize, cleaveSize, 0.0F);

        float spreadX = 4.0F + ease * 22.0F;
        float liftY = -Mth.sin(p * Mth.PI) * 9.0F;
        drawScaledStageItem(ctx, cachedSplitLeft, cx - spreadX, apexY + liftY, 0.85F, -0.18F * ease);
        drawScaledStageItem(ctx, cachedSplitRight, cx + spreadX, apexY + liftY, 0.85F, 0.18F * ease);
    }

    // --- Scenes 1, 2, 3, 6, 7: Operation-Specific Channeling Choreography ---
    private static void renderChannelingScene(
        GuiGraphicsExtractor ctx,
        int cx,
        int apexY,
        int altarY,
        float tSec,
        float ch,
        OperationKind kind,
        boolean highRisk,
        ItemStack slotA,
        ItemStack slotB,
        ItemStack slotCat,
        ItemStack slotTarget
    ) {
        switch (kind) {
            case PURIFY_ABERRANT -> {
                // Scene 7: Three Resonance Stabilizers in a spinning sacred golden-white triangle extracting crimson chains
                float rot = tSec * 2.8F + ch * Mth.TWO_PI;
                float triadSize = 54.0F - ch * 8.0F;
                FusionGuiSkin.drawRotatedSprite(ctx, FusionGuiSkin.STAGE_PURIFY_TRIAD, cx, apexY, triadSize, triadSize, rot);
                for (int i = 0; i < 3; i++) {
                    float a = rot + i * (Mth.TWO_PI / 3.0F);
                    float rad = Mth.lerp(ch, 22.0F, 9.0F);
                    drawScaledStageItem(
                        ctx, stabilizerDisplay(),
                        cx + Mth.cos(a) * rad, apexY + Mth.sin(a) * rad * 0.72F,
                        0.72F, 0.0F
                    );
                }
                if (!slotTarget.isEmpty()) {
                    float purifyPulse = 1.0F + 0.16F * Mth.sin(ch * Mth.PI);
                    drawScaledStageItem(ctx, slotTarget, cx, apexY, purifyPulse, 0.0F);
                }
            }
            case EXPAND_VESSEL -> {
                // Scene 6: Sculk-cyan Echo sonic rings chiseling open a second ◇◇ Vessel Socket
                float pulse = (ch * 2.5F) % 1.0F;
                float ringSize = 20.0F + pulse * 36.0F;
                FusionGuiSkin.drawRotatedSprite(ctx, FusionGuiSkin.STAGE_SCULK_RING, cx, apexY, ringSize, ringSize, ch * 1.6F);
                if (!slotTarget.isEmpty()) {
                    drawScaledStageItem(ctx, slotTarget, cx, apexY + 2.0F, 1.08F, 0.0F);
                }
                float gemSpread = Mth.lerp(ch, 2.0F, 9.0F);
                FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.VESSEL_FILLED, Math.round(cx - gemSpread - 5), apexY - 22, 10, 10);
                FusionGuiSkin.drawSprite(
                    ctx, ch > 0.65F ? FusionGuiSkin.VESSEL_FILLED : FusionGuiSkin.VESSEL_EMPTY,
                    Math.round(cx + gemSpread - 5), apexY - 22, 10, 10
                );
            }
            case INSCRIBE_SINGLE_CROSS -> {
                // Scene 3: Single Cross-Domain Transmutation — 3 phantom silhouettes spiraling into host item outline
                ItemStack donor = !slotA.isEmpty() ? slotA : (!slotB.isEmpty() ? slotB : new ItemStack(Items.ENCHANTED_BOOK));
                float rad = Mth.lerp(ch, 23.0F, 2.0F);
                for (int i = 0; i < 3; i++) {
                    float a = tSec * 4.5F + ch * 5.0F + i * (Mth.TWO_PI / 3.0F);
                    drawScaledStageItem(ctx, donor, cx + Mth.cos(a) * rad, apexY + Mth.sin(a) * rad * 0.55F, 0.68F * (1.0F - 0.35F * ch), 0.0F);
                }
                if (!slotTarget.isEmpty()) {
                    drawScaledStageItem(ctx, slotTarget, cx, apexY, 1.02F + 0.18F * ch, Mth.sin(ch * Mth.TWO_PI * 2.0F) * 0.08F);
                }
            }
            case SOCKET_FUSED_BOOK, FUSE_ONTO_GEAR, TOP_UP_LEVEL -> {
                // Scene 2: Host gear hovers on throne while Rune Plate descends from top arch and stamps into ◇ gem
                if (!slotTarget.isEmpty()) {
                    float impactDip = ch > 0.78F ? Mth.sin(((ch - 0.78F) / 0.22F) * Mth.PI) * 3.5F : 0.0F;
                    drawScaledStageItem(ctx, slotTarget, cx, apexY + 6.0F + impactDip, 1.12F, 0.0F);
                }
                ItemStack sigilBook = !slotA.isEmpty() ? slotA : fusedBookDisplay();
                float stampY = Mth.lerp(ch * ch, apexY - 22.0F, apexY + 4.0F);
                drawScaledStageItem(ctx, sigilBook, cx, stampY, 0.82F * (1.0F - 0.25F * ch), 0.0F);
                FusionGuiSkin.drawSprite(
                    ctx, ch > 0.75F ? FusionGuiSkin.VESSEL_FILLED : FusionGuiSkin.VESSEL_EMPTY,
                    cx - 5, altarY + 4, 10, 10
                );
            }
            default -> {
                // Scene 1: Book Synthesis — Books A (Gold) & B (Cyan) spiral tightly into the crystal core!
                float jitterX = highRisk ? Mth.sin(tSec * 48.0F) * 2.2F * ch : 0.0F;
                float rad = Mth.lerp(ch * ch, 24.0F, 2.0F);
                float ang = tSec * 3.2F + ch * ch * 7.5F;
                ItemStack actorA = !slotA.isEmpty() ? slotA : new ItemStack(Items.ENCHANTED_BOOK);
                ItemStack actorB = !slotB.isEmpty() ? slotB : new ItemStack(Items.ENCHANTED_BOOK);
                drawScaledStageItem(ctx, actorA, cx + Mth.cos(ang) * rad + jitterX, apexY + Mth.sin(ang) * rad * 0.52F, 0.85F, 0.0F);
                drawScaledStageItem(ctx, actorB, cx - Mth.cos(ang) * rad - jitterX, apexY - Mth.sin(ang) * rad * 0.52F, 0.85F, 0.0F);
                if (ch > 0.72F) {
                    float condenseScale = 0.55F + ((ch - 0.72F) / 0.28F) * 0.65F;
                    drawScaledStageItem(ctx, fusedBookDisplay(), cx, apexY, condenseScale, 0.0F);
                }
            }
        }
    }

    private static void renderPostClimaxEcho(GuiGraphicsExtractor ctx, int cx, int apexY, float tSec, ItemStack slotTarget) {
        float cp = FusionRitualDirector.climaxProgress();
        float ring = 22.0F + cp * 38.0F;
        FusionGuiSkin.drawRotatedSprite(ctx, FusionGuiSkin.VFX_SHOCKWAVE_RING, cx, apexY, ring, ring, cp * 2.4F);
        // The echo is visual only: never invent a book when the real output was taken
        // (or when the operation produced/modified a different item).
        if (!slotTarget.isEmpty()) {
            float bob = Mth.sin(tSec * 4.0F) * 2.0F;
            drawScaledStageItem(ctx, slotTarget, cx, apexY + bob, 1.15F - 0.12F * cp, 0.0F);
        }
    }

    private static void renderIdleLiveActors(
        GuiGraphicsExtractor ctx,
        int cx,
        int apexY,
        int altarY,
        float tSec,
        StationPreview preview,
        boolean gearMode,
        ItemStack slotA,
        ItemStack slotB,
        ItemStack slotCat,
        ItemStack slotTarget
    ) {
        if (gearMode && !slotTarget.isEmpty()) {
            // Host gear levitates above the central throne with live ◇ vessel gem indicators
            float bob = Mth.sin(tSec * 2.4F) * 2.0F;
            drawScaledStageItem(ctx, slotTarget, cx, apexY + 2.0F + bob, 1.08F, 0.0F);

            int maxSlots = VesselCapacityRules.getMaxVesselSlots(slotTarget);
            int usedSlots = slotTarget.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).size();
            for (int v = 0; v < maxSlots; v++) {
                int gx = cx - (maxSlots * 6) + v * 12 + 1;
                FusionGuiSkin.drawSprite(
                    ctx, v < usedSlots ? FusionGuiSkin.VESSEL_FILLED : FusionGuiSkin.VESSEL_EMPTY,
                    gx, altarY + 6, 10, 10
                );
            }

            // Orbiting donor books around the host gear
            if (!slotA.isEmpty()) {
                float a = tSec * 1.6F;
                drawScaledStageItem(ctx, slotA, cx + Mth.cos(a) * 23.0F, apexY + Mth.sin(a) * 10.0F, 0.72F, 0.0F);
            }
            if (!slotB.isEmpty()) {
                float a = tSec * 1.6F + Mth.PI;
                drawScaledStageItem(ctx, slotB, cx + Mth.cos(a) * 23.0F, apexY + Mth.sin(a) * 10.0F, 0.72F, 0.0F);
            }
        } else {
            // Book Synthesis Mode: orbiting A & B donor books + output/preview book
            if (!slotTarget.isEmpty()) {
                float bob = Mth.sin(tSec * 2.5F) * 2.0F;
                drawScaledStageItem(ctx, slotTarget, cx, apexY + bob, 1.04F, 0.0F);
            } else if (preview.executable() && preview.kind() == OperationKind.FUSE_BOOKS) {
                float pulse = 0.78F + 0.08F * Mth.sin(tSec * 3.6F);
                drawScaledStageItem(ctx, fusedBookDisplay(), cx, apexY - 2.0F, pulse, 0.0F);
            }

            if (!slotA.isEmpty() && !slotB.isEmpty()) {
                float a = tSec * 1.45F;
                drawScaledStageItem(ctx, slotA, cx + Mth.cos(a) * 22.0F, apexY + Mth.sin(a) * 10.0F, 0.80F, 0.0F);
                drawScaledStageItem(ctx, slotB, cx - Mth.cos(a) * 22.0F, apexY - Mth.sin(a) * 10.0F, 0.80F, 0.0F);
            } else if (!slotA.isEmpty()) {
                drawScaledStageItem(ctx, slotA, cx - 16.0F, apexY + Mth.sin(tSec * 2.2F) * 2.0F, 0.84F, 0.0F);
            } else if (!slotB.isEmpty()) {
                drawScaledStageItem(ctx, slotB, cx + 16.0F, apexY + Mth.cos(tSec * 2.2F) * 2.0F, 0.84F, 0.0F);
            }
        }

        if (!slotCat.isEmpty()) {
            drawScaledStageItem(ctx, slotCat, cx, altarY - 2.0F + Mth.sin(tSec * 3.0F) * 1.2F, 0.62F, 0.0F);
        }
    }

    private static void drawScaledStageItem(
        GuiGraphicsExtractor ctx,
        ItemStack stack,
        float centerX,
        float centerY,
        float scale,
        float angleRad
    ) {
        if (stack == null || stack.isEmpty() || scale <= 0.05F) {
            return;
        }
        ctx.pose().pushMatrix();
        ctx.pose().translate(centerX, centerY);
        if (Math.abs(angleRad) > 0.001F) {
            ctx.pose().rotate(angleRad);
        }
        ctx.pose().scale(scale, scale);
        ctx.item(stack, -8, -8);
        ctx.pose().popMatrix();
    }
}
