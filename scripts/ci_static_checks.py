#!/usr/bin/env python3
"""Static checks for Enchantment Fusion (offline, fast, zero network required):
1. Mixin config integrity + registered mixins exist in source and are never excluded in build.gradle.
2. 11-locale translation key parity, zero literal newlines, BMP-safe characters, and Java key coverage.
3. Pixel-art UI invariant: zero raw `.fill(` placeholder calls in client GUI sources.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
LOCALES = (
    "de_de", "en_us", "es_es", "fr_fr", "it_it", "ja_jp",
    "ko_kr", "pt_br", "ru_ru", "zh_cn", "zh_tw"
)


def check_mixins() -> None:
    mixin_cfg_path = ROOT / "src/main/resources/enchantment_fusion.mixins.json"
    if not mixin_cfg_path.is_file():
        raise AssertionError(f"Missing mixin config: {mixin_cfg_path}")
    cfg = json.loads(mixin_cfg_path.read_text(encoding="utf-8"))
    pkg = cfg["package"].replace(".", "/")
    build_gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")

    for section, src_root in (
        ("mixins", ROOT / "src/main/java"),
        ("server", ROOT / "src/main/java"),
        ("client", ROOT / "src/client/java"),
    ):
        for entry in cfg.get(section, []):
            rel = f"{pkg}/{entry.replace('.', '/')}.java"
            target = src_root / rel
            if not target.is_file():
                raise AssertionError(f"Registered mixin missing source file: {target}")
            if entry in build_gradle:
                raise AssertionError(f"Registered mixin {entry} must not be excluded in build.gradle")


def check_locales() -> None:
    lang_dir = ROOT / "src/client/resources/assets/enchantment_fusion/lang"
    en_us = json.loads((lang_dir / "en_us.json").read_text(encoding="utf-8"))
    expected = set(en_us.keys())
    if len(expected) < 100:
        raise AssertionError(f"Expected >= 100 translation keys, found {len(expected)}")

    for code in LOCALES:
        p = lang_dir / f"{code}.json"
        if not p.is_file():
            raise AssertionError(f"Missing locale file: {p}")
        data = json.loads(p.read_text(encoding="utf-8"))
        if set(data.keys()) != expected:
            missing = expected - set(data.keys())
            extra = set(data.keys()) - expected
            raise AssertionError(f"Locale {code} drift: missing={missing}, extra={extra}")
        for k, v in data.items():
            if not isinstance(v, str) or "\n" in v:
                raise AssertionError(f"Invalid or newline-containing translation {code}:{k}")
            if any(ord(ch) > 0xFFFF for ch in v):
                raise AssertionError(f"Non-BMP character in {code}:{k}")

    zh_tw = json.loads((lang_dir / "zh_tw.json").read_text(encoding="utf-8"))
    if "纂刻附魔台" not in zh_tw.get("tooltip.enchantment_fusion.aberrant_cet_active", ""):
        raise AssertionError("zh_tw must use Traditional Chinese sister-mod title 纂刻附魔台")

    pat = re.compile(r'translatable\(\s*"([^"]+)"')
    for src_dir in (ROOT / "src/main/java", ROOT / "src/client/java"):
        for jf in src_dir.rglob("*.java"):
            txt = jf.read_text(encoding="utf-8")
            for m in pat.finditer(txt):
                key = m.group(1)
                if key not in expected:
                    raise AssertionError(f"Unlocalized key {key} referenced in {jf}")


def check_gui_no_solid_fills() -> None:
    gui_dir = ROOT / "src/client/java/enchantment_fusion/gui"
    tex_root = ROOT / "src/client/resources/assets/enchantment_fusion/textures"
    sprite_re = re.compile(r'EnchantmentFusion\.id\(\s*"(gui/[^"]+\.png)"\s*\)')
    for jf in gui_dir.rglob("*.java"):
        txt = jf.read_text(encoding="utf-8")
        if ".fill(" in txt:
            raise AssertionError(f"Forbidden solid-color .fill( placeholder in {jf}")
        for rel in sprite_re.findall(txt):
            if not (tex_root / rel).is_file():
                raise AssertionError(f"GUI sprite {rel} in {jf} missing PNG at {tex_root / rel}")


def check_entrypoints() -> None:
    fmj = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text(encoding="utf-8"))
    for kind, classes in fmj.get("entrypoints", {}).items():
        src_root = ROOT / ("src/main/java" if kind == "main" else "src/client/java")
        for cls in classes:
            path = src_root / (cls.replace(".", "/") + ".java")
            if not path.is_file():
                raise AssertionError(f"Entrypoint {kind}:{cls} missing source file {path}")


def main() -> int:
    check_entrypoints()
    check_mixins()
    check_locales()
    check_gui_no_solid_fills()
    print("ok: static checks passed (entrypoints, mixins, 11-locale parity, pixel-art GUI invariants)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
