package chiseled_enchanting_table.mechanics.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Optional utility: removes an {@link Items#ENCHANTED_BOOK} stack from its loot pool iff it has
 * no "Chiseled Enchanting Table" lore stamp (i.e. it's a vanilla enchanted book, not one we
 * injected).
 *
 * <p><b>Not activated by default.</b> The default injector keeps vanilla enchanted books
 * alongside ours (user decision "keep_both"). This function exists purely as a codec-registered
 * utility so a data pack or integration mod can opt-in per loot table by writing:
 * <pre>{
 *   "function": "chiseled_enchanting_table:remove_enchanted_books"
 * }</pre>
 * inside a vanilla-loot-table override, without needing our Java code to strip anything.</p>
 *
 * <p>Registered in {@link chiseled_enchanting_table.registry.LootRegistry} alongside the injector.</p>
 */
public class RemoveEnchantedBooksFunction extends LootItemConditionalFunction {
    public static final MapCodec<RemoveEnchantedBooksFunction> MAP_CODEC =
        RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, RemoveEnchantedBooksFunction::new));

    public RemoveEnchantedBooksFunction() {
        this(Optional.empty());
    }

    public RemoveEnchantedBooksFunction(Optional<Holder<LootItemCondition>> conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends LootItemConditionalFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return stack;
        // Only strip books that do NOT carry our lore stamp — this makes the function safe to
        // apply table-wide without eating our own injected books.
        var lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            return stack;
        }
        return ItemStack.EMPTY;
    }
}
