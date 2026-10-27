# Canonical Domain & Localization Glossary

> **Applies to:** Chiseled Enchanting Table (`1.1.0`, Minecraft `26.2`).
> This glossary defines the authoritative English (`en_us`), Simplified Chinese (`zh_cn`),
> Traditional Chinese (`zh_tw`), and Java/datapack identifiers for every core concept in the mod.
> Contributors adding strings, documentation, or datapack keys must keep these terms consistent.

## 1. Blocks, Items & Guides

| Concept | Java / Registry ID | `en_us` | `zh_cn` | `zh_tw` | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Mod / Primary Station** | `chiseled_enchanting_table:chiseled_enchanting_table` | Chiseled Enchanting Table | 纂刻附魔台 | 纂刻附魔台 | Never mix `雕纹附魔台` in Traditional Chinese (`zh_tw`); `雕紋` is reserved for vanilla `雕紋書架`. |
| **Depleted Tome** | `chiseled_enchanting_table:depleted_tome` | Depleted Tome | 褪魔残卷 | 褪魔殘卷 | Residual tome left after transferring, exhausting (`step_down` at L1 / `deplete`), or cleansing an Enchanted Book. Counts toward Broad Bookshelves when stored in a Chiseled Bookshelf. |
| **Arcane Synthesis Crystal** | `chiseled_enchanting_table:arcane_catalyst` | Arcane Synthesis Crystal | 秘法凝晶 | 秘法凝晶 | High-tier universal catalyst substitute for multi-ingredient rituals (Epiphany, solo book study, targeted cleanse, curse purge). |
| **Cleansing Grit** | `chiseled_enchanting_table:cleansing_grit` | Cleansing Grit | 退魔砂 | 退魔砂 | Crafted catalyst (`4×` per craft) used for targeted single-enchantment stripping, curse purging, and early manual tome-lock release. |
| **Chiseled Grimoire** | `chiseled_enchanting_table:arcane_grimoire` | Chiseled Grimoire | 纂刻秘典 | 纂刻秘典 | Interactive illustrated in-game manual (also accessible directly from the station header button). |

## 2. Bookshelf Categories

| Category | Code / Tag Identifier | `en_us` | `zh_cn` | `zh_tw` | Gameplay Role |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Regular (Wooden) Bookshelf** ($W$) | `#chiseled_enchanting_table:regular_bookshelves` | Regular Bookshelf / Wooden Bookshelf | 普通书架 / 木书架 | 普通書架 / 木書架 | Excludes `ChiseledBookShelfBlock` (enforced both in `BookshelfProgression.isWoodenBookshelf` and startup/reload tag validation). Governs blank mid-tier caps, `0..38` upgrade ladder (`MAX=2` at `22`), `-15%` wooden XP discount (`3..45`), `32+` Treasure Epiphany unlock, and Depleted Tome restoration tiers (`12` / `32`). |
| **Empty Chiseled Bookshelf** ($E$) | `minecraft:chiseled_bookshelf` (0 occupied slots) | Empty Chiseled Bookshelf | 空的雕纹书架 | 空的雕紋書架 | Contributes `0` to both Regular ($W$) and Broad ($S$) shelf counts until at least one book/tome is inserted. |
| **Occupied Chiseled Bookshelf** ($C$) | `minecraft:chiseled_bookshelf` ($\ge 1$ occupied slot) | Occupied Chiseled Bookshelf | 有书的雕纹书架 | 有書的雕紋書架 | Any slot holding a Book, Writable/Written Book, Chiseled Grimoire, Depleted Tome, or Enchanted Book counts the block as 1 Occupied Chiseled Bookshelf ($C$). |
| **Broad Bookshelf** ($S = W + C$) | `broadBookshelves` | Broad Bookshelf | 广义书架 | 廣義書架 | Sum of Regular Bookshelves + Occupied Chiseled Bookshelves. Governs Master Tome passive regeneration (`0 -> 38 -> 42` shelves), Deep Inscription wait threshold (`>= 32`), and wear-immune overload slip tiers. |
| **Master Tome (Template Book)** | Stored `minecraft:enchanted_book` inside `minecraft:chiseled_bookshelf` | Master Tome / Template Book | 雕纹母本 / 模板附魔书 | 雕紋母本 / 模板附魔書 | Unlocks direct `1..k` inscriptions on blank items (including Treasures), switches XP deduction to Vanilla `1–3` Level Mode, grants global/specific mastery XP discounts, and participates in wear/regeneration/locks. |

## 3. Mechanics, Tags & Configuration Terms

| Term | Code / JSON Key | `en_us` | `zh_cn` | `zh_tw` | Definition |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Treasure Epiphany** | `treasure_epiphany.json`, `MenuEpiphanyController` | Treasure Epiphany | 宝藏顿悟 | 寶藏頓悟 | Rotating/freezable treasure enchantment offer unlocked at `32+` Regular Bookshelves. |
| **Deep Inscription Ritual** | `InscriptionRitualMechanics`, `ritual.wait_curve` | Deep Inscription | 深层铭刻 | 深層銘刻 | Queued timed inscription active in dense libraries ($S \ge 32$ or $\ge 10$ stored enchantment entries), skippable via Soul-Fed XP Sacrifice or Rush Gamble. |
| **Provenance** | `EnchantmentProvenanceData` (`cet_provenance`) | Table Provenance | 本台溯源 | 本台溯源 | Per-enchantment record distinguishing levels inscribed at the Chiseled Enchanting Table (`xp_refund_scale`) from external loot/villager/anvil enchantments (`external_xp_refund_scale`). |
| **Copy Generation** | `TomeWearData` (`cet_generation`) | Copy Generation | 副本世代 | 副本世代 | Incremented when copying an Enchanted Book from a Master Tome; caps effective charges at `max(1, max_tome_uses - generation)` (waived while `generation <= wear_immune_max_generation` for `#wear_immune` tomes). |
| **Essence Valuation** | `library_resonance.json -> essence_valuation` | Essence Valuation | 精华估值 | 精華估值 | Rarity- and keystone-weighted enchantment value used for disenchant XP conservation and `scapegoat_pricing = "essence"` charge recovery. |
| **Keystone Major / Minor** | `#chiseled_enchanting_table:keystone_major`, `#keystone_minor` | Keystone Enchantments | 核心基石魔咒 | 核心基石魔咒 | Datapack enchantment tags assigning higher essence multipliers and ritual applicability weights to economy-defining enchantments (e.g., Mending, Infinity, Fortune, Looting, Unbreaking). |
| **Broad Applicability** | `#chiseled_enchanting_table:broad_applicability` | Broad Applicability | 广谱适用词条 | 廣譜適用詞條 | Datapack tag marking cross-equipment enchantments for Treasure Epiphany weighting and ritual applicability curves (`ritual.applicability`). |
| **Classic / Modern Presets & Datasets** | `Preset.CLASSIC` (`"classic"`), `CostTableDataset.MODERN` (`"modern"`) | Classic / Modern | 经典原味 / 现代标准 | 經典原味 / 現代標準 | De-versioned canonical configuration tokens (`classic_1_0_3` and `modern_1_1_0` remain accepted as deprecated read aliases with log warnings). |
| **Rule Trace (Rule Atoms)** | `ConfigCategory.CLASSIC_COMPARE` | Classic vs Modern Rule Trace | 经典与现代规则对比 | 經典與現代規則對比 | Category 8 in the configuration screen exposing all 20 orthogonal rule atoms that distinguish Classic and Modern gameplay. |
