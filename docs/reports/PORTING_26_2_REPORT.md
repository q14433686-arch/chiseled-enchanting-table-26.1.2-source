# Porting Report — 26.1.2 → 26.2 (Fabric branch)

> **Date:** 2026-07-19 · **Branch:** `arena/019f7982-chiseled-enchanting-table-26-1` (26.2 variant)
> **Scope:** Turn the existing 26.1.2 unofficial port into a Minecraft **26.2** Fabric build.
> **Verdict:** _Not_ a pure version-declaration bump, but the change surface is small and
> concentrated. The 26.2 **structure-processor rework** (`StructureProcessor` → interface,
> `StructureProcessorType` de-generified, codec registered into
> `BuiltInRegistries.STRUCTURE_PROCESSOR`) has been **ported** (see §3.3). The client renderer/GUI
> use high-level APIs confirmed present in 26.2 and were left as-is; they still need a
> `compileClientJava` pass to confirm exact signatures (not runnable in this headless sandbox: no
> JDK, no route to the Mojang/Fabric maven).

---

## 1. Confirmed version mapping for 26.2

| Component | 26.1.2 (was) | 26.2 (now) | Changed? |
|---|---|---|---|
| Minecraft | `26.1.2` | `26.2` | ✅ |
| Fabric Loader | `0.19.3` | `0.19.3` (still the recommended stable loader for 26.2) | ❌ |
| Fabric API | `0.151.0+26.1.2` | `0.155.2+26.2` (latest 26.2 release) | ✅ |
| Loom | `1.16-SNAPSHOT` | `1.17-SNAPSHOT` | ✅ |
| Gradle wrapper | `9.4.1` | `9.5.1` | ✅ |
| Java | `25` | `25` | ❌ |
| mod_version | `1.0.2` | `1.0.3` | ✅ |

Sources:
- Fabric "Fabric for Minecraft 26.2" article — Loom 1.17, Gradle 9.5.1, loader still 0.19.3:
  [fabricmc.net/2026/06/15/262](https://fabricmc.net/2026/06/15/262.html)
- Loader 0.19.3 is the stable loader listed for 26.2 by the Fabric meta service
  (`meta.fabricmc.net/v2/versions/loader/26.2`).
- Fabric API `0.155.2+26.2` is the current 26.2 release:
  [CurseForge – Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) (earlier
  26.2 builds: `0.153.0+26.2`, `0.155.0+26.2`).
- Note on the request wording "fabric 0.193": interpreted as **Fabric Loader 0.19.3**, which is
  unchanged. The component that actually moves is **Fabric API** (`0.151.0+26.1.2` → `0.155.2+26.2`).

---

## 2. Files changed in this branch (definite version declarations)

- `gradle.properties` — `minecraft_version=26.2`, `fabric_version=0.155.2+26.2` (loader stays `0.19.3`).
- `build.gradle` — Loom `1.16-SNAPSHOT` → `1.17-SNAPSHOT`.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle `9.4.1` → `9.5.1`.
- `src/main/resources/fabric.mod.json` — `depends.minecraft` `26.1.2` → `26.2`; human-readable
  name/description/author/port-notes strings updated to "26.2". The `contact.sources` GitHub URL
  (`...-26.1.2-source`) is intentionally left as-is — it is the real repository name.
- `.github/workflows/publish.yml` — **NOT changed in this commit (pending manual apply).** The
  GitHub App token used to push this branch lacks the `workflows` permission, so any edit to a
  workflow file is rejected by GitHub. The intended 2-line change is:
  `MINECRAFT_VERSION: 26.1.2` → `26.2` and
  `RELEASE_NAME: ... (Unofficial 26.1.2 Port)` → `(Unofficial 26.2 Port)`.
  Apply it manually (or grant the App `workflows: write` and re-apply).

The only remaining literal `26.1.2` strings in build/metadata files are:
- historical comments in `build.gradle` describing the original P1 skeleton porting history (kept), and
- the GitHub repo name in `fabric.mod.json` `contact.sources` (must stay).

---

## 3. 26.2 breaking changes vs this mod (impact assessment)

Based on the Fabric 26.2 article and the comprehensive NeoForged
[26.2 migration primer](https://github.com/ChampionAsh5357/neoforged-github/blob/port/26.2/primers/26.2/index.md):

### 3.1 Confirmed NOT hit (no source change needed)

| 26.2 change | Why this mod is unaffected |
|---|---|
| GUI `setScreen`/`screen` moved off `Minecraft` into `Gui` | Was not hit by the original port (screens open via the menu path: `MenuScreens.register` + Fabric `ExtendedMenuProvider`). The 2026-10-23 sync **does** hit it: the grimoire overlay opens/closes screens and the JEI plugin inspects the current screen, so all three call sites use `Minecraft.getInstance().gui.setScreen(...)` / `.gui.screen` instead of the removed `Minecraft` members. |
| Registration: `BlockIds`/`ItemIds` split, `valueLookupBuilder` removed | The mod does no data-generation; it registers directly with `Registry.register(BuiltInRegistries.BLOCK/ITEM/MENU/BLOCK_ENTITY_TYPE/LOOT_FUNCTION_TYPE, ...)`. No `valueLookupBuilder`/`BlockIds` usage anywhere. |
| Raw OpenGL → Vulkan migration (must go through Blaze3D) | Renderer/GUI already use `com.mojang.blaze3d.*` (`PoseStack`, `RenderPipeline`). No `GL11`/`GlStateManager`/`org.lwjgl.opengl` raw calls. |
| `ChatFormatting` gutted in favor of `Style` | No `ChatFormatting` usage in the client source. |
| `Font.draw*` removed (must prepare text manually) | Text is drawn through the high-level `GuiGraphicsExtractor.text(...)` wrapper; the row renderers only read `font.lineHeight`/`font.width(...)` (property reads, not draw calls). |
| Loader 0.19.3 / new enum-extension API | Loader version is unchanged; the mod does not use enum extensions. |

### 3.2 Confirmed-present high-level APIs the mod relies on (per the primer)

These are the exact high-level surfaces the renderer/screen use; the primer references them as the
_current_ 26.2 API, which is why no rewrite was attempted:
- `SubmitNodeCollector.submitModel(...)` / `OrderedSubmitNodeCollector.submit*` — still the feature
  submission entry points in 26.2.
- `GuiGraphicsExtractor` (incl. `.text`, `.blit`) — still present (only sub-signatures like
  `.entity(...)`/`.skin(...)`/`$ScissorStack#push/pop` return types changed, none of which the mod uses).
- `CameraRenderState`, `EnchantTableRenderState`, `BookModel`, `SpriteId`/`SpriteGetter`,
  `BlockEntityRenderer#createRenderState/extractRenderState/submit` — vanilla's own enchanting-table
  render path that this renderer mirrors.

### 3.3 At-risk files — must be confirmed by a live compile (sandbox could not verify)

26.2 is "focused primarily on rendering and registration", so the residual risk is concentrated in
the client render-state/GUI surface and the vanilla-signature mixins:

1. **`src/client/.../render/ChiseledEnchantingTableBlockEntityRenderer.java`** (highest risk)
   - Uses `BlockEntityRenderer<T, EnchantTableRenderState>` with `createRenderState()`,
     `extractRenderState(...)`, `submit(renderState, poseStack, submitNodeCollector, cameraRenderState)`,
     `submitNodeCollector.submitModel(...)`, `ModelFeatureRenderer.CrumblingOverlay`,
     `context.bakeLayer(ModelLayers.BOOK)`, `Sheets.BLOCK_ENTITIES_MAPPER.defaultNamespaceApply(...)`.
   - _Check at compile:_ exact parameter list of `submit(...)` and `submitModel(...)`; whether
     `EnchantTableRenderState` field names (`flip`/`open`/`time`/`yRot`/`lightCoords`/`breakProgress`)
     and `ModelFeatureRenderer.CrumblingOverlay` are unchanged.

2. **`src/client/.../gui/ChiseledEnchantingTableScreen.java`** (+ `EnchantementListWidget`, `row/*`, `CustomDrawContext`)
   - Overrides `AbstractContainerScreen#extractBackground/extractRenderState/extractLabels(GuiGraphicsExtractor, mouseX, mouseY, partialTick)`,
     uses `ctx.blit(...)` and `context.text(...)`.
   - _Check at compile:_ that the `AbstractContainerScreen` extract-method signatures (the
     container-screen variant, **not** the `Gui`-class variant that changed) and the
     `GuiGraphicsExtractor.text/blit` overloads are unchanged.

3. **Structure processor subsystem** — **PORTED for 26.2 (fixed).** 26.2 turned `StructureProcessor`
   into an interface, removed `getType()`/`StructureProcessorType.register(String, MapCodec)`,
   de-generified `StructureProcessorType`, added a `BlockPos` (relative template position) parameter
   to `processBlock`, and moved registration to `BuiltInRegistries.STRUCTURE_PROCESSOR`
   (`Registry<MapCodec<? extends StructureProcessor>>`). Applied changes:
   - `mechanics/structure/BookshelfReplacerProcessor.java`: `extends`→`implements StructureProcessor`;
     `getType()`→`codec()` returning `CODEC`; `processBlock` gained the `BlockPos templatePos`
     parameter (unused; body unchanged); `finalizeProcessing` signature was already correct.
   - `registry/StructureProcessorRegistry.java`: registers `BookshelfReplacerProcessor.CODEC` via
     `Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, id, codec)`.
   - The structure mixins (`StructurePlaceSettingsMixin`, `StructureTemplatePoolProjectionMixin`,
     `StructurePieceMarkMixin`, `LevelChunkPostProcessMixin`) needed **no change** — their earlier
     errors were cascades of the `extends`-on-an-interface failure; `ChunkAccess.markPosForPostprocessing`
     still exists in 26.2.

### 3.4 Compile & CI verification — **RESOLVED (green on GitHub Actions)**

Both `:compileJava` and `:compileClientJava` (plus `remapJar` and artifact packaging) are confirmed
green on GitHub Actions against Minecraft 26.2:
- **Baseline 26.2 port (`9ef1889`):** check-run `88884451664` (`build` workflow) completed `success`.
- **26.3 feature & fix backport (`8b00060` / `796664a`, 2026-10-21):** Actions run `39663201859`
  and check-run `119447310534` (`build` workflow) completed all steps `success` (`compat_check.py`,
  `:compileJava`, `:compileClientJava`, `remapJar`, artifact `Artifacts` = 369,768 bytes).
- **26.1.2 sister-branch fixes & CI triad (`61b2431`, 2026-10-22):** `compile-check`
  (`119488382287`), `build` (`119488381220` static + `119488433821` gradle build + `119488433668`
  no-viewers), and `consistency` (`119488380920`) all completed `success`, resolving and pinning
  `mezz.jei:jei-26.2-fabric:30.47.0.239`, `me.shedaniel:RoughlyEnoughItems-api-fabric:26.2.821`,
  `me.shedaniel.cloth:basic-math:0.6.1`, and `dev.architectury:architectury-fabric:21.0.4` with
  `toolchainPins` exclusions (`net.fabricmc.fabric-api`, `net.fabricmc`, `com.mojang`).

---

## 4. How to verify locally (requires JDK 25 + network to Mojang/Fabric maven)

```bash
chmod +x ./gradlew
./gradlew --refresh-dependencies clean build --stacktrace
# targeted, faster iteration on the at-risk client surface:
./gradlew compileJava compileClientJava --stacktrace
# offline data sanity gate (no network needed):
python3 scripts/compat_check.py
```

- If `compileClientJava` fails, the errors will point at the exact render-state/GUI signature that
  moved; those are the targeted fixes for §3.3 items 1–2.
- If the game fails to load a mixin at runtime, the mixin name in the crash identifies the §3.3
  item-3 signature that changed.

## 5. Why no speculative source rewrite was done

The investigation found **zero** direct uses of any API the primer marks as removed or rewritten
(`Font.draw*`, `ChatFormatting`, `Minecraft.setScreen`/`.screen`, raw OpenGL, `valueLookupBuilder`,
`MultiBufferSource`). The two that have since been hit by new content — `ChatFormatting` and
`Minecraft#setScreen`/`#screen` — were rewritten for 26.2 as described in §3.1 and in
[`SYNC_FROM_26_1_2_SIDE_REPORT.md`](SYNC_FROM_26_1_2_SIDE_REPORT.md) §6.2. Every API the mod calls is either a stable high-level wrapper confirmed present
in 26.2, or a vanilla-internal signature that only a real compile can confirm. Rewriting the renderer
or screen to hypothetical 26.2 signatures without a compiler to check against would risk breaking
working code on a guess. The correct, minimal-risk path is: apply the certain declaration changes,
keep the source as-is, and let one live `./gradlew build` confirm (or pinpoint) the residual 2–3 files.
