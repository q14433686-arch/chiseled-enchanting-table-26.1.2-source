import unittest
from check_architecture_boundaries import check, split_violations


class SplitArchitectureTests(unittest.TestCase):
    def test_actual_repository_satisfies_all_owner_and_growth_rules(self):
        self.assertEqual([], check())

    def test_receipt_compatibility_path_does_not_grant_handler_capability(self):
        self.assertEqual([], split_violations('MenuPaymentService',
            'import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler.PaymentReceipt;'))
        self.assertTrue(split_violations('MenuPaymentService',
            'private final ChiseledEnchantingTableScreenHandler handler;'))

    def test_models_cannot_reach_widget_or_network(self):
        for owner in ('InscribeOptionModel', 'CleanseOptionModel'):
            self.assertTrue(split_violations(owner, 'EnchantementListWidget widget;'))
            self.assertTrue(split_violations(owner, 'ClientPlayNetworking.send(payload);'))

    def test_config_views_cannot_reacquire_screen_or_persistence(self):
        for owner in ('ConfigOptionRenderer', 'ConfigTraceRenderer', 'ConfigViewContext'):
            self.assertTrue(split_violations(owner, 'ChiseledEnchantingConfigScreen screen;'))
            self.assertTrue(split_violations(owner, 'ConfigFileStore.writeAtomically(path, data);'))

    def test_pure_schema_and_queue_owners_cannot_publish_or_touch_world(self):
        for owner in ('RuntimeConfigJson', 'RuntimePolicyJson', 'RuntimeConfigPresets'):
            self.assertTrue(split_violations(owner, 'applyRuntimeConfig(config);'))
        self.assertTrue(split_violations('RitualQueuePersistence', 'ChiseledEnchantingTableBlockEntity entity;'))

    def test_panel_views_cannot_dispatch_input_but_menu_read_context_is_not_screen(self):
        for owner in ('TablePanelBackground', 'TablePanelLabels', 'TablePanelTooltips', 'TableTooltipContext'):
            self.assertTrue(split_violations(owner, 'ChiseledEnchantingTableScreen screen;'))
            self.assertTrue(split_violations(owner, 'sendServerbound(payload);'))
        self.assertEqual([], split_violations('TableTooltipContext', 'ChiseledEnchantingTableScreenHandler menu;'))


if __name__ == '__main__':
    unittest.main()
