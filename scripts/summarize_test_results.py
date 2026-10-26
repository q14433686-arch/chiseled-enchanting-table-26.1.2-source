#!/usr/bin/env python3
"""Compact Gradle JUnit evidence for the compile log (works even when a test fails)."""
from pathlib import Path
import xml.etree.ElementTree as ET

suites = [ET.parse(path).getroot() for path in sorted(Path('build/test-results/test').glob('TEST-*.xml'))]
if not suites:
    print('JUnit XML: not generated (compilation failed or tests did not run)')
else:
    totals = {key: sum(int(suite.get(key, 0)) for suite in suites)
              for key in ('tests', 'failures', 'errors', 'skipped')}
    print('JUnit XML totals: ' + ', '.join(f'{key}={value}' for key, value in totals.items()))
    for suite in suites:
        print(f"  {suite.get('name')}: tests={suite.get('tests')}, failures={suite.get('failures')}, errors={suite.get('errors')}")
        for case in suite.findall('testcase'):
            for failure in list(case.findall('failure')) + list(case.findall('error')):
                print(f"FAILURE {case.get('classname')}.{case.get('name')}: {failure.get('message', '')}")
                print((failure.text or '')[:6000])
