package enchantment_fusion.gui;

import enchantment_fusion.gui.FusionGuiSkin.HitBox;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TransmuteVariant;
import enchantment_fusion.mechanics.FusionStationOperations.EnchantCandidate;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * In-Panel Smart Synergy Codex Popover integrated cleanly within the {@code 304 x 218} GUI bounds
 * (never protruding past {@code 304px} into JEI / REI):
 * <ul>
 *   <li>Top-bar {@code [秘卷 N]} badge ({@code 253..277, 8..24}) displays live matching synergy count</li>
 *   <li>Clicking opens an obsidian popover card ({@code 94..298, 25..116}) over the center/right pane</li>
 *   <li>Clicking any recipe row auto-selects {@code [A ▷ B]} in the exact directional order and closes</li>
 * </ul>
 */
public final class FusionContextDrawer {
    public static final HitBox BADGE_BTN = new HitBox(253, 8, 24, 16);
    public static final HitBox POPOVER_BOX = new HitBox(94, 25, 204, 91);

    private static final int VISIBLE_ROWS = 3;
    private static final int ROW_H = 18;
    private static final int ROW_STEP = 19;

    public record ContextRecipe(
        boolean isSignature,
        String id,
        String primaryEnchantId,
        String secondaryEnchantId,
        int discoveryState,
        String title,
        String subtitle,
        Component hoverTitle,
        Component hoverDesc
    ) {}

    private boolean expanded = false;
    private int scrollOffset = 0;
    private String lastFingerprint = "";
    private final List<ContextRecipe> matched = new ArrayList<>();

    public boolean isExpanded() {
        return this.expanded;
    }

    public void close() {
        this.expanded = false;
    }

    public HitBox badgeBox(int leftPos, int topPos) {
        return new HitBox(leftPos + BADGE_BTN.x(), topPos + BADGE_BTN.y(), BADGE_BTN.w(), BADGE_BTN.h());
    }

    public HitBox popoverBox(int leftPos, int topPos) {
        return new HitBox(leftPos + POPOVER_BOX.x(), topPos + POPOVER_BOX.y(), POPOVER_BOX.w(), POPOVER_BOX.h());
    }

    public void rebuildIfChanged(
        List<EnchantCandidate> candidates,
        ItemStack hostStack,
        HolderLookup.Provider registries
    ) {
        StringBuilder fp = new StringBuilder();
        for (EnchantCandidate c : candidates) {
            fp.append(c.id()).append(':').append(c.level()).append(';');
        }
        TargetRole hostRole = CrossDomainTransmutations.classifyTarget(hostStack);
        fp.append('|').append(hostRole.name()).append(':').append(hostStack.isDamageableItem());
        String key = fp.toString();
        if (key.equals(this.lastFingerprint)) {
            return;
        }
        this.lastFingerprint = key;
        this.matched.clear();
        this.scrollOffset = 0;

        Set<String> presentIds = new HashSet<>();
        for (EnchantCandidate c : candidates) {
            presentIds.add(c.id());
        }
        if (presentIds.isEmpty()) {
            return;
        }

        for (SignatureEntry sig : SignaturePairs.allUniqueSignatures()) {
            boolean matchExact = presentIds.contains(sig.primaryId()) && presentIds.contains(sig.secondaryId());
            boolean matchWildcard = "*".equals(sig.primaryId()) && presentIds.contains(sig.secondaryId()) && presentIds.size() >= 2;
            if (!matchExact && !matchWildcard) {
                continue;
            }
            int state = CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.sigKey(sig.id()));
            String pName = "*".equals(sig.primaryId())
                ? "*"
                : FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.primaryId()).getString();
            String sName = FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.secondaryId()).getString();
            String formula = pName + " \u25B7 " + sName;
            String title = state >= CompendiumDiscoveryTracker.STATE_MASTERED
                ? "\u2605 " + Component.translatable(sig.titleKey()).getString()
                : "\u25C7 " + formula;
            Component hDesc = state >= CompendiumDiscoveryTracker.STATE_MASTERED
                ? Component.translatable(sig.descKey())
                : Component.translatable("gui.enchantment_fusion.grimoire.heard_sig_clue", pName, sName);
            this.matched.add(new ContextRecipe(
                true, sig.id(), sig.primaryId(), sig.secondaryId(), state,
                title, formula, Component.literal(title).withStyle(ChatFormatting.GOLD), hDesc
            ));
        }

        if (hostRole != TargetRole.OTHER) {
            boolean dmg = hostStack.isDamageableItem();
            for (String enchId : presentIds) {
                Optional<TransmuteVariant> tvOpt = CrossDomainTransmutations.resolveForRole(enchId, hostRole, dmg);
                if (tvOpt.isEmpty()) {
                    continue;
                }
                TransmuteVariant tv = tvOpt.get();
                int state = CompendiumDiscoveryTracker.clientState(CompendiumDiscoveryTracker.transmuteKey(tv.id()));
                String srcName = FusionTooltipRenderer.formatEnchantmentBaseName(registries, enchId).getString();
                String title = state >= CompendiumDiscoveryTracker.STATE_MASTERED
                    ? "\u2726 " + Component.translatable(tv.titleKey()).getString()
                    : "\u2727 " + srcName;
                Component hDesc = state >= CompendiumDiscoveryTracker.STATE_MASTERED
                    ? Component.translatable(tv.descKey())
                    : Component.translatable("gui.enchantment_fusion.grimoire.heard_transmute_clue", srcName);
                this.matched.add(new ContextRecipe(
                    false, tv.id(), enchId, "", state,
                    title, srcName, Component.literal(title).withStyle(ChatFormatting.AQUA), hDesc
                ));
            }
        }
    }

    public void renderBadge(GuiGraphicsExtractor ctx, Font font, int leftPos, int topPos, int mouseX, int mouseY) {
        HitBox badge = badgeBox(leftPos, topPos);
        boolean hov = badge.contains(mouseX, mouseY) || this.expanded;
        FusionGuiSkin.drawNineSlice(
            ctx,
            hov ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL,
            badge.x(), badge.y(), badge.w(), badge.h()
        );
        String badgeText = "\u2726" + this.matched.size();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, font, badgeText,
            badge.x() + 2, badge.w() - 4, badge.y() + 8.0F,
            0.58F,
            !this.matched.isEmpty() ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );
    }

    public void renderPopover(
        GuiGraphicsExtractor ctx,
        Font font,
        int leftPos,
        int topPos,
        int mouseX,
        int mouseY
    ) {
        if (!this.expanded) {
            return;
        }
        HitBox pop = popoverBox(leftPos, topPos);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_DRAWER_PANEL, pop.x(), pop.y(), pop.w(), pop.h());

        FusionGuiSkin.drawLeftBoundedText(
            ctx, font,
            Component.translatable("gui.enchantment_fusion.drawer.header", this.matched.size()).getString(),
            pop.x() + 7, pop.y() + 5, pop.w() - 62,
            0.58F, FusionGuiSkin.COLOR_GOLD
        );

        HitBox openFullBtn = new HitBox(pop.x() + pop.w() - 52, pop.y() + 3, 46, 13);
        FusionGuiSkin.drawButtonBox(
            ctx, font, 0, 0, openFullBtn,
            true, openFullBtn.contains(mouseX, mouseY),
            Component.translatable("gui.enchantment_fusion.drawer.open_full").getString(),
            0.52F, FusionGuiSkin.COLOR_CREAM
        );

        if (this.matched.isEmpty()) {
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, font,
                Component.translatable("gui.enchantment_fusion.grimoire.no_results").getString(),
                pop.x() + 8, pop.w() - 16, pop.y() + 48.0F,
                0.58F, FusionGuiSkin.COLOR_MUTED
            );
            return;
        }

        int maxScroll = Math.max(0, this.matched.size() - VISIBLE_ROWS);
        int clamped = Math.max(0, Math.min(this.scrollOffset, maxScroll));
        int listX = pop.x() + 6;
        int listY = pop.y() + 19;
        int rowW = pop.w() - 12;

        ctx.enableScissor(listX, listY, listX + rowW, listY + VISIBLE_ROWS * ROW_STEP);
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int idx = clamped + r;
            if (idx >= this.matched.size()) {
                break;
            }
            ContextRecipe rec = this.matched.get(idx);
            HitBox rowBox = new HitBox(listX, listY + r * ROW_STEP, rowW, ROW_H);
            boolean hovRow = rowBox.contains(mouseX, mouseY);
            FusionGuiSkin.drawNineSlice(
                ctx,
                hovRow ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED,
                rowBox.x(), rowBox.y(), rowBox.w(), rowBox.h()
            );
            int col = rec.isSignature() ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CYAN;
            FusionGuiSkin.drawLeftBoundedText(
                ctx, font, rec.title(),
                rowBox.x() + 5, rowBox.y() + 3, rowBox.w() - 10, 0.56F, col
            );
            FusionGuiSkin.drawLeftBoundedText(
                ctx, font, rec.subtitle(),
                rowBox.x() + 5, rowBox.y() + 10, rowBox.w() - 10, 0.50F, FusionGuiSkin.COLOR_MUTED
            );
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            HitBox upBtn = new HitBox(pop.x() + pop.w() - 36, pop.y() + pop.h() - 14, 14, 11);
            HitBox dnBtn = new HitBox(pop.x() + pop.w() - 20, pop.y() + pop.h() - 14, 14, 11);
            FusionGuiSkin.drawButtonBox(ctx, font, 0, 0, upBtn, clamped > 0, upBtn.contains(mouseX, mouseY), "\u25B2", 0.50F, FusionGuiSkin.COLOR_CREAM);
            FusionGuiSkin.drawButtonBox(ctx, font, 0, 0, dnBtn, clamped < maxScroll, dnBtn.contains(mouseX, mouseY), "\u25BC", 0.50F, FusionGuiSkin.COLOR_CREAM);
        }
    }

    public void appendHoverTooltip(
        List<Component> out,
        int leftPos,
        int topPos,
        int mouseX,
        int mouseY
    ) {
        if (badgeBox(leftPos, topPos).contains(mouseX, mouseY)) {
            out.add(Component.translatable("gui.enchantment_fusion.drawer.header", this.matched.size()).withStyle(ChatFormatting.GOLD));
            out.add(Component.translatable("gui.enchantment_fusion.drawer.click_select_tip").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!this.expanded) {
            return;
        }
        HitBox pop = popoverBox(leftPos, topPos);
        int maxScroll = Math.max(0, this.matched.size() - VISIBLE_ROWS);
        int clamped = Math.max(0, Math.min(this.scrollOffset, maxScroll));
        int listX = pop.x() + 6;
        int listY = pop.y() + 19;
        int rowW = pop.w() - 12;

        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int idx = clamped + r;
            if (idx >= this.matched.size()) {
                break;
            }
            HitBox rowBox = new HitBox(listX, listY + r * ROW_STEP, rowW, ROW_H);
            if (rowBox.contains(mouseX, mouseY)) {
                ContextRecipe rec = this.matched.get(idx);
                out.add(rec.hoverTitle());
                out.add(rec.hoverDesc().copy().withStyle(ChatFormatting.GRAY));
                out.add(Component.translatable("gui.enchantment_fusion.drawer.click_select_tip").withStyle(ChatFormatting.AQUA));
                return;
            }
        }
    }

    public boolean mouseClicked(
        double mx,
        double my,
        int leftPos,
        int topPos,
        List<EnchantCandidate> candidates,
        BiConsumer<Integer, Boolean> pickAction,
        Runnable setSingleModeTrue,
        Runnable setSingleModeFalse,
        Runnable openFullGrimoire
    ) {
        if (badgeBox(leftPos, topPos).contains(mx, my)) {
            this.expanded = !this.expanded;
            FusionGuiMicroEffects.playUiSound(SoundEvents.BOOK_PAGE_TURN, this.expanded ? 1.12F : 0.92F, 0.72F);
            return true;
        }
        if (!this.expanded) {
            return false;
        }
        HitBox pop = popoverBox(leftPos, topPos);
        if (!pop.contains(mx, my)) {
            this.expanded = false;
            return false;
        }

        HitBox openFullBtn = new HitBox(pop.x() + pop.w() - 52, pop.y() + 3, 46, 13);
        if (openFullBtn.contains(mx, my)) {
            this.expanded = false;
            openFullGrimoire.run();
            return true;
        }

        int maxScroll = Math.max(0, this.matched.size() - VISIBLE_ROWS);
        int clamped = Math.max(0, Math.min(this.scrollOffset, maxScroll));
        if (maxScroll > 0) {
            HitBox upBtn = new HitBox(pop.x() + pop.w() - 36, pop.y() + pop.h() - 14, 14, 11);
            HitBox dnBtn = new HitBox(pop.x() + pop.w() - 20, pop.y() + pop.h() - 14, 14, 11);
            if (upBtn.contains(mx, my)) {
                this.scrollOffset = Math.max(0, clamped - 1);
                FusionGuiMicroEffects.playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.5F);
                return true;
            }
            if (dnBtn.contains(mx, my)) {
                this.scrollOffset = Math.min(maxScroll, clamped + 1);
                FusionGuiMicroEffects.playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.5F);
                return true;
            }
        }

        int listX = pop.x() + 6;
        int listY = pop.y() + 19;
        int rowW = pop.w() - 12;
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int idx = clamped + r;
            if (idx >= this.matched.size()) {
                break;
            }
            HitBox rowBox = new HitBox(listX, listY + r * ROW_STEP, rowW, ROW_H);
            if (rowBox.contains(mx, my)) {
                autoSelectRecipeCandidates(
                    this.matched.get(idx), candidates, pickAction, setSingleModeTrue, setSingleModeFalse
                );
                this.expanded = false;
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.32F, 0.72F);
                return true;
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double scrollY, int leftPos, int topPos) {
        if (!this.expanded || !popoverBox(leftPos, topPos).contains(mx, my)) {
            return false;
        }
        int maxScroll = Math.max(0, this.matched.size() - VISIBLE_ROWS);
        if (maxScroll <= 0) {
            return true;
        }
        int delta = scrollY > 0 ? -1 : 1;
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset + delta));
        return true;
    }

    private static void autoSelectRecipeCandidates(
        ContextRecipe rec,
        List<EnchantCandidate> candidates,
        BiConsumer<Integer, Boolean> pickAction,
        Runnable setSingleModeTrue,
        Runnable setSingleModeFalse
    ) {
        if (!rec.isSignature()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (rec.primaryEnchantId().equals(candidates.get(i).id())) {
                    setSingleModeTrue.run();
                    pickAction.accept(i, false);
                    return;
                }
            }
            return;
        }

        int idxB = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (rec.secondaryEnchantId().equals(candidates.get(i).id())) {
                idxB = i;
                break;
            }
        }
        int idxA = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (i == idxB) {
                continue;
            }
            if ("*".equals(rec.primaryEnchantId()) || rec.primaryEnchantId().equals(candidates.get(i).id())) {
                idxA = i;
                break;
            }
        }
        if (idxA >= 0 && idxB >= 0) {
            setSingleModeFalse.run();
            pickAction.accept(idxA, false);
            pickAction.accept(idxB, true);
        }
    }
}
