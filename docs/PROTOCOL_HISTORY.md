# 26.3 compatibility channel history

The release label stays `1.1.0`; it is not sufficient to establish client/server compatibility. Install matching current builds at both ends. `EnchantingProtocol` advertises the current revision and generates query-only legacy probes; old business packet decoders are not registered.

## 26_3_revision_13 — full PR27 forward port (2026-10-28)

Ports 26.1.2 PR27 (`abb2af10828429f07a944269ec1319a85bbe496f`, source revision22) to 26.3.
The release label remains 1.1.0. Apply actions include book extraction and curse purge; ritual actions now
encode an enum rather than a boolean; tome actions carry additional targets and ritual snapshots include
active tome slots. A new clientbound table visual-event payload drives bounded custom particles.
Both client and server must update together. All revision1–12 cost probes and revision9–12 apply probes
remain query-only; no 26.1.2 business channel or decoder is registered on the 26.3 line.

## 26_3_revision_12 — rainbow cloth block-state schema (2026-10-26)

The cosmetic adds a `rainbow_cloth` boolean to the registered table block state. Business payload fields do not change, but an old client lacks the state, variant and renderer. Advance the capability channel and version-suffixed business channels rather than silently mixing these builds. Revision 11 is automatically retained among legacy probes.

Old saved tables default to `rainbow_cloth=false`. Rainbow dropped items carry `rainbowCloth` in existing `minecraft:custom_data`; the same component also carries frozen-cycle metadata without overwriting it. No custom data-component registration, per-frame colour packet or new Mixin is introduced.

## Previous revision 11

Previous source synchronization, runtime/data hardening and UI catalogue builds used revision 11. See [synchronization audit](reports/SYNC_9F0E13B_SEVEN_PATH_AUDIT_2026_10_26.md) and [catalogue report](reports/TOME_CATALOGUE_2026_10_26.md) for historical verified sources. Their successful results remain historical, not validation of revision 12.

## Legacy probes

All prior numbered cost channels, and numbered apply channels starting at revision 9, remain query-only entries generated from the current revision number. Bare legacy cost/apply identifiers are also retained as probes. Do not retire a probe or add a legacy decoder simply because the visible release label matches.
