package chiseled_enchanting_table.mechanics.ritual;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import static chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.*;

/** The ritual world-query/mutation boundary. Deterministic calculations remain in the facade. */
final class RitualWorldAccess {
    private RitualWorldAccess() {}

    public static LibraryRitualScan scanLibraryRitualContext(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg
    ) {
        if (level == null || tablePos == null) {
            return new LibraryRitualScan(0, false, 0, 0, 0.0);
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
        int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
        if (safeCfg.requireBreathingRoom()
            && BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, safeCfg) <= 0) {
            return new LibraryRitualScan(0, false, 0, 0, 0.0);
        }

        var lrCfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.configForSide(level.isClientSide());
        var ritCfg = level.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.ritualConfig()
            : chiseled_enchanting_table.data.ChiseledEnchantingTableData.ritualConfig();

        int rawShelves = 0;
        boolean anyTome = false;
        int rawTomeEnchants = 0;
        int maxOverloadedInf = 0;
        double totalWearImmuneLoad = 0.0;
        boolean checkLos = safeCfg.requireLineOfSight();

        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!level.hasChunkAt(p)) {
                continue;
            }
            if (checkLos && !ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (ChiseledEnchantingTableBlock.isRegularBookshelf(state)) {
                if (chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                    .classifyBookshelf(true, false, false, false).isBroadBookshelf()) {
                    rawShelves++;
                }
                continue;
            }
            if (!state.is(Blocks.CHISELED_BOOKSHELF)) {
                continue;
            }
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                boolean occupiedProp = false;
                for (var prop : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
                    if (state.hasProperty(prop) && state.getValue(prop)) {
                        occupiedProp = true;
                        break;
                    }
                }
                if (chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                    .classifyBookshelf(false, true, occupiedProp, false).isBroadBookshelf()) {
                    rawShelves++;
                }
                continue;
            }
            boolean anyBookInShelf = false;
            boolean anyEnchantedInShelf = false;
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()) continue;
                anyBookInShelf = true;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                int count = enchants.size();
                if (count <= 0) continue;
                anyEnchantedInShelf = true;
                if (chiseled_enchanting_table.config.ModRuntimeConfig.forSide(level.isClientSide()).tomeLocksEnabled()
                    && chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(book)) {
                    continue;
                }
                anyTome = true;
                rawTomeEnchants += count;
                boolean hasInf = MasterTomeWear.hasEffectiveWearImmunity(lrCfg, book);
                if (hasInf) {
                    totalWearImmuneLoad += computeInfinityTomeQualificationLoad(ritCfg, lrCfg, book);
                    if ((count - 1) > 3 && count > maxOverloadedInf) {
                        maxOverloadedInf = count;
                    }
                }
            }
            if (chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                .classifyBookshelf(false, true, anyBookInShelf, anyEnchantedInShelf).isBroadBookshelf()) {
                rawShelves++;
            }
        }
        int effectiveShelves = BookshelfProgression.effectiveShelvesAfterBreathingRoom(
            rawShelves,
            ceilingBlocked,
            openAdj,
            safeCfg
        );
        int effectiveTomeEnchants = BookshelfProgression.effectiveShelvesAfterBreathingRoom(
            rawTomeEnchants,
            ceilingBlocked,
            openAdj,
            safeCfg
        );
        return new LibraryRitualScan(effectiveShelves, anyTome, effectiveTomeEnchants, maxOverloadedInf, totalWearImmuneLoad);
    }

    public static boolean doesMatchingMasterTomeHaveInfinity(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg,
        Identifier enchantmentId,
        int minLevel
    ) {
        if (level == null || tablePos == null || enchantmentId == null) {
            return false;
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        if (safeCfg.requireBreathingRoom()) {
            boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
            int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
            if (BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, safeCfg) <= 0) {
                return false;
            }
        }
        var lrCfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.configForSide(level.isClientSide());
        boolean checkLos = safeCfg.requireLineOfSight();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!level.hasChunkAt(p)) {
                continue;
            }
            if (checkLos && !ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, p)) {
                continue;
            }
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()
                    || (chiseled_enchanting_table.config.ModRuntimeConfig.forSide(level.isClientSide()).tomeLocksEnabled()
                        && chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(book))
                    || !MasterTomeWear.hasEffectiveWearImmunity(lrCfg, book)) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                for (var entry : enchants.entrySet()) {
                    var keyOpt = entry.getKey().unwrapKey();
                    if (keyOpt.isPresent()
                        && enchantmentId.equals(keyOpt.get().identifier())
                        && entry.getIntValue() >= minLevel) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static ItemStack degradeLowestPriorityMasterTomeInWorld(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg,
        @Nullable Set<Identifier> activeRitualEnchantments
    ) {
        if (level == null || tablePos == null) {
            return null;
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        if (safeCfg.requireBreathingRoom()) {
            boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
            int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
            if (BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, safeCfg) <= 0) {
                return null;
            }
        }
        var lrCfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.configForSide(level.isClientSide());
        Set<Identifier> activeIds = activeRitualEnchantments != null ? activeRitualEnchantments : Set.of();
        boolean checkLos = safeCfg.requireLineOfSight();
        List<TomeCandidate> candidates = new ArrayList<>();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!level.hasChunkAt(p)) {
                continue;
            }
            if (checkLos && !ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, p)) {
                continue;
            }
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()
                    || (chiseled_enchanting_table.config.ModRuntimeConfig.forSide(level.isClientSide()).tomeLocksEnabled()
                        && chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(book))) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                if (enchants.isEmpty()) continue;
                int count = enchants.size();
                double relSum = 0.0;
                double raritySum = 0.0;
                boolean backsActive = false;
                for (var entry : enchants.entrySet()) {
                    if (entry.getIntValue() > 0) {
                        var keyOpt = entry.getKey().unwrapKey();
                        if (keyOpt.isPresent() && activeIds.contains(keyOpt.get().identifier())) {
                            backsActive = true;
                        }
                    }
                    int maxLvl = Math.max(1, entry.getKey().value().getMaxLevel());
                    relSum += Mth.clamp((double) entry.getIntValue() / maxLvl, 0.05, 1.0);
                    int w = Math.max(1, entry.getKey().value().definition().weight());
                    raritySum += 1.0 / Math.min(16.0, w);
                }
                candidates.add(new TomeCandidate(
                    p.immutable(),
                    slot,
                    count,
                    relSum / count,
                    raritySum / count,
                    MasterTomeWear.hasEffectiveWearImmunity(lrCfg, book),
                    backsActive
                ));
            }
        }
        TomeCandidate victim = selectDegradationVictim(candidates);
        if (victim == null) {
            return null;
        }
        if (!(level.getBlockEntity(victim.shelfPos()) instanceof ChiseledBookShelfBlockEntity targetShelf)) {
            return null;
        }
        ItemStack destroyedCopy = targetShelf.getItem(victim.slot()).copy();
        targetShelf.setItem(victim.slot(), new ItemStack(BlockRegistry.DEPLETED_TOME, 1));
        targetShelf.setChanged();
        BlockState shelfState = level.getBlockState(victim.shelfPos());
        if (shelfState.is(Blocks.CHISELED_BOOKSHELF)
            && victim.slot() >= 0
            && victim.slot() < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size()) {
            var occProp = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(victim.slot());
            if (shelfState.hasProperty(occProp) && !shelfState.getValue(occProp)) {
                level.setBlock(victim.shelfPos(), shelfState.setValue(occProp, true), Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(victim.shelfPos(), shelfState, shelfState, Block.UPDATE_ALL);
            }
        }
        level.playSound(
            null,
            victim.shelfPos(),
            SoundEvents.AMETHYST_CLUSTER_BREAK,
            SoundSource.BLOCKS,
            1.0F,
            0.75F
        );
        return destroyedCopy;
    }

    public static ItemStack degradeLowestPriorityMasterTomeInWorld(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg
    ) {
        return degradeLowestPriorityMasterTomeInWorld(level, tablePos, cfg, Set.of());
    }
}
