package chiseled_enchanting_table.gui.grimoire;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Viewer-neutral bridge connecting item icons inside {@link ArcaneGrimoireScreen} (and custom cost
 * badges in {@code ChiseledEnchantingTableScreen}) to optional recipe viewers (JEI / REI) without
 * leaking optional compile-time types into core GUI classes.
 */
@Environment(EnvType.CLIENT)
public final class GrimoireViewerBridge {

    public record HoveredSlot(
        ItemStack stack,
        Rect2i area,
        boolean leftClickOpensRecipes
    ) {}

    public interface Backend {
        boolean showRecipes(ItemStack stack);

        boolean showUses(ItemStack stack);
    }

    private static final List<Backend> BACKENDS = new CopyOnWriteArrayList<>();

    private GrimoireViewerBridge() {}

    public static void registerBackend(Backend backend) {
        if (backend != null && !BACKENDS.contains(backend)) {
            BACKENDS.add(backend);
        }
    }

    public static void unregisterBackend(Backend backend) {
        if (backend != null) {
            BACKENDS.remove(backend);
        }
    }

    public static boolean isAvailable() {
        return !BACKENDS.isEmpty();
    }

    public static boolean openRecipes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemStack query = stack.copyWithCount(1);
        for (Backend backend : BACKENDS) {
            if (backend.showRecipes(query)) {
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
                return true;
            }
        }
        return false;
    }

    public static boolean openUses(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemStack query = stack.copyWithCount(1);
        for (Backend backend : BACKENDS) {
            if (backend.showUses(query)) {
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.04F)
                );
                return true;
            }
        }
        return false;
    }
}
