package io.lkanvas.core.viewport

import io.lkanvas.core.data.Candle
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewportTest {

    private fun candle(ts: Long, price: Double) =
        Candle(ts, price, price + 1, price - 1, price, 100.0)

    private fun candles(n: Int, startTs: Long = 0L): List<Candle> =
        (0 until n).map { candle(startTs + it, 10.0 + it) }

    private fun Viewport.setup(width: Double, dataCount: Int) {
        layout(width, dataCount)
    }

    // ---------- A-12：数据↔像素往返 ----------

    @Test
    fun `随机位置数据索引到像素再回索引误差可忽略`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 1000) }
        val rand = Random(42)
        repeat(200) {
            val idx = rand.nextDouble(0.0, 1000.0)
            val x = vp.candleCenterX(idx)
            val back = vp.logicalIndexAt(x)
            assertTrue(abs(back - idx) < 1e-9, "往返误差 ${abs(back - idx)}")
        }
    }

    @Test
    fun `滚动缩放后往返依旧成立`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 500) }
        vp.scrollBy(123.4, 500)
        vp.zoomBy(1.7, anchorX = 180.0, dataCount = 500)
        vp.scrollBy(-55.5, 500)
        val rand = Random(7)
        repeat(100) {
            val idx = rand.nextDouble(450.0, 500.0)
            assertEquals(idx, vp.logicalIndexAt(vp.candleCenterX(idx)), 1e-9)
        }
    }

    @Test
    fun `时间戳定位命中正确索引`() {
        val data = candles(100) // ts 0..99
        assertEquals(37, Viewport.indexOfTimestamp(37L, data))
        assertEquals(54, Viewport.indexOfTimestamp(54L, data))
        // 超出范围的时间戳：返回不大于目标的最近索引
        assertEquals(99, Viewport.indexOfTimestamp(150L, data))
        assertEquals(null, Viewport.indexOfTimestamp(-10L, data))
    }

    // ---------- A-13：前插锚点保持 ----------

    @Test
    fun `前插一千根后同时间戳像素偏移不超过一px`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 200) }
        vp.scrollToIndex(100.0, 200) // 用户正在浏览历史区域
        // 原始数据 ts 1000..1199，锚点取用户正在看的区域附近
        val original = candles(200, startTs = 1000)
        val anchorTs = 1150L
        val xBefore = vp.candleCenterX(Viewport.indexOfTimestamp(anchorTs, original)!!.toDouble())

        vp.onPrepended(1000)
        // 前插后数据 ts 0..1199（补充 0..999），原锚点索引平移 1000
        val dataAfter = candles(1200, startTs = 0)
        val idxAfter = Viewport.indexOfTimestamp(anchorTs, dataAfter)!!
        assertEquals(1150, idxAfter, "索引平移 1000")
        val xAfter = vp.candleCenterX(idxAfter.toDouble())
        assertTrue(abs(xAfter - xBefore) <= 1.0, "锚点像素偏移 ${abs(xAfter - xBefore)} px")
    }

    @Test
    fun `浏览历史时追加新行情不强制拉回末端`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 500) }
        vp.scrollToIndex(100.0, 500) // 浏览历史
        val xBefore = vp.candleCenterX(100.0)
        vp.onAppended(n = 10, dataCountBefore = 500, followLatest = true)
        assertEquals(xBefore, vp.candleCenterX(100.0), 1e-9, "追加不得移动用户视口")
    }

    @Test
    fun `位于末端且开启跟随时追加自动跟随`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 500) }
        assertTrue(vp.isAtLatest(500))
        vp.onAppended(n = 10, dataCountBefore = 500, followLatest = true)
        assertTrue(vp.isAtLatest(510), "跟随模式下保持在最新")
    }

    // ---------- A-14：缩放锚点与极限 ----------

    @Test
    fun `触点缩放锚点逻辑索引稳定`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 1000) }
        vp.scrollToIndex(700.0, 1000)
        val anchorX = 200.0
        val idxBefore = vp.logicalIndexAt(anchorX)
        vp.zoomBy(1.8, anchorX, 1000)
        val idxAfter = vp.logicalIndexAt(anchorX)
        assertEquals(idxBefore, idxAfter, 1e-6, "触点处内容不应漂移")
        vp.zoomBy(1.0 / 1.8, anchorX, 1000)
        assertEquals(idxBefore, vp.logicalIndexAt(anchorX), 1e-6)
    }

    @Test
    fun `末根缩放保持末根可见位置`() {
        val vp = Viewport(barSpacing = 8.0, rightOffsetSlots = 2).apply { setup(360.0, 400) }
        val lastX = vp.candleCenterX(399.0)
        vp.zoomAtLatest(2.0, 400)
        // 末根锚点缩放后末根仍在原像素位置附近（作为锚点）
        assertEquals(lastX, vp.candleCenterX(399.0), 1.0)
    }

    @Test
    fun `极限缩放被钳制在最小最大barSpacing`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 1000) }
        repeat(20) { vp.zoomBy(0.1, 180.0, 1000) }
        assertEquals(vp.minBarSpacing, vp.barSpacing, 1e-9)
        repeat(20) { vp.zoomBy(10.0, 180.0, 1000) }
        assertEquals(vp.maxBarSpacing, vp.barSpacing, 1e-9)
        // 极限下往返依旧成立
        val idx = vp.logicalIndexAt(100.0)
        assertEquals(idx, vp.logicalIndexAt(vp.candleCenterX(idx)), 1e-6)
    }

    @Test
    fun `零尺寸再恢复不崩溃且状态有限`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 100) }
        vp.layout(0.0, 100)
        assertTrue(vp.contentWidth == 0.0)
        vp.scrollBy(50.0, 100)
        vp.zoomBy(1.5, 0.0, 100)
        assertTrue(vp.barSpacing.isFinite() && vp.scrollIndex.isFinite())
        vp.layout(360.0, 100)
        assertTrue(vp.isAtLatest(100))
        assertEquals(50.0, vp.candleCenterX(50.0).let { vp.logicalIndexAt(it) }, 1e-6)
    }

    @Test
    fun `边界滚动不越界`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 100) }
        vp.scrollBy(-100000.0, 100)
        assertTrue(vp.scrollIndex >= 0.0 - 1e-9 || vp.scrollIndex == 0.0)
        val minReached = vp.scrollIndex
        vp.scrollBy(-1000.0, 100)
        assertEquals(minReached.coerceAtLeast(-0.0), vp.scrollIndex.coerceAtLeast(-0.0), 1e-9)
        vp.scrollBy(100000.0, 100)
        // 右边界：末槽位不超过右缘
        assertTrue(vp.isAtLatest(100))
    }

    @Test
    fun `数据少于可视区时贴左不产生负滚动`() {
        val vp = Viewport(barSpacing = 8.0).apply { setup(360.0, 3) }
        assertTrue(vp.scrollIndex <= 0.0 + 1e-9)
        val x2 = vp.candleCenterX(2.0)
        assertTrue(x2 in 0.0..360.0)
    }
}
