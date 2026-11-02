#!/usr/bin/env python3
"""Source-level ordering guards only; this does not execute a game or prove Mixin ordering."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class MeleeImpulseScopeTest(unittest.TestCase):
    def test_success_follows_all_locally_nested_frame_cleanup(self):
        source = (ROOT / 'src/main/java/enchantment_fusion/mixin/PlayerAmberAttackMixin.java').read_text(encoding='utf-8')
        swing = source[source.index('private void ef$glazeSwing('):source.index('@WrapMethod(method = "stabAttack")')]
        stab = source[source.index('private boolean ef$momentumStabFrame('):source.index('@WrapOperation(method = "attack",')]
        for body in (swing, stab):
            with self.subTest(method=body.split('(', 1)[0]):
                self.assertEqual(1, body.count('impulseCompleted = true;'))
                success = body.index('impulseCompleted = true;')
                self.assertLess(body.index('original.call('), success)
                for cleanup in ('MomentumSealSkills.endAttack(', 'WindSweepSkills.endAttack(',
                                'SegmentThreadSkills.endAttack(', 'SoulBladeSkills.endAttack(',
                                'GrapnelImpact.endAttack(', 'MeleeWorkSkills.endAttack(', 'MeleeWearSkills.endAttack('):
                    self.assertLess(body.index(cleanup), success, cleanup)
                self.assertLess(success, body.index('MeleeImpulseSkills.endAttack(impulse, impulseCompleted)'))
                self.assertLess(body.index('MeleeImpulseSkills.beginAttack('), body.index('MeleeWearSkills.beginAttack('))

    def test_shared_completion_callbacks_require_a_successful_unambiguous_attack(self):
        source = (ROOT / 'src/main/java/enchantment_fusion/mechanics/signatures/MeleeImpulseSkills.java').read_text(encoding='utf-8')
        body = source[source.index('public static void endAttack(Attack frame, boolean completed)'):
                      source.index('private static void invalidatePursuit(')]
        self.assertLess(body.index('CURRENT.get() != frame'), body.index('CURRENT.remove()'))
        self.assertLess(body.index('!completed || frame.ambiguous'), body.index('frame.completions'))
        self.assertLess(body.index('CURRENT.set(frame.previous)'), body.index('frame.completions'))
        seed = (ROOT / 'src/main/java/enchantment_fusion/mechanics/signatures/LootingFireSeedSkills.java').read_text(encoding='utf-8')
        for forbidden in ('setBlock(', 'removeBlock(', 'destroyBlock(', 'ItemEntity', 'getEntitiesOfClass(ItemEntity'):
            self.assertNotIn(forbidden, seed)
        self.assertIn('frame.completions.add(', seed)
        self.assertIn('victim.hurtServer(', seed)


if __name__ == '__main__':
    unittest.main()
