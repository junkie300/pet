package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.map.MapCluster
import io.github.junkie300.petapp.ui.map.MapPin
import io.github.junkie300.petapp.ui.map.mapNames
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 같은 자리 이름 겹침 (D-85). 실기기에서만 드러난 증상이지만 **규칙 자체는 여기서 잡힌다** —
 * 무엇을 몇 개 그릴지는 지도 없이도 셀 수 있다.
 */
class MapNamesTest {

    private fun pin(id: Long, name: String, lat: Double, lng: Double) =
        MapPin(id, name, lat, lng, PlaceCategory.HOSPITAL)

    private fun singles(vararg pins: MapPin) = pins.map { MapCluster(it.latitude, it.longitude, listOf(it)) }

    @Test
    fun `좌표가 다르면 이름을 하나씩 그린다`() {
        val names = mapNames(
            singles(
                pin(1, "가 동물병원", 37.5000, 127.0000),
                pin(2, "나 동물병원", 37.5010, 127.0010),
            ),
        )
        assertEquals(listOf("가 동물병원", "나 동물병원"), names.map { it.name })
        assertEquals(listOf(0, 0), names.map { it.hiddenCount })
    }

    /** 역삼동 `온숲` 두 곳이 이 경우다 — 좌표가 글자까지 같다. */
    @Test
    fun `좌표가 같으면 이름을 하나만 그리고 나머지 수를 남긴다`() {
        val names = mapNames(
            singles(
                pin(1, "24시 온숲 동물의료센터", 37.4979, 127.0276),
                pin(2, "온숲 영상·외과 동물의료센터", 37.4979, 127.0276),
            ),
        )
        assertEquals(1, names.size)
        assertEquals("24시 온숲 동물의료센터", names.first().name)
        assertEquals(1, names.first().hiddenCount)
        assertEquals(37.4979, names.first().latitude, 0.0)
        assertEquals(127.0276, names.first().longitude, 0.0)
    }

    /**
     * 아주 가깝지만 **다른** 좌표는 묶지 않는다. 배율을 올리면 떨어져 보이는 것들이라,
     * 여기서 반올림해 묶으면 확대해도 이름이 하나로 남는다.
     */
    @Test
    fun `1m 쯤 떨어진 두 곳은 각각 그린다`() {
        val names = mapNames(
            singles(
                pin(1, "가 동물병원", 37.49790, 127.02760),
                pin(2, "나 동물병원", 37.49791, 127.02760),
            ),
        )
        assertEquals(2, names.size)
    }

    /** 여럿짜리 묶음은 개수를 적은 원이 대신한다 — 이름을 붙이지 않는다 (D-78). */
    @Test
    fun `묶음에는 이름을 붙이지 않는다`() {
        val cluster = MapCluster(
            37.5, 127.0,
            listOf(pin(1, "가 동물병원", 37.5, 127.0), pin(2, "나 동물병원", 37.501, 127.001)),
        )
        assertEquals(emptyList<String>(), mapNames(listOf(cluster)).map { it.name })
    }

    @Test
    fun `핀이 없으면 이름도 없다`() {
        assertEquals(0, mapNames(emptyList()).size)
    }
}
