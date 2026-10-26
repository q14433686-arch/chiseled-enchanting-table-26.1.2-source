package chiseled_enchanting_table.mixin.loot;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the mod's loot-table preload part of the vanilla registry reload itself: when our
 * datapack loot rules cannot be parsed, the whole reload fails and keeps the previous rules
 * instead of silently falling back to bundled defaults (Audit RELOAD-01).
 */
@Mixin(ReloadableServerRegistries.class)
public abstract class ReloadableServerRegistriesMixin {
    @Inject(method = "reload", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cet$preloadLootRules(
        ResourceManager resourceManager,
        PreparableReloadListener.PreparationBarrier preparationBarrier,
        CallbackInfoReturnable<CompletableFuture<Identifier>> cir
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
