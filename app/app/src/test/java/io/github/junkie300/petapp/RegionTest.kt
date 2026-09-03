package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionTest {

    private fun region(
        code: String,
        level: Int,
        sido: String = "경기도",
        sigungu: String? = null,
        dong: String? = null,
        lat: Double? = null,
        lng: Double? = null,
    ) = Region(
        code = code,
        level = level,
        parentCode = null,
        fullName = listOfNotNull(sido, sigungu, dong).joinToString(" "),
        sidoName = sido,
        sigunguName = sigungu,
        dongName = dong,
        centerLat = lat,
        centerLng = lng,
    )

    @Test
    fun `단계별로 그 단계의 이름만 보여준다`() {
        assertEquals("경기도", region("4100000000", 1).shortName)
        assertEquals("성남시수정구", region("4113100000", 2, sigungu = "성남시수정구").shortName)
        assertEquals(
            "신흥동",
            region("4113110300", 3, sigungu = "성남시수정구", dong = "신흥동").shortName,
        )
    }

    @Test
    fun `이름이 비면 전체 이름으로 떨어진다`() {
        // 세종처럼 시군구가 없는 지역이 있다. 빈 칸을 그리지 않기 위한 안전장치다.
        assertEquals("경기도", region("4100000000", 2).shortName)
    }

    @Test
    fun `중심좌표는 둘 다 있어야 있는 것으로 본다`() {
        assertFalse(region("4113110300", 3).hasCenter)
        assertFalse(region("4113110300", 3, lat = 37.44).hasCenter)
        assertTrue(region("4113110300", 3, lat = 37.44, lng = 127.13).hasCenter)
    }
}
