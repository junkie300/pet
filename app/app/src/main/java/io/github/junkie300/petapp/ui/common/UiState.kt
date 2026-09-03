package io.github.junkie300.petapp.ui.common

/**
 * spec.md §5.3 — **"데이터 없음"과 "불러오기 실패"를 절대 같은 화면으로 처리하지 않는다.**
 * 사용자가 "이 동네엔 병원이 없구나"로 오해하면 앱 신뢰가 무너진다.
 * 그래서 두 상태를 타입으로 갈라 둔다. 하나로 합치고 싶어지면 이 주석을 먼저 읽을 것.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Failed(val cause: Throwable? = null) : UiState<Nothing>
}

/** 목록 결과를 상태로 바꾼다. 빈 목록은 성공이 아니라 [UiState.Empty] 다. */
fun <T> List<T>.toUiState(): UiState<List<T>> =
    if (isEmpty()) UiState.Empty else UiState.Success(this)
