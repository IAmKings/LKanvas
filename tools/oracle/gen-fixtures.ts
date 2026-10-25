/**
 * LKanvas TS 对照 oracle 生成器（开发期工具，不进入发布制品）。
 *
 * 直接 import third_party/klinechart-v10.1.0 的上游指标模板，在固定输入上运行
 * 上游 calc，把「输入 candles + 参数 + 逐位置结果（缺失为 null）」写成 golden
 * fixture JSON，供 lkanvas-core 的 JVM 测试逐位置比对（PRD 8.1 第二层验证）。
 *
 * 用法：npm run gen
 * 输出：lkanvas-core/src/test/resources/fixtures/indicator/*.json
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

import movingAverage from '../../third_party/klinechart-v10.1.0/src/extension/indicator/movingAverage.ts'
import volume from '../../third_party/klinechart-v10.1.0/src/extension/indicator/volume.ts'
import macd from '../../third_party/klinechart-v10.1.0/src/extension/indicator/movingAverageConvergenceDivergence.ts'
import ema from '../../third_party/klinechart-v10.1.0/src/extension/indicator/exponentialMovingAverage.ts'
import sma from '../../third_party/klinechart-v10.1.0/src/extension/indicator/simpleMovingAverage.ts'
import boll from '../../third_party/klinechart-v10.1.0/src/extension/indicator/bollingerBands.ts'
import bbi from '../../third_party/klinechart-v10.1.0/src/extension/indicator/bullAndBearIndex.ts'
import bias from '../../third_party/klinechart-v10.1.0/src/extension/indicator/bias.ts'
import ao from '../../third_party/klinechart-v10.1.0/src/extension/indicator/awesomeOscillator.ts'
import mtm from '../../third_party/klinechart-v10.1.0/src/extension/indicator/momentum.ts'
import roc from '../../third_party/klinechart-v10.1.0/src/extension/indicator/rateOfChange.ts'
import psy from '../../third_party/klinechart-v10.1.0/src/extension/indicator/psychologicalLine.ts'

const here = dirname(fileURLToPath(import.meta.url))
const outDir = resolve(here, '../../lkanvas-core/src/test/resources/fixtures/indicator')

/** 确定性 PRNG（mulberry32），保证 fixtures 可再生。 */
function mulberry32(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

interface Candle {
  timestamp: number
  open: number
  high: number
  low: number
  close: number
  volume?: number
  turnover?: number
}

/** 固定种子随机行情（F-04 风格合法 OHLCV）。 */
function genCandles(count: number, seed = 20261025, withVolume = true): Candle[] {
  const rand = mulberry32(seed)
  const candles: Candle[] = []
  let price = 100
  const start = Date.UTC(2026, 0, 4, 9, 30) // 周一起始，1 分钟周期
  for (let i = 0; i < count; i++) {
    const drift = (rand() - 0.5) * 2.0
    const open = price
    const close = open + drift
    const high = Math.max(open, close) + rand() * 0.8
    const low = Math.min(open, close) - rand() * 0.8
    const c: Candle = {
      timestamp: start + i * 60_000,
      open,
      high,
      low,
      close,
    }
    if (withVolume) {
      c.volume = Math.round(rand() * 10_000) + 1
      c.turnover = c.volume * (open + close) / 2
    }
    price = close
    candles.push(c)
  }
  return candles
}

/** 上游 calc 需要的 indicator 上下文（calcParams + figures）。 */
function ctx(template: any, params: number[]): any {
  const figures = template.regenerateFigures
    ? template.regenerateFigures(params, { calc: {} })
    : template.figures
  return { calcParams: params, figures }
}

interface Fixture {
  meta: { indicator: string; params: number[]; upstream: string; note?: string }
  candles: Candle[]
  /** 逐位置结果；对象内缺失键以 null 显式表达（对应 Kotlin 侧 Missing） */
  results: Array<Record<string, number | null>>
}

function run(name: string, template: any, params: number[], candles: Candle[], note?: string): Fixture {
  const raw = template.calc(candles, ctx(template, params))
  const keys: string[] = ctx(template, params).figures.map((f: any) => f.key)
  const results = raw.map((row: any) => {
    const out: Record<string, number | null> = {}
    for (const k of keys) {
      const v = row[k]
      out[k] = typeof v === 'number' && Number.isFinite(v) ? v : null
    }
    return out
  })
  return { meta: { indicator: name, params, upstream: 'klinecharts@10.1.0', note }, candles, results }
}

mkdirSync(outDir, { recursive: true })

const fixtures: Fixture[] = [
  run('MA', movingAverage, [5, 10, 30, 60], genCandles(120)),
  run('VOL', volume, [5, 10, 20], genCandles(120)),
  run('MACD', macd, [12, 26, 9], genCandles(120)),
  // T-12 均值/窗口指标组（I-04~I-12）
  run('EMA', ema, [6, 12, 20], genCandles(120)),
  run('SMA', sma, [12, 2], genCandles(120)),
  run('BOLL', boll, [20, 2], genCandles(120)),
  run('BBI', bbi, [3, 6, 12, 24], genCandles(120)),
  run('BIAS', bias, [6, 12, 24], genCandles(120)),
  run('AO', ao, [5, 34], genCandles(120)),
  run('MTM', mtm, [12, 6], genCandles(120)),
  run('ROC', roc, [12, 6], genCandles(120)),
  run('PSY', psy, [12, 6], genCandles(120)),
]

for (const f of fixtures) {
  const file = resolve(outDir, `${f.meta.indicator}.json`)
  writeFileSync(file, JSON.stringify(f, null, 1))
  console.log(`写出 ${file}（${f.candles.length} 根，${f.results.length} 行结果）`)
}
