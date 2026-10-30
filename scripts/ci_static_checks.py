#!/usr/bin/env python3
"""Static checks for Enchantment Fusion (offline, fast, zero network required):
1. Mixin config integrity + registered mixins exist in source and are never excluded in build.gradle.
2. 11-locale translation key parity, zero literal newlines, BMP-safe characters, dead-key absence,
   unified terminology invariants, and Java key coverage.
3. Pixel-art UI invariant: zero raw `.fill(` placeholder calls in client GUI sources.
4. Signature Resonance invariant: zero symmetric `regAlias(` shortcuts in SignaturePairs.java.
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
FORBIDDEN_DEAD_KEYS = {
    "gui.enchantment_fusion.preview.idle_line1",
    "gui.enchantment_fusion.preview.idle_line2",
    "gui.enchantment_fusion.preview.flip_split_ready",
    "gui.enchantment_fusion.preview.blocked",
    "gui.enchantment_fusion.preview.expand_title",
    "gui.enchantment_fusion.preview.expand_desc",
    "gui.enchantment_fusion.preview.topup_title",
    "gui.enchantment_fusion.preview.topup_desc",
    "gui.enchantment_fusion.preview.repulsive_stability",
    "gui.enchantment_fusion.preview.harmonic_stable",
    "gui.enchantment_fusion.tip.flip.title",
    "gui.enchantment_fusion.tip.flip.desc",
    "gui.enchantment_fusion.tip.split.title",
    "gui.enchantment_fusion.tip.split.desc",
    "gui.enchantment_fusion.tip.preview.title",
    "gui.enchantment_fusion.tab.compendium",
    "gui.enchantment_fusion.slot.split_target",
    "gui.enchantment_fusion.tab2.place_book_hint",
    "gui.enchantment_fusion.btn.xp_badge",
}


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
    if len(expected) < 700:
        raise AssertionError(f"Expected >= 700 translation keys, found {len(expected)}")
    leaked_dead = expected & FORBIDDEN_DEAD_KEYS
    if leaked_dead:
        raise AssertionError(f"Dead translation keys must remain purged: {sorted(leaked_dead)}")

    def extract_specifiers(s: str) -> list[str]:
        out: list[str] = []
        i = 0
        while i < len(s):
            if s[i] == "%":
                if i + 1 < len(s) and s[i + 1] == "%":
                    i += 2
                    continue
                m = re.match(r"%(?:(\d+)\$)?([sd])", s[i:])
                if m:
                    out.append(m.group(0))
                    i += len(m.group(0))
                    continue
            i += 1
        return out

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
            if len(extract_specifiers(en_us[k])) != len(extract_specifiers(v)):
                raise AssertionError(f"Format specifier count drift in {code}:{k}")

    zh_cn = json.loads((lang_dir / "zh_cn.json").read_text(encoding="utf-8"))
    zh_tw = json.loads((lang_dir / "zh_tw.json").read_text(encoding="utf-8"))
    if "纂刻附魔台" not in zh_cn.get("tooltip.enchantment_fusion.aberrant_cet_active", ""):
        raise AssertionError("zh_cn must use unified sister-mod title 纂刻附魔台")
    if "纂刻附魔台" not in zh_tw.get("tooltip.enchantment_fusion.aberrant_cet_active", ""):
        raise AssertionError("zh_tw must use unified sister-mod title 纂刻附魔台")
    for k, v in zh_cn.items():
        for bad in ("雕纹附魔台", "雕錾附魔台", "器灵槽", "谐振稳定晶", "共鸣稳定剂", "异位单魔"):
            if bad in v:
                raise AssertionError(f"Forbidden legacy term '{bad}' in zh_cn:{k}")

    pat = re.compile(r'translatable\(\s*"([^"]+)"')
    ns_lit_pat = re.compile(
        r'"((?:gui|tooltip|signature|transmute|item|block|effect|advancements)\.enchantment_fusion\.[a-z0-9_.-]+)"'
    )
    for src_dir in (ROOT / "src/main/java", ROOT / "src/client/java"):
        for jf in src_dir.rglob("*.java"):
            txt = jf.read_text(encoding="utf-8")
            for m in pat.finditer(txt):
                key = m.group(1)
                if key not in expected:
                    raise AssertionError(f"Unlocalized key {key} referenced in {jf}")
            for m in ns_lit_pat.finditer(txt):
                key = m.group(1)
                if key.endswith("."):
                    continue
                if key not in expected:
                    raise AssertionError(f"Unlocalized literal key {key} referenced in {jf}")


def check_gui_no_solid_fills() -> None:
    gui_dir = ROOT / "src/client/java/enchantment_fusion/gui"
    assets_root = ROOT / "src/client/resources/assets/enchantment_fusion"
    sprite_re = re.compile(r'EnchantmentFusion\.id\(\s*"([^"]+\.png)"\s*\)')
    for jf in gui_dir.rglob("*.java"):
        txt = jf.read_text(encoding="utf-8")
        if ".fill(" in txt:
            raise AssertionError(f"Forbidden solid-color .fill( placeholder in {jf}")
        for rel in sprite_re.findall(txt):
            if not (assets_root / rel).is_file():
                raise AssertionError(f"GUI sprite {rel} in {jf} missing PNG at {assets_root / rel}")


def check_signature_directionality() -> None:
    sig_src = (ROOT / "src/main/java/enchantment_fusion/mechanics/SignaturePairs.java").read_text(encoding="utf-8")
    if "regAlias(" in sig_src:
        raise AssertionError("Symmetric regAlias( shortcuts are forbidden in SignaturePairs.java")


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
    check_signature_directionality()
    print("ok: static checks passed (entrypoints, mixins, 11-locale parity & terminology, pixel-art GUI, 100% directional signatures)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
