package io.github.junkie300.petapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlin.coroutines.cancellation.CancellationException

/**
 * 오프라인 정책 한 곳 (spec.md §5.3 — "마지막 조회 결과를 캐시에서 표시").
 *
 * **사본을 먼저 그리고 뒤에서 갱신한다** (cache-first — D-79). 저장소가 값을 두 번 흘려보낸다:
 * 1. 받아 둔 사본 — 있으면 **즉시**. 배너는 아직 달지 않는다
 * 2. 서버 답 — 성공하면 새 값(사본도 갈아 끼운다), 실패하면 1의 사본을 **배너를 달아** 다시
 *
 * 서버를 먼저 부르고 실패해야 사본을 꺼내던 때에는, 圈外에서 화면이 뜨기까지 타임아웃(8초)을
 * 조회 수만큼 다 써야 했다 (홈은 지역 8초 + 건수 8초 = 16초).
 *
 * ⚠️ **캐시가 비어 있으면 실패는 실패다.** 빈 값을 성공으로 돌려주면 화면이
 * "이 동네엔 없습니다"를 그리는데, 우리는 없다는 것을 안 게 아니라 **모르는 것**이다.
 * §5.3 이 "데이터 없음"과 "불러오기 실패"를 절대 합치지 말라고 한 그 자리다.
 *
 * ⚠️ **[CancellationException] 을 먼저 잡아 다시 던진다.** 코루틴 취소도 예외로 오기 때문에,
 * Throwable 만 잡으면 화면을 떠난 뒤에도 캐시를 뒤지고 결과를 흘려보낸다.
 *
 * @param remote 서버 조회.
 * @param store 사본 남기기. 여기서 실패해도 **화면은 살아야 한다** — 방금 받은 값은 멀쩡하다.
 * @param cached 사본 꺼내기. 없으면 null 을 준다.
 */
internal fun <T> cachedThenFresh(
    remote: suspend () -> T,
    store: suspend (T) -> Unit,
    cached: suspend () -> Fetched<T>?,
): Flow<Fetched<T>> = flow {
    // 사본을 꺼내다 깨져도 서버 조회는 해봐야 한다. 캐시는 편의지 관문이 아니다.
    val copy = try {
        cached()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Throwable) {
        null
    }
    // ⚠️ 배너 없이 내보낸다. 이 값이 낡았는지는 **서버가 답해 봐야** 안다 (D-66).
    if (copy != null) emit(copy.stillAsking())

    try {
        val fresh = remote()
        runCatching { store(fresh) }
        emit(Fetched(fresh))
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: Throwable) {
        // 이제야 사본이 "마지막으로 아는 값"이 된다. 여기서 배너가 붙는다.
        emit(copy ?: throw failure)
    }
}

/**
 * 값 하나만 필요한 곳 — **마지막 값이 답이다.**
 *
 * 사본이 있어도 서버 답까지 기다리므로 cache-first 의 이득은 없다. 사용자가 그 자리에서
 * 결과를 기다리는 조회(S-01 의 드롭다운·검색)는 어차피 최신을 봐야 하므로 이쪽을 쓴다.
 */
internal suspend fun <T> fetchOrCached(
    remote: suspend () -> T,
    store: suspend (T) -> Unit,
    cached: suspend () -> Fetched<T>?,
): Fetched<T> = cachedThenFresh(remote, store, cached).last()
