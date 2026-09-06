package io.github.junkie300.petapp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.junkie300.petapp.data.cache.CacheDao
import io.github.junkie300.petapp.data.cache.CachedCount
import io.github.junkie300.petapp.data.cache.CachedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * places 조회 (spec.md §4). 홈의 건수 · 목록 · 상세가 모두 여기를 지난다.
 *
 * 카테고리별로 화면을 나누지 않는다 — 한 테이블·한 화면 코드가 이 프로젝트의 재사용 전략이다 (D-26).
 *
 * 세 조회 모두 **성공하면 사본을 남기고 실패하면 사본을 꺼낸다** ([fetchOrCached]).
 * 그래서 반환값이 [Fetched] 다 — 화면이 오프라인 배너를 띄우려면 값이 어디서 왔는지 알아야 한다.
 */
class PlaceRepository(private val client: SupabaseClient, private val cache: CacheDao) {

    /**
     * 선택한 읍면동의 **영업중** 장소를 카테고리별로 센다 (S-00 홈).
     *
     * 카테고리마다 count 질의를 하나씩 던지고 동시에 기다린다. 행을 받아 코틀린에서 세는 방법이
     * 왕복은 한 번이라 솔깃하지만, PostgREST 의 기본 행 제한(1000)에 걸리면 **건수가 조용히 틀린다.**
     * head 요청은 본문 없이 Content-Range 헤더만 받으므로 전송량도 이쪽이 작다.
     *
     * 적재 안 된 카테고리는 아예 묻지 않는다 — [PlaceCategory.loaded] 참고.
     */
    suspend fun countsByCategory(regionCode: String): Fetched<Map<PlaceCategory, Int>> = fetchOrCached(
        remote = {
            coroutineScope {
                PlaceCategory.loadedEntries
                    .map { category -> async { category to countIn(regionCode, category) } }
                    .awaitAll()
                    .toMap()
            }
        },
        store = { counts ->
            val now = System.currentTimeMillis()
            cache.putCounts(counts.map { (category, n) -> CachedCount(regionCode, category.dbValue, n, now) })
        },
        cached = {
            val rows = cache.countsIn(regionCode)
            if (rows.isEmpty()) {
                null
            } else {
                // 모르는 카테고리가 남아 있을 수 있다 (ENUM 이 늘었다가 줄면). 그건 버린다.
                val counts = rows.mapNotNull { row ->
                    PlaceCategory.fromDbValue(row.category)?.let { it to row.count }
                }.toMap()
                Fetched(counts, rows.minOf { it.cachedAt })
            }
        },
    )

    /**
     * 한 읍면동의 한 카테고리 목록 (S-02 목록).
     *
     * 이름 순으로 준다. 거리 순이 더 자연스럽지만 그건 현재 위치가 있어야 하고,
     * 이 앱의 기준점은 현재 위치가 아니라 **내가 고른 지역**이다 (D-24).
     */
    suspend fun listByRegion(
        regionCode: String,
        category: PlaceCategory,
        limit: Int = LIST_LIMIT,
    ): Fetched<List<Place>> = fetchOrCached(
        remote = {
            query {
                filter {
                    eq("region_code", regionCode)
                    eq("category", category.dbValue)
                    eq("status", STATUS_OPEN)
                }
                order("name", Order.ASCENDING)
                limit(limit.toLong())
            }
        },
        store = { places ->
            val now = System.currentTimeMillis()
            cache.replacePlacesIn(
                regionCode = regionCode,
                category = category.dbValue,
                places = places.map { CachedPlace.from(it, regionCode, now) },
            )
        },
        cached = {
            val rows = cache.placesIn(regionCode, category.dbValue)
            // 빈 사본은 "없다"가 아니라 "모른다"다. 실패로 둔다.
            if (rows.isEmpty()) null else Fetched(rows.map { it.toPlace() }, rows.minOf { it.cachedAt })
        },
    )

    /** 장소 상세 (S-03). 사라진 id 일 수 있으므로 null 이 날 수 있다. */
    suspend fun byId(id: Long): Fetched<Place?> = fetchOrCached(
        remote = { query { filter { eq("id", id) } }.firstOrNull() },
        store = { place ->
            if (place == null) {
                // 서버가 "그런 장소 없다"고 답했다. 사본을 남겨 두면 오프라인에서 되살아난다.
                cache.deletePlace(id)
            } else {
                // 상세 조회는 region_code 를 주지 않는다. 목록 캐시가 이미 아는 값을 지우지 않는다.
                val regionCode = cache.placeById(id)?.regionCode
                cache.putPlaces(listOf(CachedPlace.from(place, regionCode, System.currentTimeMillis())))
            }
        },
        cached = { cache.placeById(id)?.let { Fetched(it.toPlace(), it.cachedAt) } },
    )

    private suspend fun countIn(regionCode: String, category: PlaceCategory): Int =
        withContext(Dispatchers.IO) {
            client.from(TABLE).select(columns = Columns.raw("id")) {
                head = true
                count(Count.EXACT)
                filter {
                    eq("region_code", regionCode)
                    eq("category", category.dbValue)
                    eq("status", STATUS_OPEN)
                }
            }.countOrNull()?.toInt() ?: 0
        }

    private suspend fun query(
        build: io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder.() -> Unit,
    ): List<Place> = withContext(Dispatchers.IO) {
        client.from(TABLE).select(columns = Columns.raw(COLUMNS)) { build() }.decodeList<Place>()
    }

    companion object {
        private const val TABLE = "places"

        /** 폐업·휴업은 세지도, 보여주지도 않는다. plan.md 1단계 완료 기준이다. */
        private const val STATUS_OPEN = "open"

        /**
         * 한 읍면동의 한 카테고리가 이보다 많을 수 없다 (전국 최다 읍면동도 수십 곳이다).
         * PostgREST 기본 상한(1000)에 조용히 잘리지 않도록 우리가 먼저 정해 둔다.
         */
        const val LIST_LIMIT = 300

        /** 앱이 실제로 쓰는 컬럼만. `geom` 은 바이너리라 받지 않는다. */
        const val COLUMNS =
            "id,category,source,name,tel,address_road,address_jibun,lat,lng,status,extra,source_updated_at"
    }
}
