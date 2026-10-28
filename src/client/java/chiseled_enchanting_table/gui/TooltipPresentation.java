package chiseled_enchanting_table.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
    private static boolean testForceDetails = false;
    private TooltipPresentation() {}
    static void setForceDetailsForTest(boolean force) { testForceDetails = force; }
    public static boolean detailsRequested() {
        if (testForceDetails) return true;
        // Query live GLFW window Shift state directly: vanilla KeyMapping#isDown() is reset via
        // KeyMapping.releaseAll() whenever any Screen opens and stays false while screens are active.
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSHIFT);
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
                // 26.1.2 TextColor has no RED/DARK_RED constants yet; legacy-format lookup is equivalent.
                if (TextColor.fromLegacyFormat(ChatFormatting.RED).equals(color)
                    || TextColor.fromLegacyFormat(ChatFormatting.DARK_RED).equals(color)) critical.add(i);
            }
            for (int index : TooltipBudget.summaryIndices(source.size(), critical, 5)) selected.add(source.get(index));
            selected.add(Component.translatable("gui.chiseled_enchanting_table.ui.details").withStyle(ChatFormatting.DARK_GRAY));
        }
        var wrapped = new ArrayList<FormattedCharSequence>();
        for (Component line : selected) wrapped.addAll(font.split(line, TooltipBudget.width(ctx.guiWidth())));
        int budget = TooltipBudget.rows(ctx.guiHeight(), expanded);
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
