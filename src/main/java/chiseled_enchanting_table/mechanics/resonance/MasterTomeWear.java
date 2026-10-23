package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure, data-driven Master Tome charge / wear tracking for {@code Enchanted Book}s stored in
 * nearby {@link ChiseledBookShelfBlockEntity} slots ({@code D1}).
 *
 * <p>Key invariants:</p>
 * <ul>
 *   <li>Fresh untouched books never carry {@link #NBT_WEAR_KEY} so canonical {@code stack64}
 *       stacking is 100% preserved.</li>
 *   <li>In {@link ConsumptionMode#KEEP}, zero wear is applied (legacy infinite Master Tome mode).</li>
 *   <li>In {@link ConsumptionMode#WEAR} (default), gear inscriptions cost {@code 1} charge and
 *       book replications cost {@code bookCopyWearCost} charges out of {@code maxTomeUses}.</li>
 *   <li>When worn charges reach {@code maxTomeUses}, {@link DepleteBehavior#STEP_DOWN} steps the
 *       stored enchantment level down by {@code 1} (turning a {@code Lv.1} single-enchant book into
 *       a {@link BlockRegistry#DEPLETED_TOME}), whereas {@link DepleteBehavior#DEPLETED_TOME}
 *       removes that enchantment directly.</li>
 * </ul>
 */
public final class MasterTomeWear {
    public static final String NBT_WEAR_KEY = "cet_tome_wear";
    public static final int DEFAULT_MAX_TOME_USES = 16;
    public static final int DEFAULT_BOOK_COPY_WEAR_COST = 4;

    private MasterTomeWear() {}

    public enum ConsumptionMode {
        WEAR,
        KEEP,
        CONSUME;

        public String token() {
            return switch (this) {
                case WEAR -> "wear";
                case KEEP -> "keep";
                case CONSUME -> "consume";
            };
        }

        public static ConsumptionMode fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return WEAR;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "keep", "infinite", "none" -> KEEP;
                case "consume", "single_use", "deplete" -> CONSUME;
                default -> WEAR;
            };
        }
    }

    public enum DepleteBehavior {
        STEP_DOWN,
        DEPLETED_TOME;

        public String token() {
            return switch (this) {
                case STEP_DOWN -> "step_down";
                case DEPLETED_TOME -> "depleted_tome";
            };
        }

        public static DepleteBehavior fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return STEP_DOWN;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "depleted_tome", "deplete", "destroy_level" -> DEPLETED_TOME;
                default -> STEP_DOWN;
            };
        }
    }

    /** Pure headless transition result for unit testing & deterministic shelf updates. */
    public record WearStepResult(
        int nextEnchantLevel,
        int nextWearUsed,
        boolean steppedDown,
        boolean depletedCompletely
    ) {}

    public enum WearOutcome {
        NONE,
        WORN,
        STEPPED_DOWN,
        DEPLETED
    }

    /**
     * Pure headless state machine computing the exact post-inscription level and wear count for a
     * stored Master Tome of {@code currentEnchantLevel >= 1}.
     */
    public static WearStepResult computeWearStep(
        ConsumptionMode mode,
        DepleteBehavior depleteBehavior,
        int maxTomeUses,
        int bookCopyWearCost,
        int currentEnchantLevel,
        int currentWearUsed,
        boolean targetIsBook
    ) {
        int safeLevel = Math.max(1, currentEnchantLevel);
        int safeMaxUses = Math.max(1, maxTomeUses);
        int safeWear = Math.max(0, Math.min(safeMaxUses - 1, currentWearUsed));
        if (mode == ConsumptionMode.KEEP) {
            return new WearStepResult(safeLevel, safeWear, false, false);
        }
        int delta = mode == ConsumptionMode.CONSUME
            ? safeMaxUses
            : (targetIsBook ? Math.max(1, bookCopyWearCost) : 1);
        int updatedWear = safeWear + delta;
        if (updatedWear < safeMaxUses) {
            return new WearStepResult(safeLevel, updatedWear, false, false);
        }
        if (mode == ConsumptionMode.CONSUME || depleteBehavior == DepleteBehavior.DEPLETED_TOME || safeLevel <= 1) {
            return new WearStepResult(0, 0, false, true);
        }
        return new WearStepResult(safeLevel - 1, 0, true, false);
    }

    public static int wearCostForTarget(LibraryResonance.Config cfg, boolean targetIsBook) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        return switch (c.tomeConsumptionMode()) {
            case KEEP -> 0;
            case CONSUME -> c.maxTomeUses();
            case WEAR -> targetIsBook ? c.bookCopyWearCost() : 1;
        };
    }

    public static int getWearUsed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return 0;
        }
        CompoundTag root = customData.copyTag();
        return Math.max(0, root.getIntOr(NBT_WEAR_KEY, 0));
    }

    public static void setWearUsed(ItemStack stack, int wearUsed) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = Math.max(0, wearUsed);
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (clamped == 0) {
            if (existing == null || existing.isEmpty()) {
                return;
            }
            CompoundTag tag = existing.copyTag();
            tag.remove(NBT_WEAR_KEY);
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return;
        }
        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();
        tag.putInt(NBT_WEAR_KEY, clamped);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private record CandidateSlot(
        ChiseledBookShelfBlockEntity shelf,
        BlockPos pos,
        BlockState state,
        int slot,
        ItemStack stack,
        Holder<Enchantment> holder,
        int storedLevel,
        int wearUsed
    ) {}

    /**
     * Applies one Master Tome wear charge inside the surrounding Chiseled Bookshelves when a
     * Master-Tome-backed enchantment action succeeds.
     */
    public static WearOutcome applyShelfWear(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config shelfCfg,
        LibraryResonance.Config resonanceCfg,
        Identifier enchantmentId,
        int requiredLevel,
        boolean targetIsBook
    ) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        if (level == null || level.isClientSide() || tablePos == null || enchantmentId == null) {
            return WearOutcome.NONE;
        }
        if (cfg.tomeConsumptionMode() == ConsumptionMode.KEEP) {
            return WearOutcome.NONE;
        }

        Holder<Enchantment> targetHolder;
        try {
            targetHolder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
        } catch (Exception ignored) {
            return WearOutcome.NONE;
        }

        List<CandidateSlot> candidates = new ArrayList<>();
        for (BlockPos offset : chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.getScanOffsets(shelfCfg)) {
            BlockPos scanPos = tablePos.offset(offset);
            if (shelfCfg.requireLineOfSight()
                && !chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, scanPos)) continue;
            BlockState st = level.getBlockState(scanPos);
            if (!st.is(Blocks.CHISELED_BOOKSHELF)) continue;
            if (!(level.getBlockEntity(scanPos) instanceof ChiseledBookShelfBlockEntity cbs)) continue;
            for (int slot = 0; slot < 6; slot++) {
                ItemStack slotStack = cbs.getItem(slot);
                if (!slotStack.is(Items.ENCHANTED_BOOK)) continue;
                int storedLvl = EnchantmentHelper.getEnchantmentsForCrafting(slotStack).getLevel(targetHolder);
                if (storedLvl >= requiredLevel) {
                    candidates.add(new CandidateSlot(
                        cbs,
                        scanPos.immutable(),
                        st,
                        slot,
                        slotStack,
                        targetHolder,
                        storedLvl,
                        getWearUsed(slotStack)
                    ));
                }
            }
        }
        if (candidates.isEmpty()) {
            return WearOutcome.NONE;
        }

        candidates.sort(
            Comparator.comparingInt(CandidateSlot::storedLevel)
                .thenComparing(Comparator.comparingInt(CandidateSlot::wearUsed).reversed())
        );
        CandidateSlot chosen = candidates.getFirst();
        WearStepResult step = computeWearStep(
            cfg.tomeConsumptionMode(),
            cfg.tomeDepleteBehavior(),
            cfg.maxTomeUses(),
            cfg.bookCopyWearCost(),
            chosen.storedLevel(),
            chosen.wearUsed(),
            targetIsBook
        );

        ItemStack mutated = chosen.stack().copy();
        ItemEnchantments currentEnchants = EnchantmentHelper.getEnchantmentsForCrafting(mutated);
        ItemEnchantments.Mutable nextEnchants = new ItemEnchantments.Mutable(currentEnchants);
        nextEnchants.set(chosen.holder(), Math.max(0, step.nextEnchantLevel()));

        EnchantmentHelper.updateEnchantments(mutated, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0);
            for (var key : nextEnchants.keySet()) {
                int lv = nextEnchants.getLevel(key);
                if (lv > 0) {
                    m.set(key, lv);
                }
            }
        });

        WearOutcome outcome;
        if (EnchantmentHelper.getEnchantmentsForCrafting(mutated).isEmpty()) {
            mutated = new ItemStack(BlockRegistry.DEPLETED_TOME, 1);
            outcome = WearOutcome.DEPLETED;
        } else {
            setWearUsed(mutated, step.nextWearUsed());
            chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance.pruneToCurrentEnchantments(
                mutated,
                EnchantmentHelper.getEnchantmentsForCrafting(mutated)
            );
            outcome = step.steppedDown() ? WearOutcome.STEPPED_DOWN : WearOutcome.WORN;
        }

        chosen.shelf().setItem(chosen.slot(), mutated);
        chosen.shelf().setChanged();
        BlockState latestState = level.getBlockState(chosen.pos());
        level.sendBlockUpdated(chosen.pos(), chosen.state(), latestState, Block.UPDATE_ALL);

        if (outcome == WearOutcome.DEPLETED || outcome == WearOutcome.STEPPED_DOWN) {
            level.playSound(null, chosen.pos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.72F);
        }
        return outcome;
    }
}
