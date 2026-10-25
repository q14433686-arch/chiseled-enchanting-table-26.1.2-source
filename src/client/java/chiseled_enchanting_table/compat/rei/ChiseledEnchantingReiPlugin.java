package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import chiseled_enchanting_table.compat.TransformationQueryIndex;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen;
import chiseled_enchanting_table.gui.grimoire.ArcaneGrimoireScreen;
import chiseled_enchanting_table.gui.grimoire.GrimoireViewerBridge;
import chiseled_enchanting_table.gui.grimoire.GrimoireViewerBridge.HoveredSlot;
import chiseled_enchanting_table.registry.BlockRegistry;
import dev.architectury.event.CompoundEventResult;
import java.util.List;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.DisplayBoundsProvider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

/**
 * Full REI 26.1 integration covering displays, workstations (including {@code Arcane Grimoire}),
 * screen bounds &amp; exclusion zones, hover focus ({@code R}/{@code U}/{@code A}), and click lookup.
 */
public class ChiseledEnchantingReiPlugin implements REIClientPlugin {

    private static final GrimoireViewerBridge.Backend REI_BACKEND = new GrimoireViewerBridge.Backend() {
        @Override
        public boolean showRecipes(ItemStack stack) {
            return ViewSearchBuilder.builder().addRecipesFor(entry(stack)).open();
        }

        @Override
        public boolean showUses(ItemStack stack) {
            return ViewSearchBuilder.builder().addUsagesFor(entry(stack)).open();
        }
    };

    private HolderLookup.Provider cachedRegistries;
    private List<ReiEnchantingDisplay> cachedEnchanting;
    private List<ReiTransformationDisplay> cachedTransformations;

    public ChiseledEnchantingReiPlugin() {
        GrimoireViewerBridge.registerBackend(REI_BACKEND);
        SyncedEnchantmentCosts.addListener(this::invalidateDisplays);
    }

    private synchronized void invalidateDisplays() {
        this.cachedRegistries = null;
        this.cachedEnchanting = null;
        this.cachedTransformations = null;
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new ReiEnchantingCategory());
        registry.add(new ReiTransformationCategory());
        registry.addWorkstations(
            ReiEnchantingCategory.ID,
            EntryIngredient.of(entry(new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE))),
            EntryIngredient.of(entry(new ItemStack(Blocks.CHISELED_BOOKSHELF))),
            EntryIngredient.of(entry(new ItemStack(BlockRegistry.ARCANE_GRIMOIRE)))
        );
        registry.addWorkstations(
            ReiTransformationCategory.ID,
            EntryIngredient.of(entry(new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE))),
            EntryIngredient.of(entry(new ItemStack(BlockRegistry.ARCANE_GRIMOIRE)))
        );
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        // Static registrations would survive a server cost/catalog reload unchanged.
        registry.registerDisplayGenerator(ReiEnchantingCategory.ID, generator(this::enchantingDisplays));
        registry.registerDisplayGenerator(ReiTransformationCategory.ID, generator(this::transformationDisplays));
    }

    private void updateDisplays() {
        var provider = registries();
        if (provider == null) {
            invalidateDisplays();
            return;
        }
        if (this.cachedRegistries == provider && this.cachedEnchanting != null) return;
        var enchanting = new java.util.ArrayList<ReiEnchantingDisplay>();
        for (var row : EnchantQueryIndex.build(provider)) {
            EnchantQueryIndex.holder(provider, row.enchantmentId())
                .ifPresent(holder -> enchanting.add(new ReiEnchantingDisplay(row, holder)));
        }
        this.cachedEnchanting = List.copyOf(enchanting);
        this.cachedTransformations = TransformationQueryIndex.build().stream().map(ReiTransformationDisplay::new).toList();
        this.cachedRegistries = provider;
    }

    private synchronized List<ReiEnchantingDisplay> enchantingDisplays() {
        updateDisplays();
        return this.cachedEnchanting == null ? List.of() : this.cachedEnchanting;
    }

    private synchronized List<ReiTransformationDisplay> transformationDisplays() {
        updateDisplays();
        return this.cachedTransformations == null ? List.of() : this.cachedTransformations;
    }

    private static <D extends me.shedaniel.rei.api.common.display.Display>
    me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator<D> generator(java.util.function.Supplier<List<D>> displays) {
        return new me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator<>() {
            @Override
            public java.util.Optional<List<D>> generate(ViewSearchBuilder builder) {
                // Focused queries go through getRecipeFor/getUsageFor, with REI-native fuzzy filtering (including enchanted-book distinctions).
                return builder.getRecipesFor().isEmpty() && builder.getUsagesFor().isEmpty()
                    ? java.util.Optional.of(displays.get()) : java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<List<D>> getRecipeFor(EntryStack<?> focus) {
                return matching(displays.get(), focus, false);
            }

            @Override
            public java.util.Optional<List<D>> getUsageFor(EntryStack<?> focus) {
                return matching(displays.get(), focus, true);
            }
        };
    }

    private static <D extends me.shedaniel.rei.api.common.display.Display>
    java.util.Optional<List<D>> matching(List<D> displays, EntryStack<?> focus, boolean usage) {
        return java.util.Optional.of(displays.stream().filter(display -> {
            var ingredients = usage ? display.getInputEntries() : display.getOutputEntries();
            for (var ingredient : ingredients) {
                for (var entry : ingredient) {
                    if (me.shedaniel.rei.api.common.util.EntryStacks.equalsFuzzy(entry, focus)) return true;
                }
            }
            return false;
        }).toList());
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        GrimoireViewerBridge.registerBackend(REI_BACKEND);

        // 1. Enable REI overlay alongside ArcaneGrimoireScreen with exact non-overlapping bounds
        registry.registerDecider(new DisplayBoundsProvider<ArcaneGrimoireScreen>() {
            @Override
            public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
                return ArcaneGrimoireScreen.class.isAssignableFrom(screen);
            }

            @Override
            public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen) {
                return ArcaneGrimoireScreen.class.isInstance(screen)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
            }

            @Override
            public Rectangle getScreenBounds(ArcaneGrimoireScreen screen) {
                Rect2i r = screen.fullGuiBounds();
                return new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            }
        });

        // 1b. Completely suspend REI overlays (search, bookmarks, wrench) when Config modal is open over the table
        registry.registerDecider(new DisplayBoundsProvider<ChiseledEnchantingTableScreen>() {
            @Override
            public double getPriority() {
                return 100.0;
            }

            @Override
            public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
                return ChiseledEnchantingTableScreen.class.isAssignableFrom(screen);
            }

            @Override
            public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen) {
                if (screen instanceof ChiseledEnchantingTableScreen tableScreen && tableScreen.hasActiveConfigOverlay()) {
                    return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            }

            @Override
            public Rectangle getScreenBounds(ChiseledEnchantingTableScreen screen) {
                Rect2i r = screen.viewerMainGuiBounds();
                return new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            }
        });

        // 2. Keep REI panels clear of ChiseledEnchantingTableScreen's top tabs & Grimoire button
        registry.exclusionZones().register(
            ChiseledEnchantingTableScreen.class,
            screen -> screen.extraViewerExclusionAreas().stream()
                .map(r -> new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()))
                .toList()
        );

        // 3. Expose hovered item icons in both ArcaneGrimoireScreen & ChiseledEnchantingTableScreen
        registry.registerFocusedStack((screen, mouse) -> {
            if (screen instanceof ArcaneGrimoireScreen grimoire) {
                HoveredSlot slot = grimoire.hoveredIngredient(mouse.getX(), mouse.getY());
                if (slot != null && !slot.stack().isEmpty()) {
                    return CompoundEventResult.interruptTrue(entry(slot.stack().copyWithCount(1)));
                }
            } else if (screen instanceof ChiseledEnchantingTableScreen tableScreen) {
                HoveredSlot slot = tableScreen.hoveredCostIngredient(mouse.getX(), mouse.getY());
                if (slot != null && !slot.stack().isEmpty()) {
                    return CompoundEventResult.interruptTrue(entry(slot.stack().copyWithCount(1)));
                }
            }
            return CompoundEventResult.pass();
        });
    }

    private static HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) return minecraft.level.registryAccess();
        if (minecraft.getConnection() != null) return minecraft.getConnection().registryAccess();
        ChiseledEnchantingTable.LOGGER.debug("REI integration: no registry access yet, skipping displays");
        return null;
    }

    private static EntryStack<ItemStack> entry(ItemStack stack) {
        return EntryStack.of(VanillaEntryTypes.ITEM, stack);
    }
}
