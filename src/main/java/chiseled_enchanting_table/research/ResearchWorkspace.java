package chiseled_enchanting_table.research;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Server-only authorized workspace adapter. Scans on demand, never ticks or loads absent chunks. */
public final class ResearchWorkspace {
    private ResearchWorkspace() {}
    public static Optional<ResearchCatalog.Library> resolve(ServerPlayer player) {
        if (!(player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu)
            || player.isSpectator() || !player.getAbilities().mayBuild) return Optional.empty();
        ServerLevel level = player.level();
        BlockPos pos = menu.tableBlockPos();
        if (pos == null || menu.world() != level || !level.hasChunkAt(pos)
            || !level.getBlockState(pos).is(BlockRegistry.CHISELED_ENCHANTING_TABLE)
            || !menu.stillValid(player) || !level.mayInteract(player,pos)
            || !player.mayUseItemAt(pos,Direction.UP,ItemStack.EMPTY)) return Optional.empty();
        return Optional.of(scan(level,pos,ChiseledEnchantingTableData.shelfGatesConfig()));
    }
    /** Trusted world read, not an authorization shortcut. Runtime actions must use resolve. */
    public static ResearchCatalog.Library scan(ServerLevel level, BlockPos table,
                                               BookshelfProgression.Config cfg) {
        if (!level.hasChunkAt(table)) throw new IllegalStateException("Research table chunk not loaded");
        if (cfg.requireBreathingRoom()
            && BookshelfProgression.effectiveShelvesAfterBreathingRoom(1,
                ChiseledEnchantingTableBlock.isTableCeilingBlocked(level,table),
                ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level,table),cfg) <= 0)
            return new ResearchCatalog.Library(0,Map.of());
        int shelves = 0;
        ResearchKnowledge knowledge = new ResearchKnowledge();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(cfg)) {
            BlockPos pos = table.offset(offset);
            if (!level.hasChunkAt(pos)) continue;
            if (cfg.requireLineOfSight() && !ChiseledEnchantingTableBlock.hasClearLineOfSight(level,table,pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(Blocks.BOOKSHELF) || ChiseledEnchantingTableBlock.isRegularBookshelf(state)) shelves++;
            else if (state.is(Blocks.CHISELED_BOOKSHELF)) {
                shelves++;
                if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) continue;
                for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                    var book = shelf.getItem(slot);
                    if (!book.is(Items.ENCHANTED_BOOK)) continue;
                    for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(book).entrySet()) {
                        var key = entry.getKey().unwrapKey();
                        if (key.isPresent()) knowledge.include(key.get().identifier().toString(),entry.getIntValue());
                    }
                }
            }
        }
        return new ResearchCatalog.Library(shelves,knowledge.snapshot());
    }
}
