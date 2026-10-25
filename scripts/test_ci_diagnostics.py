import tempfile
import unittest
from pathlib import Path
from post_ci_diagnostics import output


class DiagnosticsTests(unittest.TestCase):
    def test_utf8_bounded_check_contains_sha_and_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / 'test.log'
            log.write_text('error: actual compiler error\nJUnit XML totals: failures=1\n' + '中' * 100000)
            result = output('compile', 'test-sha', 'failure', [log])
            self.assertEqual('test-sha', result['head_sha'])
            self.assertLessEqual(len(result['output']['summary'].encode()), 58000)
            self.assertIn('actual compiler error', result['output']['summary'])
    def test_missing_log_does_not_masquerade_as_success(self):
        result = output('compile', 'sha', 'failure', [Path('does-not-exist.log')])
        self.assertEqual('failure', result['conclusion'])


if __name__ == '__main__':
    unittest.main()
