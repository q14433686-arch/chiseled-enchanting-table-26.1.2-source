package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.structure.BookshelfReplacerProcessor;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Registers the port's structure processor types. Currently just
 * {@link BookshelfReplacerProcessor}, which is wired into the world by the mixins under
 * {@code chiseled_enchanting_table.mixin.structure.*} — those mixins are the "trigger", this
 * class is the "declaration" that turns the processor into a proper registry entry so a data pack
 * can reference it by id ({@code chiseled_enchanting_table:bookshelf_replacer}).
 *
 * <p><b>26.2 registration change:</b> {@code StructureProcessor} is now an interface that exposes
 * its own {@code codec()}, and {@code StructureProcessorType} lost its generic parameter and its
 * static {@code register(String, MapCodec)} helper. In 26.2 the structure-processor registry
 * ({@link BuiltInRegistries#STRUCTURE_PROCESSOR}) is a
 * {@code Registry<MapCodec<? extends StructureProcessor>>}, so we register the processor's
 * {@link MapCodec} directly. The returned/registered codec is what the vanilla
 * {@code StructureProcessorType.SINGLE_CODEC} dispatch uses to (de)serialise the processor.</p>
 */
public final class StructureProcessorRegistry {
    /** The registered codec for {@link BookshelfReplacerProcessor} (kept for reference/debugging). */
    public static MapCodec<BookshelfReplacerProcessor> BOOKSHELF_REPLACER;

    private StructureProcessorRegistry() {}

    public static void init() {
        BOOKSHELF_REPLACER = BookshelfReplacerProcessor.CODEC;
        Registry.register(
            BuiltInRegistries.STRUCTURE_PROCESSOR,
            ChiseledEnchantingTable.identifier("bookshelf_replacer"),
            BOOKSHELF_REPLACER
        );
    }
}
