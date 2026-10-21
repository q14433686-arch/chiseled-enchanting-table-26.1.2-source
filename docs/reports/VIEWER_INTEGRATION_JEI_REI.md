# Recipe-viewer integration: JEI and REI (synced from the 26.3 branch, adapted to 26.1.2)

> **Status (this branch).** Both adapters are synced from the `26.3` branch and compile-gated by CI
> on this line as well (`build.yml` builds them, and a second job proves `-Penable_jei=false
> -Penable_rei=false` still compiles). **In-game verification on Minecraft 26.1.2 is still open** —
> see §5 for the checklist. Both integrations are `compileOnly` and gated by gradle properties
> (`enable_jei` / `enable_rei`, both `true`), so neither viewer is ever a runtime dependency.
>
> **Provenance.** The Java in `compat/jei/`, `compat/rei/` and `compat/EnchantQueryIndex` is
> byte-identical to the 26.3 branch; only the dependency coordinates and the comments that name a
> Minecraft/branch version differ. That is possible because both viewers' API surfaces were compared
> branch-by-branch first (§3.1, §4.1).

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

## 2. Shared core (viewer-agnostic, `main` source set)

`chiseled_enchanting_table.compat.EnchantQueryIndex` — no third-party imports, so both adapters use
it:

- `build(HolderLookup.Provider)` → one row per (enchantment, level) with:
  - `supportedItems`: every item in `BuiltInRegistries.ITEM` that `Enchantment#canEnchant(ItemStack)`
    accepts, so modded items and "universal enchants"-style datapacks are included automatically;
  - `itemCosts` / `xpLevelCost` from `ChiseledEnchantingTableData` (the same numbers the menu
    charges), preferring the server-synced row (§2.1);
  - `conflicts`: `Enchantment.areCompatible` failures, i.e. what a row would displace — which
    matches the mod's "applying an enchantment can override a conflicting one" behaviour.
- `forItem(...)` / `forBook(...)` helpers for adapters that want to pre-filter.
- `nameKey(...)` / `conflictSummary(...)` so both viewers render identical, translated strings.

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

> **26.1.2 note.** The payload itself needed no adaptation: this Minecraft line still has
> `FriendlyByteBuf#writeCollection` / `readCollection` (26.3 removed them, which is why the 26.3
> branch hand-rolls the *available-options* payload). `EnchantCostTablePayload` was already
> hand-rolled on the 26.3 side, so it ports byte-identically, and `ChiseledEnchantingMenuPayloads`
> on this branch keeps using the collection helpers. Both wire formats are self-consistent per
> branch; a 26.1.2 client only ever talks to a 26.1.2 server.

## 3. JEI (implemented)

| Piece | File |
| --- | --- |
| Plugin (`IModPlugin`) | `src/client/java/.../compat/jei/ChiseledEnchantingJeiPlugin.java` |
| Category | `.../compat/jei/EnchantingRecipeCategory.java` |
| Row model | `.../compat/jei/EnchantingRecipe.java` |

### 3.1 Why the 26.3 adapter compiles unchanged against JEI's 26.1 line

JEI's `26.1` branch pins `minecraftVersion=26.1.2` (`gradle.properties@26.1`) — i.e. it *is* the
JEI line for this Minecraft version, there is no separate `26.1.2` branch. Every JEI API file this
adapter touches was fetched from both `26.1` and `26.3` and diffed:

| API file (`Common/src/api/java/mezz/jei/api/…`) | 26.1 vs 26.3 |
| --- | --- |
| `recipe/category/AbstractRecipeCategory.java` | byte-identical |
| `recipe/types/IRecipeType.java` | byte-identical |
| `gui/builder/ITooltipBuilder.java` | byte-identical |
| `registration/ISubtypeRegistration.java` | byte-identical |
| `registration/IRecipeCategoryRegistration.java` | byte-identical |
| `IModPlugin.java`, `gui/builder/IRecipeLayoutBuilder.java`, `helpers/IGuiHelper.java`, `registration/IRecipeRegistration.java`, `registration/IRecipeCatalystRegistration.java` | differ **only** in `@since` / `@Deprecated(since=…)` javadoc version numbers |

So the API surface is the same and the adapter needs no source change — only the coordinate does.

API notes (unchanged from the 26.3 report, re-verified against `26.1`):

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

Build wiring on this line:

- repository `https://maven.blamejared.com` (the maven JEI's own `buildSrc` configures);
- `compileOnly("mezz.jei:jei-${minecraft_version}-fabric:${jei_version}")` →
  **`mezz.jei:jei-26.1.2-fabric:latest.release`**. JEI names its Fabric publication
  `"${modId}-${minecraftVersion}-fabric"` (`Fabric/build.gradle.kts@26.1`), and its `26.1` branch
  sets `minecraftVersion=26.1.2`, so interpolating *our* `minecraft_version` produces a real
  coordinate — no hardcoding, and the same `build.gradle` line works on every branch of this repo;
- **plain `compileOnly`, not `modCompileOnly`**: the 26.x line is unobfuscated (which is why this
  repo already puts `fabric-api` on plain `implementation`). Loom here is **1.16-SNAPSHOT**, not the
  1.17 the 26.3 branch moved to — that is a Minecraft-line choice, not a JEI requirement;
- `enable_jei` in `gradle.properties` (default `true`): `-Penable_jei=false` excludes
  `chiseled_enchanting_table/compat/jei/**` from the client source set. `build.yml`'s `no-viewers`
  job compiles exactly that configuration on every push, so the escape hatch cannot rot;
- `jei_version=latest.release` — **pin the exact version once a release is cut**, because a future
  JEI build can break a compile-only adapter silently;
- `fabric.mod.json`: `jei_mod_plugin` entrypoint + `"suggests": {"jei": "*"}`. JEI is never required
  at runtime and a missing JEI never touches the class.

## 4. REI (implemented)

| Piece | File |
| --- | --- |
| Plugin (`REIClientPlugin`) | `src/client/java/.../compat/rei/ChiseledEnchantingReiPlugin.java` |
| Category | `.../compat/rei/ReiEnchantingCategory.java` |
| Row model (`Display`) | `.../compat/rei/ReiEnchantingDisplay.java` |

### 4.1 Why the 26.3 adapter compiles unchanged against REI's 26.1 line

REI's `26.1` branch pins `base_version=26.1`, `minecraft_version=26.1.2`,
`fabric_api=0.150.0+26.1.2`, `architectury_version=20.0.6`, `cloth_config_version=26.1.154`
(`gradle.properties@26.1`). Diffed against `26.3`:

| REI file | 26.1 vs 26.3 |
| --- | --- |
| `api/src/main/java/me/shedaniel/rei/api/**` — `REIClientPlugin`, `CategoryRegistry`, `DisplayCategory`, `DisplayRegistry`, `Widgets`, `CategoryIdentifier`, `Display`, `DisplaySerializer`, `EntryIngredient`, `EntryStack`, `VanillaEntryTypes` | **all eleven byte-identical** |
| `api/build.gradle`, `settings.gradle` | byte-identical (so the published artifact names are the same) |

Only the *versions* differ, which is what `gradle.properties` carries:

| Property | 26.3 branch | this branch (26.1.2) | why |
| --- | --- | --- | --- |
| `rei_version` | `26.3.+` | `26.1.+` | REI versions are `base_version.GITHUB_RUN_NUMBER`; the `26.1` branch's `base_version` is `26.1` |
| `rei_architectury_version` | `22.0.3` | `20.0.6` | `architectury_version` in REI's own `gradle.properties` on each branch |
| `rei_math_version` | `0.6.1` | `0.6.1` | cloth `basic-math` is Minecraft-independent (single `master` branch, no per-version lines) |

API facts that matter (26.x names, not the older `MatrixStack`-era API):

- `REIClientPlugin#registerCategories(CategoryRegistry)` / `#registerDisplays(DisplayRegistry)`;
  entrypoint id `rei_client` in `fabric.mod.json`.
- `DisplayCategory<T extends Display>`: `getCategoryIdentifier()`, `getTitle()`, `getIcon()`
  (returns `me.shedaniel.rei.api.client.gui.Renderer`), `setupDisplay(T, Rectangle)`,
  `getDisplayWidth(T)`, `getDisplayHeight()`.
- `CategoryIdentifier.of(Identifier)`; `CategoryRegistry#add(DisplayCategory)`;
  `DisplayRegistry#add(Display)` (inherited from `DisplayConsumer`).
- **`CategoryRegistry#addWorkstations(CategoryIdentifier<D>, EntryIngredient...)`** — the varargs are
  `EntryIngredient`, not `EntryStack`. Wrapping each catalyst stack in
  `EntryIngredient.of(EntryStack...)` is what makes the call type-check; passing bare stacks fails
  with `cannot infer type-variable(s) D`.
- `Display`: `getInputEntries()`, `getOutputEntries()`, `getCategoryIdentifier()`,
  `getDisplayLocation()`, `getSerializer()`. `getSerializer()` is `@Nullable`, so a client-only
  display returns `null` — no codecs, nothing persisted or synced.
- Entries: `EntryStack.of(VanillaEntryTypes.ITEM, stack)`, `EntryIngredient.of(EntryStack)` /
  `of(Iterable<? extends EntryStack<? extends T>>)`.
- Widgets: `Widgets.createRecipeBase(Rectangle)`, `createSlot(Point)` (`.entries(...)`,
  `.markInput()`, `.markOutput()`), `createArrow(Point)`, `createLabel(Point, Component)`
  (`.tooltip(Component...)`). `Point`/`Rectangle` live in `me.shedaniel.math` (cloth basic-math).

### 4.2 Compile classpath: the api artifact alone is not enough

The 26.3 branch hit three compile errors here (`aa79aee` → `ae1fed5`). All three resolutions carry
over, with the version-appropriate coordinates:

| Error | Cause | Resolution on this branch |
| --- | --- | --- |
| `package me.shedaniel.math does not exist` (`Point`, `Rectangle`) | not re-exported by the api artifact; it comes from shedaniel's cloth `basic-math` | `compileOnly("me.shedaniel.cloth:basic-math:0.6.1")` (`rei_math_version`) |
| `cannot access FluidStack` (`EntryStacks.of(ItemStack)`) | `EntryStacks`/`EntryStack` leak `dev.architectury.fluid.FluidStack` and `dev.architectury.utils.Env` | `compileOnly("dev.architectury:architectury-fabric:20.0.6")` (`rei_architectury_version`), **and** the adapter uses `EntryStack.of(VanillaEntryTypes.ITEM, …)` so it does not depend on those overloads at all |
| `cannot infer type-variable(s) D` at `addWorkstations` | signature is `(CategoryIdentifier<D>, EntryIngredient...)`, not `(…, EntryStack...)` | wrap every catalyst in `EntryIngredient.of(...)` |

All three are `compileOnly` and gated by `enable_rei`, exactly like the JEI API; REI supplies them at
runtime.

### 4.3 Book subtypes in REI need no work

JEI needed an explicit subtype registration (`registerFromDataComponentTypes(...,
STORED_ENCHANTMENTS)`). REI has no equivalent registration to make: its own default plugin already
registers an enchanted-book comparator on the stored enchantment
(`default-plugin/src/main/java/me/shedaniel/rei/plugin/common/DefaultPlugin.java`,
`registerItemComparators(... registry.register((context, stack) -> …, Items.ENCHANTED_BOOK))`).
Books with different enchantments therefore already render as distinct, separately searchable
entries in REI — the mod only has to put the right book in the input slot, which it does.

### 4.4 Layout

Mirrors the JEI category so the two viewers look the same: three input slots (target items as one
ingredient of alternatives, the level-specific book, the material cost when the enchantment has one),
an arrow, and the output. The row of text under the slots is the XP cost; its tooltip carries what a
recipe cannot express — "the material is one of several accepted options", "the book must lie in a
nearby chiseled bookshelf", and the comma-separated conflicts list. Both adapters build the conflict
list through `EnchantQueryIndex.conflictSummary(...)`, so translations stay in one place and ship in
all eleven languages.

## 5. Verification state on this branch

Verified here (no network needed, run in the dev sandbox):

- `python3 scripts/ci_static_checks.py` → PASS (mixin configs resolve; lang parity across 11 files).
- `python3 scripts/compat_check.py` → PASS, including the two new 26.1.2-schema gates.
- `bash scripts/check_release_consistency.sh --strict` → PASS.
- The JEI/REI API surfaces were diffed against the viewers' own `26.1` branches (§3.1, §4.1); the
  adapter sources are byte-identical to the 26.3 branch, which CI compiled green there.

Pending (this branch has no compile history of its own yet):

1. `compile-check.yml` green on the sync commit — the only channel that can resolve
   `maven.fabricmc.net`, `maven.blamejared.com` and `maven.shedaniel.me`. Read it back with
   `gh api repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch>`.
2. `build.yml`'s `no-viewers` job green (`-Penable_jei=false -Penable_rei=false`).
3. In-game on Minecraft 26.1.2, JEI only: the category appears under the table and the bookshelf;
   book → items and item → books both work; enchanted-book subtypes are searchable.
4. In-game, REI only: same pass, plus search `Sharpness` → per-level books are distinct entries.
5. In-game, JEI **and** REI together: both categories coexist, no duplicate class or entrypoint error.
6. Cost sync: join a **dedicated server** whose `enchantment_costs.json` differs from the jar (e.g.
   swap lapis for amethyst) → the viewer row, the tooltip and the table GUI all show amethyst;
   `/reload` after editing the file → costs update without reconnecting; leave the server → the cache
   is cleared (single-player afterwards shows its own costs again).
