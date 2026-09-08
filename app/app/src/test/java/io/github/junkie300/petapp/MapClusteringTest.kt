package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.map.MapClustering
import io.github.junkie300.petapp.ui.map.MapPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 핀 묶기 (D-78). 화면이 없어도 확인할 수 있는 부분을 전부 여기서 잡는다 — 지도는 ARM 실기기가
 * 있어야 열리므로(D-71), 눈으로 보기 전에 규칙이 맞는지는 이 테스트가 대신 말해 준다.
 */
class MapClusteringTest {

    /** 강남역 부근. 실제 데이터에서 핀이 가장 빽빽한 동네다. */
    private val baseLat = 37.4979
    private val baseLng = 127.0276

    /** xxhdpi(=3배) 기준 격자 한 칸. */
    private val cellPx = MapClustering.CELL_DP * 3

    private fun pins(count: Int, spreadDeg: Double = 0.02): List<MapPin> = List(count) { i ->
        // 격자에 고르게 흩는다. 한 줄로 세우면 y 방향 묶기가 검증되지 않는다.
        val row = i / 10
        val col = i % 10
        MapPin(
            placeId = i.toLong(),
            name = "장소 $i",
            latitude = baseLat + row * spreadDeg / 10,
            longitude = baseLng + col * spreadDeg / 10,
            category = PlaceCategory.HOSPITAL,
        )
    }

    /**
     * `spec.md §5.2` 는 **핀 50개 초과 시** 묶으라고 한다. 그 아래에서는 하나하나가 이름과 함께
     * 보이는 편이 낫다 — 8곳짜리 동네에서 `3` 이라고 적힌 원을 눌러 들어가게 만들 이유가 없다.
     */
    @Test
    fun `50개까지는 묶지 않는다`() {
        val result = MapClustering.cluster(pins(50), zoomLevel = 12, cellPx = cellPx)

        assertEquals(50, result.size)
        assertTrue(result.all { it.size == 1 })
        assertNotNull(result.first().single)
    }

    @Test
    fun `50개를 넘으면 가까운 것끼리 묶인다`() {
        val result = MapClustering.cluster(pins(60), zoomLevel = 12, cellPx = cellPx)

        assertTrue("60개가 그대로 남았다면 묶이지 않은 것이다", result.size < 60)
        assertTrue("묶음이 하나라도 있어야 한다", result.any { it.size > 1 })
    }

    /**
     * 무엇을 하더라도 **핀이 사라지거나 두 번 나오면 안 된다.** 지도의 개수와 시트의 개수가
     * 어긋나는 순간 어느 쪽이 맞는지 화면만 보고는 알 수 없다.
     */
    @Test
    fun `모든 핀이 정확히 한 묶음에 들어간다`() {
        val pins = pins(120)

        for (zoom in listOf(8, 12, 14, 17, 20)) {
            val ids = MapClustering.cluster(pins, zoom, cellPx).flatMap { it.pins }.map { it.placeId }
            assertEquals("배율 $zoom", pins.size, ids.size)
            assertEquals("배율 $zoom · 중복", pins.map { it.placeId }.toSet(), ids.toSet())
        }
    }

    /** 확대할수록 잘게 갈라져야 한다. 안 그러면 묶음을 눌러도 영영 펼쳐지지 않는다. */
    @Test
    fun `확대하면 더 잘게 갈라진다`() {
        val pins = pins(120)

        val wide = MapClustering.cluster(pins, zoomLevel = 10, cellPx = cellPx).size
        val close = MapClustering.cluster(pins, zoomLevel = 18, cellPx = cellPx).size

        assertTrue("배율 10: $wide · 배율 18: $close", close > wide)
        assertEquals("충분히 확대하면 결국 하나씩 남는다", pins.size, close)
    }

    /** 묶음의 자리는 무게중심이다. 격자 한가운데에 찍으면 핀이 없는 빈 땅에 원이 뜬다. */
    @Test
    fun `묶음의 좌표는 속한 핀들의 무게중심이다`() {
        val pins = pins(60, spreadDeg = 0.0001)

        val result = MapClustering.cluster(pins, zoomLevel = 10, cellPx = cellPx)

        assertEquals(1, result.size)
        assertEquals(pins.map { it.latitude }.average(), result[0].latitude, 1e-9)
        assertEquals(pins.map { it.longitude }.average(), result[0].longitude, 1e-9)
    }

    /**
     * 묶음을 누르면 **갈라지는 배율까지** 당긴다 (D-83). 한 단계 올려 봐야 그대로인 묶음이
     * 대부분이라, "지금보다 한 단계"로는 눌러도 아무 일이 없다.
     */
    @Test
    fun `갈라지는 배율을 찾아 준다`() {
        val all = pins(60)
        val big = MapClustering.cluster(all, zoomLevel = 12, cellPx = cellPx).maxBy { it.size }

        val zoom = MapClustering.zoomToSplit(big.pins, fromZoom = 12, cellPx = cellPx, maxZoom = 21)

        assertNotNull("흩어진 묶음은 언젠가 갈라진다", zoom)
        assertTrue("지금보다 더 당겨야 한다", zoom!! > 12)
        // 그 배율에서 이 묶음은 더 이상 한 덩어리가 아니다 — 눌렀는데 같은 원이 다시 뜨면 안 된다.
        assertTrue(
            "갈라져야 한다",
            MapClustering.cluster(all, zoom, cellPx).none { it.pins.containsAll(big.pins) },
        )
        // **가장 낮은** 배율이어야 한 번에 너무 당기지 않는다 — 직전 배율까지는 한 덩어리다.
        assertTrue(
            "한 단계 덜 당긴 자리에서는 그대로여야 한다",
            MapClustering.cluster(all, zoom - 1, cellPx).any { it.pins.containsAll(big.pins) },
        )
    }

    /** 같은 건물에 여럿 있으면 아무리 확대해도 갈라지지 않는다. 그때는 카메라를 달리 움직인다. */
    @Test
    fun `한 점에 겹친 묶음은 갈라지는 배율이 없다`() {
        val same = List(60) { i ->
            MapPin(i.toLong(), "장소 $i", baseLat, baseLng, PlaceCategory.HOSPITAL)
        }

        val result = MapClustering.cluster(same, zoomLevel = 21, cellPx = cellPx)

        assertEquals(1, result.size)
        assertNull("여럿이므로 핀 하나짜리가 아니다", result[0].single)
        assertNull(
            "아무리 당겨도 안 갈라진다 — 부르는 쪽이 카메라를 달리 움직여야 한다",
            MapClustering.zoomToSplit(same, fromZoom = 12, cellPx = cellPx, maxZoom = 21),
        )
    }

    /**
     * 섞인 묶음의 색은 가장 많은 카테고리를 따른다. 색만으로 구분하지 않는다는 규칙
     * (`spec.md §6.5`)대로, 섞였다는 사실은 펼친 뒤 핀 하나하나가 말한다.
     */
    @Test
    fun `섞인 묶음은 가장 많은 카테고리를 따른다`() {
        val mixed = List(60) { i ->
            MapPin(
                placeId = i.toLong(),
                name = "장소 $i",
                latitude = baseLat,
                longitude = baseLng,
                category = if (i % 3 == 0) PlaceCategory.GROOMING else PlaceCategory.HOSPITAL,
            )
        }

        val result = MapClustering.cluster(mixed, zoomLevel = 14, cellPx = cellPx)

        assertEquals(1, result.size)
        assertEquals(PlaceCategory.HOSPITAL, result[0].dominantCategory)
    }

    /** 빈 목록에 묶을 것은 없다. 화면이 이 경우를 따로 그리므로 예외가 아니라 빈 목록으로 답한다. */
    @Test
    fun `핀이 없으면 묶음도 없다`() {
        assertTrue(MapClustering.cluster(emptyList(), zoomLevel = 14, cellPx = cellPx).isEmpty())
    }
}
