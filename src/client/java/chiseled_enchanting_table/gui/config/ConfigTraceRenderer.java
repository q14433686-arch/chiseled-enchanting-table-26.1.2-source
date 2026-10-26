package chiseled_enchanting_table.gui.config;

import static chiseled_enchanting_table.gui.config.ConfigPanelPainter.*;

import chiseled_enchanting_table.config.ConfigCatalog;
import chiseled_enchanting_table.config.ConfigCatalog.RuleTraceEntry;
import chiseled_enchanting_table.config.ConfigLayoutSpec;
import chiseled_enchanting_table.config.ConfigLayoutSpec.Box;
import chiseled_enchanting_table.config.ConfigLayoutSpec.TraceCardLayout;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static chiseled_enchanting_table.gui.config.ConfigSkin.*;
import static chiseled_enchanting_table.gui.config.ConfigInteractions.*;

/** ConfigTraceRenderer owns view composition and registers actions via a narrow editor surface. */
final class ConfigTraceRenderer {
    private ConfigTraceRenderer() {}
    static List<Component> renderTraceBanner(ConfigViewContext context,
        GuiGraphicsExtractor ctx,
        int x,
        int y,
        int w,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) {
        Box banner = new Box(x, y, w, ConfigLayoutSpec.TRACE_BANNER_H);
        blitNineSliceRect(ctx, ROW_PLAQUE_ATOM, x, y, w, banner.h(), 16, 6, 96, 24);

        int embSize = 32;
        int embX = x + 10;
        int embY = Math.round(banner.centerY() - embSize * 0.5f);
        ctx.blit(EMBLEM_TRACE_LARGE, embX, embY, embX + embSize, embY + embSize, 0.0f, 1.0f, 0.0f, 1.0f);

        int btnW = Math.min(76, Math.max(54, w * 21 / 100));
        int btnH = 16;
        Box btnClassic = new Box(x + w - 10 - btnW, y + 5, btnW, btnH);
        Box btnModern = new Box(x + w - 10 - btnW, y + banner.h() - 5 - btnH, btnW, btnH);

        int textLeft = embX + embSize + 6;
        int textW = Math.max(40, btnClassic.x() - 6 - textLeft);
        Box titleBox = new Box(textLeft, y + 5, textW, 15);
        Box subBox = new Box(textLeft, y + 22, textW, 15);

        int activeClassic = context.draft().build().countClassic103Atoms();
        drawClampedLeftText(context.font(),
            ctx,
            I18n.get("config.chiseled_enchanting_table.trace.banner_title"),
            titleBox,
            ConfigLayoutSpec.PRIMARY_TEXT_SCALE,
            ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
            GOLD_TEXT
        );
        drawClampedLeftText(context.font(),
            ctx,
            I18n.get("config.chiseled_enchanting_table.trace.banner_ratio",
                activeClassic, ModRuntimeConfig.CLASSIC_RULE_ATOM_COUNT),
            subBox,
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            activeClassic == ModRuntimeConfig.CLASSIC_RULE_ATOM_COUNT ? GOLD_TEXT : CYAN_TEXT
        );

        boolean writable = context.mode().writable();
        boolean inClip = mouseY >= clipTop && mouseY < clipBottom;

        // Quick-apply all 19 classic atoms
        boolean overClassic = inClip && writable && btnClassic.contains(mouseX, mouseY);
        blitNineSliceRect(ctx, !writable ? BTN_PLAQUE_DISABLED : (overClassic ? BTN_PLAQUE_HOVER : BTN_PLAQUE_GOLD),
            btnClassic.x(), btnClassic.y(), btnClassic.w(), btnClassic.h(), 4, 4, 64, 18);
        drawClampedCenteredText(context.font(),
            ctx,
            I18n.get("config.chiseled_enchanting_table.trace.btn_all_103"),
            new Box(btnClassic.x() + 3, btnClassic.y(), Math.max(12, btnClassic.w() - 6), btnClassic.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            writable ? GOLD_TEXT : DIM_TEXT
        );

        // Quick-restore all 19 modern 1.1.0 atoms
        boolean overModern = inClip && writable && btnModern.contains(mouseX, mouseY);
        blitNineSliceRect(ctx, !writable ? BTN_PLAQUE_DISABLED : (overModern ? BTN_PLAQUE_HOVER : BTN_PLAQUE),
            btnModern.x(), btnModern.y(), btnModern.w(), btnModern.h(), 4, 4, 64, 18);
        drawClampedCenteredText(context.font(),
            ctx,
            I18n.get("config.chiseled_enchanting_table.trace.btn_all_110"),
            new Box(btnModern.x() + 3, btnModern.y(), Math.max(12, btnModern.w() - 6), btnModern.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            writable ? EMERALD_TEXT : DIM_TEXT
        );

        if (writable) {
            int cTop1 = Math.max(clipTop, btnClassic.y());
            int cBot1 = Math.min(clipBottom, btnClassic.bottom());
            if (cBot1 > cTop1) {
                context.registerHit(new HitBox(new Box(btnClassic.x(), cTop1, btnClassic.w(), cBot1 - cTop1), () -> {
                    ModRuntimeConfig.Preset.CLASSIC.applyTo(context.draft());
                    context.changed();
                    context.sound(1.06f);
                }, null));
            }
            int cTop2 = Math.max(clipTop, btnModern.y());
            int cBot2 = Math.min(clipBottom, btnModern.bottom());
            if (cBot2 > cTop2) {
                context.registerHit(new HitBox(new Box(btnModern.x(), cTop2, btnModern.w(), cBot2 - cTop2), () -> {
                    ModRuntimeConfig.Preset.STANDARD.applyTo(context.draft());
                    context.changed();
                    context.sound(1.04f);
                }, null));
            }
        }
        return null;
    }

    static boolean isAtomInClassicState(ConfigViewContext context, RuleTraceEntry trace) {
        return trace.isUsingClassic103(context.draft());
    }

    static void toggleTraceAtom(ConfigViewContext context, RuleTraceEntry trace) { chiseled_enchanting_table.config.ConfigTraceActions.toggle(context.draft(), trace); context.changed(); }

    static List<Component> renderSingleTraceCard(ConfigViewContext context,
        GuiGraphicsExtractor ctx,
        int ordinal,
        RuleTraceEntry trace,
        int x,
        int y,
        int w,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) {
        TraceCardLayout tc = ConfigLayoutSpec.computeTraceCard(x, y, w);
        boolean inClip = mouseY >= clipTop && mouseY < clipBottom;
        boolean hovered = inClip && tc.card().contains(mouseX, mouseY);
        boolean isClassic = isAtomInClassicState(context, trace);

        blitNineSliceRect(
            ctx,
            hovered ? ROW_PLAQUE_HOVER : (isClassic ? ROW_PLAQUE_ATOM : ROW_PLAQUE),
            x, y, w, tc.card().h(), 16, 6, 96, 24
        );

        // Top-left: #NN + Atom Name
        String title = String.format(Locale.ROOT, "#%02d ", ordinal) + I18n.get(trace.titleKey());
        drawClampedLeftText(context.font(),
            ctx,
            title,
            tc.titleBox(),
            ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
            isClassic ? GOLD_TEXT : CREAM_TEXT
        );

        // Top-middle: Domain Jump Chip ("[↗ Pricing]")
        Box jb = tc.domainJumpBox();
        boolean overJump = inClip && jb.contains(mouseX, mouseY);
        ctx.fill(jb.x(), jb.y(), jb.right(), jb.bottom(), overJump ? 0xCC4B3475 : 0x88241838);
        String jumpText = "\u2197 " + I18n.get(trace.domain().titleKey());
        drawClampedCenteredText(context.font(),
            ctx,
            jumpText,
            new Box(jb.x() + 2, jb.y(), Math.max(12, jb.w() - 4), jb.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            overJump ? GOLD_TEXT : CYAN_TEXT
        );
        int jTop = Math.max(clipTop, jb.y());
        int jBot = Math.min(clipBottom, jb.bottom());
        if (jBot > jTop) {
            context.registerHit(new HitBox(new Box(jb.x(), jTop, jb.w(), jBot - jTop), () -> {
                context.navigate(trace.domain());
                context.sound(1.0f);
            }, null));
        }

        // Top-right: Direct Atom Toggle or [Derived] badge
        Box tb = tc.toggleActionBox();
        boolean canToggle = !trace.derived()
            && trace.optionKey() != null
            && ConfigCatalog.byKey(trace.optionKey())
                .map(o -> o.evaluate(context.draft(), context.mode()).editable())
                .orElse(false);
        boolean overToggle = inClip && canToggle && tb.contains(mouseX, mouseY);
        Identifier tSprite = trace.derived()
            ? BTN_PLAQUE_DISABLED
            : (!canToggle ? BTN_PLAQUE_DISABLED : (overToggle ? BTN_PLAQUE_HOVER : (isClassic ? BTN_PLAQUE_GOLD : BTN_PLAQUE)));
        blitNineSliceRect(ctx, tSprite, tb.x(), tb.y(), tb.w(), tb.h(), 4, 4, 64, 18);
        String tLabel = trace.derived()
            ? I18n.get("config.chiseled_enchanting_table.trace.derived_badge")
            : I18n.get(isClassic
                ? "config.chiseled_enchanting_table.trace.toggle_classic"
                : "config.chiseled_enchanting_table.trace.toggle_modern");
        drawClampedCenteredText(context.font(),
            ctx,
            tLabel,
            new Box(tb.x() + 3, tb.y(), Math.max(12, tb.w() - 6), tb.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            trace.derived() ? MUTED_TEXT : (isClassic ? GOLD_TEXT : EMERALD_TEXT)
        );
        if (canToggle) {
            int tTop = Math.max(clipTop, tb.y());
            int tBot = Math.min(clipBottom, tb.bottom());
            if (tBot > tTop) {
                context.registerHit(new HitBox(new Box(tb.x(), tTop, tb.w(), tBot - tTop), () -> {
                    toggleTraceAtom(context, trace);
                    context.sound(1.04f);
                }, null));
            }
        }

        // Bottom comparison pills: localized Classic vs Modern prefixes
        Box cPill = tc.classicPillBox();
        Box mPill = tc.modernPillBox();
        ctx.fill(cPill.x(), cPill.y(), cPill.right(), cPill.bottom(), isClassic ? 0xCC4A3316 : 0xAA120C20);
        ctx.fill(mPill.x(), mPill.y(), mPill.right(), mPill.bottom(), !isClassic ? 0xCC163D2E : 0xAA120C20);

        String classicPrefix = I18n.get("config.chiseled_enchanting_table.trace.prefix_classic");
        String modernPrefix = I18n.get("config.chiseled_enchanting_table.trace.prefix_modern");
        String cText = (isClassic ? "\u25cf " : "") + classicPrefix + " " + I18n.get(trace.classicSummaryKey());
        String mText = (!isClassic ? "\u25cf " : "") + modernPrefix + " " + I18n.get(trace.modernSummaryKey());
        drawClampedLeftText(context.font(),
            ctx,
            cText,
            new Box(cPill.x() + 3, cPill.y(), Math.max(16, cPill.w() - 6), cPill.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            isClassic ? GOLD_TEXT : 0xFFB8AAD2
        );
        drawClampedLeftText(context.font(),
            ctx,
            mText,
            new Box(mPill.x() + 3, mPill.y(), Math.max(16, mPill.w() - 6), mPill.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            !isClassic ? EMERALD_TEXT : 0xFFB8AAD2
        );

        if (hovered) {
            List<Component> cardTip = new ArrayList<>();
            cardTip.add(Component.translatable(trace.titleKey()).withStyle(ChatFormatting.GOLD));
            cardTip.add(Component.translatable("config.chiseled_enchanting_table.trace.prefix_classic")
                .append(Component.literal(" "))
                .append(Component.translatable(trace.classicSummaryKey()))
                .withStyle(isClassic ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            cardTip.add(Component.translatable("config.chiseled_enchanting_table.trace.prefix_modern")
                .append(Component.literal(" "))
                .append(Component.translatable(trace.modernSummaryKey()))
                .withStyle(!isClassic ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            cardTip.add(Component.translatable(trace.impactKey()).withStyle(ChatFormatting.AQUA));
            return cardTip;
        }
        return null;
    }
}
