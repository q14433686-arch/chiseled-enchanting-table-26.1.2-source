package enchantment_fusion.gui;

import enchantment_fusion.gui.FusionGuiSkin.HitBox;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.FusionStationOperations.EnchantCandidate;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Slide-out Contextual Synergy Drawer attached to the right wing of the Fusion Altar:
 * <ul>
 *   <li>Automatically filters Signature Resonances and Cross-Domain Transmutations matching
 *       the enchantments currently placed in the altar slots</li>
 *   <li>One-click directional auto-selection ({@code [A ▷ B]}) when matching candidates are present</li>
 *   <li>Seamless footer button to expand the full double-page Obsidian Star-Codex Grimoire</li>
 * </ul>
 */
public final class FusionContextDrawer {
    public static final int TAB_W = 24;
    public static final int TAB_H = 44;
    public static final int DRAWER_W = 142;
    public static final int DRAWER_H = 118;
    private static final int VISIBLE_ROWS = 4;

    public record DrawerRecipe(
        String id,
        boolean isTransmute,
        String primaryId,
        String secondaryId,
        int stage,
        String label,
        String formula,
        String summary,
        boolean readyToAutoPick
    ) {}

    private boolean expanded = false;
    private boolean showAllLore = false;
    private int scroll = 0;

    public boolean isExpanded() {
        return expanded;
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
        this.scroll = 0;
        FusionGuiMicroEffects.playUiSound(SoundEvents.BOOK_PAGE_TURN, expanded ? 1.12F : 0.94F, 0.75F);
    }

    public HitBox pullTabBox(int guiWidth) {
        return new HitBox(guiWidth - 2, 16, TAB_W, TAB_H);
    }

    public HitBox drawerBounds(int leftPos, int topPos, int guiWidth, int screenWidth) {
        int idealX = leftPos + guiWidth + 2;
        if (idealX + DRAWER_W > screenWidth - 4) {
            // Graceful fallback on very narrow windows: overlay neatly over right wing edge
            idealX = Math.max(4, screenWidth - DRAWER_W - 4);
        }
        return new HitBox(idealX, topPos + 6, DRAWER_W, DRAWER_H);
    }

    public List<DrawerRecipe> collectRecipes(
        List<EnchantCandidate> candidates,
        HolderLookup.Provider registries,
        boolean forceRelatedOnly
    ) {
        Set<String> presentIds = new HashSet<>();
        for (EnchantCandidate c : candidates) {
            presentIds.add(c.id());
        }
        boolean filterRelated = forceRelatedOnly || (!showAllLore && !presentIds.isEmpty());
        List<DrawerRecipe> out = new ArrayList<>();

        for (SignatureEntry sig : SignaturePairs.allOrderedEntries()) {
            boolean matchesA = "*".equals(sig.primaryId()) ? !presentIds.isEmpty() : presentIds.contains(sig.primaryId());
            boolean matchesB = presentIds.contains(sig.secondaryId());
            if (filterRelated && !matchesA && !matchesB) {
                continue;
            }
            int stage = "*".equals(sig.primaryId())
                ? Math.max(CompendiumDiscoveryTracker.STATE_HEARD, CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.sigKey(sig.id())))
                : Math.max(
                    CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.sigKey(sig.id())),
                    CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.pairKey(sig.primaryId(), sig.secondaryId()))
                );
            if (!filterRelated && stage < CompendiumDiscoveryTracker.STATE_HEARD) {
                continue;
            }

            String pName = "*".equals(sig.primaryId())
                ? "*"
                : FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.primaryId()).getString();
            String sName = FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.secondaryId()).getString();
            String formula = pName + " \u25B7 " + sName;
            String title = stage >= CompendiumDiscoveryTracker.STATE_MASTERED
                ? "\u2605 " + Component.translatable(sig.titleKey()).getString()
                : "\u25C7 " + formula;
            String desc = stage >= CompendiumDiscoveryTracker.STATE_MASTERED
                ? Component.translatable(sig.descKey()).getString()
                : Component.translatable("gui.enchantment_fusion.grimoire.heard_sig_clue", pName, sName).getString();

            boolean bothLoaded = matchesA && matchesB && candidates.size() >= 2;
            out.add(new DrawerRecipe(sig.id(), false, sig.primaryId(), sig.secondaryId(), stage, title, formula, desc, bothLoaded));
        }

        for (TransmuteVariant tv : CrossDomainTransmutations.allVariants()) {
            boolean matches = presentIds.contains(tv.sourceEnchantId());
            if (filterRelated && !matches) {
                continue;
            }
            int stage = CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.transmuteKey(tv.id()));
            if (!filterRelated && stage < CompendiumDiscoveryTracker.STATE_HEARD) {
                continue;
            }
            String srcName = FusionTooltipRenderer.formatEnchantmentBaseName(registries, tv.sourceEnchantId()).getString();
            String fullTitle = Component.translatable(tv.titleKey()).getString();
            String label = stage >= CompendiumDiscoveryTracker.STATE_MASTERED
                ? "\u25C6 " + fullTitle
                : "\u25C7 " + srcName;
            String desc = stage >= CompendiumDiscoveryTracker.STATE_MASTERED
                ? Component.translatable(tv.descKey()).getString()
                : Component.translatable("gui.enchantment_fusion.grimoire.heard_transmute_clue", srcName).getString();
            out.add(new DrawerRecipe(tv.id(), true, tv.sourceEnchantId(), "", stage, label, srcName + " \u2192 0\u25C7", desc, matches));
        }

        out.sort((a, b) -> Boolean.compare(b.readyToAutoPick(), a.readyToAutoPick()));
        return out;
    }

    public void render(
        GuiGraphicsExtractor ctx,
        Font font,
        int leftPos,
        int topPos,
        int guiWidth,
        int screenWidth,
        List<EnchantCandidate> candidates,
        HolderLookup.Provider registries,
        int mouseX,
        int mouseY
    ) {
        List<DrawerRecipe> relatedOnly = collectRecipes(candidates, registries, true);
        HitBox tab = pullTabBox(guiWidth);
        int tx = leftPos + tab.x();
        int ty = topPos + tab.y();
        boolean overTab = tab.containsAt(leftPos, topPos, mouseX, mouseY);

        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.DRAWER_TAB_BADGE, tx, ty, tab.w(), tab.h());
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, font,
            Component.translatable("gui.enchantment_fusion.drawer.tab_label").getString(),
            tx + 2, tab.w() - 4, ty + 13.0F,
            0.54F, overTab || expanded ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_AMETHYST
        );
        String badge = String.valueOf(relatedOnly.size());
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, font, badge,
            tx + 2, tab.w() - 4, ty + 29.0F,
            0.62F, !relatedOnly.isEmpty() ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_MUTED
        );

        if (!expanded) {
            return;
        }

        HitBox box = drawerBounds(leftPos, topPos, guiWidth, screenWidth);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.DRAWER_PANEL, box.x(), box.y(), box.w(), box.h(), 48, 48, 6);

        List<DrawerRecipe> list = collectRecipes(candidates, registries, false);
        HitBox filterChip = new HitBox(box.x() + 6, box.y() + 6, box.w() - 12, 14);
        boolean useRelated = !showAllLore && !candidates.isEmpty();
        String filterText = Component.translatable(
            useRelated ? "gui.enchantment_fusion.drawer.filter_related" : "gui.enchantment_fusion.drawer.filter_all",
            list.size()
        ).getString();
        FusionGuiSkin.drawButtonBox(
            ctx, font, 0, 0, filterChip,
            true, filterChip.contains(mouseX, mouseY),
            filterText, 0.54F, useRelated ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_GOLD
        );

        ctx.enableScissor(box.x() + 5, box.y() + 22, box.x() + box.w() - 5, box.y() + 96);
        try {
            if (list.isEmpty()) {
                FusionGuiSkin.drawCenteredBoundedText(
                    ctx, font,
                    Component.translatable("gui.enchantment_fusion.grimoire.no_results").getString(),
                    box.x() + 8, box.w() - 16, box.y() + 56.0F,
                    0.54F, FusionGuiSkin.COLOR_MUTED
                );
            } else {
                int maxScroll = Math.max(0, list.size() - VISIBLE_ROWS);
                scroll = Math.max(0, Math.min(scroll, maxScroll));
                for (int r = 0; r < VISIBLE_ROWS; r++) {
                    int idx = scroll + r;
                    if (idx >= list.size()) break;
                    DrawerRecipe rec = list.get(idx);
                    HitBox rb = new HitBox(box.x() + 6, box.y() + 23 + r * 18, box.w() - 12, 17);
                    boolean hov = rb.contains(mouseX, mouseY);
                    var sprite = rec.readyToAutoPick()
                        ? FusionGuiSkin.ROW_SELECTED_A
                        : (hov ? FusionGuiSkin.ROW_HOVER : FusionGuiSkin.ROW_UNSELECTED);
                    FusionGuiSkin.drawNineSlice(ctx, sprite, rb.x(), rb.y(), rb.w(), rb.h(), 64, 18, 4);
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, font, rec.label(),
                        rb.x() + 5, rb.y() + 5, rb.w() - 10, 0.56F,
                        rec.readyToAutoPick() ? FusionGuiSkin.COLOR_GOLD : (rec.isTransmute() ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_CREAM)
                    );
                }
            }
        } finally {
            ctx.disableScissor();
        }

        HitBox openFullBtn = new HitBox(box.x() + 6, box.y() + 98, box.w() - 12, 15);
        FusionGuiSkin.drawButtonBox(
            ctx, font, 0, 0, openFullBtn,
            true, openFullBtn.contains(mouseX, mouseY),
            Component.translatable("gui.enchantment_fusion.drawer.open_full").getString(),
            0.52F, FusionGuiSkin.COLOR_AMETHYST
        );
    }

    public void appendHoverTooltip(
        List<Component> lines,
        int leftPos,
        int topPos,
        int guiWidth,
        int screenWidth,
        List<EnchantCandidate> candidates,
        HolderLookup.Provider registries,
        int mouseX,
        int mouseY
    ) {
        if (pullTabBox(guiWidth).containsAt(leftPos, topPos, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.grimoire_btn").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (!expanded) {
            return;
        }
        HitBox box = drawerBounds(leftPos, topPos, guiWidth, screenWidth);
        List<DrawerRecipe> list = collectRecipes(candidates, registries, false);
        int maxScroll = Math.max(0, list.size() - VISIBLE_ROWS);
        int curScroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int idx = curScroll + r;
            if (idx >= list.size()) break;
            HitBox rb = new HitBox(box.x() + 6, box.y() + 23 + r * 18, box.w() - 12, 17);
            if (rb.contains(mouseX, mouseY)) {
                DrawerRecipe rec = list.get(idx);
                lines.add(Component.literal(rec.label()).withStyle(ChatFormatting.GOLD));
                lines.add(Component.literal(rec.formula()).withStyle(ChatFormatting.AQUA));
                lines.add(Component.literal(rec.summary()).withStyle(ChatFormatting.GRAY));
                return;
            }
        }
    }

    public boolean mouseScrolled(
        int leftPos,
        int topPos,
        int guiWidth,
        int screenWidth,
        List<EnchantCandidate> candidates,
        HolderLookup.Provider registries,
        double mouseX,
        double mouseY,
        int delta
    ) {
        if (!expanded) {
            return false;
        }
        HitBox box = drawerBounds(leftPos, topPos, guiWidth, screenWidth);
        if (!box.contains(mouseX, mouseY)) {
            return false;
        }
        List<DrawerRecipe> list = collectRecipes(candidates, registries, false);
        int max = Math.max(0, list.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(max, scroll + delta));
        return true;
    }

    public boolean mouseClicked(
        int leftPos,
        int topPos,
        int guiWidth,
        int screenWidth,
        List<EnchantCandidate> candidates,
        HolderLookup.Provider registries,
        double mx,
        double my,
        Runnable onOpenFullGrimoire,
        BiConsumer<EnchantCandidate, Boolean> onPickCandidate
    ) {
        if (pullTabBox(guiWidth).containsAt(leftPos, topPos, mx, my)) {
            toggleExpanded();
            return true;
        }
        if (!expanded) {
            return false;
        }
        HitBox box = drawerBounds(leftPos, topPos, guiWidth, screenWidth);
        if (!box.contains(mx, my)) {
            return false;
        }

        HitBox filterChip = new HitBox(box.x() + 6, box.y() + 6, box.w() - 12, 14);
        if (filterChip.contains(mx, my)) {
            this.showAllLore = !this.showAllLore;
            this.scroll = 0;
            FusionGuiMicroEffects.playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.1F, 0.5F);
            return true;
        }

        HitBox openFullBtn = new HitBox(box.x() + 6, box.y() + 98, box.w() - 12, 15);
        if (openFullBtn.contains(mx, my)) {
            onOpenFullGrimoire.run();
            return true;
        }

        List<DrawerRecipe> list = collectRecipes(candidates, registries, false);
        int maxScroll = Math.max(0, list.size() - VISIBLE_ROWS);
        int curScroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int idx = curScroll + r;
            if (idx >= list.size()) break;
            HitBox rb = new HitBox(box.x() + 6, box.y() + 23 + r * 18, box.w() - 12, 17);
            if (rb.contains(mx, my)) {
                DrawerRecipe rec = list.get(idx);
                autoSelectRecipeCandidates(rec, candidates, onPickCandidate);
                FusionGuiMicroEffects.triggerCandidatePickSpark((int) mx, (int) my, true);
                return true;
            }
        }
        return true;
    }

    private void autoSelectRecipeCandidates(
        DrawerRecipe rec,
        List<EnchantCandidate> candidates,
        BiConsumer<EnchantCandidate, Boolean> onPickCandidate
    ) {
        EnchantCandidate matchA = null;
        EnchantCandidate matchB = null;
        if ("*".equals(rec.primaryId()) && !rec.secondaryId().isEmpty()) {
            for (EnchantCandidate c : candidates) {
                if (matchB == null && c.id().equals(rec.secondaryId())) {
                    matchB = c;
                    break;
                }
            }
            for (EnchantCandidate c : candidates) {
                if (c != matchB) {
                    matchA = c;
                    break;
                }
            }
        } else {
            for (EnchantCandidate c : candidates) {
                if (matchA == null && c.id().equals(rec.primaryId())) {
                    matchA = c;
                } else if (matchB == null && !rec.secondaryId().isEmpty() && c.id().equals(rec.secondaryId())) {
                    matchB = c;
                }
            }
        }
        if (matchA != null) {
            onPickCandidate.accept(matchA, true);
        }
        if (matchB != null) {
            onPickCandidate.accept(matchB, false);
        }
    }
}
