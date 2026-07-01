# Chiseled Enchanting Table 26.1.2 port — P5/P6 report

**Date:** 2026-07-01

## Scope

This pass executes the requested P5+P6 track on top of the P4 compile/build baseline.

Because the sandbox cannot boot a full Minecraft client, P5's live-client smoke test remains a documented external validation boundary. The implementation work therefore focused on a P6-safe mechanics/data pass that improves runtime readiness without re-enabling fragile mixins or renderer code.

## P5: runtime smoke boundary

### Completed in sandbox

- Confirmed Java 25 environment and Gradle/Fabric toolchain remain usable.
- Re-ran compile/build validators after changes.
- Checked jar metadata and packaged data resources.

### Requires real client

The following P5 checks still require Minecraft 26.1.2 + Fabric Loader 0.19.3 + Fabric API 0.151.0+26.1.2 in a client runtime:

- game boot with the P6 jar;
- mod metadata load with `mixins: []`;
- custom block registration in a live world;
- block placement;
- opening the Chiseled Enchanting Table UI;
- menu payload sync into `ChiseledEnchantingTableScreen`;
- slot/list rendering and interaction;
- enchantment packet send/receive behavior;
- item cost and XP consumption;
- bookshelf scanning behavior in-world;
- multiplayer sync behavior.

## P6: data-driven mechanics slice

### Added JSON-backed data tables

Added server-data resources:

- `data/chiseled_enchanting_table/chiseled_enchanting_table/default_enchantments.json`
- `data/chiseled_enchanting_table/chiseled_enchanting_table/enchantment_costs.json`

These move default unlock content and item/XP cost values into JSON so the values can be overridden by datapacks/resource reloads instead of being expanded as Java hardcoding.

### Added reload loader

Added `chiseled_enchanting_table.data.ChiseledEnchantingTableData`:

- registers a Fabric server-data reload listener;
- loads default enchantment unlocks from JSON;
- loads item and XP cost tables from JSON;
- validates/falls back to built-in defaults if resources are missing or invalid;
- exposes defensive-copy/default APIs for the block/menu code.

### Rewired existing Java call sites

- `ChiseledEnchantingTable.onInitialize()` now initializes the data reload listener.
- `ChiseledEnchantingTableBlock.enchantments_unlocked_by_default()` now reads from the data loader.
- `ChiseledEnchantingTableScreenHandler.getEnchantmentItemCost(...)` now reads from the data loader.
- `ChiseledEnchantingTableScreenHandler.getEnchantmentXpLevelCost(...)` now reads from the data loader.
- `ChiseledEnchantingTableScreenHandler.get_cost_item()` now returns the actual cost slot stack instead of the previous P4 `ItemStack.EMPTY` stub.

## Version/artifacts

- Updated `gradle.properties` to `mod_version=0.2.3+26.1.2-p6`.
- Refreshed artifacts under `release_output/chiseled_enchanting_table_26_1_2_p6/`:
  - `chiseled-enchanting-table-0.2.3+26.1.2-p6.jar`
  - `chiseled-enchanting-table-0.2.3+26.1.2-p6-sources.jar`
  - `chiseled-enchanting-table-26.1.2-p6-source.zip`

## Validation performed

### JSON validation

```bash
python3 -m json.tool src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/default_enchantments.json
python3 -m json.tool src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/enchantment_costs.json
```

Result: **PASS**.

### Dependency/classpath check

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 dependencies --configuration compileClasspath
```

Result: **PASS**.

### Resource processing

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 processResources --stacktrace
```

Result: **PASS**.

### Main + client compile

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace
```

Result: **PASS**.

### Full build

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

Result: **PASS**.

### Jar metadata/resource spot check

Confirmed:

- jar version is `0.2.3+26.1.2-p6`;
- Minecraft dependency is `26.1.2`;
- Java dependency is `>=25`;
- Fabric Loader dependency is `>=0.19.3`;
- `mixins` remains `[]`;
- both new JSON data tables are packaged in the jar.

## Current deferrals

Still not restored in this pass:

- live-client validation;
- mixin retarget/re-enable;
- block-entity renderer and floating book renderer;
- loot-table modification hooks;
- structure processor implementation/registration;
- Chiseled Bookshelf random tick helper;
- full gameplay parity with upstream.

## Recommended next step: P7

P7 should be a live-client feedback pass:

1. Boot the P6 jar in a real Fabric 26.1.2 client.
2. Fix the first runtime blocker around block registration, menu opening, or screen rendering.
3. Keep fixes narrow and re-run the same validator set after each source/resource change.
