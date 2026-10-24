package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/**
 * Workaround for an Iris / MC 26.3 hand-render crash: Iris resolves the enchantment
 * glint sampler lazily from inside an active render pass, and the first frame that
 * renders a glinting held item (this mod ships one such item, the Arcane Catalyst,
 * but any enchanted item does it) makes the texture manager try to upload the glint
 * texture mid-pass, which the new render backend rejects with
 * "Close the existing render pass before performing additional commands" and the
 * game exits on world entry. Preloading both vanilla glint textures at the end of
 * the client tick - on the render thread but outside any render pass - turns Iris's
 * later lookup into a cache hit. Active only when Iris is installed; after the
 * first upload each call is a plain map lookup, and F3+T reloads are covered
 * because the check runs every tick while in a world.
 */
@Environment(EnvType.CLIENT)
public final class IrisGlintPreload {
    private static final Identifier ITEM_GLINT =
        Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Identifier ARMOR_GLINT =
        Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");

    private static boolean warned;

    private IrisGlintPreload() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                return;
            }
            try {
                TextureManager textures = client.getTextureManager();
                textures.getTexture(ITEM_GLINT);
                textures.getTexture(ARMOR_GLINT);
            } catch (RuntimeException error) {
                if (!warned) {
                    warned = true;
                    ChiseledEnchantingTable.LOGGER.warn(
                        "Failed to preload enchantment glint textures for Iris compat", error);
                }
            }
        });
    }
}
