#!/usr/bin/env python3
"""Freeze coordinator growth and enforce deterministic editor / loader boundaries.

These budgets are ratchets, not a claim that old rendering coordinators are now small.
Raise a budget only with an explicit responsibility review; prefer lowering it on extraction.
"""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
base = 'src/main/java/chiseled_enchanting_table/'
client = 'src/client/java/chiseled_enchanting_table/'
budgets = {
    base + 'data/ChiseledEnchantingTableData.java': 1150,
    base + 'data/DataTableLoader.java': 550,
    base + 'config/ModRuntimeConfig.java': 1420,
    base + 'config/ConfigDraft.java': 600,
    base + 'mechanics/ritual/InscriptionRitualMechanics.java': 1700,
    base + 'mechanics/ritual/RitualWorldAccess.java': 300,
    base + 'menu/ChiseledEnchantingTableScreenHandler.java': 2050,
    base + 'menu/MenuQuickMove.java': 180,
    client + 'gui/ChiseledEnchantingTableScreen.java': 2450,
    client + 'gui/config/ChiseledEnchantingConfigScreen.java': 2180,
    client + 'gui/EnchantementListWidget.java': 1610,
    client + 'gui/config/ConfigPanelPainter.java': 180,
}
errors = []
for name, limit in budgets.items():
    actual = len((root / name).read_text().splitlines())
    if actual > limit:
        errors.append(f'{name}: {actual} lines exceeds ratchet {limit}; extract a responsibility')
for name in ('ConfigSaveSession', 'ConfirmationWindow', 'SearchInputSession', 'SearchTextBuffer'):
    source = (root / base / 'config' / f'{name}.java').read_text()
    for forbidden in ('net.minecraft', 'net.fabricmc', 'java.nio.file', 'System.currentTimeMillis', 'ClientPlayNetworking'):
        if forbidden in source:
            errors.append(f'{name}: deterministic editor domain must not depend on {forbidden}')
source = (root / base / 'data/DataTableLoader.java').read_text()
for forbidden in ('static volatile', 'runtimeFrame()', 'applyRuntimeConfig(', 'setServerPackPolicy('):
    if forbidden in source:
        errors.append(f'DataTableLoader: parsing must not own live state / publish via {forbidden}')
if errors:
    print('\n'.join('ERROR: ' + error for error in errors))
    sys.exit(1)
print('ok: coordinator growth ratchets and deterministic interaction/resource-loading boundaries')
