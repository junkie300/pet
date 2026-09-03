package io.github.junkie300.petapp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S-01 지역 선택이 쓰는 유일한 데이터 출입구 (spec.md §4).
 *
 * 앱은 공공 API 를 직접 부르지 않는다. regions 는 ETL 이 이미 정규화해 둔 결과다.
 */
class RegionRepository(private val client: SupabaseClient) {

    suspend fun sidoList(): List<Region> = query {
        filter { eq("level", Region.LEVEL_SIDO) }
    }

    /** 상위 지역의 바로 아래 단계. 시도 → 시군구, 시군구 → 읍면동. */
    suspend fun children(parentCode: String): List<Region> = query {
        filter { eq("parent_code", parentCode) }
    }

    /**
     * 지역명 직접 검색. 읍면동만 대상으로 한다 — 사용자가 "연남동"처럼 목적지를 바로 치는 경우다.
     * full_name 에 트라이그램 인덱스가 있어 ilike 가 인덱스를 탄다.
     */
    suspend fun searchDong(keyword: String, limit: Int = SEARCH_LIMIT): List<Region> {
        val q = keyword.trim()
        if (q.length < MIN_SEARCH_LENGTH) return emptyList()
        return query {
            filter {
                eq("level", Region.LEVEL_DONG)
                ilike("full_name", "%$q%")
            }
            limit(limit.toLong())
        }
    }

    /** 최근 선택 지역을 코드로 되살릴 때 쓴다. 저장해 둔 코드가 사라졌을 수도 있으므로 결과가 빌 수 있다. */
    suspend fun byCodes(codes: List<String>): List<Region> {
        if (codes.isEmpty()) return emptyList()
        val found = query { filter { isIn("code", codes) } }
        // 저장된 순서(최근 순)를 유지한다. PostgREST 는 order 를 full_name 으로 주기 때문이다.
        return codes.mapNotNull { code -> found.firstOrNull { it.code == code } }
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

        /** 앱이 실제로 쓰는 컬럼만. 외부 코드 컬럼은 받지 않는다. */
        const val COLUMNS =
            "code,level,parent_code,full_name,sido_name,sigungu_name,dong_name,center_lat,center_lng"
    }
}
