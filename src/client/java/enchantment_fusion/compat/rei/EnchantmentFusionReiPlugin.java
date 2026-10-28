package enchantment_fusion.compat.rei;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.gui.EnchantmentFusionScreen;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.DisplayBoundsProvider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.InteractionResult;

/**
 * Optional REI 26.1 integration registering the exact 276x198 bounds of {@link EnchantmentFusionScreen}
 * so REI's side panels never overlap the custom fusion preview panel.
 */
public final class EnchantmentFusionReiPlugin implements REIClientPlugin {
    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDecider(new DisplayBoundsProvider<EnchantmentFusionScreen>() {
            @Override
            public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
                return EnchantmentFusionScreen.class.isAssignableFrom(screen);
            }

            @Override
            public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen) {
                return InteractionResult.PASS;
            }

            @Override
            public Rectangle getScreenBounds(EnchantmentFusionScreen screen) {
                Rect2i bounds = screen.guiBounds();
                return new Rectangle(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
            }
        });
    }

    @Override
    public String getPluginProviderName() {
        return EnchantmentFusion.MOD_ID;
    }
}
