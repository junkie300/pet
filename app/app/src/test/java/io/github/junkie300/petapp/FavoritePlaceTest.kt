package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.favorite.FavoritePlace
import org.junit.Assert.assertEquals
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

    @Test
    fun `주소도 전화도 없는 장소를 담을 수 있다`() {
        // 주소가 아예 빈 행이 있다 (D-51). 그래도 이름만으로 담을 수 있어야 한다.
        val favorite = FavoritePlace.from(place(road = null, jibun = null, tel = null))
        assertNull(favorite.address)
        assertNull(favorite.tel)
        assertEquals("삼성펫클리닉", favorite.name)
    }
}
