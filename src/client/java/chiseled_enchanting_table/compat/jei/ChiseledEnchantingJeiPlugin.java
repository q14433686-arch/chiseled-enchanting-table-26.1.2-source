package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.compat.TransformationQueryIndex;
import chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen;
import chiseled_enchanting_table.gui.grimoire.ArcaneGrimoireScreen;
import chiseled_enchanting_table.gui.grimoire.GrimoireViewerBridge;
import chiseled_enchanting_table.gui.grimoire.GrimoireViewerBridge.HoveredSlot;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

/**
 * Full JEI 26.1 integration covering recipes, catalysts (including {@code Arcane Grimoire}),
 * custom screen overlays, hoverable {@code R}/{@code U}/{@code A} focus, and click-to-lookup.
 */
public class ChiseledEnchantingJeiPlugin implements IModPlugin {
    private List<EnchantingRecipe> cachedRecipes;
    private HolderLookup.Provider cachedRegistries;
    private List<EnchantQueryIndex.Entry> cachedIndex;
    private GrimoireViewerBridge.Backend bridgeBackend;

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
            Blocks.CHISELED_BOOKSHELF,
            BlockRegistry.ARCANE_GRIMOIRE
        );
        registration.addCraftingStation(
            TransformationRecipeCategory.RECIPE_TYPE,
            BlockRegistry.CHISELED_ENCHANTING_TABLE,
            BlockRegistry.ARCANE_GRIMOIRE
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 1. Render JEI's ingredient list & bookmark panels neatly beside ArcaneGrimoireScreen
        registration.addGuiScreenHandler(ArcaneGrimoireScreen.class, screen -> {
            Rect2i bounds = screen.fullGuiBounds();
            return new IGuiProperties() {
                @Override
                public Class<? extends Screen> screenClass() {
                    return ArcaneGrimoireScreen.class;
                }

                @Override
                public int guiLeft() {
                    return bounds.getX();
                }

                @Override
                public int guiTop() {
                    return bounds.getY();
                }

                @Override
                public int guiXSize() {
                    return bounds.getWidth();
                }

                @Override
                public int guiYSize() {
                    return bounds.getHeight();
                }

                @Override
                public int screenWidth() {
                    return screen.width;
                }

                @Override
                public int screenHeight() {
                    return screen.height;
                }
            };
        });

        // 2. Expose every item icon inside ArcaneGrimoireScreen to JEI's native R / U / A keybinds
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                IClickableIngredientFactory builder,
                double mouseX,
                double mouseY
            ) {
                if (Minecraft.getInstance().gui.screen instanceof ArcaneGrimoireScreen grimoire) {
                    HoveredSlot slot = grimoire.hoveredIngredient(mouseX, mouseY);
                    if (slot != null && !slot.stack().isEmpty()) {
                        return builder.createBuilder(slot.stack().copyWithCount(1))
                            .buildWithArea(slot.area());
                    }
                }
                return Optional.empty();
            }
        });

        // 3. Protect ChiseledEnchantingTableScreen top tabs/button & expose row cost icons to JEI
        registration.addGuiContainerHandler(ChiseledEnchantingTableScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ChiseledEnchantingTableScreen containerScreen) {
                return containerScreen.extraViewerExclusionAreas();
            }

            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                IClickableIngredientFactory builder,
                ChiseledEnchantingTableScreen containerScreen,
                double mouseX,
                double mouseY
            ) {
                HoveredSlot slot = containerScreen.hoveredCostIngredient(mouseX, mouseY);
                if (slot != null && !slot.stack().isEmpty()) {
                    return builder.createBuilder(slot.stack().copyWithCount(1))
                        .buildWithArea(slot.area());
                }
                return Optional.empty();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (this.bridgeBackend != null) {
            GrimoireViewerBridge.unregisterBackend(this.bridgeBackend);
        }
        IFocusFactory focusFactory = jeiRuntime.getJeiHelpers().getFocusFactory();
        this.bridgeBackend = new GrimoireViewerBridge.Backend() {
            @Override
            public boolean showRecipes(ItemStack stack) {
                jeiRuntime.getRecipesGui().show(
                    focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack)
                );
                return true;
            }

            @Override
            public boolean showUses(ItemStack stack) {
                jeiRuntime.getRecipesGui().show(
                    focusFactory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack)
                );
                return true;
            }
        };
        GrimoireViewerBridge.registerBackend(this.bridgeBackend);
    }

    @Override
    public void onRuntimeUnavailable() {
        if (this.bridgeBackend != null) {
            GrimoireViewerBridge.unregisterBackend(this.bridgeBackend);
            this.bridgeBackend = null;
        }
    }

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

    public static List<EnchantQueryIndex.Entry> index(HolderLookup.Provider provider) {
        return EnchantQueryIndex.build(provider);
    }
}
