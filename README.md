# Chiseled Enchanting Table — Unofficial 26.3 Port

![Chiseled enchanting table banner](./banner.png)

> ⚠️ **Unofficial community port.** This repository is a community-maintained, unofficial port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> updated to **Minecraft 26.3** on Fabric. It is **not affiliated with, endorsed by, or
> maintained by the original author**. All credit for the original mod design, code, and assets
> belongs to Sacramentix and is used under the original [MIT License](LICENSE). Please direct
> bug reports about this port to **this** repository (`q14433686-arch/chiseled-enchanting-table-26.1.2-source`),
> not to the upstream project.

**Current version:** `1.0.3` *(unofficial-port SemVer; see `CURRENT_BASELINE.md` for details)*
**Target:** Minecraft `26.3`, Fabric Loader `>=0.19.5`, Fabric API `0.162.0+26.3`, Java `>=25`.

A Minecraft Fabric mod that enhances the enchanting experience.

## Concept

* Add a new chiseled enchanting table
* Unlock new enchantment for chiseled enchanting table by placing enchanted book in nearby chiseled bookshelves
* Explore your world to discover new enchanted books!

## Installation

Grab the latest `chiseled-enchanting-table-1.0.3.jar` from
`release_output/chiseled_enchanting_table_1_0_3/` (or build it yourself with
`./gradlew build` using JDK 25 — see `NEXT_TASK.md` for the exact command chain) and drop it into
your Fabric `mods/` folder alongside Fabric API `0.162.0+26.3`.

This unofficial port is **not** currently published to Modrinth or CurseForge under the original
mod's listing. If a release is published, it will be under the unofficial-port repository's own
release page, not the upstream listing.

## Usage

Once installed, the Chiseled Enchanting Table will be available in your game. Craft it using the new recipe and start enchanting!

## Continuous integration

CI mirrors the triad used by the sister port `q14433686-arch/TaCZ_Refabricated_Unofficial`, because
this port is developed in a sandbox that cannot reach `maven.fabricmc.net` and therefore cannot
compile locally — the compile has to happen on Actions and the log has to come back to the branch.

> ⚠️ The triad is **written and staged in [`docs/ci/`](docs/ci/README.md)**, not yet in
> `.github/workflows/`: pushing workflow files is rejected for a GitHub App token without the
> `workflows` permission. Install with `bash docs/ci/install.sh` (or grant the App
> `Workflows: read & write`). Until then only the pre-existing build workflow runs.

| Workflow (staged) | What it does | Trigger |
|---|---|---|
| [`docs/ci/compile-check.yml`](docs/ci/compile-check.yml) | `compileJava compileClientJava`, then **pushes the compile log back to `arena/**` branches** as `build-reports/compile-java.log` via the Contents API (Actions log blobs are unreachable from the sandbox) | push to `arena/**` / release branches, PRs |
| [`docs/ci/build.yml`](docs/ci/build.yml) | static gates first (no network needed), then full `./gradlew build` + jar artifact (14 days) | push / PR |
| [`docs/ci/consistency.yml`](docs/ci/consistency.yml) | version gate for "bumped one place, forgot the other four" | changes to `gradle.properties`, `README.md`, `fabric.mod.json`, `publish.yml` |
| [`docs/ci/publish.yml`](docs/ci/publish.yml) | manual `workflow_dispatch` release to GitHub / Modrinth (version-bumped to 26.3 / 1.0.3) | manual |

The three static gates run in seconds and need no Gradle, so they also work offline:

```bash
python3 scripts/ci_static_checks.py                 # mixin configs resolvable + registered mixins not excluded + en_us/zh_cn key parity
python3 scripts/compat_check.py                     # data-pack schema / JSON-vs-Java drift (Tier 1)
bash scripts/check_release_consistency.sh --strict  # gradle.properties / README / fabric.mod.json / publish.yml agree
```

Optional warn-only pre-commit reminder: `bash scripts/install-hooks.sh` (it never blocks a commit;
CI is the real gate).

## Forge (or other modloader) port

Not planned at the moment.
But if the mod get more popular, I might have a look at it!

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.

## License

This project is licensed under the [MIT License](LICENSE).

## Inspiration

I've looked at RPG enchanting table to get the base concept done
https://github.com/TheRedBrain/rpg-enchanting
If you are looking for a more RPG approch be sure to check out this mod!
