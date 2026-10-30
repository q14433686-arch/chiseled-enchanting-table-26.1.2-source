#!/usr/bin/env python3
"""Ensure every translatable key referenced in Java source exists in all 11 locale files.

Missing keys are added with an English default value. zh_cn / zh_tw receive
curated Chinese translations for the new in-game configuration surface so the
static-check parity gate stays green.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCALES = (
    "de_de", "en_us", "es_es", "fr_fr", "it_it", "ja_jp",
    "ko_kr", "pt_br", "ru_ru", "zh_cn", "zh_tw",
)
LANG_DIR = ROOT / "src/client/resources/assets/enchantment_fusion/lang"

KEY_RE = re.compile(
    r'translatable\(\s*"([^"]+)"\s*'
)
LIT_RE = re.compile(
    r'"((?:gui|tooltip|signature|transmute|item|block|effect|advancements|config)\.enchantment_fusion\.[A-Za-z0-9_.-]+)"'
)

# Curated Chinese translations for the new configuration surface.
ZH_CN = {
    "gui.enchantment_fusion.config.title": "融合附魔配置",
    "gui.enchantment_fusion.config.cat.system": "系统与仪式",
    "gui.enchantment_fusion.config.cat.economy": "经济与风险曲线",
    "gui.enchantment_fusion.config.cat.triggers": "战斗触发器与蓄力",
    "gui.enchantment_fusion.config.cat.entries": "词条开关",
    "gui.enchantment_fusion.config.btn.reset": "重置默认",
    "gui.enchantment_fusion.config.btn.cancel": "取消",
    "gui.enchantment_fusion.config.btn.save": "保存并应用",
    "gui.enchantment_fusion.config.state_on": "开启",
    "gui.enchantment_fusion.config.state_off": "关闭",
    "gui.enchantment_fusion.config.filter_disabled": "仅显示禁用",
    "gui.enchantment_fusion.config.filter_all": "显示全部",
    "gui.enchantment_fusion.config.status.readonly": "只读（服务器锁定）",
    "gui.enchantment_fusion.config.status.summary": "已修改 {0} 个共鸣与 {1} 个化形",
    "gui.enchantment_fusion.config.msg.reloaded": "配置已从磁盘重新加载",
    "gui.enchantment_fusion.config.msg.reset_done": "已重置全部配置",
    "gui.enchantment_fusion.config.tip.btn": "打开融合附魔台游戏内配置面板",
    "gui.enchantment_fusion.grimoire.search_hint": "搜索秘典条目…",
}
ZH_TW = {
    "gui.enchantment_fusion.config.title": "融合附魔配置",
    "gui.enchantment_fusion.config.cat.system": "系統與儀式",
    "gui.enchantment_fusion.config.cat.economy": "經濟與風險曲線",
    "gui.enchantment_fusion.config.cat.triggers": "戰鬥觸發器與蓄力",
    "gui.enchantment_fusion.config.cat.entries": "詞條開關",
    "gui.enchantment_fusion.config.btn.reset": "重設預設",
    "gui.enchantment_fusion.config.btn.cancel": "取消",
    "gui.enchantment_fusion.config.btn.save": "儲存並套用",
    "gui.enchantment_fusion.config.state_on": "開啟",
    "gui.enchantment_fusion.config.state_off": "關閉",
    "gui.enchantment_fusion.config.filter_disabled": "僅顯示禁用",
    "gui.enchantment_fusion.config.filter_all": "顯示全部",
    "gui.enchantment_fusion.config.status.readonly": "唯讀（伺服器鎖定）",
    "gui.enchantment_fusion.config.status.summary": "已修改 {0} 個共鳴與 {1} 個化形",
    "gui.enchantment_fusion.config.msg.reloaded": "配置已從磁盤重新載入",
    "gui.enchantment_fusion.config.msg.reset_done": "已重設全部配置",
    "gui.enchantment_fusion.config.tip.btn": "開啟融合附魔台遊戲內配置面板",
    "gui.enchantment_fusion.grimoire.search_hint": "搜尋秘典條目…",
}

# Field label / description English defaults (keyed by translatable key).
FIELD_LABELS = {}
FIELD_DESCS = {}


def collect_java_keys() -> set[str]:
    keys: set[str] = set()
    for src_dir in (ROOT / "src/main/java", ROOT / "src/client/java"):
        for jf in src_dir.rglob("*.java"):
            txt = jf.read_text(encoding="utf-8")
            for m in KEY_RE.finditer(txt):
                keys.add(m.group(1))
            # ci_static_checks also matches raw locale-like string literals.
            for m in LIT_RE.finditer(txt):
                keys.add(m.group(1))
    return keys


def main() -> int:
    java_keys = collect_java_keys()
    # Only the translatable() single-arg form is scanned by ci_static_checks;
    # literal keys are also checked, but translatable covers our new surface.
    en_us = json.loads((LANG_DIR / "en_us.json").read_text(encoding="utf-8"))

    # Build English defaults for config field labels/descs referenced in Java.
    missing_en = {}
    for k in sorted(java_keys):
        if k not in en_us:
            # English fallback: humanize the key tail.
            tail = k.rsplit(".", 1)[-1]
            pretty = tail.replace("_", " ").title()
            missing_en[k] = pretty

    if missing_en:
        print(f"Adding {len(missing_en)} missing English keys to en_us.json ...")
        en_us.update(missing_en)
        (LANG_DIR / "en_us.json").write_text(
            json.dumps(en_us, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )

    # Re-read en_us as the canonical key set.
    en_us = json.loads((LANG_DIR / "en_us.json").read_text(encoding="utf-8"))
    canonical = set(en_us.keys())

    added_total = 0
    for code in LOCALES:
        p = LANG_DIR / f"{code}.json"
        data = json.loads(p.read_text(encoding="utf-8"))
        before = len(data)
        trans = ZH_CN if code == "zh_cn" else (ZH_TW if code == "zh_tw" else None)
        for k in sorted(canonical):
            if k not in data:
                if trans and k in trans:
                    data[k] = trans[k]
                else:
                    data[k] = en_us[k]
        # Re-sort to keep en_us first then alphabetical; preserve existing order + append new.
        ordered = {k: data[k] for k in list(data.keys())}
        p.write_text(json.dumps(ordered, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        added_total += len(data) - before
        print(f"  {code}: {before} -> {len(data)} (+{len(data) - before})")

    print(f"Total keys added across locales: {added_total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
