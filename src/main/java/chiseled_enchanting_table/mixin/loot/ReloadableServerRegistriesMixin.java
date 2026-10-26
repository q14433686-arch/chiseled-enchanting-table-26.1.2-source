package chiseled_enchanting_table.mixin.loot;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the mod's loot-table preload part of the vanilla registry reload itself: when our
 * datapack loot rules cannot be parsed, the whole reload fails and keeps the previous rules
 * instead of silently falling back to bundled defaults (Audit RELOAD-01).
 *
 * <p>26.1.2 adaptation: {@code ReloadableServerRegistries.reload} here takes
 * {@code (LayeredRegistryAccess, List, ResourceManager, Executor)} — the two-argument
 * {@code PreparationBarrier} shape used by the 26.3 branch does not exist in this version,
 * and injecting with it crashes mixin application at server startup.</p>
 */
@Mixin(ReloadableServerRegistries.class)
public abstract class ReloadableServerRegistriesMixin {
    @Inject(method = "reload", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cet$preloadLootRules(
        LayeredRegistryAccess<?> layeredRegistryAccess,
        List<?> reloadables,
        ResourceManager resourceManager,
        Executor executor,
        CallbackInfoReturnable<CompletableFuture<?>> cir
    ) {
        try {
            ChiseledEnchantingTableData.preloadForLootRegistries(resourceManager);
        } catch (RuntimeException error) {
            ChiseledEnchantingTableData.clearLootRegistryBuildRules();
            chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.error(
                "Rejected invalid chiseled enchanting loot rules; keeping previous rules", error);
            cir.setReturnValue(CompletableFuture.failedFuture(error));
        }
    }
}
