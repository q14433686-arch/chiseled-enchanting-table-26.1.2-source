package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import chiseled_enchanting_table.registry.BlockRegistry;
import net.minecraft.world.level.block.Blocks;

/**
 * JEI plugin registration. Declared in {@code fabric.mod.json} under the {@code jei_mod_plugin}
 * entrypoint, so JEI only ever loads this class when JEI itself is installed.
 *
 * <p>What this exposes:</p>
 * <ul>
 *   <li>One {@link EnchantingRecipeCategory} row per (enchantment, level), listing every supported
 *       item as alternatives — this is what gives both the "book → items" and "item → books"
 *       directions.</li>
 *   <li>Catalysts: the chiseled enchanting table and the chiseled bookshelf, so the category shows up
 *       under {@code Recipes} for those blocks.</li>
 *   <li>Item subtypes for enchanted books, so two books with different enchantments are distinct JEI
 *       entries (and searchable by enchantment name) — this also improves every other mod's book
 *       display.</li>
 *   <li>Info pages explaining the two things a recipe cannot express: the book must sit in a nearby
 *       chiseled bookshelf, and applying an enchantment can displace conflicting ones.</li>
 * </ul>
 */
public class ChiseledEnchantingJeiPlugin implements IModPlugin {
    /** Built lazily: JEI asks for recipes after the client has a level/registries. */
    private List<EnchantingRecipe> cachedRecipes;
    private HolderLookup.Provider cachedRegistries;
    private List<EnchantQueryIndex.Entry> cachedIndex;

    @Override
    public Identifier getPluginUid() {
        return ChiseledEnchantingTable.identifier("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new EnchantingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<EnchantingRecipe> recipes = recipes();
        if (!recipes.isEmpty()) {
            registration.addRecipes(EnchantingRecipeCategory.RECIPE_TYPE, recipes);
        }

        List<ItemStack> stations = List.of(
            new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE),
            new ItemStack(Blocks.CHISELED_BOOKSHELF)
        );
        registration.addItemStackInfo(
            stations,
            Component.translatable("jei.chiseled_enchanting_table.info.station")
        );
        registration.addItemStackInfo(
            List.of(new ItemStack(Blocks.CHISELED_BOOKSHELF)),
            Component.translatable("jei.chiseled_enchanting_table.info.bookshelf")
        );
        registration.addItemStackInfo(
            List.of(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK)),
            Component.translatable("jei.chiseled_enchanting_table.info.book")
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(
            EnchantingRecipeCategory.RECIPE_TYPE,
            BlockRegistry.CHISELED_ENCHANTING_TABLE,
            Blocks.CHISELED_BOOKSHELF
        );
    }

    /**
     * Makes enchanted books distinct JEI entries per enchantment. Without this every enchanted book
     * collapses into one entry, which would make the "focus a book → see what it can enchant" half
     * of the feature unusable.
     */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(
            net.minecraft.world.item.Items.ENCHANTED_BOOK,
            DataComponents.STORED_ENCHANTMENTS
        );
    }

    // ---------------------------------------------------------------------------------------
    // Index plumbing
    // ---------------------------------------------------------------------------------------

    private HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        HolderLookup.Provider provider = minecraft.level != null
            ? minecraft.level.registryAccess()
            : (minecraft.getConnection() != null ? minecraft.getConnection().registryAccess() : null);
        if (provider == null) {
            ChiseledEnchantingTable.LOGGER.debug(
                "JEI integration: no registry access available yet, skipping recipe registration"
            );
            return null;
        }
        // Rebuild only when the registry identity changes (world switch / datapack reload).
        if (cachedRegistries != provider) {
            cachedRegistries = provider;
            cachedIndex = EnchantQueryIndex.build(provider);
            cachedRecipes = null;
        }
        return provider;
    }

    private List<EnchantingRecipe> recipes() {
        HolderLookup.Provider provider = registries();
        if (provider == null) return List.of();
        if (cachedRecipes != null) return cachedRecipes;

        HolderLookup.RegistryLookup<Enchantment> enchantments =
            provider.lookupOrThrow(Registries.ENCHANTMENT);
        List<EnchantingRecipe> out = new ArrayList<>();
        for (EnchantQueryIndex.Entry entry : cachedIndex) {
            Holder.Reference<Enchantment> holder = enchantments
                .get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, entry.enchantmentId()))
                .orElse(null);
            if (holder == null) continue;
            out.add(EnchantingRecipe.of(entry, holder));
        }
        cachedRecipes = List.copyOf(out);
        return cachedRecipes;
    }

    /** Exposed for the REI adapter (same index, different display objects). */
    public static List<EnchantQueryIndex.Entry> index(HolderLookup.Provider provider) {
        return EnchantQueryIndex.build(provider);
    }
}
