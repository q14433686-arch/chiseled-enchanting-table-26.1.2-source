# Enchantment Fusion（融合附魔）

![Enchantment Fusion icon](./icon.png)

**Current version:** `0.6.0`
**Target:** Minecraft `26.1.2`, Fabric Loader `>=0.19.3`, Fabric API `0.151.0+26.1.2`, Java `>=25`.

A Fabric enchanting mod with ordered fusion (`[A ▷ B]` is not necessarily `[B ▷ A]`), equipment inscription, single-enchantment cross-domain transmutations, and an in-game Fusion Grimoire.

- **[玩法与规则说明 / Game design](docs/GDD.md)** — recipes, operation, economy, configuration and technical boundaries.
- **[审计与建议 / Audit notes](docs/AUDIT_NOTES.md)** — verified fixes, remaining risks, hard-coded values and test coverage.
- **[术语表 / Glossary](docs/GLOSSARY.md)**

The bundled data has **151 signature entries** (148 exact ordered pairs and 3 wildcard rules) and **116 transmutation variants**. Other valid ordered pairs can use the trigger/payload matrix; not every reversed pair has its own signature. Datapacks and server overrides can change the active rules.

The first redesigned pair is **Fire Aspect × Knockback**: Fire ▷ Knockback converts a wall impact into a heat burst; Knockback ▷ Fire leaves a temporary wake along the target's actual movement. These two directions replace their vanilla primary effect rather than stacking it. Existing equipment is normalized as it reaches a player's inventory, preserving the pair and levels. **Loyalty ▷ Protection** is also being implemented as a level-consuming lethal-damage rescue; its [data-preservation and target boundaries](docs/LOYALTY_GUARD_IMPLEMENTATION.md) have not yet been validated in this round's game acceptance. Other directions remain under implementation; see the [current status](docs/FUSION_IMPLEMENTATION_STATUS.md) and [design record](docs/FUSION_REDESIGN.md).

## Install / 安装

Install Fabric Loader and Fabric API for 26.1.2, then put `enchantment-fusion-0.6.0.jar` in `mods/`. For multiplayer, install the same mod build on **both client and server**. Mod Menu, JEI/REI and the sister mod **Chiseled Enchanting Table（纂刻附魔台）** are optional; consult their own version compatibility before combining them. GitHub Actions builds provide downloadable artifacts while they remain available.

## Start playing / 快速开始

1. Craft an **Enchantment Fusion Table（附魔融合台）**. The 3×3 recipe is:

   | Amethyst Shard | Book | Amethyst Shard |
   | :---: | :---: | :---: |
   | Lapis Block | Anvil (any `#minecraft:anvil`) | Lapis Block |
   | Polished Deepslate | Polished Deepslate | Polished Deepslate |

2. Put enchanted books in the A/B slots and select their order. With the target/output slot empty, synthesize a Fused Enchanted Book; place an eligible item in that slot to work on equipment. Some combinations are rejected for compatibility, capacity or level reasons. The altar's preview is authoritative about the current price and risk.
3. The **Single** mode inscribes a supported non-native enchantment as a cross-domain transmutation; a fused pair uses a vessel slot. Most eligible equipment starts with `1◇`, and items in the `innate_two_vessel_items` tag start with `2◇` (gold equipment is included by default). Other eligible equipment can gain one slot, up to `2◇` total.
4. Craft the **Fusion Grimoire（融合秘典）** with a Book, Lapis Lazuli and Amethyst Shard, or open its in-table view to look up the active rules.

The swap control reverses the selected order with a left click; right click or Shift-left-click swaps the physical input slots. In-progress rituals belong to the server: **Esc closes the table without cancelling**. Reopen it to view progress; the initiator can click again or press Space/Enter to skip, or Shift-click the action button to cancel. Inputs are locked while it runs; unloaded chunks pause it, and owner logout or death cancels without consuming inputs.

## Risk, frost and harvesting / 风险、霜冰与挖掘

- Default synthesis risk depends on whether a pair is exclusive, at maximum levels, a signature, and its level sum. Normal fluctuations keep the books and gear but charge part of the fee. High-risk fusion can produce an aberration; high-risk single transmutation failure instead consumes the book and can apply Curse of Vanishing. The default mutually-exclusive base risk is **45%**, with an **8%** ordinary-stabilizer floor. See the [economy table](docs/GDD.md#default-economy) rather than extrapolating from one number.
- An aberration can be purified at this table: leave A/B empty, put the affected book or equipment in the target slot and, by default, supply **3 Resonance Stabilizers and 25 XP levels**. Costs are configurable. The optional sister-mod integration provides another route when compatible.
- Mod-created temporary frost replaces **air only** and neither breaking nor natural decay creates water. Adjacent original water can still flow naturally after a seal disappears. Frost seal leaves water intact, but adjacent lava **permanently becomes cobblestone**. The thin ice wake is placed above source water, not in it; sneak or enter the water to stop placing new path blocks.
- Extra-area mining uses the player's normal per-block break path, so real tool/loot rules and durability apply; it skips block entities. Sneak to pause the multi-block mining skills. Wide Reap extends only to crops that can be confirmed mature. Other world-changing effects and third-party protection mods still need the compatibility testing described in the [audit notes](docs/AUDIT_NOTES.md#已确认的剩余风险).

## In-game configuration / 游戏内配置

Run **`/fusion config`**, or open the same screen through optional Mod Menu. The singleplayer owner and authorized server operators can save; other players can inspect server rules read-only. Server permission is checked for every update.

The five categories expose existing economy/cooldown, matrix profile, exclusive-group, signature and transmutation fields. Search accepts field paths; hovering over a clipped row or hint shows more text. `*` marks an override; **Use datapack** removes it. Probabilities use `0–1`, cooldowns use ticks, booleans use `true`/`false`, and arrays require a complete JSON array. The screen edits existing fields only, not arbitrary new datapack entries or code-defined skill behavior.

Overrides are written on the **server** to `config/enchantment_fusion/overrides.json`, validated, applied immediately and synchronized to connected clients. `/reload` rereads datapacks and overrides. Invalid overrides are retained on disk for repair and are not partially applied. The title-screen Mod Menu edits that installation's bundled defaults/overrides; world datapacks are loaded when a world starts.

游戏内输入 **`/fusion config`**，或通过可选 Mod Menu 打开；单人世界主人及有权限的管理员可保存，其他玩家只读。修改保存在服务端，校验后立即生效并同步；`*` 表示覆盖，**使用数据包值**可撤销覆盖。数组输入须是完整 JSON 数组。

## License

MIT — see [`LICENSE`](LICENSE).
