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

Tier C - lang key parity (all shipped languages)
    `en_us.json` is the reference; **every** other `lang/*.json` must declare exactly
    the same key set. The 26.3 branch only compared `en_us` against `zh_cn` because it
    shipped two languages at the time it wrote this gate; this branch syncs the same gate
    together with the nine extra languages from that branch, so it checks all of them.
    Vanilla falls back per key to `en_us`, so a missing key is not fatal - but it is
    invisible in game, which is exactly why it belongs in a cheap static gate.

Tier D - dangling relative markdown links (WARN only, 26.1.2-line addition)
    Every `[label](relative/path)` in a committed `*.md` must resolve to a file that
    exists. Docs in this repository reference each other heavily (baseline -> reports ->
    extending), and a snapshot-style branch can lose a file without anything noticing:
    `docs/COMPATIBILITY.md` and the P19 cross-mod report are referenced from two documents
    but are not in the tree on any branch. Reported as warnings, not failures, because a
    missing *document* is never a build defect - but it should not stay invisible either.

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
warnings: list[str] = []


def fail(msg: str) -> None:
    failures.append(msg)


def note(msg: str) -> None:
    notes.append(msg)


def warn(msg: str) -> None:
    """Advisory finding: printed, never counted as a failure (Tier D uses this)."""
    warnings.append(msg)


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

    # Orphan gate (audit H-05): a mixin class present in the tree but missing from every
    # config silently never loads (structure bookshelf replacement was dead for a whole
    # release this way). Every *Mixin/accessor source under a mixin package must be
    # declared in some *.mixins.json.
    declared_fqcn = set()
    mixin_packages = set()
    for cfg in configs:
        try:
            data = read_json(cfg)
        except Exception:  # noqa: BLE001
            continue
        package = data.get("package", "")
        if not package:
            continue
        mixin_packages.add(package.replace(".", "/"))
        for section in MIXIN_SECTIONS:
            for mixin in data.get(section) or []:
                declared_fqcn.add(f"{package}.{mixin}")

    orphans = []
    for root, _dirs, files in os.walk(os.path.join(REPO, "src")):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(root, name)
            rel = os.path.relpath(path, REPO).replace(os.sep, "/")
            for pkg_path in mixin_packages:
                marker = f"/{pkg_path}/"
                if marker in f"/{rel}":
                    fqcn = (pkg_path + "/" + f"/{rel}".split(marker, 1)[1])[:-5].replace("/", ".")
                    if fqcn not in declared_fqcn:
                        orphans.append(f"{rel} ({fqcn})")
                    break
    for orphan in sorted(orphans):
        fail(f"orphan mixin source not declared in any *.mixins.json: {orphan}")
    if not orphans:
        note("no orphan mixin sources")


# --------------------------------------------------------------------------- #
# Tier C
# --------------------------------------------------------------------------- #

def check_lang_parity() -> None:
    en_path = os.path.join(LANG_DIR, "en_us.json")
    if not os.path.exists(en_path):
        fail(f"missing reference lang file: {os.path.relpath(en_path, REPO)}")
        return
    reference = read_json(en_path)
    reference_keys = set(reference)

    others = sorted(
        os.path.basename(path)
        for path in glob.glob(os.path.join(LANG_DIR, "*.json"))
        if os.path.basename(path) != "en_us.json"
    )
    if not others:
        note(f"lang parity ok (only en_us.json ships, {len(reference_keys)} keys)")
        return

    for name in others:
        path = os.path.join(LANG_DIR, name)
        try:
            data = read_json(path)
        except Exception as exc:  # noqa: BLE001
            fail(f"{name}: invalid JSON: {exc}")
            continue
        keys = set(data)
        for key in sorted(reference_keys - keys):
            fail(f"lang key '{key}' is in en_us.json but missing from {name}")
        for key in sorted(keys - reference_keys):
            fail(f"lang key '{key}' is in {name} but not in en_us.json (stale or typo'd key)")
        # A translated value that is byte-identical to English is allowed (some strings really are
        # the same), so only an *empty* value is treated as an unfinished translation.
        for key in sorted(keys & reference_keys):
            if isinstance(data[key], str) and not data[key].strip():
                fail(f"{name}: lang key '{key}' is empty")

    note(f"lang parity ok ({len(reference_keys)} keys across en_us + {len(others)} language(s))")

    # Also enforce 100% code/data -> lang key coverage across every .java and .json file under src/
    referenced_keys: set[str] = set()
    dyn_re = re.compile(
        r'"(gui\.chiseled_enchanting_table\.[A-Za-z0-9_.]+)"\s*\+\s*[A-Za-z0-9_.()]+\s*\+\s*"(\.[A-Za-z0-9_.]+)"'
    )
    lit_re = re.compile(r'"([a-z0-9_]+\.chiseled_enchanting_table(?:\.[A-Za-z0-9_]+)+)"')
    for root, _, files in os.walk(os.path.join(REPO, "src")):
        if os.sep + "lang" in root:
            continue
        for fname in files:
            if not fname.endswith((".java", ".json")):
                continue
            fpath = os.path.join(root, fname)
            try:
                src_text = open(fpath, encoding="utf-8").read()
            except OSError:
                continue
            for m in dyn_re.finditer(src_text):
                for ch in range(1, 9):
                    referenced_keys.add(f"{m.group(1)}{ch}{m.group(2)}")
            for m in lit_re.finditer(src_text):
                k = m.group(1)
                if not k.endswith(".ch"):
                    referenced_keys.add(k)

    missing_from_lang = sorted(referenced_keys - reference_keys)
    for key in missing_from_lang:
        fail(f"code/data references translation key '{key}' which is MISSING from lang/*.json!")
    for lname in ["en_us.json"] + others:
        lmap = read_json(os.path.join(LANG_DIR, lname))
        for key, val in lmap.items():
            if isinstance(val, str) and (val.strip() == key or "gui.chiseled_enchanting_table." in val):
                fail(f"{lname}: key '{key}' contains raw untranslated key text '{val}'")
            if isinstance(val, str) and key in reference and isinstance(reference[key], str):
                ref_slots = len(re.findall(r"(?<!%)%s", reference[key]))
                cur_slots = len(re.findall(r"(?<!%)%s", val))
                if cur_slots != ref_slots:
                    fail(
                        f"{lname}: key '{key}' has {cur_slots} '%s' placeholder(s), "
                        f"expected {ref_slots} matching en_us.json"
                    )
    note(f"code-to-lang coverage ok ({len(referenced_keys)} referenced keys verified in all {1 + len(others)} locales)")


# --------------------------------------------------------------------------- #
# Tier E - Single source of truth for bundled JSON defaults (N1)
# --------------------------------------------------------------------------- #

def check_no_embedded_json_fallbacks() -> None:
    data_java = os.path.join(
        MAIN_JAVA, "chiseled_enchanting_table", "data", "ChiseledEnchantingTableData.java"
    )
    if not os.path.exists(data_java):
        fail(f"missing {os.path.relpath(data_java, REPO)}")
        return
    src = open(data_java, encoding="utf-8").read()
    if '"""' in src:
        fail(
            "ChiseledEnchantingTableData.java contains embedded multi-line JSON text blocks "
            "(violates N1 single-source-of-truth; load bundled resources via readBundledJsonObject instead)"
        )
    else:
        note("single-source-of-truth ok (zero embedded JSON text blocks in ChiseledEnchantingTableData.java)")


# --------------------------------------------------------------------------- #
# Tier D (warn-only)
# --------------------------------------------------------------------------- #

LINK_RE = re.compile(r"\[([^\]]*)\]\(([^)\s]+)\)")


def check_markdown_links() -> None:
    """Relative markdown links must resolve. Warn-only: see the module docstring (Tier D)."""
    dangling = 0
    scanned = 0
    for root, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in {".git", "build", ".gradle", "run"}]
        for name in files:
            if not name.endswith(".md"):
                continue
            path = os.path.join(root, name)
            scanned += 1
            rel_path = os.path.relpath(path, REPO)
            try:
                with open(path, encoding="utf-8") as fh:
                    text = fh.read()
            except OSError:
                continue
            for target in (m.group(2) for m in LINK_RE.finditer(text)):
                clean = target.split("#")[0]
                if not clean or clean.startswith(("http://", "https://", "mailto:")):
                    continue
                resolved = os.path.normpath(os.path.join(os.path.dirname(path), clean))
                if not os.path.exists(resolved):
                    dangling += 1
                    warn(f"{rel_path}: dangling link '{target}' -> {os.path.relpath(resolved, REPO)} does not exist")
    if dangling == 0:
        note(f"markdown links ok ({scanned} file(s) scanned)")


def check_no_tautological_residue() -> None:
    """Generated-merge residue sweep: tautological conditions and same-value ternaries
    (e.g. `0.0 >= 0.0 && x ? 0 : 0`) compile fine but prove a line was never re-read.
    New audit P0: keep the tree free of them mechanically."""
    import re as _re

    patterns = [
        _re.compile(r"0\.0\s*(?:>=|<=|==)\s*0\.0"),
        _re.compile(r"\bif\s*\(\s*(?:true|false)\s*\)"),
        _re.compile(r"\?\s*([A-Za-z0-9_.]+)\s*:\s*\1(?![A-Za-z0-9_.])"),
    ]
    offenders = []
    for root, _dirs, files in os.walk(os.path.join(REPO, "src")):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(root, name)
            with open(path, encoding="utf-8") as fh:
                for lineno, line in enumerate(fh, 1):
                    stripped = line.strip()
                    if stripped.startswith("//") or stripped.startswith("*"):
                        continue
                    if any(p.search(line) for p in patterns):
                        offenders.append(f"{os.path.relpath(path, REPO)}:{lineno}")
    for offender in offenders:
        fail(f"tautological/generated residue: {offender}")
    if not offenders:
        note("no tautological generated residue")


def check_locale_safe_formatting() -> None:
    """DATA-03/DATA-04: String.format must pin Locale.ROOT (server default locales
    otherwise leak localized digits/decimals into lore, logs and config values)."""
    import re as _re

    pattern = _re.compile(r"String\.format\((?!\s*(?:java\.util\.)?Locale\b)")
    offenders = []
    for root, _dirs, files in os.walk(os.path.join(REPO, "src")):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(root, name)
            with open(path, encoding="utf-8") as fh:
                for lineno, line in enumerate(fh, 1):
                    if pattern.search(line):
                        offenders.append(f"{os.path.relpath(path, REPO)}:{lineno}")
    for offender in offenders:
        fail(f"String.format without Locale.ROOT: {offender}")
    if not offenders:
        note("all String.format calls pin Locale.ROOT")


# --------------------------------------------------------------------------- #

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--porcelain", action="store_true", help="machine readable output")
    args = parser.parse_args()

    check_mixin_configs()
    check_lang_parity()
    check_no_embedded_json_fallbacks()
    check_markdown_links()
    check_locale_safe_formatting()
    check_no_tautological_residue()

    if args.porcelain:
        for msg in failures:
            print(f"FAIL\t{msg}")
        for msg in warnings:
            print(f"WARN\t{msg}")
    else:
        for msg in notes:
            print(f"ok:   {msg}")
        for msg in warnings:
            print(f"warn: {msg}")
        for msg in failures:
            print(f"FAIL: {msg}")
        if failures:
            print(f"\nci_static_checks: FAILED ({len(failures)} failure(s), {len(warnings)} warning(s))")
        else:
            print(f"\nci_static_checks: PASS ({len(notes)} check group(s), {len(warnings)} warning(s))")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
