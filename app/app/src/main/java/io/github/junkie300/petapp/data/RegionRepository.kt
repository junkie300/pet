package io.github.junkie300.petapp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.junkie300.petapp.data.cache.CacheDao
import io.github.junkie300.petapp.data.cache.CachedRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    /**
     * 최근 선택 지역을 코드로 되살릴 때 쓴다. 저장해 둔 코드가 사라졌을 수도 있으므로 결과가 빌 수 있다.
     *
     * **이 조회만 흐름이다** (D-79). 홈·목록·지도가 화면을 열자마자 부르는 자리라, 사본이 있으면
     * 서버를 기다리지 않고 지역 이름부터 그린다. 나머지 조회(드롭다운·검색)는 사용자가 그 자리에서
     * 결과를 기다리는 것이라 최신을 봐야 한다.
     */
    fun byCodes(codes: List<String>): Flow<Fetched<List<Region>>> {
        if (codes.isEmpty()) return flowOf(Fetched(emptyList()))
        return cachedThenFresh(
            remote = { query { filter { isIn("code", codes) } } },
            store = ::storeRegions,
            cached = { cachedRegions { cache.regionsByCodes(codes) } },
        ).map { fetched ->
            fetched.map { found ->
                // 저장된 순서(최근 순)를 유지한다. PostgREST 는 order 를 full_name 으로 주기 때문이다.
                codes.mapNotNull { code -> found.firstOrNull { it.code == code } }
            }
        }
    }

    /**
     * [from] 에 **가장 가까운 읍면동** (「이 지역에서 다시 검색」 — D-86).
     *
     * 경계 다각형이 없으므로 "이 좌표를 품는 동"은 물을 수 없다. 좌표 상자로 후보를 받아 와서
     * 하버사인으로 가장 가까운 하나를 고른다 ([nearestTo]).
     *
     * 상자를 두 번 던진다 — 가까운 것부터 찾고, 비면 넓혀서 한 번 더. 바다 위나 산속에서
     * 눌렀을 때 "못 찾았습니다"로 끝내는 것보다 먼 동네라도 답하는 편이 낫다.
     * (넓힌 상자는 서울처럼 조밀한 곳에서 수백 행이 오므로, 처음부터 쓰지는 않는다.)
     *
     * 못 찾으면 데이터가 null 이다 — **예외가 아니다.** 부르는 쪽이 "찾지 못했다"를 화면에
     * 적어야 하고, 그건 조회 실패와 다른 말이다.
     */
    suspend fun nearestDong(from: GeoPoint): Fetched<Region?> {
        var last: Fetched<List<Region>>? = null
        for (span in SEARCH_SPANS) {
            val found = dongsAround(from, span)
            last = found
            found.data.nearestTo(from)?.let { nearest -> return found.map { nearest } }
        }
        return (last ?: Fetched(emptyList())).map { null }
    }

    /** [from] 을 한가운데 둔 [span] 도짜리 정사각 상자 안의 읍면동. */
    private suspend fun dongsAround(from: GeoPoint, span: Double): Fetched<List<Region>> {
        val minLat = from.latitude - span
        val maxLat = from.latitude + span
        val minLng = from.longitude - span
        val maxLng = from.longitude + span
        return fetchRegions(
            remote = {
                query {
                    filter {
                        eq("level", Region.LEVEL_DONG)
                        gte("center_lat", minLat)
                        lte("center_lat", maxLat)
                        gte("center_lng", minLng)
                        lte("center_lng", maxLng)
                    }
                }
            },
            cached = { cache.regionsInBox(Region.LEVEL_DONG, minLat, maxLat, minLng, maxLng) },
        )
    }

    private suspend fun fetchRegions(
        remote: suspend () -> List<Region>,
        cached: suspend () -> List<CachedRegion>,
    ): Fetched<List<Region>> = fetchOrCached(
        remote = remote,
        store = ::storeRegions,
        cached = { cachedRegions(cached) },
    )

    private suspend fun storeRegions(regions: List<Region>) {
        val now = System.currentTimeMillis()
        cache.putRegions(regions.map { CachedRegion.from(it, now) })
    }

    private suspend fun cachedRegions(rows: suspend () -> List<CachedRegion>): Fetched<List<Region>>? {
        val cached = rows()
        // 빈 사본은 "그런 지역이 없다"가 아니라 "모른다"다 (cachedThenFresh 주석 참고).
        return if (cached.isEmpty()) {
            null
        } else {
            Fetched(cached.map { it.toRegion() }, cached.minOf { it.cachedAt })
        }
    }

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

        /**
         * 최근접 읍면동을 찾을 때 훑을 상자의 반변(도).
         *
         * 위도 0.05도는 약 5.6km, 경도 0.05도는 우리 위도에서 약 4.4km 다 — 버튼이 뜨는
         * 3km(`MapRescan.TRIGGER_METERS`)보다 넉넉하다. 그래도 비면 0.5도(약 45~55km)로
         * 넓힌다. 상자는 후보를 줄이는 그물일 뿐, 거리는 하버사인이 다시 잰다.
         */
        private val SEARCH_SPANS = listOf(0.05, 0.5)

        /** 앱이 실제로 쓰는 컬럼만. 외부 코드 컬럼은 받지 않는다. */
        const val COLUMNS =
            "code,level,parent_code,full_name,sido_name,sigungu_name,dong_name,center_lat,center_lng"
    }
}
