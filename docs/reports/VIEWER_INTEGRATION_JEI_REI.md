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
  - `supportedItems`: every item `Enchantment#canEnchant` accepts (26.3:
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

Verified API notes for JEI on 26.3 (`26.3` branch, Fabric subproject, `fabricLoaderVersion=0.19.5`,
`modJavaVersion=25`):

- `IModPlugin#getPluginUid()` returns `net.minecraft.resources.Identifier` (not `ResourceLocation`).
- `IRecipeType.create(namespace, path, recipeClass)`; categories extend
  `AbstractRecipeCategory<T>(IRecipeType<T>, Component, IDrawable, width, height)`.
- Layout via `IRecipeLayoutBuilder#addInputSlot(x, y)` / `addOutputSlot(x, y)` /
  `addInvisibleIngredients(role)`; slots accept `addItemStack` / `addItemStacks`.
- `IRecipeCategory#getTooltip(ITooltipBuilder, T, IRecipeSlotsView, double, double)`; the builder
  takes `FormattedText`, and `Component` is a `FormattedText`.
- Enchanted-book subtypes: **nothing to register.** JEI's own `VanillaPlugin` already covers
  `Items.ENCHANTED_BOOK`, and a second registration through
  `ISubtypeRegistration#registerFromDataComponentTypes(...)` only logs an ERROR before being
  discarded — see §7.
- Registration hooks used: `registerCategories`, `registerRecipes`, `registerRecipeCatalysts`.

Build wiring:

- repository `https://maven.blamejared.com` (this is the maven JEI's own `buildSrc` configures);
- `compileOnly("mezz.jei:jei-26.3-fabric:${jei_version}", toolchainPins)` — **plain `compileOnly`,
  not `modCompileOnly`**: the 26.x line is unobfuscated (which is why this repo already puts
  `fabric-api` on plain `implementation`), and the first CI run confirmed Loom 1.17 does not even
  create a `modCompileOnly` configuration. The `toolchainPins` closure (§6) keeps JEI's POM from
  replacing this build's Fabric API;
- `enable_jei` in `gradle.properties` (default `true`): setting `-Penable_jei=false` excludes
  `chiseled_enchanting_table/compat/jei/**` from the client source set, which is the escape hatch for
  an environment that cannot reach the maven;
- `jei_version=latest.release` — resolves and compiles today; **pin the exact version once you
  release**, because a future JEI build can break a compile-only adapter silently;
- `fabric.mod.json`: `jei_mod_plugin` entrypoint + `"suggests": {"jei": "*"}`. JEI is never required
  at runtime and a missing JEI never touches the class.

## 4. REI (implemented)

Verified against the `26.3` branch of `shedaniel/RoughlyEnoughItems` (default branch is still `26.2`;
`26.3` exists with `base_version=26.3`, `minecraft_version=26.3`, `fabricloader_version=0.19.5`) and by
a real compile on CI.

| Piece | File |
| --- | --- |
| Plugin (`REIClientPlugin`) | `src/client/java/.../compat/rei/ChiseledEnchantingReiPlugin.java` |
| Category | `.../compat/rei/ReiEnchantingCategory.java` |
| Row model (`Display`) | `.../compat/rei/ReiEnchantingDisplay.java` |

API facts that matter (26.3 names, not the older `MatrixStack`-era API):

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

The first compile round (`aa79aee`) resolved `me.shedaniel:RoughlyEnoughItems-api-fabric:26.3.+` from
`maven.shedaniel.me`, so the coordinate was right, but produced three error classes. All three are
settled now:

| Error | Cause | Resolution |
| --- | --- | --- |
| `package me.shedaniel.math does not exist` (`Point`, `Rectangle`) | that package is **not** re-exported by the api artifact; it comes from shedaniel's cloth `basic-math` | `compileOnly("me.shedaniel.cloth:basic-math:0.6.1")` (`rei_math_version`) |
| `cannot access FluidStack` (`EntryStacks.of(ItemStack)`) | `EntryStacks`/`EntryStack` leak `dev.architectury.fluid.FluidStack` and `dev.architectury.utils.Env` | `compileOnly("dev.architectury:architectury-fabric:22.0.3")` (`rei_architectury_version`), **and** the adapter uses `EntryStack.of(VanillaEntryTypes.ITEM, …)` so it does not depend on those overloads at all |
| `cannot infer type-variable(s) D` at `addWorkstations` | signature is `(CategoryIdentifier<D>, EntryIngredient...)`, not `(…, EntryStack...)` | wrap every catalyst in `EntryIngredient.of(...)` |

All three are `compileOnly` and gated by `enable_rei`, exactly like the JEI API; REI supplies them at
runtime.

### 4.2 Book subtypes in REI need no work

JEI needed no subtype registration either — its own vanilla plugin already covers enchanted books
(§7), which this report only learned after shipping a redundant one. REI has no equivalent
registration to make at all: its own default plugin already registers an enchanted-book comparator
on the stored enchantment
(`default-plugin/src/main/java/me/shedaniel/rei/plugin/common/DefaultPlugin.java`,
`registerItemComparators(... registry.register((context, stack) -> …, Items.ENCHANTED_BOOK))` on
26.3). Books with different enchantments therefore already render as distinct, separately searchable
entries in REI — the mod only has to put the right book in the input slot, which it does.

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
- REI: compiles green on CI with `enable_rei=true` — run on `ae1fed5`, `:compileJava` and
  `:compileClientJava` BUILD SUCCESSFUL, with `RoughlyEnoughItems-api-fabric:26.3.+`,
  `cloth basic-math:0.6.1` and `architectury-fabric:22.0.3` all resolving from their mavens.
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
- Gates: `ci_static_checks.py`, `compat_check.py`, `check_release_consistency.sh --strict` pass on
  the same commit.

## 6. Hardening the compile classpath (why the viewer deps carry `exclude` clauses)

All four viewer `compileOnly` dependencies are declared with a shared `toolchainPins` closure in
`build.gradle`:

```groovy
def toolchainPins = {
    exclude group: 'net.fabricmc.fabric-api'   // pinned by this build (fabric_version)
    exclude group: 'net.fabricmc'              // loader / intermediary: pinned above
    exclude group: 'com.mojang'                // Minecraft + Mojang libraries: provided by Loom
}
```

This branch did **not** need them — and that is exactly the problem: it compiled green only because
its own `fabric-api 0.162.0+26.3` happens to win the version conflict, i.e. by being the newest
Minecraft line rather than by design. The 26.1.2 sister branch hit the failure the moment the viewers
were added to it.

### 6.1 The failure mode

Loom does not hand javac "the Minecraft jar". It builds one and applies the `injected_interfaces`
that Fabric API's modules declare, rewriting the Minecraft classes accordingly (for example
`Entity implements net.fabricmc.fabric.api.registry.fluid.EntityFluidExtension`, declared by
`fabric-content-registries-v0`). So the Fabric API on the compile classpath decides **what the
Minecraft classes look like** to javac.

A viewer POM is not just the viewer: JEI and REI both carry `net.fabricmc.fabric-api`,
`net.fabricmc` (loader/intermediary) and `com.mojang` coordinates, each pinned to their own
Minecraft line. Gradle resolves a version conflict by picking the highest, so a viewer built for a
newer or older Minecraft version can silently replace this build's Fabric API. When that happens
javac sees interfaces the resolved jar no longer supplies and the **main** source set — which does
not import a single viewer class — dies with:

```
error: cannot access EntityFluidExtension
  class file for net.fabricmc.fabric.api.registry.fluid.EntityFluidExtension not found
```

Excluding those three groups is safe: they are POM noise for a `compileOnly` dependency that is never
resolved at runtime, and neither JEI nor REI needs this mod to declare them (the runtime provides
them). The 26.2 line needs the same treatment and has not been touched by this branch — see
`docs/patches/26.2-viewer-toolchain-and-subtype.patch`.

## 7. JEI subtype registration: deliberately removed

### 7.1 The evidence

A real client log (26.1.2 branch, JEI `29.29.0.77`) shows this on **every** world load:

```
[Render thread/ERROR]: An interpreter is already registered for this: minecraft:enchanted_book
java.lang.IllegalArgumentException
	at mezz.jei.library.load.registration.SubtypeRegistration.registerSubtypeInterpreter(SubtypeRegistration.java:33)
	at mezz.jei.library.load.registration.SubtypeRegistration.registerFromDataComponentTypes(SubtypeRegistration.java:40)
	at chiseled_enchanting_table.compat.jei.ChiseledEnchantingJeiPlugin.registerItemSubtypes(ChiseledEnchantingJeiPlugin.java:99)
```

Nothing was broken by it — `SubtypeRegistration` logs that exception for its stack trace, keeps
going, and JEI's own interpreter wins — but an ERROR plus a stack trace per world load is noise, and
it is noise this mod caused.

### 7.2 Why removing the call is the fix

JEI's own `VanillaPlugin.registerItemSubtypes` already registers
`EnchantedBookSubtypeInterpreter.INSTANCE` for `Items.ENCHANTED_BOOK`, so two books with different
enchantments were already distinct, searchable entries. `ISubtypeRegistration` offers no "is one
already registered?" query, so the only correct way not to produce the ERROR is not to register one —
exactly the conclusion §4.2 reaches for REI, where the default plugin's comparator does the same job
and no mod-side registration exists to remove.

The override is gone from `ChiseledEnchantingJeiPlugin`, with a comment where it used to be so it
does not get re-added; the class javadoc now says the per-enchantment book entries come from JEI, not
from us. Behaviour is unchanged: "focus a book → see what it can enchant" still works (it is driven
by the book entry, not by who registered the interpreter).

### 7.3 The compile-check workflow's dead status capture

`.github/workflows/compile-check.yml` ran under Actions' default `bash -e`, so this was dead code:

```bash
./gradlew compileJava compileClientJava … >> build-reports/compile-java.log 2>&1
status=$?          # never reached when Gradle fails
```

On failure the shell exits at the Gradle line, so the log this branch received never contained a
`gradle exit code` line and the failure-only dependency dump never ran — precisely when it was
needed. Fixed with `status=0` + `|| status=$?`. The same step now also records the resolved
coordinates of `compileClasspath` / `clientCompileClasspath` in `build-reports/compile-java.log`,
which is what makes a floating `jei_version` / `rei_version` traceable at all (the sandbox can read
that file and nothing else).

### 7.4 State after the fix

- `53fab49` compiles green on CI (`build` and `compile-check` both success).
- The new log section proves the pins do what they claim and makes the floating coordinates visible
  in the same file:

```
=== resolved coordinates (compileClasspath / clientCompileClasspath) ===
--- compileClasspath ---
dev.architectury:architectury-fabric:22.0.3
me.shedaniel.cloth:basic-math:0.6.1
me.shedaniel:RoughlyEnoughItems-api-fabric:26.3.+ -> 26.3.824
mezz.jei:jei-26.3-fabric:latest.release -> 31.16.0.87
net.fabricmc.fabric-api:fabric-api:0.162.0+26.3
```

  i.e. the build's own `fabric-api 0.162.0+26.3` is still what the main source set compiles against,
  and no second Fabric API version appears at all. (Those two `->` arrows are the open item from the
  sync report: `jei_version=latest.release` and `rei_version=26.3.+` resolved to `31.16.0.87` and
  `26.3.824` today. Pinning them is now a two-line change with evidence attached, deliberately not
  done in this commit.)
- Cross-branch: `docs/patches/26.2-viewer-toolchain-and-subtype.patch` carries §6 + §7 to the 26.2
  line (verified to apply cleanly against `81a2a2a`); the 26.1.2 line already had all of it.
