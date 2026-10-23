package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import chiseled_enchanting_table.compat.TransformationQueryIndex;
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

    public ChiseledEnchantingReiPlugin() {
        GrimoireViewerBridge.registerBackend(REI_BACKEND);
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
        HolderLookup.Provider registries = registries();
        if (registries == null) return;

        List<EnchantQueryIndex.Entry> rows = EnchantQueryIndex.build(registries);
        int added = 0;
        for (EnchantQueryIndex.Entry row : rows) {
            Holder.Reference<Enchantment> holder =
                EnchantQueryIndex.holder(registries, row.enchantmentId()).orElse(null);
            if (holder == null) continue;
            registry.add(new ReiEnchantingDisplay(row, holder));
            added++;
        }
        List<TransformationQueryIndex.Entry> transforms = TransformationQueryIndex.build();
        for (TransformationQueryIndex.Entry trans : transforms) {
            registry.add(new ReiTransformationDisplay(trans));
        }
        ChiseledEnchantingTable.LOGGER.debug(
            "REI integration: registered {} enchanting displays and {} transformation displays",
            added,
            transforms.size()
        );
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
