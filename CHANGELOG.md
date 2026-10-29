# Changelog

## [0.5.0] - 2026-10-29

### Gameplay Overhaul — 3D Flying Blades, Full `43 × 15` Cross-Domain Coverage, Native `纂刻附魔台` Cleanse & Curse Purge, Directional Synergy Engine & Rich Inside/Outside-UI Audio-Visuals

- **3D Boomerang Flying Blade Engine (`飞剑掷斧` / `FlyingBladeManager`) & Projectile Launch Snapshot**:
  - Rebuilt `飞剑掷斧 (returning_blade_throw)` on Swords, Spears, Axes, and Maces into an immediate direct Right-Click active skill (`InteractionResult.SUCCESS` with arm swing + vanilla item cooldown overlay — zero hidden `Shift` requirement) that launches a visible spinning 3D weapon (`Display.ItemDisplay`) outbound and back to the owner's hand, slicing multiple enemies along both legs, preserving `invulnerableTime` around bonus hits, triggering on-hit enchantments/transmutations, and vacuuming nearby item drops & XP orbs back to the player.
  - Added `ProjectileLaunchWeaponAccess` + `ProjectileImpactFusionMixin#setOwner` snapshot capture so Bow, Crossbow, and Trident projectiles reliably trigger cross-domain and fused launch/impact traits without relying on fragile main-hand state at impact time.
- **Full `43 × 15` Standalone Cross-Domain Transmutation Coverage (`140` Variants)**:
  - Removed undocumented `Shift + Right-Click` gates across melee active skills (`激流斩`, `回旋弧`, `虚空踏步`, `乱流升羽`, `喷流推进`) while safely yielding when the offhand holds a usable Shield, Food, Block, or Totem.
  - Added `TargetRole.ELYTRA` and 25 new cross-domain variants (`炽焰燃矢`, `烈阳烙印`, `余烬火衣`, `沸水炙钩`, `潮汐甲胄`, `深渊打捞`, `凝霜寒甲`, `冰窟寒钓`, `远洋激流`, `鳃膜律动`, `乱流升羽`, `噬魂掘进`, `荆棘鹤嘴`, `反击散射`, `滑流掠影`, `巧手柔纶`, `碎岩重锋`, `旋风衣摆`, `折光棱甲`, `破甲穿针`, `怒涛回气`, `裂甲震壳`, `喷流推进`, `御雷巡翼`, `灵犀自钓`), covering all **43** `26.1.2` enchantments across Swords, Spears, Axes, Maces, Tridents, Bows, Crossbows, Dig Tools, Shields, Armor, **Elytra**, and **Fishing Rods**.
- **Native Sister-Mod `纂刻附魔台` (`chiseled-enchanting-table`) Cleanse & Curse-Purge Integration**:
  - Added `FusionMixinConfigPlugin` + 9 optional `@Pseudo` bridge mixins (`CetCleanseIds`, `CetCleanseOperations`, `CetCleanseServerBridge`, `CetCleanseClientBridge`) with zero hard dependency.
  - **Aberrant Purification (`PURGE_CURSE` / `PURGE_ALL_CURSES`)**: `畸变词条` appear natively as purgeable curses inside `纂刻附魔台` and purify cleanly into pure `[A ▷ B]` binary fusions without destroying the underlying fusion!
  - **Fused & Transmuted Cleanse (`EXTRACT_ENCHANTMENT_BOOK` / `STRIP_SINGLE_ENCHANTMENT` / `DOWNGRADE_ENCHANTMENT` / `DISENCHANT_ALL`)**: Clean `FUSED_PAIRS` extract onto `融合附魔书` (freeing a `◇` Vessel Slot), `融合附魔书` disenchants back to `minecraft:book`, and `异位化形` supports step-down, book restoration, targeted strip, and full disenchant with smart XP essence refunds and zero mirrored-primary duplication.
- **Binary Fusion Redesign (`SignaturePairs` + Directional Catalyst × Law `MatrixEffectExecutor`)**:
  - Eliminated `!hostStack.isDamaged()` dead-zone traps across `starlight_quiver`, `soul_siphon_mend`, `vital_siphon_shot`, `pristine_overcharge`, `spectral_condense`, and `overflow_edge` via dual-state integrity resonance; wired `沧溟飞剑` and `御风剑气` to physical 3D flying sword-qi projectiles; removed the `Shift` gate on `幻影回溯`; and upgraded buff-only signatures with visceral area/plunder/physical mechanics.
  - Rewrote `MatrixEffectExecutor` into the **Directional Catalyst × Law Synergy Engine** (`primaryTrigger × secondaryPayload × HostForm`), replacing generic short potion fillers with physical cleave shockwaves, gale launches, frost-shatter bursts, lightning arcs, block-echo mining, and durability/vitality mending.
- **Inside-UI & Outside-UI Pixel-Art Animations, Orbital 3D Rendering, Particles & Harmonic Audio**:
  - Added 3 pixel-art UI VFX sprites (`vfx_rune_halo.png`, `vfx_arcane_spark.png`, `vfx_shockwave_ring.png`) and `FusionGuiMicroEffects` (rotating pedestal rune halos, converging slot motes, shockwave rings, physics spark bursts, damped ritual micro-shake, Grimoire mastered seals, and discovery glow chimes — zero `.fill(` calls).
  - Upgraded `EnchantmentFusionTableRenderer` to 4-slot 3D orbital levitation + Ritual Surge vortex (`FusionVisualEventPayload`) and upgraded `EnchantmentFusionTableBlock#animateTick` with state-reactive orbital/aberrant particles and amethyst harmonic audio.
  - Expanded all **11** locale files to **`781` keys** with 100% key parity.

## [0.4.1] - 2026-10-29

### Audit Remediation — Transaction Integrity, Clean Non-Native Isolation, 150 Directional Signatures, Honest Tiered Risk & Unified Terminology

- **Transaction, Vanilla-API & Mixin Hardening (`EF-001/002/004/009/010/012`, `API-01..06`, `MIX-01..05`, `CPL-01..06`)**:
  - Fixed atomic multi-enchant same-book `[A + B]` synthesis so picking two enchantments from the same multi-enchant book subtracts both cleanly in one pass without duplication or residue (`EF-001`).
  - Verified actual post-use item consumption before rolling throwable `经久省用` refunds, eliminating Ender Pearl / Wind Charge cooldown and non-gliding Firework click duplication (`EF-002`).
  - Isolated Cross-Domain Transmutations and non-native fused primaries inside `enchantment_fusion:transmuted_enchants` (with automatic glint sync and legacy tooltip fallback) so non-native enchantments never leak into vanilla slot-based `DataComponents.ENCHANTMENTS` (`API-01`), and added `Enchantment.areCompatible` host mutual-exclusivity checks (`EXCLUSIVE_ON_HOST`, `API-02`).
  - Moved `自动熔炼` and `炭火熔伐` to `PlayerBlockBreakEvents.AFTER` entity transmutation (respecting spawn/Adventure protection, vanilla Fortune, stats, and exhaustion) and gated `清巢` with `mayBuild()` / `blockActionRestricted` (`API-03`).
  - Added `DamageTypeTags.BYPASSES_INVULNERABILITY` guards across all damage-immunity and death-prevention hooks so `/kill` and void damage are never blocked (`API-04`).
  - Fixed `PlayerMiningFusionMixin` underwater airborne mining (`speed <= 0.0F` guard + 6-slot `boundless_mining` scan with `FluidTags.WATER`, `API-05` / `MIX-05`), stripped orphaned `FUSED_PAIRS` and `TRANSMUTED_ENCHANTS` on vanilla Grindstone disenchanting (`MIX-01`), enabled dual `ON_PROJECTILE_IMPACT` + `ON_TRIDENT_CHANNEL` triggers on thrown Tridents plus Riptide spin-attack dispatch (`MIX-02`), separated Fishing Bobber impact from reel-in triggers (`MIX-03`), persisted projectile weapon snapshots across chunk reloads without mainhand fallback (`EF-009/010`), gated melee procs on `>= 55%` attack charge (`CPL-01`), evicted per-player caches on disconnect (`CPL-02`), throttled/gated entity scans (`CPL-03`), supported both `chiseled-enchanting-table` and `chiseled_enchanting_table` IDs in `CetSoftBridge` (`CPL-04`), ensured Flip/Split never burns Resonance Stabilizers in the Catalyst slot (`CPL-05`), and retained unloaded-chunk `Frosted Ice` cages until loaded + server shutdown flush (`EF-012`).
- **150 Unique Directional Signatures, Non-Stacking Balance & Honest Tiered Economy (`BAL-01..09`, `EF-003/005/011`)**:
  - Replaced all 36 `regAlias` symmetric shortcuts with 36 bespoke directional reverse Signature Resonances (`150` unique ordered Signature Resonances total — `[A ▷ B] ≠ [B ▷ A]` across the board, `BAL-06`).
  - Enforced non-stacking highest-level deduplication for identical Signatures across all 6 equipment slots (`BAL-01`), added a 5-second cooldown and real breakable `hurtAndBreak` durability loss to `招财金身` (`BAL-02`), scaled `炭火熔伐` by level (`60% / 100%`) with **Sneak** to pause smelting (`BAL-09`), clamped level top-ups against already-maxed targets (`BAL-08`), and scaled Cross-Domain Transmutation pricing by enchantment `anvilCost` (`BAL-07`).
  - Rebalanced datapack-driven `economy.json`: Low-tier synthesis has **`0%` failure**; Mid-tier failure **never destroys any book or host gear** (charging only half XP/reagent fees); High/Repulsive clashes consume only the needed count of `共鸣稳定晶` (`computeNeededStabilizers`, up to `3`) with an honest **`8%` minimum instability floor** on max-level repulsive clashes (`EF-003`, `BAL-03/04/05`).
- **Data Validation, Delta Networking, GUI Polish & 11-Locale Terminology Unification (`DATA-01..05`, `NET-01..05`, `GUI-01..05`, `DEAD-01..04`, `TX-01..09`)**:
  - Clamped `FusedPair` / `FusedEnchantmentList` codecs and constructors, added `replace: true` and empty `exclusive_groups` datapack support (`channeling × riptide` included), switched Compendium persistence to dirty-batched atomic temp-file writes, and synced datapack profiles/economy + incremental discoveries via `MatrixSyncPayload` and `CompendiumDeltaPayload`.
  - Hardened `StationActionPayload` with stable wire IDs (`10..17`), per-player rate limiting, and `(sourceSlot, enchantId)` selection tokens; backed client `EnchantmentFusionMenu` with `SimpleContainer(4)`.
  - Added always-visible Tab 0 Flip/Split toolbar with disabled-reason tooltips, explicit Tab 1 `[✦ Single Only / 仅单附魔]` toggle (`+ Shift+Click` shortcut), full cursor/clipboard editing in Grimoire search, shared `HitBox` widgets, and per-frame preview/entry caching.
  - Purged 22 dead lang keys (`DEAD-04`) and unified terminology (`纂刻附魔台`, `器魂槽` / `Vessel Slot`, `共鸣稳定晶`, `签名共鸣` / `Signature Resonance`, `畸变词条` / `Aberrant Fusion`, `异位化形` / `Cross-Domain Transmutation`, `未知 → 初识 → 通晓` / `Unknown → Glimpsed → Mastered`) across all 11 locales (`722` keys each) and documentation.

## [0.4.0] - 2026-10-29

### Phase 4 — 26.1.2 Lunge & Spears, Standalone Fusion Grimoire, 3-Stage Discovery, Tiered Risk & Full Runtime Effect Fixes

- Added `minecraft:lunge` (`突进`, `I..III`), `#minecraft:spears` + Copper gear (`golden_spear = 2◇`), standalone & embedded `《融合秘典》 (Fusion Grimoire)` (`360×224`), 2-tab `附魔融合台`, 3-stage discovery (`未知 -> 初识 -> 通晓`) + `《融合残页》`, Frost Walker `Blocks.FROSTED_ICE` cages, and tiered-risk reagents.

## [0.3.1] - 2026-10-29

### Hotfix — Server Tick DoT Crash Fix & Re-Entrancy Hardening

- Fixed Java 25 `ConcurrentHashMap.entrySet().removeIf` `UnsupportedOperationException` in `SignaturePairs.tickWorldDots` and added `ThreadLocal` re-entrancy guards.

## [0.3.0] - 2026-10-29

### Phase 3 — Cross-Domain Transmutations ("异位化形")

- Added `0◇` Fusion-Table-exclusive single cross-domain transmutations across non-native equipment and special relics (`Totem of Undying`, `Glass Bottle`, consumables) with Shift-expandable transmuted tooltips.

## [0.2.0] - 2026-10-28

### Phase 2 — Workstation, Universal Host Inscription, Signature Resonances & Aberrant Fusions

- Added `16×12×16` 3D Enchantment Fusion Table, universal host inscription, directional Signature Resonances, and 6 Aberrant Fusions cleansable exclusively via `纂刻附魔台` (*Chiseled Enchanting Table*).

## [0.1.0] - 2026-10-28

### Phase 1 — Core Skeleton, Enchantment Fusion Table & 8×14 Adaptive Matrix

- Initial release of Vessel Slots (`◇ / ◇◇`), `8 × 14` universal cross-domain matrix, soft synergy bridge with `chiseled-enchanting-table`, and 11-locale localization.
