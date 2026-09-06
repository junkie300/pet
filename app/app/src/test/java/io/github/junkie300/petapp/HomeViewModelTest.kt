package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.home.HomeCategory
import io.github.junkie300.petapp.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 홈이 지켜야 하는 규칙들. ViewModel 전체를 돌리려면 Supabase 클라이언트가 필요하므로
 * 여기서는 **상태 타입과 그리드 구성**만 본다. 나머지는 에뮬레이터에서 확인한다 (D-45).
 */
class HomeViewModelTest {

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
     * spec.md §5.3 의 규칙을 홈으로 확장한 것.
     * "아직 안 골랐다"(NoRegion)와 "못 불러왔다"(Failed)는 사용자가 할 일이 정반대다.
     */
    @Test
    fun `지역 미선택과 불러오기 실패는 다른 상태다`() {
        val states: List<HomeUiState> = listOf(
            HomeUiState.Loading,
            HomeUiState.NoRegion,
            HomeUiState.Failed("1144012000"),
            HomeUiState.Ready(yeonnam, UiState.Loading),
        )
        // 넷이 서로 다른 타입이어야 화면이 넷을 다르게 그릴 수 있다.
        assertEquals(4, states.map { it::class }.toSet().size)
        // 실패 상태는 재시도할 코드를 들고 있어야 한다. NoRegion 은 재시도할 것이 없다.
        assertEquals("1144012000", states.filterIsInstance<HomeUiState.Failed>().single().code)
        assertNotNull(states.filterIsInstance<HomeUiState.NoRegion>().single())
    }

    /**
     * ⚠️ 건수가 전부 0 이어도 Empty 가 아니라 Success 다.
     * 적재된 카테고리의 0 은 "이 동네엔 없습니다"라는 **유효한 답**이기 때문이다.
     * (적재 안 된 카테고리는 애초에 이 맵에 들어오지 않는다.)
     */
    @Test
    fun `건수 0 은 성공이다`() {
        val ready = HomeUiState.Ready(
            region = yeonnam,
            counts = UiState.Success(mapOf(PlaceCategory.HOSPITAL to 0)),
        )
        val counts = ready.counts
        assertEquals(UiState.Success(mapOf(PlaceCategory.HOSPITAL to 0)), counts)
        assertEquals(0, (counts as UiState.Success).data[PlaceCategory.HOSPITAL])
    }

    /**
     * 오프라인 배너는 **화면이 사본으로 그려졌을 때만** 뜬다 (spec.md §5.3).
     * 기본값이 null 이라는 것은 "지금 받은 값"이 기본이라는 뜻이다 — 배너가 잘못 뜨면
     * 멀쩡한 정보를 낡은 것으로 보이게 만든다.
     */
    @Test
    fun `사본으로 그린 화면만 받아 둔 시각을 들고 있다`() {
        val fresh = HomeUiState.Ready(yeonnam, UiState.Success(mapOf(PlaceCategory.HOSPITAL to 8)))
        assertNull(fresh.cachedAt)

        val cached = fresh.copy(cachedAt = 1_000L)
        assertEquals(1_000L, cached.cachedAt)
        // 값 자체는 사본이든 아니든 똑같이 그린다. 다른 것은 배너 한 줄뿐이다.
        assertEquals(fresh.counts, cached.counts)
    }

    /** 그리드는 6칸이고, 그중 입양만 places 가 아니다 (spec.md §5.1). */
    @Test
    fun `홈 그리드는 여섯 칸이고 입양만 places 밖이다`() {
        assertEquals(6, HomeCategory.entries.size)
        assertEquals(
            listOf(HomeCategory.ADOPTION),
            HomeCategory.entries.filter { it.place == null },
        )
        assertEquals(PlaceCategory.entries.size, HomeCategory.entries.count { it.place != null })
    }

    /** 지금 건수를 보여줄 수 있는 칸은 동물병원뿐이다. 나머지는 "준비 중"이다. */
    @Test
    fun `적재된 칸만 누를 수 있다`() {
        assertEquals(listOf(HomeCategory.HOSPITAL), HomeCategory.entries.filter { it.loaded })
        assertEquals(HomeCategory.HOSPITAL, HomeCategory.of(PlaceCategory.HOSPITAL))
    }
}
