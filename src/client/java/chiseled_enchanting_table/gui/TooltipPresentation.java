package chiseled_enchanting_table.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
        // 26.1.2 keeps the GLFW-era signature: the window must be passed explicitly
        // (26.3's windowless overload does not exist here).
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
    }
    public static void show(GuiGraphicsExtractor ctx, Font font, List<Component> source,
        Optional<TooltipComponent> image, int mouseX, int mouseY, Identifier style) {
        boolean expanded = detailsRequested();
        var selected = new ArrayList<Component>();
        if (expanded || source.size() <= 5) selected.addAll(source);
        else {
            var critical = new ArrayList<Integer>();
            // TextColor.RED / DARK_RED constants only exist on 26.3; derive them from the
            // legacy ChatFormatting palette on 26.1.2.
            var red = TextColor.fromLegacyFormat(ChatFormatting.RED);
            var darkRed = TextColor.fromLegacyFormat(ChatFormatting.DARK_RED);
            for (int i = 1; i < source.size(); i++) {
                var color = source.get(i).getStyle().getColor();
                if (red.equals(color) || darkRed.equals(color)) critical.add(i);
            }
            for (int index : TooltipBudget.summaryIndices(source.size(), critical, 5)) selected.add(source.get(index));
            selected.add(Component.translatable("gui.chiseled_enchanting_table.ui.details").withStyle(ChatFormatting.DARK_GRAY));
        }
        var wrapped = new ArrayList<FormattedCharSequence>();
        var windowSize = net.minecraft.client.Minecraft.getInstance().getWindow();
        for (Component line : selected) wrapped.addAll(font.split(line, TooltipBudget.width(windowSize.getGuiScaledWidth())));
        int budget = TooltipBudget.rows(windowSize.getGuiScaledHeight(), expanded);
        if (wrapped.size() > budget) {
            wrapped.subList(budget - 1, wrapped.size()).clear();
            wrapped.add(Component.translatable(expanded ? "gui.chiseled_enchanting_table.ui.guide"
                : "gui.chiseled_enchanting_table.ui.details").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        // Rich item images can be arbitrarily tall; opt into them alongside the full item details.
        ctx.setTooltipForNextFrame(font, wrapped, expanded ? image : Optional.empty(), DefaultTooltipPositioner.INSTANCE,
            mouseX, mouseY, false, style);
    }
}
