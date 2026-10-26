#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对齐矩阵完整性校验（F3 落地 / gap-matrix「统计」节的执行者）。

校验项（任一失败退出码 1）：
  1. Chart.ts 全部公开方法（正则提取）在 gap-matrix 有 CH- 行，且符号列精确同名
  2. 每个方法名出现在 ts-inventory
  3. 方法名纠错护栏：上游实际为 getConvertPictureUrl，文档不得再出现旧误名 convertToPictureUrl
  4. Styles.ts 全部 export interface 在 ts-inventory（字段级）与 gap-matrix（STY- 行）出现
  5. 矩阵行格式：ID 前缀合法且唯一；分类为单字母 A-E；验收/工作量/批次取值合法
  6. 统计输出：按分类、按批次计数（矩阵本文不手写统计的唯一来源）

用法：python3 tools/alignment/check.py
"""
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UP = ROOT / 'third_party/klinechart-v10.1.0/src'
MATRIX = ROOT / 'docs/alignment/gap-matrix.md'
TSINV = ROOT / 'docs/alignment/ts-inventory.md'

PREFIXES = ('CH', 'IDX', 'EVT', 'TYP', 'IND', 'OVL', 'STY', 'NUM')
CLASSES = set('ABCDE')
VERIFS = {'test', 'diff', 'compile', 'parity', 'manual', 'existing', '—'}
WORKLOADS = {'S', 'M', 'L', '—'}
BATCHES = {'2a', '2b', '2c', '3', '4', '5', '6', '—'}

errors = []


def err(msg):
    errors.append(msg)


def extract_chart_methods():
    src = (UP / 'Chart.ts').read_text()
    out = []
    for m in re.finditer(r'^  ([a-zA-Z][a-zA-Z0-9]*)\(', src, re.M):
        name = m.group(1)
        if name != 'constructor':
            out.append((name, src[:m.start()].count('\n') + 1))
    return out


def extract_style_interfaces():
    src = (UP / 'common/Styles.ts').read_text()
    return re.findall(r'export interface (\w+)', src)


def parse_matrix():
    rows = []
    for i, line in enumerate(MATRIX.read_text().split('\n'), 1):
        if not re.match(r'^\| [A-Z]{2,3}-\d{3}', line):
            continue
        cells = [c.strip() for c in line.split('|')]
        # | ID | 符号 | 源 | 现状 | 分类 | 验收 | 工作量 | 批次 | 备注 |
        if len(cells) < 10:
            err(f'矩阵:{i} 列数不足（{len(cells) - 1}/9）: {line[:60]}')
            continue
        rows.append({
            'line': i, 'id': cells[1], 'symbol': cells[2], 'cls': cells[5],
            'verif': cells[6], 'work': cells[7], 'batch': cells[8],
        })
    return rows


def main():
    methods = extract_chart_methods()
    interfaces = extract_style_interfaces()
    matrix = parse_matrix()
    matrix_text = MATRIX.read_text()
    tsinv = TSINV.read_text()

    # --- 格式与取值 ---
    ids = [r['id'] for r in matrix]
    dup = [k for k, v in Counter(ids).items() if v > 1]
    if dup:
        err(f'ID 重复: {dup}')
    for r in matrix:
        if not re.fullmatch(r'[A-Z]{2,3}-\d{3}(~\d{3})?', r['id']):
            err(f"矩阵:{r['line']} ID 非法: {r['id']}")
        if r['id'].split('-')[0] not in PREFIXES:
            err(f"矩阵:{r['line']} ID 前缀非法: {r['id']}")
        if r['cls'] not in CLASSES:
            err(f"矩阵:{r['line']} 分类列非法（须单字母）: {r['cls']!r}")
        for part in r['verif'].split('+'):
            if part not in VERIFS:
                err(f"矩阵:{r['line']} 验收列非法: {r['verif']!r}")
        if r['work'] not in WORKLOADS:
            err(f"矩阵:{r['line']} 工作量列非法: {r['work']!r}")
        if r['batch'] not in BATCHES:
            err(f"矩阵:{r['line']} 批次列非法: {r['batch']!r}")

    # --- Chart 方法 100% 覆盖（符号列精确同名，杜绝合并行）---
    ch_symbols = set()
    for r in matrix:
        if r['id'].startswith('CH-'):
            m = re.fullmatch(r'`([a-zA-Z][a-zA-Z0-9]*)`', r['symbol'])
            if m:
                ch_symbols.add(m.group(1))
            else:
                err(f"矩阵:{r['line']} CH 行符号列须为单个方法名: {r['symbol']}")
    for name, ln in methods:
        if name not in ch_symbols:
            err(f'Chart.ts:{ln} 方法 `{name}` 无 CH- 行')
        if name not in tsinv:
            err(f'ts-inventory 缺方法 `{name}`')

    # --- 方法名纠错护栏 ---
    if '`convertToPictureUrl' in matrix_text or '`convertToPictureUrl' in tsinv:
        err('出现已纠错的方法名 convertToPictureUrl（上游为 getConvertPictureUrl）')

    # --- Styles 接口覆盖 ---
    sty_rows = {r['symbol'].strip('`') for r in matrix if r['id'].startswith('STY-')}
    for itf in interfaces:
        if f'`{itf}`' not in tsinv:
            err(f'ts-inventory 缺 Styles 接口 `{itf}`')
        if itf not in sty_rows:
            err(f'gap-matrix 缺 STY- 行: `{itf}`')

    # --- 统计输出 ---
    by_cls = Counter(r['cls'] for r in matrix)
    by_batch = Counter(r['batch'] for r in matrix)
    print(f'Chart.ts 公开方法: {len(methods)}（100% 要求 CH- 行）')
    print(f'Styles.ts 接口: {len(interfaces)}')
    print(f'矩阵数据行: {len(matrix)}')
    print('按分类:', dict(sorted(by_cls.items())), '| unknown:',
          sum(v for k, v in by_cls.items() if k not in CLASSES))
    print('按批次:', dict(sorted(by_batch.items())))

    if errors:
        print(f'\n校验失败 {len(errors)} 项:', file=sys.stderr)
        for e in errors[:40]:
            print('  -', e, file=sys.stderr)
        sys.exit(1)
    print('\n校验通过：覆盖完整、格式合法、0 unknown')


if __name__ == '__main__':
    main()
