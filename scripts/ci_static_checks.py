#!/usr/bin/env python3
"""Cheap static gates that run before Gradle ever starts.

Ported from the CI triad of the sister repository `TaCZ_Refabricated_Unofficial`
(`.github/workflows/build.yml` inline checks), adapted to this project's layout:

Tier A - mixin config integrity
    Every class listed in a `*.mixins.json` must exist as a `.java` file. A typo
    here is not caught by `compileJava` (the class is never referenced from Java),
    it only blows up at runtime when Mixin tries to load the config.

Tier B - registered mixins must actually be compiled
    `build.gradle` parks part of the old upstream tree with `sourceSets` excludes.
    If a mixin that `fabric.mod.json` registers is also excluded from compilation,
    the jar ships a mixin config pointing at a class that is not in the jar:
    the game crashes on startup with `ClassNotFoundException`. This gate makes that
    combination impossible to merge.

Tier C - lang key parity
    `en_us.json` and `zh_cn.json` must declare the same key set, otherwise one
    locale silently shows raw translation keys.

Usage:
    python3 scripts/ci_static_checks.py            # human readable, exit 1 on failure
    python3 scripts/ci_static_checks.py --porcelain  # FAIL\\t<message> lines only
"""

from __future__ import annotations

import argparse
import fnmatch
import glob
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN_JAVA = os.path.join(REPO, "src", "main", "java")
CLIENT_JAVA = os.path.join(REPO, "src", "client", "java")
FABRIC_MOD_JSON = os.path.join(REPO, "src", "main", "resources", "fabric.mod.json")
BUILD_GRADLE = os.path.join(REPO, "build.gradle")
LANG_DIR = os.path.join(
    REPO, "src", "client", "resources", "assets", "chiseled_enchanting_table", "lang"
)

MIXIN_SECTIONS = ("mixins", "client", "server")

failures: list[str] = []
notes: list[str] = []


def fail(msg: str) -> None:
    failures.append(msg)


def note(msg: str) -> None:
    notes.append(msg)


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #

def read_json(path: str):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def mixin_config_files() -> list[str]:
    found = sorted(glob.glob(os.path.join(REPO, "src", "*", "resources", "*mixins*.json")))
    return found


def registered_mixin_configs() -> list[str]:
    """Mixin configs actually loaded at runtime (fabric.mod.json `mixins`)."""
    try:
        data = read_json(FABRIC_MOD_JSON)
    except Exception as exc:  # noqa: BLE001
        fail(f"cannot read fabric.mod.json: {exc}")
        return []
    return [str(entry) for entry in data.get("mixins") or []]


def gradle_main_excludes() -> list[str]:
    """`exclude '...'` patterns declared inside the `main` sourceSet block."""
    try:
        with open(BUILD_GRADLE, encoding="utf-8") as fh:
            text = fh.read()
    except OSError as exc:
        fail(f"cannot read build.gradle: {exc}")
        return []
    match = re.search(r"sourceSets\s*\{(.*?)\n\}", text, re.S)
    block = match.group(1) if match else text
    # Only the `main { java { ... } }` part; client excludes must not hide main mixins.
    main_match = re.search(r"main\s*\{(.*?)\n    \}", block, re.S)
    scope = main_match.group(1) if main_match else block
    return re.findall(r"^\s*exclude\s+'([^']+)'", scope, re.M)


def is_excluded(rel_path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(rel_path, pat) for pat in patterns)


def resolve_mixin_source(package: str, mixin: str) -> tuple[str | None, str]:
    """Return (abs path, repo-relative display path) for a mixin class, or (None, rel)."""
    rel = os.path.join(package.replace(".", os.sep), *mixin.split(".")) + ".java"
    for root in (MAIN_JAVA, CLIENT_JAVA):
        candidate = os.path.join(root, *rel.split(os.sep))
        if os.path.exists(candidate):
            return candidate, rel
    return None, rel


# --------------------------------------------------------------------------- #
# Tier A + B
# --------------------------------------------------------------------------- #

def check_mixin_configs() -> None:
    configs = mixin_config_files()
    if not configs:
        fail("no *.mixins.json found under src/*/resources")
        return

    registered = {os.path.basename(cfg) for cfg in registered_mixin_configs()}
    excludes = gradle_main_excludes()
    declared = 0

    for cfg in configs:
        rel_cfg = os.path.relpath(cfg, REPO)
        try:
            data = read_json(cfg)
        except Exception as exc:  # noqa: BLE001
            fail(f"{rel_cfg}: invalid JSON: {exc}")
            continue

        package = data.get("package", "")
        if not package:
            fail(f"{rel_cfg}: missing 'package'")
            continue

        is_registered = os.path.basename(cfg) in registered
        for section in MIXIN_SECTIONS:
            for mixin in data.get(section) or []:
                declared += 1
                abs_path, rel = resolve_mixin_source(package, mixin)
                if abs_path is None:
                    fail(f"{rel_cfg}: [{section}] {mixin} -> src/**/{rel} does not exist")
                    continue
                if is_registered and abs_path.startswith(MAIN_JAVA):
                    if is_excluded(rel, excludes):
                        fail(
                            f"{rel_cfg}: [{section}] {mixin} is registered in fabric.mod.json "
                            f"but excluded from compilation by build.gradle ('{rel}'): "
                            f"the shipped jar would fail at startup with ClassNotFoundException"
                        )

        if not is_registered and any(data.get(s) for s in MIXIN_SECTIONS):
            note(
                f"{rel_cfg} declares mixins but is NOT registered in fabric.mod.json "
                f"(parked config; it never loads)"
            )

    if declared == 0:
        note("mixin configs declare no mixin classes")
    else:
        note(f"{declared} declared mixin class reference(s) checked")


# --------------------------------------------------------------------------- #
# Tier C
# --------------------------------------------------------------------------- #

def check_lang_parity() -> None:
    en_path = os.path.join(LANG_DIR, "en_us.json")
    zh_path = os.path.join(LANG_DIR, "zh_cn.json")
    for path in (en_path, zh_path):
        if not os.path.exists(path):
            fail(f"missing lang file: {os.path.relpath(path, REPO)}")
            return
    en = read_json(en_path)
    zh = read_json(zh_path)
    only_en = sorted(set(en) - set(zh))
    only_zh = sorted(set(zh) - set(en))
    for key in only_en:
        fail(f"lang key '{key}' only in en_us.json")
    for key in only_zh:
        fail(f"lang key '{key}' only in zh_cn.json")
    if not only_en and not only_zh:
        note(f"lang parity ok ({len(en)} keys in en_us/zh_cn)")


# --------------------------------------------------------------------------- #

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--porcelain", action="store_true", help="machine readable output")
    args = parser.parse_args()

    check_mixin_configs()
    check_lang_parity()

    if args.porcelain:
        for msg in failures:
            print(f"FAIL\t{msg}")
    else:
        for msg in notes:
            print(f"ok:   {msg}")
        for msg in failures:
            print(f"FAIL: {msg}")
        if failures:
            print(f"\nci_static_checks: FAILED ({len(failures)} failure(s))")
        else:
            print(f"\nci_static_checks: PASS ({len(notes)} check group(s))")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
