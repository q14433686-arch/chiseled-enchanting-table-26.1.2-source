package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

/**
 * Guarded compatibility preload for an Iris / MC 26.3 hand-render crash where Iris resolves
 * enchantment glint samplers lazily inside an active render pass.
 *
 * <p>Audit DEAD-01: gated on both {@code iris} being loaded AND Minecraft 26.3+ (so 26.1.2
 * clients incur zero per-tick work), and caches the active {@link TextureManager} identity so
 * preloading runs once per resource manager lifecycle instead of every tick.</p>
 */
@Environment(EnvType.CLIENT)
public final class IrisGlintPreload {
    private static final Identifier ITEM_GLINT =
        Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Identifier ARMOR_GLINT =
        Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");

    private static volatile boolean warned;
    private static volatile TextureManager lastPreloadedManager;

    private IrisGlintPreload() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return;
        }
        String mcVersionId = resolveMinecraftVersionId();
        if (!mcVersionId.startsWith("26.3")) {
            return;
        }
        // F3+T can reload textures on the SAME TextureManager instance.
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override public Identifier getFabricId() {
                    return ChiseledEnchantingTable.identifier("iris_glint_preload_reset");
                }
                @Override public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                    lastPreloadedManager = null;
                }
            });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TextureManager textures = client.getTextureManager();
            if (textures == null || textures == lastPreloadedManager) {
                return;
            }
            try {
                if (client.getResourceManager().getResource(ITEM_GLINT).isPresent()) {
                    textures.getTexture(ITEM_GLINT);
                }
                if (client.getResourceManager().getResource(ARMOR_GLINT).isPresent()) {
                    textures.getTexture(ARMOR_GLINT);
                }
                lastPreloadedManager = textures;
            } catch (RuntimeException error) {
                lastPreloadedManager = textures;
                if (!warned) {
                    warned = true;
                    ChiseledEnchantingTable.LOGGER.warn(
                        "Failed to preload enchantment glint textures for Iris compat", error);
                }
            }
        });
    }

    private static String resolveMinecraftVersionId() {
        try {
            return SharedConstants.getCurrentVersion().id();
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
