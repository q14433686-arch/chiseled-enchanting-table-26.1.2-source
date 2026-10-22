package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.compat.TransformationQueryIndex;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
 *   <li>Nothing for enchanted-book subtypes — deliberately. Two books with different enchantments
 *       are already distinct JEI entries because JEI's own {@code VanillaPlugin} registers an
 *       {@code EnchantedBookSubtypeInterpreter} for {@code Items.ENCHANTED_BOOK}; registering a
 *       second one only produces an ERROR line and is discarded. See the comment where the
 *       {@code registerItemSubtypes} override used to be.</li>
 *   <li>Info pages explaining the two things a recipe cannot express: the book must sit in a nearby
 *       chiseled bookshelf, and applying an enchantment can displace conflicting ones.</li>
 * </ul>
 */
public class ChiseledEnchantingJeiPlugin implements IModPlugin {
    /** Built lazily: JEI asks for recipes after the client has a level/registries. */
    private List<EnchantingRecipe> cachedRecipes;
    private HolderLookup.Provider cachedRegistries;
    private List<EnchantQueryIndex.Entry> cachedIndex;

    public ChiseledEnchantingJeiPlugin() {
        SyncedEnchantmentCosts.addListener(this::invalidateCache);
    }

    private void invalidateCache() {
        this.cachedIndex = null;
        this.cachedRecipes = null;
    }

    @Override
    public Identifier getPluginUid() {
        return ChiseledEnchantingTable.identifier("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
            new EnchantingRecipeCategory(guiHelper),
            new TransformationRecipeCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<EnchantingRecipe> recipes = recipes();
        if (!recipes.isEmpty()) {
            registration.addRecipes(EnchantingRecipeCategory.RECIPE_TYPE, recipes);
        }
        List<TransformationRecipe> transformations = TransformationQueryIndex.build()
            .stream()
            .map(TransformationRecipe::of)
            .toList();
        if (!transformations.isEmpty()) {
            registration.addRecipes(TransformationRecipeCategory.RECIPE_TYPE, transformations);
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
        registration.addItemStackInfo(
            List.of(new ItemStack(BlockRegistry.ARCANE_CATALYST)),
            Component.translatable("jei.chiseled_enchanting_table.info.catalyst")
        );
        registration.addItemStackInfo(
            List.of(new ItemStack(BlockRegistry.DEPLETED_TOME)),
            Component.translatable("jei.chiseled_enchanting_table.info.depleted_tome")
        );
        registration.addItemStackInfo(
            List.of(new ItemStack(BlockRegistry.CLEANSING_GRIT)),
            Component.translatable("jei.chiseled_enchanting_table.info.cleansing_grit")
        );
        registration.addItemStackInfo(
            List.of(new ItemStack(BlockRegistry.ARCANE_GRIMOIRE)),
            Component.translatable("jei.chiseled_enchanting_table.info.grimoire")
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(
            EnchantingRecipeCategory.RECIPE_TYPE,
            BlockRegistry.CHISELED_ENCHANTING_TABLE,
            Blocks.CHISELED_BOOKSHELF
        );
        registration.addCraftingStation(
            TransformationRecipeCategory.RECIPE_TYPE,
            BlockRegistry.CHISELED_ENCHANTING_TABLE
        );
    }

    // Deliberately no `registerItemSubtypes` override here.
    //
    // This class used to call `registration.registerFromDataComponentTypes(Items.ENCHANTED_BOOK,
    // DataComponents.STORED_ENCHANTMENTS)` so that two enchanted books with different enchantments
    // would be distinct JEI entries. That is redundant: JEI's own VanillaPlugin already registers
    // `EnchantedBookSubtypeInterpreter.INSTANCE` for `Items.ENCHANTED_BOOK`
    // (Library/.../plugins/vanilla/VanillaPlugin.java, `registerItemSubtypes`), and
    // `SubtypeRegistration.registerSubtypeInterpreter` reacts to a second registration by logging
    //
    //     [ERROR] An interpreter is already registered for this: minecraft:enchanted_book
    //     java.lang.IllegalArgumentException  (synthetic, for the stack trace only — nothing throws)
    //
    // and then *discarding* ours, i.e. JEI's interpreter wins either way. Observed in a real 26.1.2
    // client log with JEI 29.29.0.77 (see docs/reports/VIEWER_INTEGRATION_JEI_REI.md §7): an ERROR
    // line plus a stack trace on every world load, for no behavioural gain. `ISubtypeRegistration`
    // offers no way to ask whether an interpreter is already present, so the only correct fix is not
    // to register one. Per-enchantment book entries — and therefore the "focus a book → see what it
    // can enchant" half of this integration — keep working through JEI's own interpreter.

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
        // Rebuild when registry identity changes OR when S2C cost sync invalidated the cache.
        if (cachedRegistries != provider || cachedIndex == null) {
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
