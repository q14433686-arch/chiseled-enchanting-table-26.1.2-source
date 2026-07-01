package chiseled_enchanting_table.mixin.structureProcessor.bookshelfReplacer;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import chiseled_enchanting_table.structureProcessor.BookshelfReplacerProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

@Mixin(net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings.class)
public class InjectIn_StructurePlacementData {
    
    @Shadow
    private List<StructureProcessor> processors;
   
    @ModifyReturnValue(method = "getProcessors()Ljava/util/List;", at = @At("RETURN"))
    private List<StructureProcessor> injectBookshelfProcessor(List<StructureProcessor> cir) {
        return Stream.concat(cir.stream(), Stream.of(BookshelfReplacerProcessor.INSTANCE))
                      .collect(Collectors.toList());
    }
}