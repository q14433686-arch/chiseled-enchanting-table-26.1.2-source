# Worldgen bookshelf fill: hard deadlock fix (Minecraft 26.2 / mod 1.0.3 line)

> **Scope.** Fix for the integrated-server freeze reported against this unofficial 26.2 port:
> `BookshelfReplacerProcessor#finalizeProcessing` touching the live `ServerLevel` from a worldgen
> worker thread. Affects `mechanics/structure/BookshelfReplacerProcessor`,
> `mechanics/structure/BookshelfBookFiller` and `mixin/structure/LevelChunkPostProcessMixin`.
>
> **Status:** patch generated against the `chiseled-enchanting-table-26.2-source` branch
> (see the branch notes below and §7). **Compile and runtime verification are both still
> open on this branch** — the `compile-check` workflow does not build it, and there is no
> client in the authoring sandbox.

> **Branch notes.** Generated on 2026-10-22 from the 26.3 write-up (source commits
> `55a62fc` + the `ChunkStatus` package fix on the 26.3 line); the code in this patch is
> adapted to the `26.2` API.
>
> **This branch:** identical API surface to 26.3 for everything this patch touches (`StructureProcessor` interface with `codec()`, `ChunkStatus` in `net.minecraft.world.level.chunk.status`, `ChunkAccess#markPosForPostProcessing`). The three files are the 26.3 fix verbatim.

## 1. Symptom

With Distant Horizons (`chunkGeneratorMode = FEATURES`, `generatorPlan = SURFACE_THEN_CHUNKS`) the
integrated server freezes completely while distant terrain is generated: no TPS, no log output, no
crash, world cannot be saved. A thread dump (stable across 3 dumps, 5 s apart) shows every
`DH-World Gen Thread` parked in `CompletableFuture.join()` below
`BookshelfBookFiller.applyToPlacedBookshelf`, and the server thread parked in a nested
`ServerChunkCache.getChunkBlocking` / `BlockableEventLoop.managedBlock` chain.

The same chain also appears on a vanilla/C2ME `Worker-Main` thread, so the unsafe cross-thread
access existed without DH — DH only turns an occasional stall into a deterministic deadlock because
it generates chunks on its own pool instead of the vanilla chunk scheduler.

## 2. Root cause

### 2.1 What the vanilla 26.2 API actually hands a processor

Verified against decompiled Minecraft 26.2 sources (`net.minecraft.world.level.levelgen.structure.
templatesystem.StructureTemplate`):

```java
public static List<StructureBlockInfo> processBlockInfos(
        ServerLevelAccessor level, BlockPos position, BlockPos referencePos,
        StructurePlaceSettings settings, List<StructureBlockInfo> blockInfoList) {
    ...
    for (StructureBlockInfo blockInfo : blockInfoList) {
        BlockPos blockPos = StructureTemplate.calculateRelativePosition(settings, blockInfo.pos)
                                               .offset(position);          // ← ABSOLUTE world pos
        if (processOnlyInCurrentChunk && chunkBb != null && !chunkBb.isInside(blockPos)) continue;
        StructureBlockInfo processedBlockInfo = new StructureBlockInfo(blockPos, blockInfo.state, ...);
        while (processedBlockInfo != null && iterator.hasNext()) {
            processedBlockInfo = iterator.next().processBlock(level, position, referencePos,
                                                              blockInfo.pos, processedBlockInfo, settings);
        }
        ...
    }
    for (StructureProcessor processor : settings.getProcessors()) {
        processedBlockInfoList = processor.finalizeProcessing(
                level, position, referencePos, originalBlockInfoList, processedBlockInfoList, settings);
    }
    ...
}
```

Three consequences matter here:

1. `finalizeProcessing` runs **on the worldgen worker thread**, inside a `WorldGenRegion`
   (`ChunkGenerator#applyBiomeDecoration` → `StructureStart#placeInChunk`).
   `StructureProcessor#evaluatesEntirePieceState()` defaults to `false`, so the list handed to us
   is clipped to the **chunk currently being generated**.
2. The positions in `processedBlockInfoList` are **absolute world positions** — and, because of the
   clipping above, they are positions inside the chunk that is *right now* being generated.
3. Block states are rotated/mirrored by `placeInWorld` **after** processing
   (`blockInfo.state.mirror(settings.getMirror()).rotate(settings.getRotation())`), and the
   optional `blockInfo.nbt` is handed to `BlockEntity#loadWithComponents` immediately after
   `setBlock`. So a processor may legitimately attach a finished block-entity tag to the info and
   let vanilla place it.

### 2.2 What the mod did

```java
ServerLevel level = world.getLevel();                 // WorldGenRegion → real level
...
BookshelfBookFiller.applyToPlacedBookshelf(level, info.pos(), random);
    BlockState existing = level.getBlockState(pos);   // ServerChunkCache.getChunk(...)
    ... level.setBlock(pos, ...); level.getBlockEntity(pos);
```

For a chunk that is mid-generation, `ServerChunkCache#getChunk` cannot be a cache hit. In 26.2 it
is explicitly off-thread-aware:

```java
public @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate) {
    if (Thread.currentThread() != this.mainThread) {
        return CompletableFuture.supplyAsync(() -> this.getChunk(x, z, targetStatus, loadOrGenerate),
                                             this.mainThreadProcessor).join();   // ← blocks forever
    }
    ...
    this.mainThreadProcessor.managedBlock(serverFuture::isDone);               // ← server side
```

The worker blocks in `join()`; the server thread that is supposed to complete that future is itself
blocked in `managedBlock` waiting for the chunk the worker is "generating". Classic
producer/consumer cycle across the two thread pools — hence the freeze with no crash and no log.

Reading neighbours through the same real level was equally wrong (`getBlockState` on the four
neighbours of a shelf on a chunk border). And **the worldgen-side alternative would not have been
safe either**: `WorldGenRegion#getBlockState` throws a `ReportedException`
("Requested chunk unavailable during world generation") for anything outside the generating step's
cache — a hard crash, not a fallback. `WorldGenRegion` also logs
`warnIfReadOutsideWriteZone` for out-of-write-zone reads.

### 2.3 Second-order bug found while fixing it

Because the processor ran the *live-world* fill, the template path's outcome depended on
`level.getBlockState(info.pos())` returning a bookshelf at a position that was, by construction,
not yet placed. In practice villages/mansions/ancient cities got an **empty** chiseled bookshelf
(the `processBlock` rewrite did land), while the fill either no-op'd, blocked, or — once the chunk
happened to be generatable — ran against a chunk that vanilla then overwrote. The deferred
"stronghold library" path (`StructurePieceMarkMixin` → `LevelChunkPostProcessMixin`) is the only
one that was filling shelves, and only for programmatic pieces.

## 3. Fix

Three changes, in order of importance.

### 3.1 The fill is now pure, and is written into the `StructureBlockInfo` list

`BookshelfReplacerProcessor#finalizeProcessing` no longer touches a level at all:

```java
RandomSource random = RandomSource.create(seedFor(worldSeed, info.pos()));
BookshelfBookFiller.Fill fill = BookshelfBookFiller.computeFill(info.state(), random, enchantments);
result.add(new StructureBlockInfo(info.pos(), fill.state(),
        BookshelfBookFiller.toBlockEntityTag(fill.items(), level.registryAccess())));
```

* `computeFill(...)` is a pure roll: datapack pool + `HolderGetter<Enchantment>` +
  `RandomSource`. It returns the chiseled-bookshelf state with the six `slot_*_occupied`
  properties applied plus the six slot stacks.
* `toBlockEntityTag(...)` writes the tag exactly the way
  `ChiseledBookShelfBlockEntity#saveAdditional` does — `ContainerHelper.saveAllItems` into a
  `TagValueOutput` created with **registry-aware ops**
  (`TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess)`), which yields
  `{id, Items: [{Slot, id, count, components}, …]}`. Vanilla's `placeInWorld` feeds that tag
  straight into `loadWithComponents`, on whichever thread owns the generation step.
* The only things read from the passed `ServerLevelAccessor` are `registryAccess()` and the seed
  (`WorldGenLevel#getSeed()`, a field `WorldGenRegion` copies at construction). No chunk, no block
  entity, no `setBlock`.
* The seed is still `worldSeed ^ mix(position)`, so world generation stays deterministic per
  position and the two fill paths (template + post-process) derive identical rolls for the same
  block.

### 3.2 The facing heuristic is deferred to the server thread

The v3 facing algorithm needs the **finished** geometry: while a processor runs, the piece's own
walls are still in the template, not in the world. So:

* `finalizeProcessing` marks each filled position for chunk post-processing
  (`ProtoChunk#markPosForPostProcessing`, reached through the `WorldGenRegion` only, guarded by
  `instanceof WorldGenLevel` and a `try/catch` for the clipping-disabled case — losing the mark
  costs a facing pick, never the books).
* `LevelChunkPostProcessMixin` (already `@At("HEAD")` of `LevelChunk#postProcessGeneration`, i.e.
  server thread, chunk `FULL`) now handles both states:
  * `BOOKSHELF` → `postProcessBookshelf` (full fill: stronghold libraries, unchanged),
  * `CHISELED_BOOKSHELF` → `postProcessFilledBookshelf` (facing only, books already in the NBT).
* Post-processing runs from `ChunkMap#prepareTickingChunk`, i.e. when the chunk starts ticking. The
  player only ever sees the refined facing, and an unloaded/unvisited chunk keeps the template's
  rotated default facing instead of losing its books.

### 3.3 Live-world access is now explicitly server-thread-only and never loads a chunk

* `BookshelfBookFiller.applyToPlacedBookshelf` / `applyFacingToPlacedBookshelf` start with
  `requireServerThread(level, …)`: off-thread calls log **one** error and return instead of
  blocking. If some future code path regresses, the failure mode becomes a log line, not a frozen
  server.
* Neighbour reads go through `getLoadedBlockState(...)`, a `ServerChunkCache#getChunkNow` lookup —
  a pure cache read that returns `null` off-thread and never generates. An unknown neighbour fails
  the "front must be air" hard filter (we never rotate a shelf into a chunk we cannot see) and
  contributes no backing/side score otherwise.
* Slot contents are committed through `ChiseledBookShelfBlockEntity#getItems()`, not `setItem()`:
  `setItem` re-validates against `#minecraft:bookshelf_books` and rewrites the whole block state
  once per slot, which is both redundant (the state was computed already) and stricter than the
  NBT-load path vanilla uses for structures.

## 4. Behaviour delta

| Case | Before | After |
| --- | --- | --- |
| Template structures (villages, mansions, ancient cities, trial chambers) | Empty chiseled bookshelf, or a worldgen-thread stall/deadlock while trying to fill it | Shelf is filled during placement (books in the block-entity NBT), no live-world access |
| Same, facing | Template-rotated default facing (the fill almost never reached the shelf) | Default facing at generation, corrected on the server thread when the chunk starts ticking |
| Stronghold libraries / programmatic pieces | Filled on the server thread at chunk post-processing | Unchanged path, now guarded (server thread + non-loading neighbour reads) |
| Any future off-thread call into the live filler | Silent deadlock risk | One error log line, no-op |
| Determinism | Seed = `worldSeed ^ mix(pos)`, but the fill rarely applied | Same seed formula, applied on both paths, so a shelf always rolls the same books for its position |

## 5. Explicitly *not* changed

* Book-selection weights, `bookshelf_book_pool.json` schema, enchanted-book lore stamp.
* The v3 facing rubric itself (open axis +100, sturdy back +10, non-sturdy back +6, sides +3/+1,
  fixed WEST → EAST → NORTH → SOUTH tie-break).
* `processBlock`'s `BOOKSHELF` → `CHISELED_BOOKSHELF` rewrite and `REPLACEMENT_ENABLED`.
* The mixin set. No new mixin was needed; `LevelChunkPostProcessMixin` grew one `else if` branch.

## 6. Why not the other two suggestions from the report

* **"Use the passed `ServerLevelAccessor` and guard for positions outside the generation area."**
  Right idea, but 26.2's `WorldGenRegion#getBlockState` *throws*
  (`ReportedException: Requested chunk unavailable during world generation`) instead of failing
  fast, and inside the write zone it sees pre-placement terrain — the piece's own walls are not
  there yet, so the facing rubric would score an empty room. Pure-data fill + deferred facing keeps
  both properties.
* **"Defer the whole fill to a main-thread hook."** That is what the stronghold path does, and it
  works — but it makes the *books* depend on the post-processing pipeline firing for every chunk.
  Filling in the info list means a shelf can never come out empty; the deferred hook then only
  polishes the facing.

## 7. Verification

Done in this round (no client available in the sandbox):

* `python3 scripts/ci_static_checks.py` → `PASS` (mixin configs, 17 declared mixin reference(s), lang
  parity).
* `python3 scripts/compat_check.py` → `PASS` (Tier 1; Tier 2 skipped, no MC jar).
* `bash scripts/check_release_consistency.sh --strict` → `12 passed, 0 warned, 0 failed`.
* Compile: **not** verified from this patch. The `compile-check` workflow's push filter does not
  cover this branch, so apply the patch and run `./gradlew build --rerun-tasks` before
  releasing (or add the branch to the workflow's `on.push.branches` list).

Still open (needs a real client):

1. DH + C2ME + Lithium world, `FEATURES`/`SURFACE_THEN_CHUNKS`, generate distant terrain while
   flying: no freeze, `jstack` shows no `DH-World Gen Thread` in `CompletableFuture.join()` under
   `BookshelfReplacerProcessor`.
2. A village library / mansion library / ancient-city shelf: books present, `slot_*_occupied`
   matches the contents, shelf faces the open side.
3. Stronghold library regression pass (post-process path).
4. A world with `bookshelf_book_pool.json` `book_count = {min: 0, max: 0}` still swaps the plain
   bookshelf for an empty chiseled one.
