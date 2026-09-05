package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceCategory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTest {

    /** DB 에서 실제로 오는 행 하나. 컬럼 이름·형식이 바뀌면 여기서 먼저 깨진다. */
    private val row = """
        {
          "id": 600,
          "category": "hospital",
          "source": "localdata",
          "name": "삼성펫클리닉",
          "tel": null,
          "address_road": "서울특별시 강남구 봉은사로 640, 에스빌딩 1층 (삼성동)",
          "address_jibun": "서울특별시 강남구 삼성동 163 에스빌딩",
          "lat": 37.5148490652155,
          "lng": 127.065338477586,
          "status": "open",
          "extra": {"zip": "06170", "sales_status": "영업/정상", "detail_status": "정상"},
          "source_updated_at": "2026-03-20T22:02:03+00:00"
        }
    """.trimIndent()

    private fun parse(json: String): Place = Json.decodeFromString(json)

    @Test
    fun `DB 행을 그대로 읽는다`() {
        val place = parse(row)
        assertEquals("삼성펫클리닉", place.name)
        assertEquals(PlaceCategory.HOSPITAL, place.placeCategory)
        assertEquals("영업/정상", place.salesStatus)
        assertTrue(place.hasCoordinates)
        assertNull(place.tel)
    }

    /**
     * ⚠️ 기준일에 시간대 변환을 하면 안 된다.
     *
     * 원본(`DAT_UPDT_PNT`)은 시간대가 없는 값인데 ETL 이 UTC 로 못박아 저장했다.
     * 22:02 를 KST 로 바꾸면 **다음 날로 넘어가 기준일이 하루 밀린다.** 날짜만 떼어 쓴다.
     */
    @Test
    fun `기준일은 날짜만 그대로 떼어 쓴다`() {
        assertEquals("2026-03-20", parse(row).sourceUpdatedDate)
    }

    @Test
    fun `기준일이 없는 행도 있다`() {
        val place = parse(row.replace("\"2026-03-20T22:02:03+00:00\"", "null"))
        assertNull(place.sourceUpdatedDate)
    }

    @Test
    fun `도로명이 없으면 지번으로 떨어진다`() {
        val place = parse(row.replace("\"서울특별시 강남구 봉은사로 640, 에스빌딩 1층 (삼성동)\"", "null"))
        assertEquals("서울특별시 강남구 삼성동 163 에스빌딩", place.address)
    }

    @Test
    fun `좌표가 없는 행은 길찾기를 걸지 않는다`() {
        // 영업중 5,470/5,474 는 좌표가 있지만 없는 행이 남아 있다 (D-51).
        val place = parse(row.replace("\"lat\": 37.5148490652155", "\"lat\": null"))
        assertFalse(place.hasCoordinates)
    }

    @Test
    fun `모르는 카테고리여도 앱이 죽지 않는다`() {
        val place = parse(row.replace("\"hospital\"", "\"cafe\""))
        assertNull(place.placeCategory)
        assertEquals("cafe", place.category)
    }

    @Test
    fun `extra 가 없어도 읽힌다`() {
        // 소스마다 extra 키가 다르다. 없는 키를 물어도 null 이어야 한다.
        val place = parse(row.replace("""{"zip": "06170", "sales_status": "영업/정상", "detail_status": "정상"}""", "null"))
        assertNull(place.salesStatus)
    }
}
