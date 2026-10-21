# Recipe-viewer integration: JEI (shipped) and REI (planned)

> **Status.** JEI integration is implemented, compiles green on CI and was verified in-game by the
> maintainer. REI: the adapter source exists (`src/client/java/.../compat/rei/`) and its API jar
> resolves, but it is **gated off** (`enable_rei=false`) because three compile-time details are still
> open — see §4.1. Nothing about the shipped build depends on REI.

## 1. The query both viewers answer

This mod's mechanic is a two-input recipe:

```
[target item] + [enchanted book] + [material]  →  [enchanted item]   (XP level cost, optional conflict displacement)
```

Modelling it that way gives **both directions for free**, because viewers let the player focus any
input slot:

| Focus | Viewer shows |
| --- | --- |
| an enchanted book (`U`ses) | every item that book can enchant, level by level |
| an item (`U`ses) | every book that works on it, with cost and XP |
| the table / bookshelf (`R`ecipes) | the whole catalogue (catalysts) |

No reverse index, no custom screen, no second lookup UI. That is the entire design.

## 2. Shared core (already written, viewer-agnostic)

`chiseled_enchanting_table.compat.EnchantQueryIndex` — no third-party imports, so both adapters use
it:

- `build(HolderLookup.Provider)` → one row per (enchantment, level) with:
  - `supportedItems`: every item `Enchantment#canEnchant` accepts (26.3:
    `definition().supportedItems()`, a `HolderSet<Item>`), so modded items and
    "universal enchants"-style datapacks are included automatically;
  - `itemCosts` / `xpLevelCost` from `ChiseledEnchantingTableData` (the same numbers the menu
    charges);
  - `conflicts`: `Enchantment.areCompatible` failures, i.e. what a row would displace — which
    matches the mod's "applying an enchantment can override a conflicting one" behaviour.
- `forItem(...)` / `forBook(...)` helpers for adapters that want to pre-filter.

**Cost caveat (documented in code and in the UI tooltip):** the menu salts cost selection with the
player UUID, so a player may be offered a different *option* than the canonical one the viewer shows.
Viewers should present the material slot as "one of the accepted options".

**What cannot be a recipe:** the rule "the enchanted book must be lying in a nearby chiseled
bookshelf" is live world state. It is expressed as a tooltip line + info pages, not as an ingredient.

## 3. JEI (implemented)

| Piece | File |
| --- | --- |
| Plugin (`IModPlugin`) | `src/client/java/.../compat/jei/ChiseledEnchantingJeiPlugin.java` |
| Category | `.../compat/jei/EnchantingRecipeCategory.java` |
| Row model | `.../compat/jei/EnchantingRecipe.java` |

Verified API notes for JEI on 26.3 (`26.3` branch, Fabric subproject, `fabricLoaderVersion=0.19.5`,
`modJavaVersion=25`):

- `IModPlugin#getPluginUid()` returns `net.minecraft.resources.Identifier` (not `ResourceLocation`).
- `IRecipeType.create(namespace, path, recipeClass)`; categories extend
  `AbstractRecipeCategory<T>(IRecipeType<T>, Component, IDrawable, width, height)`.
- Layout via `IRecipeLayoutBuilder#addInputSlot(x, y)` / `addOutputSlot(x, y)` /
  `addInvisibleIngredients(role)`; slots accept `addItemStack` / `addItemStacks`.
- `IRecipeCategory#getTooltip(ITooltipBuilder, T, IRecipeSlotsView, double, double)`; the builder
  takes `FormattedText`, and `Component` is a `FormattedText`.
- Enchanted-book subtypes: `ISubtypeRegistration#registerFromDataComponentTypes(Item,
  DataComponentType...)` with `DataComponents.STORED_ENCHANTMENTS` — no hand-written interpreter.
- Registration hooks used: `registerCategories`, `registerRecipes`, `registerRecipeCatalysts`,
  `registerItemSubtypes`.

Build wiring:

- repository `https://maven.blamejared.com` (this is the maven JEI's own `buildSrc` configures);
- `compileOnly("mezz.jei:jei-26.3-fabric:${jei_version}")` — **plain `compileOnly`, not
  `modCompileOnly`**: the 26.x line is unobfuscated (which is why this repo already puts
  `fabric-api` on plain `implementation`), and the first CI run confirmed Loom 1.17 does not even
  create a `modCompileOnly` configuration;
- `enable_jei` in `gradle.properties` (default `true`): setting `-Penable_jei=false` excludes
  `chiseled_enchanting_table/compat/jei/**` from the client source set, which is the escape hatch for
  an environment that cannot reach the maven;
- `jei_version=latest.release` — resolves and compiles today; **pin the exact version once you
  release**, because a future JEI build can break a compile-only adapter silently;
- `fabric.mod.json`: `jei_mod_plugin` entrypoint + `"suggests": {"jei": "*"}`. JEI is never required
  at runtime and a missing JEI never touches the class.

## 4. REI (next)

Verified on the `26.3` branch of `shedaniel/RoughlyEnoughItems` (default branch is still `26.2`;
`26.3` exists with `base_version=26.3`, `minecraft_version=26.3`, `fabricloader_version=0.19.5`).

| Need | API (26.3) |
| --- | --- |
| Plugin | `me.shedaniel.rei.api.client.plugins.REIClientPlugin` (`registerCategories`, `registerDisplays`) |
| Category | `DisplayCategory<T extends Display>` — `getCategoryIdentifier()`, `getTitle()`, `getIcon()`, `setupDisplay(T, Rectangle)`, `getDisplayWidth/Height` |
| Category id | `CategoryIdentifier.of(Identifier)` |
| Registration | `CategoryRegistry#add(DisplayCategory)`; `DisplayRegistry` (via `DisplayConsumer#add(Display)`) |
| Row model | implement `Display`: `getInputEntries()`, `getOutputEntries()`, `getCategoryIdentifier()`, `getSerializer()` |
| Serializer | `DisplaySerializer.of(MapCodec<D>, StreamCodec<RegistryFriendlyByteBuf, D>)` |
| Entries | `EntryStack.of(EntryType, value)`, `EntryIngredient.of(...)` |

### 4.1 What the first compile round proved (and what is left)

First real compile with `enable_rei=true` (run on `aa79aee`) resolved
`me.shedaniel:RoughlyEnoughItems-api-fabric:26.3.+` from `maven.shedaniel.me`, so the coordinate and
the 26.3 artifact are correct. Three concrete errors remain:

| Error | Cause | Fix |
| --- | --- | --- |
| `package me.shedaniel.math does not exist` (`Point`, `Rectangle`) | that package is not re-exported by the api artifact | add the matching `me.shedaniel.math` artifact, or avoid `Point`/`Rectangle` by writing a `Renderer` |
| `cannot access FluidStack` at `EntryStacks.of(ItemStack)` | `EntryStacks` overloads reference `dev.architectury.fluid.FluidStack` | add architectury to the compile classpath, or use `EntryStack.of(VanillaEntryTypes.ITEM, stack)` |
| `cannot infer type-variable(s) D` at `CategoryRegistry#addWorkstations` | generic signature differs from the older REI line | read the 26.3 signature off the resolved jar |

The adapter files are kept in the tree (excluded from compilation while `enable_rei=false`) so the
next pass only has to fix these three points rather than start over.

Work items for the REI pass:

1. `ReiEnchantingDisplay implements Display` — reuse `EnchantQueryIndex.Entry`, build
   `EntryIngredient`s from item stacks / the book.
2. `ReiEnchantingDisplayCategory implements DisplayCategory<ReiEnchantingDisplay>` — renderer via
   `Widgets.createSlot(...)` + `Widgets.createArrow(...)` + `Widgets.createRecipeBase(bounds)`
   (needs `me.shedaniel.math.Rectangle`), title/icon from the existing lang keys.
3. Plugin class implementing `REIClientPlugin`; entrypoint `rei_client` in `fabric.mod.json`.
4. Gradle: `maven { url "https://maven.shedaniel.me/" }` +
   `compileOnly("me.shedaniel:RoughlyEnoughItems-api-fabric:${rei_version}")`, gated by
   `enable_rei` exactly like JEI. **Unverified:** the exact artifact/version string
   (`26.3.<build>`); REI publishes via its own `unified-publishing` setup, so expect one CI round to
   settle it.
5. Book subtypes: REI's equivalent of JEI's subtype interpreter is its entry/`EntryStack` handling;
   verify whether `STORED_ENCHANTMENTS` needs an explicit subtype rule before relying on "focus a
   book".

## 5. Verification state

- JEI: `compile-check` + `build` green on CI (both source sets and `remapJar`), `ci_static_checks`,
  `compat_check` and `check_release_consistency.sh --strict` all pass.
- **Not verified: in-game.** No client in the authoring sandbox. Manual pass needed:
  1. install JEI only → no crash without the mod's blocks in a world; the category appears under the
     table and the bookshelf;
  2. focus an enchanted book → items list; focus a diamond sword → book list;
  3. search `Sharpness` → returns the enchantment-level books (subtype interpreter working);
  4. tooltip shows XP, cost, conflicts and the "book must be in a bookshelf" line;
  5. a pack with `#minecraft:bookshelf_books`-only books or a modded enchantment appears with the
     right items (index is registry-driven).
