package chiseled_enchanting_table.mixin.structure;

import chiseled_enchanting_table.mechanics.structure.BookshelfReplacerProcessor;
import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tier-1 injection: appends {@link BookshelfReplacerProcessor#INSTANCE} to the processor list of
 * every {@link StructureTemplatePool.Projection} at read-time, so any template-pool-based
 * structure (villages, ancient cities, trial chambers, ...) runs our replacement on its blocks.
 *
 * <p>Uses MixinExtras {@code @ModifyReturnValue} to append without needing to rewrite the getter.
 * MixinExtras ships with Fabric Loader &gt;= 0.15 so no extra dependency is needed.</p>
 */
@Mixin(StructureTemplatePool.Projection.class)
public class StructureTemplatePoolProjectionMixin {
    @ModifyReturnValue(method = "getProcessors()Lcom/google/common/collect/ImmutableList;", at = @At("RETURN"))
    private ImmutableList<StructureProcessor> chiseled_enchanting_table$appendBookshelfReplacer(ImmutableList<StructureProcessor> original) {
        if (original.contains(BookshelfReplacerProcessor.INSTANCE)) {
            return original;
        }
        return ImmutableList.<StructureProcessor>builderWithExpectedSize(original.size() + 1)
            .addAll(original)
            .add(BookshelfReplacerProcessor.INSTANCE)
            .build();
    }
}
