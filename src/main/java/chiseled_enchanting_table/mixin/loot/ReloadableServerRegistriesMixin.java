package chiseled_enchanting_table.mixin.loot;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures {@code loot_injections.json} and {@code pack_policy.json} are preloaded from the incoming
 * {@link ResourceManager} BEFORE {@code RegistryDataLoader.load(..., RELOADABLE_REGISTRIES, ...)}
 * builds {@code Registries.LOOT_TABLE} and fires {@code LootTableEvents.MODIFY} on both initial
 * world boot and {@code /reload}.
 */
@Mixin(ReloadableServerRegistries.class)
public abstract class ReloadableServerRegistriesMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private static void cet$preloadLootInjectionRules(
        LayeredRegistryAccess<RegistryLayer> context,
        List<Registry.PendingTags<?>> updatedContextTags,
        ResourceManager manager,
        Executor executor,
        CallbackInfoReturnable<CompletableFuture<ReloadableServerRegistries.LoadResult>> cir
    ) {
        ChiseledEnchantingTableData.preloadForLootRegistries(manager);
    }
}
