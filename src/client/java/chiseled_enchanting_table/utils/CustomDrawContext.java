package chiseled_enchanting_table.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.api.EnvType;

@Environment(EnvType.CLIENT)
public class CustomDrawContext {
    public static void drawItemInSlotWithColor(GuiGraphicsExtractor ctx, Font textRenderer, ItemStack stack, int x, int y, int color) {
        if (stack.isEmpty()) return;
        var text = String.valueOf(stack.getCount());
        ctx.nextStratum();
        ctx.text(textRenderer, text, x + 19 - 2 - textRenderer.width(text), y + 6 + 3, color, true);
    
        if (stack.isBarVisible()) {
           var i = stack.getBarWidth();
           var j = stack.getBarColor();
           int k = x + 2;
           int l = y + 13;
           ctx.fill(k, l, k + 13, l + 2, -16777216);
           ctx.fill(k, l, k + i, l + 1, j | -16777216);
        }
    }
}
