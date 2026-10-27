package chiseled_enchanting_table.gui.config;

import static chiseled_enchanting_table.gui.config.ConfigPanelPainter.*;

import chiseled_enchanting_table.config.ConfigCatalog.ControlKind;
import chiseled_enchanting_table.config.ConfigCatalog.OptionAvailability;
import chiseled_enchanting_table.config.ConfigCatalog.OptionEntry;
import chiseled_enchanting_table.config.ConfigLayoutSpec;
import chiseled_enchanting_table.config.ConfigLayoutSpec.Box;
import chiseled_enchanting_table.config.ConfigLayoutSpec.OptionRowLayout;
import chiseled_enchanting_table.config.ConfigLayoutSpec.SliderControlLayout;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.config.ModRuntimeConfig.Draft;
import java.util.ArrayList;
import java.util.List;
import chiseled_enchanting_table.compat.CetFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import static chiseled_enchanting_table.gui.config.ConfigSkin.*;
import static chiseled_enchanting_table.gui.config.ConfigInteractions.*;

/** ConfigOptionRenderer owns view composition and registers actions via a narrow editor surface. */
final class ConfigOptionRenderer {
    private ConfigOptionRenderer() {}
    static List<Component> renderSingleOptionRow(ConfigViewContext context,
        GuiGraphicsExtractor ctx,
        OptionEntry entry,
        int flatIndex,
        int x,
        int y,
        int w,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) {
        OptionRowLayout row = ConfigLayoutSpec.computeOptionRow(x, y, w);
        OptionAvailability avail = entry.evaluate(context.draft(), context.mode());
        boolean editable = avail.editable();
        boolean inClip = mouseY >= clipTop && mouseY < clipBottom;
        boolean hovered = inClip && row.plaque().contains(mouseX, mouseY);
        boolean focused = (flatIndex == context.focusedIndex());

        Identifier plaqueSprite = !editable
            ? ROW_PLAQUE_DISABLED
            : (hovered ? ROW_PLAQUE_HOVER : (entry.isCoreRuleAtom() ? ROW_PLAQUE_ATOM : ROW_PLAQUE));
        blitNineSliceRect(ctx, plaqueSprite, x, y, w, row.plaque().h(), 16, 6, 96, 24);

        if (focused) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + 2, GOLD_TEXT);
            ctx.fill(x + 1, y + row.plaque().h() - 2, x + w - 1, y + row.plaque().h() - 1, GOLD_TEXT);
        }

        // 1. Modified indicator pip
        if (avail.modifiedFromDefault()) {
            Box pip = row.modifiedPip();
            int cx = Math.round(pip.centerX());
            int cy = Math.round(pip.centerY());
            ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, GOLD_TEXT);
        } else if (entry.isCoreRuleAtom()) {
            Box pip = row.modifiedPip();
            int cx = Math.round(pip.centerX());
            int cy = Math.round(pip.centerY());
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, editable ? 0xFFB995FF : DIM_TEXT);
        }

        // 2. Option Title (strictly >= 0.85f)
        String name = I18n.get(entry.nameKey());
        int nameColor = !editable
            ? MUTED_TEXT
            : (entry.isCoreRuleAtom() ? GOLD_TEXT : CREAM_TEXT);
        drawClampedLeftText(context.font(),
            ctx,
            name,
            row.labelBox(),
            ConfigLayoutSpec.PRIMARY_TEXT_SCALE,
            ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE,
            nameColor
        );

        // 3. Dual-coded Scope + Lock Glyph Chip (strictly >= 0.75f, BMP-safe geometric glyphs)
        Box sb = row.scopeBadgeBox();
        String scopeGlyph = (!editable ? "🔒" : "") + avail.effectScope().glyph();
        int scopeColor = switch (avail.effectScope()) {
            case IMMEDIATE -> EMERALD_TEXT;
            case NEW_GENERATION -> CYAN_TEXT;
            case RESTART_REQUIRED -> AMBER_TEXT;
        };
        if (!editable) scopeColor = MUTED_TEXT;
        ctx.fill(sb.x(), sb.y(), sb.right(), sb.bottom(), 0x88140D22);
        drawClampedCenteredText(context.font(),
            ctx,
            scopeGlyph,
            sb,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            scopeColor
        );

        // 4. Interactive Control (Boolean Toggle / Enum Plaque / Slider)
        renderOptionControl(context, ctx, entry, row.controlBox(), editable, inClip, mouseX, mouseY, clipTop, clipBottom);

        // 5. Reset-to-default micro-button + crisp pixel-art reset glyph
        Box rb = row.resetBox();
        boolean canReset = editable && avail.modifiedFromDefault();
        boolean overReset = inClip && canReset && rb.contains(mouseX, mouseY);
        blitNineSliceRect(
            ctx,
            canReset ? (overReset ? MICRO_BTN_HOVER : MICRO_BTN) : MICRO_BTN_DISABLED,
            rb.x(), rb.y(), rb.w(), rb.h(), 3, 3, 16, 16
        );
        int iconSz = Math.min(10, Math.min(rb.w() - 4, rb.h() - 4));
        int iconX = Math.round(rb.centerX() - iconSz * 0.5f);
        int iconY = Math.round(rb.centerY() - iconSz * 0.5f);
        ctx.blit(
            canReset ? ICON_RESET : ICON_RESET_DISABLED,
            iconX, iconY, iconX + iconSz, iconY + iconSz,
            0.0f, 1.0f, 0.0f, 1.0f
        );
        if (canReset) {
            int cTop = Math.max(clipTop, rb.y());
            int cBot = Math.min(clipBottom, rb.bottom());
            if (cBot > cTop) {
                context.registerHit(new HitBox(new Box(rb.x(), cTop, rb.w(), cBot - cTop), () -> {
                    entry.resetToDefault(context.draft());
                    context.changed();
                    context.sound(0.92f);
                }, null));
            }
        }

        return hovered ? buildL3ProvenanceTooltip(context, entry, avail) : null;
    }

    static void renderOptionControl(ConfigViewContext context,
        GuiGraphicsExtractor ctx,
        OptionEntry entry,
        Box cb,
        boolean editable,
        boolean inClip,
        int mouseX,
        int mouseY,
        int clipTop,
        int clipBottom
    ) {
        Draft shown = context.displayed(entry);
        String valText = formatValueFrom(entry, shown);
        int cTop = Math.max(clipTop, cb.y());
        int cBot = Math.min(clipBottom, cb.bottom());
        boolean hitValid = cBot > cTop;

        if (entry.kind() == ControlKind.BOOLEAN) {
            boolean isOn = Boolean.TRUE.equals(entry.get(shown));
            boolean overBool = inClip && editable && cb.contains(mouseX, mouseY);
            int swW = 36;
            int swH = 16;
            int swX = cb.right() - swW;
            int swY = Math.round(cb.centerY() - swH * 0.5f);
            Identifier swSprite = !editable ? TOGGLE_LOCKED : (isOn ? TOGGLE_ON : TOGGLE_OFF);
            ctx.blit(swSprite, swX, swY, swX + swW, swY + swH, 0.0f, 1.0f, 0.0f, 1.0f);
            if (overBool) {
                ctx.fill(swX + 1, swY + 1, swX + swW - 1, swY + swH - 1, 0x24FFE599);
            }

            Box stateTextBox = new Box(cb.x(), cb.y(), Math.max(20, swX - 4 - cb.x()), cb.h());
            String stateText = I18n.get(isOn
                ? "config.chiseled_enchanting_table.val.on"
                : "config.chiseled_enchanting_table.val.off");
            drawClampedCenteredText(context.font(),
                ctx,
                stateText,
                stateTextBox,
                ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
                ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
                !editable ? DIM_TEXT : (isOn ? EMERALD_TEXT : (overBool ? CREAM_TEXT : MUTED_TEXT))
            );

            if (editable && hitValid) {
                context.registerHit(new HitBox(new Box(cb.x(), cTop, cb.w(), cBot - cTop), () -> {
                    entry.toggleOrCycle(context.draft(), 1);
                    context.changed();
                    context.sound(1.04f);
                }, () -> {
                    entry.toggleOrCycle(context.draft(), -1);
                    context.changed();
                    context.sound(0.96f);
                }));
            }
            return;
        }

        if (entry.kind() == ControlKind.ENUM_CYCLE) {
            boolean over = inClip && editable && cb.contains(mouseX, mouseY);
            Identifier sprite = !editable
                ? BTN_PLAQUE_DISABLED
                : (over ? BTN_PLAQUE_HOVER : (entry.isCoreRuleAtom() ? BTN_PLAQUE_GOLD : BTN_PLAQUE));
            blitNineSliceRect(ctx, sprite, cb.x(), cb.y(), cb.w(), cb.h(), 6, 6, 64, 18);

            String decorated = "\u25c2 " + valText + " \u25b8";
            drawClampedCenteredText(context.font(),
                ctx,
                decorated,
                new Box(cb.x() + 4, cb.y(), Math.max(16, cb.w() - 8), cb.h()),
                ConfigLayoutSpec.SECONDARY_TEXT_SCALE,
                ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
                !editable ? DIM_TEXT : CREAM_TEXT
            );

            if (editable && hitValid) {
                context.registerHit(new HitBox(new Box(cb.x(), cTop, cb.w(), cBot - cTop), () -> {
                    entry.toggleOrCycle(context.draft(), 1);
                    context.changed();
                    context.sound(1.04f);
                }, () -> {
                    entry.toggleOrCycle(context.draft(), -1);
                    context.changed();
                    context.sound(0.96f);
                }));
            }
            return;
        }

        // Numeric Slider: [valuePill] [-] [=== unobstructed track + knob ===] [+]
        SliderControlLayout sc = ConfigLayoutSpec.computeSliderControl(cb);
        Box valuePill = sc.valuePill();
        Box minusBox = sc.minusBtn();
        Box trackArea = sc.trackArea();
        Box plusBox = sc.plusBtn();

        // Dedicated left value readout pill (never overlaps sliding knob)
        ctx.fill(valuePill.x(), valuePill.y(), valuePill.right(), valuePill.bottom(), 0xCC110B1F);
        ctx.fill(valuePill.x(), valuePill.bottom() - 1, valuePill.right(), valuePill.bottom(), 0x667A5C38);
        drawClampedCenteredText(context.font(),
            ctx,
            valText,
            new Box(valuePill.x() + 2, valuePill.y(), Math.max(12, valuePill.w() - 4), valuePill.h()),
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            editable ? GOLD_TEXT : DIM_TEXT
        );

        boolean overMinus = inClip && editable && minusBox.contains(mouseX, mouseY);
        boolean overPlus = inClip && editable && plusBox.contains(mouseX, mouseY);
        boolean overTrack = inClip && editable && trackArea.contains(mouseX, mouseY);

        blitNineSliceRect(ctx, !editable ? MICRO_BTN_DISABLED : (overMinus ? MICRO_BTN_HOVER : MICRO_BTN),
            minusBox.x(), minusBox.y(), minusBox.w(), minusBox.h(), 3, 3, 16, 16);
        drawClampedCenteredText(context.font(), ctx, "-", minusBox,
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE, ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            editable ? (overMinus ? GOLD_TEXT : CREAM_TEXT) : DIM_TEXT);

        blitNineSliceRect(ctx, !editable ? MICRO_BTN_DISABLED : (overPlus ? MICRO_BTN_HOVER : MICRO_BTN),
            plusBox.x(), plusBox.y(), plusBox.w(), plusBox.h(), 3, 3, 16, 16);
        drawClampedCenteredText(context.font(), ctx, "+", plusBox,
            ConfigLayoutSpec.SECONDARY_TEXT_SCALE, ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE,
            editable ? (overPlus ? GOLD_TEXT : CREAM_TEXT) : DIM_TEXT);

        int barH = 8;
        int barY = Math.round(trackArea.centerY() - barH * 0.5f);
        blitNineSliceRect(ctx, SLIDER_TRACK, trackArea.x(), barY, trackArea.w(), barH, 4, 3, 64, 8);

        double norm = entry.normalizedSliderValue(shown);
        int fillW = Math.round((float) (trackArea.w() * norm));
        if (fillW > 2) {
            blitNineSliceRect(ctx, SLIDER_FILL, trackArea.x(), barY, fillW, barH, 4, 3, 64, 8);
        }

        int knobW = 10;
        int knobH = 14;
        int knobX = trackArea.x() + Mth.clamp(fillW - knobW / 2, 0, Math.max(0, trackArea.w() - knobW));
        int knobY = Math.round(trackArea.centerY() - knobH * 0.5f);
        ctx.blit(overTrack ? SLIDER_KNOB_HOVER : SLIDER_KNOB, knobX, knobY, knobX + knobW, knobY + knobH, 0.0f, 1.0f, 0.0f, 1.0f);

        if (editable && hitValid) {
            context.registerHit(new HitBox(new Box(minusBox.x(), cTop, minusBox.w(), cBot - cTop), () -> {
                entry.stepValue(context.draft(), -1);
                context.changed();
                context.sound(0.96f);
            }, () -> {
                entry.stepValue(context.draft(), -5);
                context.changed();
                context.sound(0.90f);
            }));
            context.registerHit(new HitBox(new Box(plusBox.x(), cTop, plusBox.w(), cBot - cTop), () -> {
                entry.stepValue(context.draft(), 1);
                context.changed();
                context.sound(1.04f);
            }, () -> {
                entry.stepValue(context.draft(), 5);
                context.changed();
                context.sound(1.10f);
            }));
            context.registerSlider(new SliderTrack(entry, new Box(trackArea.x(), cTop, trackArea.w(), cBot - cTop)));
        }
    }

    static List<Component> buildL3ProvenanceTooltip(ConfigViewContext context, OptionEntry entry, OptionAvailability avail) {
        List<Component> lines = new ArrayList<>();
        String titlePrefix = entry.isCoreRuleAtom() ? "\u2726 " : "";
        lines.add(Component.literal(titlePrefix).append(Component.translatable(entry.nameKey())).withStyle(CetFormatting.GOLD));
        if (entry.isCoreRuleAtom()) {
            lines.add(Component.translatable("config.chiseled_enchanting_table.tip.core_atom_badge")
                .withStyle(CetFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.translatable(entry.descKey()).withStyle(CetFormatting.GRAY));

        String draftVal = formatValueFrom(entry, context.draft());
        String effectiveVal = formatValueFrom(entry, ModRuntimeConfig.effective().toDraft());
        String defaultVal = formatValueFrom(entry, context.defaults());
        String classicVal = formatValueFrom(entry, context.classicReference());

        lines.add(Component.translatable("config.chiseled_enchanting_table.tip.prov_draft", draftVal)
            .withStyle(avail.modifiedFromDefault() ? CetFormatting.YELLOW : CetFormatting.GREEN));
        lines.add(Component.translatable("config.chiseled_enchanting_table.tip.prov_effective", effectiveVal)
            .withStyle(draftVal.equals(effectiveVal) ? CetFormatting.AQUA : CetFormatting.GOLD));
        lines.add(Component.translatable("config.chiseled_enchanting_table.tip.prov_defaults", defaultVal, classicVal)
            .withStyle(CetFormatting.DARK_AQUA));

        if ("enchanted_book_stack_size".equals(entry.key())) {
            lines.add(Component.translatable("config.chiseled_enchanting_table.tip.stack_shrink_save_risk")
                .withStyle(CetFormatting.RED));
        }

        Style scopeFmt = switch (avail.effectScope()) {
            case IMMEDIATE -> CetFormatting.GREEN;
            case NEW_GENERATION -> CetFormatting.AQUA;
            case RESTART_REQUIRED -> CetFormatting.GOLD;
        };
        lines.add(Component.literal(avail.effectScope().glyph() + " ")
            .append(Component.translatable(avail.effectScope().badgeKey() + ".detail"))
            .withStyle(scopeFmt));

        if (avail.inactiveReasonKey() != null) {
            lines.add(Component.literal("🔒 ")
                .append(Component.translatable(avail.inactiveReasonKey()))
                .withStyle(CetFormatting.RED));
        }
        return lines;
    }

    static String formatValueFrom(OptionEntry entry, Draft source) {
        String raw = entry.rawOrKeyDisplay(source);
        return raw.startsWith("@") ? I18n.get(raw.substring(1)) : raw;
    }
}
