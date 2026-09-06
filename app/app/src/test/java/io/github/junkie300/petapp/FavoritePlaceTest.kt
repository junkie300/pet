package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.favorite.FavoritePlace
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritePlaceTest {

    private fun place(
        id: Long = 600,
        name: String = "삼성펫클리닉",
        road: String? = "서울특별시 강남구 봉은사로 640",
        jibun: String? = "서울특별시 강남구 삼성동 163",
        tel: String? = "02-541-7515",
    ) = Place(
        id = id,
        category = "hospital",
        source = "localdata",
        name = name,
        tel = tel,
        addressRoad = road,
        addressJibun = jibun,
        status = "open",
        extra = Json.parseToJsonElement("""{"sales_status":"영업/정상"}""").jsonObject,
        sourceUpdatedAt = "2026-01-14T22:02:03+00:00",
    )

    /**
     * ⚠️ 즐겨찾기는 id 만 담지 않는다.
     *
     * "이따 다시 볼 곳"이라 網이 없는 데서 열릴 확률이 높다. 이름·주소를 함께 베껴 두지 않으면
     * 그때 빈 줄이 뜬다. 이 테스트가 깨지면 오프라인에서 즐겨찾기가 비게 된다.
     */
    @Test
    fun `이름과 주소를 함께 베껴 둔다`() {
        val favorite = FavoritePlace.from(place(), savedAt = 1_000L)
        assertEquals(600L, favorite.placeId)
        assertEquals("삼성펫클리닉", favorite.name)
        assertEquals("서울특별시 강남구 봉은사로 640", favorite.address)
        assertEquals("02-541-7515", favorite.tel)
        assertEquals("hospital", favorite.category)
        assertEquals(1_000L, favorite.savedAt)
    }

    @Test
    fun `도로명이 없으면 지번을 베낀다`() {
        val favorite = FavoritePlace.from(place(road = null))
        assertEquals("서울특별시 강남구 삼성동 163", favorite.address)
    }

    /**
     * ⚠️ **출처와 기준일까지 베껴 둔다** (D-61).
     *
     * 상세 화면에는 출처·기준일이 의무 표기다 (spec.md §5.2 · §8). 스냅샷에 그게 없으면
     * 오프라인에서 즐겨찾기를 눌러도 상세를 그릴 수 없다 — 지어내면 거짓 표기가 되기 때문이다.
     */
    @Test
    fun `출처와 기준일까지 베껴 둔다`() {
        val favorite = FavoritePlace.from(place())
        assertEquals("localdata", favorite.source)
        assertEquals("2026-01-14T22:02:03+00:00", favorite.sourceUpdatedAt)

        val restored = favorite.toPlace()
        assertNotNull(restored)
        assertEquals("localdata", restored!!.source)
        assertEquals("2026-01-14", restored.sourceUpdatedDate)
        assertEquals("서울특별시 강남구 봉은사로 640", restored.addressRoad)
        assertEquals("영업/정상", restored.salesStatus)
    }

    /**
     * ⚠️ DB 버전 1 때 담은 즐겨찾기에는 출처가 없다. 그때는 상세를 그리지 **않는다.**
     * 화면을 채우려고 "공공데이터" 같은 말로 메우면, 그건 없는 사실을 적는 것이다.
     */
    @Test
    fun `출처가 없는 옛 스냅샷으로는 상세를 그리지 않는다`() {
        val v1 = FavoritePlace(
            placeId = 600,
            category = "hospital",
            name = "삼성펫클리닉",
            address = "서울특별시 강남구 봉은사로 640",
            tel = "02-541-7515",
            savedAt = 1_000L,
        )
        assertNull(v1.toPlace())
        // 그래도 목록에는 뜬다 — 담아 둔 게 안 보이는 즐겨찾기는 즐겨찾기가 아니다 (D-60).
        assertEquals("삼성펫클리닉", v1.name)
    }

    @Test
    fun `주소도 전화도 없는 장소를 담을 수 있다`() {
        // 주소가 아예 빈 행이 있다 (D-51). 그래도 이름만으로 담을 수 있어야 한다.
        val favorite = FavoritePlace.from(place(road = null, jibun = null, tel = null))
        assertNull(favorite.address)
        assertNull(favorite.tel)
        assertEquals("삼성펫클리닉", favorite.name)
    }
}
