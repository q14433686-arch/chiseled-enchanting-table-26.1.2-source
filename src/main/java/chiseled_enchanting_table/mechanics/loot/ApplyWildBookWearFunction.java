package chiseled_enchanting_table.mechanics.loot;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Applies data-driven pre-wear ({@code wild_book_wear} in {@code library_resonance.json}, weighted so
 * {@code 80%} of non-{@code Infinity} wild Enchanted Books spawn with {@code 1–2} durability left)
 * to Enchanted Books rolled from world structure loot tables.
 */
public class ApplyWildBookWearFunction extends LootItemConditionalFunction {
    public static final MapCodec<ApplyWildBookWearFunction> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, ApplyWildBookWearFunction::new));

    public ApplyWildBookWearFunction() {
        this(Optional.empty());
    }

    public ApplyWildBookWearFunction(Optional<Holder<LootItemCondition>> condition) {
        super(condition);
    }

    @Override
    public MapCodec<? extends LootItemConditionalFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(Items.ENCHANTED_BOOK)) {
            return stack;
        }
        MasterTomeWear.applyWildBookWear(
            stack,
            context.getRandom(),
            ChiseledEnchantingTableData.libraryResonanceConfig()
        );
        return stack;
    }
}
