import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
public class Dummy {
    public void render(GuiGraphicsExtractor ctx, Identifier id) {
        ctx.blit(id, 0, 0, 100, 100, 0.0f, 1.0f, 0.0f, 1.0f);
    }
}
