"""UI intent contracts for the user's terminology, lock and progressive-disclosure decisions."""
import json
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / 'src/client/java/chiseled_enchanting_table/gui'
ASSETS = ROOT / 'src/client/resources/assets/chiseled_enchanting_table'


class UiReadabilityTests(unittest.TestCase):
    def test_chinese_uses_enchantment_not_parallel_jargon(self):
        text = (ASSETS / 'lang/zh_cn.json').read_text()
        for token in ('魔咒', '木书架', '嬗变', '温养', '⊘', '普通普通', '模板书书', '移除附魔洗练'):
            self.assertNotIn(token, text)

    def test_full_bookshelf_labels_replace_ambiguous_abbreviations(self):
        lang = json.loads((ASSETS / 'lang/zh_cn.json').read_text())
        text = lang['gui.chiseled_enchanting_table.header.shelf_hud_max']
        self.assertIn('普通书架', text)
        self.assertIn('书架总数', text)
        self.assertNotIn('木 %s', text)

    def test_locked_badge_is_lock_and_level_not_unexplained_parenthesis(self):
        source = (GUI / 'EnchantementListWidget.java').read_text()
        self.assertIn('badgeText = "🔒 " + romanNumeral(curLvl + 1);', source)
        self.assertNotIn('\\u2298', source)
        self.assertNotIn('" (" + nextReq', source)

    def test_all_six_tab_images_share_exact_png_geometry(self):
        for tab in ('inscribe', 'cleanse', 'tome'):
            for state in ('active', 'inactive'):
                data = (ASSETS / f'gui/tab_{tab}_{state}.png').read_bytes()
                self.assertEqual((150, 36), struct.unpack('>II', data[16:24]))

    def test_every_owned_hover_uses_the_shared_presentation_policy(self):
        for path in GUI.rglob('*.java'):
            if path.name != 'TooltipPresentation.java':
                self.assertNotIn('.setTooltipForNextFrame(', path.read_text(), str(path))

    def test_technical_appendix_and_mixed_counter_milestones_are_not_default(self):
        source = (GUI / 'ResonanceTooltip.java').read_text()
        self.assertIn('if (TooltipPresentation.detailsRequested())', source)
        self.assertNotIn('computeNextShelfMilestoneTarget', (GUI / 'ChiseledEnchantingTableScreen.java').read_text())
        self.assertIn('reqShelves - shelves', (GUI / 'TableOptionTooltips.java').read_text())

    def test_lock_exception_does_not_remove_general_glyph_gate(self):
        source = (ROOT / 'scripts/ci_static_checks.py').read_text()
        self.assertIn('ord(ch) > 0xFFFF and ch != "🔒"', source)
        self.assertIn('surrogate_esc.search(text)', source)

    def test_catalogue_actions_are_sprites_not_text_or_unicode_glyphs(self):
        source = (GUI / 'TomeDirectoryControls.java').read_text()
        self.assertIn('CatalogueSkin.icon(', source)
        self.assertNotIn('"↓"', source)
        self.assertNotIn('"↑"', source)
        panel = (GUI / 'TablePanelBackground.java').read_text()
        self.assertIn('CatalogueSkin.icon(ctx,icons[b]', panel)
        self.assertNotIn('ctx.blit(TOME_BUTTONS[b]', panel)

    def test_shared_material_has_explicit_inner_clip_and_corner_preservation(self):
        source = (GUI / 'CatalogueSkin.java').read_text()
        self.assertIn('enableScissor', source)
        self.assertIn('finally { ctx.disableScissor(); }', source)
        self.assertIn('float[] uv={0,.25f,.75f,1}', source)
        self.assertIn('frame_hover', source)
        self.assertIn('frame_disabled', source)

    def test_catalogue_layout_is_shared_by_hit_tests_paint_and_list_geometry(self):
        source = (GUI / 'TomeDirectoryControls.java').read_text()
        for call in ('TomeDirectoryLayout.button(i)', 'TomeDirectoryLayout.popupRow(', 'TomeDirectoryLayout.FRAME'):
            self.assertIn(call, source)
        self.assertNotIn('ctx.fill(bx,by,bx+b[2]', source)
        screen = (GUI / 'ChiseledEnchantingTableScreen.java').read_text()
        self.assertIn('TomeDirectoryLayout.listOffset()', screen)
        self.assertIn('TomeDirectoryLayout.lockButton(index)', screen)

    def test_catalogue_material_and_icon_texture_dimensions(self):
        for name in ('frame', 'frame_hover', 'frame_disabled'):
            data = (ASSETS / f'gui/catalogue_{name}.png').read_bytes()
            self.assertEqual((32, 32), struct.unpack('>II', data[16:24]))
        for path in (ASSETS / 'gui').glob('catalogue_icon_*.png'):
            self.assertEqual((7, 7), struct.unpack('>II', path.read_bytes()[16:24]))
        self.assertEqual(10, len(list((ASSETS / 'gui').glob('catalogue_icon_*.png'))))


if __name__ == '__main__':
    unittest.main()
