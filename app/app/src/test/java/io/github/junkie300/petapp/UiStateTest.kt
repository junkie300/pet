package io.github.junkie300.petapp

import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.toUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    /**
     * spec.md §5.3 의 핵심 규칙. 이 테스트가 깨지면 "이 동네엔 병원이 없구나" 오해가 생긴다.
     */
    @Test
    fun `빈 목록은 성공이 아니라 Empty 다`() {
        assertTrue(emptyList<String>().toUiState() is UiState.Empty)
    }

    @Test
    fun `내용이 있으면 Success 로 감싼다`() {
        val state = listOf("가", "나").toUiState()
        assertTrue(state is UiState.Success)
        assertEquals(listOf("가", "나"), (state as UiState.Success).data)
    }
}
