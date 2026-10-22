# Cross-mod compatibility notes

This page describes the compatibility model of the unofficial Minecraft 26.3 port. The source
branch contains a reference to a P19 investigation report, but that report is not present in this
repository snapshot; this page records only behavior that can be confirmed from the current code.

## Enchantment mods and datapacks

- Default enchantment discovery is data-driven through `default_enchantments.json`. Tag rules are
  expanded against the active enchantment registry, so enchantments contributed through the
  configured vanilla tags can be discovered without hard-coding their ids.
- Enchantment/item compatibility is evaluated through Minecraft's enchantment predicates rather
  than a static list of vanilla items. This is intended to include compatible modded items and
  enchantments; datapack tag conventions still determine whether an enchantment is initially
  available.
- Treasure enchantments can be explicitly gated in `treasure_epiphany.json`. Keep any custom
  treasure enchantments out of default unlock rules when a datapack expects them to require a tome
  or an epiphany.

## Recipe viewers

JEI and REI integrations are optional client-only adapters. Neither is required on a dedicated
server or by players who do not use a viewer. Both provide enchanting and item-transformation
queries, including reverse item-use lookups. Viewer coordinates are Minecraft-version-specific and
must be changed deliberately with the corresponding adapter build verified.

## Anvil and other gameplay mods

The active anvil mixin narrowly blocks the same-level enchanted-book merge used for book upgrades
while preserving other anvil combinations. Broader anvil XP/prior-work-cost changes remain parked.
Mods that also rewrite these anvil paths may require an in-game compatibility check. The broader
legacy integration and enchanted-book component accessors are intentionally not enabled by default.

## Reporting an incompatibility

Include the Minecraft/Fabric versions, mod list, relevant datapack, and the first exception / mixin
warning from `latest.log`. For datapack-related issues, run `python3 scripts/compat_check.py`; for
viewer issues, reproduce once with only the affected viewer enabled.
