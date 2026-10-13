# Porting Report — 26.2 → 26.3 (Fabric branch)

> **Date:** 2026-10-13 · **Branch:** `arena/2e402cd9-chiseled-enchanting-table-26-1` (26.3 variant)
> **Scope:** Turn the 26.2 unofficial port into a Minecraft **26.3** Fabric build, and put a
> TACZR-style CI triad in place *first* so the port can be compile-verified from a sandbox that has
> no route to the Fabric/Mojang maven repositories.
> **Verdict:** Small but real change surface — **5 source files, 3 data files, 5 version
> declarations**. Everything below was verified against actual 26.3 code (not inferred): the Fabric
> API half by git-blob comparison of `FabricMC/fabric` tags `0.155.2+26.2` vs `0.162.0+26.3`, the
> Mojang half by reading decompiled **Minecraft 26.3** sources (`0khs/minecraft_source_26.3`,
> pushed 2026-10-09).

---

## 0. Version matrix (all four pins confirmed, not guessed)

| Component | 26.2 (was) | 26.3 (now) | Evidence |
|---|---|---|---|
| Minecraft | `26.2` | `26.3` (stable) | `FabricMC/fabric@26.3` → `gradle.properties`: `minecraft_version=26.3`, `prerelease=false` |
| Fabric API | `0.155.2+26.2` | `0.162.0+26.3` | latest 26.3 release tag `0.162.0+26.3`, published 2026-10-06 (`repos/FabricMC/fabric/releases`) |
| Fabric Loader | `0.19.3` | `0.19.5` | latest loader release `0.19.5`, 2026-08-28; Fabric API 26.3 only requires `fabricloader >=0.19.3` |
| Loom | `1.17-SNAPSHOT` | `1.17-SNAPSHOT` (unchanged) | `FabricMC/fabric@26.3` → `gradle/libs.versions.toml`: `fabric-loom = "1.17.20"` (same 1.17 line) |
| Java | `25` | `25` (unchanged) | `fabric-api@0.162.0+26.3` → `fabric.mod.json`: `"java": ">=25"` |
| Gradle wrapper | `9.5.1` | `9.5.1` (unchanged) | no 26.3 requirement found |
| `mod_version` | `1.0.2` | `1.0.3` | new MC line = new release; see §6 for the gate that keeps the five declarations in sync |

---

## 1. Method — how each claim was checked

| Claim type | How it was verified | Reproducible from |
|---|---|---|
| Fabric API class stability | `git tree` blob-SHA comparison of every class this mod imports, between tags `0.155.2+26.2` and `0.162.0+26.3` | `gh api repos/FabricMC/fabric/git/trees/<tag>?recursive=1` |
| Vanilla class/method signatures | Read the decompiled 26.3 source of each touched class (CFR output, MC 26.3) | `gh api repos/0khs/minecraft_source_26.3/contents/<path>` |
| Vanilla data-file schemas | Read the codec declarations (`RecordCodecBuilder` field names) of the 26.3 classes | same repo, e.g. `LootPool.java`, `RecipeCraftedTrigger.java` |
| Cross-check of the render-layer story | Sister repo's completed 26.3 changelog (blaze3d → renderpearl, `PoseStack#mulPose → rotate`, shader `#include`, first-person split) | `q14433686-arch/TaCZ_Refabricated_Unofficial@26.3` → `docs/CHANGELOG_26_3_R1.md` |
| Compile verification | **GitHub Actions** (`compile-check.yml` pushes the javac log back to this branch) | `build-reports/compile-java.log` on this branch |

The sandbox cannot run `./gradlew` (no `maven.fabricmc.net`, no `piston-meta.mojang.com`), which is
exactly why the CI triad was built first — see §6.

---

## 2. Fabric API: 10 imported classes, 9 byte-identical, 1 javadoc-only

Every `net.fabricmc.fabric.*` class this mod imports, compared by blob SHA between the two tags:

| Class | 26.2 → 26.3 |
|---|---|
| `client.networking.v1.ClientPlayNetworking` | identical (`e22427c`) |
| `client.rendering.v1.BlockEntityRendererRegistry` | identical (`4aa37db`) |
| `loot.v3.LootTableEvents` | identical (`912a6e9`) |
| `loot.v3.LootTableSource` | identical (`6e2af89`) |
| `menu.v1.ExtendedMenuProvider` | identical (`953c069`) |
| `menu.v1.ExtendedMenuType` | **changed — javadoc example only**; no signature/API change (`8cebc4a` → `6b602ee`) |
| `networking.v1.PayloadTypeRegistry` | identical (`af320c2`) |
| `networking.v1.ServerPlayNetworking` | identical (`8763a39`) |
| `object.builder.v1.block.entity.FabricBlockEntityTypeBuilder` | identical (`07d4704`) |
| `resource.SimpleSynchronousResourceReloadListener` | identical (`944e562`) |

⇒ **No Fabric-API-side code change was needed for 26.3.** (The `loot.v3` package did not become
`v4`; `fabric-loot-api-v3` is still the only loot module on the 26.3 branch.)

---

## 3. Verified breaking changes and the edits made

### 3.1 Client renderer — `PoseStack` rotation API (`render/ChiseledEnchantingTableBlockEntityRenderer.java`)

26.3 `com.mojang.blaze3d.vertex.PoseStack` no longer has `mulPose(Quaternionf)`; it exposes
`rotate(Quaternionfc)`, `rotate(Axis, float)` and `rotateDegrees(Axis, float)`. Vanilla's own
`EnchantTableRenderer` (the class this renderer mirrors) now reads:

```java
poseStack.rotate(Axis.YP, -yRot);
poseStack.rotateDegrees(Axis.ZP, 80.0f);
```

**Edit:** `mulPose(Axis.YP.rotation(-yRot))` → `rotate(Axis.YP, -yRot)`;
`mulPose(Axis.ZP.rotationDegrees(80F))` → `rotateDegrees(Axis.ZP, 80F)`.

### 3.2 Client renderer — `submitModel` lost its break-progress argument

26.3 `OrderedSubmitNodeCollector` declares
`submitModel(Model, S, PoseStack, int lightCoords, int overlayCoords, int tintedColor, SpriteId, SpriteGetter, int outlineColor)`
— **9 parameters**. Our call passed 10 (trailing `renderState.breakProgress`), which no longer
exists. Vanilla submits the crumbling overlay separately:

```java
if (state.breakProgress != null) {
    submitNodeCollector.order(1).submitCrumblingOverlay(bookModel, bookState, poseStack,
        BOOK_TEXTURE.renderType(bookModel.renderType()), state.lightCoords, OverlayTexture.NO_OVERLAY,
        -1, state.breakProgress);
}
```

**Edit:** dropped the 10th argument and added the vanilla-shaped `order(1).submitCrumblingOverlay(...)`
branch (so breaking the table still shows the crack overlay). `Model#renderType()` and
`SpriteId#renderType(Function)` were both confirmed present in 26.3.

### 3.3 Client screen — stale `blaze3d.pipeline.RenderPipeline` import

26.3 moved the pipeline state classes to `com.mojang.renderpearl.api.pipeline.*`
(`blaze3d/pipeline/` in 26.3 keeps only `MainTarget`, `PipelineCache`, `RenderTarget`, `TextureTarget`).
`ChiseledEnchantingTableScreen` imported `com.mojang.blaze3d.pipeline.RenderPipeline` but never used
it (its `ctx.blit(Identifier, …)` call resolves to the 9-argument overload that still exists in
26.3 `GuiGraphicsExtractor`). **Edit:** removed the dead import.

### 3.4 Loot builder API — number providers and entry builders (`mechanics/loot/EnchantedBookLootInjector.java`)

| 26.2 | 26.3 | 26.3 evidence |
|---|---|---|
| `providers.number.ConstantValue.exactly(int)` | `providers.number.ints.ContextIntProviders.exactly(int)` → `Holder<ContextIntProvider>` | `ConstantValue` no longer exists under `providers/number/`; `LootPool.CODEC` uses `ContextIntProviders.CODEC` for `rolls` |
| `entries.LootPoolSingletonContainer.Builder<?>` | `entries.UniformContainerBase.Builder<?>` | `LootPoolSingletonContainer` is gone; `LootItem.lootTableItem(...)` and `EmptyLootItem.emptyItem()` both return `UniformContainerBase.Builder<?>` |

`LootPool.Builder.setRolls(Holder<ContextIntProvider>)`, `LootPool.Builder.add(LootPoolEntryContainer.Builder<?>)`,
`UniformContainerBase.Builder.setWeight(int)`, `SetEnchantmentsFunction.Builder.withEnchantment(Holder, Holder<ContextIntProvider>)`,
`SetLoreFunction.setLore().addLine(Component)` and `LootTable.Builder.withPool(LootPool.Builder)` were
all confirmed unchanged, as were the `FunctionUserBuilder.apply(LootItemFunction.Builder)` /
`ConditionUserBuilder.when(LootItemCondition.Builder)` convenience overloads.

### 3.5 Custom loot function — condition became a single optional holder (`mechanics/loot/RemoveEnchantedBooksFunction.java`)

26.3: `protected LootItemConditionalFunction(Optional<Holder<LootItemCondition>> condition)` (was a
`List<LootItemCondition>`). **Edit:** constructors now take `Optional<Holder<LootItemCondition>>`
(default `Optional.empty()`); the javadoc example was updated to the 26.3 `"type"` dispatch key.

### 3.6 Networking — `FriendlyByteBuf` collection helpers removed (`menu/ChiseledEnchantingMenuPayloads.java`)

26.3 `FriendlyByteBuf` (now `extends ByteBuf`) has **no** `writeCollection`, `readCollection`,
`readList` or `readMap`/`writeMap`; that family moved into `ByteBufCodecs` collection codecs. The
sister port hit the same wall (`FriendlyByteBuf#readMap/writeMap` removed → `BufMapCodec`).

**Edit:** `AvailableEnchantmentPayload` now hand-rolls the identical wire format (VarInt length
prefix + elements) using `writeVarInt`/`readVarInt` (both confirmed present). No protocol change for
clients on the same build; `ApplyEnchantmentPayload` needed nothing (`writeUtf`/`readUtf`/`writeInt`/
`readInt`/`writeIdentifier`/`readIdentifier` all still exist).

---

## 4. Verified **unchanged** — do not re-audit these for 26.3

Confirmed by reading the 26.3 sources of each class:

* **Mixin targets and injection points** (all 8 active mixins):
  `LevelChunk.postProcessGeneration(ServerLevel)`, `ChunkAccess.markPosForPostProcessing(BlockPos)`,
  `ChunkAccess.postProcessing` field, `ProtoChunk.unpackOffsetCoordinates(short, int, ChunkPos)`,
  `StructurePiece.getWorldPos(int,int,int)` + `StructurePiece.placeBlock(WorldGenLevel, BlockState, int,int,int, BoundingBox)`,
  `StructurePlaceSettings.getProcessors() : List<StructureProcessor>`,
  `StructureTemplatePool.Projection.getProcessors() : ImmutableList<StructureProcessor>`,
  `AnvilMenu.createResult()`, `EnchantmentMenu.slotsChanged(Container)`,
  `BlockBehaviour.isRandomlyTicking(BlockState)` + `randomTick(BlockState, ServerLevel, BlockPos, RandomSource)`
  (both still `protected`, which is what `AddRandomTick` overrides).
* **`StructureProcessor`** is still the same interface the 26.2 port already implements
  (`processBlock(LevelReader, BlockPos, BlockPos, BlockPos templateRelativePos, StructureBlockInfo, StructurePlaceSettings)`,
  `codec()`, `finalizeProcessing(...)`).
* **Block-entity renderer surface**: `BlockEntityRenderer<T, S>` (`createRenderState`,
  `extractRenderState(T, S, float, Vec3, @Nullable ModelFeatureRenderer.CrumblingOverlay)`,
  `submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`), `EnchantTableRenderState`
  (`time/yRot/flip/open`), `BlockEntityRenderState.lightCoords/breakProgress`,
  `Sheets.BLOCK_ENTITIES_MAPPER`, `BookModel.State.forAnimation(...)`, `ModelLayers.BOOK`,
  `ModelFeatureRenderer` still in `net.minecraft.client.renderer.feature`.
* **Container-screen GUI surface**: `AbstractContainerScreen.extractBackground/extractRenderState/extractLabels(GuiGraphicsExtractor, …)`,
  `GuiGraphicsExtractor.blit(Identifier, int, int, int, int, float, float, float, float)`,
  `.text(Font, String|Component, …)`, `.fill(int,int,int,int,int)`, `.pose() : Matrix3x2fStack`,
  `.nextStratum()`.
* **List widget surface**: `AbstractSelectionList.extractListBackground(GuiGraphicsExtractor)`,
  `Entry.extractContent(GuiGraphicsExtractor, int, int, boolean, float)`, `updateWidgetNarration`,
  `scrollBarX()`, `getRowWidth/getRowLeft/getRowRight`, `mouseScrolled`, `mouseDragged`.
* **Recipe JSON schema**: `ShapedRecipe.MAP_CODEC` still reads `pattern` / `key` / `result`
  (`ItemStackTemplate` = `id` + optional `count` + optional `components`). Our shaped recipe is
  untouched.
* **Advancement `display.background`**: now `ClientAsset.ResourceTexture.CODEC`, which is
  `Identifier.CODEC.xmap(...)` mapping `ns:path` → `textures/<path>.png`. Our
  `"minecraft:block/crying_obsidian"` resolves to `textures/block/crying_obsidian.png`, so it stays valid.
* **Payload codecs**: `CustomPacketPayload.codec(StreamMemberEncoder, StreamDecoder)`,
  `StreamCodec.composite`, `Identifier.STREAM_CODEC`, `ByteBufCodecs.VAR_INT`.

Not touched by this mod at all (checked, so nobody re-audits): `ItemInHandRenderer` →
`FirstPersonHandsAndItems` (no first-person code here), custom core shaders / `#moj_import` (none),
OIT post-effect files (none), `RenderSystem`/`TextureTarget`/`GpuTexture` (none).

---

## 5. Data-pack schema migration (26.3)

### 5.1 Block loot table — singular `condition` / `modifier` dispatched on `type`

26.3 codecs: `LootPool.CODEC` → `optionalFieldOf("condition")` + `optionalFieldOf("modifier")`;
`LootPoolEntryContainer.commonFields` → the same two keys; `LootItemFunctions.DIRECT_CODEC` dispatches
through `BuiltInRegistries.LOOT_FUNCTION_TYPE.byNameCodec()` ⇒ the dispatch key is `type`.
A list is still allowed (`SequenceFunction.INLINE_CODEC` / `AllOfCondition`), but the 26.2 plural
arrays `conditions` / `functions` and the `function` / bare-string `condition` forms are gone — and
they fail **silently** (unknown keys are ignored, so the drop just loses its condition/function).

`data/chiseled_enchanting_table/loot_table/blocks/chiseled_enchanting_table.json` was rewritten:

```json
"condition": { "type": "minecraft:survives_explosion" },
"entries": [ { "type": "minecraft:item", "name": "...",
               "modifier": { "type": "minecraft:copy_components", "source": "block_entity",
                             "include": ["minecraft:custom_name"] } } ]
```

(`CopyComponentsFunction.MAP_CODEC` still reads `source` / `include` / `exclude`, and `LootTable`
still accepts `random_sequence`.)

### 5.2 Advancement `minecraft:recipe_crafted` — `recipe_id` → `recipes[]`

26.3 `RecipeCraftedTrigger.TriggerInstance.CODEC` = `player?` + **`recipes` (required,
`Recipe.LIST_CODEC` = `RegistryCodecs.holderSet(Registries.RECIPE)`)** + `ingredients?`. There is no
`recipe_id` field any more, so the old form makes the advancement fail to load.

Migrated `advancement/craft.json` and `advancement/root.json` to
`"recipes": ["chiseled_enchanting_table:chiseled_enchanting_table"]`.

### 5.3 Advancement `show_toast` / `announce_to_chat` moved into `display`

26.3 `Advancement.CODEC` knows only `parent` / `display` / `rewards` / `criteria` / `requirements` /
`sends_telemetry_event`; the two flags are `DisplayInfo` fields. `craft.json` had them at the top
level (silently ignored, i.e. the intended "no toast, no chat" never applied). They were **moved
into `display`**, which preserves the original intent instead of dropping it.

### 5.4 CI now guards both schemas

`scripts/compat_check.py` gained two Tier-1 checks — `check_loot_tables_use_26_3_schema()` and
`check_advancements_use_26_3_schema()`. Negative-tested: pointed at the pre-port 26.2 files they
report **7 failures**; against the migrated files they pass.

---

## 6. CI: the TACZR triad, adapted (built before the port, on purpose)

Borrowed from `q14433686-arch/TaCZ_Refabricated_Unofficial/.github/workflows/` — including its
hard-won comments (log push-back via Contents API instead of `git push`; no `/` in artifact names;
`paths-ignore: build-reports/**` to stop the log commit re-triggering the workflow; only `arena/**`
gets log commits).

> **Where the files live:** they are staged in **`docs/ci/`**, not `.github/workflows/`. The push
> was rejected outright:
>
> ```
> ! [remote rejected] arena/2e402cd9-chiseled-enchanting-table-26-1 (refusing to allow a GitHub App
>   to create or update workflow `.github/workflows/build.yml` without `workflows` permission)
> ```
>
> Same blocker as the 26.2 round. `bash docs/ci/install.sh` copies all four into place;
> `bash docs/ci/install.sh --check` reports drift without writing. Details:
> [`../ci/README.md`](../ci/README.md).

| Staged file | Purpose |
|---|---|
| `docs/ci/compile-check.yml` | `compileJava compileClientJava` on Actions, then writes `build-reports/compile-java.log` back to `arena/**` via the Contents API. This is the only compile-verification channel available to a sandbox without maven access. Differs from TACZR by compiling **both** source sets — this repo splits `main` and `client`, so compiling only `main` would silently skip the whole GUI/renderer half. |
| `docs/ci/build.yml` | static gates (no network) → full `./gradlew build` → jar artifact, 14-day retention. |
| `docs/ci/consistency.yml` | version gate that also fires on docs-only changes (`build.yml` `paths-ignore`s `*.md`). |
| `docs/ci/publish.yml` | the release workflow with the version bump the 26.2 round could not land (`MINECRAFT_VERSION: 26.3`, `VERSION: 1.0.3`). |
| `scripts/ci_static_checks.py` | mixin configs resolve to real `.java` files; **a mixin registered in `fabric.mod.json` must not be excluded by `build.gradle`** (that combination ships a config pointing at a class that is not in the jar → startup crash); `en_us`/`zh_cn` key parity. |
| `scripts/check_release_consistency.sh` | `gradle.properties` ↔ `README.md` ↔ `fabric.mod.json` ↔ `publish.yml`. |
| `.githooks/pre-commit` + `scripts/install-hooks.sh` | warn-only local reminder; never blocks a commit. |

**Proof the version gate earns its place:** on the unmodified 26.2 tree it reported

```
FAIL: publish.yml MINECRAFT_VERSION is '26.1.2', gradle.properties says '26.2'
FAIL: publish.yml RELEASE_NAME does not mention '26.2'
```

— i.e. the drift that `docs/reports/PORTING_26_2_REPORT.md` §2 had to record as "pending manual
apply" is now caught automatically.

Because `.github/workflows/publish.yml` still cannot be written, the gate has an explicit, narrow
escape hatch: a stale `publish.yml` is a **warning** when `docs/ci/publish.yml` already carries the
correct values, and a hard **failure** otherwise. All three states were exercised:

| state | result |
|---|---|
| branch as pushed (stale `publish.yml`, correct staged copy) | `9 passed, 3 warned, 0 failed`, `--strict` exit **0** |
| staged copy installed into `.github/workflows/` | `12 passed, 0 warned, 0 failed`, exit **0** |
| staged copy broken (`VERSION: 9.9.9`) | `3 failed`, `--strict` exit **1** |

---

## 7. Not verified (honest list)

| Item | Why it is open | How to close it |
|---|---|---|
| CI triad is not active | pushing `.github/workflows/*` needs the `workflows` token permission | `bash docs/ci/install.sh` from a permitted identity |
| Compile of the whole tree | needs Gradle + maven access | the pre-existing build workflow runs `./gradlew build` on push; once the triad is installed, read `build-reports/compile-java.log` on this branch |
| **Mixin method-level injection points** | a green compile only proves the target *classes* exist; `@Inject(method=…)` descriptors are resolved at runtime | launch MC 26.3 and check the log for `@Inject` failures; the 5 structure mixins + anvil/enchanting-table/bookshelf mixins are the ones to watch |
| Live behaviour: menu open, enchant, transformation, book upgrades, XP deduction | runtime only | live test list in `NEXT_TASK.md` §A |
| Floating-book renderer visuals under 26.3 OIT | OIT replaced "Improved Transparency" in 26.3-snapshot-2; the book is a small translucent-ish overlay, no known impact, but nobody has looked | F3+X both states next to a chiseled table |
| Chest/archaeology loot injection actually rolling in-world | runtime only | open village/ancient-city chests with the mod installed |
| Modrinth/CurseForge publication | `publish.yml` is manual and needs secrets | maintainer runs `workflow_dispatch` |

---

## 8. Sources (reproducible)

```bash
# Fabric API blob comparison
gh api "repos/FabricMC/fabric/git/trees/0.155.2%2B26.2?recursive=1" --jq '.tree[]|select(.type=="blob")|"\(.path)\t\(.sha)"' > 26_2.tsv
gh api "repos/FabricMC/fabric/git/trees/0.162.0%2B26.3?recursive=1" --jq '.tree[]|select(.type=="blob")|"\(.path)\t\(.sha)"' > 26_3.tsv
# then compare the 10 rows for the classes this mod imports

# version pins
gh api "repos/FabricMC/fabric/contents/gradle.properties?ref=26.3"          # minecraft_version=26.3, prerelease=false
gh api "repos/FabricMC/fabric/contents/gradle/libs.versions.toml?ref=26.3"  # fabric-loom=1.17.20, fabric-loader=0.19.3
gh api "repos/FabricMC/fabric/releases?per_page=8"                          # 0.162.0+26.3 (2026-10-06)
gh api "repos/FabricMC/fabric-loader/releases?per_page=6"                   # 0.19.5 (2026-08-28)

# vanilla 26.3 signatures (decompiled sources)
gh api "repos/0khs/minecraft_source_26.3/contents/com/mojang/blaze3d/vertex/PoseStack.java"
gh api "repos/0khs/minecraft_source_26.3/contents/net/minecraft/client/renderer/blockentity/EnchantTableRenderer.java"
gh api "repos/0khs/minecraft_source_26.3/contents/net/minecraft/world/level/storage/loot/LootPool.java"
gh api "repos/0khs/minecraft_source_26.3/contents/net/minecraft/advancements/triggers/RecipeCraftedTrigger.java"

# sister-repo corroboration of the 26.3 render-layer story
gh api "repos/q14433686-arch/TaCZ_Refabricated_Unofficial/contents/docs/CHANGELOG_26_3_R1.md?ref=26.3"
gh api "repos/q14433686-arch/TaCZ_Refabricated_Unofficial/contents/docs/investigations/PORT_26_3_FEASIBILITY_2026_09_02.md"
```
