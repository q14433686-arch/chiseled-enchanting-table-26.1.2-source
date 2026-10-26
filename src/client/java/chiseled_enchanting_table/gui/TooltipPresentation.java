package chiseled_enchanting_table.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import chiseled_enchanting_table.compat.CetFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** A shared, viewport-aware presentation policy for table, configuration and guide hovers. */
public final class TooltipPresentation {
    private TooltipPresentation() {}
    public static boolean detailsRequested() {
        // 26.1.2 adaptation: this line has no Screen.hasShiftDown() and no single-arg
        // InputConstants.isKeyDown(int); the vanilla sneak KeyMapping state is queryable anywhere.
        var mc = Minecraft.getInstance();
        return mc != null && mc.options != null && mc.options.keyShift.isDown();
    }
    public static void show(GuiGraphicsExtractor ctx, Font font, List<Component> source,
        Optional<TooltipComponent> image, int mouseX, int mouseY, Identifier style) {
        boolean expanded = detailsRequested();
        var selected = new ArrayList<Component>();
        if (expanded || source.size() <= 5) selected.addAll(source);
        else {
            var critical = new ArrayList<Integer>();
            for (int i = 1; i < source.size(); i++) {
                var color = source.get(i).getStyle().getColor();
                // 26.2 adaptation: ChatFormatting/TextColor legacy constants are gone. Compare the
                // low 24 RGB bits so the check is correct whether getValue() carries an alpha byte
                // or not; red / dark-red rows are the risk lines TooltipBudget keeps first.
                if (color != null) {
                    int rgb = color.getValue() & 0xFFFFFF;
                    if (rgb == (CetFormatting.RED_ARGB & 0xFFFFFF)
                        || rgb == (CetFormatting.DARK_RED_ARGB & 0xFFFFFF)) critical.add(i);
                }
            }
            for (int index : TooltipBudget.summaryIndices(source.size(), critical, 5)) selected.add(source.get(index));
            selected.add(Component.translatable("gui.chiseled_enchanting_table.ui.details").withStyle(CetFormatting.DARK_GRAY));
        }
        var wrapped = new ArrayList<FormattedCharSequence>();
        for (Component line : selected) wrapped.addAll(font.split(line, TooltipBudget.width(ctx.guiWidth())));
        int budget = TooltipBudget.rows(ctx.guiHeight(), expanded);
        if (wrapped.size() > budget) {
            wrapped.subList(budget - 1, wrapped.size()).clear();
            wrapped.add(Component.translatable(expanded ? "gui.chiseled_enchanting_table.ui.guide"
                : "gui.chiseled_enchanting_table.ui.details").withStyle(CetFormatting.DARK_GRAY).getVisualOrderText());
        }
        // Rich item images can be arbitrarily tall; opt into them alongside the full item details.
        ctx.setTooltipForNextFrame(font, wrapped, expanded ? image : Optional.empty(), DefaultTooltipPositioner.INSTANCE,
            mouseX, mouseY, false, style);
    }
}
