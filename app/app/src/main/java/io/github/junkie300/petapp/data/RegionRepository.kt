package io.github.junkie300.petapp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.junkie300.petapp.data.cache.CacheDao
import io.github.junkie300.petapp.data.cache.CachedRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S-01 지역 선택이 쓰는 유일한 데이터 출입구 (spec.md §4).
 *
 * 앱은 공공 API 를 직접 부르지 않는다. regions 는 ETL 이 이미 정규화해 둔 결과다.
 *
 * **한 번이라도 불러온 지역은 사본으로 남는다** ([CachedRegion]). 지역 이름이 없으면
 * 오프라인에서 홈의 지역 칩부터 비어 아무 화면도 그릴 수 없기 때문에, 장소보다 먼저 캐시한다.
 */
class RegionRepository(private val client: SupabaseClient, private val cache: CacheDao) {

    suspend fun sidoList(): Fetched<List<Region>> = fetchRegions(
        remote = { query { filter { eq("level", Region.LEVEL_SIDO) } } },
        cached = { cache.regionsByLevel(Region.LEVEL_SIDO) },
    )

    /** 상위 지역의 바로 아래 단계. 시도 → 시군구, 시군구 → 읍면동. */
    suspend fun children(parentCode: String): Fetched<List<Region>> = fetchRegions(
        remote = { query { filter { eq("parent_code", parentCode) } } },
        cached = { cache.regionChildren(parentCode) },
    )

    /**
     * 지역명 직접 검색. 읍면동만 대상으로 한다 — 사용자가 "연남동"처럼 목적지를 바로 치는 경우다.
     * full_name 에 트라이그램 인덱스가 있어 ilike 가 인덱스를 탄다.
     *
     * ⚠️ 오프라인이면 **이미 받아 둔 읍면동 안에서만** 찾는다. 전국 5,067개가 다 들어 있지
     * 않으므로 결과가 적을 수 있고, 그래서 화면이 오프라인 배너를 함께 띄운다.
     */
    suspend fun searchDong(keyword: String, limit: Int = SEARCH_LIMIT): Fetched<List<Region>> {
        val q = keyword.trim()
        if (q.length < MIN_SEARCH_LENGTH) return Fetched(emptyList())
        return fetchRegions(
            remote = {
                query {
                    filter {
                        eq("level", Region.LEVEL_DONG)
                        ilike("full_name", "%$q%")
                    }
                    limit(limit.toLong())
                }
            },
            cached = { cache.searchRegions(q, Region.LEVEL_DONG, limit) },
        )
    }

    /** 최근 선택 지역을 코드로 되살릴 때 쓴다. 저장해 둔 코드가 사라졌을 수도 있으므로 결과가 빌 수 있다. */
    suspend fun byCodes(codes: List<String>): Fetched<List<Region>> {
        if (codes.isEmpty()) return Fetched(emptyList())
        return fetchRegions(
            remote = { query { filter { isIn("code", codes) } } },
            cached = { cache.regionsByCodes(codes) },
        ).map { found ->
            // 저장된 순서(최근 순)를 유지한다. PostgREST 는 order 를 full_name 으로 주기 때문이다.
            codes.mapNotNull { code -> found.firstOrNull { it.code == code } }
        }
    }

    private suspend fun fetchRegions(
        remote: suspend () -> List<Region>,
        cached: suspend () -> List<CachedRegion>,
    ): Fetched<List<Region>> = fetchOrCached(
        remote = remote,
        store = { regions ->
            val now = System.currentTimeMillis()
            cache.putRegions(regions.map { CachedRegion.from(it, now) })
        },
        cached = {
            val rows = cached()
            // 빈 사본은 "그런 지역이 없다"가 아니라 "모른다"다 (fetchOrCached 주석 참고).
            if (rows.isEmpty()) null else Fetched(rows.map { it.toRegion() }, rows.minOf { it.cachedAt })
        },
    )

    private suspend fun query(
        build: io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder.() -> Unit,
    ): List<Region> = withContext(Dispatchers.IO) {
        client.from(TABLE).select(columns = Columns.raw(COLUMNS)) {
            build()
            order("full_name", Order.ASCENDING)
        }.decodeList<Region>()
    }

    companion object {
        private const val TABLE = "regions"
        const val MIN_SEARCH_LENGTH = 2
        const val SEARCH_LIMIT = 20

        /** 앱이 실제로 쓰는 컬럼만. 외부 코드 컬럼은 받지 않는다. */
        const val COLUMNS =
            "code,level,parent_code,full_name,sido_name,sigungu_name,dong_name,center_lat,center_lng"
    }
}
