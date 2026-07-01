# Chiseled Enchanting Table 26.1.2 port — P3 client compile slice and test jar

Date: 2026-06-30

## Scope

P3 follows P2's server/core compile slice by establishing a full Gradle `build` pass on Minecraft/Fabric 26.1.2.

This is still a **porting scaffold**, not gameplay-complete parity with upstream. The pass intentionally favors a boot-safer compileable artifact over pretending the old Yarn-named UI, renderer, and mixin targets are already migrated.

## Version/output

Local port version used for this pass:

```text
0.2.3+26.1.2-p3
```

Refreshed local output directory:

```text
release_output/chiseled_enchanting_table_26_1_2_p3/
```

Files:

```text
chiseled-enchanting-table-0.2.3+26.1.2-p3.jar
chiseled-enchanting-table-source-26.1.2-p3.zip
```

## Main changes

### Client compile slice

The previous client source still used Yarn-named packages such as:

```text
net.minecraft.client.gui.screen.ingame.*
net.minecraft.client.render.*
net.minecraft.client.texture.*
net.minecraft.client.util.math.*
```

For P3, the client source set now temporarily excludes the unmigrated UI/renderer/helper areas:

```text
chiseled_enchanting_table/gui/**
chiseled_enchanting_table/render/**
chiseled_enchanting_table/utils/**
```

`ChiseledEnchantingTableClient` has been reduced to a no-op client initializer with TODOs to restore:

- custom handled screen registration;
- block entity renderer registration;
- block/item color providers;
- render layer setup.

This allows `compileClientJava` to pass without falsely half-migrating the old UI classes.

### Mixin runtime safety

P2 excluded the old mixin source files from the compile slice, but the upstream `fabric.mod.json` still referenced the mixin configs. That would create a runtime boot hazard because Fabric/Mixin would attempt to load classes that were intentionally excluded.

P3 therefore temporarily sets:

```json
"mixins": []
```

and records a custom port note in `fabric.mod.json`:

```text
26.1.2 P3 temporary: mixins disabled until P4 retargets upstream injections.
```

This is temporary. P4/P5 must restore mixins one by one after retargeting them to 26.1.2 names and method descriptors.

### Version marker

`gradle.properties` now uses:

```text
mod_version=0.2.3+26.1.2-p3
```

so generated test jars cannot be confused with P1/P2 scaffolds or upstream `0.2.3`.

## Current behavior status

The built jar is expected to be loadable at metadata/class level, but many gameplay systems remain intentionally inactive or stubbed.

Currently inactive/stubbed from P2/P3:

- custom client screen registration;
- custom block-entity renderer;
- color provider/render-layer setup;
- full enchantment application in the screen handler;
- nearby chiseled-bookshelf enchantment scanning;
- `EnchantmentFinder` book scanning;
- floating book animation tick;
- structure processor registration;
- loot table hooks;
- all upstream mixins;
- enchanted-book max-stack mutation;
- creative tab placement.

The custom block/item/menu classes compile, but opening/using the table in a live client is not yet a completed gameplay target.

## Validation performed

### Main + client compile

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace
```

Result: **PASS**.

### Full Gradle build

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

Result: **PASS**.

### Jar metadata spot check

Checked the staged jar's `fabric.mod.json` and confirmed:

- version is `0.2.3+26.1.2-p3`;
- Minecraft dependency is `26.1.2`;
- Java dependency is `>=25`;
- Fabric Loader dependency is `>=0.19.3`;
- license is `MIT`;
- mixin list is temporarily empty.

## Next step: P4 recommendation

P4 should restore one real feature slice instead of broadly uncommenting everything.

Recommended P4 order:

1. Restore client screen registration with a minimal migrated handled screen so opening the custom table does not fail client-side.
2. Restore a minimal table GUI that can display the current default enchantment payload, even if applying enchantments remains stubbed.
3. Then restore block/item color provider and render layer.
4. Leave block-entity floating book renderer for a later renderer-specific pass if needed.

Alternative P4 track:

- restore server-side bookshelf scanning and enchantment payload semantics first, while keeping client UI minimal.

## Client-only / later validation

Still not covered by automated checks:

- live Minecraft boot with Fabric Loader 0.19.3 and Fabric API 0.151.0+26.1.2;
- placing the custom block;
- custom table UI opening;
- table UI rendering and click behavior;
- applying enchantments;
- nearby chiseled-bookshelf unlock scanning;
- loot generation;
- structure bookshelf replacement;
- anvil/enchanting-table behavior changes;
- multiplayer packet behavior beyond compile-level registration.
