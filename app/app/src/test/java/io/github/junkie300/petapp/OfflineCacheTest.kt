package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.Fetched
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.data.cache.CachedPlace
import io.github.junkie300.petapp.data.cache.CachedRegion
import io.github.junkie300.petapp.data.oldestCachedAt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오프라인 캐시가 지켜야 하는 것들 (spec.md §5.3).
 *
 * Room 자체는 기기가 있어야 돌아가므로 여기서는 **표에 담고 꺼내는 변환**과
 * **출처 표시(Fetched)** 만 본다. 실제 저장은 에뮬레이터에서 확인한다 (D-45).
 */
class OfflineCacheTest {

    private fun extra(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val place = Place(
        id = 600,
        category = "hospital",
        source = "localdata",
        name = "삼성펫클리닉",
        tel = "02-541-7515",
        addressRoad = "서울특별시 강남구 봉은사로 640",
        addressJibun = "서울특별시 강남구 삼성동 163",
        lat = 37.5145,
        lng = 127.0561,
        status = "open",
        extra = extra("""{"sales_status":"영업/정상"}"""),
        sourceUpdatedAt = "2026-01-14T22:02:03+00:00",
    )

    private val yeonnam = Region(
        code = "1144012000",
        level = Region.LEVEL_DONG,
        parentCode = "1144000000",
        fullName = "서울특별시 마포구 연남동",
        sidoName = "서울특별시",
        sigunguName = "마포구",
        dongName = "연남동",
        centerLat = 37.5637,
        centerLng = 126.9256,
    )

    /** 캐시를 거쳐도 화면이 그리는 값이 하나도 바뀌면 안 된다 — 특히 출처와 기준일이다. */
    @Test
    fun `장소를 캐시에 담았다 꺼내면 그대로다`() {
        val restored = CachedPlace.from(place, regionCode = "1168010500", cachedAt = 1_000L).toPlace()
        assertEquals(place, restored)
        assertEquals("영업/정상", restored.salesStatus)
        assertEquals("2026-01-14", restored.sourceUpdatedDate)
    }

    /** extra 는 소스마다 키가 달라 컬럼으로 펴지 않고 원문 JSON 으로 담는다. 없으면 없는 대로. */
    @Test
    fun `extra 가 없는 장소도 담긴다`() {
        val bare = place.copy(extra = null, tel = null, lat = null, lng = null)
        val cached = CachedPlace.from(bare, regionCode = null, cachedAt = 1_000L)
        assertNull(cached.extra)
        assertNull(cached.regionCode) // 상세만 열어 담은 행은 읍면동을 모른다
        assertEquals(bare, cached.toPlace())
    }

    @Test
    fun `지역을 캐시에 담았다 꺼내면 그대로다`() {
        assertEquals(yeonnam, CachedRegion.from(yeonnam, cachedAt = 1_000L).toRegion())
    }

    /**
     * ⚠️ 화면 하나가 여러 조회를 합쳐 그릴 때는 **가장 오래된 것**이 그 화면의 기준이다.
     * 지역 이름은 방금 받았는데 건수는 어제 것이면, 사용자가 보는 화면은 어제 것이다.
     */
    @Test
    fun `여러 조회를 합치면 가장 오래된 시각이 화면의 기준이다`() {
        assertEquals(1_000L, oldestCachedAt(3_000L, 1_000L))
        assertEquals(1_000L, oldestCachedAt(null, 1_000L))
        // 전부 지금 받은 값이면 배너를 띄우지 않는다.
        assertNull(oldestCachedAt(null, null))
    }

    @Test
    fun `방금 받은 값과 꺼내 온 값이 구분된다`() {
        assertFalse(Fetched("지금").fromCache)
        assertTrue(Fetched("사본", cachedAt = 1_000L).fromCache)
        // map 은 값만 바꾸고 출처는 그대로 들고 간다 (byCodes 가 순서를 되돌릴 때 쓴다).
        assertEquals(Fetched(2, cachedAt = 1_000L), Fetched("사본", cachedAt = 1_000L).map { it.length })
    }
}
