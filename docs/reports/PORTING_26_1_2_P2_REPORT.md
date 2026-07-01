# Chiseled Enchanting Table 26.1.2 port — P2 core server compile slice

Date: 2026-06-30

## Scope

P2 continues the local 26.1.2 compatibility port after P1's build-system migration.

The goal for this pass was not full gameplay parity. The goal was to move the non-client, non-mixin core surface far enough onto the 26.1.2 Mojang-official-name toolchain that the main Java source set can compile as a controlled server/core slice.

## Important boundary

Minecraft 26.1.2 uses Mojang official package/class names. The upstream 1.21.5 source is Yarn-named. P2 therefore performs a first source migration pass and deliberately defers fragile behavior restoration.

Deferred to later passes:

- client GUI and renderer migration;
- mixin target retouching;
- loot-table rework hooks;
- structure bookshelf replacement hooks;
- full enchantment application logic;
- nearby chiseled-bookshelf enchantment scanning;
- enchanted-book max-stack component mutation;
- creative-tab placement;
- live boot/gameplay validation.

## Source-set boundary used in P2

To keep P2 focused on the core compile slice, the following source areas are excluded from the main `compileJava` smoke until their 26.1.2 APIs are migrated in P3+:

```text
chiseled_enchanting_table/mixin/**
chiseled_enchanting_table/modifyChestLootTable/**
chiseled_enchanting_table/structureProcessor/**
chiseled_enchanting_table/integration/**
chiseled_enchanting_table/chiseledEnchantingTable/ChiseledBookshelfTick.java
chiseled_enchanting_table/utils/BlockPosStream.java
chiseled_enchanting_table/utils/ChiseledBookshelfLootTable.java
chiseled_enchanting_table/utils/InventoryUtils.java
```

This is a temporary porting boundary, not a final feature decision.

## Main changes

### Mojang-name package migration, first pass

Applied a broad first-pass package migration from Yarn-style names such as:

```text
net.minecraft.item.*
net.minecraft.block.*
net.minecraft.entity.player.*
net.minecraft.screen.*
net.minecraft.text.*
net.minecraft.util.Identifier
```

toward current 26.1.2 names such as:

```text
net.minecraft.world.item.*
net.minecraft.world.level.block.*
net.minecraft.world.entity.player.*
net.minecraft.world.inventory.*
net.minecraft.network.chat.*
net.minecraft.resources.Identifier
```

### Registration/core classes migrated enough for compile

Updated the current core compile slice around:

- `ChiseledEnchantingTable`
- `BlockRegistry`
- `EntityRegistry`
- `ScreenHandlerRegistry`
- `StructureProcessorRegistry` as a temporary no-op shell
- `ChiseledEnchantingTableBlock`
- `ChiseledEnchantingTableBlockEntity`
- `ChiseledEnchantingTableScreenHandler`
- `FloatingBook` as a temporary animation shell
- `Advancement` as a minimal advancement helper
- `EnchantmentWithLevel` as a 26.1.2 `StreamCodec` payload record
- `EnchantmentFinder` as a temporary no-op shell

### API pivots made in P2

- `BlockWithEntity` → `BaseEntityBlock`.
- `ScreenHandler` / `ScreenHandlerType` → `AbstractContainerMenu` / `MenuType` ecosystem.
- Fabric `ExtendedScreenHandlerType` → `net.fabricmc.fabric.api.menu.v1.ExtendedMenuType`.
- Fabric `ExtendedScreenHandlerFactory` → `ExtendedMenuProvider`.
- Networking payload API updated to `CustomPacketPayload.Type` / `type()`.
- Fabric payload registration updated to `PayloadTypeRegistry.serverboundPlay()`.
- `ComponentMap` / `DataComponentTypes` replaced by `DataComponentMap` / `DataComponents` in the migrated slice.
- Block entity persistence moved to the 26.1.2 `ValueInput` / `ValueOutput` methods.

## Temporary behavior stubs introduced

To get a clean P2 server/core compile slice, these behaviors are intentionally stubbed and must be restored later:

- `ChiseledEnchantingTableScreenHandler.applyEnchantement(...)` currently does nothing.
- `ChiseledEnchantingTableBlock.get_unlocked_enchantements(...)` currently returns an empty set.
- `EnchantmentFinder.streamAllEnchantements(...)` currently returns an empty stream.
- `FloatingBook.tick(...)` currently does nothing.
- `StructureProcessorRegistry.init()` currently does not register the bookshelf replacer processor.
- Loot-table hooks are not called from the initializer.
- The old enchanted-book stack-size component mutation is not applied.
- Creative-tab placement is not restored yet.

These stubs are intentionally documented so P3/P4 can restore behavior without losing track of what was parked.

## Validation performed

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

### Main Java compile slice

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileJava --stacktrace
```

Result: **PASS**.

### Client compile smoke

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileClientJava --stacktrace
```

Result: **expected failure**.

The client source still uses Yarn-style client packages, for example:

```text
net.minecraft.client.gui.screen.ingame.*
net.minecraft.client.render.*
net.minecraft.client.texture.*
net.minecraft.client.util.math.*
```

Client migration is intentionally left for a later pass.

## Next step: P3 recommendation

P3 should choose one of two tracks:

1. **Client-first track** — migrate the client entrypoint, screen, widget, draw-context helper, and block-entity renderer so a full `compileClientJava` can pass.
2. **Mechanics-first track** — keep client excluded temporarily and restore server mechanics one by one: bookshelf scanning, full screen-handler enchantment application, loot-table hooks, and structure processor registration.

Given this mod is UI-heavy, the recommended next pass is client-first so the project can return to full `build` sooner.

## Client-only / later validation

Still not covered:

- game boot;
- opening the custom table UI;
- visual book renderer behavior;
- actual enchantment application;
- bookshelf unlock scanning;
- loot distribution changes;
- structure bookshelf replacement;
- anvil/enchanting-table mixin behavior;
- multiplayer packet behavior beyond compile-level payload registration.
