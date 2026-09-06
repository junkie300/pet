package io.github.junkie300.petapp.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * 오프라인 정책 한 곳 (spec.md §5.3 — "마지막 조회 결과를 캐시에서 표시").
 *
 * 서버를 먼저 부르고, 성공하면 사본을 남기고, 실패하면 사본을 꺼낸다. 저장소 세 개가
 * 같은 순서를 지키도록 규칙을 여기 하나로 모은다.
 *
 * ⚠️ **캐시가 비어 있으면 실패는 실패다.** 빈 값을 성공으로 돌려주면 화면이
 * "이 동네엔 없습니다"를 그리는데, 우리는 없다는 것을 안 게 아니라 **모르는 것**이다.
 * §5.3 이 "데이터 없음"과 "불러오기 실패"를 절대 합치지 말라고 한 그 자리다.
 *
 * ⚠️ **[CancellationException] 을 먼저 잡아 다시 던진다.** 코루틴 취소도 예외로 오기 때문에,
 * Throwable 만 잡으면 화면을 떠난 뒤에도 캐시를 뒤지고 결과를 흘려보낸다.
 *
 * @param store 사본 남기기. 여기서 실패해도 **화면은 살아야 한다** — 방금 받은 값은 멀쩡하다.
 * @param cached 사본 꺼내기. 없으면 null 을 준다.
 */
internal suspend fun <T> fetchOrCached(
    remote: suspend () -> T,
    store: suspend (T) -> Unit,
    cached: suspend () -> Fetched<T>?,
): Fetched<T> = try {
    val fresh = remote()
    runCatching { store(fresh) }
    Fetched(fresh)
} catch (cancel: CancellationException) {
    throw cancel
} catch (failure: Throwable) {
    cached() ?: throw failure
}
