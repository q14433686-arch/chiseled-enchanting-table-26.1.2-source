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
        for key in sorted(keys):
            if isinstance(data[key], str) and "\\n" in data[key]:
                fail(f"{name}: lang key '{key}' contains a literal backslash-n (use a real line break)")
    for key in sorted(reference_keys):
        if isinstance(reference[key], str) and "\\n" in reference[key]:
            fail(f"en_us.json: lang key '{key}' contains a literal backslash-n (use a real line break)")

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

    # Verify dynamic enum/preset translation keys constructed in ConfigCatalog / Preset / ConfigScreen
    for ds_token in ("modern", "classic", "modern_1_1_0", "classic_1_0_3"):
        ek = f"config.chiseled_enchanting_table.enum.cost_table.{ds_token}"
        if ek not in reference_keys:
            fail(f"dynamic enum translation key '{ek}' is missing from en_us.json")
    for preset_id in ("classic", "classic_1_0_3", "casual", "standard", "expert"):
        suffixes = ("short", "title", "desc", "inert_note", "applied_notice") if preset_id.startswith("classic") else ("short", "title", "desc")
        for suffix in suffixes:
            pk = f"config.chiseled_enchanting_table.preset.{preset_id}.{suffix}"
            if pk not in reference_keys:
                fail(f"dynamic preset translation key '{pk}' is missing from en_us.json")

    mc_fmt_re = re.compile(r"%(?:(\d+)\$)?([A-Za-z%])")

    def format_arg_slots(text: str) -> set[int]:
        slots: set[int] = set()
        seq = 0
        for match in mc_fmt_re.finditer(text):
            idx, conv = match.group(1), match.group(2)
            if conv == "%":
                continue
            if idx is not None:
                slots.add(int(idx))
            else:
                seq += 1
                slots.add(seq)
        return slots

    symbol_only_keys = {
        "gui.chiseled_enchanting_table.cleanse.downgrade_row",
        "gui.chiseled_enchanting_table.tomes.btn_next",
        "gui.chiseled_enchanting_table.tomes.lock_mode_short.0",
        "gui.chiseled_enchanting_table.tomes.sacrifice_short",
    }

    for lname in ["en_us.json"] + others:
        lmap = read_json(os.path.join(LANG_DIR, lname))
        same_as_en = 0
        for key, val in lmap.items():
            if not isinstance(val, str):
                continue
            if val.strip() == key or "gui.chiseled_enchanting_table." in val:
                fail(f"{lname}: key '{key}' contains raw untranslated key text '{val}'")
            if any(ord(ch) > 0xFFFF and ch != "🔒" for ch in val):
                fail(
                    f"{lname}: key '{key}' contains supplementary-plane (> U+FFFF) glyph(s); "
                    f"only the explicitly accepted lock emoji may be non-BMP"
                )
            if key in reference and isinstance(reference[key], str):
                ref_slots = format_arg_slots(reference[key])
                cur_slots = format_arg_slots(val)
                if cur_slots != ref_slots:
                    fail(
                        f"{lname}: key '{key}' has format argument slots {sorted(cur_slots)}, "
                        f"expected {sorted(ref_slots)} matching en_us.json"
                    )
                if lname != "en_us.json" and key not in symbol_only_keys and val == reference[key]:
                    same_as_en += 1
        # Guard against bulk English fallback regression (genuine Romance/Germanic cognates <= 20)
        if lname != "en_us.json":
            cap = 0 if lname in {"zh_cn.json", "zh_tw.json", "ja_jp.json", "ko_kr.json", "ru_ru.json"} else 20
            if same_as_en > cap:
                fail(
                    f"{lname}: {same_as_en} value(s) are identical to en_us.json "
                    f"(exceeds cognate allowance {cap}; translate missing locale strings)"
                )
    note(
        f"code-to-lang, placeholder-slot, BMP-glyph, and translation coverage ok "
        f"({len(referenced_keys)} referenced keys verified across all {1 + len(others)} locales)"
    )


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
                    msg = f"{rel_path}: dangling link '{target}' -> {os.path.relpath(resolved, REPO)} does not exist"
                    if rel_path in {"README.md", os.path.join("docs", "DOCUMENTATION_INDEX.md"), os.path.join("docs", "WIKI.md")}:
                        fail(msg)
                    else:
                        warn(msg)
    if dangling == 0:
        note(f"markdown links ok ({scanned} file(s) scanned)")


def check_bmp_safe_java_literals() -> None:
    """GUI-06: Java sources must not embed supplementary-plane emojis or UTF-16
    surrogate escapes (\\uD800..\\uDFFF) outside SearchTextBuffer tests."""
    surrogate_esc = re.compile(r"\\u[Dd][89ABab][0-9A-Fa-f]{2}")
    offenders: list[str] = []
    for sub in ("main", "client"):
        root_dir = os.path.join(REPO, "src", sub, "java")
        for root, _dirs, files in os.walk(root_dir):
            for name in files:
                if not name.endswith(".java"):
                    continue
                path = os.path.join(root, name)
                text = open(path, encoding="utf-8").read()
                if any(ord(ch) > 0xFFFF and ch != "🔒" for ch in text) or surrogate_esc.search(text):
                    offenders.append(os.path.relpath(path, REPO))
    for off in offenders:
        fail(f"non-BMP glyph or surrogate escape in production Java source: {off}")
    if not offenders:
        note("production Java glyphs are BMP-safe except the explicitly accepted lock emoji")



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


def check_bundled_defaults_fresh() -> None:
    """JSON is the single source of truth for mechanic defaults: the checked-in
    generated BundledDefaults.java must be byte-identical to a fresh regeneration
    from the datapack JSON (drift gate, 2026-10-24 audit)."""
    import subprocess

    gen = os.path.join(REPO, "scripts", "gen_bundled_defaults.py")
    proc = subprocess.run(
        [sys.executable, gen, "--check"], capture_output=True, text=True, cwd=REPO
    )
    if proc.returncode != 0:
        fail("BundledDefaults.java is stale vs datapack JSON; run scripts/gen_bundled_defaults.py "
             f"({(proc.stderr or proc.stdout).strip()[:300]})")
    else:
        note("BundledDefaults.java matches datapack JSON (single source of truth)")

# --------------------------------------------------------------------------- #

def check_baseline_mixin_inventory() -> None:
    """Count AND class identities must agree with the generated baseline documentation."""
    import subprocess
    proc = subprocess.run(
        [sys.executable, os.path.join(REPO, "scripts", "check_baseline_mixins.py"), "--check"],
        capture_output=True, text=True, cwd=REPO,
    )
    if proc.returncode:
        fail((proc.stdout or proc.stderr).strip())
    else:
        note("CURRENT_BASELINE Mixin count and inventory match registered configs")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--porcelain", action="store_true", help="machine readable output")
    args = parser.parse_args()

    check_mixin_configs()
    check_lang_parity()
    check_no_embedded_json_fallbacks()
    check_markdown_links()
    check_bmp_safe_java_literals()
    check_locale_safe_formatting()
    check_bundled_defaults_fresh()
    check_baseline_mixin_inventory()

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
