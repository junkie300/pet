package io.github.junkie300.petapp.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * places 조회 (spec.md §4). 지금은 홈(S-00)의 카테고리별 건수만 쓴다.
 * 목록·상세·반경 검색이 붙어도 출입구는 여기 하나다.
 */
class PlaceRepository(private val client: SupabaseClient) {

    /**
     * 선택한 읍면동의 **영업중** 장소를 카테고리별로 센다.
     *
     * 카테고리마다 count 질의를 하나씩 던지고 동시에 기다린다. 행을 받아 코틀린에서 세는 방법이
     * 왕복은 한 번이라 솔깃하지만, PostgREST 의 기본 행 제한(1000)에 걸리면 **건수가 조용히 틀린다.**
     * head 요청은 본문 없이 Content-Range 헤더만 받으므로 전송량도 이쪽이 작다.
     *
     * 적재 안 된 카테고리는 아예 묻지 않는다 — [PlaceCategory.loaded] 참고.
     */
    suspend fun countsByCategory(regionCode: String): Map<PlaceCategory, Int> = coroutineScope {
        PlaceCategory.loadedEntries
            .map { category -> async { category to countIn(regionCode, category) } }
            .awaitAll()
            .toMap()
    }

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

    companion object {
        private const val TABLE = "places"

        /** 폐업·휴업은 세지 않는다. plan.md 1단계 완료 기준 — "폐업 업소가 표시되지 않는다". */
        private const val STATUS_OPEN = "open"
    }
}
