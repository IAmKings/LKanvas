#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对齐矩阵完整性校验 v4（gap-matrix「统计」节的执行者/发布判定工具）。

相对 v3：引入四态状态体系（审计发现 1 的收口）——
  状态列取值：已声明｜已接线｜行为通过｜结构可对应｜—
  * 已接线/行为通过/结构可对应 行必须引用至少一个 `.kt` 路径，且被引用文件在
    lkanvas-core / lkanvas-compose 中真实存在（全路径或文件名均可）；
  * 行为通过 行还必须引用至少一个 `*Test` 测试类，且对应测试文件真实存在；
  * E=0 不再视为完成态，发布判定以状态分布为准。
  * SLA- 行（审计发现追踪）只校验状态枚举，证据由 structure-map.md 与矩阵行承载。
v3 既有七面校验保留：
  ① Chart.ts 全部公开方法 → CH- 行（符号精确同名，禁合并行）
  ② Styles.ts 全部 export interface → ts-inventory 字段级 + 矩阵 STY- 行
  ③ index.ts export 块 39 符号 → 20 个值符号须在矩阵；19 类型别名须在 ts-inventory
  ④ index.ts utils 对象 25 个 key → 逐一出现在矩阵
  ⑤ Action.ts ActionType 9 种 → 矩阵 EVT- 行逐一对应
  ⑥ Indicator.ts Indicator 接口 23 字段 → 矩阵 IND- 行逐一对应
  ⑦ Overlay.ts Overlay 实例接口 24 字段 → 矩阵 OVL- 行逐一对应
另：行格式（ID 唯一/前缀/分类单字母/验收/工作量/批次枚举）与
    方法名纠错护栏（getConvertPictureUrl）。

用法：python3 tools/alignment/check.py   （失败退出码 1，可挂 CI/pre-commit）
"""
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UP = ROOT / 'third_party/klinechart-v10.1.0/src'
MATRIX = ROOT / 'docs/alignment/gap-matrix.md'
TSINV = ROOT / 'docs/alignment/ts-inventory.md'

PREFIXES = ('CH', 'IDX', 'EVT', 'TYP', 'IND', 'OVL', 'STY', 'NUM', 'SLA')
CLASSES = set('ABCDE')
VERIFS = {'test', 'diff', 'compile', 'parity', 'manual', 'existing', '—'}
WORKLOADS = {'S', 'M', 'L', '—'}
BATCHES = {'2a', '2b', '2c', '3', '4', '5', '6', '06', '—'}
STATES = {'已声明', '已接线', '行为通过', '结构可对应', '—'}
WIRED_STATES = {'已接线', '行为通过', '结构可对应'}

errors = []


def err(msg):
    errors.append(msg)



def extract_chart_methods():
    src = (UP / 'Chart.ts').read_text()
    out = []
    for m in re.finditer(r'^  ([a-zA-Z][a-zA-Z0-9]*)\(', src, re.M):
        if m.group(1) != 'constructor':
            out.append((m.group(1), src[:m.start()].count('\n') + 1))
    return out


def extract_styles():
    return re.findall(r'export interface (\w+)', (UP / 'common/Styles.ts').read_text())


def extract_index_exports():
    src = (UP / 'index.ts').read_text()
    block = re.search(r'export \{(.+?)\}', src, re.S).group(1)
    syms = [s.strip().replace('type ', '') for s in block.split(',') if s.strip()]
    values, types = [], []
    for s in syms:
        (types if s[0].isupper() else values).append(s)
    return values, types


def extract_utils_keys():
    src = (UP / 'index.ts').read_text()
    block = re.search(r'const utils = \{(.+?)\n\}', src, re.S).group(1)
    return re.findall(r'^\s{2}(\w+)[,\n]', block, re.M)


def extract_action_types():
    src = (UP / 'common/Action.ts').read_text()
    line = re.search(r"ActionType = (.+)$", src, re.M).group(1)
    return re.findall(r"'(on\w+)'", line)


def extract_iface_fields(path, iface):
    """锚定精确接口名（含泛型参数），避免误抓 IndicatorFigure/OverlayPerformEventParams 等前缀同名接口。"""
    src = (UP / path).read_text()
    m = re.search(rf'export interface {iface}(?![A-Za-z])(<[^{{>]*>)?[^{{]*\{{', src)
    start = m.end()
    depth = 1
    i = start
    while depth > 0 and i < len(src):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
        i += 1
    body = src[start:i - 1]
    return re.findall(r'^\s{2}([a-zA-Z]+)\??:', body, re.M)


def parse_matrix():
    rows = []
    for i, line in enumerate(MATRIX.read_text().split('\n'), 1):
        if not re.match(r'^\| [A-Z]{2,3}-\d{3}', line):
            continue
        cells = [c.strip() for c in line.split('|')]
        if cells[1].startswith('SLA-'):
            # SLA 行（§14 审计追踪表）为 5 列：ID | 发现 | 批次 | 备注 | 状态
            if len(cells) < 6:
                err(f'矩阵:{i} SLA 行列数不足: {line[:60]}')
                continue
            rows.append({'line': i, 'id': cells[1], 'symbol': cells[2], 'cls': '—',
                         'verif': '—', 'work': '—', 'batch': cells[3],
                         'state': cells[5] if len(cells) > 5 else None, 'text': line})
            continue
        if len(cells) < 10:
            err(f'矩阵:{i} 列数不足（{len(cells)-1}/9）: {line[:60]}')
            continue
        state = cells[10] if len(cells) > 11 else None
        rows.append({'line': i, 'id': cells[1], 'symbol': cells[2], 'cls': cells[5],
                     'verif': cells[6], 'work': cells[7], 'batch': cells[8],
                     'state': state, 'text': line})
    return rows


def validate_structure_map():
    """R12：structure-map.md 引用的测试类/文件必须真实存在。"""
    smap = ROOT / 'docs/alignment/structure-map.md'
    if not smap.exists():
        return
    text = smap.read_text()
    test_files = set()
    for p in (ROOT / 'lkanvas-core/src/test').rglob('*.kt'):
        test_files.add(p.stem)
    for p in (ROOT / 'lkanvas-compose/src/test').rglob('*.kt'):
        test_files.add(p.stem)
    for m in re.finditer(r'Parity\d+\w*Test', text):
        if m.group(0) not in test_files:
            err(f"structure-map: 引用的测试类不存在: {m.group(0)}")
    for m in re.finditer(r'lkanvas-[\w-]+/[\w/.-]+\.kt', text):
        tok = m.group(0)
        if '/' in tok and not (ROOT / tok).exists():
            err(f"structure-map: 引用文件不存在: {tok}")
    if '待登记' in text or '待回填' in text:
        err("structure-map: 存在「待登记/待回填」占位（映射台账必须完整）")


def collect_kt_files():
    files = {}
    for base in ('lkanvas-core', 'lkanvas-compose'):
        d = ROOT / base
        if d.is_dir():
            for f in d.rglob('*.kt'):
                files[f.name] = f
    return files


def validate_states(rows, kt_files):
    by_state = Counter()
    for r in rows:
        s = r['state']
        if s is None:
            err(f"矩阵:{r['line']} 缺状态列（v4 必填）: {r['id']}")
            continue
        if s not in STATES:
            err(f"矩阵:{r['line']} 状态列非法（{sorted(STATES)}）: {r['id']} = {s!r}")
            continue
        by_state[s] += 1
        if s == '—':
            continue
        # R12（复审 2026-10-30）：SLA 行不再豁免证据检查——
        # 行为通过须引用真实存在的 .kt 与 *Test（与能力行同口径）
        if s in WIRED_STATES:
            kt_tokens = re.findall(r'[\w][\w/.-]*\.kt', r['text'])
            if not kt_tokens:
                err(f"矩阵:{r['line']} 状态={s} 须引用至少一个 .kt 符号路径: {r['id']}")
                continue
            for tok in set(kt_tokens):
                if '/' in tok:
                    if not (ROOT / tok).exists():
                        err(f"矩阵:{r['line']} 引用文件不存在: {tok} ({r['id']})")
                elif tok not in kt_files:
                    err(f"矩阵:{r['line']} 引用文件不存在（文件名未找到）: {tok} ({r['id']})")
        if s == '行为通过':
            tests = set(re.findall(r'\b(\w+Test)\b', r['text']))
            if not tests:
                err(f"矩阵:{r['line']} 状态=行为通过 须引用至少一个 *Test 测试类: {r['id']}")
                continue
            test_files = {f.name for f in kt_files.values() if '/src/test/' in str(f)}
            for t in tests:
                if f'{t}.kt' not in test_files:
                    err(f"矩阵:{r['line']} 引用测试文件不存在: {t}.kt ({r['id']})")
    return by_state


def main():
    methods = extract_chart_methods()
    interfaces = extract_styles()
    idx_values, idx_types = extract_index_exports()
    utils_keys = extract_utils_keys()
    actions = extract_action_types()
    ind_fields = extract_iface_fields('component/Indicator.ts', 'Indicator')
    ovl_fields = extract_iface_fields('component/Overlay.ts', 'Overlay')

    matrix = parse_matrix()
    matrix_text = MATRIX.read_text()
    tsinv = TSINV.read_text()

    # ---- 格式与取值 ----
    ids = [r['id'] for r in matrix]
    for k, v in Counter(ids).items():
        if v > 1:
            err(f'ID 重复: {k}')
    for r in matrix:
        if not re.fullmatch(r'[A-Z]{2,3}-\d{3}(~\d{3})?', r['id']):
            err(f"矩阵:{r['line']} ID 非法: {r['id']}")
        if r['id'].split('-')[0] not in PREFIXES:
            err(f"矩阵:{r['line']} ID 前缀非法: {r['id']}")
        if r['id'].startswith('SLA-'):
            continue
        if r['cls'] not in CLASSES:
            err(f"矩阵:{r['line']} 分类列非法（须单字母）: {r['cls']!r}")
        for part in r['verif'].split('+'):
            if part not in VERIFS:
                err(f"矩阵:{r['line']} 验收列非法: {r['verif']!r}")
        if r['work'] not in WORKLOADS:
            err(f"矩阵:{r['line']} 工作量列非法: {r['work']!r}")
        if r['batch'] not in BATCHES:
            err(f"矩阵:{r['line']} 批次列非法: {r['batch']!r}")

    # ---- ① Chart 方法 ----
    ch_symbols = set()
    for r in matrix:
        if r['id'].startswith('CH-'):
            m = re.fullmatch(r'`([a-zA-Z][a-zA-Z0-9]*)`', r['symbol'])
            if m:
                ch_symbols.add(m.group(1))
            else:
                err(f"矩阵:{r['line']} CH 行符号列须为单方法名: {r['symbol']}")
    for name, ln in methods:
        if name not in ch_symbols:
            err(f'[Chart] Chart.ts:{ln} `{name}` 无 CH- 行')
        if name not in tsinv:
            err(f'[Chart] ts-inventory 缺 `{name}`')
    # 签名列非空校验（v2 全空缺陷的回归护栏）
    empty_sigs = re.findall(r'^\| `([a-zA-Z]+)` \| Chart\.ts:\d+ \| \(\) \|$', tsinv, re.M)
    for n in empty_sigs:
        if n in ('resize',):  # 个别确无参可豁免（显式列出）
            continue
        err(f'[Chart] ts-inventory `{n}` 签名列为空 ()')

    # ---- ② Styles ----
    sty = {r['symbol'].strip('`') for r in matrix if r['id'].startswith('STY-')}
    for itf in interfaces:
        if f'`{itf}`' not in tsinv:
            err(f'[Styles] ts-inventory 缺接口 `{itf}`')
        if itf not in sty:
            err(f'[Styles] 矩阵缺 STY- 行 `{itf}`')

    # ---- ③ index.ts 导出 ----
    for sym in idx_values:
        # utils 对象本身以 `utils.` 前缀行展开（IDX-004~028）
        hit = f'`{sym}`' in matrix_text or f'`{sym}.' in matrix_text or sym in ch_symbols
        if not hit:
            err(f'[index] 值导出 `{sym}` 未出现在矩阵')
    for t in idx_types:
        if t not in tsinv:
            err(f'[index] 类型别名 `{t}` 未出现在 ts-inventory')

    # ---- ④ utils ----
    for k in utils_keys:
        if k not in matrix_text:
            err(f'[utils] `{k}` 未出现在矩阵')

    # ---- ⑤ ActionType ----
    evt_syms = set()
    for r in matrix:
        if r['id'].startswith('EVT-'):
            m = re.match(r'`(on\w+)`', r['symbol'])
            if m:
                evt_syms.add(m.group(1))
    for a in actions:
        if a not in evt_syms:
            err(f'[Action] `{a}` 无 EVT- 行')

    # ---- ⑥ Indicator 字段 ----
    ind_syms = set()
    for r in matrix:
        if r['id'].startswith('IND-'):
            m = re.fullmatch(r'`([a-zA-Z]+)`', r['symbol'])
            if m:
                ind_syms.add(m.group(1))
    for f in ind_fields:
        if f not in ind_syms and f'`{f}`' not in matrix_text:
            err(f'[Indicator] 字段 `{f}` 无 IND- 行/未出现')

    # ---- ⑦ Overlay 实例字段 ----
    ovl_syms = set()
    for r in matrix:
        if r['id'].startswith('OVL-'):
            m = re.fullmatch(r'`([a-zA-Z]+)`', r['symbol'])
            if m:
                ovl_syms.add(m.group(1))
    for f in ovl_fields:
        if f not in ovl_syms:
            err(f'[Overlay] 实例字段 `{f}` 无 OVL- 行')

    # ---- 状态体系（v4） ----
    kt_files = collect_kt_files()
    by_state = validate_states(matrix, kt_files)

    # ---- 方法名纠错护栏 ----
    for name in ('convertToPictureUrl',):
        if f'`{name}' in matrix_text or f'`{name}' in tsinv:
            err(f'[护栏] 出现已纠错方法名 {name}')

    # ---- 统计输出 ----
    by_cls = Counter(r['cls'] for r in matrix)
    by_batch = Counter(r['batch'] for r in matrix)
    print(f'校验面: Chart 方法 {len(methods)} | Styles 接口 {len(interfaces)} | '
          f'index 值导出 {len(idx_values)}+类型 {len(idx_types)} | utils {len(utils_keys)} | '
          f'ActionType {len(actions)} | Indicator 字段 {len(ind_fields)} | Overlay 实例字段 {len(ovl_fields)}')
    print(f'矩阵数据行: {len(matrix)}')
    print('按分类:', dict(sorted(by_cls.items())))
    print('按批次:', dict(sorted(by_batch.items())))
    print('按状态:', dict(sorted(by_state.items())))

    if errors:
        print(f'\n校验失败 {len(errors)} 项:', file=sys.stderr)
        for e in errors[:50]:
            print('  -', e, file=sys.stderr)
        sys.exit(1)
    print('\n校验通过：七面覆盖完整、格式合法、签名列非空、状态列合法且引用文件存在')


if __name__ == '__main__':
    main()
