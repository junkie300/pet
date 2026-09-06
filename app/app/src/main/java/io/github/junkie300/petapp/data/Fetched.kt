package io.github.junkie300.petapp.data

/**
 * 조회 결과 하나와 **그 값이 어디서 왔는지**.
 *
 * `spec.md §5.3` 은 오프라인일 때 "마지막 조회 결과를 캐시에서 표시 + 상단에 오프라인 배너"를
 * 요구한다. 배너를 띄우려면 화면이 **지금 받은 값과 꺼내 온 값을 구분**할 수 있어야 하는데,
 * 저장소가 값만 돌려주면 그 구분이 사라진다. 그래서 값에 출처를 붙여서 돌려준다.
 *
 * [cachedAt] 이 null 이면 방금 서버에서 받은 값이고, 아니면 그 시각에 받아 둔 값이다.
 */
data class Fetched<out T>(val data: T, val cachedAt: Long? = null) {

    val fromCache: Boolean get() = cachedAt != null

    fun <R> map(transform: (T) -> R): Fetched<R> = Fetched(transform(data), cachedAt)
}

/** 화면 하나가 여러 조회를 합쳐 그릴 때, **가장 오래된 캐시 시각**이 그 화면의 기준이다. */
fun oldestCachedAt(vararg values: Long?): Long? = values.filterNotNull().minOrNull()
