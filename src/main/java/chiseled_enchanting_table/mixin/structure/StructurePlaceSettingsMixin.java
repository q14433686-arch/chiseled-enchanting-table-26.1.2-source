package chiseled_enchanting_table.mixin.structure;

import chiseled_enchanting_table.mechanics.structure.BookshelfReplacerProcessor;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tier-1 injection (variant): woodland mansions (and any other structure that runs a raw
 * {@code StructurePlaceSettings} rather than going through a template pool) still needs the
 * bookshelf replacer wired in. Same pattern as
 * {@link StructureTemplatePoolProjectionMixin} but on {@code getProcessors()} of
 * {@link StructurePlaceSettings}.
 */
@Mixin(StructurePlaceSettings.class)
public class StructurePlaceSettingsMixin {
    @ModifyReturnValue(method = "getProcessors()Ljava/util/List;", at = @At("RETURN"))
    private List<StructureProcessor> chiseled_enchanting_table$appendBookshelfReplacer(List<StructureProcessor> original) {
        List<StructureProcessor> augmented = new ArrayList<>(original);
        augmented.add(BookshelfReplacerProcessor.INSTANCE);
        return augmented;
    }
}
