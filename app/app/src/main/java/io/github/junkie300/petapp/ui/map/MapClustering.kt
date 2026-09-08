package io.github.junkie300.petapp.ui.map

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * 핀 묶기 (spec.md §5.2 "핀 50개 초과 시 클러스터링").
 *
 * 화면에서 겹치는 것을 푸는 일이므로 **위경도 거리가 아니라 화면 픽셀**로 묶는다. 같은 0.001도라도
 * 배율이 낮으면 한 점에 겹치고 높으면 한참 떨어져 보인다 — 배율을 빼고 묶으면 확대해도 뭉친 채로
 * 남거나, 축소했는데 흩어진 채로 남는다.
 *
 * ⚠️ **카카오맵 SDK 2.15.1 에는 클러스터러가 없다.** `Clusterer` 계열 클래스가 아예 없어서
 * (`LodLabelLayer` 는 배율별 표시/숨김이지 묶기가 아니다) 우리가 직접 묶는다. SDK 가 나중에
 * 클러스터러를 내놓으면 이 파일이 통째로 사라질 자리다.
 */
object MapClustering {

    /**
     * 이 수를 넘을 때만 묶는다 (`spec.md §5.2`). 그 아래에서는 핀 하나하나가 이름과 함께 보이는 편이
     * 낫다 — 8곳짜리 동네에서 `3` 이라고 적힌 원을 눌러 들어가게 만들 이유가 없다.
     */
    const val THRESHOLD = 50

    /** 묶는 격자 한 칸의 크기(dp). 핀 그림보다 넉넉해야 원끼리도 겹치지 않는다. */
    const val CELL_DP = 72

    /**
     * [pins] 를 [zoomLevel] 기준 화면 격자로 묶는다.
     *
     * @param cellPx 격자 한 칸(px). [CELL_DP] 를 화면 밀도로 환산한 값이다.
     * @return 핀 하나짜리 묶음도 그대로 [MapCluster] 로 돌려준다 — 부르는 쪽이 두 갈래를 따로
     *   그리지 않아도 되도록. 순서는 입력 순서를 따른다(같은 목록이면 같은 결과).
     */
    fun cluster(pins: List<MapPin>, zoomLevel: Int, cellPx: Int): List<MapCluster> {
        if (pins.size <= THRESHOLD) return pins.map { MapCluster(it.latitude, it.longitude, listOf(it)) }

        val cell = cellPx.coerceAtLeast(1)
        val world = worldSizePx(zoomLevel)
        val grouped = LinkedHashMap<Long, MutableList<MapPin>>()
        for (pin in pins) {
            grouped.getOrPut(cellKey(pin, world, cell)) { mutableListOf() } += pin
        }
        return grouped.values.map { group ->
            // 묶음의 자리는 무게중심이다. 격자 한가운데에 찍으면 핀이 없는 빈 땅에 원이 뜬다.
            MapCluster(
                latitude = group.sumOf { it.latitude } / group.size,
                longitude = group.sumOf { it.longitude } / group.size,
                pins = group,
            )
        }
    }

    /**
     * 이 핀들이 **실제로 갈라지는** 가장 낮은 배율. 아무리 당겨도 안 갈라지면 null 이다
     * (같은 건물에 여럿 있는 경우).
     *
     * ⚠️ **묶음을 펼칠 때 `fitMapPoints` 를 쓰면 안 된다** (D-83). 카카오맵 2.15.1 의 그 함수는
     * 점들이 화면에 **들어오게만** 하고 화면을 채우도록 당겨 주지는 않는다 — 방금 화면에 다 보이는
     * 묶음을 누른 것이므로 배율이 그대로고, 같은 원이 다시 그려진다. 눌러도 아무 일이 없는 것이다.
     * **어디까지 당겨야 갈라지는지는 우리가 안다** — 묶는 규칙이 여기 있으니 여기서 계산한다.
     *
     * @param fromZoom 지금 배율. 여기서 한 단계 위부터 찾는다.
     * @param maxZoom 지도가 허용하는 최대 배율.
     */
    fun zoomToSplit(pins: List<MapPin>, fromZoom: Int, cellPx: Int, maxZoom: Int): Int? {
        if (pins.size < 2) return null
        val cell = cellPx.coerceAtLeast(1)
        for (zoom in (fromZoom + 1)..minOf(maxZoom, MAX_ZOOM)) {
            val world = worldSizePx(zoom)
            if (pins.mapTo(HashSet()) { cellKey(it, world, cell) }.size > 1) return zoom
        }
        return null
    }

    /** 격자 좌표 두 개를 키 하나로. y 는 배율 22 에서도 2^30 을 넘지 않는다. */
    private fun cellKey(pin: MapPin, world: Double, cell: Int): Long {
        val x = floor(worldX(pin.longitude, world) / cell).toLong()
        val y = floor(worldY(pin.latitude, world) / cell).toLong()
        return x * KEY_STRIDE + y
    }

    /** 배율 [zoomLevel] 에서 세계 지도 한 장의 한 변(px). 카카오맵도 256px 타일을 쓴다. */
    private fun worldSizePx(zoomLevel: Int): Double =
        TILE_SIZE * Math.pow(2.0, zoomLevel.coerceIn(MIN_ZOOM, MAX_ZOOM).toDouble())

    private fun worldX(longitude: Double, world: Double): Double =
        (longitude + 180.0) / 360.0 * world

    /** 웹 메르카토르. 극점 근처는 무한대로 발산하므로 잘라 낸다 — 국내 좌표는 걸릴 일이 없다. */
    private fun worldY(latitude: Double, world: Double): Double {
        val lat = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val rad = lat * PI / 180.0
        val y = ln(tan(PI / 4.0 + rad / 2.0))
        return (0.5 - y / (2.0 * PI)) * world
    }

    private const val TILE_SIZE = 256.0
    private const val MIN_ZOOM = 1
    private const val MAX_ZOOM = 22
    private const val MAX_LATITUDE = 85.05112878
    private const val KEY_STRIDE = 1L shl 32
}

/**
 * 지도에 그릴 한 덩어리. 핀 하나면 그 핀을, 여럿이면 개수를 적은 원을 그린다.
 */
data class MapCluster(
    val latitude: Double,
    val longitude: Double,
    val pins: List<MapPin>,
) {
    val size: Int get() = pins.size

    /** 핀 하나짜리 묶음이면 그 핀. 여럿이면 null. */
    val single: MapPin? get() = pins.singleOrNull()

    /**
     * 묶음의 색. 섞여 있으면 가장 많은 카테고리를 따른다 — 색만으로 구분하지 않는다는 규칙
     * (`spec.md §6.5`)대로, 섞였다는 사실은 눌러서 펼친 뒤 핀 하나하나가 말한다.
     */
    val dominantCategory get() = pins.groupingBy { it.category }.eachCount()
        .maxByOrNull { it.value }!!.key

}
