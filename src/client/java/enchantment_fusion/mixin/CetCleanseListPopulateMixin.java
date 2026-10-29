package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseClientBridge;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.gui.CleanseListSupport", remap = false)
public abstract class CetCleanseListPopulateMixin {
    @Inject(
        method = "populate(Lchiseled_enchanting_table/gui/EnchantementListWidget;Lnet/minecraft/client/Minecraft;Ljava/util/Set;Ljava/util/Set;Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/Consumer;IZD)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onPopulateCleanseList(
        @Coerce Object list,
        Minecraft minecraft,
        Set<Identifier> expandedCleanseCategories,
        Set<Identifier> visibleCleanseGroupIds,
        Function<Object, Object> buildTopEntry,
        Function<Object, Object> buildChildEntry,
        Consumer<Object> addRow,
        int navigationIndex,
        boolean targetChanged,
        double previousScroll,
        CallbackInfo ci
    ) {
        if (CetCleanseClientBridge.tryPopulateFusionAwareCleanseList(
            list,
            minecraft,
            expandedCleanseCategories,
            visibleCleanseGroupIds,
            buildTopEntry,
            buildChildEntry,
            addRow,
            navigationIndex,
            targetChanged,
            previousScroll
        )) {
            ci.cancel();
        }
    }
}
