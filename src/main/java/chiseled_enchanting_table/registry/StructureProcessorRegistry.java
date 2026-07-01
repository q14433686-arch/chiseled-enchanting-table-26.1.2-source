package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.structure.BookshelfReplacerProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

/**
 * Registers the port's structure processor types. Currently just
 * {@link BookshelfReplacerProcessor}, which is wired into the world by the mixins under
 * {@code chiseled_enchanting_table.mixin.structure.*} — those mixins are the "trigger", this
 * class is the "declaration" that turns the processor into a proper registry entry so a data pack
 * can reference it by id ({@code chiseled_enchanting_table:bookshelf_replacer}).
 *
 * <p>26.1.2 registers structure processor types via {@link StructureProcessorType#register}, which
 * takes a namespaced id string and a MapCodec. The returned {@link StructureProcessorType}
 * instance is what {@link BookshelfReplacerProcessor#getType} needs to return.</p>
 */
public final class StructureProcessorRegistry {
    public static StructureProcessorType<BookshelfReplacerProcessor> BOOKSHELF_REPLACER;

    private StructureProcessorRegistry() {}

    public static void init() {
        BOOKSHELF_REPLACER = StructureProcessorType.register(
            ChiseledEnchantingTable.identifier("bookshelf_replacer").toString(),
            BookshelfReplacerProcessor.CODEC
        );
    }
}
