# Next Task: Post-1.1.0 Live Verification & Backlog (Minecraft 26.1.2 Line)

> **Unofficial community port.** All instructions below apply to the unofficial `26.1.2` port
> (this repository). The upstream `Sacramentix/chiseled-enchanting-table` project is not affected
> by this version line.

**Current mod version:** `1.1.0` · **Wire protocol:** `26_1_2_revision_17` · **Minecraft:** `26.1.2` ·
**Fabric API:** `0.151.0+26.1.2` · **Loader:** `0.19.3` · **Loom:** `1.16-SNAPSHOT` · **Gradle wrapper:** `9.4.1`.
See [`CURRENT_BASELINE.md`](CURRENT_BASELINE.md), [`docs/WIKI.md`](docs/WIKI.md), and
[`docs/DOCUMENTATION_INDEX.md`](docs/DOCUMENTATION_INDEX.md) for current architecture and documentation.

## Building & Running Static Gates

```bash
chmod +x ./gradlew

# Fast offline gates (no network, no JDK, no Gradle required):
python3 scripts/ci_static_checks.py                 # mixins, 11-locale parity/slots/BMP/coverage, markdown links, BundledDefaults
python3 scripts/compat_check.py                     # data-pack schema & code-vs-JSON invariants (Tier 1)
bash scripts/check_release_consistency.sh --strict  # 5-way version consistency gate
bash docs/ci/install.sh --check                     # docs/ci ≡ .github/workflows

# Full build + headless JUnit suite (requires JDK 25 + Maven access, or run via GitHub Actions):
./gradlew --no-daemon build --rerun-tasks --stacktrace
```

## Open Items (Priority Order)

### A. In-Client Live Smoke Pass for `1.1.0` (`26_1_2_revision_17`)

All static gates, headless JUnit suites, and CI compile/test workflows validate bytecode and math invariants automatically; run the following interactive client checks before tagging a public GitHub release:

1. **80-Option Configuration Screen & Classic/Modern Rule Trace**:
   - Open via station `⚙` overlay button, `Chiseled Grimoire` `[⚙ Config]` button, ModMenu, and `/cet config`.
   - Switch between `Classic`, `Casual`, `Standard`, and `Expert` presets; verify individual toggles on Category `8` (`Classic vs Modern Rule Trace`) and confirm `ESC` closes overlays cleanly back to the parent screen without dropping items.
   - Test Unicode/CJK input and cursor navigation in the search box (`/` or `Ctrl+F`).
2. **11-Locale Visual Layout & Font Check**:
   - Spot-check `en_us`, `zh_cn`, `zh_tw` (`纂刻附魔台` / `雕紋書架`), `ja_jp`, `ru_ru`, `de_de`, and `fr_fr` across `GUI Scale 2–4` to confirm zero text clipping and crisp BMP symbol rendering (`⊘`, `✦`, `◆`, `❖`).
3. **Datapack `/reload` & Sync Safety**:
   - Keep a Chiseled Enchanting Table menu open while running `/reload` -> verify the menu closes cleanly with the localized reload notice (`message.chiseled_enchanting_table.reload_closed`) and re-opening reflects updated `library_resonance.json` / `enchantment_costs.json` values in both the station UI and JEI/REI.
4. **Mixin & Villager Edge Cases**:
   - Walk through [`docs/MIXIN_UPGRADE_CHECKLIST.md`](docs/MIXIN_UPGRADE_CHECKLIST.md) §3 (grindstone Shift-click Depleted Tome output, locked shelf hopper/break protection, facing-aware bookshelf particles, and `restrict_villager_infinity_books` `Infinity -> Power I` repricing at `5..8` emeralds).

### A2. 26.3 回移植后续（2026-10-26 彻查轮遗留，见 docs/reports/BACKPORT_FROM_26_3_2026_10_26.md）

- **实机冒烟**：母本目录（过滤/排序/多选/批量锁队列二次确认、Shift 全附魔分页）在专用服务器 + 客户端各跑一轮；创造模式书架精确抽取（`BookshelfCreativeExtractionMixin`）手测；坏数据包 `/reload` 应被拒绝而非静默半发布。
- **Rainbow Cloth（未移植，需独立立项）**：涉及方块状态 `RAINBOW_CLOTH` schema 变更——必须按 `docs/PROTOCOL_HISTORY.md` §3 升协议号、评估旧档迁移，并先在 26.1.2 上验证 `ColorLerper`/`submitCustomGeometry`/`colorOrDefault` 等价 API；BER 保持本分支 `mulPose`+10 参 `submitModel`。
- **UI 可读性包（未移植）**：`TooltipPresentation`/`TooltipBudget` 与 `ui.*` 键的整体策略移植需单独适配本分支 6 参提示与 `event.hasShiftDown()` 输入模型。
- **保留键清理（可选）**：`tomes.btn_lock_all`/`tip_lock_all` 已无代码引用（服务端 `LOCK_ALL_FULL` 处理为线兼容保留）；下一次大版本可与 `LOCK_ALL_FULL` 服务端处理一并评估移除。

### B. Dedicated Multi-Slot Auxiliary Cost Panel (Future UI Enhancement)

Currently the container exposes `ENCHANTABLE_SLOT` + `COST_SLOT`, while `additional_costs` (from `enchantment_costs.json`, `disenchanting.json`, and `item_transformations.json`) are displayed in row/slot tooltips andJEI/REI and consumed automatically from the player's inventory. Adding physical auxiliary input slots to `ChiseledEnchantingTableScreenHandler` remains an optional future UX expansion.

### C. Optional Third-Party Mod Integrations

- `chiseled_enchanting_table.integration.*` — Colorful Books visual integration remains parked/opt-in until verified against a `26.1.2` build of the target mod.
- Cross-branch maintenance (`26.2` / `26.3` branches): sync Waves A–D (`Tome` locks, 80-option layered config + 20-atom Rule Trace, datapack unification, `SearchTextBuffer`, `PayloadLimits`, and 975-key 11-locale translations) following [`docs/reports/SYNC_TO_26_2_AND_26_3_2026_10_26.md`](docs/reports/SYNC_TO_26_2_AND_26_3_2026_10_26.md).
