#!/usr/bin/env python3
"""Freeze coordinator growth and enforce deterministic editor / loader boundaries.

These budgets are ratchets, not a claim that old rendering coordinators are now small.
Raise a budget only with an explicit responsibility review; prefer lowering it on extraction.
"""
from pathlib import Path
import sys
import re

root = Path(__file__).resolve().parents[1]
base = 'src/main/java/chiseled_enchanting_table/'
client = 'src/client/java/chiseled_enchanting_table/'
budgets = {
    base + 'data/ChiseledEnchantingTableData.java': 1150,
    base + 'data/DataTableLoader.java': 450,
    base + 'config/ModRuntimeConfig.java': 840,
    base + 'config/ConfigDraft.java': 600,
    base + 'config/ConfigFileStore.java': 100,
    base + 'mechanics/ritual/InscriptionRitualMechanics.java': 1600,
    base + 'mechanics/ritual/RitualWorldAccess.java': 300,
    base + 'menu/ChiseledEnchantingTableScreenHandler.java': 1970,
    base + 'menu/MenuQuickMove.java': 180,
    client + 'gui/ChiseledEnchantingTableScreen.java': 920,
    client + 'gui/config/ChiseledEnchantingConfigScreen.java': 1630,
    client + 'gui/EnchantementListWidget.java': 1400,
    client + 'gui/config/ConfigPanelPainter.java': 180,
    client + 'gui/ResonanceTooltip.java': 170,
}
# Budgets apply to new owners too: moving all code into another monolith is not a split.
for directory, limits in (
    (base + 'config/', {'RuntimeConfigJson': 330, 'RuntimePolicyJson': 110, 'RuntimeConfigPresets': 190, 'ConfigTraceActions': 40, 'ConfigSliderDragSession': 45}),
    (base + 'menu/', {'MenuPaymentService': 180, 'MenuPricing': 240, 'MenuPricingAccess': 65, 'MenuMaterialQuotes': 220, 'MenuXpQuotes': 230}),
    (base + 'mechanics/ritual/', {'RitualQueuePersistence': 160}),
    (base + 'research/', {'ResearchCatalog': 130, 'ResearchJournal': 65, 'ResearchEngine': 140, 'ResearchJson': 130, 'ResearchStore': 120, 'ResearchRuntime': 180, 'ResearchWorkspace': 150, 'ResearchKnowledge': 65, 'ResearchOutcomes': 100, 'ResearchSampleAnalysis': 100, 'ResearchGoalView': 130, 'ResearchPrototype': 150}),
    (client + 'gui/', {'InscribeOptionModel': 240, 'CleanseOptionModel': 100, 'EnchantmentDisplayNames': 150,
        'TablePanelBackground': 400, 'TablePanelLabels': 230, 'TableNavigationTooltips': 240,
        'TableCostTooltips': 210, 'TableOptionTooltips': 300, 'TablePanelTooltips': 200,
        'TableTooltipContext': 55, 'TablePanelContext': 20, 'TableListRead': 35, 'TableRowView': 30}),
    (client + 'gui/config/', {'ConfigOptionRenderer': 365, 'ConfigTraceRenderer': 280, 'ConfigViewContext': 35}),
):
    budgets.update({directory + name + '.java': limit for name, limit in limits.items()})


def split_violations(name, source):
    """Testable dependency rules, alongside size ratchets, for the six engineering extractions."""
    errors = []
    rules = []
    if name in ('RuntimeConfigJson', 'RuntimePolicyJson', 'RuntimeConfigPresets'):
        rules += ['java.nio.file', 'ClientPlayNetworking', 'setServerPackPolicy(', 'applyRuntimeConfig(', 'runtimeFrame(']
    if name in ('ResearchCatalog', 'ResearchJournal', 'ResearchEngine', 'ResearchJson', 'ResearchStore', 'ResearchKnowledge', 'ResearchOutcomes', 'ResearchSampleAnalysis', 'ResearchGoalView'):
        rules += ['net.minecraft', 'net.fabricmc', 'ChiseledEnchantingTableScreenHandler', 'ClientPlayNetworking']
    if name in ('ResearchCatalog', 'ResearchJournal', 'ResearchEngine', 'ResearchKnowledge', 'ResearchOutcomes', 'ResearchSampleAnalysis', 'ResearchGoalView'):
        rules += ['java.nio.file', 'System.currentTimeMillis', 'System.nanoTime', 'ItemStack', 'ResearchRuntime']
    if name == 'RitualQueuePersistence':
        rules += ['net.minecraft.world.level', 'ChiseledEnchantingTableBlockEntity', 'ChiseledEnchantingTableScreenHandler', 'System.currentTimeMillis']
    if name in ('MenuPaymentService', 'MenuMaterialQuotes', 'MenuXpQuotes', 'MenuPricingAccess'):
        # Receipt retains its existing public type path; no handler instance enters these owners.
        code = source.replace('import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler.PaymentReceipt;', '')
        if re.search(r'\bChiseledEnchantingTableScreenHandler\b', code):
            errors.append(name + ': economic owner must consume narrow capabilities, not Handler')
        rules += ['ClientPlayNetworking', 'ServerPlayNetworking', 'containerId', 'ContainerInput']
    if name in ('InscribeOptionModel', 'CleanseOptionModel'):
        rules += ['EnchantementListWidget', 'ChiseledEnchantingTableScreenHandler', 'GuiGraphicsExtractor', 'addEntry(', 'setScrollAmount(', 'ClientPlayNetworking']
    if name in ('ConfigOptionRenderer', 'ConfigTraceRenderer', 'ConfigViewContext'):
        rules += ['ChiseledEnchantingConfigScreen', 'ClientPlayNetworking', 'ModConfigNetworking', 'ConfigFileStore', 'Minecraft.getInstance(']
    if name.startswith('Table') and name not in ('TableSkin',):
        rules += ['ChiseledEnchantingTableScreen', 'ClientPlayNetworking', 'sendServerbound', 'Minecraft.getInstance(']
        rules += ['EnchantementListWidget']
    for forbidden in rules:
        present = bool(re.search(r'\b' + re.escape(forbidden) + r'\b', source)) if forbidden[-1].isalnum() else forbidden in source
        if present:
            errors.append(name + ': split owner must not depend on ' + forbidden)
    return errors


def check():
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
    for name in budgets:
        errors.extend(split_violations(Path(name).stem, (root / name).read_text()))
    return errors


if __name__ == '__main__':
    errors = check()
    if errors:
        print('\n'.join('ERROR: ' + error for error in errors))
        sys.exit(1)
    print('ok: coordinator/helper growth ratchets and schema/payment/persistence/view capability boundaries')
