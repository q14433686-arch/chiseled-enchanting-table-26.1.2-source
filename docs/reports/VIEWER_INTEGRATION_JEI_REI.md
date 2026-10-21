# Recipe-viewer integration: JEI and REI (both implemented)

> **Status.** Both adapters are implemented and compile green on CI. JEI was verified in-game by the
> maintainer; REI still needs that manual pass (§5). Both are `compileOnly` integrations gated by
> gradle properties (`enable_jei` / `enable_rei`, both `true`), so neither viewer is ever a runtime
> dependency and `-Penable_rei=false` still builds.

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
  - `supportedItems`: every item `Enchantment#canEnchant` accepts (26.2:
    `definition().supportedItems()`, a `HolderSet<Item>`), so modded items and
    "universal enchants"-style datapacks are included automatically;
  - `itemCosts` / `xpLevelCost` from `ChiseledEnchantingTableData` (the same numbers the menu
    charges);
  - `conflicts`: `Enchantment.areCompatible` failures, i.e. what a row would displace — which
    matches the mod's "applying an enchantment can override a conflicting one" behaviour.
- `forItem(...)` / `forBook(...)` helpers for adapters that want to pre-filter.

### 2.1 Costs are whatever the server says (S2C sync)

The material cost is not derivable on the client:

- the cost *table* is a server data-pack file — `ChiseledEnchantingTableData` is registered on
  `PackType.SERVER_DATA`, so a dedicated server's customised `enchantment_costs.json` never reaches
  the client;
- the cost *option* is salted with the player UUID
  (`stablePositiveHash(id:level:maxLevel:uuid:option) % options.size()`), so even the same table
  yields a per-player answer.

So the server resolves one row per (enchantment, level) with the joining player's UUID and ships it:

| Piece | File | Role |
| --- | --- | --- |
| `EnchantCostTablePayload` | `chiseled_enchanting_table/menu/EnchantCostTablePayload.java` | S2C payload, id `chiseled_enchanting_table:enchant_cost_table`; hand-rolled wire format (`Identifier` + VarInts) because every cost is a plain `item + count` |
| `EnchantCostSync` | `.../menu/EnchantCostSync.java` | registers `PayloadTypeRegistry.clientboundPlay()`, pushes the table on `ServerPlayConnectionEvents.JOIN` and after every successful `ServerLifecycleEvents.END_DATA_PACK_RELOAD` (so `/reload` does not leave stale costs); `ServerPlayNetworking.canSend` keeps vanilla clients out |
| `SyncedEnchantmentCosts` | `.../compat/SyncedEnchantmentCosts.java` | client-side cache, cleared on `ClientPlayConnectionEvents.DISCONNECT` |

Consumers:

- **Viewers** — `EnchantQueryIndex.build(...)` prefers the synced row and only falls back to the
  canonical empty-salt option when nothing was synced; `Entry#serverCosts()` records which one it
  used, so the tooltip says either "resolved by the server for you"
  (`jei.chiseled_enchanting_table.tooltip.cost_synced`) or the older "may be one of several accepted
  items" line.
- **The table's own GUI** — `ChiseledEnchantingTableScreenHandler` reads the same cache for
  `OptionType.ENCHANTMENT` rows, guarded by `world.isClientSide()`: the server always resolves
  locally (so a LAN host can never hand one player another player's salted option), while a client
  shows exactly what the server will charge. This also fixes the previous behaviour where a dedicated
  server with a customised cost table showed jar-default costs in the GUI.
- Transformations and book upgrades are *not* in the payload — they stay server-resolved at use time,
  because their rows are not part of the viewer catalogue.

**What cannot be a recipe:** the rule "the enchanted book must be lying in a nearby chiseled
bookshelf" is live world state. It is expressed as a tooltip line + info pages, not as an ingredient.

## 3. JEI (implemented)

| Piece | File |
| --- | --- |
| Plugin (`IModPlugin`) | `src/client/java/.../compat/jei/ChiseledEnchantingJeiPlugin.java` |
| Category | `.../compat/jei/EnchantingRecipeCategory.java` |
| Row model | `.../compat/jei/EnchantingRecipe.java` |

Verified API notes for JEI on 26.2 (`26.2` branch, Fabric subproject, `fabricLoaderVersion=0.19.4`,
`modJavaVersion=25`):

- `IModPlugin#getPluginUid()` returns `net.minecraft.resources.Identifier` (not `ResourceLocation`).
- `IRecipeType.create(namespace, path, recipeClass)`; categories extend
  `AbstractRecipeCategory<T>(IRecipeType<T>, Component, IDrawable, width, height)`.
- Layout via `IRecipeLayoutBuilder#addInputSlot(x, y)` / `addOutputSlot(x, y)` /
  `addInvisibleIngredients(role)`; slots accept `addItemStack` / `addItemStacks`.
- `IRecipeCategory#getTooltip(ITooltipBuilder, T, IRecipeSlotsView, double, double)`; the builder
  takes `FormattedText`, and `Component` is a `FormattedText`.
- Enchanted-book subtypes: **do not register one**. JEI's own `VanillaPlugin` already registers
  `EnchantedBookSubtypeInterpreter.INSTANCE` for `Items.ENCHANTED_BOOK`
  (`Library/.../plugins/vanilla/VanillaPlugin.java`, `registerItemSubtypes`), and a second
  registration via `ISubtypeRegistration#registerFromDataComponentTypes(Items.ENCHANTED_BOOK,
  DataComponents.STORED_ENCHANTMENTS)` causes `SubtypeRegistration` to log
  `[ERROR] An interpreter is already registered for this: minecraft:enchanted_book` (with a
  synthetic `IllegalArgumentException` stack trace) on every world load and discard ours anyway.
  Per-enchantment book entries keep working through JEI's built-in interpreter.
- Registration hooks used: `registerCategories`, `registerRecipes`, `registerRecipeCatalysts`
  (no `registerItemSubtypes`).

Build wiring:

- repository `https://maven.blamejared.com` (this is the maven JEI's own `buildSrc` configures);
- `compileOnly("mezz.jei:jei-26.2-fabric:${jei_version}", toolchainPins)` — **plain `compileOnly`,
  not `modCompileOnly`**, with `toolchainPins` excluding `net.fabricmc.fabric-api`, `net.fabricmc`,
  and `com.mojang` so the viewer's POM can never displace the build's pinned Fabric API / loader /
  Minecraft jars (§6.1);
- `enable_jei` in `gradle.properties` (default `true`): setting `-Penable_jei=false` excludes
  `chiseled_enchanting_table/compat/jei/**` from the client source set, which is the escape hatch for
  an environment that cannot reach the maven;
- `jei_version=30.47.0.239` — pinned to the exact coordinate verified by `compile-check.yml` on
  2026-10-22 (§6), so future upstream JEI builds cannot break this compile-only adapter silently;
- `fabric.mod.json`: `jei_mod_plugin` entrypoint + `"suggests": {"jei": "*"}`. JEI is never required
  at runtime and a missing JEI never touches the class.

## 4. REI (implemented)

Verified against the `26.2` branch of `shedaniel/RoughlyEnoughItems` (`base_version=26.2`,
`minecraft_version=26.2`, `fabricloader_version=0.19.3`, `architectury_version=21.0.2`) and by
a real compile on CI.

| Piece | File |
| --- | --- |
| Plugin (`REIClientPlugin`) | `src/client/java/.../compat/rei/ChiseledEnchantingReiPlugin.java` |
| Category | `.../compat/rei/ReiEnchantingCategory.java` |
| Row model (`Display`) | `.../compat/rei/ReiEnchantingDisplay.java` |

API facts that matter (26.x names, not the older `MatrixStack`-era API):

- `REIClientPlugin#registerCategories(CategoryRegistry)` / `#registerDisplays(DisplayRegistry)`;
  entrypoint id `rei_client` in `fabric.mod.json`.
- `DisplayCategory<T extends Display>`: `getCategoryIdentifier()`, `getTitle()`, `getIcon()`
  (returns `me.shedaniel.rei.api.client.gui.Renderer`), `setupDisplay(T, Rectangle)`,
  `getDisplayWidth(T)`, `getDisplayHeight()`. `DisplayCategoryView` only declares
  `getDisplayRenderer` and `setupDisplay`, so everything else is optional.
- `CategoryIdentifier.of(Identifier)`; `CategoryRegistry#add(DisplayCategory)`;
  `DisplayRegistry#add(Display)` (inherited from `DisplayConsumer`).
- **`DisplayRegistry#addWorkstations(CategoryIdentifier<D>, EntryIngredient...)`** — the varargs are
  `EntryIngredient`, not `EntryStack`. Wrapping each catalyst stack in
  `EntryIngredient.of(EntryStack...)` is what makes the call type-check; passing bare stacks fails
  with `cannot infer type-variable(s) D`.
- `Display`: `getInputEntries()`, `getOutputEntries()`, `getCategoryIdentifier()`,
  `getDisplayLocation()`, `getSerializer()`. `getSerializer()` is `@Nullable`, so a client-only
  display returns `null` — no codecs, nothing persisted or synced.
- Entries: `EntryStack.of(VanillaEntryTypes.ITEM, stack)`, `EntryIngredient.of(EntryStack)` /
  `of(Iterable<? extends EntryStack<? extends T>>)`. `EntryIngredient` *is* a
  `List<EntryStack<?>>`, so it can be handed straight to `Slot#entries(Collection)`.
- Widgets: `Widgets.createRecipeBase(Rectangle)`, `createSlot(Point)` (`.entries(...)`,
  `.markInput()`, `.markOutput()`), `createArrow(Point)`, `createLabel(Point, Component)`
  (`.tooltip(Component...)`). `Point`/`Rectangle` live in `me.shedaniel.math` (cloth basic-math).

### 4.1 Compile classpath: the api artifact alone is not enough

The first compile round (`aa79aee`) resolved `me.shedaniel:RoughlyEnoughItems-api-fabric:26.2.+` from
`maven.shedaniel.me`, so the coordinate was right, but produced three error classes. All three are
settled now:

| Error | Cause | Resolution |
| --- | --- | --- |
| `package me.shedaniel.math does not exist` (`Point`, `Rectangle`) | that package is **not** re-exported by the api artifact; it comes from shedaniel's cloth `basic-math` | `compileOnly("me.shedaniel.cloth:basic-math:0.6.1")` (`rei_math_version`) |
| `cannot access FluidStack` (`EntryStacks.of(ItemStack)`) | `EntryStacks`/`EntryStack` leak `dev.architectury.fluid.FluidStack` and `dev.architectury.utils.Env` | `compileOnly("dev.architectury:architectury-fabric:21.0.4")` (`rei_architectury_version`), **and** the adapter uses `EntryStack.of(VanillaEntryTypes.ITEM, …)` so it does not depend on those overloads at all |
| `cannot infer type-variable(s) D` at `addWorkstations` | signature is `(CategoryIdentifier<D>, EntryIngredient...)`, not `(…, EntryStack...)` | wrap every catalyst in `EntryIngredient.of(...)` |

All three are `compileOnly` and gated by `enable_rei`, exactly like the JEI API; REI supplies them at
runtime.

### 4.2 Book subtypes in both JEI and REI need no work

Neither viewer needs an explicit subtype registration from this mod:
- **JEI:** JEI's own `VanillaPlugin` already registers `EnchantedBookSubtypeInterpreter.INSTANCE`
  for `Items.ENCHANTED_BOOK`. Registering a duplicate via `registerFromDataComponentTypes(...,
  STORED_ENCHANTMENTS)` logs `[ERROR] An interpreter is already registered for this:
  minecraft:enchanted_book` on every world load and is discarded (`ChiseledEnchantingJeiPlugin`
  intentionally omits `registerItemSubtypes`).
- **REI:** REI's own default plugin already registers an enchanted-book comparator on the stored
  enchantment (`default-plugin/src/main/java/me/shedaniel/rei/plugin/common/DefaultPlugin.java`,
  `registerItemComparators(... registry.register((context, stack) -> …, Items.ENCHANTED_BOOK))`).

Books with different enchantments therefore already render as distinct, separately searchable
entries in both JEI and REI — the mod only has to put the right book in the input slot, which it does.

### 4.3 Layout

Mirrors the JEI category so the two viewers look the same: three input slots (target items as one
ingredient of alternatives, the level-specific book, the material cost when the enchantment has one),
an arrow, and the output. The row of text under the slots is the XP cost; its tooltip carries what a
recipe cannot express — "the material is one of several accepted options", "the book must lie in a
nearby chiseled bookshelf", and the comma-separated conflicts list. Both adapters build the conflict
list through `EnchantQueryIndex.conflictSummary(...)`, so translations stay in one place.

## 5. Verification state

- JEI: compiles green on CI and **verified in-game by the maintainer** — category appears under the
  table and the bookshelf, book → items and item → books both work, book subtypes are searchable.
- REI: compiles green on CI with `enable_rei=true` (`RoughlyEnoughItems-api-fabric:26.2.+`,
  `cloth basic-math:0.6.1` and `architectury-fabric:21.0.4` all resolving from their mavens).
  **Not verified in-game yet** — needs the same manual pass:
  1. install REI only (no JEI) → no crash, the category appears under the table and the bookshelf;
  2. focus an enchanted book → item list; focus a diamond sword → book list (both directions come
     from the two input slots);
  3. search `Sharpness` → enchantment-level books are distinct entries (REI's default comparator);
  4. the XP line under the slots shows the level cost and its tooltip shows the cost caveat, the
     bookshelf rule and the conflicts line;
  5. install JEI **and** REI together → both categories coexist, no duplicate class or entrypoint
     error.
- Cost sync compiles green on CI: `6e10544`, `:compileJava` + `:compileClientJava`
  BUILD SUCCESSFUL (payload in the main source set, receiver in the client source set).
- Cost sync (both viewers): join a **dedicated server** whose `enchantment_costs.json` differs from
  the jar (e.g. swap lapis for amethyst) → the viewer row, the tooltip and the table GUI all show
  amethyst; run `/reload` after editing the file → costs update without reconnecting; leave the
  server → the cache is cleared (single-player afterwards shows its own costs again).
- Gates: `ci_static_checks.py`, `compat_check.py`, `check_release_consistency.sh --strict`, and
  `docs/ci/install.sh --check` pass on the same commit.

## 6. Resolved coordinates on 26.2 and toolchain exclusions (`toolchainPins`)

`compile-check.yml` records the exact coordinates resolved on `compileClasspath` and
`clientCompileClasspath` into `build-reports/compile-java.log`. On 2026-10-22 (commit `61b2431`,
check run `119488382287`), the 26.2 build resolved:

| Dependency | Floating spec | Resolved on 2026-10-22 | Pinned in `gradle.properties` |
| --- | --- | --- | --- |
| `mezz.jei:jei-26.2-fabric` | `latest.release` | `30.47.0.239` | `30.47.0.239` |
| `me.shedaniel:RoughlyEnoughItems-api-fabric` | `26.2.+` | `26.2.821` | `26.2.821` |
| `me.shedaniel.cloth:basic-math` | `0.6.1` | `0.6.1` | `0.6.1` |
| `dev.architectury:architectury-fabric` | `21.0.4` | `21.0.4` | `21.0.4` |
| `net.fabricmc.fabric-api:fabric-api` | `0.155.2+26.2` | `0.155.2+26.2` (**unchanged**) | `0.155.2+26.2` |
| `net.fabricmc:fabric-loader` | `0.19.3` | `0.19.3` (**unchanged**) | `0.19.3` |

Both floating viewer coordinates are now pinned in `gradle.properties` (`jei_version=30.47.0.239`,
`rei_version=26.2.821`). In `jei-26.2-fabric:30.47.0.239`, `IIngredientAcceptor#addItemStack` is
`@Deprecated(forRemoval = true)` (producing the two javac warnings at `EnchantingRecipeCategory.java`
lines 60 and 71); when bumping `jei_version` in the future, migrate `addItemStack(stack)` →
`add(stack)` in the same commit.

### 6.1 Why `toolchainPins` excludes `net.fabricmc.fabric-api`, `net.fabricmc`, and `com.mojang`

As discovered on the `26.1.2` sister branch (`18b851a`), a viewer artifact's POM can transitively
depend on a newer Fabric API or loader for a different Minecraft version. Because Loom applies
Fabric API's declared `injected_interfaces` (such as `EntityFluidExtension` on `Entity`) when
preparing the Minecraft jar, a transitive Fabric API version bump causes `compileJava` to fail in
the **main** source set (`cannot access EntityFluidExtension`). All four viewer `compileOnly`
dependencies in `build.gradle` therefore share the `toolchainPins` exclusion closure so optional
viewer POMs can never move this build's pinned `fabric-api` (`0.155.2+26.2`), `fabric-loader`
(`0.19.3`), or `com.mojang` libraries.

