package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

/**
 * REI mirror of the JEI row: one (enchantment, level) with every item it supports as alternatives.
 *
 * <p>Same trick as the JEI side — a display with the target item <i>and</i> the book as inputs is
 * focusable from either, so REI answers both "book → items" and "item → books" without a second
 * registry.</p>
 */
public record ReiEnchantingDisplay(Identifier enchantmentId, int level) implements Display {
    public static final MapCodec<ReiEnchantingDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.fieldOf("enchantment").forGetter(ReiEnchantingDisplay::enchantmentId),
        Codec.INT.fieldOf("level").forGetter(ReiEnchantingDisplay::level)
    ).apply(instance, ReiEnchantingDisplay::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReiEnchantingDisplay> STREAM_CODEC =
        StreamCodec.composite(
            Identifier.STREAM_CODEC, ReiEnchantingDisplay::enchantmentId,
            ByteBufCodecs.VAR_INT, ReiEnchantingDisplay::level,
            ReiEnchantingDisplay::new
        );

    public static final DisplaySerializer<ReiEnchantingDisplay> SERIALIZER =
        DisplaySerializer.of(MAP_CODEC, STREAM_CODEC, false);

    /** The index row this display renders, resolved from the client registries. */
    public Optional<EnchantQueryIndex.Entry> entry() {
        for (EnchantQueryIndex.Entry candidate : index()) {
            if (candidate.enchantmentId().equals(enchantmentId) && candidate.level() == level) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public Optional<Holder.Reference<Enchantment>> holder() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return Optional.empty();
        return minecraft.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .get(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        List<EntryIngredient> inputs = new ArrayList<>();
        entry().ifPresent(row -> inputs.add(EntryIngredient.of(
            row.supportedItems().stream().map(ReiEnchantingDisplay::ofItem).toList()
        )));
        holder().ifPresent(enchantment -> {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            int bookLevel = level;
            EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(enchantment, bookLevel));
            inputs.add(EntryIngredient.of(ofItem(book)));
        });
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        List<EntryIngredient> outputs = new ArrayList<>();
        entry().ifPresent(row -> holder().ifPresent(enchantment -> {
            ItemStack target = row.supportedItems().isEmpty()
                ? new ItemStack(Items.BOOK)
                : row.supportedItems().getFirst();
            ItemStack result = target.copy();
            int enchantmentLevel = level;
            EnchantmentHelper.updateEnchantments(result, mutable -> mutable.set(enchantment, enchantmentLevel));
            outputs.add(EntryIngredient.of(ofItem(result)));
        }));
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return ReiEnchantingCategory.ID;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(ChiseledEnchantingTable.identifier(
            "enchanting/" + enchantmentId.getNamespace() + "/" + enchantmentId.getPath() + "/" + level
        ));
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        return SERIALIZER;
    }

    private static EntryStack<ItemStack> ofItem(ItemStack stack) {
        return EntryStacks.of(stack);
    }

    /** Kept for adapters that want the raw item list without the entry wrapper. */
    public List<Item> supportedItems() {
        return entry().map(row -> row.supportedItems().stream().map(ItemStack::getItem).toList()).orElse(List.of());
    }

    private static List<EnchantQueryIndex.Entry> index() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        List<EnchantQueryIndex.Entry> cached = CACHED;
        if (cached != null) return cached;
        CACHED = EnchantQueryIndex.build(minecraft.level.registryAccess());
        return CACHED;
    }

    private static List<EnchantQueryIndex.Entry> CACHED;
}
