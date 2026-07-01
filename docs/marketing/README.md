# Marketing / Listing Copy

This folder holds ready-to-paste listing copy for the unofficial **Chiseled Enchanting Table — 26.1.2 port**
(`1.0.2`) on three platforms. Always paste verbatim from these files so the "unofficial port"
status, author credit, and version target stay consistent everywhere.

| File | Target |
|---|---|
| [`modrinth.md`](modrinth.md) | Modrinth project page — short summary + full description (Markdown). |
| [`curseforge.md`](curseforge.md) | CurseForge project page — short summary + full description (HTML-friendly Markdown / BBCode-safe). |
| [`mcmod.md`](mcmod.md) | MC百科 (mcmod.cn) — 中文简介、模组介绍正文、编辑要点。 |

## Project facts to keep consistent (do not edit per-platform without updating here too)

- **Display name:** Chiseled Enchanting Table — Unofficial 26.1.2 Port
- **Original mod:** [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table) — MIT
- **Original mod (Modrinth):** <https://modrinth.com/mod/chiseled-enchanting-table>
- **This port (source repo):** <https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source>
- **Port version:** `1.0.2`
- **Minecraft target:** `26.1.2`
- **Loader:** Fabric Loader `>=0.19.3`
- **Required:** Fabric API `0.151.0+26.1.2`
- **Java:** `>=25`
- **Side:** client & server (both required)
- **License:** MIT (inherited from upstream)
- **Chinese display name (官中):** 纂刻附魔台 (carried over from the upstream zh_cn translation in this repo)

## Cover artwork (ready to upload)

All three platforms need a square icon. Use the staged copies under
`release_output/chiseled_enchanting_table_1_0_2/`:

| File | Size | Use for |
|---|---|---|
| `cover-icon-256.png` | 256×256 PNG RGBA | Modrinth project icon (≥64×64; 512×512 recommended, 256 still acceptable), CurseForge project avatar (400×400 recommended; will auto-scale), MC百科 模组封面 (240×240 推荐，会自动缩放) |
| `cover-banner.png` | 660 845 B PNG | Optional gallery banner / first screenshot on Modrinth and CurseForge; MC百科 「封面图」(240×150 会被自动拉伸) 也可裁剪后使用 |

The same `cover-icon-256.png` bytes are already embedded inside
`chiseled-enchanting-table-1.0.2.jar` at `assets/chiseled-enchanting-table/icon.png`, so the
in-game mods-list entry matches the platform listings.

### Platform-specific icon notes

- **Modrinth:** upload via project Settings → "Project icon". Accepts PNG/JPG, square; renders at
  various sizes from 64 to 256 in search/cards. `cover-icon-256.png` is fine; if you want maximum
  crispness, you can upscale to 512×512 with nearest-neighbour to preserve pixel-art edges.
- **CurseForge:** upload via project Settings → "Avatar". Square; 400×400 recommended; will be
  auto-cropped to a square if you upload a rectangle. `cover-icon-256.png` works; for best
  results upscale to 400×400 nearest-neighbour.
- **MC百科:** 模组封面字段 (240×240 上下) 直接传 `cover-icon-256.png` 即可，百科会自动缩放。
  如果想顺手补一张「模组图集」首图，把 `cover-banner.png` 也上传。

---

## Universal "unofficial port" disclaimer (paste at the top of every listing)

> This is an **unofficial community port** of Sacramentix's *Chiseled Enchanting Table* to Minecraft 26.1.2.
> It is **not affiliated with, endorsed by, or maintained by the original author**.
> All original mod design, code, and assets © Sacramentix, used under the MIT License.
> Please direct bug reports about this 26.1.2 port to **this** repository, not to the upstream project.

> 本项目是 Sacramentix 制作的 *Chiseled Enchanting Table* 模组的 **非官方社区移植版**，仅用于将其更新到 Minecraft 26.1.2。
> **与原作者无任何隶属或背书关系**，原模组的设计、代码与资源版权归 Sacramentix 所有，依 MIT 协议使用。
> 与本 26.1.2 移植版相关的 bug 请到本仓库反馈，不要打扰原作者。
