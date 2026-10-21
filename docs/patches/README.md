# Cross-branch patches

Point-in-time patches that carry the 26.3 fix to the two older lines of this repository. They live
here because the sandbox session that produced them is pinned to the 26.3 branch and cannot push
anywhere else.

| Patch | Target branch | Target version |
| --- | --- | --- |
| `26.2-worldgen-bookshelf-deadlock.patch` | `chiseled-enchanting-table-26.2-source` | Minecraft 26.2 / mod 1.0.2 |
| `26.1.2-worldgen-bookshelf-deadlock.patch` | `chiseled-enchanting-table-26.1.2-source` | Minecraft 26.1.2 / mod 1.0.2 |

Both fix the same defect that [issue #3 "Chuck soft crashes"](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3)
reports: `BookshelfReplacerProcessor#finalizeProcessing` ran on a worldgen worker thread but reached
through `WorldGenRegion#getLevel()` into the live `ServerLevel`, reading/writing the chunk it was
itself generating. That is a hard deadlock (server keeps running, world stops, work is lost) and,
because the fill never actually landed, it also left template-placed chiseled bookshelves **empty**.
Full analysis: [`../reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`](../reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md).

## What a patch changes

Per branch, four files:

- `mechanics/structure/BookshelfBookFiller.java` — split into a pure half
  (`computeFill`, `toBlockEntityTag`: pool JSON + enchantment registry + seed only) and a
  live-world half (`applyToPlacedBookshelf`, `applyFacingToPlacedBookshelf`) that refuses to run
  off the server thread and never loads a chunk for a neighbour read
  (`ServerChunkCache#getChunkNow`).
- `mechanics/structure/BookshelfReplacerProcessor.java` — `finalizeProcessing` no longer touches a
  level: it returns a finished `StructureBlockInfo` (state with `slot_*_occupied` + block-entity
  NBT), which vanilla's `StructureTemplate.placeInWorld` applies via `loadWithComponents`.
- `mixin/structure/LevelChunkPostProcessMixin.java` — the deferred, server-thread facing pass
  (`CHISELED_BOOKSHELF` branch) next to the existing `BOOKSHELF` fill.
- `docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md` — the write-up, version-adjusted.

`CHANGELOG.md` is deliberately **not** touched, so the patch cannot conflict with whatever else
lands on those branches. Suggested release note:

> **Fixed — worldgen deadlock / unloadable chunks.** Structure-placed bookshelves were filled from
> a worldgen worker thread against the live `ServerLevel`, which deadlocked the server whenever the
> target chunk was still being generated (deterministic with Distant Horizons' `FEATURES` LOD
> generator: no TPS, no log, unsavable world). The fill is now pure and travels in the structure
> block info; the shelf-facing pass runs on the server thread after the chunk starts ticking.
> Template-placed chiseled bookshelves are also actually filled now (previously they came out
> empty).

## Applying

```bash
# 26.2 line
git switch chiseled-enchanting-table-26.2-source
git apply -p1 /path/to/docs/patches/26.2-worldgen-bookshelf-deadlock.patch

# 26.1.2 line
git switch chiseled-enchanting-table-26.1.2-source
git apply -p1 /path/to/docs/patches/26.1.2-worldgen-bookshelf-deadlock.patch
```

`git apply --check -p1 <patch>` passes against the current tip of each branch (`9ef1889` on 26.2,
`79717b5` on 26.1.2), verified by extracting `git archive` of both branches and applying the patch
to the clean tree. If a branch moves, `git apply --3way` is the fallback.

Integrity (sha256):

```
751b27f81e7e1db1424335453414abb77da2bf493dbbe171efdc6ed6a9040344  26.1.2-worldgen-bookshelf-deadlock.patch
e7a3534ea1ac24ee3291279ac49d948bf1944566edc041604752c15ff1aea990  26.2-worldgen-bookshelf-deadlock.patch
```

## API differences handled per branch

| | 26.2 | 26.1.2 |
| --- | --- | --- |
| `StructureProcessor` | interface, `codec()` | **abstract class**, `getType()`, `processBlock(..., StructureBlockInfo originalBlockInfo, ...)` |
| post-processing mark | `ChunkAccess#markPosForPostProcessing` | `ChunkAccess#markPosForPostprocessing` |
| `ChunkStatus` package | `net.minecraft.world.level.chunk.status` | same |
| `processBlockInfos` clipping | clipped to the generating chunk | **not clipped** — the processor sees the whole piece; the deferred facing pass skips positions outside the worldgen region |
| `TagValueOutput` / `ContainerHelper.saveAllItems(ValueOutput, …)` / `ProblemReporter.DISCARDING` / `ServerChunkCache#getChunkNow` | present | present |

Symbols were checked one by one against decompiled 26.2 and 26.1 sources; every `net.minecraft.*`
import in the patched files resolves to a class in those trees.

## Verification state

Verified here:

- `git apply --check -p1` (clean) on a fresh `git archive` of each branch, and the applied result is
  byte-identical to the file set the patch was generated from.
- Every imported Minecraft class exists in that version's source tree; the members the patch calls
  (`markPosForPostProcessing`/`markPosForPostprocessing`, `getChunkNow(int,int)`,
  `TagValueOutput.createWithContext`, `ContainerHelper.saveAllItems(…, boolean)`,
  `ProblemReporter.DISCARDING`, `BlockableEventLoop#isSameThread`, `ChunkStatus.EMPTY`,
  `ChiseledBookShelfBlockEntity#getItems`, `StructureTemplate.placeInWorld`'s
  `loadWithComponents` step) were confirmed against 26.2 and 26.1 sources.

**Not** verified (needs the maintainer):

1. `./gradlew build` on each branch — the `compile-check` workflow's `on.push.branches` filter does
   not include `chiseled-enchanting-table-26.2-source` / `-26.1.2-source`, so nothing compiles these
   automatically. Adding those two branches to the workflow would give you a check on push.
2. In-game: reproduce issue #3 (fly into an affected mesa village chunk on a *fresh* world) and
   confirm no freeze; confirm the shelves in village libraries / mansions / ancient cities actually
   contain books and face the open side; regression-test stronghold libraries.
