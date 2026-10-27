#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从上游 TS 指标模板源码提取元数据（F1：元数据 diff 的生成器）。

修复记录：figures 提取不再全文扫 key/title（会误抓 getVolumeFigure()
函数定义导致顺序失真），而是优先解析 `figures: [...]` 块；仅当模板
使用 regenerateFigures 动态生成（无静态 figures）时才回退全文扫描。
"""
import json
import re
from pathlib import Path

UP = Path(__file__).resolve().parents[2] / 'third_party/klinechart-v10.1.0/src/extension/indicator'


def extract_figures_in_order(src: str):
    """优先：figures: [ ... ] 块内按序抓 key；找不到块时全文抓。"""
    m = re.search(r'figures:\s*\[', src)
    if m:
        start = m.end()
        depth = 1
        i = start
        while depth > 0 and i < len(src):
            if src[i] == '[':
                depth += 1
            elif src[i] == ']':
                depth -= 1
            i += 1
        body = src[start:i - 1]
        keys = re.findall(r"key:\s*'(\w+)'", body)
        if keys:
            return keys
    # 回退：全文（regenerateFigures 动态模板）
    return re.findall(r"key:\s*'(\w+)'", src)


def main():
    result = {}
    for path in sorted(UP.glob('*.ts')):
        if path.name == 'index.ts':
            continue
        src = path.read_text()
        name_m = re.search(r"name: '(\w+)'", src)
        if not name_m:
            continue
        name = name_m.group(1)

        def grab_str(key):
            m = re.search(rf"{key}: '([^']*)'", src)
            return m.group(1) if m else None

        def grab_num(key):
            m = re.search(rf"{key}: (-?\d+(?:\.\d+)?)", src)
            return float(m.group(1)) if m else None

        params_m = re.search(r'calcParams: \[([^\]]*)\]', src)
        params = None
        if params_m:
            nums = re.findall(r'-?\d+(?:\.\d+)?', params_m.group(1))
            params = [float(x) for x in nums] if nums else []

        # figures：块内顺序提取
        fig_keys = extract_figures_in_order(src)
        # 函数调用型 figure（如 VOL 的 getVolumeFigure()）：块内无字面量 key，
        # 从函数定义抓 key，并登记 hasFigureFunctionCall 供测试改集合断言
        has_fig_fn = bool(re.search(r'figures:\s*\[[^\]]*\bget\w+Figure\(\)', src, re.S))
        if has_fig_fn:
            fn_keys = re.findall(r"key:\s*'(\w+)'", src.split('figures:')[0])
            fig_keys = fig_keys + [k for k in fn_keys if k not in fig_keys]
        # figures title：与 key 配对（同块内按出现顺序）
        figs = []
        if fig_keys:
            title_map = {}
            for k, t in re.findall(r"key:\s*'(\w+)',\s*title:\s*'([^']*)'", src):
                title_map[k] = t
            figs = [[k, title_map.get(k, '')] for k in fig_keys]

        result[name] = {
            'file': path.name,
            'shortName': grab_str('shortName'),
            'series': grab_str('series'),
            'calcParams': params,
            'precision': grab_num('precision'),
            'minValue': grab_num('minValue'),
            'maxValue': grab_num('maxValue'),
            'shouldOhlc': bool(re.search(r'shouldOhlc:\s*true', src)),
            'shouldFormatBigNumber': bool(re.search(r'shouldFormatBigNumber:\s*true', src)),
            'hasFigureFunctionCall': has_fig_fn,
            'figures': figs,
        }

    out = Path(__file__).resolve().parents[2] / 'lkanvas-core/src/test/resources/fixtures/ts-indicator-meta.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=1, ensure_ascii=False))
    print(f'提取 {len(result)} 个模板 → {out}')
    vol = result.get('VOL')
    print('VOL figures 顺序:', [f[0] for f in vol['figures']])


if __name__ == '__main__':
    main()
