package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.ui.region.HereState
import io.github.junkie300.petapp.ui.region.RegionPickerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-01 의 「현재 위치로」 (`spec.md §5.2`).
 *
 * ViewModel 전체를 돌리려면 Supabase 클라이언트와 안드로이드 [android.location.LocationManager]
 * 가 필요하므로, 여기서는 **화면이 지켜야 하는 규칙**만 본다. 위치를 실제로 잡는 것은
 * 실기기 몫이다 (D-71·D-81).
 */
class RegionHereTest {

    private val yeonnam = Region(
        code = "1144012000",
        level = Region.LEVEL_DONG,
        parentCode = "1144000000",
        fullName = "서울특별시 마포구 연남동",
        sidoName = "서울특별시",
        sigunguName = "마포구",
        dongName = "연남동",
    )

    /**
     * `spec.md §5.3` 의 규칙을 이 버튼으로 확장한 것. "못 잡았다"와 "근처에 없다"는
     * 사용자가 할 일이 다르므로 화면이 다르게 그릴 수 있어야 한다.
     */
    @Test
    fun `못 잡은 것과 못 찾은 것은 다른 상태다`() {
        val states: List<HereState> = listOf(
            HereState.Idle,
            HereState.Working,
            HereState.Failed,
            HereState.NotFound,
            HereState.Found(yeonnam),
        )
        assertEquals(5, states.map { it::class }.toSet().size)
    }

    /** 찾은 지역을 들고 있어야 화면이 그리로 나갈 수 있다. */
    @Test
    fun `찾았으면 지역을 들고 있다`() {
        val found = HereState.Found(yeonnam)
        assertEquals("1144012000", found.region.code)
    }

    /**
     * ⚠️ **명세가 못박은 것** — 권한을 거부해도 지역 선택은 그대로 되어야 한다 (`spec.md §5.2`).
     * 버튼 하나가 안 될 뿐이지 화면이 막히면 안 된다.
     */
    @Test
    fun `위치를 못 잡아도 지역 선택은 그대로 된다`() {
        val state = RegionPickerUiState(selectedDong = yeonnam, here = HereState.Failed)
        assertTrue(state.confirmable)
        assertTrue(state.copy(here = HereState.NotFound).confirmable)
    }

    /** 도는 동안에도 3단 드롭다운은 잠기지 않는다 — 위치를 기다리며 손으로 고를 수 있다. */
    @Test
    fun `위치를 잡는 동안에도 드롭다운은 열려 있다`() {
        val state = RegionPickerUiState(
            selectedSido = yeonnam,
            selectedSigungu = yeonnam,
            here = HereState.Working,
        )
        assertTrue(state.sigunguEnabled)
        assertTrue(state.dongEnabled)
    }
}
