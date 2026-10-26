# Network Protocol & Wire Revision History

> **Current wire revision:** `26_1_2_revision_18` (`EnchantingProtocol.WIRE_REVISION`)
> **Owner classes:** `chiseled_enchanting_table.menu.EnchantingProtocol`, `PayloadLimits`,
> `EnchantCostSync`, `EnchantCostTablePayload`, `AvailableEnchantmentsPayload`, `ConfigSyncNetworking`.

## 1. Design Principles

1. **Revision-suffixed channels + explicit capability handshake**: Every S2C/C2S gameplay channel embeds a protocol revision suffix or validates `EnchantingProtocol.WIRE_REVISION` during configuration/play join so mismatched client/server jars disconnect cleanly with a human-readable diagnostic rather than throwing `DecoderException` mid-session.
2. **Centralized `PayloadLimits` bounds**: All collection headers and byte budgets are checked against `chiseled_enchanting_table.menu.PayloadLimits` before allocation:
   - `MAX_SYNC_OPTIONS = 64` (per-menu option/epiphany slice cap)
   - `MAX_SYNC_ENTRIES = 1_024` (cost-table / transformation / upgrade map cap)
   - `MAX_PAYLOAD_WIRE_BYTES = 980_000` (pre-flight `Unpooled.buffer()` wire budget comfortably inside Fabric/vanilla's `1,048,576`-byte custom payload ceiling)
3. **Datapack `/reload` safety**: Because `EnchantCostTablePayload` replaces the active `RuleBook` / `RuntimeFrame`, `ChiseledEnchantingTableData` closes any open `ChiseledEnchantingTableScreenHandler` containers on `END_DATA_PACK_RELOAD` and sends `message.chiseled_enchanting_table.reload_closed` so no player clicks a stale pre-reload option index.
4. **Per-UUID player-salted cost cache**: `EnchantCostSync` caches serialized `EnchantCostTablePayload` frames per `UUID` when `MaterialSelectionMode.SALTED_SINGLE` is active (and shares a single global frame in `MULTI_CHOICE` mode), preventing cross-player cache thrashing on populated servers.

## 2. Revision Ledger (`26.1.2` Line)

| Wire Revision | Summary of Wire Contract Changes |
| :--- | :--- |
| `26_1_2_revision_1` – `revision_5` | Initial 26.1.2 S2C cost synchronization (`EnchantCostTablePayload`) and typed `AvailableOption` menu list sync (`AvailableEnchantmentsPayload`) for direct enchants, book upgrades, and item transformations. |
| `26_1_2_revision_6` | Backport of `RuleBook` / `RuntimeFrame` architecture, ritual queue status sync, and explicit client/server capability probe. |
| `26_1_2_revision_7` – `revision_9` | Added `shelf_gates.json`, `disenchanting.json`, `treasure_epiphany.json`, and `library_resonance.json` rule sync fields for client tooltips and JEI/REI parity. |
| `26_1_2_revision_10` | Added copy-generation fading (`cet_generation`), `scapegoat_pricing`, and `ritual.wait_curve` 6-coefficient synchronization. |
| `26_1_2_revision_11` | Added Tome management page (`OptionType` / lock state sync), page-unlock advancement state, container/session routing guards, and payload integrity tail marker. |
| `26_1_2_revision_12` – `revision_15` | Added server-authoritative `ModRuntimeConfig` sync (`ConfigSyncNetworking`), `pack_policy.json` lock state, LAN publish permission bit (`IntegratedServer.isPublished()`), and 20 orthogonal Classic/Modern rule atoms. |
| `26_1_2_revision_16` | Eliminated `t=0` vs `20-tick` opening sync skew across container `DataSlot` / initial `AvailableEnchantmentsPayload` push. |
| `26_1_2_revision_17` | Added data-driven `DisenchantmentApplication.EssenceValuation` (`essence_valuation`) and `InscriptionRitualMechanics.ApplicabilityConfig` (`ritual.applicability`) to `EnchantCostTablePayload`; enforced centralized `PayloadLimits` (`64` / `1,024` / `980,000` bytes via `Unpooled.buffer()` measurement); switched `EnchantCostSync` to a `UUID`-keyed cache; de-versioned canonical `CostTableDataset` (`modern`/`classic`) and `Preset` (`classic`) wire tokens while retaining read aliases for `classic_1_0_3` / `modern_1_1_0`. |

| **`26_1_2_revision_18`** *(current)* | Added the `rainbow_cloth` block-state property (26.3 rainbow tablecloth port): a client/server-shared block-state schema change, so the capability/cost channels bump even though no menu payload field changed. revision_17 remains a generated legacy probe only. |

## 3. Checklist When Bumping `WIRE_REVISION`

Whenever any `FriendlyByteBuf` encoder/decoder changes:
1. Increment `EnchantingProtocol.WIRE_REVISION` (and keep `PROBE_COMPATIBLE_REVISIONS` strictly for handshake rejection notices, never silent partial decoding).
2. Keep writer and reader field order byte-for-byte symmetrical in `EnchantCostTablePayload`, `AvailableEnchantmentsPayload`, and `ConfigSyncNetworking`.
3. Bound every `readVarInt()` collection length with `PayloadLimits.MAX_SYNC_OPTIONS` or `PayloadLimits.MAX_SYNC_ENTRIES`.
4. Add or update round-trip and cap-enforcement assertions in `src/test/java/chiseled_enchanting_table/NetworkCompatibilityTest.java`.
