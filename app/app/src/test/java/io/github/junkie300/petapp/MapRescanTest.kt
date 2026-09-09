package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.GeoPoint
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.data.nearestTo
import io.github.junkie300.petapp.ui.map.MapRescan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「이 지역에서 다시 검색」 (D-86) — **언제 뜨는가**와 **어디로 갈아타는가**.
 *
 * 경계 다각형이 없어서 둘 다 거리로 답한다. 그 거리가 곧 규칙이므로 값이 바뀌면 여기가 먼저 깨진다.
 */
class MapRescanTest {

    /** 강남구 삼성동 중심. */
    private val samseong = GeoPoint(37.5145, 127.0565)

    private fun dong(code: String, name: String, lat: Double?, lng: Double?) = Region(
        code = code,
        level = Region.LEVEL_DONG,
        fullName = name,
        sidoName = "서울특별시",
        centerLat = lat,
        centerLng = lng,
    )

    @Test
    fun `지역 중심에 있으면 뜨지 않는다`() {
        assertFalse(MapRescan.shouldOffer(samseong, samseong))
    }

    /** 위도 0.01도는 약 1.1km — 3km 문턱 안이다. */
    @Test
    fun `조금 밀어서는 뜨지 않는다`() {
        val nearby = GeoPoint(samseong.latitude + 0.01, samseong.longitude)
        assertFalse(MapRescan.shouldOffer(samseong, nearby))
    }

    /** 위도 0.05도는 약 5.6km — 넘는다. */
    @Test
    fun `한참 밀면 뜬다`() {
        val far = GeoPoint(samseong.latitude + 0.05, samseong.longitude)
        assertTrue(MapRescan.shouldOffer(samseong, far))
    }

    /** 중심좌표가 없는 지역이 남아 있다 (0단계 잔여분). 잴 수 없으면 묻지 않는다. */
    @Test
    fun `기준점이 없으면 뜨지 않는다`() {
        assertFalse(MapRescan.shouldOffer(null, samseong))
        assertFalse(MapRescan.shouldOffer(samseong, null))
    }

    @Test
    fun `가장 가까운 읍면동을 고른다`() {
        val regions = listOf(
            dong("1168010100", "서울특별시 강남구 역삼동", 37.4954, 127.0333),
            dong("1168010500", "서울특별시 강남구 삼성동", 37.5145, 127.0565),
            dong("1168010600", "서울특별시 강남구 대치동", 37.4940, 127.0629),
        )
        assertEquals("1168010500", regions.nearestTo(samseong)?.code)
    }

    /** 중심좌표가 없는 지역은 잴 수 없으므로 셈에서 뺀다 — 답으로 나와서는 안 된다. */
    @Test
    fun `중심좌표가 없는 지역은 고르지 않는다`() {
        val regions = listOf(
            dong("4113110300", "경기도 성남시 분당구 정자동", null, null),
            dong("1168010500", "서울특별시 강남구 삼성동", 37.5145, 127.0565),
        )
        assertEquals("1168010500", regions.nearestTo(samseong)?.code)
    }

    @Test
    fun `고를 것이 없으면 null 이다`() {
        assertNull(emptyList<Region>().nearestTo(samseong))
        assertNull(listOf(dong("4113110300", "정자동", null, null)).nearestTo(samseong))
    }
}
