# Chiseled Enchanting Table 26.1.2 port — P1 build-system migration

Date: 2026-06-30

## Scope

P1 starts a local compatibility port of Sacramentix's `chiseled-enchanting-table` to the current Fabric/Minecraft 26.1.2 toolchain.

This pass intentionally only touches project setup and metadata. It does **not** attempt to migrate gameplay Java code, mixin targets, GUI code, registry APIs, loot code, or structure-processing behavior yet.

## Source/license notes

- Upstream repository: `https://github.com/Sacramentix/chiseled-enchanting-table`
- Repository `LICENSE`: MIT
- README license statement: MIT
- P1 metadata now uses `MIT` in `fabric.mod.json` to match the repository license.

## Toolchain migration

Updated local port metadata/build setup to:

- Minecraft: `26.1.2`
- Fabric Loader: `0.19.3`
- Fabric API: `0.151.0+26.1.2`
- Loom: `1.16-SNAPSHOT` resolving as `1.16.3`
- Gradle Wrapper: `9.4.1`
- Java compile target: `25`

Important 26.1.2 note: this line uses Mojang official package names, not Yarn. The upstream code is still written against the older Yarn-named 1.21.5 API (`net.minecraft.item.*`, `net.minecraft.block.*`, etc.), so Java source migration is expected to be P2/P3 work.

## Build-system changes

- Replaced old Loom `1.10-SNAPSHOT` setup with `net.fabricmc.fabric-loom` `1.16-SNAPSHOT`.
- Updated Gradle wrapper to `9.4.1`.
- Removed the Kotlin JVM plugin and Fabric Language Kotlin dependency from the local port setup because the current source is Java-only.
- Removed Kotlin entrypoint adapters from `fabric.mod.json`.
- Disabled the Modrinth publishing plugin/block for the local port smoke path; release publishing can be restored later once the port builds and a fork/release policy is chosen.
- Updated `fabric.mod.json` dependencies to 26.1.2 / Java 25 / Loader 0.19.3.
- Updated mixin `compatibilityLevel` from `JAVA_21` to `JAVA_25`.
- Made `fabric.mod.json` version use the Gradle-expanded `${version}` placeholder.

## Validation performed

### Dependency/classpath check

Command:

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 dependencies --configuration compileClasspath
```

Result: **PASS**. Gradle/Loom resolves the 26.1.2 toolchain and Fabric API dependencies.

### Compile smoke

Command:

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace
```

Result: **expected Java source failure** after Gradle/Loom setup completes and `:compileJava` starts.

The first failures are source/API migration failures such as:

```text
package net.minecraft.component does not exist
package net.minecraft.item does not exist
package net.minecraft.block does not exist
cannot find symbol: net.minecraft.util.Identifier
```

This is the desired P1 boundary: build infrastructure now reaches the Java compiler under 26.1.2, and the remaining failures are real source migration tasks caused by the package/API shift from upstream 1.21.5 Yarn names to 26.1.2 Mojang official names.

## Next step: P2

P2 should start source migration for the non-mixin core surface first:

1. Convert imports/types from Yarn-style packages to 26.1.2 Mojang official package names.
2. Prioritize registration and metadata classes:
   - `ChiseledEnchantingTable`
   - `BlockRegistry`
   - `EntityRegistry`
   - `ScreenHandlerRegistry`
   - `StructureProcessorRegistry`
3. Then migrate the custom block/block-entity/screen-handler enough to compile without validating mixin targets yet.
4. Defer fragile mixin retargeting to P3.

## Client-only / later validation

Not covered by P1:

- Game boot.
- Block/item registration in a live client.
- GUI open/sync behavior.
- Chiseled bookshelf enchantment discovery behavior.
- Loot-table modifications.
- Structure bookshelf replacement.
- Anvil/enchanting-table behavior mixins.
